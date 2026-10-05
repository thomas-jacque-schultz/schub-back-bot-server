package schultz.thomas.discord.bot.business.services;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.springframework.stereotype.Service;
import schultz.thomas.discord.bot.data.entity.ChannelEntity;
import schultz.thomas.discord.bot.data.view.GameServerView;
import schultz.thomas.discord.bot.data.entity.MessageEntity;
import schultz.thomas.discord.bot.data.repository.ChannelRepository;


import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Slf4j
@RequiredArgsConstructor
@Service
public class DiscordMessageService {

    private final ChannelRepository channelRepository;
    private final GameServerViewService gameServerViewService;

    private List<ChannelEntity> subscribedChannelsCache;

    // Contenu publié par identifiant de message : un embed inchangé n'est pas réédité (quota Discord).
    private final Map<String, Map<String, Object>> publishedEmbeds = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        subscribedChannelsCache = channelRepository.findAll();
    }

    public List<ChannelEntity> getSubscribedChannels() {
        return List.copyOf(subscribedChannelsCache);
    }

    @SuppressWarnings("null")
    public boolean subscribeDiscordChannel(ChannelEntity channelEntity) {
        if (subscribedChannelsCache.stream().anyMatch(channel -> channel.getChannelId().equals(channelEntity.getChannelId()))) {
            throw new EntityExistsException("Channel already exists");
        }
        var savedChannel = channelRepository.save(channelEntity);
        return subscribedChannelsCache.add(savedChannel);
    }

    public void subscribeAndRefresh(ChannelEntity channelEntity) {
        try {
            subscribeDiscordChannel(channelEntity);
        } catch (EntityExistsException ignored) {
        }

        publishStatusRefreshForAllServers();
    }

    public void subscribeAndRefresh(List<ChannelEntity> channelEntities) {
        for (ChannelEntity channelEntity : channelEntities) {
            subscribeAndRefresh(channelEntity);
        }
    }

    public boolean unsubscribeDiscordChannel(ChannelEntity channelEntity) {
        if (subscribedChannelsCache.stream().noneMatch(channel -> channel.getChannelId().equals(channelEntity.getChannelId()))) {
            throw new EntityNotFoundException("Channel doesn't exist");
        }
        subscribedChannelsCache.remove(channelEntity);
        channelRepository.saveAll(subscribedChannelsCache);
        return true;
    }

    public void sendMessage(ChannelEntity channel, GameServerView gamingServerEntity, JDA jda) {
        TextChannel textChannel = jda.getTextChannelById(channel.getChannelId());
        if (textChannel == null) {
            log.error("TextChannel with ID {} not found", channel.getChannelId());
            return;
        }
        MessageEmbed embed = createEmbedFromServer(gamingServerEntity);
        textChannel.sendMessageEmbeds(embed).queue(
                message -> {
                    publishedEmbeds.put(message.getId(), embed.toData().toMap());
                    MessageEntity messageEntity = new MessageEntity();
                    messageEntity.setEntityId(gamingServerEntity.getId());
                    messageEntity.setMessageId(message.getId());
                    channel.getMessages().add(messageEntity);
                    channelRepository.save(channel);
                    log.info("Message sent and saved for GameServerView ID {}", gamingServerEntity.getId());
                },
                throwable -> log.error("Failed to send message for GameServerView ID {}", gamingServerEntity.getId(), throwable)
        );
    }

    public void updateMessageOrCreate(GameServerView gamingServerEntity, ChannelEntity channel, MessageEntity message, JDA jda) {
        TextChannel textChannel = jda.getTextChannelById(channel.getChannelId());
        if (textChannel == null) {
            log.error("TextChannel with ID {} not found", channel.getChannelId());
            return;
        }
        MessageEmbed embed = createEmbedFromServer(gamingServerEntity);
        Map<String, Object> content = embed.toData().toMap();
        if (content.equals(publishedEmbeds.get(message.getMessageId()))) {
            return;
        }
        textChannel.editMessageEmbedsById(message.getMessageId(), embed).queue(
                success -> {
                    publishedEmbeds.put(message.getMessageId(), content);
                    log.info("Message updated for GameServerView ID {}", gamingServerEntity.getId());
                },
                failure -> {
                    if (isGone(failure)) {
                        log.warn("Message {} disparu du salon {}, reposté", message.getMessageId(), channel.getChannelId());
                        forget(channel, message);
                        sendMessage(channel, gamingServerEntity, jda);
                    } else {
                        log.warn("Édition du message {} en échec, nouvel essai au prochain passage : {}",
                                message.getMessageId(), failure.getMessage());
                    }
                }
        );
    }

    public void messageDeleted(String channelId, String messageId, JDA jda) {
        Optional<ChannelEntity> channel = subscribedChannelsCache.stream()
                .filter(candidate -> candidate.getChannelId().equals(channelId))
                .findFirst();
        Optional<MessageEntity> message = channel.flatMap(found -> found.getMessages().stream()
                .filter(candidate -> candidate.getMessageId().equals(messageId))
                .findFirst());
        if (message.isEmpty()) {
            return;
        }
        log.info("Message {} supprimé du salon {}, reposté", messageId, channelId);
        forget(channel.get(), message.get());
        gameServerViewService.byId(message.get().getEntityId())
                .ifPresent(server -> sendMessage(channel.get(), server, jda));
    }

    private void forget(ChannelEntity channel, MessageEntity message) {
        publishedEmbeds.remove(message.getMessageId());
        channel.getMessages().remove(message);
        channelRepository.save(channel);
    }

    private static boolean isGone(Throwable failure) {
        return failure instanceof ErrorResponseException error
                && (error.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE
                || error.getErrorResponse() == ErrorResponse.UNKNOWN_CHANNEL);
    }

    public void createOrUpdateMessageForGamingServerEntity(GameServerView gsEntity, JDA jda) {
        subscribedChannelsCache.forEach(channelEntity -> {
            MessageEntity existingMessage = channelEntity.getMessages().stream()
                    .filter(messageEntity -> messageEntity.getEntityId().equals(gsEntity.getId()))
                    .findFirst()
                    .orElse(null);

            if (existingMessage == null) {
                sendMessage(channelEntity, gsEntity, jda);
            } else {
                updateMessageOrCreate(gsEntity, channelEntity, existingMessage, jda);
            }
        });
    }

    private MessageEmbed createEmbedFromServer(GameServerView gamingServerEntity) {
        EmbedBuilder embedBuilder = new EmbedBuilder();

        if(gamingServerEntity.getName() == null || gamingServerEntity.getName().isEmpty()){
            embedBuilder.setTitle(gamingServerEntity.getName());
        }
        else {
            embedBuilder.setTitle(gamingServerEntity.getGameLabel() + " - " + gamingServerEntity.getName());
        }
        embedBuilder.setColor(gamingServerEntity.isOnline() ? Color.GREEN : Color.RED);

        embedBuilder.addField("URL : ```" + gamingServerEntity.getUrlConnection()+ "```","", false);
        embedBuilder.addField("Nombre de joueurs max", String.valueOf(gamingServerEntity.getPlayersMax()), true);
        embedBuilder.addField("Version", Objects.requireNonNullElse(gamingServerEntity.getVersion(),""), true);

        if (gamingServerEntity.getInstallation() != null && !gamingServerEntity.getInstallation().isEmpty()) {
            embedBuilder.addField("Installation :", gamingServerEntity.getInstallation(), false);
        }

        if (gamingServerEntity.getDescription() != null && !gamingServerEntity.getDescription().isEmpty()) {
            embedBuilder.setDescription(gamingServerEntity.getDescription());
        }

        if(gamingServerEntity.getSlug() != null && !gamingServerEntity.getSlug().isEmpty()){
            embedBuilder.addField("Identifiant :", gamingServerEntity.getSlug(), false);
        }

        if (gamingServerEntity.getReferents() != null && !gamingServerEntity.getReferents().isEmpty()) {
            embedBuilder.addField("Référents", gamingServerEntity.getReferents().stream()
                    .map(referent -> referent.getDiscordId() != null
                            ? "<@" + referent.getDiscordId() + ">"
                            : referent.getDisplayName())
                    .collect(Collectors.joining(" ")), false);
        }

        GameServerView.CommandFailure failure = gamingServerEntity.getLastCommandFailure();
        if (failure != null) {
            String action = "stop".equals(failure.getAction()) ? "arrêt" : "démarrage";
            embedBuilder.addField("⚠️ Dernier " + action + " en échec", Objects.requireNonNullElse(failure.getMessage(), ""), false);
        }

        embedBuilder.setFooter("Statut : " +  (gamingServerEntity.isOnline() ? "\uD83D\uDFE2":"\uD83D\uDD34"), null);

        embedBuilder.setThumbnail(gamingServerEntity.getGameIconUrl());

        return embedBuilder.build();
    }

    private void publishStatusRefreshForAllServers() {
        log.debug("Rafraîchissement demandé pour {} serveurs", gameServerViewService.all().size());
    }

    public void refreshAllMessages(JDA jda) {
        gameServerViewService.all().forEach(server -> createOrUpdateMessageForGamingServerEntity(server, jda));
    }
}

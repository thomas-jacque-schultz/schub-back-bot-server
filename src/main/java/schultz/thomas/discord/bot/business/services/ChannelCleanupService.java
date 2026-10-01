package schultz.thomas.discord.bot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;
import schultz.thomas.discord.bot.api.dto.ChannelCleanupDto;
import schultz.thomas.discord.bot.data.entity.ChannelEntity;
import schultz.thomas.discord.bot.data.entity.MessageEntity;
import schultz.thomas.discord.bot.data.repository.ChannelRepository;
import schultz.thomas.discord.bot.data.view.GameServerView;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

// Un salon d'état ne garde que les encarts du bot, un par serveur de jeu (discord#28).
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelCleanupService {

    private final JDA jda;
    private final DiscordMessageService messages;
    private final GameServerViewService servers;
    private final ChannelRepository channels;

    public List<ChannelCleanupDto> preview() {
        return messages.getSubscribedChannels().stream().map(channel -> plan(channel).dto()).toList();
    }

    public List<ChannelCleanupDto> clean() {
        List<ChannelCleanupDto> bilan = messages.getSubscribedChannels().stream().map(this::clean).toList();
        messages.refreshAllMessages(jda);
        return bilan;
    }

    private ChannelCleanupDto clean(ChannelEntity channel) {
        Plan plan = plan(channel);
        if (plan.salon() == null || !plan.dto().allowed()) {
            return plan.dto();
        }
        CompletableFuture.allOf(plan.salon().purgeMessages(plan.aSupprimer()).toArray(CompletableFuture[]::new))
                .join();
        channel.getMessages().removeIf(encart -> !plan.gardes().contains(encart.getMessageId()));
        channels.save(channel);
        log.info("Salon {} nettoyé : {} message(s) supprimé(s), {} encart(s) à reposter.",
                channel.getChannelId(), plan.aSupprimer().size(), plan.dto().toRepost());
        return plan.dto();
    }

    // Un encart est gardé s'il est encore dans le salon et que son serveur existe toujours.
    private Plan plan(ChannelEntity channel) {
        TextChannel salon = jda.getTextChannelById(channel.getChannelId());
        Set<String> serveurs = servers.all().stream().map(GameServerView::getId).collect(Collectors.toSet());
        if (salon == null) {
            return new Plan(null, List.of(), Set.of(),
                    new ChannelCleanupDto(channel.getChannelId(), channel.getName(), false, 0, 0));
        }
        boolean autorise = salon.getGuild().getSelfMember()
                .hasPermission(salon, Permission.MESSAGE_HISTORY, Permission.MESSAGE_MANAGE);
        Set<String> attendus = channel.getMessages().stream()
                .filter(encart -> serveurs.contains(encart.getEntityId()))
                .map(MessageEntity::getMessageId)
                .collect(Collectors.toSet());
        List<Message> historique = autorise ? salon.getIterableHistory().stream().toList() : List.of();
        List<Message> aSupprimer = historique.stream().filter(m -> !attendus.contains(m.getId())).toList();
        Set<String> gardes = historique.stream().map(Message::getId).filter(attendus::contains)
                .collect(Collectors.toSet());
        return new Plan(salon, aSupprimer, gardes,
                new ChannelCleanupDto(channel.getChannelId(), salon.getName(), autorise, aSupprimer.size(),
                        autorise ? serveurs.size() - gardes.size() : 0));
    }

    private record Plan(TextChannel salon, List<Message> aSupprimer, Set<String> gardes, ChannelCleanupDto dto) {
    }
}

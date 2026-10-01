package schultz.thomas.discord.bot.api.controller;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.discord.bot.business.mapper.DiscordChannelMapper;
import schultz.thomas.discord.bot.business.services.ChannelCleanupService;
import schultz.thomas.discord.bot.business.services.DirectMessageService;
import schultz.thomas.discord.bot.business.services.DiscordMessageService;
import schultz.thomas.discord.bot.api.dto.ChannelCleanupDto;
import schultz.thomas.discord.bot.api.dto.DirectMessageAck;
import schultz.thomas.discord.bot.api.dto.DirectMessageRequest;
import schultz.thomas.discord.bot.api.dto.DiscordChannelSubscriptionRequest;
import schultz.thomas.discord.bot.api.dto.DiscordGuildChannelsDto;
import schultz.thomas.discord.bot.data.entity.ChannelEntity;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/discord")
@RequiredArgsConstructor
public class DiscordController {

    private final JDA jda;
    private final DiscordMessageService discordMessageService;
    private final DiscordChannelMapper discordChannelMapper;
    private final DirectMessageService directMessageService;
    private final ChannelCleanupService channelCleanupService;

    @GetMapping("/guilds/channels")
    public ResponseEntity<List<DiscordGuildChannelsDto>> getGuildsWithChannels() {
        Set<String> subscribedChannelIds = discordMessageService.getSubscribedChannels().stream()
                .map(ChannelEntity::getChannelId)
                .collect(Collectors.toSet());

        List<DiscordGuildChannelsDto> payload = jda.getGuilds().stream()
            .map(guild -> discordChannelMapper.toGuildDto(guild, subscribedChannelIds))
                .toList();

        return ResponseEntity.ok(payload);
    }

    @PostMapping("/channels/subscribe")
    public ResponseEntity<Void> subscribeChannels(@RequestBody DiscordChannelSubscriptionRequest request) {
        if (request == null || request.channels() == null || request.channels().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<ChannelEntity> channels = request.channels().stream()
                .map(discordChannelMapper::toChannelEntity)
                .toList();

        discordMessageService.subscribeAndRefresh(channels);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/channels/clean")
    public List<ChannelCleanupDto> previewCleanup() {
        return channelCleanupService.preview();
    }

    @PostMapping("/channels/clean")
    public List<ChannelCleanupDto> cleanChannels() {
        return channelCleanupService.clean();
    }

    @PostMapping("/direct-messages")
    public ResponseEntity<DirectMessageAck> sendDirectMessage(@RequestBody DirectMessageRequest request) {
        if (request == null
                || request.recipientId() == null || request.recipientId().isBlank()
                || request.body() == null || request.body().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        DirectMessageAck ack = directMessageService.send(request);
        return ack.delivered()
                ? ResponseEntity.ok(ack)
                : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ack);
    }
}

package schultz.thomas.discord.bot.api.events.listeners;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import schultz.thomas.discord.bot.business.services.DiscordMessageService;

@Component
@RequiredArgsConstructor
public class TrackedMessageDeleteListener extends ListenerAdapter {

    private final DiscordMessageService discordMessageService;

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        discordMessageService.messageDeleted(event.getChannel().getId(), event.getMessageId(), event.getJDA());
    }
}

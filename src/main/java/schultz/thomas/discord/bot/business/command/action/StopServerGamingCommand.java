package schultz.thomas.discord.bot.business.command.action;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.internal.interactions.CommandDataImpl;
import org.springframework.stereotype.Component;
import schultz.thomas.discord.bot.business.command.Command;
import schultz.thomas.discord.bot.business.command.CommandContext;
import schultz.thomas.discord.bot.business.exceptions.CommandFailedException;
import schultz.thomas.discord.bot.business.services.CoreClient;
import schultz.thomas.discord.bot.business.services.GameServerViewService;
import schultz.thomas.discord.bot.data.enums.CommandEnum;
import schultz.thomas.discord.bot.data.enums.PermissionEnum;


@Slf4j
@Component
@RequiredArgsConstructor
public class StopServerGamingCommand implements Command {

    private final CoreClient coreClient;
    private final GameServerViewService gameServerViewService;

    @Override
    public PermissionEnum permissionNeeded() {
        return PermissionEnum.SERVER_STOP;
    }

    @Override
    public CommandData getCommandData() {
        return new CommandDataImpl("pause", "arrête le serveur de jeu")
                .addOptions(new OptionData(OptionType.STRING, "identifier", "identifiant du serveur", true));
    }

    @Override
    public CommandEnum getEnum() {
        return CommandEnum.STOP_SGAMING;
    }

    @Override
    public String execute(CommandContext context) {
        String slug = context.getOptions().get("identifier");
        gameServerViewService.bySlug(slug)
                .orElseThrow(() -> new CommandFailedException("Le serveur de jeu n'existe pas"));
        try {
            coreClient.stop(slug, context.getOptions().get("user-id"));
        } catch (RuntimeException e) {
            if (CoreClient.isNotReferent(e)) {
                throw new CommandFailedException("Tu n'es pas référent de ce serveur");
            }
            log.warn("Arrêt refusé par le cœur pour '{}' : {}", slug, e.getMessage());
            throw new CommandFailedException("Impossible d'arrêter le serveur de jeu");
        }
        return "Commande reçue et transmise.";
    }
}

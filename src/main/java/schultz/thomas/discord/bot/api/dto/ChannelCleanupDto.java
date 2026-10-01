package schultz.thomas.discord.bot.api.dto;

/** Ce que le nettoyage d'un salon d'état supprime et reposte ; {@code allowed} faux sans « Gérer les messages ». */
public record ChannelCleanupDto(
        String channelId,
        String name,
        boolean allowed,
        int toDelete,
        int toRepost
) {
}

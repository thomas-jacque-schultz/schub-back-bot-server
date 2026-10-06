package schultz.thomas.discord.bot.data.view;

import lombok.Data;

import java.util.List;

@Data
public class GameServerView {

    private String id;
    private String slug;
    private String name;
    private String urlConnection;
    private String gameLabel;
    private String gameIconUrl;
    private Integer playersMax;
    private String installation;
    private String version;
    private String description;
    private String status;
    private CommandFailure lastCommandFailure;
    private List<Referent> referents = List.of();

    @Data
    public static class Referent {
        private String displayName;
        private String discordId;
    }

    @Data
    public static class CommandFailure {
        private String action;
        private String message;
    }

    public boolean isOnline() {
        return "ONLINE".equals(status);
    }
}

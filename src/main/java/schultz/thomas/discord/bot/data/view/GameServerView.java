package schultz.thomas.discord.bot.data.view;

import lombok.Data;

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

    @Data
    public static class CommandFailure {
        private String action;
        private String message;
    }

    public boolean isOnline() {
        return "ONLINE".equals(status);
    }
}

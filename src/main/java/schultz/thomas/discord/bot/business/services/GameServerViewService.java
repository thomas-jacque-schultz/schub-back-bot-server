package schultz.thomas.discord.bot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import schultz.thomas.discord.bot.data.view.GameServerView;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerViewService {

    private final CoreClient coreClient;

    private final AtomicReference<List<GameServerView>> view = new AtomicReference<>(List.of());

    public boolean refresh() {
        try {
            view.set(List.copyOf(coreClient.fetchAll()));
            return true;
        } catch (RuntimeException e) {
            log.warn("Lecture du cœur impossible, la vue précédente est conservée : {}", e.getMessage());
            return false;
        }
    }

    public List<GameServerView> all() {
        return view.get();
    }

    public Optional<GameServerView> byId(String id) {
        return view.get().stream().filter(server -> id.equals(server.getId())).findFirst();
    }

    public Optional<GameServerView> bySlug(String slug) {
        return view.get().stream().filter(server -> slug.equals(server.getSlug())).findFirst();
    }
}

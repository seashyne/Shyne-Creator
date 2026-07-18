package seashyne.shynecore.team;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import seashyne.shynecore.ShyneCore;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SAVE_TYPE = new TypeToken<Map<String, String>>(){}.getType();
    private final Map<UUID, String> teams = new ConcurrentHashMap<>();
    private final Path saveFile;

    public TeamManager(Path configDir) {
        this.saveFile = configDir.resolve("teams.json");
    }

    public void loadOrCreate() {
        try {
            Files.createDirectories(saveFile.getParent());
            if (!Files.exists(saveFile)) {
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(saveFile)) {
                Map<String, String> raw = GSON.fromJson(reader, SAVE_TYPE);
                teams.clear();
                if (raw != null) {
                    for (Map.Entry<String, String> e : raw.entrySet()) {
                        try { teams.put(UUID.fromString(e.getKey()), e.getValue()); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            ShyneCore.LOGGER.warn("[Teams] Could not load teams.json: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(saveFile.getParent());
            Map<String, String> raw = new TreeMap<>();
            for (Map.Entry<UUID, String> e : teams.entrySet()) raw.put(e.getKey().toString(), e.getValue());
            try (Writer writer = Files.newBufferedWriter(saveFile)) {
                GSON.toJson(raw, writer);
            }
        } catch (Exception e) {
            ShyneCore.LOGGER.warn("[Teams] Could not save teams.json: {}", e.getMessage());
        }
    }

    public Optional<String> getTeam(UUID playerId) { return Optional.ofNullable(teams.get(playerId)); }
    public Optional<String> getTeam(ServerPlayer player) { return getTeam(player.getUUID()); }

    public void setTeam(UUID playerId, String teamId) {
        if (teamId == null || teamId.isBlank()) teams.remove(playerId); else teams.put(playerId, teamId.toLowerCase(Locale.ROOT));
        save();
    }

    public void clear(UUID playerId) { teams.remove(playerId); save(); }
    public boolean sameTeam(UUID a, UUID b) { return Objects.equals(teams.get(a), teams.get(b)) && teams.containsKey(a); }
    public Map<UUID, String> all() { return Map.copyOf(teams); }
}

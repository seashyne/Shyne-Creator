package seashyne.shynecore.team;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.world.entity.Entity;
import seashyne.shynecore.ShyneCore;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamRuntime {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, String> teamsByEntity = new ConcurrentHashMap<>();
    private final Map<String, TeamSettings> settingsByTeam = new ConcurrentHashMap<>();
    private final Path savePath;

    public TeamRuntime(Path savePath) {
        this.savePath = savePath;
    }

    public String getTeam(UUID entityId) { return teamsByEntity.getOrDefault(entityId, ""); }

    public void setTeam(Entity entity, String teamId) {
        if (entity == null) return;
        if (teamId == null || teamId.isBlank()) {
            teamsByEntity.remove(entity.getUUID());
            save();
            return;
        }
        String normalized = teamId.trim().toLowerCase(Locale.ROOT);
        teamsByEntity.put(entity.getUUID(), normalized);
        settingsByTeam.putIfAbsent(normalized, new TeamSettings(normalized, false, true, true));
        save();
    }

    public TeamSettings getSettings(String teamId) {
        return settingsByTeam.getOrDefault(teamId == null ? "" : teamId.toLowerCase(Locale.ROOT), TeamSettings.DEFAULT);
    }

    public void configureTeam(String teamId, boolean friendlyFire, boolean targetMobs, boolean targetPlayers) {
        if (teamId == null || teamId.isBlank()) return;
        String normalized = teamId.trim().toLowerCase(Locale.ROOT);
        settingsByTeam.put(normalized, new TeamSettings(normalized, friendlyFire, targetMobs, targetPlayers));
        save();
    }

    public boolean areAllied(UUID a, UUID b) {
        String ta = getTeam(a), tb = getTeam(b);
        return !ta.isBlank() && ta.equals(tb);
    }

    public void pruneMissing(Set<UUID> live) {
        boolean changed = teamsByEntity.keySet().removeIf(id -> !live.contains(id));
        if (changed) save();
    }

    public void load() {
        if (!Files.exists(savePath)) return;
        try (Reader reader = Files.newBufferedReader(savePath)) {
            SaveData data = GSON.fromJson(reader, SaveData.class);
            teamsByEntity.clear();
            settingsByTeam.clear();
            if (data != null) {
                if (data.entityTeams != null) {
                    data.entityTeams.forEach((key, value) -> teamsByEntity.put(UUID.fromString(key), value));
                }
                if (data.teamSettings != null) settingsByTeam.putAll(data.teamSettings);
            }
        } catch (Exception e) {
            ShyneCore.LOGGER.warn("[Teams] Could not load team config: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(savePath.getParent());
            SaveData data = new SaveData();
            teamsByEntity.forEach((k, v) -> data.entityTeams.put(k.toString(), v));
            data.teamSettings.putAll(settingsByTeam);
            try (Writer writer = Files.newBufferedWriter(savePath)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            ShyneCore.LOGGER.warn("[Teams] Could not save team config: {}", e.getMessage());
        }
    }

    public record TeamSettings(String teamId, boolean friendlyFire, boolean targetMobs, boolean targetPlayers) {
        public static final TeamSettings DEFAULT = new TeamSettings("", false, true, true);
    }

    private static final class SaveData {
        Map<String, String> entityTeams = new LinkedHashMap<>();
        Map<String, TeamSettings> teamSettings = new LinkedHashMap<>();
    }
}

package seashyne.shynecore.power;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import seashyne.shynecore.ShyneCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Server-authoritative power configuration for gameplay balancing,
 * permission control, and dimension restrictions.
 */
public final class PowerServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final PowerServerConfig INSTANCE = new PowerServerConfig();

    private float globalCooldownMultiplier = 1.0f;
    private float globalManaCostMultiplier = 1.0f;
    private boolean enablePvpDamage = true;
    private boolean freeSelection = true;
    private List<String> bannedSkills = new ArrayList<>();
    private Map<String, List<String>> dimensionBlacklist = new LinkedHashMap<>();

    private PowerServerConfig() {}

    public static PowerServerConfig get() {
        return INSTANCE;
    }

    public synchronized void load(Path configDir) {
        if (configDir == null) return;
        Path file = configDir.resolve("shyne_powers_server.json");
        if (!Files.exists(file)) {
            save(configDir);
            return;
        }
        try {
            String json = Files.readString(file);
            PowerServerConfig loaded = GSON.fromJson(json, PowerServerConfig.class);
            if (loaded != null) {
                this.globalCooldownMultiplier = Math.max(0.05f, Math.min(20.0f, loaded.globalCooldownMultiplier));
                this.globalManaCostMultiplier = Math.max(0.0f, Math.min(20.0f, loaded.globalManaCostMultiplier));
                this.enablePvpDamage = loaded.enablePvpDamage;
                this.freeSelection = loaded.freeSelection;
                this.bannedSkills = loaded.bannedSkills != null ? new ArrayList<>(loaded.bannedSkills) : new ArrayList<>();
                this.dimensionBlacklist = loaded.dimensionBlacklist != null ? new LinkedHashMap<>(loaded.dimensionBlacklist) : new LinkedHashMap<>();
            }
        } catch (Exception e) {
            ShyneCore.LOGGER.error("[PowerServerConfig] Failed to load server config: {}", e.getMessage());
        }
    }

    public synchronized void save(Path configDir) {
        if (configDir == null) return;
        try {
            Files.createDirectories(configDir);
            Path file = configDir.resolve("shyne_powers_server.json");
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            ShyneCore.LOGGER.error("[PowerServerConfig] Failed to write server config: {}", e.getMessage());
        }
    }

    public synchronized boolean isBanned(String skillId) {
        if (skillId == null || skillId.isBlank()) return false;
        return bannedSkills.stream().anyMatch(banned -> banned.equalsIgnoreCase(skillId.trim()));
    }

    public synchronized boolean isDimensionBanned(String dimension, String skillId) {
        if (dimension == null || skillId == null) return false;
        List<String> list = dimensionBlacklist.get(dimension);
        if (list == null) return false;
        return list.stream().anyMatch(banned -> banned.equalsIgnoreCase(skillId.trim()));
    }

    public synchronized float getGlobalCooldownMultiplier() {
        return globalCooldownMultiplier;
    }

    public synchronized float getGlobalManaCostMultiplier() {
        return globalManaCostMultiplier;
    }

    public synchronized boolean isEnablePvpDamage() {
        return enablePvpDamage;
    }

    public synchronized boolean isFreeSelection() {
        return freeSelection;
    }

    public synchronized List<String> getBannedSkills() {
        return List.copyOf(bannedSkills);
    }

    public synchronized Map<String, List<String>> getDimensionBlacklist() {
        return Map.copyOf(dimensionBlacklist);
    }

    public synchronized String toJson() {
        return GSON.toJson(this);
    }

    public synchronized void applyJson(String json) {
        if (json == null || json.isBlank()) return;
        try {
            PowerServerConfig loaded = GSON.fromJson(json, PowerServerConfig.class);
            if (loaded != null) {
                this.globalCooldownMultiplier = loaded.globalCooldownMultiplier;
                this.globalManaCostMultiplier = loaded.globalManaCostMultiplier;
                this.enablePvpDamage = loaded.enablePvpDamage;
                this.freeSelection = loaded.freeSelection;
                this.bannedSkills = loaded.bannedSkills != null ? new ArrayList<>(loaded.bannedSkills) : new ArrayList<>();
                this.dimensionBlacklist = loaded.dimensionBlacklist != null ? new LinkedHashMap<>(loaded.dimensionBlacklist) : new LinkedHashMap<>();
            }
        } catch (Exception e) {
            ShyneCore.LOGGER.warn("[PowerServerConfig] Malformed config payload: {}", e.getMessage());
        }
    }
}

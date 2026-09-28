package seashyne.shynecore.client.state;

import com.google.gson.Gson;
import seashyne.shynecore.ShyneCore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side cached power configuration synchronized from the server.
 */
public final class ClientPowerConfig {
    private static final Gson GSON = new Gson();
    private static volatile float globalCooldownMultiplier = 1.0f;
    private static volatile float globalManaCostMultiplier = 1.0f;
    private static volatile boolean enablePvpDamage = true;
    private static volatile boolean freeSelection = true;
    private static final Set<String> BANNED_SKILLS = ConcurrentHashMap.newKeySet();
    private static final Map<String, List<String>> DIMENSION_BLACKLIST = new ConcurrentHashMap<>();

    private ClientPowerConfig() {}

    public static void handleSync(String json) {
        if (json == null || json.isBlank()) return;
        try {
            ConfigPayload payload = GSON.fromJson(json, ConfigPayload.class);
            if (payload != null) {
                globalCooldownMultiplier = payload.globalCooldownMultiplier;
                globalManaCostMultiplier = payload.globalManaCostMultiplier;
                enablePvpDamage = payload.enablePvpDamage;
                freeSelection = payload.freeSelection;
                BANNED_SKILLS.clear();
                if (payload.bannedSkills != null) {
                    BANNED_SKILLS.addAll(payload.bannedSkills);
                }
                DIMENSION_BLACKLIST.clear();
                if (payload.dimensionBlacklist != null) {
                    DIMENSION_BLACKLIST.putAll(payload.dimensionBlacklist);
                }
            }
        } catch (Exception e) {
            ShyneCore.LOGGER.warn("[ClientPowerConfig] Failed to parse sync payload: {}", e.getMessage());
        }
    }

    public static boolean isBanned(String skillId) {
        if (skillId == null || skillId.isBlank()) return false;
        return BANNED_SKILLS.stream().anyMatch(b -> b.equalsIgnoreCase(skillId.trim()));
    }

    public static boolean isDimensionBanned(String dimension, String skillId) {
        if (dimension == null || skillId == null) return false;
        List<String> list = DIMENSION_BLACKLIST.get(dimension);
        return list != null && list.stream().anyMatch(b -> b.equalsIgnoreCase(skillId.trim()));
    }

    public static float getGlobalCooldownMultiplier() {
        return globalCooldownMultiplier;
    }

    public static float getGlobalManaCostMultiplier() {
        return globalManaCostMultiplier;
    }

    public static boolean isEnablePvpDamage() {
        return enablePvpDamage;
    }

    public static boolean isFreeSelection() {
        return freeSelection;
    }

    public static Set<String> getBannedSkills() {
        return Set.copyOf(BANNED_SKILLS);
    }

    public static void reset() {
        globalCooldownMultiplier = 1.0f;
        globalManaCostMultiplier = 1.0f;
        enablePvpDamage = true;
        freeSelection = true;
        BANNED_SKILLS.clear();
        DIMENSION_BLACKLIST.clear();
    }

    private static class ConfigPayload {
        float globalCooldownMultiplier = 1.0f;
        float globalManaCostMultiplier = 1.0f;
        boolean enablePvpDamage = true;
        boolean freeSelection = true;
        List<String> bannedSkills;
        Map<String, List<String>> dimensionBlacklist;
    }
}

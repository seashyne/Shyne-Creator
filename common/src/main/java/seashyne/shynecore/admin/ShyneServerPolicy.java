package seashyne.shynecore.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Server-authoritative governance policy and security settings.
 * <p>
 * Philosophy: By default, everything is completely UNRESTRICTED and OPEN (Unlimited freedom).
 * There are NO damage caps, NO reach limits, NO projectile quotas, NO summon limits, and anti-cheat
 * is disabled by default so all anime, fantasy, god-tier, and bullet-hell powers function with 100% freedom.
 * <p>
 * Server administrators can set custom limits whenever they wish via:
 * <ul>
 *   <li>The configuration file: {@code config/shyne-creator/server_policy.json}</li>
 *   <li>In-game command: {@code /shyne policy set <key> <value>}</li>
 *   <li>Hot-reload command: {@code /shyne policy reload}</li>
 * </ul>
 * Setting a numerical limit to {@code -1}, {@code 0}, or {@code "unlimited"} removes the limit.
 */
public final class ShyneServerPolicy {
    private static final Logger LOGGER = Logger.getLogger("ShyneServerPolicy");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ShyneServerPolicy INSTANCE = new ShyneServerPolicy();
    private static Path configPath;

    // =========================================================================
    // Griefing & World Interaction Controls (Default: Fully Open)
    // =========================================================================

    /** Allows avatar skills and magic to modify/destroy blocks in the world. */
    private boolean blockDamageAllowed = true;

    /** Allows fire spread and ignition caused by avatar skills. */
    private boolean fireSpreadAllowed = true;

    // =========================================================================
    // Combat & Damage Policies (Default: Unlimited Damage & Open Combat)
    // =========================================================================

    /** Allows avatar skills to damage other players in PvP encounters. */
    private boolean pvpSkillsAllowed = true;

    /** Allows avatar skills to damage teammates registered in the same team. */
    private boolean friendlyFireAllowed = true;

    /** Maximum allowed damage per skill cast. Value <= 0 means UNLIMITED. */
    private double maxSkillDamageCap = -1.0;

    // =========================================================================
    // Movement & Abilities (Default: Fully Open)
    // =========================================================================

    /** Allows custom animation flight and script-driven levitation. */
    private boolean customFlightAllowed = true;

    /** Allows avatars to stream audio from external URLs. */
    private boolean externalAudioStreamsAllowed = true;

    // =========================================================================
    // Entity & Spawn Quotas (Default: Unlimited)
    // =========================================================================

    /** Maximum concurrent active projectiles per player. Value <= 0 means UNLIMITED. */
    private int maxProjectilesPerPlayer = -1;

    /** Maximum concurrent active summoned entities per player. Value <= 0 means UNLIMITED. */
    private int maxSummonsPerPlayer = -1;

    // =========================================================================
    // Anti-Cheat & Rate Limiting (Default: Disabled / Unlimited)
    // =========================================================================

    /** Enables the server-authoritative rate limiter and reach validation (default false). */
    private boolean antiCheatEnabled = false;

    /** Maximum allowed skill cast packets per second. Value <= 0 means UNLIMITED. */
    private int maxSkillCastsPerSecond = -1;

    /** Maximum allowed reach distance (blocks) between player and target. Value <= 0 means UNLIMITED. */
    private double maxTargetReachDistance = -1.0;

    /** Punishment applied on violations: "LOG_ONLY", "CANCEL_ACTION", or "KICK" (default: "LOG_ONLY"). */
    private String antiCheatPunishment = "LOG_ONLY";

    public ShyneServerPolicy() {}

    /**
     * Gets the active singleton server policy.
     * @return current active {@link ShyneServerPolicy}
     */
    public static ShyneServerPolicy get() {
        return INSTANCE;
    }

    /**
     * Initializes the policy system for the given game directory.
     * Resets the active instance and loads from disk or generates defaults.
     *
     * @param gameDir root Minecraft game directory
     */
    public static synchronized void init(Path gameDir) {
        INSTANCE = new ShyneServerPolicy();
        configPath = gameDir.resolve("config").resolve("shyne-creator").resolve("server_policy.json");
        load();
    }

    /**
     * Loads the server policy from disk. If the file does not exist, saves current defaults.
     */
    public static synchronized void load() {
        if (configPath == null || !Files.exists(configPath)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            ShyneServerPolicy loaded = GSON.fromJson(reader, ShyneServerPolicy.class);
            if (loaded != null) {
                INSTANCE = loaded;
                LOGGER.info(() -> "[ShyneServerPolicy] Successfully loaded server policy from " + configPath);
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "[ShyneServerPolicy] Failed to read server policy, using defaults: " + e.getMessage());
        }
    }

    /**
     * Persists the active server policy to disk.
     */
    public static synchronized void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            LOGGER.warning(() -> "[ShyneServerPolicy] Failed to save server policy: " + e.getMessage());
        }
    }

    /**
     * Dynamically updates a policy key at runtime and persists changes to disk.
     * Accepts "-1", "0", "unlimited", or "none" for numerical fields to remove limits.
     *
     * @param key setting identifier (case-insensitive)
     * @param rawValue string value to parse
     * @return true if updated successfully, false if key or value is invalid
     */
    public synchronized boolean set(String key, String rawValue) {
        if (key == null || rawValue == null) return false;
        String k = key.trim().toLowerCase(java.util.Locale.ROOT);
        String v = rawValue.trim();

        try {
            switch (k) {
                case "block_damage", "block_damage_allowed" -> blockDamageAllowed = Boolean.parseBoolean(v);
                case "fire_spread", "fire_spread_allowed" -> fireSpreadAllowed = Boolean.parseBoolean(v);
                case "pvp_skills", "pvp_skills_allowed" -> pvpSkillsAllowed = Boolean.parseBoolean(v);
                case "friendly_fire", "friendly_fire_allowed" -> friendlyFireAllowed = Boolean.parseBoolean(v);
                case "custom_flight", "custom_flight_allowed" -> customFlightAllowed = Boolean.parseBoolean(v);
                case "audio_streams", "external_audio_streams_allowed" -> externalAudioStreamsAllowed = Boolean.parseBoolean(v);
                case "max_damage", "max_skill_damage_cap" -> {
                    if (isUnlimitedToken(v)) {
                        maxSkillDamageCap = -1.0;
                    } else {
                        double parsed = Double.parseDouble(v);
                        if (parsed <= 0.0) return false;
                        maxSkillDamageCap = parsed;
                    }
                }
                case "max_projectiles", "max_projectiles_per_player" -> {
                    if (isUnlimitedToken(v)) {
                        maxProjectilesPerPlayer = -1;
                    } else {
                        int parsed = Integer.parseInt(v);
                        if (parsed <= 0) return false;
                        maxProjectilesPerPlayer = parsed;
                    }
                }
                case "max_summons", "max_summons_per_player" -> {
                    if (isUnlimitedToken(v)) {
                        maxSummonsPerPlayer = -1;
                    } else {
                        int parsed = Integer.parseInt(v);
                        if (parsed <= 0) return false;
                        maxSummonsPerPlayer = parsed;
                    }
                }
                case "anti_cheat", "anti_cheat_enabled" -> antiCheatEnabled = Boolean.parseBoolean(v);
                case "max_casts_per_sec", "max_skill_casts_per_second" -> {
                    if (isUnlimitedToken(v)) {
                        maxSkillCastsPerSecond = -1;
                    } else {
                        int parsed = Integer.parseInt(v);
                        if (parsed <= 0) return false;
                        maxSkillCastsPerSecond = parsed;
                    }
                }
                case "max_reach", "max_target_reach_distance" -> {
                    if (isUnlimitedToken(v)) {
                        maxTargetReachDistance = -1.0;
                    } else {
                        double parsed = Double.parseDouble(v);
                        if (parsed <= 0.0) return false;
                        maxTargetReachDistance = parsed;
                    }
                }
                case "punishment", "anti_cheat_punishment" -> {
                    String upper = v.toUpperCase(java.util.Locale.ROOT);
                    if (upper.equals("LOG_ONLY") || upper.equals("CANCEL_ACTION") || upper.equals("KICK")) {
                        antiCheatPunishment = upper;
                    } else return false;
                }
                default -> { return false; }
            }
            save();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isUnlimitedToken(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("unlimited") || lower.equals("none") || lower.equals("off")
            || lower.equals("-1") || lower.equals("-1.0") || lower.equals("0");
    }

    /**
     * Returns the human-readable formatted representation of a policy setting.
     * Numerical limits <= 0 are presented as "UNLIMITED".
     *
     * @param key setting key name
     * @return readable status string
     */
    public String getFormatted(String key) {
        if (key == null) return "UNKNOWN";
        return switch (key.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "block_damage", "block_damage_allowed" -> String.valueOf(blockDamageAllowed);
            case "fire_spread", "fire_spread_allowed" -> String.valueOf(fireSpreadAllowed);
            case "pvp_skills", "pvp_skills_allowed" -> String.valueOf(pvpSkillsAllowed);
            case "friendly_fire", "friendly_fire_allowed" -> String.valueOf(friendlyFireAllowed);
            case "custom_flight", "custom_flight_allowed" -> String.valueOf(customFlightAllowed);
            case "audio_streams", "external_audio_streams_allowed" -> String.valueOf(externalAudioStreamsAllowed);
            case "max_damage", "max_skill_damage_cap" -> maxSkillDamageCap <= 0 ? "UNLIMITED" : String.valueOf(maxSkillDamageCap);
            case "max_projectiles", "max_projectiles_per_player" -> maxProjectilesPerPlayer <= 0 ? "UNLIMITED" : String.valueOf(maxProjectilesPerPlayer);
            case "max_summons", "max_summons_per_player" -> maxSummonsPerPlayer <= 0 ? "UNLIMITED" : String.valueOf(maxSummonsPerPlayer);
            case "anti_cheat", "anti_cheat_enabled" -> antiCheatEnabled ? "ENABLED" : "DISABLED";
            case "max_casts_per_sec", "max_skill_casts_per_second" -> maxSkillCastsPerSecond <= 0 ? "UNLIMITED" : (maxSkillCastsPerSecond + " casts/sec");
            case "max_reach", "max_target_reach_distance" -> maxTargetReachDistance <= 0 ? "UNLIMITED" : String.valueOf(maxTargetReachDistance);
            case "punishment", "anti_cheat_punishment" -> antiCheatPunishment;
            default -> "UNKNOWN";
        };
    }

    /**
     * Generates a readable key-value snapshot of all current policy settings.
     * @return map of policy keys to their current values
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("block_damage_allowed", blockDamageAllowed);
        map.put("fire_spread_allowed", fireSpreadAllowed);
        map.put("pvp_skills_allowed", pvpSkillsAllowed);
        map.put("friendly_fire_allowed", friendlyFireAllowed);
        map.put("custom_flight_allowed", customFlightAllowed);
        map.put("external_audio_streams_allowed", externalAudioStreamsAllowed);
        map.put("max_skill_damage_cap", maxSkillDamageCap);
        map.put("max_projectiles_per_player", maxProjectilesPerPlayer);
        map.put("max_summons_per_player", maxSummonsPerPlayer);
        map.put("anti_cheat_enabled", antiCheatEnabled);
        map.put("max_skill_casts_per_second", maxSkillCastsPerSecond);
        map.put("max_target_reach_distance", maxTargetReachDistance);
        map.put("anti_cheat_punishment", antiCheatPunishment);
        return map;
    }

    public boolean isBlockDamageAllowed() { return blockDamageAllowed; }
    public boolean isFireSpreadAllowed() { return fireSpreadAllowed; }
    public boolean isPvpSkillsAllowed() { return pvpSkillsAllowed; }
    public boolean isFriendlyFireAllowed() { return friendlyFireAllowed; }
    public boolean isCustomFlightAllowed() { return customFlightAllowed; }
    public boolean isExternalAudioStreamsAllowed() { return externalAudioStreamsAllowed; }
    public double getMaxSkillDamageCap() { return maxSkillDamageCap; }
    public int getMaxProjectilesPerPlayer() { return maxProjectilesPerPlayer; }
    public int getMaxSummonsPerPlayer() { return maxSummonsPerPlayer; }
    public boolean isAntiCheatEnabled() { return antiCheatEnabled; }
    public int getMaxSkillCastsPerSecond() { return maxSkillCastsPerSecond; }
    public double getMaxTargetReachDistance() { return maxTargetReachDistance; }
    public String getAntiCheatPunishment() { return antiCheatPunishment; }
}

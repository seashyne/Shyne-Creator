package seashyne.shynecore.admin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Server-authoritative anti-cheat and exploit protection system.
 * Validates skill cast frequency, reach distance, damage caps, and punishes repeat offenders.
 * Pure logic decoupled from Minecraft classes for full loader-neutral testing.
 */
public final class ShyneAntiCheat {
    public record ValidationResult(boolean allowed, String reason) {
        public static final ValidationResult ALLOWED = new ValidationResult(true, "");
        public static ValidationResult rejected(String reason) {
            return new ValidationResult(false, reason);
        }
    }

    private static final long MIN_CAST_INTERVAL_NANOS = 50_000_000L; // 50 ms minimum between casts
    private static final int STRIKE_KICK_THRESHOLD = 8;

    private static final Map<UUID, Long> LAST_CAST_NANOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CASTS_IN_WINDOW = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> WINDOW_START_NANOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> STRIKES = new ConcurrentHashMap<>();
    private static final AtomicInteger TOTAL_BLOCKED_PACKETS = new AtomicInteger(0);

    private static final Logger LOGGER = Logger.getLogger("ShyneAntiCheat");

    private ShyneAntiCheat() {}

    /**
     * Validates an incoming skill cast from a player.
     * Checks rate limits, minimum intervals, and records strikes on violation.
     */
    public static ValidationResult checkSkillCast(UUID uuid, String playerName, Runnable kickAction) {
        ShyneServerPolicy policy = ShyneServerPolicy.get();
        if (!policy.isAntiCheatEnabled()) return ValidationResult.ALLOWED;

        long now = System.nanoTime();

        boolean shouldCancel = "CANCEL_ACTION".equalsIgnoreCase(policy.getAntiCheatPunishment())
                            || "KICK".equalsIgnoreCase(policy.getAntiCheatPunishment());

        // 1. Minimum interval check (blocks packet flood macros)
        Long lastCast = LAST_CAST_NANOS.get(uuid);
        if (lastCast != null && now - lastCast < MIN_CAST_INTERVAL_NANOS) {
            recordViolation(uuid, playerName, "packet_macro_spam (cast < 50ms)", kickAction);
            if (shouldCancel) return ValidationResult.rejected("rate_limited");
        }

        // 2. Rolling window rate limiter
        int maxCasts = policy.getMaxSkillCastsPerSecond();
        if (maxCasts > 0) {
            Long windowStart = WINDOW_START_NANOS.get(uuid);
            if (windowStart == null || now - windowStart > 1_000_000_000L) {
                WINDOW_START_NANOS.put(uuid, now);
                CASTS_IN_WINDOW.put(uuid, 1);
            } else {
                int currentCasts = CASTS_IN_WINDOW.getOrDefault(uuid, 0) + 1;
                CASTS_IN_WINDOW.put(uuid, currentCasts);
                if (currentCasts > maxCasts) {
                    recordViolation(uuid, playerName, "cast_rate_exceeded (" + currentCasts + " casts/sec)", kickAction);
                    if (shouldCancel) return ValidationResult.rejected("rate_limited");
                }
            }
        }

        LAST_CAST_NANOS.put(uuid, now);
        return ValidationResult.ALLOWED;
    }

    /**
     * Checks whether a distance squared is within the allowed reach threshold.
     * If anti-cheat is disabled or maxTargetReachDistance <= 0, reach is UNLIMITED.
     */
    public static boolean isDistanceAllowed(double distSqr) {
        ShyneServerPolicy policy = ShyneServerPolicy.get();
        if (!policy.isAntiCheatEnabled()) return true;
        if ("LOG_ONLY".equalsIgnoreCase(policy.getAntiCheatPunishment())) return true;
        double maxReach = policy.getMaxTargetReachDistance();
        if (maxReach <= 0.0) return true;
        return distSqr <= maxReach * maxReach;
    }

    /**
     * Clamps skill or projectile damage according to server policy.
     * If maxSkillDamageCap <= 0, damage is UNLIMITED (no cap applied).
     */
    public static double clampDamage(double originalDamage) {
        double cap = ShyneServerPolicy.get().getMaxSkillDamageCap();
        if (cap <= 0.0) {
            return Math.max(0.0, originalDamage);
        }
        return Math.max(0.0, Math.min(originalDamage, cap));
    }

    /**
     * Records a security violation strike against a player and executes configured punishment.
     */
    public static void recordViolation(UUID uuid, String playerName, String reason, Runnable kickAction) {
        TOTAL_BLOCKED_PACKETS.incrementAndGet();
        int currentStrikes = STRIKES.compute(uuid, (k, v) -> v == null ? 1 : v + 1);

        LOGGER.warning(() -> String.format("[AntiCheat] Suspicious action by '%s' (%s) - Reason: %s [Strikes: %d]",
            playerName, uuid, reason, currentStrikes));

        String punishment = ShyneServerPolicy.get().getAntiCheatPunishment();
        if ("KICK".equalsIgnoreCase(punishment) && currentStrikes >= STRIKE_KICK_THRESHOLD) {
            if (kickAction != null) {
                kickAction.run();
            }
            STRIKES.remove(uuid);
        }
    }

    public static int getStrikes(UUID uuid) {
        return STRIKES.getOrDefault(uuid, 0);
    }

    public static void resetStrikes(UUID uuid) {
        STRIKES.remove(uuid);
    }

    public static void resetAll() {
        STRIKES.clear();
        LAST_CAST_NANOS.clear();
        CASTS_IN_WINDOW.clear();
        WINDOW_START_NANOS.clear();
        TOTAL_BLOCKED_PACKETS.set(0);
    }

    public static int getTotalBlockedPackets() {
        return TOTAL_BLOCKED_PACKETS.get();
    }
}

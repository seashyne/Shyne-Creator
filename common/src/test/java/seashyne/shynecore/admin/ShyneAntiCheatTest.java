package seashyne.shynecore.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

final class ShyneAntiCheatTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ShyneServerPolicy.init(tempDir);
        ShyneAntiCheat.resetAll();
    }

    @Test
    void damageClampingRespectsServerPolicy() {
        // Negative damage should clamp to 0.0
        assertEquals(0.0, ShyneAntiCheat.clampDamage(-25.0));

        // In default UNLIMITED mode (cap <= 0.0), huge damage numbers are completely un-clamped
        assertEquals(150.0, ShyneAntiCheat.clampDamage(150.0));
        assertEquals(9999.0, ShyneAntiCheat.clampDamage(9999.0));
        assertEquals(999_999.0, ShyneAntiCheat.clampDamage(999_999.0));
        assertEquals(10_000_000.0, ShyneAntiCheat.clampDamage(10_000_000.0));

        // When admin sets custom cap dynamically, clampDamage immediately adheres
        ShyneServerPolicy.get().set("max_damage", "200.0");
        assertEquals(200.0, ShyneAntiCheat.clampDamage(250.0));
        assertEquals(180.0, ShyneAntiCheat.clampDamage(180.0));

        // When set back to unlimited or none, cap is removed
        ShyneServerPolicy.get().set("max_damage", "unlimited");
        assertEquals(999_999.0, ShyneAntiCheat.clampDamage(999_999.0));
    }

    @Test
    void strikeTrackingAndResetLifecycle() {
        UUID playerUuid = UUID.randomUUID();
        assertEquals(0, ShyneAntiCheat.getStrikes(playerUuid));
        assertEquals(0, ShyneAntiCheat.getTotalBlockedPackets());

        ShyneAntiCheat.recordViolation(playerUuid, "TestPlayer", "macro_spam", null);
        assertEquals(1, ShyneAntiCheat.getStrikes(playerUuid));
        assertEquals(1, ShyneAntiCheat.getTotalBlockedPackets());

        ShyneAntiCheat.resetStrikes(playerUuid);
        assertEquals(0, ShyneAntiCheat.getStrikes(playerUuid));

        ShyneAntiCheat.resetAll();
        assertEquals(0, ShyneAntiCheat.getTotalBlockedPackets());
    }

    @Test
    void reachValidationChecksDistanceAndPolicy() {
        // In default open mode (anti-cheat disabled and unlimited reach), long distances are allowed
        assertTrue(ShyneAntiCheat.isDistanceAllowed(10.0 * 10.0));
        assertTrue(ShyneAntiCheat.isDistanceAllowed(200.0 * 200.0));
        assertTrue(ShyneAntiCheat.isDistanceAllowed(1000.0 * 1000.0));

        // When admin enables anti-cheat, configures CANCEL_ACTION punishment and custom reach
        ShyneServerPolicy.get().set("anti_cheat", "true");
        ShyneServerPolicy.get().set("punishment", "CANCEL_ACTION");
        ShyneServerPolicy.get().set("max_reach", "24.0");

        assertTrue(ShyneAntiCheat.isDistanceAllowed(10.0 * 10.0));
        assertTrue(ShyneAntiCheat.isDistanceAllowed(24.0 * 24.0));
        assertFalse(ShyneAntiCheat.isDistanceAllowed(25.0 * 25.0));

        // When anti-cheat reach is set to unlimited, any distance is allowed
        ShyneServerPolicy.get().set("max_reach", "unlimited");
        assertTrue(ShyneAntiCheat.isDistanceAllowed(1000.0 * 1000.0));

        // When anti-cheat is disabled entirely, any distance is allowed
        ShyneServerPolicy.get().set("anti_cheat", "false");
        assertTrue(ShyneAntiCheat.isDistanceAllowed(1000.0 * 1000.0));
    }

    @Test
    void violationKicksPlayerWhenThresholdExceededAndConfigured() {
        UUID playerUuid = UUID.randomUUID();
        ShyneServerPolicy.get().set("punishment", "KICK");
        AtomicBoolean kicked = new AtomicBoolean(false);
        Runnable kickCallback = () -> kicked.set(true);

        // 7 strikes should not trigger kick yet (threshold is 8)
        for (int i = 0; i < 7; i++) {
            ShyneAntiCheat.recordViolation(playerUuid, "Cheater", "strike_test", kickCallback);
            assertFalse(kicked.get(), "Should not kick before 8 strikes");
        }

        // 8th strike triggers kick
        ShyneAntiCheat.recordViolation(playerUuid, "Cheater", "strike_test", kickCallback);
        assertTrue(kicked.get(), "Should kick on 8th strike");
        assertEquals(0, ShyneAntiCheat.getStrikes(playerUuid), "Strikes should be reset after kick");
    }

    @Test
    void skillCastRateLimiterBehavesAccordingToPunishment() {
        UUID playerUuid = UUID.randomUUID();
        // In default (anti-cheat disabled), rapid cast is freely allowed
        ShyneAntiCheat.ValidationResult first = ShyneAntiCheat.checkSkillCast(playerUuid, "FastPlayer", null);
        assertTrue(first.allowed(), "First cast allowed");

        ShyneAntiCheat.ValidationResult second = ShyneAntiCheat.checkSkillCast(playerUuid, "FastPlayer", null);
        assertTrue(second.allowed(), "Default allows rapid cast without blocking");
        assertEquals(0, ShyneAntiCheat.getTotalBlockedPackets());

        // When admin enables anti_cheat in LOG_ONLY mode, rapid consecutive cast is logged as violation but allowed
        ShyneServerPolicy.get().set("anti_cheat", "true");
        ShyneServerPolicy.get().set("punishment", "LOG_ONLY");
        ShyneAntiCheat.checkSkillCast(playerUuid, "FastPlayer", null); // Initial tracked cast
        ShyneAntiCheat.checkSkillCast(playerUuid, "FastPlayer", null); // Rapid follow-up cast (< 50ms)
        assertTrue(ShyneAntiCheat.getTotalBlockedPackets() >= 1);

        // When admin sets punishment to CANCEL_ACTION, rapid cast (< 50ms) is rejected
        ShyneServerPolicy.get().set("punishment", "CANCEL_ACTION");
        ShyneAntiCheat.ValidationResult spam = ShyneAntiCheat.checkSkillCast(playerUuid, "FastPlayer", null);
        assertFalse(spam.allowed(), "CANCEL_ACTION mode must reject sub-50ms macro cast");
        assertEquals("rate_limited", spam.reason());
    }
}

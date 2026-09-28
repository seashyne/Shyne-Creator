package seashyne.shynecore.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ShyneServerPolicyTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ShyneServerPolicy.init(tempDir);
    }

    @Test
    void defaultPolicyIsOpenAndFreeForCreators() {
        ShyneServerPolicy policy = ShyneServerPolicy.get();
        // Fully unrestricted & open by default: NO limits, NO caps, NO restrictions
        assertTrue(policy.isBlockDamageAllowed(), "Block damage must be enabled by default for creator freedom");
        assertTrue(policy.isFireSpreadAllowed(), "Fire spread must be enabled by default");
        assertTrue(policy.isPvpSkillsAllowed(), "PvP skills should be enabled by default");
        assertTrue(policy.isFriendlyFireAllowed(), "Friendly fire should be enabled by default for open combat");
        assertTrue(policy.isCustomFlightAllowed(), "Custom flight should be enabled by default");
        assertTrue(policy.isExternalAudioStreamsAllowed(), "External audio streams allowed by default");
        assertEquals(-1.0, policy.getMaxSkillDamageCap(), "Damage cap is unlimited (<= 0) by default");
        assertEquals(-1, policy.getMaxProjectilesPerPlayer(), "Projectile quota is unlimited (<= 0) by default");
        assertEquals(-1, policy.getMaxSummonsPerPlayer(), "Summon quota is unlimited (<= 0) by default");
        assertFalse(policy.isAntiCheatEnabled(), "Anti-cheat disabled by default for true creator freedom");
        assertEquals(-1, policy.getMaxSkillCastsPerSecond(), "Cast rate is unlimited (<= 0) by default");
        assertEquals(-1.0, policy.getMaxTargetReachDistance(), "Reach distance is unlimited (<= 0) by default");
        assertEquals("LOG_ONLY", policy.getAntiCheatPunishment(), "Default punishment is LOG_ONLY");
    }

    @Test
    void livePolicyUpdatesWorkCorrectly() {
        ShyneServerPolicy policy = ShyneServerPolicy.get();

        // Admins can restrict block damage if they want to protect their world
        assertTrue(policy.set("block_damage", "false"));
        assertFalse(policy.isBlockDamageAllowed());

        assertTrue(policy.set("max_damage", "750.5"));
        assertEquals(750.5, policy.getMaxSkillDamageCap(), 0.001);
        assertEquals("750.5", policy.getFormatted("max_damage"));

        assertTrue(policy.set("max_damage", "unlimited"));
        assertEquals(-1.0, policy.getMaxSkillDamageCap());
        assertEquals("UNLIMITED", policy.getFormatted("max_damage"));

        // Various unlimited tokens
        assertTrue(policy.set("max_reach", "none"));
        assertEquals(-1.0, policy.getMaxTargetReachDistance());
        assertTrue(policy.set("max_projectiles", "-1"));
        assertEquals(-1, policy.getMaxProjectilesPerPlayer());
        assertTrue(policy.set("max_summons", "0"));
        assertEquals(-1, policy.getMaxSummonsPerPlayer());
        assertTrue(policy.set("max_casts_per_sec", "off"));
        assertEquals(-1, policy.getMaxSkillCastsPerSecond());

        // Arbitrary negative values like -1999 or -50 must be rejected as invalid
        assertFalse(policy.set("max_damage", "-1999"));
        assertFalse(policy.set("max_reach", "-50.0"));
        assertFalse(policy.set("max_projectiles", "-10"));
        assertFalse(policy.set("max_summons", "-99"));
        assertFalse(policy.set("max_casts_per_sec", "-5"));

        assertTrue(policy.set("max_projectiles", "40"));
        assertEquals(40, policy.getMaxProjectilesPerPlayer());
        assertEquals("40", policy.getFormatted("max_projectiles"));

        assertTrue(policy.set("punishment", "KICK"));
        assertEquals("KICK", policy.getAntiCheatPunishment());

        assertFalse(policy.set("unknown_setting_xyz", "true"));
        assertFalse(policy.set("punishment", "INVALID_PUNISHMENT"));
    }

    @Test
    void snapshotContainsAllExpectedKeys() {
        ShyneServerPolicy policy = ShyneServerPolicy.get();
        Map<String, Object> snapshot = policy.snapshot();

        assertTrue(snapshot.containsKey("block_damage_allowed"));
        assertTrue(snapshot.containsKey("max_skill_damage_cap"));
        assertTrue(snapshot.containsKey("anti_cheat_enabled"));
        assertTrue(snapshot.containsKey("anti_cheat_punishment"));
        assertEquals(13, snapshot.size());
    }
}

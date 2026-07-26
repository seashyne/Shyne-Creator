package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ShyneApiStandardTest {
    @Test
    void omittedApiTracksLatestAndOldStandardsAreRejected() {
        ShyneApiStandard.Selection automatic = ShyneApiStandard.select(null);
        assertEquals(ShyneApiStandard.LATEST, automatic.version());
        assertTrue(automatic.automatic());

        ShyneApiStandard.Selection explicit = ShyneApiStandard.select("2.0");
        assertEquals("2.0", explicit.version());
        assertFalse(explicit.automatic());

        assertThrows(IllegalArgumentException.class, () -> ShyneApiStandard.select("1.3"));
        assertThrows(IllegalArgumentException.class, () -> ShyneApiStandard.modulesFor("1.3"));
    }

    @Test
    void moduleRequirementsSupportRanges() {
        assertTrue(ShyneApiStandard.supports("2.0", "render", ">=1.1"));
        assertTrue(ShyneApiStandard.supports("2.0", "render", ">=1.2"));
        assertTrue(ShyneApiStandard.supports("2.0", "render", ">=1.3"));
        assertTrue(ShyneApiStandard.supports("2.0", "events", ">=2.0"));
        assertTrue(ShyneApiStandard.supports("2.0", "transform", "^1.0"));
        assertTrue(ShyneApiStandard.supports("2.0", "behavior", ">=2.0"));
        assertTrue(ShyneApiStandard.supports("2.0", "easy", ">=1.0"));
        assertTrue(ShyneApiStandard.supports("2.0", "rig", ">=1.3"));
        assertTrue(ShyneApiStandard.supports("2.0", "scheduler", "^1.1"));
        assertFalse(ShyneApiStandard.supports("2.0", "scheduler", ">=2.0"));
        assertThrows(IllegalArgumentException.class,
            () -> ShyneApiStandard.validateRequirements("2.0", Map.of("render", ">=2.0")));
    }
}

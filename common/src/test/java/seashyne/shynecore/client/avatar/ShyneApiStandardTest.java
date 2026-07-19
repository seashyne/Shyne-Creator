package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ShyneApiStandardTest {
    @Test
    void omittedApiTracksLatestWhileLegacyFieldStaysStable() {
        ShyneApiStandard.Selection automatic = ShyneApiStandard.select(null, null);
        assertEquals(ShyneApiStandard.LATEST, automatic.version());
        assertTrue(automatic.automatic());

        ShyneApiStandard.Selection legacy = ShyneApiStandard.select(null, 1);
        assertEquals("1.0", legacy.version());
        assertFalse(legacy.automatic());
    }

    @Test
    void moduleRequirementsSupportRanges() {
        assertTrue(ShyneApiStandard.supports("1.1", "render", ">=1.1"));
        assertTrue(ShyneApiStandard.supports("1.1", "scheduler", "^1.1"));
        assertFalse(ShyneApiStandard.supports("1.0", "scheduler", "*"));
        assertThrows(IllegalArgumentException.class,
            () -> ShyneApiStandard.validateRequirements("1.1", Map.of("render", ">=2.0")));
    }
}

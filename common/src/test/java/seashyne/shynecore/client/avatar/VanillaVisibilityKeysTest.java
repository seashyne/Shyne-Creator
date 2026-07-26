package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class VanillaVisibilityKeysTest {
    @Test
    void normalizesLegacyAndFiguraStyleAliases() {
        assertAll(
            () -> assertEquals(VanillaVisibilityKeys.HELD_ITEMS, VanillaVisibilityKeys.normalize("held-item")),
            () -> assertEquals(VanillaVisibilityKeys.LEFT_ITEM, VanillaVisibilityKeys.normalize("left hand item")),
            () -> assertEquals(VanillaVisibilityKeys.MAIN_HAND, VanillaVisibilityKeys.normalize("mainhand")),
            () -> assertEquals(VanillaVisibilityKeys.HEAD_ITEM, VanillaVisibilityKeys.normalize("skull")),
            () -> assertEquals("CHESTPLATE", VanillaVisibilityKeys.normalize("body armor"))
        );
    }

    @Test
    void findsNonCanonicalRemoteSnapshotKeys() {
        Map<String, Boolean> legacy = Map.of("left-held-item", false, "cape", true);
        assertEquals(Boolean.FALSE, VanillaVisibilityKeys.find(legacy, VanillaVisibilityKeys.LEFT_ITEM));
        assertEquals(Boolean.TRUE, VanillaVisibilityKeys.find(legacy, "CAPE"));
    }

    @Test
    void replaceFallbackOnlyChangesPlayerGroup() {
        assertFalse(VanillaVisibilityKeys.isVisible(Map.of(), true, VanillaVisibilityKeys.PLAYER));
        assertTrue(VanillaVisibilityKeys.isVisible(Map.of(), true, "ELYTRA"));
        assertTrue(VanillaVisibilityKeys.isVisible(Map.of(VanillaVisibilityKeys.PLAYER, true), true, VanillaVisibilityKeys.PLAYER));
    }

    @Test
    void effectiveVisibilityIncludesPlayerAndCapturedPartState() {
        assertFalse(VanillaVisibilityKeys.effectiveVisible(Map.of("PLAYER", false, "HAT", true), false, "HAT", true));
        assertFalse(VanillaVisibilityKeys.effectiveVisible(Map.of("PLAYER", true, "HAT", false), false, "HAT", true));
        assertFalse(VanillaVisibilityKeys.effectiveVisible(Map.of("PLAYER", true, "HAT", true), false, "HAT", false));
        assertTrue(VanillaVisibilityKeys.effectiveVisible(Map.of("PLAYER", true, "HAT", true), false, "HAT", true));
    }
}

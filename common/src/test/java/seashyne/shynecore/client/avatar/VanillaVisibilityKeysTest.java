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

    @Test
    void expandsCompoundPartsAndOuterLayers() {
        assertAll(
            () -> assertEquals(java.util.List.of("RIGHT_ARM", "LEFT_ARM", "RIGHT_SLEEVE", "LEFT_SLEEVE"), VanillaVisibilityKeys.expand("arms")),
            () -> assertEquals(java.util.List.of("RIGHT_LEG", "LEFT_LEG", "RIGHT_PANTS", "LEFT_PANTS"), VanillaVisibilityKeys.expand("legs")),
            () -> assertEquals(java.util.List.of("BODY", "JACKET"), VanillaVisibilityKeys.expand("torso")),
            () -> assertEquals(java.util.List.of("RIGHT_ARM", "RIGHT_SLEEVE"), VanillaVisibilityKeys.expand("right_arm")),
            () -> assertEquals(java.util.List.of("LEFT_LEG", "LEFT_PANTS"), VanillaVisibilityKeys.expand("left_leg")),
            () -> assertEquals(java.util.List.of("HEAD", "HAT"), VanillaVisibilityKeys.expand("head"))
        );
    }
}

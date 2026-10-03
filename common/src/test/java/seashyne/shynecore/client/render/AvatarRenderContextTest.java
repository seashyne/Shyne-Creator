package seashyne.shynecore.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarRenderContextTest {
    @Test
    void normalizesOnlyStablePublicContexts() {
        assertEquals(AvatarRenderContext.FIRST_PERSON, AvatarRenderContext.normalize("first-person"));
        assertEquals(AvatarRenderContext.MINECRAFT_GUI, AvatarRenderContext.normalize("minecraft gui"));
        assertEquals(AvatarRenderContext.PORTRAIT, AvatarRenderContext.normalize("portrait"));
        assertEquals(AvatarRenderContext.SKULL, AvatarRenderContext.normalize("skull"));
        assertEquals(AvatarRenderContext.HELD_ITEM, AvatarRenderContext.normalize("held-item"));
        assertEquals(AvatarRenderContext.OTHER, AvatarRenderContext.normalize("unknown_mod_context"));
        assertTrue(AvatarRenderContext.worldSpace("render"));
        assertFalse(AvatarRenderContext.worldSpace("minecraft_gui"));
        assertFalse(AvatarRenderContext.worldSpace("portrait"));
    }

    @Test
    void pushAndPopContextOverridesCurrentContext() {
        try {
            AvatarRenderContext.pushContext(AvatarRenderContext.PORTRAIT);
            assertEquals(AvatarRenderContext.PORTRAIT, AvatarRenderContext.currentOverride());
            AvatarRenderContext.pushContext(AvatarRenderContext.SKULL);
            assertEquals(AvatarRenderContext.SKULL, AvatarRenderContext.currentOverride());
            AvatarRenderContext.popContext();
            assertEquals(AvatarRenderContext.PORTRAIT, AvatarRenderContext.currentOverride());
        } finally {
            AvatarRenderContext.popContext();
        }
        assertEquals(AvatarRenderContext.OTHER, AvatarRenderContext.currentOverride());
    }
}

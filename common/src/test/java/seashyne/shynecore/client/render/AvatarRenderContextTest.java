package seashyne.shynecore.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarRenderContextTest {
    @Test
    void normalizesOnlyStablePublicContexts() {
        assertEquals(AvatarRenderContext.FIRST_PERSON, AvatarRenderContext.normalize("first-person"));
        assertEquals(AvatarRenderContext.MINECRAFT_GUI, AvatarRenderContext.normalize("minecraft gui"));
        assertEquals(AvatarRenderContext.OTHER, AvatarRenderContext.normalize("unknown_mod_context"));
        assertTrue(AvatarRenderContext.worldSpace("render"));
        assertFalse(AvatarRenderContext.worldSpace("minecraft_gui"));
    }
}

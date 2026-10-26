package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarFirstPersonArmTest {
    @Test
    void avatarStateSupportsFirstPersonArmToggle() {
        AvatarState state = new AvatarState("test", "test", Path.of("test"), false, Set.of(), Set.of());
        assertTrue(state.firstPersonArm());

        state.setFirstPersonArm(false);
        assertFalse(state.firstPersonArm());

        state.setFirstPersonArm(true);
        assertTrue(state.firstPersonArm());
    }

    @Test
    void avatarStateSupportsShadowRadiusConfiguration() {
        AvatarState state = new AvatarState("test", "test", Path.of("test"), false, Set.of(), Set.of());
        assertEquals(-1.0f, state.shadowRadius());

        state.setShadowRadius(0.5f);
        assertEquals(0.5f, state.shadowRadius());

        state.setShadowRadius(0.0f);
        assertEquals(0.0f, state.shadowRadius());
    }
}

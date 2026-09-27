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
}

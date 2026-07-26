package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarPhysicsControllerTest {
    @Test
    void presetRootControlsItsDescendantChainWithoutReplacingLuaLayer() {
        BbBoneDefinition tail = new BbBoneDefinition(
            "tail", "Tail", null, null, "Body", "tail", List.of(), "tail", 0,
            0, 12, 2, 0, 0, 0, true, List.of("tip")
        );
        BbBoneDefinition tip = new BbBoneDefinition(
            "tip", "Tip", "Tail", "tail", "", "", List.of(), "none", 0,
            0, 12, 6, 0, 0, 0, true, List.of()
        );
        BbModelDefinition model = new BbModelDefinition(
            "test", "test", "test", Path.of("test.bbmodel"), 1, 16, 16, "",
            List.of(), List.of(tail, tip), List.of(), List.of()
        );
        AvatarState state = new AvatarState("test", "test", Path.of("test"), true, Set.of(), Set.of());
        state.clearSnapshotDirty();
        state.getPart("model.Tail").setAdditiveRotation(3f, 4f, 5f);
        AvatarPhysicsController controller = new AvatarPhysicsController(model);

        controller.tick(new AvatarPhysicsController.Signals(0, 0, 0, 0, 0, true, false, 0), state);
        controller.tick(new AvatarPhysicsController.Signals(0.20, 0.05, 0, 8, 1, false, false, 1), state);

        assertAll(
            () -> assertTrue(controller.active()),
            () -> assertEquals(2, controller.nodeCount()),
            () -> assertEquals(List.of("model.Tail", "model.Tail.Tip"), controller.controlledPaths()),
            () -> assertTrue(state.getPart("model.Tail").additiveRotationLayers().containsKey(AvatarPhysicsController.ROTATION_LAYER)),
            () -> assertNotEquals(3f, state.getPart("model.Tail").additiveRotX(), 0.0001f),
            () -> assertTrue(state.isPoseDirty()),
            () -> assertFalse(state.isSnapshotDirty())
        );
    }

    @Test
    void additiveLayersComposeAndCanBeClearedIndependently() {
        AvatarPartState part = new AvatarPartState();
        part.setAdditiveRotation(1, 2, 3);
        part.setAdditiveRotationLayer("physics", 4, 5, 6);
        part.setAdditiveRotationLayer("constraint", -1, -2, -3);

        assertAll(
            () -> assertEquals(4f, part.additiveRotX()),
            () -> assertEquals(5f, part.additiveRotY()),
            () -> assertEquals(6f, part.additiveRotZ()),
            () -> assertTrue(part.clearAdditiveRotationLayer("physics")),
            () -> assertEquals(0f, part.additiveRotX()),
            () -> assertEquals(0f, part.additiveRotY()),
            () -> assertEquals(0f, part.additiveRotZ())
        );
    }
}

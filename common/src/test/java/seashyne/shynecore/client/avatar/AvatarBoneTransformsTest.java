package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AvatarBoneTransformsTest {
    @Test
    void childPivotInheritsParentRotation() {
        BbBoneDefinition parent = new BbBoneDefinition("parent", "parent", null, null, "", "", List.of(), 0, 0, 0, 0, 0, 0, 90, List.of("child"));
        BbBoneDefinition child = new BbBoneDefinition("child", "child", "parent", "parent", "", "", List.of(), 0, 2, 0, 0, 0, 0, 0, List.of());
        BbModelDefinition model = model(parent, child);
        AvatarState state = state();

        var transform = AvatarBoneTransforms.resolve(model, state, child);

        assertEquals(0f, transform.x(), 0.0001f);
        assertEquals(2f, transform.y(), 0.0001f);
        assertEquals(0f, transform.z(), 0.0001f);
        assertEquals(90f, transform.rotationZ(), 0.0001f);
    }

    @Test
    void luaScaleOnParentMovesChildPivot() {
        BbBoneDefinition parent = new BbBoneDefinition("parent", "parent", null, null, "", "", List.of(), 0, 0, 0, 0, 0, 0, 0, List.of("child"));
        BbBoneDefinition child = new BbBoneDefinition("child", "child", "parent", "parent", "", "", List.of(), 0, 2, 0, 0, 0, 0, 0, List.of());
        BbModelDefinition model = model(parent, child);
        AvatarState state = state();
        state.getPart("model.parent").setScale(2, 1, 1);

        assertEquals(4f, AvatarBoneTransforms.resolve(model, state, child).x(), 0.0001f);
    }

    private static AvatarState state() {
        return new AvatarState("test", "test", Path.of("test"), true, Set.of(), Set.of());
    }

    private static BbModelDefinition model(BbBoneDefinition... bones) {
        return new BbModelDefinition("test", "test", "test", Path.of("test.bbmodel"), 1, 16, 16, "", List.of(), List.of(bones), List.of(), List.of());
    }
}

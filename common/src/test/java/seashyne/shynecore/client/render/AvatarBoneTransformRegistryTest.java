package seashyne.shynecore.client.render;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarBoneTransformRegistryTest {
    @AfterEach
    void clear() { AvatarBoneTransformRegistry.clear(); }

    @Test
    void keepsContextSnapshotsButWorldLookupNeverReturnsGuiCoordinates() {
        UUID entity = UUID.randomUUID();
        AvatarBoneTransformRegistry.publish(entity, "avatar", AvatarRenderContext.RENDER,
            Map.of("model.Head", transform(10, true, AvatarRenderContext.RENDER)));
        AvatarBoneTransformRegistry.publish(entity, "avatar", AvatarRenderContext.MINECRAFT_GUI,
            Map.of("model.Head", transform(99, false, AvatarRenderContext.MINECRAFT_GUI)));

        assertEquals(99, AvatarBoneTransformRegistry.find(entity, "avatar", "MODEL.HEAD", AvatarRenderContext.MINECRAFT_GUI).x());
        assertEquals(10, AvatarBoneTransformRegistry.findWorld(entity, "avatar", "model.head").x());
        assertEquals(2, AvatarBoneTransformRegistry.snapshotCount());
    }

    @Test
    void matricesAreDefensivelyCopiedAndEntityCleanupIsScoped() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var source = transform(3, true, AvatarRenderContext.RENDER);
        AvatarBoneTransformRegistry.publish(first, "avatar", AvatarRenderContext.RENDER, Map.of("model.Head", source));
        AvatarBoneTransformRegistry.publish(second, "avatar", AvatarRenderContext.RENDER, Map.of("model.Head", source));

        var captured = AvatarBoneTransformRegistry.findWorld(first, "avatar", "model.Head");
        float[] matrix = captured.matrix();
        matrix[12] = 500;
        assertEquals(3, AvatarBoneTransformRegistry.findWorld(first, "avatar", "model.Head").matrix()[12]);

        AvatarBoneTransformRegistry.clearEntity(first);
        assertNull(AvatarBoneTransformRegistry.findWorld(first, "avatar", "model.Head"));
        assertNotNull(AvatarBoneTransformRegistry.findWorld(second, "avatar", "model.Head"));
    }

    @Test
    void worldLookupDoesNotKeepAHiddenAvatarsLastPoseForever() throws Exception {
        UUID entity = UUID.randomUUID();
        AvatarBoneTransformRegistry.publish(entity, "avatar", AvatarRenderContext.RENDER,
            Map.of("model.Head", transform(3, true, AvatarRenderContext.RENDER)));

        assertNotNull(AvatarBoneTransformRegistry.findWorld(entity, "avatar", "model.Head"));
        Thread.sleep(300L);
        assertNull(AvatarBoneTransformRegistry.findWorld(entity, "avatar", "model.Head"));
    }

    private static AvatarBoneTransformRegistry.BoneTransform transform(float x, boolean world, String context) {
        float[] matrix = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, 2, 3, 1};
        return new AvatarBoneTransformRegistry.BoneTransform(matrix, x, 2, 3, 0, 0, 0, 1, 1, 1, true, world, context);
    }
}

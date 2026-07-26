package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RemoteAvatarResourceBudgetTest {
    @Test
    void balancedAcceptsAnOrdinaryAvatar() {
        RemoteAvatarResourceBudget.Metrics metrics = metrics(
            3_200, 2_000, 40, 120, 12, 16, 4_000,
            4, 1_024, 8L * 1_048_576L, 2L * 1_048_576L
        );

        RemoteAvatarResourceBudget.Decision result = RemoteAvatarResourceBudget.evaluate(
            RemoteAvatarResourceBudget.Preset.BALANCED, metrics
        );

        assertTrue(result.allowed());
        assertNull(result.resource());
        assertSame(metrics, result.metrics());
    }

    @Test
    void performanceRejectsBeforeTextureMemoryCanReachTheGpu() {
        RemoteAvatarResourceBudget.Decision result = RemoteAvatarResourceBudget.evaluate(
            RemoteAvatarResourceBudget.Preset.PERFORMANCE,
            metrics(1_000, 500, 20, 40, 4, 4, 100, 2, 2_048, 20L * 1_048_576L, 1_048_576L)
        );

        assertFalse(result.allowed());
        assertEquals(RemoteAvatarResourceBudget.Resource.TEXTURE_MEMORY, result.resource());
        assertEquals(16L * 1_048_576L, result.limit());
    }

    @Test
    void balancedRejectsGeometryThatOnlyExceedsTriangleBudget() {
        RemoteAvatarResourceBudget.Decision result = RemoteAvatarResourceBudget.evaluate(
            RemoteAvatarResourceBudget.Preset.BALANCED,
            metrics(20_001, 100, 10, 10, 1, 1, 10, 1, 64, 16_384, 1_024)
        );

        assertFalse(result.allowed());
        assertEquals("triangles", result.resourceId());
        assertEquals(20_001L, result.actual());
        assertEquals(20_000L, result.limit());
    }

    @Test
    void unlimitedStillKeepsHardAllocationCeilings() {
        RemoteAvatarResourceBudget.Decision result = RemoteAvatarResourceBudget.evaluate(
            RemoteAvatarResourceBudget.Preset.UNLIMITED,
            metrics(1, 1, 1, 1, 1, 1, 1, 1, 16_384, 1, 1)
        );

        assertFalse(result.allowed());
        assertEquals(RemoteAvatarResourceBudget.Resource.TEXTURE_DIMENSION, result.resource());
        assertEquals(8_192L, result.limit());
    }

    @Test
    void unknownPresetFallsBackToBalanced() {
        assertEquals(RemoteAvatarResourceBudget.Preset.PERFORMANCE, RemoteAvatarResourceBudget.Preset.fromId(" Performance "));
        assertEquals(RemoteAvatarResourceBudget.Preset.BALANCED, RemoteAvatarResourceBudget.Preset.fromId("not-a-preset"));
        assertEquals(RemoteAvatarResourceBudget.Preset.BALANCED, RemoteAvatarResourceBudget.Preset.fromId(null));
    }

    private static RemoteAvatarResourceBudget.Metrics metrics(
        long triangles, long meshVertices, long bones, long cubes, long meshes,
        long animations, long keyframes, long textures, long maxTextureDimension,
        long textureMemoryBytes, long textureTransferBytes
    ) {
        return new RemoteAvatarResourceBudget.Metrics(
            triangles, meshVertices, bones, cubes, meshes, animations, keyframes,
            textures, maxTextureDimension, textureMemoryBytes, textureTransferBytes
        );
    }
}

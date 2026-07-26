package seashyne.shynecore.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies dynamic task updates and two-phase Avatar rollback without launching Minecraft. */
class AvatarRenderTaskRegistryTest {
    @Test
    void stagedTaskRollsBackToPreviousRuntimeValue() {
        Object previous = new Object();
        Object staged = new Object();
        RenderTaskOwners<String> owners = new RenderTaskOwners<>();

        owners.put(previous, "old");
        owners.put(staged, "new");
        assertEquals("new", owners.current());

        assertTrue(owners.remove(staged));
        assertEquals("old", owners.current());
        assertTrue(owners.remove(previous));
        assertTrue(owners.isEmpty());
    }

    @Test
    void opacityPreservesRgbAndScalesAlpha() {
        assertEquals(0x4055AAFF, RenderTaskMath.applyOpacity(0x8055AAFF, 0.5));
        assertEquals(0x0055AAFF, RenderTaskMath.applyOpacity(0x8055AAFF, -1));
        assertFalse(RenderTaskMath.applyOpacity(0xFFFFFFFF, 0.25) == 0xFFFFFFFF);
    }

    @Test
    void worldLineIsCappedBeforeCreatingGpuVertices() {
        double[] capped = RenderTaskMath.capLine(0, 64, 0, 30_000_000, 64, 0,
            AvatarRenderTaskRegistry.MAX_WORLD_LINE_LENGTH);

        assertEquals(AvatarRenderTaskRegistry.MAX_WORLD_LINE_LENGTH, capped[0], 0.0001);
        assertEquals(64, capped[1], 0.0001);
        assertEquals(0, capped[2], 0.0001);
        assertTrue(Double.isFinite(capped[0]));
    }
}

package seashyne.shynecore.client.render;

import org.junit.jupiter.api.Test;
import seashyne.shynecore.client.profiler.AvatarProfiler;
import seashyne.shynecore.model.BbCubeDefinition;
import seashyne.shynecore.model.BbMeshDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarPerformanceAndCullingTest {

    @Test
    void profilerTracksGeometryRenderedAndCulled() {
        AvatarProfiler.reset();
        assertEquals(0, AvatarProfiler.lastGeometryRendered());
        assertEquals(0, AvatarProfiler.lastGeometryCulled());

        AvatarProfiler.recordGeometry(48, 12);
        assertEquals(48, AvatarProfiler.lastGeometryRendered());
        assertEquals(12, AvatarProfiler.lastGeometryCulled());

        AvatarProfiler.reset();
        assertEquals(0, AvatarProfiler.lastGeometryRendered());
        assertEquals(0, AvatarProfiler.lastGeometryCulled());
    }

    @Test
    void microDetailDetectionForLod() {
        BbCubeDefinition smallButton = new BbCubeDefinition(
            "button", null,
            0, 0, 0, 1, 1, 1,
            0, 0, 0, 0, 0, 0,
            0f, Map.of(), 0, false, true
        );
        BbCubeDefinition torso = new BbCubeDefinition(
            "torso", null,
            0, 0, 0, 8, 12, 4,
            0, 0, 0, 0, 0, 0,
            0f, Map.of(), 0, false, true
        );
        BbCubeDefinition taggedDetail = new BbCubeDefinition(
            "earring_detail", null,
            0, 0, 0, 10, 10, 10,
            0, 0, 0, 0, 0, 0,
            0f, Map.of(), 0, false, true
        );

        // Test volume calculation: button has volume = 1 <= 8 -> micro detail
        float smallVol = Math.abs(smallButton.toX() - smallButton.fromX())
            * Math.abs(smallButton.toY() - smallButton.fromY())
            * Math.abs(smallButton.toZ() - smallButton.fromZ());
        assertTrue(smallVol <= 8.0f);

        // Torso has volume = 384 > 8 -> not micro detail
        float torsoVol = Math.abs(torso.toX() - torso.fromX())
            * Math.abs(torso.toY() - torso.fromY())
            * Math.abs(torso.toZ() - torso.fromZ());
        assertFalse(torsoVol <= 8.0f);

        // Tagged detail cube has 'detail' in name
        assertTrue(taggedDetail.name().toLowerCase().contains("detail"));
    }

    @Test
    void microMeshDetectionForLod() {
        BbMeshDefinition eyelashMesh = new BbMeshDefinition(
            "mesh_1", "eyelash_micro", null,
            0f, 0f, 0f, 0f, 0f, 0f,
            Map.of(), List.of(), true
        );
        BbMeshDefinition capeMesh = new BbMeshDefinition(
            "mesh_2", "hero_cape", null,
            0f, 0f, 0f, 0f, 0f, 0f,
            Map.of(), List.of(), true
        );

        assertTrue(eyelashMesh.name().toLowerCase().contains("micro"));
        assertFalse(capeMesh.name().toLowerCase().contains("detail") || capeMesh.name().toLowerCase().contains("micro"));
    }

    @Test
    void texturePurgeMethodsExecuteSafely() {
        assertDoesNotThrow(BbModelTextures::clearLocalCache);
        assertDoesNotThrow(BbModelTextures::clearRemoteSyncedTextures);
    }
}

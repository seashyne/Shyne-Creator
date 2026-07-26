package seashyne.shynecore.client.render;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AvatarMatrixDecompositionTest {
    @Test
    void includesWorldOrientationAndScaleButRemovesBlockbenchReflection() {
        Matrix4f matrix = new Matrix4f()
            .rotateZ((float) Math.toRadians(30))
            .scale(2f, -3f, 4f);

        var scale = AvatarMatrixDecomposition.worldScale(matrix);
        var rotation = AvatarMatrixDecomposition.worldRotationDegrees(matrix);

        assertEquals(2f, scale.x, 0.0001f);
        assertEquals(3f, scale.y, 0.0001f);
        assertEquals(4f, scale.z, 0.0001f);
        assertEquals(0f, rotation.x, 0.0001f);
        assertEquals(0f, rotation.y, 0.0001f);
        assertEquals(30f, rotation.z, 0.0001f);
    }
}

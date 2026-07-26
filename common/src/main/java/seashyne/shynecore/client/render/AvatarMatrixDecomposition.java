package seashyne.shynecore.client.render;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Decomposes renderer matrices for the Lua world-transform API. */
public final class AvatarMatrixDecomposition {
    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    private AvatarMatrixDecomposition() {}

    public static Vector3f worldScale(Matrix4f matrix) {
        return matrix == null ? new Vector3f(1f) : matrix.getScale(new Vector3f());
    }

    /**
     * Returns a right-handed world rotation after removing scale and Shyne's
     * Blockbench Y reflection. This keeps the scalar API consistent with the
     * exact matrix used by native bone-bound render tasks.
     */
    public static Vector3f worldRotationDegrees(Matrix4f matrix) {
        if (matrix == null) return new Vector3f();
        Matrix3f basis = new Matrix3f(matrix);
        float sx = axisLength(basis.m00(), basis.m01(), basis.m02());
        float sy = axisLength(basis.m10(), basis.m11(), basis.m12());
        float sz = axisLength(basis.m20(), basis.m21(), basis.m22());
        basis.m00(basis.m00() / sx).m01(basis.m01() / sx).m02(basis.m02() / sx);
        basis.m10(basis.m10() / sy).m11(basis.m11() / sy).m12(basis.m12() / sy);
        basis.m20(basis.m20() / sz).m21(basis.m21() / sz).m22(basis.m22() / sz);
        if (basis.determinant() < 0f) basis.m10(-basis.m10()).m11(-basis.m11()).m12(-basis.m12());
        return basis.getNormalizedRotation(new Quaternionf()).getEulerAnglesXYZ(new Vector3f()).mul(RAD_TO_DEG);
    }

    private static float axisLength(float x, float y, float z) {
        return Math.max(0.000001f, (float) Math.sqrt(x * x + y * y + z * z));
    }
}

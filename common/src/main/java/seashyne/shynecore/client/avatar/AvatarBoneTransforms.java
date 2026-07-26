package seashyne.shynecore.client.avatar;

import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.HashSet;
import java.util.Set;

/** Resolves a bone pivot through its complete Blockbench parent hierarchy. */
public final class AvatarBoneTransforms {
    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    private AvatarBoneTransforms() {}

    public record WorldTransform(float x, float y, float z, float rotationX, float rotationY, float rotationZ) {}

    /**
     * Returns the bone pivot and rotation in avatar-model coordinates. Lua-controlled
     * position, rotation and scale are applied exactly as they are in the model renderer.
     */
    public static WorldTransform resolve(BbModelDefinition model, AvatarState state, BbBoneDefinition bone) {
        if (model == null || bone == null) return new WorldTransform(0f, 0f, 0f, 0f, 0f, 0f);
        double[] matrix = buildMatrix(model, state, bone, new HashSet<>());
        float[] pivot = transformPosition(matrix, bone.pivotX(), bone.pivotY(), bone.pivotZ());
        float[] rotation = rotationZYX(matrix);
        return new WorldTransform(pivot[0], pivot[1], pivot[2], rotation[0], rotation[1], rotation[2]);
    }

    private static double[] buildMatrix(BbModelDefinition model, AvatarState state, BbBoneDefinition bone, Set<String> visiting) {
        double[] parentMatrix = identity();
        if (bone.parentUuid() != null && visiting.add(bone.uuid())) {
            BbBoneDefinition parent = model.findBoneByUuid(bone.parentUuid());
            if (parent != null) parentMatrix = buildMatrix(model, state, parent, visiting);
            visiting.remove(bone.uuid());
        }

        AvatarPartState part = state.parts().get(model.bonePath(bone.uuid()));
        float positionX = bone.pivotX() + (part != null && part.positionControlled() ? part.posX() : 0f);
        float positionY = bone.pivotY() + (part != null && part.positionControlled() ? part.posY() : 0f);
        float positionZ = bone.pivotZ() + (part != null && part.positionControlled() ? part.posZ() : 0f);
        float rotationX = bone.rotationX() + (part != null && part.rotationControlled() ? part.rotX() : 0f);
        float rotationY = bone.rotationY() + (part != null && part.rotationControlled() ? part.rotY() : 0f);
        float rotationZ = bone.rotationZ() + (part != null && part.rotationControlled() ? part.rotZ() : 0f);
        float scaleX = part != null && part.scaleControlled() ? part.scaleX() : 1f;
        float scaleY = part != null && part.scaleControlled() ? part.scaleY() : 1f;
        float scaleZ = part != null && part.scaleControlled() ? part.scaleZ() : 1f;

        return multiply(multiply(multiply(multiply(parentMatrix, translation(positionX, positionY, positionZ)), rotationZYX(rotationX, rotationY, rotationZ)), scale(scaleX, scaleY, scaleZ)), translation(-bone.pivotX(), -bone.pivotY(), -bone.pivotZ()));
    }

    private static double[] identity() {
        double[] value = new double[16];
        value[0] = value[5] = value[10] = value[15] = 1.0;
        return value;
    }

    private static double[] translation(double x, double y, double z) {
        double[] value = identity();
        value[12] = x; value[13] = y; value[14] = z;
        return value;
    }

    private static double[] scale(double x, double y, double z) {
        double[] value = new double[16];
        value[0] = x; value[5] = y; value[10] = z; value[15] = 1.0;
        return value;
    }

    /** Matrix order is Rz * Ry * Rx, matching Matrix4f.rotateZYX in the renderer. */
    private static double[] rotationZYX(double xDegrees, double yDegrees, double zDegrees) {
        double x = Math.toRadians(xDegrees), y = Math.toRadians(yDegrees), z = Math.toRadians(zDegrees);
        double cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y), cz = Math.cos(z), sz = Math.sin(z);
        double[] value = identity();
        value[0] = cz * cy;
        value[4] = cz * sy * sx - sz * cx;
        value[8] = cz * sy * cx + sz * sx;
        value[1] = sz * cy;
        value[5] = sz * sy * sx + cz * cx;
        value[9] = sz * sy * cx - cz * sx;
        value[2] = -sy;
        value[6] = cy * sx;
        value[10] = cy * cx;
        return value;
    }

    private static double[] multiply(double[] left, double[] right) {
        double[] result = new double[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                for (int index = 0; index < 4; index++) result[column * 4 + row] += left[index * 4 + row] * right[column * 4 + index];
            }
        }
        return result;
    }

    private static float[] transformPosition(double[] matrix, double x, double y, double z) {
        return new float[] {
            (float) (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]),
            (float) (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]),
            (float) (matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14])
        };
    }

    private static float[] rotationZYX(double[] matrix) {
        double r00 = matrix[0] / axisLength(matrix[0], matrix[1], matrix[2]);
        double r10 = matrix[1] / axisLength(matrix[0], matrix[1], matrix[2]);
        double r20 = matrix[2] / axisLength(matrix[0], matrix[1], matrix[2]);
        double r21 = matrix[6] / axisLength(matrix[4], matrix[5], matrix[6]);
        double r22 = matrix[10] / axisLength(matrix[8], matrix[9], matrix[10]);
        double y = Math.asin(Math.max(-1.0, Math.min(1.0, -r20)));
        double x = Math.atan2(r21, r22);
        double z = Math.atan2(r10, r00);
        return new float[] {(float) (x * RAD_TO_DEG), (float) (y * RAD_TO_DEG), (float) (z * RAD_TO_DEG)};
    }

    private static double axisLength(double x, double y, double z) {
        return Math.max(0.000001, Math.sqrt(x * x + y * y + z * z));
    }
}

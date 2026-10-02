package seashyne.shynecore.client.render;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import seashyne.shynecore.client.avatar.AvatarPartState;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbCubeDefinition;
import seashyne.shynecore.model.BbFaceUvDefinition;
import seashyne.shynecore.model.BbMeshDefinition;
import seashyne.shynecore.model.BbMeshFaceDefinition;
import seashyne.shynecore.model.BbMeshVertexDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves Blockbench rig topology, limb selection and geometry visibility.
 * แก้โครงสร้าง rig ของ Blockbench, เลือกแขนขา และตรวจ visibility ของ geometry.
 */
public final class BbModelRigResolver {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private BbModelRigResolver() {}

public static boolean belongsToBone(BbModelDefinition model, String boneUuid, String expectedAncestorUuid) {
    Set<String> visited = new HashSet<>();
    String current = boneUuid;
    while (current != null && visited.add(current)) {
        if (current.equals(expectedAncestorUuid)) return true;
        BbBoneDefinition bone = model.findBoneByUuid(current);
        current = bone == null ? null : bone.parentUuid();
    }
    return false;
}

public static float[] boneBounds(BbModelDefinition model, String boneUuid) {
    float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
    float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
    for (BbCubeDefinition cube : model.cubes()) {
        if (!belongsToBone(model, cube.parentBoneUuid(), boneUuid)) continue;
        minX = Math.min(minX, Math.min(cube.fromX(), cube.toX()));
        minY = Math.min(minY, Math.min(cube.fromY(), cube.toY()));
        minZ = Math.min(minZ, Math.min(cube.fromZ(), cube.toZ()));
        maxX = Math.max(maxX, Math.max(cube.fromX(), cube.toX()));
        maxY = Math.max(maxY, Math.max(cube.fromY(), cube.toY()));
        maxZ = Math.max(maxZ, Math.max(cube.fromZ(), cube.toZ()));
    }
    for (BbMeshDefinition mesh : model.meshes()) {
        if (!belongsToBone(model, mesh.parentBoneUuid(), boneUuid)) continue;
        Matrix4f local = new Matrix4f()
            .translate(mesh.originX(), mesh.originY(), mesh.originZ())
            .rotateZYX(mesh.rotationZ() * DEG_TO_RAD, mesh.rotationY() * DEG_TO_RAD, mesh.rotationX() * DEG_TO_RAD);
        Vector3f point = new Vector3f();
        for (BbMeshVertexDefinition vertex : mesh.vertices().values()) {
            point.set(vertex.x(), vertex.y(), vertex.z());
            local.transformPosition(point);
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
    }
    if (!Float.isFinite(minX)) return new float[] {0f, 0f, 0f, 4f, 12f, 4f};
    return new float[] {minX, minY, minZ, maxX, maxY, maxZ};
}

public static float minimumScale(float actual, float expected) {
    if (actual <= 0.001f || actual >= expected) return 1f;
    return Math.min(1.5f, expected / actual);
}

private static String normalizeBoneName(String name) {
    return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
}

private static BbBoneDefinition findHumanoidArm(BbModelDefinition model, HumanoidArm arm) {
    BbBoneDefinition leftNamed = null;
    BbBoneDefinition rightNamed = null;
    for (BbBoneDefinition bone : model.bones()) {
        if (isHumanoidArmBone(bone, HumanoidArm.LEFT)) leftNamed = bone;
        if (isHumanoidArmBone(bone, HumanoidArm.RIGHT)) rightNamed = bone;
    }
    if (leftNamed != null && rightNamed != null && Math.abs(leftNamed.pivotX() - rightNamed.pivotX()) > 0.01f) {
        BbBoneDefinition spatialRight = leftNamed.pivotX() < rightNamed.pivotX() ? leftNamed : rightNamed;
        BbBoneDefinition spatialLeft = spatialRight == leftNamed ? rightNamed : leftNamed;
        return arm == HumanoidArm.RIGHT ? spatialRight : spatialLeft;
    }
    return arm == HumanoidArm.LEFT ? leftNamed : rightNamed;
}

/**
 * Recognizes conventional Blockbench arm names and stable Shyne humanoid identifiers.
 * ระบุชื่อแขนแบบ Blockbench ทั่วไปและ identifier มนุษย์ของ Shyne ที่เสถียร.
 */
private static boolean isHumanoidArmBone(BbBoneDefinition bone, HumanoidArm arm) {
    if (bone == null) return false;
    String side = arm == HumanoidArm.LEFT ? "left" : "right";
    String expected = side + "arm";
    String name = normalizeBoneName(bone.name());
    String uuid = normalizeBoneName(bone.uuid());
    return name.equals(expected)
        || name.equals("humanoid" + expected)
        || name.equals("shynehumanoid" + expected)
        || uuid.equals("shynehumanoid" + expected);
}

/**
 * Prefers an authored first-person arm tree and falls back to the normal arm.
 * เลือก tree แขนมุมมองบุคคลที่หนึ่งที่ผู้สร้างกำหนดก่อน แล้วจึง fallback เป็นแขนปกติ.
 */
public static BbBoneDefinition findFirstPersonArm(BbModelDefinition model, HumanoidArm arm) {
    BbBoneDefinition leftCandidate = null;
    BbBoneDefinition rightCandidate = null;
    for (BbBoneDefinition bone : model.bones()) {
        if (isFirstPersonArmBone(bone, HumanoidArm.LEFT)) leftCandidate = bone;
        if (isFirstPersonArmBone(bone, HumanoidArm.RIGHT)) rightCandidate = bone;
    }
    if (leftCandidate != null && rightCandidate != null && Math.abs(leftCandidate.pivotX() - rightCandidate.pivotX()) > 0.01f) {
        // Minecraft's physical right limb is negative model X, matching normal-arm resolution.
        // แขนขวาจริงของ Minecraft อยู่แกน X ลบ จึงต้องจับคู่ FP tree ให้ตรงกับแขนปกติ.
        BbBoneDefinition spatialRight = leftCandidate.pivotX() < rightCandidate.pivotX() ? leftCandidate : rightCandidate;
        BbBoneDefinition spatialLeft = spatialRight == leftCandidate ? rightCandidate : leftCandidate;
        return arm == HumanoidArm.RIGHT ? spatialRight : spatialLeft;
    }
    BbBoneDefinition semanticCandidate = arm == HumanoidArm.LEFT ? leftCandidate : rightCandidate;
    if (semanticCandidate != null) return semanticCandidate;
    return findHumanoidArm(model, arm);
}

private static boolean isFirstPersonArmBone(BbBoneDefinition bone, HumanoidArm arm) {
    if (bone == null) return false;
    String side = arm == HumanoidArm.LEFT ? "left" : "right";
    String name = normalizeBoneName(bone.name());
    if (name.equals(side + "armfp") || name.equals(side + "armfirstperson") || name.equals("firstperson" + side + "arm")) return true;

    String wantedRole = "firstperson" + side + "arm";
    if (normalizeBoneName(bone.role()).equals(wantedRole)) return true;
    for (String tag : bone.tags()) {
        if (normalizeBoneName(tag).equals(wantedRole)) return true;
    }
    return false;
}

public static boolean isDedicatedFirstPersonArm(BbBoneDefinition bone) {
    return isFirstPersonArmBone(bone, HumanoidArm.LEFT) || isFirstPersonArmBone(bone, HumanoidArm.RIGHT);
}

/**
 * Hides authored first-person trees from regular player renders.
 * ซ่อน tree สำหรับมุมมองบุคคลที่หนึ่งจากการวาดผู้เล่นปกติ.
 */
public static Set<String> hiddenFirstPersonBones(BbModelDefinition model) {
    Set<String> hidden = new HashSet<>();
    for (BbBoneDefinition bone : model.bones()) {
        if (isFirstPersonArmBone(bone, HumanoidArm.LEFT) || isFirstPersonArmBone(bone, HumanoidArm.RIGHT)) {
            addBoneSubtree(model, bone.uuid(), hidden);
        }
    }
    return hidden;
}

private static void addBoneSubtree(BbModelDefinition model, String rootUuid, Set<String> output) {
    if (rootUuid == null) return;
    java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
    pending.add(rootUuid);
    while (!pending.isEmpty()) {
        String current = pending.removeFirst();
        if (!output.add(current)) continue;
        BbBoneDefinition bone = model.findBoneByUuid(current);
        if (bone != null) pending.addAll(bone.childBoneUuids());
    }
}

public static boolean hasDrawableGeometry(BbModelDefinition model, UUID entityId, String rootBoneUuid, Map<String, BbModelEntityRenderer.BonePose> bonePoses) {
    for (BbCubeDefinition cube : model.cubes()) {
        if (!belongsToBone(model, cube.parentBoneUuid(), rootBoneUuid)) continue;
        BbModelEntityRenderer.BonePose bonePose = cube.parentBoneUuid() == null ? BbModelEntityRenderer.BonePose.IDENTITY : bonePoses.getOrDefault(cube.parentBoneUuid(), BbModelEntityRenderer.BonePose.IDENTITY);
        if (!bonePose.visible()) continue;
        AvatarPartState cubePart = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), model.cubePath(cube));
        boolean cubeVisible = cube.visible();
        if (cubePart != null && cubePart.visibilityControlled()) cubeVisible = cubePart.visible();
        if (!cubeVisible) continue;
        if (cube.faces().values().stream().anyMatch(BbFaceUvDefinition::enabled)) return true;
    }
    for (BbMeshDefinition mesh : model.meshes()) {
        if (!belongsToBone(model, mesh.parentBoneUuid(), rootBoneUuid)) continue;
        BbModelEntityRenderer.BonePose bonePose = mesh.parentBoneUuid() == null ? BbModelEntityRenderer.BonePose.IDENTITY : bonePoses.getOrDefault(mesh.parentBoneUuid(), BbModelEntityRenderer.BonePose.IDENTITY);
        if (!bonePose.visible()) continue;
        AvatarPartState meshPart = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), model.meshPath(mesh));
        boolean meshVisible = mesh.visible();
        if (meshPart != null && meshPart.visibilityControlled()) meshVisible = meshPart.visible();
        if (!meshVisible) continue;
        if (mesh.faces().stream().anyMatch(BbMeshFaceDefinition::enabled)) return true;
    }
    return false;
}

public static String automaticPoseKey(BbModelDefinition model, BbBoneDefinition bone) {
    String name = normalizeBoneName(bone.name());
    boolean arm = name.equals("leftarm") || name.equals("rightarm");
    boolean leg = name.equals("leftleg") || name.equals("rightleg");
    boolean vanillaPart = arm || leg || name.equals("head") || name.equals("body") || name.equals("torso");
    if (vanillaPart && hasSameNamedDescendant(model, bone, name)) return "";
    if (!arm && !leg) return name;

    String oppositeName = name.startsWith("left")
        ? "right" + name.substring("left".length())
        : "left" + name.substring("right".length());
    BbBoneDefinition opposite = model.bones().stream()
        .filter(candidate -> normalizeBoneName(candidate.name()).equals(oppositeName))
        .findFirst()
        .orElse(null);
    if (opposite == null || Math.abs(bone.pivotX() - opposite.pivotX()) <= 0.01f) return name;

    // Minecraft's physical right limbs sit on negative model X; spatial order wins over editor labels.
    // แขนขวาจริงของ Minecraft อยู่แกน X ลบ จึงใช้ตำแหน่งจริงเหนือชื่อจากมุมมอง editor.
    String side = bone.pivotX() < opposite.pivotX() ? "right" : "left";
    return side + (arm ? "arm" : "leg");
}

/** Prevents nested aliases from receiving the same automatic pose twice.\n     * ป้องกัน alias ที่ซ้อนกันได้รับ automatic pose เดิมซ้ำสองครั้ง. */
private static boolean hasSameNamedDescendant(BbModelDefinition model, BbBoneDefinition ancestor, String normalizedName) {
    for (BbBoneDefinition candidate : model.bones()) {
        if (candidate == ancestor || !normalizeBoneName(candidate.name()).equals(normalizedName)) continue;
        String parentUuid = candidate.parentUuid();
        while (parentUuid != null) {
            if (parentUuid.equals(ancestor.uuid())) return true;
            BbBoneDefinition parent = model.findBoneByUuid(parentUuid);
            parentUuid = parent == null ? null : parent.parentUuid();
        }
    }
    return false;
}

/** Reads parent_type metadata so accessories do not require Lua binding code.\n     * อ่าน metadata parent_type เพื่อให้ accessory ไม่ต้องมีโค้ด Lua สำหรับผูก bone. */
private static String normalizeVanillaParentType(String value) {
    return switch (normalizeBoneName(value)) {
        case "head", "hat", "helmet" -> "head";
        case "body", "torso", "chestplate" -> "body";
        case "leftarm", "leftsleeve" -> "leftarm";
        case "rightarm", "rightsleeve" -> "rightarm";
        case "leftleg", "leftpants" -> "leftleg";
        case "rightleg", "rightpants" -> "rightleg";
        default -> "";
    };
}

public static String vanillaAttachmentKey(BbBoneDefinition bone, AvatarPartState part) {
    if (part != null && part.vanillaParentControlled()) return normalizeVanillaParentType(part.vanillaParent());
    return normalizeVanillaParentType(bone.parentType());
}
}

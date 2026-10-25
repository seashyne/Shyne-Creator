package seashyne.shynecore.client.compat;

import net.minecraft.client.Minecraft;
import seashyne.shynecore.client.avatar.AvatarPartState;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.render.AvatarRenderContext;
import seashyne.shynecore.client.render.BbModelRigResolver;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Compatibility bridge for First-Person Body & Camera mods (FirstPersonModel by tr7zw,
 * RealCamera, Shoulder Surfing Reloaded) as well as Shyne's built-in head masking in
 * first-person view.
 *
 * <p>สะพานเชื่อมความเข้ากันได้สำหรับม็อดมุมมองบุคคลที่หนึ่งเห็นลำตัว (FirstPersonModel,
 * RealCamera, Shoulder Surfing) และการซ่อนส่วนหัวอัตโนมัติในมุมมอง First-Person เพื่อไม่ให้
 * เส้นผมหรือลูกตาของอวาตาร์บังกล้องของผู้เล่น.</p>
 */
public final class FirstPersonModelCompat {

    private static final boolean TR7ZW_FIRST_PERSON_PRESENT = isClassPresent("dev.tr7zw.firstperson.api.FirstPersonAPI")
        || isClassPresent("dev.tr7zw.firstperson.FirstPersonModelCore");
    private static final boolean REAL_CAMERA_PRESENT = isClassPresent("com.xtracr.realcamera.RealCameraCore")
        || isClassPresent("com.xtracr.realcamera.api.RealCameraAPI");
    private static final boolean SHOULDER_SURFING_PRESENT = isClassPresent("com.teamderpy.shouldersurfing.api.ShoulderSurfing");

    private static Method tr7zwIsRenderingPlayerMethod;
    private static Field tr7zwIsRenderingPlayerField;
    private static boolean tr7zwReflectionInitialized;

    private static Method realCameraIsRenderingMethod;
    private static Method realCameraIsActiveMethod;
    private static boolean realCameraReflectionInitialized;

    private FirstPersonModelCompat() {}

    /**
     * Returns true if any third-party first-person body rendering mod is installed.
     */
    public static boolean isFirstPersonBodyModPresent() {
        return TR7ZW_FIRST_PERSON_PRESENT || REAL_CAMERA_PRESENT;
    }

    /**
     * Returns true if Shoulder Surfing Reloaded is installed.
     */
    public static boolean isShoulderSurfingPresent() {
        return SHOULDER_SURFING_PRESENT;
    }

    /**
     * Queries whether a third-party first-person body mod (FirstPersonModel or RealCamera)
     * is currently rendering the local player's full body in first-person view.
     */
    public static boolean isExternalFirstPersonBodyRendering() {
        if (TR7ZW_FIRST_PERSON_PRESENT && checkTr7zwRenderingPlayer()) {
            return true;
        }
        if (REAL_CAMERA_PRESENT && checkRealCameraRendering()) {
            return true;
        }
        return false;
    }

    /**
     * Determines whether head/hat/hair bones should be masked for the given entity during the current render pass.
     *
     * @param context  Current AvatarRenderContext override or resolved context
     * @param entityId UUID of the rendered entity
     * @return true if head bones should be hidden to avoid blocking the first-person camera
     */
    public static boolean shouldMaskHeadForEntity(String context, UUID entityId) {
        if (entityId == null) return false;
        // Never mask the head inside GUI paperdolls, portraits, or skull renders
        if (AvatarRenderContext.SHYNE_GUI.equals(context)
            || AvatarRenderContext.MINECRAFT_GUI.equals(context)
            || AvatarRenderContext.PORTRAIT.equals(context)
            || AvatarRenderContext.SKULL.equals(context)
            || AvatarRenderContext.HELD_ITEM.equals(context)) {
            return false;
        }

        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null) return false;
            if (!entityId.equals(client.player.getUUID())) return false;
            if (client.gui != null && client.gui.screen() != null) return false;

            boolean externalFpBody = isExternalFirstPersonBodyRendering();
            boolean isFirstPersonCamera = client.options != null && client.options.getCameraType().isFirstPerson();
            if (!externalFpBody && !isFirstPersonCamera) {
                return false;
            }

            // If an external FP body mod is actively rendering the player in FP, or if the avatar
            // has first-person head masking enabled, mask the head hierarchy.
            return externalFpBody
                || AvatarRuntime.shouldHideHeadInFirstPerson()
                || (isFirstPersonBodyModPresent() && AvatarRuntime.shouldMaskFirstPerson());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Returns true if the given bone (or any of its ancestors in the model hierarchy)
     * is a Head bone (`head`, `hat`, `helmet`, or `parent_type = Head`).
     */
    public static boolean isHeadOrDescendantBone(BbModelDefinition model, BbBoneDefinition bone, AvatarPartState part) {
        if (model == null || bone == null) return false;
        if (isDirectHeadBone(model, bone, part)) return true;

        Set<String> visited = new HashSet<>();
        String parentUuid = bone.parentUuid();
        while (parentUuid != null && visited.add(parentUuid)) {
            BbBoneDefinition parent = model.findBoneByUuid(parentUuid);
            if (parent == null) break;
            if (isDirectHeadBone(model, parent, null)) {
                return true;
            }
            parentUuid = parent.parentUuid();
        }
        return false;
    }

    public static boolean isDirectHeadBone(BbModelDefinition model, BbBoneDefinition bone, AvatarPartState part) {
        if (bone == null) return false;
        String attachment = BbModelRigResolver.vanillaAttachmentKey(bone, part);
        if ("head".equals(attachment)) return true;
        if (model != null && "head".equals(BbModelRigResolver.automaticPoseKey(model, bone))) return true;
        String normalized = normalizeBoneName(bone.name());
        return "head".equals(normalized) || "hat".equals(normalized) || "helmet".equals(normalized);
    }

    private static String normalizeBoneName(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static boolean checkTr7zwRenderingPlayer() {
        try {
            if (!tr7zwReflectionInitialized) {
                tr7zwReflectionInitialized = true;
                try {
                    Class<?> apiClass = Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI");
                    tr7zwIsRenderingPlayerMethod = apiClass.getMethod("isRenderingPlayer");
                } catch (Throwable ignored) {
                    try {
                        Class<?> coreClass = Class.forName("dev.tr7zw.firstperson.FirstPersonModelCore");
                        tr7zwIsRenderingPlayerField = coreClass.getDeclaredField("isRenderingPlayer");
                        tr7zwIsRenderingPlayerField.setAccessible(true);
                    } catch (Throwable ignored2) {}
                }
            }
            if (tr7zwIsRenderingPlayerMethod != null) {
                Object res = tr7zwIsRenderingPlayerMethod.invoke(null);
                if (res instanceof Boolean b) return b;
            }
            if (tr7zwIsRenderingPlayerField != null) {
                Object res = tr7zwIsRenderingPlayerField.get(null);
                if (res instanceof Boolean b) return b;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean checkRealCameraRendering() {
        try {
            if (!realCameraReflectionInitialized) {
                realCameraReflectionInitialized = true;
                try {
                    Class<?> coreClass = Class.forName("com.xtracr.realcamera.RealCameraCore");
                    realCameraIsRenderingMethod = coreClass.getMethod("isRendering");
                    realCameraIsActiveMethod = coreClass.getMethod("isActive");
                } catch (Throwable ignored) {}
            }
            if (realCameraIsRenderingMethod != null) {
                Object res = realCameraIsRenderingMethod.invoke(null);
                if (res instanceof Boolean b && b) return true;
            }
            if (realCameraIsActiveMethod != null) {
                Object res = realCameraIsActiveMethod.invoke(null);
                if (res instanceof Boolean b && b) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, FirstPersonModelCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}

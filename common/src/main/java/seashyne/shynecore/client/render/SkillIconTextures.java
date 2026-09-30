package seashyne.shynecore.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.avatar.PngTextureValidator;
import seashyne.shynecore.skill.SkillIconAsset;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/** Client-side GPU cache for verified PNG icons sent with the content registries. */
public final class SkillIconTextures {
    private static final Map<String, Icon> BY_SKILL = new HashMap<>();
    private static final Map<String, Icon> BY_HASH = new HashMap<>();

    private SkillIconTextures() {}

    /**
     * Installs one icon from a registry sync. Invalid network data is discarded
     * before it can reach the GPU, even though the server has already validated it.
     */
    public static boolean installSkill(String skillId, String contentHash, int width, int height, String contentBase64) {
        return install(scopedKey("skill", skillId), contentHash, width, height, contentBase64);
    }

    public static boolean installItem(String itemId, String contentHash, int width, int height, String contentBase64) {
        return install(scopedKey("item", itemId), contentHash, width, height, contentBase64);
    }

    private static boolean install(String ownerId, String contentHash, int width, int height, String contentBase64) {
        if (ownerId == null || ownerId.isBlank() || contentHash == null || !contentHash.matches("[0-9a-fA-F]{64}")
            || contentBase64 == null || contentBase64.isBlank()
            || contentBase64.length() > (SkillIconAsset.MAX_ICON_BYTES * 4 / 3) + 8
            || width <= 0 || height <= 0 || width > SkillIconAsset.MAX_ICON_DIMENSION || height > SkillIconAsset.MAX_ICON_DIMENSION) {
            return false;
        }

        String hash = contentHash.toLowerCase(java.util.Locale.ROOT);
        Icon existing = BY_SKILL.get(ownerId);
        if (existing != null && existing.hash.equals(hash)) return true;

        Icon shared = BY_HASH.get(hash);
        if (shared != null) {
            replaceSkillIcon(ownerId, shared);
            return true;
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(contentBase64);
            if (bytes.length <= 0 || bytes.length > SkillIconAsset.MAX_ICON_BYTES
                || !PngTextureValidator.matches(bytes, width, height)
                || !hash.equals(sha256(bytes))) {
                return false;
            }
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            try {
                Identifier id = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "skill-icons/" + hash);
                Minecraft.getInstance().getTextureManager().register(
                    id,
                    new DynamicTexture(() -> "Shyne content icon " + ownerId, image)
                );
                image = null; // DynamicTexture now owns the decoded image.
                Icon icon = new Icon(id, hash, width, height);
                BY_HASH.put(hash, icon);
                replaceSkillIcon(ownerId, icon);
                return true;
            } finally {
                if (image != null && !image.isClosed()) image.close();
            }
        } catch (IOException | RuntimeException error) {
            ShyneCore.LOGGER.warn("[ContentIcon] Rejected icon for {}: {}", ownerId, error.getMessage());
            return false;
        }
    }

    /** Removes icons that were absent from the latest complete skill registry snapshot. */
    public static void retainSkills(Set<String> activeSkillIds) {
        retainScoped("skill", activeSkillIds);
    }

    /** Removes icons that were absent from the latest complete item registry snapshot. */
    public static void retainItems(Set<String> activeItemIds) {
        retainScoped("item", activeItemIds);
    }

    public static void clear() {
        for (String skillId : Set.copyOf(BY_SKILL.keySet())) replaceSkillIcon(skillId, null);
    }

    /** Draws a centred, aspect-preserving skill icon. */
    public static boolean drawSkillCentered(GuiGraphicsExtractor graphics, String skillId, int x, int y, int width, int height) {
        return drawCentered(graphics, scopedKey("skill", skillId), x, y, width, height);
    }

    /** Draws a centred, aspect-preserving item icon. */
    public static boolean drawItemCentered(GuiGraphicsExtractor graphics, String itemId, int x, int y, int width, int height) {
        return drawCentered(graphics, scopedKey("item", itemId), x, y, width, height);
    }

    private static boolean drawCentered(GuiGraphicsExtractor graphics, String ownerId, int x, int y, int width, int height) {
        Icon icon = ownerId == null ? null : BY_SKILL.get(ownerId);
        if (icon == null || width <= 0 || height <= 0) return false;
        float scale = Math.min(width / (float) icon.width, height / (float) icon.height);
        int drawWidth = Math.max(1, Math.round(icon.width * scale));
        int drawHeight = Math.max(1, Math.round(icon.height * scale));
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            icon.id,
            x + (width - drawWidth) / 2,
            y + (height - drawHeight) / 2,
            0,
            0,
            drawWidth,
            drawHeight,
            icon.width,
            icon.height,
            icon.width,
            icon.height
        );
        return true;
    }

    private static void retainScoped(String scope, Set<String> activeIds) {
        String prefix = scope + ":";
        Set<String> keep = new java.util.HashSet<>();
        if (activeIds != null) {
            for (String id : activeIds) {
                String scoped = scopedKey(scope, id);
                if (scoped != null) keep.add(scoped);
            }
        }
        for (String ownerId : Set.copyOf(BY_SKILL.keySet())) {
            if (ownerId.startsWith(prefix) && !keep.contains(ownerId)) replaceSkillIcon(ownerId, null);
        }
    }

    private static String scopedKey(String scope, String id) {
        return scope == null || id == null || id.isBlank() ? null : scope + ":" + id;
    }

    private static void replaceSkillIcon(String skillId, Icon next) {
        Icon previous = next == null ? BY_SKILL.remove(skillId) : BY_SKILL.put(skillId, next);
        if (previous == null || previous == next) return;
        boolean stillUsed = BY_SKILL.values().stream().anyMatch(icon -> icon == previous);
        if (!stillUsed && BY_HASH.remove(previous.hash, previous)) {
            Minecraft.getInstance().getTextureManager().release(previous.id);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private record Icon(Identifier id, String hash, int width, int height) {}
}

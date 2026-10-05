package seashyne.shynecore.client.avatar;

import java.util.Locale;
import java.util.Map;

/**
 * Canonical names and compatibility aliases for vanilla render visibility.
 *
 * <p>Keeping this normalization outside the renderer makes local Lua writes
 * and remote snapshots behave identically, including snapshots produced by an
 * older client that used lower-case or Figura-style aliases.</p>
 */
public final class VanillaVisibilityKeys {
    public static final String PLAYER = "PLAYER";
    public static final String HELD_ITEMS = "HELD_ITEMS";
    public static final String LEFT_ITEM = "LEFT_ITEM";
    public static final String RIGHT_ITEM = "RIGHT_ITEM";
    public static final String MAIN_HAND = "MAIN_HAND";
    public static final String OFF_HAND = "OFF_HAND";
    public static final String HEAD_ITEM = "HEAD_ITEM";

    private VanillaVisibilityKeys() {}

    public static String normalize(String key) {
        if (key == null || key.isBlank()) return PLAYER;
        String normalized = key.trim().toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
        while (normalized.contains("__")) normalized = normalized.replace("__", "_");
        return switch (normalized) {
            case "HELD_ITEM", "ITEMS" -> HELD_ITEMS;
            case "LEFT_HAND_ITEM", "LEFT_HELD_ITEM" -> LEFT_ITEM;
            case "RIGHT_HAND_ITEM", "RIGHT_HELD_ITEM" -> RIGHT_ITEM;
            case "MAINHAND", "MAIN_HAND_ITEM" -> MAIN_HAND;
            case "OFFHAND", "OFF_HAND_ITEM" -> OFF_HAND;
            case "SKULL", "HELMET_ITEM", "HEAD_ITEM", "HELD_ON_HEAD", "CUSTOM_HEAD" -> HEAD_ITEM;
            case "LEFT_PANTS_LEG" -> "LEFT_PANTS";
            case "RIGHT_PANTS_LEG" -> "RIGHT_PANTS";
            case "HEAD_ARMOR" -> "HELMET";
            case "CHEST_ARMOR", "BODY_ARMOR" -> "CHESTPLATE";
            case "LEG_ARMOR" -> "LEGGINGS";
            case "TORSO" -> "BODY";
            case "RIGHT_HAND" -> "RIGHT_ARM";
            case "LEFT_HAND" -> "LEFT_ARM";
            case "RIGHT_FOOT" -> "RIGHT_LEG";
            case "LEFT_FOOT" -> "LEFT_LEG";
            case "SLEEVES" -> "SLEEVE";
            case "PANTS" -> "PANTS";
            default -> normalized;
        };
    }

    /**
     * Determines whether the given canonical key is a body part of the player rig.
     * Non-body parts like ARMOR, CAPE, ELYTRA, and HELD_ITEMS are independent of PLAYER.
     */
    public static boolean isPlayerBodyPart(String key) {
        String canonical = normalize(key);
        return switch (canonical) {
            case "HEAD", "HAT", "BODY", "JACKET",
                 "RIGHT_ARM", "LEFT_ARM", "RIGHT_SLEEVE", "LEFT_SLEEVE",
                 "RIGHT_LEG", "LEFT_LEG", "RIGHT_PANTS", "LEFT_PANTS" -> true;
            default -> false;
        };
    }

    /**
     * Expands compound part keys (e.g. ARMS, LEGS) into their constituent parts and outer clothing layers.
     */
    public static java.util.List<String> expand(String key) {
        String canonical = normalize(key);
        return switch (canonical) {
            case "ARMS", "ARM" -> java.util.List.of("RIGHT_ARM", "LEFT_ARM", "RIGHT_SLEEVE", "LEFT_SLEEVE");
            case "LEGS", "LEG" -> java.util.List.of("RIGHT_LEG", "LEFT_LEG", "RIGHT_PANTS", "LEFT_PANTS");
            case "HEAD" -> java.util.List.of("HEAD", "HAT");
            case "BODY", "TORSO" -> java.util.List.of("BODY", "JACKET");
            case "RIGHT_ARM" -> java.util.List.of("RIGHT_ARM", "RIGHT_SLEEVE");
            case "LEFT_ARM" -> java.util.List.of("LEFT_ARM", "LEFT_SLEEVE");
            case "RIGHT_LEG" -> java.util.List.of("RIGHT_LEG", "RIGHT_PANTS");
            case "LEFT_LEG" -> java.util.List.of("LEFT_LEG", "LEFT_PANTS");
            case "SLEEVES", "SLEEVE" -> java.util.List.of("RIGHT_SLEEVE", "LEFT_SLEEVE");
            case "PANTS" -> java.util.List.of("RIGHT_PANTS", "LEFT_PANTS");
            case "ARMOR" -> java.util.List.of("ARMOR", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
            case HELD_ITEMS, "ITEMS" -> java.util.List.of(HELD_ITEMS, LEFT_ITEM, RIGHT_ITEM, MAIN_HAND, OFF_HAND);
            default -> java.util.List.of(canonical);
        };
    }

    /** Returns a normalized value, tolerating non-normalized legacy map keys. */
    public static Boolean find(Map<String, Boolean> visibility, String key) {
        if (visibility == null || visibility.isEmpty()) return null;
        String canonical = normalize(key);
        Boolean direct = visibility.get(canonical);
        if (direct != null) return direct;
        for (Map.Entry<String, Boolean> entry : visibility.entrySet()) {
            if (canonical.equals(normalize(entry.getKey()))) return entry.getValue();
        }
        return null;
    }

    /**
     * Missing layer keys remain visible. Only PLAYER inherits replaceVanilla,
     * matching Figura's independent PLAYER/ARMOR/ELYTRA/CAPE groups.
     */
    public static boolean isVisible(Map<String, Boolean> visibility, boolean replaceVanilla, String key) {
        Boolean value = find(visibility, key);
        if (value != null) return value;
        return !PLAYER.equals(normalize(key)) || !replaceVanilla;
    }

    /**
     * Resolves the visibility a script can actually observe on screen. Child
     * layers are also hidden when the PLAYER group is hidden, while the pose
     * captured from Minecraft may independently mark its model part invisible.
     */
    public static boolean effectiveVisible(
        Map<String, Boolean> visibility,
        boolean replaceVanilla,
        String key,
        boolean capturedVisible
    ) {
        if (!capturedVisible) return false;
        String canonical = normalize(key);
        if (PLAYER.equals(canonical)) return isVisible(visibility, replaceVanilla, PLAYER);
        if (isPlayerBodyPart(canonical)) {
            return isVisible(visibility, replaceVanilla, PLAYER)
                && isVisible(visibility, replaceVanilla, canonical);
        }
        return isVisible(visibility, replaceVanilla, canonical);
    }
}

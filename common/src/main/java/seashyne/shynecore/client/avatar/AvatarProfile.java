package seashyne.shynecore.client.avatar;

import java.util.Locale;

/**
 * High-level render profile used by Shyne Standard 2.0.
 *
 * <p>The profile supplies safe defaults only. Authors can still override the
 * individual manifest switches when a model needs a mixed setup.</p>
 */
public enum AvatarProfile {
    ACCESSORY("accessory", false, false, false),
    FULL_BODY("full_body", true, true, true),
    CUSTOM("custom", false, false, false);

    private final String id;
    private final boolean replaceVanilla;
    private final boolean firstPersonMasking;
    private final boolean localCamera;

    AvatarProfile(String id, boolean replaceVanilla, boolean firstPersonMasking, boolean localCamera) {
        this.id = id;
        this.replaceVanilla = replaceVanilla;
        this.firstPersonMasking = firstPersonMasking;
        this.localCamera = localCamera;
    }

    public String id() { return id; }
    public boolean replaceVanilla() { return replaceVanilla; }
    public boolean firstPersonMasking() { return firstPersonMasking; }
    public boolean localCamera() { return localCamera; }

    public static AvatarProfile parse(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "full", "fullbody", "full_body", "avatar" -> FULL_BODY;
            case "custom", "special", "aquatic", "merling" -> CUSTOM;
            case "", "accessory", "overlay", "tail", "ears", "wings" -> ACCESSORY;
            default -> throw new IllegalArgumentException("unsupported Shyne avatar profile: " + raw);
        };
    }
}

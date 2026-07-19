package seashyne.shynecore.client.avatar;

import java.util.Locale;
import java.util.Optional;

/**
 * Capabilities a Public Avatar must declare in avatar.json before its Lua
 * runtime can access the corresponding client feature.
 */
public enum AvatarPermission {
    PARTICLE("particle", false),
    SOUND("sound", false),
    CAMERA("camera", true),
    MICROPHONE("microphone", true),
    COMMAND("command", true),
    HUD_RENDER("hud_render", true),
    WORLD_RENDER("world_render", true);

    private final String id;
    private final boolean dangerous;

    AvatarPermission(String id, boolean dangerous) {
        this.id = id;
        this.dangerous = dangerous;
    }

    public String id() {
        return id;
    }

    public boolean dangerous() {
        return dangerous;
    }

    public String translationKey() {
        return "screen.shyne_core.permission." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".description";
    }

    public static Optional<AvatarPermission> fromId(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AvatarPermission permission : values()) {
            if (permission.id.equals(normalized)) return Optional.of(permission);
        }
        return Optional.empty();
    }
}

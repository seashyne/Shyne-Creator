package seashyne.shynecore.client.render;

import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.UUID;

/** Stable Shyne render contexts exposed to Avatar Lua and transform snapshots. */
public final class AvatarRenderContext {
    public static final String SHYNE_GUI = "SHYNE_GUI";
    public static final String MINECRAFT_GUI = "MINECRAFT_GUI";
    public static final String FIRST_PERSON = "FIRST_PERSON";
    public static final String RENDER = "RENDER";
    public static final String WORLD = "WORLD";
    public static final String PORTRAIT = "PORTRAIT";
    public static final String SKULL = "SKULL";
    public static final String HELD_ITEM = "HELD_ITEM";
    public static final String OTHER = "OTHER";

    private static final ThreadLocal<java.util.Deque<String>> CONTEXT_STACK = ThreadLocal.withInitial(java.util.ArrayDeque::new);

    private AvatarRenderContext() {}

    /** Sets an explicit scoped render context for portrait, skull, or held-item passes. */
    public static void pushContext(String context) {
        if (context != null) {
            CONTEXT_STACK.get().push(normalize(context));
        }
    }

    /** Clears the most recent scoped render context override. */
    public static void popContext() {
        java.util.Deque<String> stack = CONTEXT_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /** Returns the active scoped render context override on this thread, or OTHER if none is set. */
    public static String currentOverride() {
        java.util.Deque<String> stack = CONTEXT_STACK.get();
        return stack.isEmpty() ? OTHER : stack.peek();
    }

    /** Returns the active render context, honoring thread-local presentation overrides. */
    public static String current(Minecraft client) {
        String override = currentOverride();
        if (!OTHER.equals(override)) {
            return override;
        }
        if (client == null) return OTHER;
        var screen = client.gui.screen();
        if (screen != null) {
            String packageName = screen.getClass().getPackageName();
            return packageName.startsWith("seashyne.shynecore.client.ui") ? SHYNE_GUI : MINECRAFT_GUI;
        }
        if (client.level == null) return OTHER;
        return client.options.getCameraType().isFirstPerson() ? FIRST_PERSON : RENDER;
    }

    /** Context used while a particular player model is submitted. */
    public static String forEntity(Minecraft client, UUID entityId) {
        String override = currentOverride();
        if (!OTHER.equals(override)) {
            return override;
        }
        String frame = current(client);
        if (MINECRAFT_GUI.equals(frame) || SHYNE_GUI.equals(frame) || PORTRAIT.equals(frame)
            || SKULL.equals(frame) || HELD_ITEM.equals(frame)) return frame;
        if (FIRST_PERSON.equals(frame) && client != null && client.player != null
            && entityId != null && !entityId.equals(client.player.getUUID())) return RENDER;
        return frame;
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return OTHER;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case SHYNE_GUI, MINECRAFT_GUI, FIRST_PERSON, RENDER, WORLD, PORTRAIT, SKULL, HELD_ITEM, OTHER -> normalized;
            default -> OTHER;
        };
    }

    public static boolean worldSpace(String context) {
        String normalized = normalize(context);
        return normalized.equals(RENDER) || normalized.equals(WORLD);
    }
}

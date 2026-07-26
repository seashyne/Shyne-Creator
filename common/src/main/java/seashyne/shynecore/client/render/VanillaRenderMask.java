package seashyne.shynecore.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import seashyne.shynecore.client.avatar.AvatarRuntime;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Associates a transient player render state with the avatar UUID that owns it. */
public final class VanillaRenderMask {
    private static final Map<AvatarRenderState, UUID> OWNERS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private VanillaRenderMask() {}

    public static void bind(AvatarRenderState state, UUID playerId) {
        if (state != null && playerId != null) OWNERS.put(state, playerId);
    }

    public static boolean visible(AvatarRenderState state, String key) {
        if (state == null) return true;
        UUID playerId = OWNERS.get(state);
        if (playerId == null) {
            Minecraft client = Minecraft.getInstance();
            Entity entity = client.level == null ? null : client.level.getEntity(state.id);
            if (entity != null) {
                playerId = entity.getUUID();
                OWNERS.put(state, playerId);
            }
        }
        return AvatarRuntime.isVanillaPartVisible(playerId, key);
    }
}

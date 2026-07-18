package seashyne.shynecore.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;

/** Fabric adapter that inserts Shyne's loader-neutral tasks into the HUD extraction pass. */
public final class AvatarRenderTasks {
    private AvatarRenderTasks() {}

    public static void init() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "avatar_render_tasks"),
            (graphics, delta) -> AvatarRenderTaskRegistry.extractHud(graphics)
        );
    }
}

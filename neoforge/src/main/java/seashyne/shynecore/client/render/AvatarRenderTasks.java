package seashyne.shynecore.client.render;

import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge adapter that inserts Shyne's loader-neutral tasks into the HUD extraction pass. */
public final class AvatarRenderTasks {
    private AvatarRenderTasks() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class,
            event -> AvatarRenderTaskRegistry.extractHud(event.getGuiGraphics()));
    }
}

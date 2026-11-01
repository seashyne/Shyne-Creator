package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ShynePauseMenu {
    private ShynePauseMenu() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            var screen = event.getScreen();
            if (!(screen instanceof PauseScreen pauseScreen) || !pauseScreen.showsPauseMenu()) return;

            int width = screen.width;
            int height = screen.height;
            Component label = Component.literal("✦ ").append(Component.translatable("screen.shyne_core.pause_button"));
            int buttonWidth = Math.max(108, Math.min(156, minecraft.font.width(label.getString()) + 20));
            int x = Math.max(6, width - buttonWidth - 8);
            int y = Math.max(4, height - 26);

            ShyneButton studioBtn = ShyneButton.primary(
                label,
                ignored -> minecraft.gui.setScreen(new AvatarManagerScreen(screen))
            ).bounds(x, y, buttonWidth, 20)
                .tooltipText(Component.translatable("screen.shyne_core.pause_studio.tooltip"));
            event.addListener(studioBtn);
        });
    }
}

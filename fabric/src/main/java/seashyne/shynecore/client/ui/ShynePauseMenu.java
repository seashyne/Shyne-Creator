package seashyne.shynecore.client.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

public final class ShynePauseMenu {
    private ShynePauseMenu() {}

    public static void init() {
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (!(screen instanceof PauseScreen pauseScreen) || !pauseScreen.showsPauseMenu()) return;

            Component label = Component.literal("✦ ").append(Component.translatable("screen.shyne_core.pause_button"));
            int buttonWidth = Math.max(108, Math.min(156, minecraft.font.width(label.getString()) + 20));
            int x = Math.max(6, width - buttonWidth - 8);
            int y = Math.max(4, height - 26);

            ShyneButton studioBtn = ShyneButton.primary(
                label,
                ignored -> minecraft.gui.setScreen(new AvatarManagerScreen(screen))
            ).bounds(x, y, buttonWidth, 20)
                .tooltipText(Component.translatable("screen.shyne_core.pause_studio.tooltip"));
            Screens.getWidgets(screen).add(studioBtn);
        });
    }
}

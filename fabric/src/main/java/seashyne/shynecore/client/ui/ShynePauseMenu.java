package seashyne.shynecore.client.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

public final class ShynePauseMenu {
    private ShynePauseMenu() {}

    public static void init() {
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (!(screen instanceof PauseScreen pauseScreen) || !pauseScreen.showsPauseMenu()) return;

            int buttonWidth = 106;
            int x = Math.max(6, width - buttonWidth - 6);
            int y = Math.max(4, height - 26);

            Button shyne = Button.builder(
                Component.literal("✦ ").withStyle(ChatFormatting.AQUA)
                    .append(Component.translatable("screen.shyne_core.pause_button").withStyle(ChatFormatting.WHITE)),
                ignored -> minecraft.gui.setScreen(new ShyneHomeScreen(screen))
            ).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.pause_button.tooltip")))
                .bounds(x, y, buttonWidth, 20).build();
            Screens.getWidgets(screen).add(shyne);
        });
    }
}

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

            int buttonWidth = 74;
            int gap = 4;
            int groupWidth = buttonWidth * 3 + gap * 2;
            int x = Math.max(6, width - groupWidth - 6);
            int y = Math.max(4, height - 26);

            Button avatars = Button.builder(
                Component.literal("✦ ").withStyle(ChatFormatting.AQUA)
                    .append(Component.translatable("screen.shyne_core.pause_avatar_button").withStyle(ChatFormatting.WHITE)),
                ignored -> minecraft.gui.setScreen(new AvatarManagerScreen(screen))
            ).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.pause_avatar_button.tooltip")))
                .bounds(x, y, buttonWidth, 20).build();
            Screens.getWidgets(screen).add(avatars);

            Button powers = Button.builder(
                Component.literal("✦ ").withStyle(ChatFormatting.AQUA)
                    .append(Component.translatable("screen.shyne_core.pause_powers_button").withStyle(ChatFormatting.WHITE)),
                ignored -> minecraft.gui.setScreen(new PowerDeckScreen(screen))
            ).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.pause_powers_button.tooltip")))
                .bounds(x + buttonWidth + gap, y, buttonWidth, 20).build();
            Screens.getWidgets(screen).add(powers);

            Button settings = Button.builder(
                Component.literal("✦ ").withStyle(ChatFormatting.AQUA)
                    .append(Component.translatable("screen.shyne_core.pause_button").withStyle(ChatFormatting.WHITE)),
                ignored -> minecraft.gui.setScreen(new ShyneSettingsScreen(screen))
            ).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.pause_button.tooltip")))
                .bounds(x + (buttonWidth + gap) * 2, y, buttonWidth, 20).build();
            Screens.getWidgets(screen).add(settings);
        });
    }
}

package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
            int buttonWidth = 104;
            int gap = 4;
            int groupWidth = buttonWidth * 2 + gap;
            int x = Math.max(6, width - groupWidth - 6);
            int y = Math.max(4, height - 26);
            Button button = Button.builder(
                Component.literal("✦ ").withStyle(ChatFormatting.AQUA)
                    .append(Component.translatable("screen.shyne_core.pause_button").withStyle(ChatFormatting.WHITE)),
                ignored -> minecraft.gui.setScreen(new ShyneSettingsScreen(screen))
            ).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.pause_button.tooltip")))
                .bounds(x + buttonWidth + gap, y, buttonWidth, 20).build();
            event.addListener(button);

            Button avatars = Button.builder(
                Component.literal("✦ ").withStyle(ChatFormatting.AQUA)
                    .append(Component.translatable("screen.shyne_core.pause_avatar_button").withStyle(ChatFormatting.WHITE)),
                ignored -> minecraft.gui.setScreen(new AvatarManagerScreen(screen))
            ).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.pause_avatar_button.tooltip")))
                .bounds(x, y, buttonWidth, 20).build();
            event.addListener(avatars);
        });
    }
}

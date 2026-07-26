package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/** Adds a collision-aware Shyne settings shortcut to Minecraft's title-screen icon row. */
public final class ShyneTitleMenu {
    private ShyneTitleMenu() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
            var screen = event.getScreen();
            if (!(screen instanceof TitleScreen)) return;
            if (screen.children().stream().anyMatch(ShyneLogoButton.class::isInstance)) return;

            List<TitleMenuButtonLayout.Bounds> occupied = screen.children().stream()
                .filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .map(widget -> new TitleMenuButtonLayout.Bounds(
                    widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()
                ))
                .toList();
            TitleMenuButtonLayout.findPosition(screen.width, screen.height, occupied).ifPresent(position -> {
                ShyneLogoButton button = new ShyneLogoButton(position.x(), position.y(), ignored ->
                    Minecraft.getInstance().gui.setScreen(new ShyneSettingsScreen(screen))
                );
                button.setTooltip(Tooltip.create(Component.translatable("screen.shyne_core.pause_button.tooltip")));
                event.addListener(button);
            });
        });
    }
}

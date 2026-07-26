package seashyne.shynecore.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;

/** Vanilla-styled icon button that reuses the Shyne Creator logo texture. */
public final class ShyneLogoButton extends Button {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(
        ShyneCore.MOD_ID,
        "textures/gui/shyne_creator_logo.png"
    );
    private static final int LOGO_TEXTURE_SIZE = 1280;

    public ShyneLogoButton(int x, int y, OnPress onPress) {
        super(
            x,
            y,
            TitleMenuButtonLayout.BUTTON_SIZE,
            TitleMenuButtonLayout.BUTTON_SIZE,
            Component.translatable("screen.shyne_core.settings"),
            onPress,
            DEFAULT_NARRATION
        );
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractDefaultSprite(graphics);
        int iconSize = Math.max(1, Math.min(getWidth(), getHeight()) - 6);
        int iconX = getX() + (getWidth() - iconSize) / 2;
        int iconY = getY() + (getHeight() - iconSize) / 2;
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            LOGO,
            iconX,
            iconY,
            0,
            0,
            iconSize,
            iconSize,
            LOGO_TEXTURE_SIZE,
            LOGO_TEXTURE_SIZE,
            LOGO_TEXTURE_SIZE,
            LOGO_TEXTURE_SIZE
        );
    }
}

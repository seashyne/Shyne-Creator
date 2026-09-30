package seashyne.shynecore.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.render.AvatarRenderTaskRegistry;

/** Input shell for a Lua-authored canvas. It intentionally has no built-in chrome or widgets. */
final class AvatarScriptCanvasScreen extends Screen {
    private final AvatarScriptCanvasRegistry.Canvas canvas;
    private boolean closed;

    AvatarScriptCanvasScreen(AvatarScriptCanvasRegistry.Canvas canvas) {
        super(Component.empty());
        this.canvas = canvas;
    }

    AvatarScriptCanvasRegistry.Canvas canvas() { return canvas; }
    boolean isOwnedBy(Object owner) { return canvas.isOwnedBy(owner); }

    @Override public boolean isPauseScreen() { return canvas.pausesGame(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if ((canvas.backdropColor() >>> 24) != 0) {
            graphics.fill(0, 0, this.width, this.height, canvas.backdropColor());
        }
        AvatarRenderTaskRegistry.extractSurface(graphics, canvas.avatarId(), canvas.surface());
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var button = canvas.hit(event.x(), event.y());
        if (button == null) return true;
        if (button.callback() != null) {
            button.callback().accept(new AvatarScriptCanvasRegistry.CanvasPointerEvent(
                button.id(), event.x(), event.y(), event.button(), doubleClick
            ));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 && canvas.closeOnEscape()) {
            closeFromScript();
            return true;
        }
        return true;
    }

    void closeFromScript() {
        if (closed) return;
        closed = true;
        canvas.close();
        if (this.minecraft != null && this.minecraft.gui.screen() == this) this.minecraft.gui.setScreen(null);
    }

    @Override public void onClose() { closeFromScript(); }
}

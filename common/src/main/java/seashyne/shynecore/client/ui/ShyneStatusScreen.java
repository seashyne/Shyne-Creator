package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.ShyneStatusClient;
import seashyne.shynecore.client.config.ShyneClientSettings;

public final class ShyneStatusScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "textures/gui/shyne_creator_logo.png");
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 192;
    private final Screen parent;
    private int panelX;
    private int panelY;
    private EditBox endpointBox;
    private boolean enabled;

    public ShyneStatusScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.status.title"));
        this.parent = parent;
        this.enabled = ShyneClientSettings.cloudEnabled;
    }

    @Override
    protected void init() {
        int width = Math.min(PANEL_WIDTH, this.width - 24);
        panelX = (this.width - width) / 2;
        panelY = Math.max(16, (this.height - PANEL_HEIGHT) / 2);
        addRenderableWidget(Button.builder(enabledLabel(), button -> {
            enabled = !enabled;
            button.setMessage(enabledLabel());
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.status.enabled.tooltip")))
            .bounds(panelX + width - 82, panelY + 50, 68, 20).build());
        endpointBox = new EditBox(this.font, panelX + 14, panelY + 91, width - 28, 20, Component.translatable("screen.shyne_core.status.endpoint"));
        endpointBox.setMaxLength(240);
        endpointBox.setValue(ShyneClientSettings.cloudEndpoint);
        addRenderableWidget(endpointBox);
        int footerY = panelY + PANEL_HEIGHT - 30;
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.status.check"), button -> check())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.status.check.tooltip")))
            .bounds(panelX + 14, footerY, 116, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(panelX + width - 170, footerY, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose()).bounds(panelX + width - 92, footerY, 78, 20).build());
    }

    private Component enabledLabel() {
        return Component.translatable(enabled ? "screen.shyne_core.status.enabled" : "screen.shyne_core.status.disabled")
            .withStyle(enabled ? ChatFormatting.AQUA : ChatFormatting.GRAY);
    }

    private void applyFields() {
        ShyneClientSettings.cloudEnabled = enabled;
        ShyneClientSettings.cloudEndpoint = endpointBox.getValue().trim();
        ShyneClientSettings.save();
    }

    private void check() {
        applyFields();
        if (this.minecraft == null) return;
        ShyneStatusClient.check().whenComplete((result, error) -> this.minecraft.execute(this::rebuildWidgets));
        rebuildWidgets();
    }

    private void saveAndClose() {
        applyFields();
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int width = Math.min(PANEL_WIDTH, this.width - 24);
        graphics.fill(0, 0, this.width, this.height, 0x99030918);
        graphics.fillGradient(panelX, panelY, panelX + width, panelY + PANEL_HEIGHT, 0xF20A1630, 0xF2071026);
        graphics.outline(panelX, panelY, width, PANEL_HEIGHT, 0xFF22D7E8);
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, panelX + 12, panelY + 10, 0, 0, 28, 28, 1280, 1280);
        graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.AQUA), panelX + 49, panelY + 11, 0xFFFFFFFF, true);
        graphics.text(this.font, Component.translatable("screen.shyne_core.status.subtitle"), panelX + 49, panelY + 26, 0xFF91A7C6, false);
        graphics.fill(panelX + 12, panelY + 44, panelX + width - 12, panelY + 45, 0x6632D8EA);
        graphics.text(this.font, Component.translatable("screen.shyne_core.status.enabled.label"), panelX + 14, panelY + 56, 0xFFF3F7FF, false);
        graphics.text(this.font, Component.translatable("screen.shyne_core.status.endpoint"), panelX + 14, panelY + 78, 0xFF91A7C6, false);
        ShyneStatusClient.StatusResult result = ShyneStatusClient.lastResult();
        int color = switch (result.state()) {
            case SUCCESS -> 0xFF79D8B2;
            case ERROR -> 0xFFFF7F8B;
            case WORKING -> 0xFF8DECF3;
            case IDLE -> 0xFF7188A8;
        };
        String status = this.font.plainSubstrByWidth(result.message(), width - 34);
        graphics.text(this.font, Component.literal("● " + status), panelX + 16, panelY + 122, color, false);
        graphics.fill(panelX + 12, panelY + PANEL_HEIGHT - 38, panelX + width - 12, panelY + PANEL_HEIGHT - 37, 0x4432D8EA);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { if (this.minecraft != null) this.minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }
}

package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    private static final int PANEL_WIDTH = 450;
    private static final int PANEL_HEIGHT = 200;
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

        addRenderableWidget(ShyneButton.toggle(
            enabledLabel(),
            () -> enabled,
            button -> {
                enabled = !enabled;
                button.setMessage(enabledLabel());
            }
        ).bounds(panelX + width - 96, panelY + 50, 82, 20)
        .tooltipText(Component.translatable("screen.shyne_core.status.enabled.tooltip")));

        endpointBox = new EditBox(this.font, panelX + 16, panelY + 92, width - 32, 20, Component.translatable("screen.shyne_core.status.endpoint"));
        endpointBox.setMaxLength(240);
        endpointBox.setValue(ShyneClientSettings.cloudEndpoint);
        addRenderableWidget(endpointBox);

        int footerY = panelY + PANEL_HEIGHT - 32;
        addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.status.check"), button -> check())
            .bounds(panelX + 16, footerY, 116, 20)
            .tooltipText(Component.translatable("screen.shyne_core.status.check.tooltip")));

        addRenderableWidget(ShyneButton.secondary(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(panelX + width - 176, footerY, 76, 20));

        addRenderableWidget(ShyneButton.primary(Component.translatable("gui.done"), button -> saveAndClose())
            .bounds(panelX + width - 94, footerY, 80, 20));
    }

    private Component enabledLabel() {
        return Component.translatable(enabled ? "screen.shyne_core.status.enabled" : "screen.shyne_core.status.disabled");
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
        ShyneTheme.drawBackdrop(graphics, this.width, this.height);
        ShyneTheme.drawPanel(graphics, panelX, panelY, width, PANEL_HEIGHT, 44);

        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, panelX + 14, panelY + 8, 0, 0, 28, 28, 1280, 1280);
        graphics.text(this.font, this.title, panelX + 48, panelY + 12, ShyneTheme.TEXT_PRIMARY, true);
        graphics.text(this.font, Component.translatable("screen.shyne_core.status.subtitle"), panelX + 48, panelY + 28, ShyneTheme.TEXT_SECONDARY, false);

        graphics.text(this.font, Component.translatable("screen.shyne_core.status.enabled.label"), panelX + 16, panelY + 56, ShyneTheme.TEXT_PRIMARY, false);
        graphics.text(this.font, Component.translatable("screen.shyne_core.status.endpoint"), panelX + 16, panelY + 80, ShyneTheme.TEXT_SECONDARY, false);

        ShyneStatusClient.StatusResult result = ShyneStatusClient.lastResult();
        int color = switch (result.state()) {
            case SUCCESS -> ShyneTheme.SUCCESS;
            case ERROR -> ShyneTheme.DANGER;
            case WORKING -> ShyneTheme.ACCENT;
            case IDLE -> ShyneTheme.TEXT_SECONDARY;
        };
        String status = this.font.plainSubstrByWidth(result.message(), width - 34);
        graphics.text(this.font, Component.literal("● " + status), panelX + 16, panelY + 124, color, false);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

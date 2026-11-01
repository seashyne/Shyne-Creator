package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.input.DynamicAvatarInputRegistry;

import java.util.List;

/**
 * In-game Studio editor for key and mouse controls declared by currently loaded Avatars.
 */
public final class AvatarInputSettingsScreen extends Screen {
    private static final int PAGE_SIZE = 8;
    private final Screen parent;
    private int page;
    private String capturing;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public AvatarInputSettingsScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.inputs.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<DynamicAvatarInputRegistry.Snapshot> bindings = DynamicAvatarInputRegistry.snapshots();
        int pages = Math.max(1, (bindings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        panelWidth = Math.min(640, this.width - 20);
        panelHeight = 310;
        panelX = (this.width - panelWidth) / 2;
        panelY = Math.max(12, (this.height - panelHeight) / 2);

        int start = page * PAGE_SIZE;
        int end = Math.min(bindings.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            DynamicAvatarInputRegistry.Snapshot binding = bindings.get(i);
            int y = panelY + 54 + (i - start) * 26;
            boolean isCapturing = capturing != null && capturing.equals(binding.stableId());
            ShyneButton keyBtn;
            if (isCapturing) {
                keyBtn = ShyneButton.primary(Component.translatable("screen.shyne_core.inputs.press_key"), button -> {
                    capturing = null;
                    rebuildWidgets();
                }).bounds(panelX + panelWidth - 200, y + 2, 116, 20);
            } else {
                keyBtn = ShyneButton.secondary(binding.key(), button -> {
                    capturing = binding.stableId();
                    rebuildWidgets();
                }).bounds(panelX + panelWidth - 200, y + 2, 116, 20);
            }
            addRenderableWidget(keyBtn);

            addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.inputs.reset"), button -> {
                DynamicAvatarInputRegistry.reset(binding.stableId());
                capturing = null;
                rebuildWidgets();
            }).bounds(panelX + panelWidth - 80, y + 2, 68, 20));
        }

        int footerY = panelY + panelHeight - 32;
        addRenderableWidget(ShyneButton.secondary(Component.literal("<"), button -> { page--; rebuildWidgets(); })
            .bounds(panelX + 14, footerY, 32, 20).activeState(page > 0));
        addRenderableWidget(ShyneButton.secondary(Component.literal(">"), button -> { page++; rebuildWidgets(); })
            .bounds(panelX + 50, footerY, 32, 20).activeState(page + 1 < pages));

        addRenderableWidget(ShyneButton.danger(Component.translatable("screen.shyne_core.inputs.reset_all"), button -> {
            for (DynamicAvatarInputRegistry.Snapshot binding : bindings) DynamicAvatarInputRegistry.reset(binding.stableId());
            capturing = null;
            rebuildWidgets();
        }).bounds(panelX + panelWidth - 212, footerY, 98, 20));

        addRenderableWidget(ShyneButton.primary(Component.translatable("gui.done"), button -> onClose())
            .bounds(panelX + panelWidth - 110, footerY, 98, 20));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing == null) return super.keyPressed(event);
        if (event.key() == InputConstants.KEY_ESCAPE) {
            capturing = null;
        } else if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
            DynamicAvatarInputRegistry.unbind(capturing);
            capturing = null;
        } else {
            DynamicAvatarInputRegistry.setKey(capturing, InputConstants.Type.KEYBOARD.getOrCreate(event.key()));
            capturing = null;
        }
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (capturing != null) {
            DynamicAvatarInputRegistry.setKey(capturing, InputConstants.Type.MOUSE.getOrCreate(event.button()));
            capturing = null;
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ShyneTheme.drawBackdrop(graphics, this.width, this.height);
        ShyneTheme.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight, 46);

        graphics.text(this.font, this.title, panelX + 16, panelY + 14, ShyneTheme.TEXT_PRIMARY, true);
        graphics.text(this.font, Component.translatable("screen.shyne_core.inputs.subtitle"), panelX + 16, panelY + 31, ShyneTheme.TEXT_SECONDARY, false);

        List<DynamicAvatarInputRegistry.Snapshot> bindings = DynamicAvatarInputRegistry.snapshots();
        int start = page * PAGE_SIZE;
        int end = Math.min(bindings.size(), start + PAGE_SIZE);
        if (bindings.isEmpty()) {
            graphics.text(this.font, Component.translatable("screen.shyne_core.inputs.empty"), panelX + 16, panelY + 70, ShyneTheme.TEXT_SECONDARY, false);
        }
        for (int i = start; i < end; i++) {
            DynamicAvatarInputRegistry.Snapshot binding = bindings.get(i);
            int y = panelY + 54 + (i - start) * 26;
            boolean isCapturing = capturing != null && capturing.equals(binding.stableId());
            boolean hovered = mouseX >= panelX + 12 && mouseX < panelX + panelWidth - 12 && mouseY >= y && mouseY < y + 24;

            ShyneTheme.drawCard(graphics, panelX + 12, y, panelWidth - 24, 24, hovered, isCapturing);

            graphics.text(this.font, Component.literal(binding.label()), panelX + 18, y + 4, ShyneTheme.TEXT_PRIMARY, false);
            String owner = binding.avatarId() + " · " + binding.bindingId();
            graphics.text(this.font, Component.literal(owner), panelX + 18, y + 14, ShyneTheme.TEXT_DIM, false);
            if (!binding.conflicts().isEmpty()) {
                graphics.text(this.font, Component.translatable("screen.shyne_core.inputs.conflict"), panelX + panelWidth - 280, y + 8, ShyneTheme.DANGER, false);
            }
        }
        int pages = Math.max(1, (bindings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int footerY = panelY + panelHeight - 32;
        graphics.text(this.font, Component.literal((page + 1) + " / " + pages), panelX + 90, footerY + 6, ShyneTheme.TEXT_SECONDARY, false);
        graphics.text(this.font, Component.translatable("screen.shyne_core.inputs.unbind_hint"), panelX + 140, footerY + 6, ShyneTheme.TEXT_DIM, false);
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

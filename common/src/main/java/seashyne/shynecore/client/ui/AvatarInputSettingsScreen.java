package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import seashyne.shynecore.client.input.DynamicAvatarInputRegistry;

import java.util.List;

/** In-game editor for key and mouse controls declared by the currently loaded Avatars. */
public final class AvatarInputSettingsScreen extends Screen {
    private static final int PAGE_SIZE = 8;
    private final Screen parent;
    private int page;
    private String capturing;
    private int panelX;
    private int panelY;
    private int panelWidth;

    public AvatarInputSettingsScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.inputs.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<DynamicAvatarInputRegistry.Snapshot> bindings = DynamicAvatarInputRegistry.snapshots();
        int pages = Math.max(1, (bindings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        panelWidth = Math.min(620, this.width - 20);
        panelX = (this.width - panelWidth) / 2;
        panelY = Math.max(12, (this.height - 292) / 2);

        int start = page * PAGE_SIZE;
        int end = Math.min(bindings.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            DynamicAvatarInputRegistry.Snapshot binding = bindings.get(i);
            int y = panelY + 54 + (i - start) * 25;
            Component keyLabel = capturing != null && capturing.equals(binding.stableId())
                ? Component.translatable("screen.shyne_core.inputs.press_key").withStyle(ChatFormatting.YELLOW)
                : binding.key();
            addRenderableWidget(Button.builder(keyLabel, button -> {
                capturing = binding.stableId();
                rebuildWidgets();
            }).bounds(panelX + panelWidth - 196, y, 112, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.inputs.reset"), button -> {
                DynamicAvatarInputRegistry.reset(binding.stableId());
                capturing = null;
                rebuildWidgets();
            }).bounds(panelX + panelWidth - 80, y, 68, 20).build());
        }

        int footerY = panelY + 260;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuildWidgets(); })
            .bounds(panelX + 12, footerY, 34, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuildWidgets(); })
            .bounds(panelX + 50, footerY, 34, 20).build()).active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.inputs.reset_all"), button -> {
            for (DynamicAvatarInputRegistry.Snapshot binding : bindings) DynamicAvatarInputRegistry.reset(binding.stableId());
            capturing = null;
            rebuildWidgets();
        }).bounds(panelX + panelWidth - 212, footerY, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(panelX + panelWidth - 110, footerY, 98, 20).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing == null) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            capturing = null;
        } else if (event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_DELETE) {
            DynamicAvatarInputRegistry.unbind(capturing);
            capturing = null;
        } else {
            DynamicAvatarInputRegistry.setKey(capturing, InputConstants.Type.KEYSYM.getOrCreate(event.key()));
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
        graphics.fill(0, 0, this.width, this.height, 0xAA030918);
        graphics.fillGradient(panelX, panelY, panelX + panelWidth, panelY + 292, 0xF20A1630, 0xF2071026);
        graphics.outline(panelX, panelY, panelWidth, 292, 0xFF22D7E8);
        graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.AQUA), panelX + 12, panelY + 12, 0xFFFFFFFF, true);
        graphics.text(this.font, Component.translatable("screen.shyne_core.inputs.subtitle"), panelX + 12, panelY + 29, 0xFF91A7C6, false);

        List<DynamicAvatarInputRegistry.Snapshot> bindings = DynamicAvatarInputRegistry.snapshots();
        int start = page * PAGE_SIZE;
        int end = Math.min(bindings.size(), start + PAGE_SIZE);
        if (bindings.isEmpty()) {
            graphics.text(this.font, Component.translatable("screen.shyne_core.inputs.empty"), panelX + 12, panelY + 70, 0xFF91A7C6, false);
        }
        for (int i = start; i < end; i++) {
            DynamicAvatarInputRegistry.Snapshot binding = bindings.get(i);
            int y = panelY + 54 + (i - start) * 25;
            if ((i - start) % 2 == 0) graphics.fill(panelX + 8, y - 2, panelX + panelWidth - 8, y + 22, 0x2219BFD1);
            graphics.text(this.font, Component.literal(binding.label()), panelX + 14, y + 1, 0xFFF3F7FF, false);
            String owner = binding.avatarId() + " · " + binding.bindingId();
            graphics.text(this.font, Component.literal(owner), panelX + 14, y + 11, 0xFF7188A8, false);
            if (!binding.conflicts().isEmpty()) {
                graphics.text(this.font, Component.translatable("screen.shyne_core.inputs.conflict"), panelX + panelWidth - 270, y + 6, 0xFFFF6B7A, false);
            }
        }
        int pages = Math.max(1, (bindings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.text(this.font, Component.literal((page + 1) + " / " + pages), panelX + 92, panelY + 266, 0xFF91A7C6, false);
        graphics.text(this.font, Component.translatable("screen.shyne_core.inputs.unbind_hint"), panelX + 165, panelY + 266, 0xFF7188A8, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return true; }
}

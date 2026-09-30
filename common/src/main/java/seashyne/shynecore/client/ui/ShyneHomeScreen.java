package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The intentionally small, player-facing entry point for Shyne Creator.
 *
 * <p>It keeps the first choice clear: manage the avatar currently in use or
 * add one from disk.  Specialist surfaces stay available, but no longer
 * compete with the first-time setup flow in Minecraft's pause menu.</p>
 */
public final class ShyneHomeScreen extends Screen {
    private static final int SURFACE = 0xE40B1422;
    private static final int CARD = 0xE0142134;
    private static final int ACCENT = 0xFF42D8E9;
    private static final int MUTED = 0xFF93A5BB;
    private static final int GAP = 6;

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelWidth;

    public ShyneHomeScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.home.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        panelWidth = Math.max(300, Math.min(520, width - 20));
        int panelHeight = Math.max(222, Math.min(250, height - 20));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int innerX = panelX + 14;
        int innerWidth = panelWidth - 28;
        int half = (innerWidth - GAP) / 2;
        int y = panelY + 88;

        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.home.avatars"), ignored -> open(new AvatarManagerScreen(this)))
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.home.avatars.tooltip")))
            .bounds(innerX, y, innerWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.home.folder"), ignored -> openAvatarFolder())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.home.folder.tooltip")))
            .bounds(innerX, y + 25, innerWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.home.actions"), ignored -> open(new AvatarActionWheelScreen(this)))
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.home.actions.tooltip")))
            .bounds(innerX, y + 50, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.home.content"), ignored -> open(new ItemCatalogScreen(this)))
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.home.content.tooltip")))
            .bounds(innerX + half + GAP, y + 50, innerWidth - half - GAP, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.home.settings"), ignored -> open(new ShyneSettingsScreen(this)))
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.home.settings.tooltip")))
            .bounds(innerX, y + 75, innerWidth, 20).build());

        Minecraft client = Minecraft.getInstance();
        AvatarRuntime.refreshCatalogAsync().whenComplete((entries, error) -> client.execute(() -> {
            if (minecraft != null && minecraft.gui.screen() == this) rebuildWidgets();
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        int panelHeight = Math.max(222, Math.min(250, height - 20));
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SURFACE);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, ACCENT);

        graphics.text(font, Component.translatable("screen.shyne_core.home.title"), panelX + 14, panelY + 14, ACCENT, true);
        graphics.text(font, Component.translatable("screen.shyne_core.home.subtitle"), panelX + 14, panelY + 28, MUTED, false);
        graphics.fill(panelX + 14, panelY + 48, panelX + panelWidth - 14, panelY + 49, 0x663DD9E8);

        AvatarState active = AvatarRuntime.active();
        String activeName = active == null
            ? Component.translatable("screen.shyne_core.home.none").getString()
            : AvatarRuntime.catalog().stream().filter(entry -> entry.id().equalsIgnoreCase(active.avatarId()))
                .map(entry -> entry.name().isBlank() ? entry.id() : entry.name()).findFirst().orElse(active.avatarId());
        int avatarCount = AvatarRuntime.catalog().size();
        graphics.text(font, Component.translatable("screen.shyne_core.home.current", font.plainSubstrByWidth(activeName, panelWidth - 120)),
            panelX + 14, panelY + 59, 0xFFF3F7FF, false);
        graphics.text(font, Component.translatable("screen.shyne_core.home.count", avatarCount), panelX + 14, panelY + 72, MUTED, false);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void open(Screen screen) {
        if (minecraft != null) minecraft.gui.setScreen(screen);
    }

    private void openAvatarFolder() {
        Path folder = AvatarLoader.avatarsDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(folder);
            Blaze3D.openPath(folder);
        } catch (IOException ignored) {
            // The library remains usable even when the operating system cannot open a file browser.
        }
    }
}

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
    private static final int ACCENT = 0xFF42D8E9;
    private static final int MUTED = 0xFF93A5BB;
    private static final int GAP = 6;

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public ShyneHomeScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.home.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        panelWidth = Math.max(300, Math.min(500, width - 20));
        panelHeight = Math.max(196, Math.min(202, height - 20));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int innerX = panelX + 14;
        int innerWidth = panelWidth - 28;
        int half = (innerWidth - GAP) / 2;
        int y = panelY + 95;

        addHomeButton(Component.translatable("screen.shyne_core.home.avatars"), Component.translatable("screen.shyne_core.home.avatars.tooltip"),
            innerX, y, innerWidth, 22, true, ignored -> open(new AvatarManagerScreen(this)));
        addHomeButton(Component.translatable("screen.shyne_core.home.folder"), Component.translatable("screen.shyne_core.home.folder.tooltip"),
            innerX, y + 27, innerWidth, 18, false, ignored -> openAvatarFolder());
        addHomeButton(Component.translatable("screen.shyne_core.home.actions"), Component.translatable("screen.shyne_core.home.actions.tooltip"),
            innerX, y + 50, half, 18, false, ignored -> open(new AvatarActionWheelScreen(this)));
        addHomeButton(Component.translatable("screen.shyne_core.home.content"), Component.translatable("screen.shyne_core.home.content.tooltip"),
            innerX + half + GAP, y + 50, innerWidth - half - GAP, 18, false, ignored -> open(new ItemCatalogScreen(this)));
        addHomeButton(Component.translatable("screen.shyne_core.home.settings"), Component.translatable("screen.shyne_core.home.settings.tooltip"),
            innerX, y + 73, innerWidth, 18, false, ignored -> open(new ShyneSettingsScreen(this)));

        Minecraft client = Minecraft.getInstance();
        AvatarRuntime.refreshCatalogAsync().whenComplete((entries, error) -> client.execute(() -> {
            if (minecraft != null && minecraft.gui.screen() == this) rebuildWidgets();
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SURFACE);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, ACCENT);

        graphics.text(font, Component.translatable("screen.shyne_core.home.title"), panelX + 14, panelY + 14, ACCENT, true);
        graphics.text(font, Component.translatable("screen.shyne_core.home.subtitle"), panelX + 14, panelY + 28, MUTED, false);

        AvatarState active = AvatarRuntime.active();
        String activeName = active == null
            ? Component.translatable("screen.shyne_core.home.none").getString()
            : AvatarRuntime.catalog().stream().filter(entry -> entry.id().equalsIgnoreCase(active.avatarId()))
                .map(entry -> entry.name().isBlank() ? entry.id() : entry.name()).findFirst().orElse(active.avatarId());
        int avatarCount = AvatarRuntime.catalog().size();
        int cardX = panelX + 14;
        int cardY = panelY + 47;
        int cardW = panelWidth - 28;
        graphics.fill(cardX, cardY, cardX + cardW, cardY + 41, 0xB0102134);
        graphics.outline(cardX, cardY, cardW, 41, 0xFF294864);
        graphics.fill(cardX, cardY, cardX + 3, cardY + 41, ACCENT);
        graphics.fill(cardX + 10, cardY + 9, cardX + 32, cardY + 31, 0xFF10263A);
        graphics.outline(cardX + 10, cardY + 9, 22, 22, ACCENT);
        graphics.text(font, Component.literal("✦"), cardX + 17, cardY + 16, ACCENT, true);
        graphics.text(font, Component.translatable("screen.shyne_core.home.active_label"), cardX + 42, cardY + 7, MUTED, false);
        String clippedName = font.plainSubstrByWidth(activeName, Math.max(24, cardW - 54));
        graphics.text(font, Component.literal(clippedName), cardX + 42, cardY + 20, 0xFFF3F7FF, true);
        String count = Component.translatable("screen.shyne_core.home.count", avatarCount).getString();
        graphics.text(font, Component.literal(count), cardX + cardW - 10 - font.width(count), cardY + 7, MUTED, false);

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

    private void addHomeButton(Component label, Component tooltip, int x, int y, int width, int height,
                               boolean primary, Button.OnPress onPress) {
        HomeButton button = new HomeButton(x, y, width, height, label, primary, onPress);
        button.setTooltip(Tooltip.create(tooltip));
        addRenderableWidget(button);
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

    /** Theme-aware action button; avoids vanilla's large grey strips on the Home screen. */
    private static final class HomeButton extends Button {
        private final boolean primary;

        private HomeButton(int x, int y, int width, int height, Component message, boolean primary, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.primary = primary;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean focused = isHoveredOrFocused();
            int background = !active ? 0x66101C2B
                : primary ? (focused ? 0xFF216582 : 0xFF174A66)
                : (focused ? 0xFF1B3A52 : 0xFF12283B);
            int border = !active ? 0xFF33445A : (focused ? 0xFF72F1FF : (primary ? ACCENT : 0xFF365B76));
            int textColor = !active ? 0xFF6F8197 : (primary || focused ? 0xFFFFFFFF : 0xFFD8E7F4);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            graphics.outline(getX(), getY(), getWidth(), getHeight(), border);
            if (primary) graphics.fill(getX(), getY(), getX() + 3, getY() + getHeight(), ACCENT);
            Component message = getMessage();
            graphics.text(Minecraft.getInstance().font, message,
                getX() + (getWidth() - Minecraft.getInstance().font.width(message)) / 2,
                getY() + (getHeight() - 8) / 2, textColor, true);
        }
    }
}

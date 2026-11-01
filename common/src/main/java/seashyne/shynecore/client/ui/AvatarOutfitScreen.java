package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarOutfit;
import seashyne.shynecore.client.avatar.AvatarOutfitLoader;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.avatar.AvatarUserLayerPrefs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Studio Wardrobe Screen: Browse, switch, and preview alternate skins / outfits
 * with interactive 3D rotation, zoom, layer controls, and drag-and-drop import.
 */
public final class AvatarOutfitScreen extends Screen {
    private static final int ROW_HEIGHT = 34;

    private final Screen parent;
    private int requestedPage;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int previewX;
    private int previewWidth;
    private int listX;
    private int listWidth;
    private int contentTop;
    private int footerY;
    private int pageSize;
    private int currentPage;
    private int totalPages;
    private List<Row> rows = List.of();

    // Interactive 3D Model inspector
    private float previewYawOffset = 0f;
    private float previewPitchOffset = 0f;
    private float previewZoom = 1.0f;
    private boolean draggingPreview = false;

    public AvatarOutfitScreen(Screen parent) {
        this(parent, 0);
    }

    private AvatarOutfitScreen(Screen parent, int page) {
        super(Component.translatable("screen.shyne_core.outfits.title"));
        this.parent = parent;
        this.requestedPage = Math.max(0, page);
    }

    @Override
    protected void init() {
        AvatarState active = AvatarRuntime.active();
        if (active == null) {
            onClose();
            return;
        }

        List<Row> discovered = new ArrayList<>();
        discovered.add(new Row(
            AvatarOutfitLoader.DEFAULT_OUTFIT,
            Component.translatable("screen.shyne_core.outfits.default").getString(),
            "",
            true,
            ""
        ));
        for (AvatarOutfit outfit : active.outfits()) {
            String dimensions = outfit.valid() ? outfit.width() + "×" + outfit.height() : outfit.problem();
            discovered.add(new Row(outfit.id(), outfit.name(), dimensions, outfit.valid(), outfit.problem()));
        }
        rows = List.copyOf(discovered);

        panelWidth = Math.min(640, Math.max(300, this.width - 16));
        panelHeight = Math.min(360, Math.max(230, this.height - 16));
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        int innerX = panelX + 16;
        int innerWidth = panelWidth - 32;
        contentTop = panelY + 54;
        footerY = panelY + panelHeight - 28;
        previewWidth = Math.max(96, Math.min(156, innerWidth / 3));
        previewX = innerX;
        listX = previewX + previewWidth + 10;
        listWidth = innerX + innerWidth - listX - 6;
        pageSize = Math.max(1, Math.min(6, (footerY - contentTop - 25) / ROW_HEIGHT));
        totalPages = Math.max(1, (rows.size() + pageSize - 1) / pageSize);
        currentPage = Math.min(requestedPage, totalPages - 1);

        addQuickLayerToggles(active);
        addRows();
        addFooter(innerX, innerWidth);
    }

    private void addQuickLayerToggles(AvatarState active) {
        int toggleY = footerY - 26;
        int gap = 3;
        int btnW = Math.max(28, (previewWidth - gap * 2) / 3);

        boolean elytraVis = AvatarUserLayerPrefs.isLayerVisible(active, AvatarUserLayerPrefs.LAYER_ELYTRA);
        boolean armorVis = AvatarUserLayerPrefs.isLayerVisible(active, AvatarUserLayerPrefs.LAYER_ARMOR);
        boolean capeVis = AvatarUserLayerPrefs.isLayerVisible(active, AvatarUserLayerPrefs.LAYER_CAPE);

        ShyneButton elytraBtn = ShyneButton.toggle(
            Component.literal("Wing " + (elytraVis ? "✓" : "✗")),
            () -> AvatarUserLayerPrefs.isLayerVisible(AvatarRuntime.active(), AvatarUserLayerPrefs.LAYER_ELYTRA),
            ignored -> {
                AvatarState current = AvatarRuntime.active();
                if (current != null) {
                    AvatarUserLayerPrefs.toggleLayer(current, AvatarUserLayerPrefs.LAYER_ELYTRA);
                    rebuildWidgets();
                }
            }
        ).bounds(previewX, toggleY, btnW, 18)
        .tooltipText(Component.literal("Toggle Vanilla Elytra / Wings visibility (saved per avatar)"));

        ShyneButton armorBtn = ShyneButton.toggle(
            Component.literal("Armor " + (armorVis ? "✓" : "✗")),
            () -> AvatarUserLayerPrefs.isLayerVisible(AvatarRuntime.active(), AvatarUserLayerPrefs.LAYER_ARMOR),
            ignored -> {
                AvatarState current = AvatarRuntime.active();
                if (current != null) {
                    AvatarUserLayerPrefs.toggleLayer(current, AvatarUserLayerPrefs.LAYER_ARMOR);
                    rebuildWidgets();
                }
            }
        ).bounds(previewX + btnW + gap, toggleY, btnW, 18)
        .tooltipText(Component.literal("Toggle Vanilla Armor visibility (saved per avatar)"));

        ShyneButton capeBtn = ShyneButton.toggle(
            Component.literal("Cape " + (capeVis ? "✓" : "✗")),
            () -> AvatarUserLayerPrefs.isLayerVisible(AvatarRuntime.active(), AvatarUserLayerPrefs.LAYER_CAPE),
            ignored -> {
                AvatarState current = AvatarRuntime.active();
                if (current != null) {
                    AvatarUserLayerPrefs.toggleLayer(current, AvatarUserLayerPrefs.LAYER_CAPE);
                    rebuildWidgets();
                }
            }
        ).bounds(previewX + (btnW + gap) * 2, toggleY, previewWidth - (btnW + gap) * 2, 18)
        .tooltipText(Component.literal("Toggle Vanilla Cape visibility (saved per avatar)"));

        addRenderableWidget(elytraBtn);
        addRenderableWidget(armorBtn);
        addRenderableWidget(capeBtn);
    }

    private void addRows() {
        int start = currentPage * pageSize;
        int end = Math.min(rows.size(), start + pageSize);
        int buttonWidth = Math.min(76, Math.max(56, listWidth / 3));
        String selected = AvatarRuntime.selectedOutfitId();
        for (int i = start; i < end; i++) {
            Row row = rows.get(i);
            boolean active = row.id().equalsIgnoreCase(selected);
            ShyneButton select;
            if (active) {
                select = ShyneButton.ghost(Component.translatable("screen.shyne_core.outfits.active"), ignored -> {})
                    .bounds(listX + listWidth - buttonWidth - 5, contentTop + (i - start) * ROW_HEIGHT + 5, buttonWidth, 20)
                    .activeState(false)
                    .tooltipText(Component.translatable("screen.shyne_core.outfits.active.tooltip", row.name()));
            } else {
                select = ShyneButton.primary(Component.translatable("screen.shyne_core.outfits.use"), ignored -> select(row.id()))
                    .bounds(listX + listWidth - buttonWidth - 5, contentTop + (i - start) * ROW_HEIGHT + 5, buttonWidth, 20)
                    .activeState(row.valid())
                    .tooltipText(row.valid()
                        ? Component.translatable("screen.shyne_core.outfits.use.tooltip", row.name())
                        : Component.literal(row.problem()));
            }
            addRenderableWidget(select);
        }
    }

    private void addFooter(int innerX, int innerWidth) {
        ShyneButton folder = ShyneButton.secondary(
            Component.translatable("screen.shyne_core.outfits.folder"),
            ignored -> openFolder()
        ).tooltipText(Component.translatable("screen.shyne_core.outfits.folder.tooltip"));

        ShyneButton refresh = ShyneButton.secondary(
            Component.translatable("screen.shyne_core.outfits.refresh"),
            ignored -> refreshOutfits()
        ).tooltipText(Component.translatable("screen.shyne_core.outfits.refresh.tooltip"));

        ShyneButton back = ShyneButton.primary(
            Component.translatable("gui.back"),
            ignored -> onClose()
        );

        List<ShyneButton> buttons = List.of(folder, refresh, back);
        int gap = 6;
        int width = (innerWidth - gap * (buttons.size() - 1)) / buttons.size();
        for (int i = 0; i < buttons.size(); i++) {
            ShyneButton button = buttons.get(i);
            button.bounds(innerX + i * (width + gap), footerY, width, 20);
            addRenderableWidget(button);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int mx = (int) event.x();
        int my = (int) event.y();
        int previewBottom = footerY - 30;
        if (event.button() == 0 && mx >= previewX && mx < previewX + previewWidth
                && my >= contentTop && my < previewBottom) {
            if (doubleClick) {
                previewYawOffset = 0f;
                previewPitchOffset = 0f;
                previewZoom = 1.0f;
            } else {
                draggingPreview = true;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingPreview && event.button() == 0) {
            previewYawOffset = Math.max(-240f, Math.min(240f, previewYawOffset - (float) dragX * 2.2f));
            previewPitchOffset = Math.max(-160f, Math.min(160f, previewPitchOffset - (float) dragY * 1.8f));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingPreview && event.button() == 0) {
            draggingPreview = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int previewBottom = footerY - 30;
        if (verticalAmount != 0 && mouseX >= previewX && mouseX < previewX + previewWidth
                && mouseY >= contentTop && mouseY < previewBottom) {
            previewZoom = Math.max(0.6f, Math.min(2.4f, previewZoom + (verticalAmount > 0 ? 0.15f : -0.15f)));
            return true;
        }
        if (verticalAmount != 0 && totalPages > 1) {
            int newPage = Math.max(0, Math.min(totalPages - 1, currentPage + (verticalAmount < 0 ? 1 : -1)));
            if (newPage != currentPage) {
                openPage(newPage);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ShyneTheme.drawBackdrop(graphics, this.width, this.height);
        ShyneTheme.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight, 46);

        graphics.text(this.font, Component.translatable("screen.shyne_core.outfits.title"), panelX + 16, panelY + 14, ShyneTheme.TEXT_PRIMARY, true);
        graphics.text(this.font, Component.translatable("screen.shyne_core.outfits.subtitle", Math.max(0, rows.size() - 1)), panelX + 16, panelY + 31, ShyneTheme.TEXT_SECONDARY, false);

        int previewBottom = footerY - 30;
        ShyneTheme.drawInset(graphics, previewX, contentTop, previewWidth, previewBottom - contentTop, ShyneTheme.BORDER_ACCENT);

        UiViewportBounds preview = UiViewportBounds.clip(
            previewX + 4, contentTop + 4, previewX + previewWidth - 4, previewBottom - 4,
            graphics.guiWidth(), graphics.guiHeight()
        );
        if (this.minecraft != null && this.minecraft.player != null && preview.drawable()) {
            int baseScale = Math.max(18, Math.min(38, (previewBottom - contentTop) / 4));
            int zoomedScale = Math.max(12, Math.min(64, Math.round(baseScale * previewZoom)));
            float lookX = mouseX + previewYawOffset;
            float lookY = mouseY + previewPitchOffset;
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics, preview.left(), preview.top(), preview.right(), preview.bottom(),
                zoomedScale, 0.0625F, lookX, lookY, this.minecraft.player
            );
        }

        // Interactive hint inside 3D viewport when hovered
        boolean overViewport = mouseX >= previewX + 4 && mouseX < previewX + previewWidth - 4
            && mouseY >= contentTop + 4 && mouseY < previewBottom - 4;
        if (overViewport) {
            Component hint = Component.literal("↕ Drag · Zoom");
            int hintW = this.font.width(hint);
            graphics.fill(previewX + previewWidth - hintW - 10, contentTop + 6, previewX + previewWidth - 4, contentTop + 18, 0x88060F1A);
            graphics.text(this.font, hint, previewX + previewWidth - hintW - 7, contentTop + 8, ShyneTheme.ACCENT_BRIGHT, false);
        }

        int start = currentPage * pageSize;
        int end = Math.min(rows.size(), start + pageSize);
        String selected = AvatarRuntime.selectedOutfitId();
        for (int i = start; i < end; i++) {
            Row row = rows.get(i);
            boolean active = row.id().equalsIgnoreCase(selected);
            int y = contentTop + (i - start) * ROW_HEIGHT;
            boolean hovered = mouseX >= listX && mouseX < listX + listWidth && mouseY >= y && mouseY < y + 30;
            ShyneTheme.drawCard(graphics, listX, y, listWidth, 30, hovered, active);

            int textRoom = Math.max(36, listWidth - Math.min(86, Math.max(66, listWidth / 3)) - 18);
            String title = this.font.plainSubstrByWidth(row.name(), textRoom);
            String detail = row.id().equals(AvatarOutfitLoader.DEFAULT_OUTFIT)
                ? Component.translatable("screen.shyne_core.outfits.default.detail").getString()
                : row.detail();
            detail = this.font.plainSubstrByWidth(detail, textRoom);
            graphics.text(this.font, Component.literal(title), listX + 10, y + 5, ShyneTheme.TEXT_PRIMARY, false);
            graphics.text(this.font, Component.literal(detail), listX + 10, y + 17, row.valid() ? ShyneTheme.TEXT_SECONDARY : ShyneTheme.DANGER, false);
        }

        Component page = totalPages > 1
            ? Component.literal("Scroll ↕  " + (currentPage + 1) + " / " + totalPages)
            : Component.translatable("screen.shyne_core.avatars.page", currentPage + 1, totalPages);
        graphics.text(this.font, page, panelX + panelWidth - 16 - this.font.width(page), panelY + 31, ShyneTheme.TEXT_SECONDARY, false);

        if (totalPages > 1) {
            int trackX = panelX + panelWidth - 16 - 3;
            int trackTop = contentTop;
            int trackBottom = footerY - 8;
            ShyneTheme.drawScrollbar(graphics, trackX, trackTop, trackBottom, currentPage, totalPages - 1, 1, totalPages);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void select(String outfitId) {
        AvatarRuntime.selectOutfit(outfitId, Minecraft.getInstance());
        openPage(currentPage);
    }

    private void openFolder() {
        AvatarState active = AvatarRuntime.active();
        if (active == null) return;
        Path folder = AvatarOutfitLoader.outfitDir(active.rootDir());
        try {
            Files.createDirectories(folder);
            Blaze3D.openPath(folder);
        } catch (IOException error) {
            ShyneCore.LOGGER.error("[AvatarOutfit] Could not open {}: {}", folder, error.getMessage());
        }
    }

    private void refreshOutfits() {
        AvatarRuntime.refreshOutfits();
        openPage(currentPage);
    }

    private void openPage(int page) {
        this.requestedPage = Math.max(0, Math.min(page, totalPages - 1));
        this.currentPage = this.requestedPage;
        rebuildWidgets();
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        AvatarState active = AvatarRuntime.active();
        if (active == null || files == null || files.isEmpty()) return;
        Path folder = AvatarOutfitLoader.outfitDir(active.rootDir());
        int copied = 0;
        try {
            Files.createDirectories(folder);
            for (Path p : files) {
                if (p != null && Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
                    Files.copy(p, folder.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
        } catch (IOException e) {
            ShyneCore.LOGGER.warn("[AvatarOutfit] Failed to import dropped outfit PNG: {}", e.getMessage());
        }
        if (copied > 0) {
            refreshOutfits();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private record Row(String id, String name, String detail, boolean valid, String problem) {}
}

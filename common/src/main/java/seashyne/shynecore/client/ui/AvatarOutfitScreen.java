package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarOutfit;
import seashyne.shynecore.client.avatar.AvatarOutfitLoader;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AvatarOutfitScreen extends Screen {
    private static final int ROW_HEIGHT = 34;
    private static final int SURFACE = 0xF20B1222;
    private static final int SURFACE_RAISED = 0xE8142034;
    private static final int ACCENT = 0xFF3DD9E8;
    private static final int TEXT_MUTED = 0xFF91A0B7;

    private final Screen parent;
    private final int requestedPage;
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
        // Activation already discovered outfits. Reuse that snapshot while paging
        // and only touch the file system when the user presses Refresh.
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

        panelWidth = Math.min(620, Math.max(300, this.width - 16));
        panelHeight = Math.min(340, Math.max(230, this.height - 16));
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        int innerX = panelX + 16;
        int innerWidth = panelWidth - 32;
        contentTop = panelY + 54;
        footerY = panelY + panelHeight - 28;
        previewWidth = Math.max(92, Math.min(150, innerWidth / 3));
        previewX = innerX;
        listX = previewX + previewWidth + 10;
        listWidth = innerX + innerWidth - listX;
        pageSize = Math.max(1, Math.min(6, (footerY - contentTop - 25) / ROW_HEIGHT));
        totalPages = Math.max(1, (rows.size() + pageSize - 1) / pageSize);
        currentPage = Math.min(requestedPage, totalPages - 1);

        addRows();
        addFooter(innerX, innerWidth);
    }

    private void addRows() {
        int start = currentPage * pageSize;
        int end = Math.min(rows.size(), start + pageSize);
        int buttonWidth = Math.min(76, Math.max(56, listWidth / 3));
        String selected = AvatarRuntime.selectedOutfitId();
        for (int i = start; i < end; i++) {
            Row row = rows.get(i);
            boolean active = row.id().equalsIgnoreCase(selected);
            Button select = Button.builder(
                Component.translatable(active ? "screen.shyne_core.outfits.active" : "screen.shyne_core.outfits.use"),
                ignored -> select(row.id())
            ).tooltip(Tooltip.create(row.valid()
                ? Component.translatable(active ? "screen.shyne_core.outfits.active.tooltip" : "screen.shyne_core.outfits.use.tooltip", row.name())
                : Component.literal(row.problem())
            )).bounds(listX + listWidth - buttonWidth - 5, contentTop + (i - start) * ROW_HEIGHT + 5, buttonWidth, 20).build();
            select.active = row.valid() && !active;
            addRenderableWidget(select);
        }
    }

    private void addFooter(int innerX, int innerWidth) {
        Button previous = Button.builder(Component.literal("‹"), ignored -> openPage(currentPage - 1))
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.previous"))).build();
        previous.active = currentPage > 0;
        Button next = Button.builder(Component.literal("›"), ignored -> openPage(currentPage + 1))
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.next"))).build();
        next.active = currentPage + 1 < totalPages;
        Button folder = Button.builder(Component.translatable("screen.shyne_core.outfits.folder"), ignored -> openFolder())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.outfits.folder.tooltip"))).build();
        Button refresh = Button.builder(Component.translatable("screen.shyne_core.outfits.refresh"), ignored -> refreshOutfits())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.outfits.refresh.tooltip"))).build();
        Button back = Button.builder(Component.translatable("gui.back"), ignored -> onClose()).build();

        List<Button> buttons = List.of(previous, next, folder, refresh, back);
        int gap = 4;
        int width = (innerWidth - gap * (buttons.size() - 1)) / buttons.size();
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            button.setX(innerX + i * (width + gap));
            button.setY(footerY);
            button.setWidth(width);
            button.setHeight(20);
            addRenderableWidget(button);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xA8030710);
        graphics.fill(panelX + 3, panelY + 4, panelX + panelWidth + 3, panelY + panelHeight + 4, 0x66000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SURFACE);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, ACCENT);

        graphics.text(this.font, Component.translatable("screen.shyne_core.outfits.title"), panelX + 16, panelY + 14, 0xFFF4F7FC, true);
        graphics.text(this.font, Component.translatable("screen.shyne_core.outfits.subtitle", Math.max(0, rows.size() - 1)), panelX + 16, panelY + 31, TEXT_MUTED, false);

        int previewBottom = footerY - 8;
        graphics.fill(previewX, contentTop, previewX + previewWidth, previewBottom, 0xFF101B2C);
        graphics.outline(previewX, contentTop, previewWidth, previewBottom - contentTop, 0x8841D7E5);
        UiViewportBounds preview = UiViewportBounds.clip(
            previewX + 4, contentTop + 4, previewX + previewWidth - 4, previewBottom - 4,
            graphics.guiWidth(), graphics.guiHeight()
        );
        if (this.minecraft != null && this.minecraft.player != null && preview.drawable()) {
            int scale = Math.max(18, Math.min(38, (previewBottom - contentTop) / 4));
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics, preview.left(), preview.top(), preview.right(), preview.bottom(),
                scale, 0.0625F, mouseX, mouseY, this.minecraft.player
            );
        }

        int start = currentPage * pageSize;
        int end = Math.min(rows.size(), start + pageSize);
        String selected = AvatarRuntime.selectedOutfitId();
        for (int i = start; i < end; i++) {
            Row row = rows.get(i);
            boolean active = row.id().equalsIgnoreCase(selected);
            int y = contentTop + (i - start) * ROW_HEIGHT;
            graphics.fill(listX, y, listX + listWidth, y + 30, active ? 0xFF152C3D : SURFACE_RAISED);
            graphics.outline(listX, y, listWidth, 30, active ? 0x9941D7E5 : 0x66445A78);
            if (active) graphics.fill(listX, y, listX + 3, y + 30, ACCENT);
            int textRoom = Math.max(36, listWidth - Math.min(86, Math.max(66, listWidth / 3)) - 18);
            String title = this.font.plainSubstrByWidth(row.name(), textRoom);
            String detail = row.id().equals(AvatarOutfitLoader.DEFAULT_OUTFIT)
                ? Component.translatable("screen.shyne_core.outfits.default.detail").getString()
                : row.detail();
            detail = this.font.plainSubstrByWidth(detail, textRoom);
            graphics.text(this.font, Component.literal(title), listX + 10, y + 5, 0xFFF3F7FF, false);
            graphics.text(this.font, Component.literal(detail), listX + 10, y + 17, row.valid() ? TEXT_MUTED : 0xFFFF8793, false);
        }

        Component page = Component.translatable("screen.shyne_core.avatars.page", currentPage + 1, totalPages);
        graphics.text(this.font, page, panelX + panelWidth - 16 - this.font.width(page), panelY + 31, TEXT_MUTED, false);
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
            Util.getPlatform().openPath(folder);
        } catch (IOException error) {
            ShyneCore.LOGGER.error("[AvatarOutfit] Could not open {}: {}", folder, error.getMessage());
        }
    }

    private void refreshOutfits() {
        AvatarRuntime.refreshOutfits();
        openPage(currentPage);
    }

    private void openPage(int page) {
        if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarOutfitScreen(parent, page));
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

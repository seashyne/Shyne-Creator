package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarActivationResult;
import seashyne.shynecore.client.avatar.AvatarCatalogEntry;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.avatar.AvatarUserLayerPrefs;
import seashyne.shynecore.client.avatar.importer.AvatarDragDropInstaller;
import seashyne.shynecore.client.render.AvatarIconTextures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Primary Shyne Studio hub for browsing, previewing, equipping, and configuring
 * local & Figura-imported Avatars, unified with top workspace tabs.
 */
public class AvatarManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 36;
    private static final int CARD_HEIGHT = 32;
    private static final int[] AVATAR_ICON_COLORS = {
        0xFF42D7E8, 0xFF7C9CFF, 0xFFB98BFF, 0xFF79D8B2, 0xFFFFC66D, 0xFFFF8FA3
    };

    private final Screen parent;
    private int page;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int headerNavLeftX;

    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;
    private int viewportBottom;

    private int listX;
    private int listWidth;
    private int contentTop;
    private int pagerY;
    private int footerTop;

    private int pageSize;
    private int currentPage;
    private int totalPages;
    private boolean catalogRefreshRequested;
    private boolean suppressSearchResponder;
    private String searchText = "";
    private EditBox searchBox;
    private ShyneButton clearSearchBtn;

    private final List<AbstractWidget> dynamicRowWidgets = new ArrayList<>();
    private List<AvatarCatalogEntry> allCatalog = List.of();
    private List<AvatarCatalogEntry> catalog = List.of();
    private AvatarState active;

    // Interactive 3D preview controls (drag to aim/rotate, scroll to zoom, double-click to reset)
    private boolean draggingPreview;
    private float previewYawOffset;
    private float previewPitchOffset;
    private float previewZoom = 1.0f;

    public AvatarManagerScreen(Screen parent) {
        this(parent, 0, "");
    }

    private AvatarManagerScreen(Screen parent, int page, String searchText) {
        super(Component.translatable("screen.shyne_core.avatars.title"));
        this.parent = parent;
        this.page = Math.max(0, page);
        this.searchText = searchText == null ? "" : searchText;
    }

    @Override
    protected void init() {
        boolean restoreSearchFocus = searchBox != null && searchBox.isFocused();
        int restoreCursor = searchBox != null ? searchBox.getCursorPosition() : searchText.length();

        clearWidgets();
        dynamicRowWidgets.clear();

        allCatalog = AvatarRuntime.catalog();
        active = AvatarRuntime.active();

        panelWidth = Math.max(310, Math.min(780, this.width - 16));
        panelHeight = Math.max(220, Math.min(392, this.height - 16));
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;

        int innerX = panelX + 14;
        int innerWidth = panelWidth - 28;
        footerTop = panelY + panelHeight - 28;
        pagerY = footerTop - 22;

        // Top unified Shyne Studio navigation tabs
        headerNavLeftX = ShyneNavBar.addTabs(
            this.minecraft,
            this,
            this.parent,
            ShyneNavBar.Section.AVATARS,
            panelX + panelWidth - 12,
            panelY + 10,
            Math.max(120, panelWidth - 170),
            this::addRenderableWidget
        );

        previewWidth = Math.max(114, Math.min(176, innerWidth * 28 / 100));
        previewX = innerX;
        previewY = panelY + 46;
        previewHeight = footerTop - 8 - previewY;

        listX = previewX + previewWidth + 12;
        listWidth = innerX + innerWidth - listX;
        contentTop = panelY + 72;

        pageSize = Math.max(1, Math.min(6, (pagerY - contentTop) / ROW_HEIGHT));

        addSearchControls(restoreSearchFocus, restoreCursor);
        addPreviewControls();
        addFooterControls(innerX, innerWidth);
        rebuildFilteredRows();
        requestCatalogRefresh();
    }

    private void addSearchControls(boolean restoreFocus, int restoreCursor) {
        int clearWidth = 22;
        int boxWidth = Math.max(60, listWidth - clearWidth - 4);
        searchBox = new EditBox(
            this.font,
            listX,
            panelY + 46,
            boxWidth,
            19,
            Component.translatable("screen.shyne_core.avatars.search")
        );
        searchBox.setHint(Component.translatable("screen.shyne_core.avatars.search"));
        suppressSearchResponder = true;
        searchBox.setValue(searchText);
        if (restoreFocus) {
            searchBox.setFocused(true);
            searchBox.setCursorPosition(Math.min(restoreCursor, searchText.length()));
        }
        suppressSearchResponder = false;
        searchBox.setResponder(value -> {
            if (suppressSearchResponder) return;
            searchText = value == null ? "" : value;
            page = 0;
            if (clearSearchBtn != null) {
                clearSearchBtn.activeState(!searchText.isEmpty());
            }
            rebuildFilteredRows();
        });
        addRenderableWidget(searchBox);
        if (restoreFocus) {
            setFocused(searchBox);
        }

        clearSearchBtn = ShyneButton.ghost(Component.literal("✕"), ignored -> clearSearch())
            .bounds(listX + boxWidth + 4, panelY + 46, clearWidth, 19)
            .tooltipText(Component.translatable("screen.shyne_core.ui.clear.tooltip"))
            .activeState(!searchText.isEmpty());
        addRenderableWidget(clearSearchBtn);
    }

    private void addPreviewControls() {
        boolean hasActive = active != null;
        int controlsHeight = hasActive ? 42 : 0;
        viewportBottom = previewY + previewHeight - 32 - controlsHeight;

        if (!hasActive || previewHeight < 120) {
            return;
        }

        int pad = 6;
        int innerW = previewWidth - pad * 2;
        int row1Y = previewY + previewHeight - 42;
        int row2Y = previewY + previewHeight - 22;

        int gap = 3;
        int pillW = (innerW - gap * 2) / 3;
        int lastPillW = innerW - (pillW + gap) * 2;

        addRenderableWidget(ShyneButton.toggle(
            Component.translatable("screen.shyne_core.avatars.mask"),
            () -> AvatarRuntime.active() != null && AvatarRuntime.active().firstPersonMasking(),
            btn -> {
                AvatarState current = AvatarRuntime.active();
                if (current != null) current.setFirstPersonMasking(!current.firstPersonMasking());
            }
        ).bounds(previewX + pad, row1Y, pillW, 17)
            .tooltipText(Component.translatable("screen.shyne_core.avatars.mask.tooltip")));

        addRenderableWidget(ShyneButton.toggle(
            Component.translatable("screen.shyne_core.avatars.wings"),
            () -> AvatarUserLayerPrefs.isLayerVisible(AvatarRuntime.active(), AvatarUserLayerPrefs.LAYER_ELYTRA),
            btn -> {
                AvatarState current = AvatarRuntime.active();
                if (current != null) AvatarUserLayerPrefs.toggleLayer(current, AvatarUserLayerPrefs.LAYER_ELYTRA);
            }
        ).bounds(previewX + pad + pillW + gap, row1Y, pillW, 17)
            .tooltipText(Component.translatable("screen.shyne_core.avatars.wings.tooltip")));

        addRenderableWidget(ShyneButton.toggle(
            Component.translatable("screen.shyne_core.avatars.armor"),
            () -> AvatarUserLayerPrefs.isLayerVisible(AvatarRuntime.active(), AvatarUserLayerPrefs.LAYER_ARMOR),
            btn -> {
                AvatarState current = AvatarRuntime.active();
                if (current != null) AvatarUserLayerPrefs.toggleLayer(current, AvatarUserLayerPrefs.LAYER_ARMOR);
            }
        ).bounds(previewX + pad + (pillW + gap) * 2, row1Y, lastPillW, 17)
            .tooltipText(Component.translatable("screen.shyne_core.avatars.armor.tooltip")));

        int halfW = (innerW - gap) / 2;
        int secondHalfW = innerW - halfW - gap;
        addRenderableWidget(ShyneButton.secondary(
            Component.translatable("screen.shyne_core.avatars.outfit"),
            btn -> {
                if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarOutfitScreen(this));
            }
        ).bounds(previewX + pad, row2Y, halfW, 17)
            .tooltipText(Component.translatable("screen.shyne_core.avatars.outfit.tooltip")));

        addRenderableWidget(ShyneButton.secondary(
            Component.translatable("screen.shyne_core.avatars.permissions"),
            btn -> {
                AvatarState current = AvatarRuntime.active();
                if (current != null && this.minecraft != null) {
                    this.minecraft.gui.setScreen(new AvatarPermissionScreen(
                        this,
                        current.avatarId(),
                        resolveDisplayName(current.avatarId()),
                        current.requestedPermissions(),
                        this::rebuildWidgets
                    ));
                }
            }
        ).bounds(previewX + pad + halfW + gap, row2Y, secondHalfW, 17)
            .tooltipText(Component.translatable("screen.shyne_core.avatars.permissions.tooltip")));
    }

    private void addFooterControls(int innerX, int innerWidth) {
        int backW = 74;
        addRenderableWidget(ShyneButton.secondary(Component.translatable("gui.back"), btn -> onClose())
            .bounds(innerX, footerTop, backW, 20)
            .tooltipText(Component.translatable("screen.shyne_core.ui.back.tooltip")));

        List<ShyneButton> tools = new ArrayList<>();
        if (active != null) {
            tools.add(ShyneButton.ghost(Component.translatable("screen.shyne_core.avatars.vanilla"), btn -> {
                AvatarRuntime.deactivate(Minecraft.getInstance());
                rebuildWidgets();
            }).tooltipText(Component.translatable("screen.shyne_core.avatars.vanilla.tooltip")));
        }

        tools.add(ShyneButton.secondary(
            Component.translatable(active == null ? "screen.shyne_core.avatars.scan" : "screen.shyne_core.avatars.reload"),
            btn -> {
                if (AvatarRuntime.active() == null) {
                    rescanCatalog();
                } else {
                    AvatarRuntime.reloadActive(Minecraft.getInstance());
                    rebuildWidgets();
                }
            }
        ).tooltipText(Component.translatable(active == null
            ? "screen.shyne_core.avatars.scan.tooltip"
            : "screen.shyne_core.avatars.reload.tooltip")));

        tools.add(ShyneButton.secondary(Component.translatable("screen.shyne_core.avatars.importer"), btn -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarImporterScreen(this));
        }).tooltipText(Component.translatable("screen.shyne_core.avatars.importer.tooltip")));

        tools.add(ShyneButton.secondary(Component.translatable("screen.shyne_core.avatars.validate"), btn -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarValidationScreen(this));
        }).tooltipText(Component.translatable("screen.shyne_core.avatars.validate.tooltip")));

        tools.add(ShyneButton.secondary(Component.translatable("screen.shyne_core.avatars.folder"), btn -> openAvatarFolder())
            .tooltipText(Component.translatable("screen.shyne_core.avatars.folder.tooltip")));

        int gap = 5;
        int availableRight = Math.max(120, innerWidth - backW - 12);
        int maxToolTotal = Math.min(availableRight, tools.size() * 94 + (tools.size() - 1) * gap);
        int toolW = Math.max(44, (maxToolTotal - gap * (tools.size() - 1)) / tools.size());
        int startX = innerX + innerWidth - (toolW * tools.size() + gap * (tools.size() - 1));
        for (int i = 0; i < tools.size(); i++) {
            ShyneButton tool = tools.get(i);
            tool.bounds(startX + i * (toolW + gap), footerTop, toolW, 20);
            addRenderableWidget(tool);
        }
    }

    private void rebuildFilteredRows() {
        for (AbstractWidget widget : dynamicRowWidgets) {
            removeWidget(widget);
        }
        dynamicRowWidgets.clear();

        catalog = filterCatalog(allCatalog, searchText.trim());
        totalPages = Math.max(1, (catalog.size() + pageSize - 1) / pageSize);
        currentPage = Math.min(Math.max(0, page), totalPages - 1);
        page = currentPage;

        int actionWidth = Math.min(62, Math.max(46, listWidth / 5));
        int rightEdge = listX + listWidth - 10;
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, catalog.size());

        for (int i = start; i < end; i++) {
            AvatarCatalogEntry entry = catalog.get(i);
            boolean isActive = isActive(entry);
            int btnY = contentTop + (i - start) * ROW_HEIGHT + 6;

            if (!entry.valid()) {
                ShyneButton errBtn = ShyneButton.danger(Component.literal("!"), ignored -> {
                    if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarValidationScreen(this));
                }).bounds(rightEdge - 24, btnY, 24, 20)
                    .tooltipText(Component.literal(entry.problem()));
                addRowWidget(errBtn);
                continue;
            }

            int cursorRight = rightEdge;
            if (isActive) {
                ShyneButton outfitBtn = ShyneButton.secondary(
                    Component.translatable("screen.shyne_core.avatars.outfit"),
                    btn -> {
                        if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarOutfitScreen(this));
                    }
                ).bounds(cursorRight - actionWidth, btnY, actionWidth, 20)
                    .tooltipText(Component.translatable("screen.shyne_core.avatars.outfit.tooltip"));
                addRowWidget(outfitBtn);
                cursorRight -= (actionWidth + 4);

                ShyneButton activePill = ShyneButton.successPill(
                    Component.translatable("screen.shyne_core.avatars.active"),
                    ignored -> {}
                ).bounds(cursorRight - actionWidth, btnY, actionWidth, 20)
                    .tooltipText(Component.translatable("screen.shyne_core.avatars.active.tooltip", entry.name()))
                    .activeState(false);
                addRowWidget(activePill);
                cursorRight -= (actionWidth + 4);
            } else {
                ShyneButton useBtn = ShyneButton.primary(
                    Component.translatable("screen.shyne_core.avatars.use"),
                    btn -> activate(entry)
                ).bounds(cursorRight - actionWidth, btnY, actionWidth, 20)
                    .tooltipText(Component.translatable("screen.shyne_core.avatars.use.tooltip", entry.name()));
                addRowWidget(useBtn);
                cursorRight -= (actionWidth + 4);
            }

            long warnCount = entry.validation().warningCount();
            if (warnCount > 0) {
                String warnText = "!" + warnCount;
                int warnW = Math.max(24, this.font.width(warnText) + 10);
                ShyneButton warnBtn = ShyneButton.ghost(Component.literal(warnText), ignored -> {
                    if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarValidationScreen(this));
                }).bounds(cursorRight - warnW, btnY, warnW, 20)
                    .tooltipText(Component.translatable("screen.shyne_core.avatars.validate.tooltip"));
                addRowWidget(warnBtn);
            }
        }
    }

    private void addRowWidget(ShyneButton button) {
        dynamicRowWidgets.add(button);
        addRenderableWidget(button);
    }

    private void requestCatalogRefresh() {
        if (catalogRefreshRequested) return;
        catalogRefreshRequested = true;
        Minecraft client = Minecraft.getInstance();
        AvatarRuntime.refreshCatalogAsync().whenComplete((entries, error) -> client.execute(() -> {
            if (this.minecraft != null && this.minecraft.gui.screen() == this) {
                allCatalog = AvatarRuntime.catalog();
                rebuildFilteredRows();
            }
        }));
    }

    private String resolveDisplayName(String avatarId) {
        if (avatarId == null || avatarId.isBlank()) {
            return Component.translatable("screen.shyne_core.avatars.vanilla.name").getString();
        }
        return allCatalog.stream()
            .filter(entry -> entry.id().equalsIgnoreCase(avatarId))
            .map(entry -> entry.name().isBlank() ? avatarId : entry.name())
            .findFirst()
            .orElse(avatarId);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ShyneTheme.drawBackdrop(graphics, this.width, this.height);
        ShyneTheme.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight, 38);

        // Header title & ready count
        int maxHeaderTextWidth = Math.max(60, headerNavLeftX - (panelX + 14) - 8);
        String headerTitle = ShyneTheme.ellipsize(
            this.font,
            "✦ " + Component.translatable("screen.shyne_core.avatars.title").getString(),
            maxHeaderTextWidth
        );
        graphics.text(this.font, Component.literal(headerTitle), panelX + 14, panelY + 9, ShyneTheme.TEXT_PRIMARY, true);

        int availableCount = (int) allCatalog.stream().filter(AvatarCatalogEntry::valid).count();
        String subText = ShyneTheme.ellipsize(
            this.font,
            Component.translatable("screen.shyne_core.avatars.subtitle", availableCount).getString(),
            maxHeaderTextWidth
        );
        graphics.text(this.font, Component.literal(subText), panelX + 14, panelY + 22, ShyneTheme.TEXT_SECONDARY, false);

        renderPreviewInspector(graphics, mouseX, mouseY);
        renderAvatarRows(graphics, mouseX, mouseY);
        renderPagerAndDivider(graphics);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreviewInspector(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF0C1524);
        graphics.outline(previewX, previewY, previewWidth, previewHeight, 0x883DD9E8);
        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + 2, ShyneTheme.ACCENT);

        int viewportTop = previewY + 4;
        int safeViewportBottom = Math.max(viewportTop + 28, viewportBottom);
        graphics.fill(previewX + 4, viewportTop, previewX + previewWidth - 4, safeViewportBottom, ShyneTheme.PANEL_INSET);
        graphics.outline(previewX + 4, viewportTop, previewWidth - 8, safeViewportBottom - viewportTop, 0x333B5270);

        UiViewportBounds preview = UiViewportBounds.clip(
            previewX + 5, viewportTop + 2, previewX + previewWidth - 5, safeViewportBottom - 2,
            graphics.guiWidth(), graphics.guiHeight()
        );
        if (this.minecraft != null && this.minecraft.player != null && preview.drawable()) {
            int baseScale = Math.max(18, Math.min(42, (safeViewportBottom - viewportTop) / 3));
            int zoomedScale = Math.max(12, Math.min(72, Math.round(baseScale * previewZoom)));
            float lookX = mouseX + previewYawOffset;
            float lookY = mouseY + previewPitchOffset;
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics,
                preview.left(),
                preview.top(),
                preview.right(),
                preview.bottom(),
                zoomedScale,
                0.0625F,
                lookX,
                lookY,
                this.minecraft.player
            );
        }

        // Subtle interactive hint inside top-right of 3D viewport when hovered
        boolean overViewport = mouseX >= previewX + 4 && mouseX < previewX + previewWidth - 4
            && mouseY >= viewportTop && mouseY < safeViewportBottom;
        if (overViewport) {
            String hint = Component.translatable("screen.shyne_core.avatars.preview_hint").getString();
            String fittedHint = ShyneTheme.ellipsize(this.font, hint, previewWidth - 14);
            graphics.text(this.font, Component.literal(fittedHint),
                previewX + (previewWidth - this.font.width(fittedHint)) / 2,
                safeViewportBottom - 11,
                0xAA8DECF3,
                false);
        }

        int nameY = safeViewportBottom + 5;
        String displayName = active == null
            ? Component.translatable("screen.shyne_core.avatars.vanilla.name").getString()
            : resolveDisplayName(active.avatarId());
        String fittedName = ShyneTheme.ellipsize(this.font, displayName, previewWidth - 12);
        graphics.text(
            this.font,
            Component.literal(fittedName),
            previewX + (previewWidth - this.font.width(fittedName)) / 2,
            nameY,
            ShyneTheme.TEXT_PRIMARY,
            false
        );

        AvatarActivationResult result = AvatarRuntime.lastActivation();
        String statusMsg = active == null
            ? Component.translatable("screen.shyne_core.avatars.vanilla.description").getString()
            : (result.success() ? result.message() : result.formattedError());
        int statusColor = active == null
            ? ShyneTheme.TEXT_SECONDARY
            : (result.success() ? ShyneTheme.SUCCESS : ShyneTheme.DANGER);
        String prefix = active == null ? "● " : (result.success() ? "✓ " : "✗ ");
        String fittedStatus = ShyneTheme.ellipsize(this.font, prefix + statusMsg, previewWidth - 12);
        graphics.text(
            this.font,
            Component.literal(fittedStatus),
            previewX + (previewWidth - this.font.width(fittedStatus)) / 2,
            nameY + 12,
            statusColor,
            false
        );
    }

    private void renderAvatarRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int cardWidth = listWidth - 6;
        if (catalog.isEmpty()) {
            if (!allCatalog.isEmpty() && !searchText.isBlank()) {
                ShyneTheme.drawCard(graphics, listX, contentTop, cardWidth, CARD_HEIGHT, false, false);
                String msg = ShyneTheme.ellipsize(
                    this.font,
                    Component.translatable("screen.shyne_core.avatars.search.empty").getString(),
                    cardWidth - 16
                );
                graphics.text(this.font, Component.literal(msg), listX + 10, contentTop + 12, ShyneTheme.TEXT_SECONDARY, false);
            } else {
                ShyneTheme.drawCard(graphics, listX, contentTop, cardWidth, CARD_HEIGHT, false, true);
                renderInitialIcon(graphics, "MC", ShyneTheme.SUCCESS, contentTop, true);
                int textRoom = Math.max(30, cardWidth - 44);
                String vanilla = ShyneTheme.ellipsize(this.font, Component.translatable("screen.shyne_core.avatars.vanilla.name").getString(), textRoom);
                String desc = ShyneTheme.ellipsize(this.font, Component.translatable("screen.shyne_core.avatars.vanilla.description").getString(), textRoom);
                graphics.text(this.font, Component.literal(vanilla), listX + 36, contentTop + 6, ShyneTheme.TEXT_PRIMARY, false);
                graphics.text(this.font, Component.literal(desc), listX + 36, contentTop + 18, ShyneTheme.TEXT_SECONDARY, false);

                if (contentTop + 72 < pagerY) {
                    String emptyTitle = ShyneTheme.ellipsize(this.font, Component.translatable("screen.shyne_core.avatars.empty.title").getString(), cardWidth - 16);
                    String emptyHint = ShyneTheme.ellipsize(this.font, Component.translatable("screen.shyne_core.avatars.empty").getString(), cardWidth - 16);
                    graphics.text(this.font, Component.literal(emptyTitle), listX + 6, contentTop + 44, ShyneTheme.TEXT_PRIMARY, false);
                    graphics.text(this.font, Component.literal(emptyHint), listX + 6, contentTop + 57, ShyneTheme.TEXT_SECONDARY, false);
                }
            }
            return;
        }

        int actionWidth = Math.min(62, Math.max(46, listWidth / 5));
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, catalog.size());

        for (int i = start; i < end; i++) {
            AvatarCatalogEntry entry = catalog.get(i);
            boolean isActive = isActive(entry);
            int y = contentTop + (i - start) * ROW_HEIGHT;
            boolean hovered = mouseX >= listX && mouseX < listX + cardWidth && mouseY >= y && mouseY < y + CARD_HEIGHT;

            ShyneTheme.drawCard(graphics, listX, y, cardWidth, CARD_HEIGHT, hovered, isActive);
            renderAvatarIcon(graphics, entry, y, isActive);

            int warnReserve = (entry.valid() && entry.validation().warningCount() > 0) ? 32 : 0;
            int buttonReserve = !entry.valid()
                ? 36
                : (isActive ? actionWidth * 2 + 16 + warnReserve : actionWidth + 12 + warnReserve);
            int textRoom = Math.max(32, cardWidth - buttonReserve - 38);

            String title = ShyneTheme.ellipsize(this.font, entry.name(), textRoom);
            graphics.text(this.font, Component.literal(title), listX + 36, y + 6, ShyneTheme.TEXT_PRIMARY, false);

            String version = entry.version() == null || entry.version().isBlank() ? "" : "  ·  v" + entry.version();
            String detail = entry.valid() ? (entry.id() + version) : entry.problem();
            String fittedDetail = ShyneTheme.ellipsize(this.font, detail, textRoom);
            graphics.text(
                this.font,
                Component.literal(fittedDetail),
                listX + 36,
                y + 18,
                entry.valid() ? ShyneTheme.TEXT_SECONDARY : ShyneTheme.DANGER,
                false
            );
        }

        if (totalPages > 1) {
            ShyneTheme.drawScrollbar(
                graphics,
                listX + listWidth - 3,
                contentTop,
                pagerY - 4,
                currentPage,
                totalPages - 1,
                pageSize,
                catalog.size()
            );
        }
    }

    private void renderPagerAndDivider(GuiGraphicsExtractor graphics) {
        String pageLabel = totalPages > 1
            ? "Scroll ↕  " + (currentPage + 1) + " / " + totalPages
            : Component.translatable("screen.shyne_core.avatars.page", currentPage + 1, totalPages).getString();
        graphics.text(this.font, Component.literal(pageLabel), listX + 2, pagerY + 5, ShyneTheme.TEXT_DIM, false);

        String activeDisplay = active == null
            ? Component.translatable("screen.shyne_core.avatars.vanilla.name").getString()
            : resolveDisplayName(active.avatarId());
        String currentLabel = ShyneTheme.ellipsize(
            this.font,
            Component.translatable("screen.shyne_core.avatars.current", activeDisplay).getString(),
            Math.max(40, listWidth - 110)
        );
        graphics.text(
            this.font,
            Component.literal(currentLabel),
            listX + listWidth - 6 - this.font.width(currentLabel),
            pagerY + 5,
            ShyneTheme.ACCENT,
            false
        );

        int dividerY = footerTop - 5;
        graphics.fill(panelX + 14, dividerY, panelX + panelWidth - 14, dividerY + 1, 0x333B5270);
    }

    private void renderAvatarIcon(GuiGraphicsExtractor graphics, AvatarCatalogEntry entry, int rowY, boolean selected) {
        AvatarIconTextures.Icon icon = AvatarIconTextures.resolve(entry.root());
        int x = listX + 7;
        int y = rowY + 5;
        if (icon != null) {
            float scale = Math.min(20f / icon.width(), 20f / icon.height());
            int w = Math.max(1, Math.round(icon.width() * scale));
            int h = Math.max(1, Math.round(icon.height() * scale));
            int iconX = x + 1 + (20 - w) / 2;
            int iconY = y + 1 + (20 - h) / 2;
            graphics.fill(x, y, x + 22, y + 22, 0xFF0A1322);
            graphics.outline(x, y, 22, 22, selected ? ShyneTheme.ACCENT : ShyneTheme.BORDER_SUBTLE);
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                icon.id(),
                iconX,
                iconY,
                0,
                0,
                w,
                h,
                icon.width(),
                icon.height(),
                icon.width(),
                icon.height()
            );
            return;
        }
        int color = AVATAR_ICON_COLORS[Math.floorMod(entry.id().toLowerCase(Locale.ROOT).hashCode(), AVATAR_ICON_COLORS.length)];
        renderInitialIcon(graphics, avatarInitials(entry.name()), color, rowY, selected);
    }

    private void renderInitialIcon(GuiGraphicsExtractor graphics, String initials, int color, int rowY, boolean selected) {
        int x = listX + 7;
        int y = rowY + 5;
        graphics.fill(x, y, x + 22, y + 22, 0xFF0A1322);
        graphics.outline(x, y, 22, 22, selected ? ShyneTheme.ACCENT : color);
        graphics.fill(x + 1, y + 1, x + 21, y + 3, color);
        int textX = x + (22 - this.font.width(initials)) / 2;
        graphics.text(this.font, Component.literal(initials), textX, y + 8, ShyneTheme.TEXT_PRIMARY, false);
    }

    private String avatarInitials(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) return "?";
        String[] words = normalized.split("[^\\p{L}\\p{N}]+");
        if (words.length > 1 && !words[1].isEmpty()) {
            return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(Locale.ROOT);
        }
        int length = Math.min(2, normalized.length());
        return normalized.substring(0, length).toUpperCase(Locale.ROOT);
    }

    private boolean isActive(AvatarCatalogEntry entry) {
        return active != null && active.avatarId().equalsIgnoreCase(entry.id());
    }

    private void activate(AvatarCatalogEntry entry) {
        AvatarRuntime.switchAvatar(entry, Minecraft.getInstance());
        rebuildWidgets();
    }

    private void clearSearch() {
        if (searchBox == null) return;
        searchBox.setValue("");
    }

    private List<AvatarCatalogEntry> filterCatalog(List<AvatarCatalogEntry> source, String query) {
        if (query == null || query.isBlank()) return source;
        String needle = query.toLowerCase(Locale.ROOT);
        return source.stream()
            .filter(entry -> containsIgnoreCase(entry.id(), needle)
                || containsIgnoreCase(entry.name(), needle)
                || containsIgnoreCase(entry.version(), needle)
                || containsIgnoreCase(entry.description(), needle))
            .toList();
    }

    private boolean containsIgnoreCase(String value, String lowercaseNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowercaseNeedle);
    }

    private void openAvatarFolder() {
        Path folder = AvatarLoader.avatarsDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(folder);
            Blaze3D.openPath(folder);
            AvatarRuntime.refreshCatalogAsync(true).whenComplete((entries, error) -> Minecraft.getInstance().execute(() -> {
                if (this.minecraft != null && this.minecraft.gui.screen() == this) {
                    page = 0;
                    allCatalog = AvatarRuntime.catalog();
                    rebuildFilteredRows();
                }
            }));
        } catch (IOException error) {
            ShyneCore.LOGGER.error("[AvatarManager] Could not open avatar folder {}: {}", folder, error.getMessage());
        }
    }

    private void rescanCatalog() {
        Minecraft client = Minecraft.getInstance();
        AvatarRuntime.refreshCatalogAsync(true).whenComplete((entries, error) -> client.execute(() -> {
            if (client.gui.screen() == this) {
                page = 0;
                allCatalog = AvatarRuntime.catalog();
                rebuildFilteredRows();
            }
        }));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int mx = (int) event.x();
        int my = (int) event.y();

        // Interactive 3D viewport drag / double-click reset
        int viewportTop = previewY + 4;
        int safeViewportBottom = Math.max(viewportTop + 28, viewportBottom);
        if (event.button() == 0 && mx >= previewX + 4 && mx < previewX + previewWidth - 4
            && my >= viewportTop && my < safeViewportBottom) {
            if (doubleClick) {
                previewYawOffset = 0f;
                previewPitchOffset = 0f;
                previewZoom = 1.0f;
                draggingPreview = false;
            } else {
                draggingPreview = true;
            }
            return true;
        }

        // Clicking an inactive valid avatar row equips it directly
        if (event.button() == 0 && !catalog.isEmpty()) {
            int cardWidth = listWidth - 6;
            int start = currentPage * pageSize;
            int end = Math.min(start + pageSize, catalog.size());
            for (int i = start; i < end; i++) {
                int y = contentTop + (i - start) * ROW_HEIGHT;
                if (mx >= listX && mx < listX + cardWidth && my >= y && my < y + CARD_HEIGHT) {
                    AvatarCatalogEntry entry = catalog.get(i);
                    if (entry.valid() && !isActive(entry)) {
                        activate(entry);
                        return true;
                    }
                    break;
                }
            }
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
        if (verticalAmount == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int viewportTop = previewY + 4;
        int safeViewportBottom = Math.max(viewportTop + 28, viewportBottom);
        if (mouseX >= previewX + 4 && mouseX < previewX + previewWidth - 4
            && mouseY >= viewportTop && mouseY < safeViewportBottom) {
            previewZoom = Math.max(0.65f, Math.min(1.85f, previewZoom + (float) verticalAmount * 0.1f));
            return true;
        }

        if (totalPages > 1) {
            int newPage = Math.max(0, Math.min(totalPages - 1, currentPage + (verticalAmount < 0 ? 1 : -1)));
            if (newPage != currentPage) {
                page = newPage;
                rebuildFilteredRows();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE && searchBox != null && searchBox.isFocused() && !searchText.isEmpty()) {
            clearSearch();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        AvatarDragDropInstaller.DropResult result = AvatarDragDropInstaller.installDroppedFiles(files);
        if (result.installedCount() > 0) {
            rescanCatalog();
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
}

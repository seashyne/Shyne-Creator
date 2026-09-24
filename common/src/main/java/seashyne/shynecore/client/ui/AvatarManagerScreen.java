package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.avatar.AvatarActivationResult;
import seashyne.shynecore.client.avatar.AvatarCatalogEntry;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.avatar.ShyneStatusClient;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.render.AvatarIconTextures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class AvatarManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 34;
    private static final int SURFACE = 0xF20B1222;
    private static final int SURFACE_RAISED = 0xE8142034;
    private static final int BORDER = 0x66445A78;
    private static final int ACCENT = 0xFF3DD9E8;
    private static final int TEXT_MUTED = 0xFF91A0B7;
    private static final int[] AVATAR_ICON_COLORS = {
        0xFF42D7E8, 0xFF7C9CFF, 0xFFB98BFF, 0xFF79D8B2, 0xFFFFC66D, 0xFFFF8FA3
    };

    private final Screen parent;
    private int page;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int previewX;
    private int previewWidth;
    private int listX;
    private int listWidth;
    private int contentTop;
    private int pagerY;
    private int footerTop;
    private int headerCloudX;
    private int headerPlayersX;
    private int pageSize;
    private int currentPage;
    private int totalPages;
    private boolean compactFooter;
    private boolean compactHeight;
    private boolean moreOpen;
    private boolean catalogRefreshRequested;
    private String searchText = "";
    private EditBox searchBox;
    private List<AvatarCatalogEntry> allCatalog = List.of();
    private List<AvatarCatalogEntry> catalog = List.of();
    private AvatarState active;

    public AvatarManagerScreen(Screen parent) {
        this(parent, 0, "", false);
    }

    private AvatarManagerScreen(Screen parent, int page, String searchText, boolean moreOpen) {
        super(Component.translatable("screen.shyne_core.avatars.title"));
        this.parent = parent;
        this.page = Math.max(0, page);
        this.searchText = searchText == null ? "" : searchText;
        this.moreOpen = moreOpen;
    }

    @Override
    protected void init() {
        allCatalog = AvatarRuntime.catalog();
        catalog = filterCatalog(allCatalog, searchText);
        active = AvatarRuntime.active();

        panelWidth = Math.max(1, Math.min(760, this.width - 16));
        panelHeight = Math.max(1, Math.min(360, this.height - 16));
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        compactFooter = panelWidth < 500;
        compactHeight = panelHeight < 220;
        if (compactHeight) moreOpen = false;

        int innerX = panelX + 16;
        int innerWidth = panelWidth - 32;
        contentTop = panelY + (compactHeight ? 58 : 70);
        footerTop = panelY + panelHeight - (compactFooter ? 52 : 28);
        pagerY = compactHeight ? footerTop - 22 : footerTop - (moreOpen ? 48 : 24);

        previewWidth = Math.max(82, Math.min(148, innerWidth / 3));
        previewX = innerX;
        listX = previewX + previewWidth + 10;
        listWidth = innerX + innerWidth - listX;
        pageSize = compactHeight ? 1 : Math.max(1, Math.min(5, (pagerY - contentTop - 2) / ROW_HEIGHT));
        totalPages = Math.max(1, (catalog.size() + pageSize - 1) / pageSize);
        currentPage = Math.min(page, totalPages - 1);

        addHeaderControls();
        addSearchControls();
        addAvatarRows();
        if (!compactHeight) addPager();
        addFooter(innerX, innerWidth);
        requestCatalogRefresh();
    }

    private void addHeaderControls() {
        int cloudWidth = 70;
        headerCloudX = panelX + panelWidth - 16 - cloudWidth;
        headerPlayersX = headerCloudX - 74;
        Button players = Button.builder(Component.translatable("screen.shyne_core.players.short"), btn -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new ServerPlayersScreen(this));
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.players.open.tooltip")))
            .bounds(headerPlayersX, panelY + 11, cloudWidth, 20).build();
        players.active = this.minecraft != null && this.minecraft.getConnection() != null && this.minecraft.level != null;
        addRenderableWidget(players);
        Button cloud = Button.builder(Component.translatable("screen.shyne_core.avatars.cloud.short"), btn -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new CloudAvatarLibraryScreen(this));
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.cloud.open.tooltip")))
            .bounds(headerCloudX, panelY + 11, cloudWidth, 20).build();
        cloud.active = ShyneClientSettings.cloudEnabled;
        addRenderableWidget(cloud);
    }

    private void addSearchControls() {
        int searchButtonWidth = 22;
        searchBox = new EditBox(
            this.font,
            listX,
            panelY + 43,
            Math.max(48, listWidth - searchButtonWidth - 4),
            20,
            Component.translatable("screen.shyne_core.avatars.search")
        );
        searchBox.setHint(Component.translatable("screen.shyne_core.avatars.search"));
        searchBox.setValue(searchText);
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal("⌕"), ignored -> applySearch())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.search.tooltip")))
            .bounds(listX + listWidth - searchButtonWidth, panelY + 43, searchButtonWidth, 20)
            .build());
    }

    private void requestCatalogRefresh() {
        if (catalogRefreshRequested) return;
        catalogRefreshRequested = true;
        Minecraft client = Minecraft.getInstance();
        AvatarRuntime.refreshCatalogAsync().whenComplete((entries, error) -> client.execute(() -> {
            if (this.minecraft != null && this.minecraft.gui.screen() == this) rebuildWidgets();
        }));
    }

    private void addAvatarRows() {
        int actionWidth = Math.min(64, Math.max(46, listWidth / 4));
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, catalog.size());
        for (int i = start; i < end; i++) {
            AvatarCatalogEntry entry = catalog.get(i);
            boolean isActive = isActive(entry);
            int rowY = contentTop + (i - start) * ROW_HEIGHT + 5;
            if (!entry.valid()) {
                addRenderableWidget(Button.builder(Component.literal("!"), ignored -> {
                    if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarValidationScreen(this));
                }).tooltip(Tooltip.create(Component.literal(entry.problem())))
                    .bounds(listX + listWidth - 27, rowY, 22, 20).build());
            } else if (isActive) {
                Button activeButton = Button.builder(Component.translatable("screen.shyne_core.avatars.active"), ignored -> {})
                    .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.active.tooltip", entry.name())))
                    .bounds(listX + listWidth - actionWidth * 2 - 9, rowY, actionWidth, 20).build();
                activeButton.active = false;
                addRenderableWidget(activeButton);

                Button outfit = Button.builder(Component.translatable("screen.shyne_core.avatars.outfit"), btn -> {
                    if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarOutfitScreen(this));
                }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.outfit.tooltip")))
                    .bounds(listX + listWidth - actionWidth - 5, rowY, actionWidth, 20).build();
                addRenderableWidget(outfit);
            } else {
                addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.avatars.use"), btn -> activate(entry))
                    .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.use.tooltip", entry.name())))
                    .bounds(listX + listWidth - actionWidth - 5, rowY, actionWidth, 20).build());
            }
        }
        if (allCatalog.isEmpty()) {
            Button vanillaActive = Button.builder(Component.translatable("screen.shyne_core.avatars.active"), ignored -> {})
                .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.vanilla.active.tooltip")))
                .bounds(listX + listWidth - actionWidth - 5, contentTop + 5, actionWidth, 20).build();
            vanillaActive.active = false;
            addRenderableWidget(vanillaActive);
        }
    }

    private void addPager() {
        Button previous = Button.builder(Component.literal("‹"), btn -> openPage(currentPage - 1))
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.previous")))
            .bounds(listX, pagerY, 30, 20).build();
        previous.active = currentPage > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal("›"), btn -> openPage(currentPage + 1))
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.next")))
            .bounds(listX + 34, pagerY, 30, 20).build();
        next.active = currentPage + 1 < totalPages;
        addRenderableWidget(next);
    }

    private void addFooter(int innerX, int innerWidth) {
        Button back = Button.builder(Component.translatable("gui.back"), btn -> onClose()).build();

        Button vanilla = Button.builder(Component.translatable("screen.shyne_core.avatars.vanilla"), btn -> {
            AvatarRuntime.deactivate(Minecraft.getInstance());
            openPage(currentPage);
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.vanilla.tooltip"))).build();
        vanilla.active = active != null;

        Button reload = Button.builder(Component.translatable("screen.shyne_core.avatars.reload"), btn -> {
            AvatarRuntime.reloadActive(Minecraft.getInstance());
            openPage(currentPage);
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.reload.tooltip"))).build();
        reload.active = active != null;

        Button mask = Button.builder(maskLabel(active), btn -> {
            AvatarState current = AvatarRuntime.active();
            if (current != null) current.setFirstPersonMasking(!current.firstPersonMasking());
            openPage(currentPage);
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.mask.tooltip"))).build();
        mask.active = active != null;

        Button wheel = Button.builder(Component.translatable("screen.shyne_core.avatars.actions"), btn -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new ShynePaletteScreen(this));
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.actions.tooltip"))).build();
        wheel.active = active != null && !active.actions().isEmpty();

        Button folder = Button.builder(Component.translatable("screen.shyne_core.avatars.folder"), btn -> openAvatarFolder())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.folder.tooltip"))).build();

        Button validate = Button.builder(Component.translatable("screen.shyne_core.avatars.validate"), btn -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarValidationScreen(this));
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.validate.tooltip"))).build();

        ShyneStatusClient.StatusResult status = ShyneStatusClient.lastResult();
        Button verify = Button.builder(Component.translatable("screen.shyne_core.avatars.status.check"), btn -> checkStatus())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.status.check.tooltip"))).build();
        verify.active = !status.working();

        Button cloudSettings = Button.builder(Component.translatable("screen.shyne_core.avatars.status.settings"), btn -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new ShyneStatusScreen(this));
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.status.settings.tooltip"))).build();

        Button more = Button.builder(
            Component.translatable(moreOpen ? "screen.shyne_core.avatars.more.close" : "screen.shyne_core.avatars.more"),
            ignored -> {
                moreOpen = !moreOpen;
                rebuildWidgets();
            }
        ).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.more.tooltip"))).build();

        if (moreOpen) {
            placeRow(innerX, footerTop - 24, innerWidth, List.of(mask, validate, folder, verify, cloudSettings));
        }

        if (compactFooter) {
            placeRow(innerX, footerTop, innerWidth, List.of(back, vanilla, reload));
            if (compactHeight) {
                Button previous = Button.builder(Component.literal("‹"), btn -> openPage(currentPage - 1))
                    .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.previous"))).build();
                previous.active = currentPage > 0;
                Button next = Button.builder(Component.literal("›"), btn -> openPage(currentPage + 1))
                    .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.next"))).build();
                next.active = currentPage + 1 < totalPages;
                placeRow(innerX, footerTop + 24, innerWidth, List.of(wheel, previous, next));
            } else {
                placeRow(innerX, footerTop + 24, innerWidth, List.of(wheel, more));
            }
        } else {
            // Vanilla and Reload intentionally remain separate, adjacent actions.
            placeRow(innerX, footerTop, innerWidth, List.of(back, vanilla, reload, wheel, more));
        }
    }

    private void openAvatarFolder() {
        Path folder = AvatarLoader.avatarsDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(folder);
            Blaze3D.openPath(folder);
            AvatarRuntime.refreshCatalogAsync(true).whenComplete((entries, error) -> Minecraft.getInstance().execute(() -> {
                page = 0;
                rebuildWidgets();
            }));
        } catch (IOException error) {
            seashyne.shynecore.ShyneCore.LOGGER.error("[AvatarManager] Could not open avatar folder {}: {}", folder, error.getMessage());
        }
    }

    private void placeRow(int x, int y, int width, List<Button> buttons) {
        int gap = 4;
        int buttonWidth = (width - gap * (buttons.size() - 1)) / buttons.size();
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            button.setX(x + i * (buttonWidth + gap));
            button.setY(y);
            button.setWidth(buttonWidth);
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

        graphics.text(this.font, Component.translatable("screen.shyne_core.avatars.title"), panelX + 16, panelY + 14, 0xFFF4F7FC, true);
        int availableCount = allCatalog.isEmpty() ? 1 : allCatalog.size();
        graphics.text(this.font, Component.translatable("screen.shyne_core.avatars.subtitle", availableCount), panelX + 16, panelY + 31, TEXT_MUTED, false);
        Component local = Component.translatable("screen.shyne_core.avatars.local");
        if (panelWidth >= 430) {
            graphics.text(this.font, local, headerPlayersX - 10 - this.font.width(local), panelY + 18, 0xFF64748B, false);
            graphics.text(this.font, Component.literal("●"), headerPlayersX - 9, panelY + 17, cloudStatusColor(), false);
        }

        renderPreview(graphics, mouseX, mouseY);
        renderAvatarRows(graphics);

        if (!compactHeight) {
            graphics.text(this.font, Component.translatable("screen.shyne_core.avatars.page", currentPage + 1, totalPages), listX + 72, pagerY + 6, 0xFF718198, false);
        }
        String activeName = active == null ? Component.translatable("screen.shyne_core.avatars.vanilla.name").getString() : active.avatarId();
        Component activeLabel = Component.translatable("screen.shyne_core.avatars.current", activeName);
        String clippedCurrent = this.font.plainSubstrByWidth(activeLabel.getString(), Math.max(40, listWidth - 150));
        graphics.text(this.font, Component.literal(clippedCurrent), listX + listWidth - this.font.width(clippedCurrent), pagerY + 6, 0xFF8DECF3, false);

        int dividerY = footerTop - (moreOpen ? 31 : 7);
        graphics.fill(panelX + 16, dividerY, panelX + panelWidth - 16, dividerY + 1, 0x33445A78);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int previewBottom = pagerY + 20;
        graphics.fill(previewX, contentTop, previewX + previewWidth, previewBottom, 0xFF101B2C);
        graphics.outline(previewX, contentTop, previewWidth, previewBottom - contentTop, 0x8841D7E5);
        graphics.fill(previewX, contentTop, previewX + previewWidth, contentTop + 2, ACCENT);

        int statusLineY = previewBottom - 17;
        int labelY = statusLineY - 15;
        String name = active == null ? Component.translatable("screen.shyne_core.avatars.vanilla.name").getString() : active.avatarId();
        String shortName = this.font.plainSubstrByWidth(name, previewWidth - 12);
        graphics.text(this.font, Component.literal(shortName), previewX + (previewWidth - this.font.width(shortName)) / 2, labelY, 0xFFF4F7FC, false);
        graphics.fill(previewX + 6, labelY - 4, previewX + previewWidth - 6, labelY - 3, 0x3341D7E5);

        AvatarActivationResult result = AvatarRuntime.lastActivation();
        String activation = active == null
            ? Component.translatable("screen.shyne_core.avatars.vanilla").getString()
            : result.message();
        int activationColor = result.success() || active == null ? 0xFF7DDBB3 : 0xFFFF8793;
        String shortActivation = this.font.plainSubstrByWidth(activation, previewWidth - 24);
        graphics.text(this.font, Component.literal("✓ " + shortActivation), previewX + 12, statusLineY, activationColor, false);

        UiViewportBounds preview = UiViewportBounds.clip(
            previewX + 4, contentTop + 4, previewX + previewWidth - 4, labelY - 5,
            graphics.guiWidth(), graphics.guiHeight()
        );
        if (this.minecraft != null && this.minecraft.player != null && preview.drawable()) {
            int scale = Math.max(18, Math.min(40, (labelY - contentTop) / 3));
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics,
                preview.left(),
                preview.top(),
                preview.right(),
                preview.bottom(),
                scale,
                0.0625F,
                mouseX,
                mouseY,
                this.minecraft.player
            );
        }
    }

    private void renderAvatarRows(GuiGraphicsExtractor graphics) {
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, catalog.size());
        if (catalog.isEmpty()) {
            if (!allCatalog.isEmpty() && !searchText.isBlank()) {
                renderRowBackground(graphics, contentTop, false);
                Component emptySearch = Component.translatable("screen.shyne_core.avatars.search.empty");
                String shortMessage = this.font.plainSubstrByWidth(emptySearch.getString(), listWidth - 16);
                graphics.text(this.font, Component.literal(shortMessage), listX + 8, contentTop + 11, TEXT_MUTED, false);
            } else {
                renderRowBackground(graphics, contentTop, true);
                renderInitialIcon(graphics, "MC", 0xFF79D8B2, contentTop, true);
                int textWidth = Math.max(30, listWidth - 112);
                String vanilla = this.font.plainSubstrByWidth(Component.translatable("screen.shyne_core.avatars.vanilla.name").getString(), textWidth);
                graphics.text(this.font, Component.literal(vanilla), listX + 36, contentTop + 5, 0xFFF4F7FC, false);
                graphics.text(this.font, Component.translatable("screen.shyne_core.avatars.vanilla.description"), listX + 36, contentTop + 17, TEXT_MUTED, false);
            }
            return;
        }

        for (int i = start; i < end; i++) {
            AvatarCatalogEntry entry = catalog.get(i);
            boolean isActive = isActive(entry);
            int y = contentTop + (i - start) * ROW_HEIGHT;
            renderRowBackground(graphics, y, isActive);
            renderAvatarIcon(graphics, entry, y, isActive);
            int buttonWidth = Math.min(64, Math.max(46, listWidth / 4));
            int buttonReserve = !entry.valid() ? 36 : isActive ? buttonWidth * 2 + 13 : buttonWidth + 9;
            int textRoom = Math.max(32, listWidth - buttonReserve - 40);
            String title = this.font.plainSubstrByWidth(entry.name(), textRoom);
            graphics.text(this.font, Component.literal(title), listX + 36, y + 5, 0xFFF3F7FF, false);
            String version = entry.version() == null || entry.version().isBlank() ? "" : "  •  v" + entry.version();
            String warnings = entry.valid() && entry.validation().warningCount() > 0 ? "  •  !" + entry.validation().warningCount() : "";
            String detail = entry.valid() ? entry.id() + version + warnings : entry.problem();
            detail = this.font.plainSubstrByWidth(detail, textRoom);
            graphics.text(this.font, Component.literal(detail), listX + 36, y + 17, entry.valid() ? TEXT_MUTED : 0xFFFF8793, false);
        }
    }

    private void renderRowBackground(GuiGraphicsExtractor graphics, int y, boolean selected) {
        graphics.fill(listX, y, listX + listWidth, y + 30, selected ? 0xFF152C3D : SURFACE_RAISED);
        graphics.outline(listX, y, listWidth, 30, selected ? 0x9941D7E5 : BORDER);
        if (selected) graphics.fill(listX, y, listX + 3, y + 30, ACCENT);
    }

    private int cloudStatusColor() {
        if (!ShyneClientSettings.cloudEnabled) return 0xFF7188A8;
        ShyneStatusClient.StatusResult cloudStatus = ShyneStatusClient.lastResult();
        return switch (cloudStatus.state()) {
            case SUCCESS -> 0xFF79D8B2;
            case ERROR -> 0xFFFF7F8B;
            case WORKING -> 0xFF8DECF3;
            case IDLE -> 0xFF7188A8;
        };
    }

    private void renderAvatarIcon(GuiGraphicsExtractor graphics, AvatarCatalogEntry entry, int rowY, boolean selected) {
        AvatarIconTextures.Icon icon = AvatarIconTextures.resolve(entry.root());
        if (icon != null) {
            int x = listX + 7;
            int y = rowY + 4;
            float scale = Math.min(20f / icon.width(), 20f / icon.height());
            int width = Math.max(1, Math.round(icon.width() * scale));
            int height = Math.max(1, Math.round(icon.height() * scale));
            int iconX = x + 1 + (20 - width) / 2;
            int iconY = y + 1 + (20 - height) / 2;
            graphics.fill(x, y, x + 22, y + 22, 0xFF0E1929);
            graphics.outline(x, y, 22, 22, selected ? ACCENT : BORDER);
            // In the 26.2 overload, width/height come before source size.
            // Reversing them samples only the dark top-left corner of large
            // avatar.png files and draws it across the library.
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                icon.id(),
                iconX,
                iconY,
                0,
                0,
                width,
                height,
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
        int y = rowY + 4;
        graphics.fill(x, y, x + 22, y + 22, 0xFF0E1929);
        graphics.outline(x, y, 22, 22, selected ? ACCENT : color);
        graphics.fill(x + 1, y + 1, x + 21, y + 3, color);
        int textX = x + (22 - this.font.width(initials)) / 2;
        graphics.text(this.font, Component.literal(initials), textX, y + 8, 0xFFF4F7FC, false);
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

    private Component maskLabel(AvatarState state) {
        boolean enabled = state != null && state.firstPersonMasking();
        return Component.translatable("screen.shyne_core.avatars.mask")
            .append(Component.literal(enabled ? " ON" : " OFF").withStyle(enabled ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }

    private void activate(AvatarCatalogEntry entry) {
        // The selected catalog entry already contains its validated root path, so
        // activation does not need another full directory scan on the UI thread.
        AvatarRuntime.switchAvatar(entry, Minecraft.getInstance());
        openPage(currentPage);
    }

    private void applySearch() {
        searchText = searchBox == null ? "" : searchBox.getValue().trim();
        page = 0;
        rebuildWidgets();
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

    private void checkStatus() {
        Minecraft client = Minecraft.getInstance();
        var operation = ShyneStatusClient.check();
        operation.whenComplete((result, error) -> client.execute(this::rebuildWidgets));
        rebuildWidgets();
    }

    private void openPage(int newPage) {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(new AvatarManagerScreen(parent, newPage, searchText, moreOpen));
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

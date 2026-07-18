package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.avatar.AvatarActivationResult;
import seashyne.shynecore.client.avatar.AvatarCatalogEntry;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.avatar.ShyneStatusClient;
import seashyne.shynecore.client.config.ShyneClientSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AvatarManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 34;
    private static final int SURFACE = 0xF20B1222;
    private static final int SURFACE_RAISED = 0xE8142034;
    private static final int BORDER = 0x66445A78;
    private static final int ACCENT = 0xFF3DD9E8;
    private static final int TEXT_MUTED = 0xFF91A0B7;

    private final Screen parent;
    private final int page;
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
    private int statusY;
    private int cloudY;
    private int footerTop;
    private int pageSize;
    private int currentPage;
    private int totalPages;
    private boolean compactFooter;
    private List<AvatarCatalogEntry> catalog = List.of();
    private AvatarState active;

    public AvatarManagerScreen(Screen parent) {
        this(parent, 0);
    }

    private AvatarManagerScreen(Screen parent, int page) {
        super(Component.translatable("screen.shyne_core.avatars.title"));
        this.parent = parent;
        this.page = Math.max(0, page);
    }

    @Override
    protected void init() {
        AvatarRuntime.refreshCatalog();
        catalog = AvatarRuntime.catalog();
        active = AvatarRuntime.active();

        panelWidth = Math.min(720, Math.max(288, this.width - 16));
        panelHeight = Math.min(360, Math.max(220, this.height - 16));
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        compactFooter = panelWidth < 500;

        int innerX = panelX + 16;
        int innerWidth = panelWidth - 32;
        contentTop = panelY + 58;
        footerTop = panelY + panelHeight - (compactFooter ? 52 : 28);
        cloudY = footerTop - 28;
        statusY = cloudY - 18;
        pagerY = statusY - 24;

        previewWidth = Math.max(82, Math.min(148, innerWidth / 3));
        previewX = innerX;
        listX = previewX + previewWidth + 10;
        listWidth = innerX + innerWidth - listX;
        pageSize = Math.max(1, Math.min(5, (pagerY - contentTop - 2) / ROW_HEIGHT));
        totalPages = Math.max(1, (catalog.size() + pageSize - 1) / pageSize);
        currentPage = Math.min(page, totalPages - 1);

        addAvatarRows();
        addPager();
        addCloudControls(innerX, innerWidth);
        addFooter(innerX, innerWidth);
    }

    private void addAvatarRows() {
        int selectWidth = Math.min(72, Math.max(54, listWidth / 3));
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, catalog.size());
        for (int i = start; i < end; i++) {
            AvatarCatalogEntry entry = catalog.get(i);
            boolean isActive = isActive(entry);
            Button select = Button.builder(
                Component.translatable(!entry.valid() ? "screen.shyne_core.avatars.invalid" : isActive ? "screen.shyne_core.avatars.active" : "screen.shyne_core.avatars.use"),
                btn -> activate(entry)
            ).tooltip(Tooltip.create(!entry.valid()
                ? Component.literal(entry.problem())
                : Component.translatable(isActive ? "screen.shyne_core.avatars.active.tooltip" : "screen.shyne_core.avatars.use.tooltip", entry.name())
            )).bounds(listX + listWidth - selectWidth - 5, contentTop + (i - start) * ROW_HEIGHT + 5, selectWidth, 20).build();
            select.active = entry.valid() && !isActive;
            addRenderableWidget(select);
            if (isActive) {
                int outfitWidth = selectWidth;
                Button outfit = Button.builder(Component.translatable("screen.shyne_core.avatars.outfit"), btn -> {
                    if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarOutfitScreen(this));
                }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.outfit.tooltip")))
                    .bounds(listX + listWidth - selectWidth - outfitWidth - 9, contentTop + (i - start) * ROW_HEIGHT + 5, outfitWidth, 20).build();
                addRenderableWidget(outfit);
            }
        }
        if (catalog.isEmpty()) {
            Button vanillaActive = Button.builder(Component.translatable("screen.shyne_core.avatars.active"), ignored -> {})
                .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.vanilla.active.tooltip")))
                .bounds(listX + listWidth - selectWidth - 5, contentTop + 5, selectWidth, 20).build();
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

    private void addCloudControls(int innerX, int innerWidth) {
        int gap = 4;
        int settingsWidth = Math.min(78, Math.max(60, innerWidth / 5));
        int statusWidth = Math.min(96, Math.max(72, innerWidth / 4));
        int libraryWidth = innerWidth - settingsWidth - statusWidth - gap * 2;
        Button cloudLibrary = Button.builder(Component.translatable("screen.shyne_core.cloud.open"), btn -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new CloudAvatarLibraryScreen(this));
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.cloud.open.tooltip")))
            .bounds(innerX, cloudY, libraryWidth, 20).build();
        cloudLibrary.active = ShyneClientSettings.cloudEnabled;
        addRenderableWidget(cloudLibrary);

        ShyneStatusClient.StatusResult status = ShyneStatusClient.lastResult();
        Button verify = Button.builder(Component.translatable("screen.shyne_core.avatars.status.check"), btn -> checkStatus())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.status.check.tooltip")))
            .bounds(innerX + libraryWidth + gap, cloudY, statusWidth, 20).build();
        verify.active = !status.working();
        addRenderableWidget(verify);

        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.avatars.status.settings"), btn -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new ShyneStatusScreen(this));
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.status.settings.tooltip")))
            .bounds(innerX + libraryWidth + statusWidth + gap * 2, cloudY, settingsWidth, 20).build());
    }

    private void addFooter(int innerX, int innerWidth) {
        Button vanilla = Button.builder(Component.translatable("screen.shyne_core.avatars.vanilla"), btn -> {
            AvatarRuntime.deactivate(Minecraft.getInstance());
            openPage(currentPage);
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.vanilla.tooltip"))).build();
        vanilla.active = active != null;

        Button reload = Button.builder(Component.translatable("screen.shyne_core.avatars.reload"), btn -> {
            AvatarRuntime.reloadActive(Minecraft.getInstance());
            openPage(currentPage);
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.reload.tooltip"))).build();
        reload.active = true;

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

        Button back = Button.builder(Component.translatable("gui.back"), btn -> onClose()).build();
        if (compactFooter) {
            placeRow(innerX, footerTop, innerWidth, List.of(vanilla, reload, mask));
            placeRow(innerX, footerTop + 24, innerWidth, List.of(wheel, validate, folder, back));
        } else {
            placeRow(innerX, footerTop, innerWidth, List.of(vanilla, reload, mask, wheel, validate, folder, back));
        }
    }

    private void openAvatarFolder() {
        Path folder = AvatarLoader.avatarsDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(folder);
            Util.getPlatform().openPath(folder);
            AvatarRuntime.refreshCatalog();
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
        int availableCount = catalog.isEmpty() ? 1 : catalog.size();
        graphics.text(this.font, Component.translatable("screen.shyne_core.avatars.subtitle", availableCount), panelX + 16, panelY + 31, TEXT_MUTED, false);
        Component local = Component.translatable("screen.shyne_core.avatars.local");
        graphics.text(this.font, local, panelX + panelWidth - 16 - this.font.width(local), panelY + 18, 0xFF64748B, false);

        renderPreview(graphics, mouseX, mouseY);
        renderAvatarRows(graphics);

        graphics.text(this.font, Component.translatable("screen.shyne_core.avatars.page", currentPage + 1, totalPages), listX + 72, pagerY + 6, 0xFF718198, false);
        String activeName = active == null ? Component.translatable("screen.shyne_core.avatars.vanilla.name").getString() : active.avatarId();
        Component activeLabel = Component.translatable("screen.shyne_core.avatars.current", activeName);
        String clippedCurrent = this.font.plainSubstrByWidth(activeLabel.getString(), Math.max(40, listWidth - 150));
        graphics.text(this.font, Component.literal(clippedCurrent), listX + listWidth - this.font.width(clippedCurrent), pagerY + 6, 0xFF8DECF3, false);

        renderStatus(graphics);
        graphics.fill(panelX + 16, footerTop - 7, panelX + panelWidth - 16, footerTop - 6, 0x33445A78);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int previewBottom = pagerY + 20;
        graphics.fill(previewX, contentTop, previewX + previewWidth, previewBottom, 0xFF101B2C);
        graphics.outline(previewX, contentTop, previewWidth, previewBottom - contentTop, 0x8841D7E5);
        graphics.fill(previewX, contentTop, previewX + previewWidth, contentTop + 2, ACCENT);

        int labelY = previewBottom - 17;
        String name = active == null ? Component.translatable("screen.shyne_core.avatars.vanilla.name").getString() : active.avatarId();
        String shortName = this.font.plainSubstrByWidth(name, previewWidth - 12);
        graphics.text(this.font, Component.literal(shortName), previewX + (previewWidth - this.font.width(shortName)) / 2, labelY, 0xFFF4F7FC, false);
        graphics.fill(previewX + 6, labelY - 4, previewX + previewWidth - 6, labelY - 3, 0x3341D7E5);

        if (this.minecraft != null && this.minecraft.player != null && labelY - contentTop > 30) {
            int scale = Math.max(18, Math.min(40, (labelY - contentTop) / 3));
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics,
                previewX + 4,
                contentTop + 4,
                previewX + previewWidth - 4,
                labelY - 5,
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
            renderRowBackground(graphics, contentTop, true);
            int textWidth = Math.max(30, listWidth - 86);
            String vanilla = this.font.plainSubstrByWidth(Component.translatable("screen.shyne_core.avatars.vanilla.name").getString(), textWidth);
            graphics.text(this.font, Component.literal(vanilla), listX + 10, contentTop + 5, 0xFFF4F7FC, false);
            graphics.text(this.font, Component.literal("Minecraft default"), listX + 10, contentTop + 17, TEXT_MUTED, false);
            if (pageSize > 1) {
                Component hint = Component.translatable("screen.shyne_core.avatars.empty");
                String shortHint = this.font.plainSubstrByWidth(hint.getString(), listWidth - 8);
                graphics.text(this.font, Component.literal(shortHint), listX + 4, contentTop + 45, TEXT_MUTED, false);
            }
            return;
        }

        for (int i = start; i < end; i++) {
            AvatarCatalogEntry entry = catalog.get(i);
            boolean isActive = isActive(entry);
            int y = contentTop + (i - start) * ROW_HEIGHT;
            renderRowBackground(graphics, y, isActive);
            int buttonWidth = Math.min(72, Math.max(54, listWidth / 3));
            int buttonReserve = isActive ? buttonWidth * 2 + 13 : buttonWidth + 9;
            int textRoom = Math.max(32, listWidth - buttonReserve - 14);
            String title = this.font.plainSubstrByWidth(entry.name(), textRoom);
            graphics.text(this.font, Component.literal(title), listX + 10, y + 5, 0xFFF3F7FF, false);
            String version = entry.version() == null || entry.version().isBlank() ? "" : "  •  v" + entry.version();
            String warnings = entry.valid() && entry.validation().warningCount() > 0 ? "  •  !" + entry.validation().warningCount() : "";
            String detail = entry.valid() ? entry.id() + version + warnings : entry.problem();
            detail = this.font.plainSubstrByWidth(detail, textRoom);
            graphics.text(this.font, Component.literal(detail), listX + 10, y + 17, entry.valid() ? TEXT_MUTED : 0xFFFF8793, false);
        }
    }

    private void renderRowBackground(GuiGraphicsExtractor graphics, int y, boolean selected) {
        graphics.fill(listX, y, listX + listWidth, y + 30, selected ? 0xFF152C3D : SURFACE_RAISED);
        graphics.outline(listX, y, listWidth, 30, selected ? 0x9941D7E5 : BORDER);
        if (selected) graphics.fill(listX, y, listX + 3, y + 30, ACCENT);
    }

    private void renderStatus(GuiGraphicsExtractor graphics) {
        AvatarActivationResult result = AvatarRuntime.lastActivation();
        String activation = catalog.isEmpty() ? "Ready" : result.message();
        int activationColor = result.success() ? 0xFF7DDBB3 : 0xFFFF8793;
        String shortActivation = this.font.plainSubstrByWidth(activation, Math.max(60, panelWidth / 2 - 24));
        graphics.text(this.font, Component.literal(shortActivation), panelX + 16, statusY + 4, activationColor, false);

        ShyneStatusClient.StatusResult cloudStatus = ShyneStatusClient.lastResult();
        int cloudColor = switch (cloudStatus.state()) {
            case SUCCESS -> 0xFF79D8B2;
            case ERROR -> 0xFFFF7F8B;
            case WORKING -> 0xFF8DECF3;
            case IDLE -> 0xFF7188A8;
        };
        String cloudText = ShyneClientSettings.cloudEnabled
            ? cloudStatus.message()
            : Component.translatable("screen.shyne_core.avatars.status.disabled").getString();
        String shortCloud = this.font.plainSubstrByWidth(cloudText, Math.max(50, panelWidth / 3));
        String status = "● " + shortCloud;
        graphics.text(this.font, Component.literal(status), panelX + panelWidth - 16 - this.font.width(status), statusY + 4, cloudColor, false);
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
        AvatarRuntime.switchAvatar(entry.id(), Minecraft.getInstance());
        openPage(currentPage);
    }

    private void checkStatus() {
        Minecraft client = Minecraft.getInstance();
        var operation = ShyneStatusClient.check();
        operation.whenComplete((result, error) -> client.execute(this::rebuildWidgets));
        rebuildWidgets();
    }

    private void openPage(int newPage) {
        if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarManagerScreen(parent, newPage));
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

package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.avatar.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CloudAvatarLibraryScreen extends Screen {
    private static final int DESIRED_PANEL_HEIGHT = 390;
    private static final int ROW_HEIGHT = 42;
    private static final int MAX_VISIBLE_ROWS = 6;

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int footerY;
    private int pagerY;
    private int visibleRows;
    private EditBox search;
    private List<ShyneCloudClient.CloudAvatar> items = List.of();
    private int selected;
    private int offset;
    private Integer nextOffset;
    private boolean loading;
    private boolean initialLoadRequested;
    private String searchText = "";
    private CompletableFuture<?> activeRequest;
    private long requestGeneration;
    private Runnable retryAction;
    private boolean publicMode;

    public CloudAvatarLibraryScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.cloud.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(760, Math.max(1, this.width - 20));
        panelHeight = Math.min(DESIRED_PANEL_HEIGHT, Math.max(1, this.height - 16));
        panelX = (this.width - panelWidth) / 2;
        panelY = Math.max(8, (this.height - panelHeight) / 2);
        footerY = panelY + panelHeight - 32;
        pagerY = footerY - 22;
        search = new EditBox(this.font, panelX + 14, panelY + 43, Math.max(120, panelWidth - 250), 20, Component.translatable("screen.shyne_core.cloud.search"));
        search.setHint(Component.translatable("screen.shyne_core.cloud.search"));
        search.setValue(searchText);
        addRenderableWidget(search);

        Button mineTab = Button.builder(Component.translatable("screen.shyne_core.cloud.tab.mine"), button -> switchMode(false))
            .bounds(panelX + Math.max(180, panelWidth - 458), panelY + 12, 86, 20).build();
        mineTab.active = publicMode && !loading;
        addRenderableWidget(mineTab);
        Button publicTab = Button.builder(Component.translatable("screen.shyne_core.cloud.tab.public"), button -> switchMode(true))
            .bounds(panelX + Math.max(270, panelWidth - 368), panelY + 12, 96, 20).build();
        publicTab.active = !publicMode && !loading;
        addRenderableWidget(publicTab);

        Button searchButton = Button.builder(Component.translatable("screen.shyne_core.cloud.search_button"), button -> { offset = 0; refresh(); })
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.cloud.search.tooltip")))
            .bounds(panelX + panelWidth - 226, panelY + 43, 72, 20).build();
        searchButton.active = !loading;
        addRenderableWidget(searchButton);
        Button account = Button.builder(Component.literal(ShyneCloudClient.signedIn() ? ShyneCloudClient.accountName() : Component.translatable("screen.shyne_core.cloud.signin").getString()), button -> signIn())
            .tooltip(Tooltip.create(Component.translatable(ShyneCloudClient.signedIn() ? "screen.shyne_core.cloud.signout.tooltip" : "screen.shyne_core.cloud.signin.tooltip")))
            .bounds(panelX + panelWidth - 140, panelY + 43, 126, 20).build();
        account.active = !loading;
        addRenderableWidget(account);

        int listX = panelX + 14;
        int listY = panelY + 76;
        int listWidth = Math.max(260, panelWidth - 316);
        visibleRows = Math.max(1, Math.min(MAX_VISIBLE_ROWS, Math.max(1, (pagerY - listY - 4) / ROW_HEIGHT)));
        for (int i = 0; i < Math.min(visibleRows, items.size()); i++) {
            final int index = i;
            addRenderableWidget(Button.builder(Component.literal("›"), button -> select(index))
                .bounds(listX + listWidth - 30, listY + i * ROW_HEIGHT + 10, 22, 20).build());
        }

        Button previous = Button.builder(Component.literal("‹"), button -> { offset = Math.max(0, offset - 30); refresh(); })
            .bounds(listX, pagerY, 32, 20).build();
        previous.active = offset > 0 && !loading;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal("›"), button -> { if (nextOffset != null) { offset = nextOffset; refresh(); } })
            .bounds(listX + 36, pagerY, 32, 20).build();
        next.active = nextOffset != null && !loading;
        addRenderableWidget(next);

        ShyneCloudClient.CloudAvatar current = selectedAvatar();
        int detailX = panelX + panelWidth - 288;
        int detailWidth = 274;
        int detailActionY = Math.min(panelY + 224, footerY - 76);
        Button download = Button.builder(Component.translatable(publicMode ? "screen.shyne_core.cloud.use_public" : "screen.shyne_core.cloud.restore"), button -> downloadCurrent())
            .tooltip(Tooltip.create(Component.translatable(current == null ? "screen.shyne_core.cloud.download.disabled.tooltip" : "screen.shyne_core.cloud.download.tooltip")))
            .bounds(detailX, detailActionY, detailWidth, 20).build();
        download.active = current != null && !loading && (!publicMode || ShyneCloudClient.signedIn());
        addRenderableWidget(download);

        int nextDetailActionY = detailActionY + 24;
        if (publicMode && current != null && !current.permissions().isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.cloud.permissions"), button -> openPermissions(current))
                .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.cloud.permissions.tooltip")))
                .bounds(detailX, nextDetailActionY, detailWidth, 20).build());
            nextDetailActionY += 24;
        }

        AvatarState active = AvatarRuntime.active();
        if (!publicMode && current != null) {
            boolean canPublish = current.published() || (active != null && current.id().equalsIgnoreCase(active.avatarId()));
            Button publish = Button.builder(Component.translatable(current.published()
                    ? "screen.shyne_core.cloud.unpublish" : "screen.shyne_core.cloud.publish"), button -> publishCurrent())
                .tooltip(Tooltip.create(Component.translatable(current.published()
                    ? "screen.shyne_core.cloud.unpublish.tooltip" : "screen.shyne_core.cloud.publish.tooltip")))
                .bounds(detailX, nextDetailActionY, detailWidth, 20).build();
            publish.active = ShyneCloudClient.signedIn() && canPublish && !loading;
            addRenderableWidget(publish);
            nextDetailActionY += 24;
        }
        Button upload = Button.builder(Component.translatable("screen.shyne_core.cloud.backup_active"), button -> uploadActive())
            .tooltip(Tooltip.create(Component.translatable(!ShyneCloudClient.signedIn()
                ? "screen.shyne_core.cloud.upload.signin.tooltip"
                : active == null ? "screen.shyne_core.cloud.upload.avatar.tooltip" : "screen.shyne_core.cloud.upload.tooltip")))
            .bounds(detailX, Math.min(nextDetailActionY, footerY - 24), detailWidth, 20).build();
        upload.active = ShyneCloudClient.signedIn() && active != null && !loading;
        addRenderableWidget(upload);

        ShyneCloudClient.Operation operation = ShyneCloudClient.lastOperation();
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.settings"), button -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new ShyneSettingsScreen(this));
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.cloud.settings.tooltip")))
            .bounds(panelX + panelWidth - 280, footerY, 86, 20).build());
        if (loading && operation.cancellable()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.cloud.cancel"), button -> cancelOperation())
                .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.cloud.cancel.tooltip")))
                .bounds(panelX + panelWidth - 184, footerY, 86, 20).build());
        } else if (!loading && (operation.state() == ShyneCloudClient.State.ERROR || operation.state() == ShyneCloudClient.State.CANCELLED) && retryAction != null) {
            addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.cloud.retry"), button -> retryOperation())
                .bounds(panelX + panelWidth - 184, footerY, 86, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
            .bounds(panelX + panelWidth - 94, footerY, 80, 20).build());

        if (!initialLoadRequested && !loading) {
            initialLoadRequested = true;
            refresh();
        }
    }

    private void refresh() {
        if (loading) return;
        loading = true;
        searchText = search == null ? searchText : search.getValue();
        String query = searchText;
        retryAction = this::refresh;
        if (!publicMode && !ShyneCloudClient.signedIn()) {
            loading = false;
            items = List.of();
            nextOffset = null;
            rebuildWidgets();
            return;
        }
        var request = publicMode ? ShyneCloudClient.discover(query, offset) : ShyneCloudClient.mine(query, offset);
        activeRequest = request;
        long requestId = ++requestGeneration;
        request.whenComplete((page, error) -> completeOnUi(requestId, () -> {
            loading = false;
            activeRequest = null;
            if (error == null && page != null) {
                items = page.items(); nextOffset = page.nextOffset(); selected = Math.min(selected, Math.max(0, items.size() - 1));
            }
            rebuildWidgets();
        }));
        rebuildWidgets();
    }

    private void signIn() {
        if (ShyneCloudClient.signedIn()) {
            ShyneCloudClient.signOut(); offset = 0; selected = 0; items = List.of(); nextOffset = null;
            if (publicMode) refresh(); else rebuildWidgets();
            return;
        }
        loading = true;
        retryAction = this::signIn;
        var request = ShyneCloudClient.signIn(Minecraft.getInstance());
        activeRequest = request;
        long requestId = ++requestGeneration;
        request.whenComplete((result, error) -> completeOnUi(requestId, () -> {
            loading = false;
            activeRequest = null;
            if (result != null && result.state() == ShyneCloudClient.State.SUCCESS) refresh();
            else rebuildWidgets();
        }));
        rebuildWidgets();
    }

    private void select(int index) { selected = index; rebuildWidgets(); }

    private void switchMode(boolean nextPublicMode) {
        if (loading || publicMode == nextPublicMode) return;
        publicMode = nextPublicMode;
        offset = 0;
        selected = 0;
        items = List.of();
        nextOffset = null;
        searchText = "";
        refresh();
    }

    private void downloadCurrent() {
        ShyneCloudClient.CloudAvatar current = selectedAvatar();
        if (current == null) return;
        if (publicMode && ShyneCloudClient.needsPermissionDecision(current)) {
            openPermissions(current);
            return;
        }
        startDownload(current);
    }

    private void openPermissions(ShyneCloudClient.CloudAvatar current) {
        if (this.minecraft == null || current == null) return;
        this.minecraft.gui.setScreen(new PublicAvatarPermissionScreen(this, current, () -> startDownload(current)));
    }

    private void startDownload(ShyneCloudClient.CloudAvatar current) {
        loading = true;
        retryAction = this::downloadCurrent;
        activeRequest = publicMode ? ShyneCloudClient.usePublic(current, Minecraft.getInstance()) : ShyneCloudClient.download(current.id());
        long requestId = ++requestGeneration;
        activeRequest.whenComplete((result, error) -> completeOnUi(requestId, () -> {
            loading = false;
            activeRequest = null;
            AvatarRuntime.refreshCatalogAsync();
            rebuildWidgets();
        }));
        rebuildWidgets();
    }

    private void publishCurrent() {
        ShyneCloudClient.CloudAvatar current = selectedAvatar();
        if (current == null) return;
        loading = true;
        retryAction = this::publishCurrent;
        if (current.published()) {
            activeRequest = ShyneCloudClient.unpublish(current.id());
        } else {
            AvatarState active = AvatarRuntime.active();
            if (active == null || !current.id().equalsIgnoreCase(active.avatarId())) { loading = false; rebuildWidgets(); return; }
            activeRequest = ShyneCloudClient.publish(active.rootDir());
        }
        long requestId = ++requestGeneration;
        activeRequest.whenComplete((result, error) -> completeOnUi(requestId, () -> {
            loading = false;
            activeRequest = null;
            refresh();
        }));
        rebuildWidgets();
    }

    private void uploadActive() {
        AvatarState active = AvatarRuntime.active();
        if (active == null) return;
        loading = true;
        retryAction = this::uploadActive;
        var request = ShyneCloudClient.upload(active.rootDir());
        activeRequest = request;
        long requestId = ++requestGeneration;
        request.whenComplete((result, error) -> completeOnUi(requestId, () -> {
            loading = false;
            activeRequest = null;
            if (result != null && result.state() == ShyneCloudClient.State.SUCCESS) {
                offset = 0; items = List.of(); selected = 0; refresh();
            } else rebuildWidgets();
        }));
        rebuildWidgets();
    }

    private ShyneCloudClient.CloudAvatar selectedAvatar() { return selected >= 0 && selected < items.size() ? items.get(selected) : null; }

    private void cancelOperation() {
        requestGeneration++;
        ShyneCloudClient.cancelCurrentOperation();
        if (activeRequest != null) activeRequest.cancel(true);
        activeRequest = null;
        loading = false;
        rebuildWidgets();
    }

    private void retryOperation() {
        if (retryAction != null && !loading) retryAction.run();
    }

    private void completeOnUi(long requestId, Runnable action) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            // Ignore stale callbacks after cancel, another request, or leaving the screen.
            if (requestId != requestGeneration || client.gui.screen() != this) return;
            action.run();
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0030918);
        graphics.fillGradient(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF20A1630, 0xF2071026);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xFF22D7E8);
        graphics.text(this.font, Component.translatable("screen.shyne_core.cloud.title").withStyle(ChatFormatting.AQUA), panelX + 14, panelY + 14, 0xFFFFFFFF, true);
        graphics.text(this.font, Component.translatable(publicMode ? "screen.shyne_core.cloud.subtitle.public" : "screen.shyne_core.cloud.subtitle"), panelX + 14, panelY + 27, 0xFF91A7C6, false);
        int listX = panelX + 14;
        int listY = panelY + 76;
        int listWidth = Math.max(260, panelWidth - 316);
        ShyneCloudClient.Operation operation = ShyneCloudClient.lastOperation();
        if (items.isEmpty()) {
            Component emptyTitle = loading
                ? Component.translatable("screen.shyne_core.cloud.loading")
                : operation.state() == ShyneCloudClient.State.ERROR
                    ? Component.translatable("screen.shyne_core.cloud.error")
                    : Component.translatable(publicMode ? "screen.shyne_core.cloud.empty.public" : ShyneCloudClient.signedIn() ? "screen.shyne_core.cloud.empty.mine" : "screen.shyne_core.cloud.empty.signin.title");
            int emptyColor = operation.state() == ShyneCloudClient.State.ERROR ? 0xFFFF7F8B : 0xFF91A7C6;
            graphics.text(this.font, emptyTitle, listX + 8, listY + 12, emptyColor, false);
            if (!loading) {
                String help = operation.state() == ShyneCloudClient.State.ERROR
                    ? operation.message()
                    : Component.translatable(publicMode ? "screen.shyne_core.cloud.empty.public.help" : ShyneCloudClient.signedIn() ? "screen.shyne_core.cloud.empty.backup" : "screen.shyne_core.cloud.empty.signin").getString();
                graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(help, listWidth - 16)), listX + 8, listY + 28, 0xFF7188A8, false);
            }
        }
        for (int i = 0; i < Math.min(visibleRows, items.size()); i++) {
            ShyneCloudClient.CloudAvatar item = items.get(i);
            int y = listY + i * ROW_HEIGHT;
            graphics.fill(listX, y, listX + listWidth, y + 36, i == selected ? 0x5532D8EA : 0x2419BFD1);
            if (i == selected) graphics.fill(listX, y, listX + 3, y + 36, 0xFF22D7E8);
            graphics.text(this.font, Component.literal(item.name()), listX + 10, y + 6, 0xFFF3F7FF, false);
            String info = item.id() + "  •  " + item.ownerName() + "  •  v" + item.version();
            graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(info, listWidth - 52)), listX + 10, y + 20, 0xFF8195B4, false);
        }
        int divider = panelX + panelWidth - 302;
        graphics.fill(divider, panelY + 76, divider + 1, Math.max(panelY + 77, pagerY - 6), 0x5532D8EA);
        ShyneCloudClient.CloudAvatar current = selectedAvatar();
        int detailX = divider + 14;
        if (current != null) {
            graphics.text(this.font, Component.literal(current.name()).withStyle(ChatFormatting.WHITE), detailX, panelY + 82, 0xFFFFFFFF, true);
            graphics.text(this.font, Component.translatable(publicMode ? "screen.shyne_core.cloud.public_share" : "screen.shyne_core.cloud.private_backup"), detailX, panelY + 99, 0xFF8DECF3, false);
            String description = current.description().isBlank() ? Component.translatable("screen.shyne_core.cloud.no_description").getString() : current.description();
            graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(description, 262)), detailX, panelY + 122, 0xFF91A7C6, false);
            graphics.text(this.font, Component.translatable(publicMode ? "screen.shyne_core.cloud.public_notice" : "screen.shyne_core.cloud.private_notice"), detailX, panelY + 154, 0xFFB7C6DB, false);
            graphics.text(this.font, Component.translatable(publicMode ? "screen.shyne_core.cloud.public_protected" : "screen.shyne_core.cloud.private_owner_only"), detailX, panelY + 168, 0xFFB7C6DB, false);
        } else {
            graphics.text(this.font, Component.translatable("screen.shyne_core.cloud.start.title"), detailX, panelY + 84, 0xFFF3F7FF, true);
            graphics.text(this.font, Component.translatable(ShyneCloudClient.signedIn() ? "screen.shyne_core.cloud.start.backup" : "screen.shyne_core.cloud.start.signin"), detailX, panelY + 106, 0xFF91A7C6, false);
            graphics.text(this.font, Component.translatable("screen.shyne_core.cloud.start.folder"), detailX, panelY + 122, 0xFF7188A8, false);
        }
        int color = operation.state() == ShyneCloudClient.State.ERROR ? 0xFFFF7F8B
            : operation.state() == ShyneCloudClient.State.SUCCESS ? 0xFF79D8B2
            : operation.state() == ShyneCloudClient.State.CANCELLED ? 0xFF91A0B7 : 0xFF8DECF3;
        if (operation.state() == ShyneCloudClient.State.WORKING) {
            int barLeft = panelX + 14;
            int barRight = panelX + panelWidth - 202;
            int filled = barLeft + (int) ((barRight - barLeft) * operation.progress());
            graphics.fill(barLeft, footerY - 8, barRight, footerY - 5, 0x4432D8EA);
            graphics.fill(barLeft, footerY - 8, filled, footerY - 5, 0xFF32D8EA);
        }
        graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(operation.message(), Math.max(20, panelWidth - 220))), panelX + 14, footerY + 6, color, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { if (this.minecraft != null) this.minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }
}

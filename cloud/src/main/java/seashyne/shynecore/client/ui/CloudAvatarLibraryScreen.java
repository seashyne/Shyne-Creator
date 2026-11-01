package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
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
    private boolean retryIsSignIn;
    private boolean publicMode;
    private ShyneButton accountButton;

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

        // Top Studio Navigation Bar
        ShyneNavBar.addTabs(
            this.minecraft,
            this,
            this.parent,
            ShyneNavBar.Section.CLOUD,
            panelX + panelWidth - 14,
            panelY + 12,
            panelWidth - 240,
            this::addRenderableWidget
        );

        search = new EditBox(this.font, panelX + 14, panelY + 43, Math.max(120, panelWidth - 250), 20, Component.translatable("screen.shyne_core.cloud.search"));
        search.setHint(Component.translatable("screen.shyne_core.cloud.search"));
        search.setValue(searchText);
        addRenderableWidget(search);

        ShyneButton mineTab = ShyneButton.tab(Component.translatable("screen.shyne_core.cloud.tab.mine"), button -> switchMode(false), !publicMode)
            .bounds(panelX + Math.max(180, panelWidth - 458), panelY + 12, 86, 20);
        mineTab.active = publicMode && !loading;
        addRenderableWidget(mineTab);

        ShyneButton publicTab = ShyneButton.tab(Component.translatable("screen.shyne_core.cloud.tab.public"), button -> switchMode(true), publicMode)
            .bounds(panelX + Math.max(270, panelWidth - 368), panelY + 12, 96, 20);
        publicTab.active = !publicMode && !loading;
        addRenderableWidget(publicTab);

        ShyneButton luaTab = ShyneButton.tab(Component.translatable("screen.shyne_core.cloud.tab.lua"), button -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new CloudLuaLibraryScreen(this));
        }, false).bounds(panelX + Math.max(370, panelWidth - 268), panelY + 12, 106, 20);
        addRenderableWidget(luaTab);

        ShyneButton searchButton = ShyneButton.ghost(Component.translatable("screen.shyne_core.cloud.search_button"), button -> { offset = 0; refresh(); })
            .tooltipText(Component.translatable("screen.shyne_core.cloud.search.tooltip"))
            .bounds(panelX + panelWidth - 226, panelY + 43, 72, 20);
        searchButton.active = !loading;
        addRenderableWidget(searchButton);

        accountButton = ShyneButton.secondary(Component.literal(ShyneCloudClient.signedIn() ? ShyneCloudClient.accountName() : Component.translatable("screen.shyne_core.cloud.signin").getString()), button -> signIn())
            .tooltipText(Component.translatable(ShyneCloudClient.signedIn() ? "screen.shyne_core.cloud.signout.tooltip" : "screen.shyne_core.cloud.signin.tooltip"))
            .bounds(panelX + panelWidth - 140, panelY + 43, 126, 20);
        accountButton.active = !loading && (ShyneCloudClient.signedIn() || ShyneCloudClient.signInRetrySeconds() == 0);
        addRenderableWidget(accountButton);

        int listX = panelX + 14;
        int listY = panelY + 76;
        int listWidth = Math.max(260, panelWidth - 316);
        visibleRows = Math.max(1, Math.min(MAX_VISIBLE_ROWS, Math.max(1, (pagerY - listY - 4) / ROW_HEIGHT)));
        for (int i = 0; i < Math.min(visibleRows, items.size()); i++) {
            final int index = i;
            addRenderableWidget(ShyneButton.ghost(Component.literal("›"), button -> select(index))
                .bounds(listX + listWidth - 30, listY + i * ROW_HEIGHT + 10, 22, 20));
        }

        ShyneButton previous = ShyneButton.ghost(Component.literal("‹"), button -> { offset = Math.max(0, offset - 30); refresh(); })
            .bounds(listX, pagerY, 32, 20);
        previous.active = offset > 0 && !loading;
        addRenderableWidget(previous);

        ShyneButton next = ShyneButton.ghost(Component.literal("›"), button -> { if (nextOffset != null) { offset = nextOffset; refresh(); } })
            .bounds(listX + 36, pagerY, 32, 20);
        next.active = nextOffset != null && !loading;
        addRenderableWidget(next);

        ShyneCloudClient.CloudAvatar current = selectedAvatar();
        int detailX = panelX + panelWidth - 288;
        int detailWidth = 274;
        int detailActionY = Math.min(panelY + 224, footerY - 76);
        ShyneButton download = ShyneButton.primary(Component.translatable(publicMode ? "screen.shyne_core.cloud.use_public" : "screen.shyne_core.cloud.restore"), button -> downloadCurrent())
            .tooltipText(Component.translatable(current == null ? "screen.shyne_core.cloud.download.disabled.tooltip" : "screen.shyne_core.cloud.download.tooltip"))
            .bounds(detailX, detailActionY, detailWidth, 20);
        download.active = current != null && !loading && (!publicMode || ShyneCloudClient.signedIn());
        addRenderableWidget(download);

        int nextDetailActionY = detailActionY + 24;
        if (publicMode && current != null && !current.permissions().isEmpty()) {
            addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.cloud.permissions"), button -> openPermissions(current))
                .tooltipText(Component.translatable("screen.shyne_core.cloud.permissions.tooltip"))
                .bounds(detailX, nextDetailActionY, detailWidth, 20));
            nextDetailActionY += 24;
        }

        AvatarState active = AvatarRuntime.active();
        if (!publicMode && current != null) {
            boolean canPublish = current.published() || (active != null && current.id().equalsIgnoreCase(active.avatarId()));
            ShyneButton publish = ShyneButton.secondary(Component.translatable(current.published()
                    ? "screen.shyne_core.cloud.unpublish" : "screen.shyne_core.cloud.publish"), button -> publishCurrent())
                .tooltipText(Component.translatable(current.published()
                    ? "screen.shyne_core.cloud.unpublish.tooltip" : "screen.shyne_core.cloud.publish.tooltip"))
                .bounds(detailX, nextDetailActionY, detailWidth, 20);
            publish.active = ShyneCloudClient.signedIn() && canPublish && !loading;
            addRenderableWidget(publish);
            nextDetailActionY += 24;
        }
        ShyneButton upload = ShyneButton.secondary(Component.translatable("screen.shyne_core.cloud.backup_active"), button -> uploadActive())
            .tooltipText(Component.translatable(!ShyneCloudClient.signedIn()
                ? "screen.shyne_core.cloud.upload.signin.tooltip"
                : active == null ? "screen.shyne_core.cloud.upload.avatar.tooltip" : "screen.shyne_core.cloud.upload.tooltip"))
            .bounds(detailX, Math.min(nextDetailActionY, footerY - 24), detailWidth, 20);
        upload.active = ShyneCloudClient.signedIn() && active != null && !loading;
        addRenderableWidget(upload);

        ShyneCloudClient.Operation operation = ShyneCloudClient.lastOperation();
        addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.settings"), button -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new ShyneSettingsScreen(this));
        }).tooltipText(Component.translatable("screen.shyne_core.cloud.settings.tooltip"))
            .bounds(panelX + panelWidth - 280, footerY, 86, 20));

        if (loading && operation.cancellable()) {
            addRenderableWidget(ShyneButton.danger(Component.translatable("screen.shyne_core.cloud.cancel"), button -> cancelOperation())
                .tooltipText(Component.translatable("screen.shyne_core.cloud.cancel.tooltip"))
                .bounds(panelX + panelWidth - 184, footerY, 86, 20));
        } else if (!loading && (operation.state() == ShyneCloudClient.State.ERROR || operation.state() == ShyneCloudClient.State.CANCELLED) && retryAction != null) {
            ShyneButton retry = ShyneButton.primary(Component.translatable("screen.shyne_core.cloud.retry"), button -> retryOperation())
                .bounds(panelX + panelWidth - 184, footerY, 86, 20);
            retry.active = !retryIsSignIn || ShyneCloudClient.signInRetrySeconds() == 0;
            addRenderableWidget(retry);
        }
        addRenderableWidget(ShyneButton.secondary(Component.translatable("gui.back"), button -> onClose())
            .bounds(panelX + panelWidth - 94, footerY, 80, 20));

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
        retryIsSignIn = false;
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
        retryIsSignIn = true;
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
        retryIsSignIn = false;
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
        retryIsSignIn = false;
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
        retryIsSignIn = false;
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
    public void tick() {
        super.tick();
        if (accountButton != null && !ShyneCloudClient.signedIn()) {
            accountButton.active = !loading && ShyneCloudClient.signInRetrySeconds() == 0;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShyneTheme.BACKDROP);
        ShyneTheme.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
        graphics.text(this.font, Component.literal("✦ ").append(Component.translatable("screen.shyne_core.cloud.title")), panelX + 14, panelY + 14, ShyneTheme.ACCENT, true);
        graphics.text(this.font, Component.translatable(publicMode ? "screen.shyne_core.cloud.subtitle.public" : "screen.shyne_core.cloud.subtitle"), panelX + 14, panelY + 27, ShyneTheme.TEXT_MUTED, false);
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
            int emptyColor = operation.state() == ShyneCloudClient.State.ERROR ? ShyneTheme.DANGER : ShyneTheme.TEXT_MUTED;
            graphics.text(this.font, emptyTitle, listX + 8, listY + 12, emptyColor, false);
            if (!loading) {
                String help = operation.state() == ShyneCloudClient.State.ERROR
                    ? operation.message()
                    : Component.translatable(publicMode ? "screen.shyne_core.cloud.empty.public.help" : ShyneCloudClient.signedIn() ? "screen.shyne_core.cloud.empty.backup" : "screen.shyne_core.cloud.empty.signin").getString();
                graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(help, listWidth - 16)), listX + 8, listY + 28, ShyneTheme.TEXT_MUTED, false);
            }
        }
        for (int i = 0; i < Math.min(visibleRows, items.size()); i++) {
            ShyneCloudClient.CloudAvatar item = items.get(i);
            int y = listY + i * ROW_HEIGHT;
            boolean sel = (i == selected);
            int bg = sel ? ShyneTheme.CARD_SELECTED : ShyneTheme.CARD_BG;
            int border = sel ? ShyneTheme.ACCENT : ShyneTheme.BORDER_SUBTLE;
            ShyneTheme.drawCard(graphics, listX, y, listWidth, 36, bg, border);
            graphics.text(this.font, Component.literal(item.name()), listX + 10, y + 6, ShyneTheme.TEXT_TITLE, false);
            String info = item.id() + "  •  " + item.ownerName() + "  •  v" + item.version();
            graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(info, listWidth - 52)), listX + 10, y + 20, ShyneTheme.TEXT_MUTED, false);
        }
        int divider = panelX + panelWidth - 302;
        graphics.fill(divider, panelY + 76, divider + 1, Math.max(panelY + 77, pagerY - 6), ShyneTheme.BORDER_SUBTLE);
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
        int color = operation.state() == ShyneCloudClient.State.ERROR ? 0xFFFF7F8B : operation.state() == ShyneCloudClient.State.SUCCESS ? 0xFF79D8B2 : operation.state() == ShyneCloudClient.State.CANCELLED ? 0xFF91A0B7 : 0xFF8DECF3;
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

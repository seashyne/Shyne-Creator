package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.avatar.RemoteAvatarResourceBudget;
import seashyne.shynecore.client.avatar.RemoteAvatarState;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Local privacy and performance controls for peer avatars on the current server. */
public final class ServerPlayersScreen extends Screen {
    private static final int SURFACE = 0xF20B1222;
    private static final int SURFACE_RAISED = 0xE8142034;
    private static final int BORDER = 0x66445A78;
    private static final int ACCENT = 0xFF3DD9E8;
    private static final int TEXT_MUTED = 0xFF91A0B7;
    private static final int ROW_HEIGHT = 38;

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listTop;
    private int page;
    private int pageSize;
    private int totalPages;
    private int refreshTicks;
    private String playerFingerprint = "";
    private List<PlayerEntry> players = List.of();

    public ServerPlayersScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.players.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.max(1, Math.min(760, width - 16));
        panelHeight = Math.max(1, Math.min(360, height - 16));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        listTop = panelY + 52;
        pageSize = Math.max(1, (panelHeight - 104) / ROW_HEIGHT);
        players = collectPlayers();
        playerFingerprint = fingerprint(players);
        totalPages = Math.max(1, (players.size() + pageSize - 1) / pageSize);
        page = Math.min(page, totalPages - 1);

        addRenderableWidget(Button.builder(Component.translatable(
                ShyneClientSettings.hideUnratedRemoteAvatars
                    ? "screen.shyne_core.players.unrated.active"
                    : "screen.shyne_core.players.unrated"
            ), ignored -> toggleUnrated())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.players.unrated.tooltip")))
            .bounds(panelX + panelWidth - 116, panelY + 11, 100, 20).build());

        int start = page * pageSize;
        int end = Math.min(players.size(), start + pageSize);
        int actionWidth = Math.max(54, Math.min(72, (panelWidth - 270) / 3));
        for (int i = start; i < end; i++) {
            PlayerEntry entry = players.get(i);
            int y = listTop + (i - start) * ROW_HEIGHT + 8;
            int right = panelX + panelWidth - 16;
            addRenderableWidget(policyButton(entry, "hidden", "screen.shyne_core.players.hide", right - actionWidth * 3 - 8, y, actionWidth));
            addRenderableWidget(policyButton(entry, "muted", "screen.shyne_core.players.mute", right - actionWidth * 2 - 4, y, actionWidth));
            addRenderableWidget(policyButton(entry, "blocked", "screen.shyne_core.players.block", right - actionWidth, y, actionWidth));
        }

        int footerY = panelY + panelHeight - 28;
        Button previous = Button.builder(Component.literal("‹"), ignored -> openPage(page - 1)).bounds(panelX + 16, footerY, 30, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal("›"), ignored -> openPage(page + 1)).bounds(panelX + 50, footerY, 30, 20).build();
        next.active = page + 1 < totalPages;
        addRenderableWidget(next);
        int footerActionWidth = Math.max(58, Math.min(78, (panelWidth - 112) / 3));
        int footerActionsX = panelX + panelWidth - 16 - footerActionWidth * 3 - 8;
        addRenderableWidget(Button.builder(Component.translatable(
                ShyneClientSettings.hideAllRemoteAvatars ? "screen.shyne_core.players.hide_all.active" : "screen.shyne_core.players.hide_all"
            ), ignored -> toggleHideAll())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.players.hide_all.tooltip")))
            .bounds(footerActionsX, footerY, footerActionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.players.show_all"), ignored -> showAll())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.players.show_all.tooltip")))
            .bounds(footerActionsX + footerActionWidth + 4, footerY, footerActionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
            .bounds(footerActionsX + (footerActionWidth + 4) * 2, footerY, footerActionWidth, 20).build());
    }

    private Button policyButton(PlayerEntry entry, String policy, String translation, int x, int y, int buttonWidth) {
        boolean globalHidden = "hidden".equals(policy)
            && (ShyneClientSettings.hideAllRemoteAvatars || ShyneClientSettings.hideUnratedRemoteAvatars);
        boolean active = globalHidden || ShyneClientSettings.hasRemotePlayerPolicy(entry.id(), policy);
        Component label = Component.translatable(active ? translation + ".active" : translation);
        Button button = Button.builder(label, ignored -> togglePolicy(entry.id(), policy))
            .tooltip(Tooltip.create(Component.translatable(translation + ".tooltip", entry.name())))
            .bounds(x, y, buttonWidth, 20).build();
        button.active = !globalHidden;
        return button;
    }

    private void togglePolicy(UUID playerId, String policy) {
        boolean wasLoadable = ShyneClientSettings.shouldLoadRemoteAvatar(playerId);
        boolean enabled = !ShyneClientSettings.hasRemotePlayerPolicy(playerId, policy);
        ShyneClientSettings.setRemotePlayerPolicyEnabled(playerId, policy, enabled);
        boolean loadable = ShyneClientSettings.shouldLoadRemoteAvatar(playerId);
        if (!loadable) {
            if (ShyneClientSettings.isRemotePlayerBlocked(playerId)) ClientAnimationState.removeBlockedRemotePlayer(playerId);
            else ClientAnimationState.removeRemoteAvatar(playerId);
            ShyneClientNetworking.unsubscribeRemoteAvatar(playerId);
        } else if (!wasLoadable) {
            ShyneClientNetworking.requestRemoteAvatar(playerId);
        }
        rebuildWidgets();
    }

    private void showAll() {
        boolean needsResync = ShyneClientSettings.hideAllRemoteAvatars
            || ShyneClientSettings.hideUnratedRemoteAvatars
            || players.stream().anyMatch(player -> ShyneClientSettings.isRemotePlayerBlocked(player.id()));
        ShyneClientSettings.showAllRemoteAvatars(players.stream().map(PlayerEntry::id).toList());
        if (needsResync) ShyneClientNetworking.requestRemoteAvatar(null);
        rebuildWidgets();
    }

    private void toggleHideAll() {
        boolean hidden = !ShyneClientSettings.hideAllRemoteAvatars;
        ShyneClientSettings.setHideAllRemoteAvatars(hidden);
        if (hidden) {
            ShyneClientNetworking.resetRemoteAvatarSubscriptions();
            for (RemoteAvatarState avatar : ClientAnimationState.allRemoteAvatars()) ClientAnimationState.removeRemoteAvatar(avatar.playerId());
        } else if (!ShyneClientSettings.hideUnratedRemoteAvatars) {
            ShyneClientNetworking.synchronizeRemoteAvatarSubscriptions();
        }
        rebuildWidgets();
    }

    private void toggleUnrated() {
        boolean hidden = !ShyneClientSettings.hideUnratedRemoteAvatars;
        ShyneClientSettings.setHideUnratedRemoteAvatars(hidden);
        if (hidden) {
            ShyneClientNetworking.resetRemoteAvatarSubscriptions();
            for (RemoteAvatarState avatar : ClientAnimationState.allRemoteAvatars()) ClientAnimationState.removeRemoteAvatar(avatar.playerId());
        } else if (!ShyneClientSettings.hideAllRemoteAvatars) {
            ShyneClientNetworking.synchronizeRemoteAvatarSubscriptions();
        }
        rebuildWidgets();
    }

    private void openPage(int target) {
        page = Math.max(0, Math.min(target, totalPages - 1));
        rebuildWidgets();
    }

    private List<PlayerEntry> collectPlayers() {
        Minecraft client = Minecraft.getInstance();
        UUID localId = client.player == null ? null : client.player.getUUID();
        List<PlayerEntry> result = new ArrayList<>();
        if (client.getConnection() != null) {
            for (PlayerInfo player : client.getConnection().getOnlinePlayers()) {
                UUID playerId = player.getProfile().id();
                if (playerId.equals(localId)) continue;
                RemoteAvatarState avatar = ClientAnimationState.getRemoteAvatar(playerId);
                boolean shyne = ShyneTabStatusIcons.isShynePlayer(playerId) || avatar != null;
                if (shyne) result.add(new PlayerEntry(
                    playerId, player.getProfile().name(), avatar,
                    ShyneTabStatusIcons.hasShyneAvatar(playerId) || avatar != null
                ));
            }
        }
        result.sort(Comparator.comparing(PlayerEntry::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private String fingerprint(List<PlayerEntry> entries) {
        StringBuilder result = new StringBuilder();
        for (PlayerEntry entry : entries) {
            result.append(entry.id()).append('|').append(entry.name()).append('|').append(entry.avatarAvailable()).append('|');
            if (entry.avatar() != null) result.append(entry.avatar().avatarId()).append('|').append(entry.avatar().modelId());
            RemoteAvatarResourceBudget.Decision rejection = ClientAnimationState.getRemoteAvatarRejection(entry.id());
            if (rejection != null) {
                result.append('|').append(rejection.resourceId()).append('|').append(rejection.actual()).append('|').append(rejection.limit());
            }
            result.append('|').append(ShyneClientSettings.remotePlayerPolicy(entry.id())).append(';');
        }
        return result.toString();
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks < 20) return;
        refreshTicks = 0;
        List<PlayerEntry> refreshed = collectPlayers();
        String refreshedFingerprint = fingerprint(refreshed);
        if (!refreshedFingerprint.equals(playerFingerprint)) rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA8030710);
        graphics.fill(panelX + 3, panelY + 4, panelX + panelWidth + 3, panelY + panelHeight + 4, 0x66000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SURFACE);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, ACCENT);
        graphics.text(font, title, panelX + 16, panelY + 14, 0xFFF4F7FC, true);
        graphics.text(font, Component.translatable("screen.shyne_core.players.subtitle", players.size()), panelX + 16, panelY + 31, TEXT_MUTED, false);

        int start = page * pageSize;
        int end = Math.min(players.size(), start + pageSize);
        for (int i = start; i < end; i++) renderPlayerRow(graphics, players.get(i), listTop + (i - start) * ROW_HEIGHT);
        if (players.isEmpty()) {
            graphics.text(font, Component.translatable("screen.shyne_core.players.empty"), panelX + 20, listTop + 14, TEXT_MUTED, false);
        }
        if (panelWidth >= 420) {
            graphics.text(font, Component.translatable("screen.shyne_core.avatars.page", page + 1, totalPages), panelX + 88, panelY + panelHeight - 22, TEXT_MUTED, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayerRow(GuiGraphicsExtractor graphics, PlayerEntry entry, int y) {
        graphics.fill(panelX + 16, y + 2, panelX + panelWidth - 16, y + ROW_HEIGHT - 2, SURFACE_RAISED);
        graphics.outline(panelX + 16, y + 2, panelWidth - 32, ROW_HEIGHT - 4, BORDER);
        int actionWidth = Math.max(54, Math.min(72, (panelWidth - 270) / 3));
        int maxWidth = Math.max(40, panelWidth - 59 - actionWidth * 3);
        graphics.text(font, Component.literal(font.plainSubstrByWidth(entry.name(), maxWidth)), panelX + 27, y + 8, 0xFFF4F7FC, false);
        String details;
        int detailsColor;
        if (ShyneClientSettings.isRemotePlayerBlocked(entry.id())) {
            details = Component.translatable("screen.shyne_core.players.blocked_details").getString();
            detailsColor = 0xFFFF8793;
        } else if (ClientAnimationState.getRemoteAvatarRejection(entry.id()) != null) {
            details = budgetRejectionDetails(ClientAnimationState.getRemoteAvatarRejection(entry.id()));
            detailsColor = 0xFFFF8793;
        } else if (entry.avatar() == null) {
            details = Component.translatable(entry.avatarAvailable()
                ? "screen.shyne_core.players.syncing"
                : "screen.shyne_core.players.no_avatar").getString();
            detailsColor = TEXT_MUTED;
        } else {
            details = entry.avatar().avatarId() + "  •  " + costLabel(entry.avatar()) + "  •  "
                + Component.translatable("screen.shyne_core.players.rating.unrated").getString();
            detailsColor = costColor(entry.avatar());
        }
        graphics.text(font, Component.literal(font.plainSubstrByWidth(details, maxWidth)), panelX + 27, y + 22, detailsColor, false);
    }

    private String budgetRejectionDetails(RemoteAvatarResourceBudget.Decision rejection) {
        String resource = Component.translatable("screen.shyne_core.players.budget." + rejection.resourceId()).getString();
        return Component.translatable(
            "screen.shyne_core.players.budget_rejected",
            resource,
            formatBudgetValue(rejection.resource(), rejection.actual()),
            formatBudgetValue(rejection.resource(), rejection.limit())
        ).getString();
    }

    private String formatBudgetValue(RemoteAvatarResourceBudget.Resource resource, long value) {
        if (resource == RemoteAvatarResourceBudget.Resource.TEXTURE_MEMORY
            || resource == RemoteAvatarResourceBudget.Resource.TEXTURE_TRANSFER) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", value / 1_048_576.0);
        }
        if (resource == RemoteAvatarResourceBudget.Resource.TEXTURE_DIMENSION) return value + " px";
        return Long.toString(value);
    }

    private String costLabel(RemoteAvatarState avatar) {
        BbModelDefinition model = ClientAnimationState.getModel(avatar.modelId());
        if (model == null) return Component.translatable("screen.shyne_core.players.cost.unknown").getString();
        ModelCost cost = modelCost(model);
        return Component.translatable(
            "screen.shyne_core.players.cost." + cost.level(),
            cost.triangles(), String.format(java.util.Locale.ROOT, "%.1f", cost.textureBytes() / 1_048_576.0)
        ).getString();
    }

    private int costColor(RemoteAvatarState avatar) {
        BbModelDefinition model = ClientAnimationState.getModel(avatar.modelId());
        if (model == null) return TEXT_MUTED;
        return switch (modelCost(model).level()) {
            case "heavy" -> 0xFFFF8793;
            case "medium" -> 0xFFFFC66D;
            default -> 0xFF7DDBB3;
        };
    }

    private ModelCost modelCost(BbModelDefinition model) {
        long triangles = (long) model.cubes().size() * 12L;
        long vertices = 0L;
        for (var mesh : model.meshes()) {
            vertices += mesh.vertices().size();
            for (var face : mesh.faces()) triangles += Math.max(0, face.vertexIds().size() - 2L);
        }
        long textureBytes = 0L;
        for (var texture : model.textures()) textureBytes += Math.max(0L, (long) texture.width() * texture.height() * 4L);
        boolean heavy = triangles > 20_000L || vertices > 30_000L || textureBytes > 64L * 1_048_576L
            || model.bones().size() > 180 || model.animations().size() > 128;
        boolean medium = triangles > 5_000L || vertices > 8_000L || textureBytes > 16L * 1_048_576L
            || model.bones().size() > 80 || model.animations().size() > 64;
        return new ModelCost(heavy ? "heavy" : medium ? "medium" : "light", triangles, textureBytes);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private record PlayerEntry(UUID id, String name, RemoteAvatarState avatar, boolean avatarAvailable) {}
    private record ModelCost(String level, long triangles, long textureBytes) {}
}

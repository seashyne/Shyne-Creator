package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.client.avatar.RemoteAvatarState;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.network.ShyneNetwork;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShyneTabStatusIcons {
    private static final FontDescription.Resource ICON_FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath("shyne_creator", "tab_icons")
    );
    private static final Map<UUID, ShyneNetwork.NetPlayerPresence> PRESENCE = new ConcurrentHashMap<>();

    private static final String AVATAR_LOADED = "\uE000";
    private static final String SKIN_AND_AVATAR = "\uE001";
    private static final String SYNCING = "\uE002";
    private static final String LOCAL_PLAYER = "\uE003";
    private static final String BLOCKED = "\uE004";
    private static final String SKIN_ONLY = "\uE005";

    private ShyneTabStatusIcons() {}

    public static void handlePresenceSync(String json) {
        ShyneNetwork.PlayerPresenceSyncPayload payload;
        try {
            payload = ShyneNetwork.GSON.fromJson(json, ShyneNetwork.PlayerPresenceSyncPayload.class);
        } catch (RuntimeException malformed) {
            return;
        }
        if (payload == null || payload.players() == null) return;

        PRESENCE.clear();
        for (ShyneNetwork.NetPlayerPresence player : payload.players()) {
            if (player == null || player.playerId() == null) continue;
            try {
                PRESENCE.put(UUID.fromString(player.playerId()), player);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static void clear() {
        PRESENCE.clear();
    }

    public static boolean isShynePlayer(UUID playerId) {
        return playerId != null && PRESENCE.containsKey(playerId);
    }

    public static boolean hasShyneAvatar(UUID playerId) {
        ShyneNetwork.NetPlayerPresence presence = playerId == null ? null : PRESENCE.get(playerId);
        return presence != null && presence.avatarAvailable();
    }

    public static Component decorate(PlayerInfo playerInfo, Component playerName) {
        if (playerInfo == null || playerName == null) return playerName;
        UUID playerId = playerInfo.getProfile().id();
        Minecraft minecraft = Minecraft.getInstance();
        boolean isLocal = minecraft.player != null && playerId.equals(minecraft.player.getUUID());

        Component styledName = playerName;
        if (isLocal) {
            String listText = seashyne.shynecore.client.avatar.AvatarRuntime.localListNameplateText();
            var listStyle = seashyne.shynecore.client.avatar.AvatarRuntime.localListNameplateStyle();
            boolean visible = seashyne.shynecore.client.avatar.AvatarRuntime.localListNameplateVisible();
            if (visible && (!listText.isBlank() || !listStyle.badge().isBlank())) {
                String label = listText.isBlank() ? playerName.getString() : listText;
                if (!listStyle.badge().isBlank()) label = listStyle.badge() + " " + label;
                styledName = Component.literal(label).withStyle(s -> s
                    .withColor(listStyle.colorArgb() & 0x00FFFFFF)
                    .withBold(listStyle.bold())
                    .withItalic(listStyle.italic()));
            }
        } else {
            var remoteStyle = ClientAnimationState.getRemoteNameplateStyle(playerId);
            Boolean visible = ClientAnimationState.getRemoteNameplateVisible(playerId);
            if (Boolean.TRUE.equals(visible) && remoteStyle != null && !remoteStyle.badge().isBlank()) {
                styledName = Component.literal(remoteStyle.badge() + " ").withStyle(s -> s
                    .withColor(remoteStyle.colorArgb() & 0x00FFFFFF)
                    .withBold(remoteStyle.bold())
                    .withItalic(remoteStyle.italic())).append(playerName);
            }
        }

        ShyneNetwork.NetPlayerPresence presence = PRESENCE.get(playerId);
        if (presence == null) return styledName;

        String glyph = statusGlyph(playerId, presence);
        Component icon = Component.literal(glyph).withStyle(style -> style.withFont(ICON_FONT));
        return Component.empty().append(icon).append(" ").append(styledName);
    }

    private static String statusGlyph(UUID playerId, ShyneNetwork.NetPlayerPresence presence) {
        Minecraft minecraft = Minecraft.getInstance();
        PlayerSocialManager social = minecraft.getPlayerSocialManager();
        if (minecraft.player != null && playerId.equals(minecraft.player.getUUID())) return LOCAL_PLAYER;
        if (social.isBlocked(playerId) || social.isHidden(playerId)
            || ShyneClientSettings.isRemoteAvatarHidden(playerId)) return BLOCKED;
        if (!presence.avatarAvailable()) return SKIN_ONLY;

        RemoteAvatarState remoteAvatar = ClientAnimationState.getRemoteAvatar(playerId);
        if (remoteAvatar == null) return SYNCING;
        return remoteAvatar.replaceVanilla() ? AVATAR_LOADED : SKIN_AND_AVATAR;
    }
}

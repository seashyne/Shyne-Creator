package seashyne.shynecore.network;

import java.util.List;

public final class ShyneNetworkProtocol {
    public static final int PROTOCOL_VERSION = 17;
    public static final String CAP_SERVER_AUTHORITATIVE_GAMEPLAY = "gameplay.server_authoritative";
    public static final String CAP_DYNAMIC_ACTION_DECK = "gameplay.dynamic_action_deck_v1";
    public static final String CAP_SKILL_ICON_ASSETS = "content.skill_icon_assets_v1";
    public static final String CAP_ITEM_CATALOG = "content.item_catalog_v1";
    public static final String CAP_CONTENT_REGISTRY_SYNC = "content.registry_sync";
    public static final String CAP_AVATAR_PEER_SNAPSHOT = "avatar.peer_snapshot_v2";
    public static final String CAP_PLAYER_TAB_STATUS = "player.tab_status_v1";
    public static final String CAP_AVATAR_SNAPSHOT_REQUEST = "avatar.snapshot_request_v1";
    public static final String CAP_AVATAR_RECIPIENT_SUBSCRIPTIONS = "avatar.recipient_subscriptions_v1";
    public static final String CAP_AVATAR_BONE_PHYSICS = "avatar.bone_physics_v1";
    public static final String CAP_PACKET_COMPRESSION = "network.compression_v1";
    public static final List<String> SERVER_CAPABILITIES = List.of(
        CAP_SERVER_AUTHORITATIVE_GAMEPLAY,
        CAP_DYNAMIC_ACTION_DECK,
        CAP_SKILL_ICON_ASSETS,
        CAP_ITEM_CATALOG,
        CAP_CONTENT_REGISTRY_SYNC,
        CAP_AVATAR_PEER_SNAPSHOT,
        CAP_PLAYER_TAB_STATUS,
        CAP_AVATAR_SNAPSHOT_REQUEST,
        CAP_AVATAR_RECIPIENT_SUBSCRIPTIONS,
        CAP_AVATAR_BONE_PHYSICS,
        CAP_PACKET_COMPRESSION
    );

    public static final double MAX_AVATAR_TRACKING_DISTANCE = 160.0;
    public static final double MAX_AVATAR_TRACKING_DISTANCE_SQR = MAX_AVATAR_TRACKING_DISTANCE * MAX_AVATAR_TRACKING_DISTANCE;

    private ShyneNetworkProtocol() {}
}

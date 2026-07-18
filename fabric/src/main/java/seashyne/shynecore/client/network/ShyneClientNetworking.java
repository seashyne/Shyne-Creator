package seashyne.shynecore.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.avatar.AvatarValueCodec;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.client.ui.ShyneTabStatusIcons;
import seashyne.shynecore.network.ShyneNetwork;

import java.util.Map;
import java.util.Set;

public final class ShyneClientNetworking {
    private static volatile boolean protocolReady;
    private static volatile Set<String> serverCapabilities = Set.of();

    private ShyneClientNetworking() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.PROTOCOL_STATUS_PAYLOAD, (payload, context) -> context.client().execute(() -> handleProtocolStatus(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.SYNC_MODELS_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handleModelSync(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.SYNC_ACTIVE_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handleActiveSync(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.SYNC_ATTACHMENTS_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handleAttachmentSync(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.SYNC_POWER_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handlePowerSync(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.SYNC_SKILLS_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handleSkillSync(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.SYNC_PROFILES_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handleProfileSync(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.SYNC_WEAPONS_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handleWeaponSync(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.SYNC_LOADOUTS_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handleLoadoutSync(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.PLAY_ANIMATION_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handlePlay(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.STOP_ANIMATION_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handleStop(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.SYNC_AVATAR_VARS_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handleAvatarVarSync(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.SYNC_AVATAR_SNAPSHOTS_PAYLOAD, (payload, context) -> context.client().execute(() -> ClientAnimationState.handleAvatarSnapshotSync(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.SYNC_PLAYER_PRESENCE_PAYLOAD, (payload, context) -> context.client().execute(() -> ShyneTabStatusIcons.handlePresenceSync(payload.json())));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sendProtocolHello());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            protocolReady = false;
            serverCapabilities = Set.of();
            ShyneTabStatusIcons.clear();
        });
    }

    private static void sendProtocolHello() {
        protocolReady = false;
        serverCapabilities = Set.of();
        if (ClientPlayNetworking.canSend(ShyneNetwork.PROTOCOL_HELLO_PAYLOAD)) {
            ClientPlayNetworking.send(new ShyneNetwork.ProtocolHelloPayload(ShyneNetwork.PROTOCOL_VERSION, ShyneCore.VERSION));
        }
    }

    private static void handleProtocolStatus(String json) {
        try {
            ShyneNetwork.ProtocolStatus status = ShyneNetwork.GSON.fromJson(json, ShyneNetwork.ProtocolStatus.class);
            protocolReady = status != null && status.accepted();
            serverCapabilities = protocolReady && status.capabilities() != null
                ? Set.copyOf(status.capabilities())
                : Set.of();
            if (protocolReady) {
                ShyneCore.LOGGER.info("[ShyneNetwork] Connected to Shyne server {} using protocol {}; capabilities={}", status.modVersion(), status.protocolVersion(), serverCapabilities);
            } else if (status != null) {
                ShyneCore.LOGGER.error("[ShyneNetwork] Protocol rejected: {}", status.message());
            }
        } catch (RuntimeException malformed) {
            protocolReady = false;
            serverCapabilities = Set.of();
            ShyneCore.LOGGER.error("[ShyneNetwork] Invalid protocol status from server: {}", malformed.getMessage());
        }
    }

    public static boolean isProtocolReady() {
        return protocolReady;
    }

    public static boolean serverSupports(String capability) {
        return capability != null && serverCapabilities.contains(capability);
    }

    public static Set<String> serverCapabilities() {
        return serverCapabilities;
    }

    public static void sendSkillKey(String skill, int slot) {
        if (serverSupports(ShyneNetwork.CAP_SERVER_AUTHORITATIVE_GAMEPLAY) && ClientPlayNetworking.canSend(ShyneNetwork.SKILL_KEY_PAYLOAD)) {
            ClientPlayNetworking.send(new ShyneNetwork.SkillKeyPayload(skill, slot));
        }
    }

    public static void sendAvatarVars(String avatarId, Map<String, Object> values) {
        if (serverSupports(ShyneNetwork.CAP_AVATAR_PEER_SNAPSHOT) && ClientPlayNetworking.canSend(ShyneNetwork.AVATAR_VAR_SET_PAYLOAD)) {
            ClientPlayNetworking.send(new ShyneNetwork.AvatarVarSetPayload(avatarId, AvatarValueCodec.encodeMap(values)));
        }
    }

    public static boolean sendAvatarSnapshot(ShyneNetwork.NetAvatarSnapshot snapshot) {
        if (!serverSupports(ShyneNetwork.CAP_AVATAR_PEER_SNAPSHOT) || snapshot == null || !ClientPlayNetworking.canSend(ShyneNetwork.AVATAR_SNAPSHOT_PAYLOAD)) return false;
        ClientPlayNetworking.send(new ShyneNetwork.JsonPayload(ShyneNetwork.AVATAR_SNAPSHOT_PAYLOAD, ShyneNetwork.GSON.toJson(new ShyneNetwork.AvatarSnapshotSyncPayload(java.util.List.of(snapshot)))));
        return true;
    }

    public static boolean sendAvatarClear(String playerId) {
        return sendAvatarSnapshot(new ShyneNetwork.NetAvatarSnapshot(
            playerId, "", "", false, false, null, java.util.List.of(), java.util.Map.of(), java.util.Map.of(), "", 0L, java.util.List.of(), "", true
        ));
    }
}

package seashyne.shynecore.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.avatar.AvatarValueCodec;
import seashyne.shynecore.client.config.RemotePlayerPolicy;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.client.ui.ShyneTabStatusIcons;
import seashyne.shynecore.network.ShyneNetwork;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ShyneClientNetworking {
    private static volatile boolean protocolReady;
    private static volatile Set<String> serverCapabilities = Set.of();
    private static volatile int lastOversizeAvatarPayloadBytes = -1;

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
            ClientAnimationState.clearRemoteSession();
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
                synchronizeRemoteAvatarSubscriptions();
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

    public static boolean sendAvatarVars(String avatarId, Map<String, Object> values) {
        if (serverSupports(ShyneNetwork.CAP_AVATAR_PEER_SNAPSHOT) && ClientPlayNetworking.canSend(ShyneNetwork.AVATAR_VAR_SET_PAYLOAD)) {
            ClientPlayNetworking.send(new ShyneNetwork.AvatarVarSetPayload(avatarId, AvatarValueCodec.encodeMap(values)));
            return true;
        }
        return false;
    }

    public static boolean sendAvatarSnapshot(ShyneNetwork.NetAvatarSnapshot snapshot) {
        return sendAvatarSnapshot(snapshot, 0L);
    }

    public static boolean sendAvatarSnapshot(ShyneNetwork.NetAvatarSnapshot snapshot, long revision) {
        if (!serverSupports(ShyneNetwork.CAP_AVATAR_PEER_SNAPSHOT) || snapshot == null || !ClientPlayNetworking.canSend(ShyneNetwork.AVATAR_SNAPSHOT_PAYLOAD)) return false;
        String json = ShyneNetwork.GSON.toJson(new ShyneNetwork.AvatarSnapshotSyncPayload(java.util.List.of(snapshot), revision));
        int payloadBytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > ShyneNetwork.MAX_AVATAR_JSON_CHARS) {
            if (lastOversizeAvatarPayloadBytes != payloadBytes) {
                lastOversizeAvatarPayloadBytes = payloadBytes;
                ShyneCore.LOGGER.error("[ShyneNetwork] Avatar snapshot is {} bytes; maximum single-packet size is {} bytes", payloadBytes, ShyneNetwork.MAX_AVATAR_JSON_CHARS);
            }
            return false;
        }
        lastOversizeAvatarPayloadBytes = -1;
        ClientPlayNetworking.send(new ShyneNetwork.JsonPayload(ShyneNetwork.AVATAR_SNAPSHOT_PAYLOAD, json));
        return true;
    }

    public static boolean sendAvatarClear(String playerId) {
        return sendAvatarClear(playerId, 0L);
    }

    public static boolean sendAvatarClear(String playerId, long revision) {
        return sendAvatarSnapshot(new ShyneNetwork.NetAvatarSnapshot(
            playerId, "", "", false, false, null, java.util.List.of(), java.util.Map.of(), java.util.Map.of(), "", 0L, java.util.List.of(), "", true
        ), revision);
    }

    public static void requestRemoteAvatar(UUID playerId) {
        sendAvatarSubscriptionCommand(playerId == null ? "*" : "+" + playerId);
    }

    public static void unsubscribeRemoteAvatar(UUID playerId) {
        if (playerId != null) sendAvatarSubscriptionCommand("-" + playerId);
    }

    public static void resetRemoteAvatarSubscriptions() {
        sendAvatarSubscriptionCommand("!");
    }

    public static void synchronizeRemoteAvatarSubscriptions() {
        if (!serverSupports(ShyneNetwork.CAP_AVATAR_RECIPIENT_SUBSCRIPTIONS)) return;
        if (ShyneClientSettings.hideAllRemoteAvatars || ShyneClientSettings.hideUnratedRemoteAvatars) {
            resetRemoteAvatarSubscriptions();
            return;
        }

        Set<UUID> suppressed = suppressedPlayerIds();
        if (suppressed.size() <= 240) {
            StringBuilder command = new StringBuilder("*");
            for (UUID playerId : suppressed) command.append(",-").append(playerId);
            if (command.toString().getBytes(StandardCharsets.UTF_8).length <= ShyneNetwork.MAX_AVATAR_SUBSCRIPTION_COMMAND_BYTES) {
                sendAvatarSubscriptionCommand(command.toString());
                return;
            }
        }

        resetRemoteAvatarSubscriptions();
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        List<String> allowed = new ArrayList<>();
        UUID localId = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        connection.getOnlinePlayers().forEach(info -> {
            UUID id = info.getProfile().id();
            if (!id.equals(localId) && !suppressed.contains(id)) allowed.add("+" + id);
        });
        sendSubscriptionBatches(allowed);
    }

    private static Set<UUID> suppressedPlayerIds() {
        Set<UUID> result = new LinkedHashSet<>();
        ShyneClientSettings.remotePlayerPolicies.forEach((encodedId, policy) -> {
            if (!RemotePlayerPolicy.has(policy, RemotePlayerPolicy.HIDDEN)
                && !RemotePlayerPolicy.has(policy, RemotePlayerPolicy.BLOCKED)) return;
            try {
                result.add(UUID.fromString(encodedId));
            } catch (IllegalArgumentException ignored) {}
        });
        return result;
    }

    private static void sendSubscriptionBatches(List<String> commands) {
        for (int start = 0; start < commands.size(); start += 128) {
            int end = Math.min(commands.size(), start + 128);
            sendAvatarSubscriptionCommand(String.join(",", commands.subList(start, end)));
        }
    }

    private static boolean sendAvatarSubscriptionCommand(String command) {
        if (!serverSupports(ShyneNetwork.CAP_AVATAR_RECIPIENT_SUBSCRIPTIONS)
            || !ClientPlayNetworking.canSend(ShyneNetwork.AVATAR_SYNC_REQUEST_PAYLOAD)) return false;
        String safe = command == null ? "" : command;
        if (safe.getBytes(StandardCharsets.UTF_8).length > ShyneNetwork.MAX_AVATAR_SUBSCRIPTION_COMMAND_BYTES) return false;
        ClientPlayNetworking.send(new ShyneNetwork.AvatarSyncRequestPayload(safe));
        return true;
    }
}

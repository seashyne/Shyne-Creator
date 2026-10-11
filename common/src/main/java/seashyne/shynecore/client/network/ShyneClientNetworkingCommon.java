package seashyne.shynecore.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.avatar.AvatarValueCodec;
import seashyne.shynecore.client.config.RemotePlayerPolicy;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.client.ui.ShyneTabStatusIcons;
import seashyne.shynecore.network.ShyneNetworkCommon;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Shared client networking coordinator for Shyne Creator multiloader architecture.
 *
 * <p>Manages client-side connection lifecycle, capability negotiation,
 * packet transmission via {@link ShyneClientPacketSender}, inbound packet routing
 * to client animation/power/item runtimes, and avatar subscription synchronization.</p>
 *
 * <p>Loader-specific client implementations (Fabric/NeoForge) extend this class,
 * bind their platform's packet dispatch mechanism, and register incoming packet hooks.</p>
 */
public abstract class ShyneClientNetworkingCommon {

    // =========================================================================
    // Networking State & Transceiver References
    // =========================================================================

    protected static ShyneClientPacketSender packetSender;
    protected static volatile boolean protocolReady;
    protected static volatile Set<String> serverCapabilities = Set.of();
    protected static volatile int lastOversizeAvatarPayloadBytes = -1;

    // =========================================================================
    // Packet Sender Binding & Dispatch Primitives
    // =========================================================================

    /**
     * Binds the platform-specific packet sender implementation and registers the
     * avatar channel bridge packet dispatcher.
     *
     * @param sender Loader-specific packet sender adapter
     */
    protected static void bindPacketSender(ShyneClientPacketSender sender) {
        packetSender = sender;
        seashyne.shynecore.client.avatar.bridge.AvatarChannelBridge.PACKET_SENDER = packet -> {
            if (serverSupports(ShyneNetworkCommon.CAP_AVATAR_CHANNELS) && canSend(ShyneNetworkCommon.AVATAR_CHANNEL_PAYLOAD)) {
                sender.send(new ShyneNetworkCommon.AvatarChannelPayload(packet.senderId(), packet.channel(), packet.payloadJson()));
            } else {
                seashyne.shynecore.client.avatar.AvatarRuntime.channelPacket("local", packet.channel(), packet.payloadJson());
            }
        };
    }

    /**
     * Checks if the client network channel for the given payload type is available and open.
     *
     * @param type The packet payload type to check
     * @return true if the packet can be safely transmitted
     */
    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return packetSender != null && packetSender.canSend(type);
    }

    /**
     * Transmits a custom packet payload to the server if the packet sender is bound.
     *
     * @param payload The payload to transmit
     */
    public static void sendPacket(CustomPacketPayload payload) {
        if (packetSender != null) {
            packetSender.send(payload);
        }
    }

    // =========================================================================
    // Connection Lifecycle Management
    // =========================================================================

    /**
     * Called when the client establishes a play connection to a server.
     * Initiates the protocol handshake hello packet.
     */
    public static void onConnected() {
        sendProtocolHello();
    }

    /**
     * Called when the client disconnects from the server.
     * Resets protocol status, server capabilities, tab status icons, and remote animation states.
     */
    public static void onDisconnected() {
        protocolReady = false;
        serverCapabilities = Set.of();
        ClientAnimationState.clearRemoteSession();
        ShyneTabStatusIcons.clear();
        seashyne.shynecore.client.state.ClientPowerConfig.reset();
    }

    // =========================================================================
    // Inbound Packet Handling & State Routing
    // =========================================================================

    /**
     * Handles inbound custom channel packets for avatar script communications.
     *
     * @param payload The avatar channel payload received from the server
     */
    public static void handleAvatarChannel(ShyneNetworkCommon.AvatarChannelPayload payload) {
        if (payload == null) return;
        seashyne.shynecore.client.avatar.bridge.AvatarChannelBridge.onPacketReceived(
            payload.senderId(), payload.channel(), payload.payloadJson()
        );
    }

    /**
     * Central dispatch router for all inbound JSON-encoded payloads from the server.
     * Dispatches payloads to their corresponding client runtime subsystems.
     *
     * @param type The payload identifier type
     * @param json Raw JSON string payload
     */
    public static void handlePayload(CustomPacketPayload.Type<ShyneNetworkCommon.JsonPayload> type, String json) {
        if (type.equals(ShyneNetworkCommon.PROTOCOL_STATUS_PAYLOAD)) handleProtocolStatus(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_MODELS_PAYLOAD)) ClientAnimationState.handleModelSync(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_ACTIVE_PAYLOAD)) ClientAnimationState.handleActiveSync(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_ATTACHMENTS_PAYLOAD)) ClientAnimationState.handleAttachmentSync(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_POWER_PAYLOAD)) ClientAnimationState.handlePowerSync(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_SKILLS_PAYLOAD)) ClientAnimationState.handleSkillSync(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_ITEMS_PAYLOAD)) ClientAnimationState.handleItemSync(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_PROFILES_PAYLOAD)) ClientAnimationState.handleProfileSync(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_WEAPONS_PAYLOAD)) ClientAnimationState.handleWeaponSync(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_LOADOUTS_PAYLOAD)) ClientAnimationState.handleLoadoutSync(json);
        else if (type.equals(ShyneNetworkCommon.PLAY_ANIMATION_PAYLOAD)) ClientAnimationState.handlePlay(json);
        else if (type.equals(ShyneNetworkCommon.STOP_ANIMATION_PAYLOAD)) ClientAnimationState.handleStop(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_AVATAR_VARS_PAYLOAD)) ClientAnimationState.handleAvatarVarSync(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_AVATAR_SNAPSHOTS_PAYLOAD)) ClientAnimationState.handleAvatarSnapshotSync(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_PLAYER_PRESENCE_PAYLOAD)) ShyneTabStatusIcons.handlePresenceSync(json);
        else if (type.equals(ShyneNetworkCommon.SYNC_POWER_CONFIG_PAYLOAD)) seashyne.shynecore.client.state.ClientPowerConfig.handleSync(json);
    }

    // =========================================================================
    // Handshake & Capability Negotiation
    // =========================================================================

    /**
     * Sends the initial protocol handshake packet to the server with our protocol and mod version.
     */
    public static void sendProtocolHello() {
        protocolReady = false;
        serverCapabilities = Set.of();
        if (canSend(ShyneNetworkCommon.PROTOCOL_HELLO_PAYLOAD)) {
            sendPacket(new ShyneNetworkCommon.ProtocolHelloPayload(ShyneNetworkCommon.PROTOCOL_VERSION, ShyneCore.VERSION));
        }
    }

    /**
     * Processes server handshake response and initializes negotiated feature capabilities.
     *
     * @param json The JSON representation of {@link ShyneNetworkCommon.ProtocolStatus}
     */
    public static void handleProtocolStatus(String json) {
        try {
            ShyneNetworkCommon.ProtocolStatus status = ShyneNetworkCommon.GSON.fromJson(json, ShyneNetworkCommon.ProtocolStatus.class);
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

    /**
     * @return true if handshake succeeded and server protocol was accepted
     */
    public static boolean isProtocolReady() {
        return protocolReady;
    }

    /**
     * Checks whether the connected server supports a specific feature capability flag.
     *
     * @param capability Capability identifier (e.g. {@link ShyneNetworkCommon#CAP_AVATAR_PEER_SNAPSHOT})
     * @return true if supported by server
     */
    public static boolean serverSupports(String capability) {
        return capability != null && serverCapabilities.contains(capability);
    }

    /**
     * @return Set of capability flags supported by the current server
     */
    public static Set<String> serverCapabilities() {
        return serverCapabilities;
    }

    // =========================================================================
    // Outbound Gameplay & Skill Packets
    // =========================================================================

    /**
     * Sends a skill key activation packet for server-authoritative combat/gameplay execution.
     *
     * @param skill Skill identifier
     * @param slot  Hotkey or skill bar slot index
     */
    public static void sendSkillKey(String skill, int slot) {
        if (serverSupports(ShyneNetworkCommon.CAP_SERVER_AUTHORITATIVE_GAMEPLAY) && canSend(ShyneNetworkCommon.SKILL_KEY_PAYLOAD)) {
            sendPacket(new ShyneNetworkCommon.SkillKeyPayload(skill, slot));
        }
    }

    /**
     * Sends a locally configured action-deck selection for server-side permission and cast checks.
     *
     * @param skillId Target skill identifier
     */
    public static void sendCustomDeckSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) return;
        if (serverSupports(ShyneNetworkCommon.CAP_DYNAMIC_ACTION_DECK) && canSend(ShyneNetworkCommon.SKILL_KEY_PAYLOAD)) {
            sendPacket(new ShyneNetworkCommon.SkillKeyPayload(skillId, ShyneNetworkCommon.DYNAMIC_ACTION_DECK_SLOT));
        }
    }

    /**
     * Sends a skill equip request to assign a skill to a loadout slot on the server.
     *
     * @param skillId Skill identifier
     * @param slot    Target equipment slot
     */
    public static void sendEquipSkill(String skillId, String slot) {
        if (canSend(ShyneNetworkCommon.EQUIP_SKILL_PAYLOAD)) {
            String json = ShyneNetworkCommon.GSON.toJson(new ShyneNetworkCommon.EquipRequest(skillId, slot));
            sendPacket(new ShyneNetworkCommon.JsonPayload(ShyneNetworkCommon.EQUIP_SKILL_PAYLOAD, json));
        }
    }

    // =========================================================================
    // Outbound Avatar Synchronization
    // =========================================================================

    /**
     * Transmits modified avatar synchronized variables to the server.
     *
     * @param avatarId ID of the avatar
     * @param values   Map of modified variable names to values
     * @return true if packet was successfully dispatched
     */
    public static boolean sendAvatarVars(String avatarId, Map<String, Object> values) {
        if (serverSupports(ShyneNetworkCommon.CAP_AVATAR_PEER_SNAPSHOT) && canSend(ShyneNetworkCommon.AVATAR_VAR_SET_PAYLOAD)) {
            sendPacket(new ShyneNetworkCommon.AvatarVarSetPayload(avatarId, AvatarValueCodec.encodeMap(values)));
            return true;
        }
        return false;
    }

    /**
     * Dispatches an avatar snapshot update to the server (with revision 0).
     *
     * @param snapshot The avatar snapshot data
     * @return true if packet passed size limits and was sent
     */
    public static boolean sendAvatarSnapshot(ShyneNetworkCommon.NetAvatarSnapshot snapshot) {
        return sendAvatarSnapshot(snapshot, 0L);
    }

    /**
     * Dispatches an avatar snapshot update with a specific sequence revision number.
     * Performs size verification against {@link ShyneNetworkCommon#MAX_AVATAR_JSON_CHARS}.
     *
     * @param snapshot The avatar snapshot data
     * @param revision State revision number
     * @return true if packet passed size limits and was sent
     */
    public static boolean sendAvatarSnapshot(ShyneNetworkCommon.NetAvatarSnapshot snapshot, long revision) {
        if (!serverSupports(ShyneNetworkCommon.CAP_AVATAR_PEER_SNAPSHOT) || snapshot == null || !canSend(ShyneNetworkCommon.AVATAR_SNAPSHOT_PAYLOAD)) return false;
        String json = ShyneNetworkCommon.GSON.toJson(new ShyneNetworkCommon.AvatarSnapshotSyncPayload(java.util.List.of(snapshot), revision));
        int payloadBytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > ShyneNetworkCommon.MAX_AVATAR_JSON_CHARS) {
            if (lastOversizeAvatarPayloadBytes != payloadBytes) {
                lastOversizeAvatarPayloadBytes = payloadBytes;
                ShyneCore.LOGGER.error("[ShyneNetwork] Avatar snapshot is {} bytes; maximum single-packet size is {} bytes", payloadBytes, ShyneNetworkCommon.MAX_AVATAR_JSON_CHARS);
            }
            return false;
        }
        lastOversizeAvatarPayloadBytes = -1;
        sendPacket(new ShyneNetworkCommon.JsonPayload(ShyneNetworkCommon.AVATAR_SNAPSHOT_PAYLOAD, json));
        return true;
    }

    /**
     * Requests the server to clear and remove an avatar instance for a player.
     *
     * @param playerId UUID string of the player
     * @return true if dispatched
     */
    public static boolean sendAvatarClear(String playerId) {
        return sendAvatarClear(playerId, 0L);
    }

    /**
     * Requests the server to clear and remove an avatar instance for a player at a specific revision.
     *
     * @param playerId UUID string of the player
     * @param revision Sequence revision number
     * @return true if dispatched
     */
    public static boolean sendAvatarClear(String playerId, long revision) {
        return sendAvatarSnapshot(new ShyneNetworkCommon.NetAvatarSnapshot(
            playerId, "", "", false, false, null, java.util.List.of(), java.util.Map.of(), java.util.Map.of(), "", 0L, java.util.List.of(), "", true
        ), revision);
    }

    // =========================================================================
    // Avatar Recipient Subscription Management
    // =========================================================================

    /**
     * Requests the server to stream avatar updates for a specific remote player.
     *
     * @param playerId Target player UUID (or null for all players)
     */
    public static void requestRemoteAvatar(UUID playerId) {
        sendAvatarSubscriptionCommand(playerId == null ? "*" : "+" + playerId);
    }

    /**
     * Requests the server to stop streaming avatar updates for a specific remote player.
     *
     * @param playerId Target player UUID
     */
    public static void unsubscribeRemoteAvatar(UUID playerId) {
        if (playerId != null) sendAvatarSubscriptionCommand("-" + playerId);
    }

    /**
     * Clears all avatar subscriptions on the server (stops all remote avatar syncing).
     */
    public static void resetRemoteAvatarSubscriptions() {
        sendAvatarSubscriptionCommand("!");
    }

    /**
     * Synchronizes current player avatar subscription preferences with the server.
     * Applies privacy policies, hidden/blocked player lists, and batch limits.
     */
    public static void synchronizeRemoteAvatarSubscriptions() {
        if (!serverSupports(ShyneNetworkCommon.CAP_AVATAR_RECIPIENT_SUBSCRIPTIONS)) return;
        if (ShyneClientSettings.hideAllRemoteAvatars || ShyneClientSettings.hideUnratedRemoteAvatars) {
            resetRemoteAvatarSubscriptions();
            return;
        }

        Set<UUID> suppressed = suppressedPlayerIds();
        if (suppressed.size() <= 240) {
            StringBuilder command = new StringBuilder("*");
            for (UUID playerId : suppressed) command.append(",-").append(playerId);
            if (command.toString().getBytes(StandardCharsets.UTF_8).length <= ShyneNetworkCommon.MAX_AVATAR_SUBSCRIPTION_COMMAND_BYTES) {
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
        if (!serverSupports(ShyneNetworkCommon.CAP_AVATAR_RECIPIENT_SUBSCRIPTIONS)
            || !canSend(ShyneNetworkCommon.AVATAR_SYNC_REQUEST_PAYLOAD)) return false;
        String safe = command == null ? "" : command;
        if (safe.getBytes(StandardCharsets.UTF_8).length > ShyneNetworkCommon.MAX_AVATAR_SUBSCRIPTION_COMMAND_BYTES) return false;
        sendPacket(new ShyneNetworkCommon.AvatarSyncRequestPayload(safe));
        return true;
    }
}

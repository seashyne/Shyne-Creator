package seashyne.shynecore.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
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
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> sendProtocolHello());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            protocolReady = false;
            serverCapabilities = Set.of();
            ShyneTabStatusIcons.clear();
        });
    }

    public static void handlePayload(ShyneNetwork.JsonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var type = payload.type();
            String json = payload.json();
            if (type.equals(ShyneNetwork.PROTOCOL_STATUS_PAYLOAD)) handleProtocolStatus(json);
            else if (type.equals(ShyneNetwork.SYNC_MODELS_PAYLOAD)) ClientAnimationState.handleModelSync(json);
            else if (type.equals(ShyneNetwork.SYNC_ACTIVE_PAYLOAD)) ClientAnimationState.handleActiveSync(json);
            else if (type.equals(ShyneNetwork.SYNC_ATTACHMENTS_PAYLOAD)) ClientAnimationState.handleAttachmentSync(json);
            else if (type.equals(ShyneNetwork.SYNC_POWER_PAYLOAD)) ClientAnimationState.handlePowerSync(json);
            else if (type.equals(ShyneNetwork.SYNC_SKILLS_PAYLOAD)) ClientAnimationState.handleSkillSync(json);
            else if (type.equals(ShyneNetwork.SYNC_PROFILES_PAYLOAD)) ClientAnimationState.handleProfileSync(json);
            else if (type.equals(ShyneNetwork.SYNC_WEAPONS_PAYLOAD)) ClientAnimationState.handleWeaponSync(json);
            else if (type.equals(ShyneNetwork.SYNC_LOADOUTS_PAYLOAD)) ClientAnimationState.handleLoadoutSync(json);
            else if (type.equals(ShyneNetwork.PLAY_ANIMATION_PAYLOAD)) ClientAnimationState.handlePlay(json);
            else if (type.equals(ShyneNetwork.STOP_ANIMATION_PAYLOAD)) ClientAnimationState.handleStop(json);
            else if (type.equals(ShyneNetwork.SYNC_AVATAR_VARS_PAYLOAD)) ClientAnimationState.handleAvatarVarSync(json);
            else if (type.equals(ShyneNetwork.SYNC_AVATAR_SNAPSHOTS_PAYLOAD)) ClientAnimationState.handleAvatarSnapshotSync(json);
            else if (type.equals(ShyneNetwork.SYNC_PLAYER_PRESENCE_PAYLOAD)) ShyneTabStatusIcons.handlePresenceSync(json);
        });
    }

    private static void sendProtocolHello() {
        protocolReady = false;
        serverCapabilities = Set.of();
        if (canSend(ShyneNetwork.PROTOCOL_HELLO)) {
            ClientPacketDistributor.sendToServer(new ShyneNetwork.ProtocolHelloPayload(ShyneNetwork.PROTOCOL_VERSION, ShyneCore.VERSION));
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
        if (serverSupports(ShyneNetwork.CAP_SERVER_AUTHORITATIVE_GAMEPLAY) && canSend(ShyneNetwork.SKILL_KEY)) {
            ClientPacketDistributor.sendToServer(new ShyneNetwork.SkillKeyPayload(skill, slot));
        }
    }

    public static void sendAvatarVars(String avatarId, Map<String, Object> values) {
        if (serverSupports(ShyneNetwork.CAP_AVATAR_PEER_SNAPSHOT) && canSend(ShyneNetwork.AVATAR_VAR_SET)) {
            ClientPacketDistributor.sendToServer(new ShyneNetwork.AvatarVarSetPayload(avatarId, AvatarValueCodec.encodeMap(values)));
        }
    }

    public static boolean sendAvatarSnapshot(ShyneNetwork.NetAvatarSnapshot snapshot) {
        if (!serverSupports(ShyneNetwork.CAP_AVATAR_PEER_SNAPSHOT) || snapshot == null || !canSend(ShyneNetwork.AVATAR_SNAPSHOT)) return false;
        ClientPacketDistributor.sendToServer(new ShyneNetwork.JsonPayload(ShyneNetwork.AVATAR_SNAPSHOT_PAYLOAD, ShyneNetwork.GSON.toJson(new ShyneNetwork.AvatarSnapshotSyncPayload(java.util.List.of(snapshot)))));
        return true;
    }

    private static boolean canSend(Identifier channel) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && NetworkRegistry.hasChannel(connection, channel);
    }

    public static boolean sendAvatarClear(String playerId) {
        return sendAvatarSnapshot(new ShyneNetwork.NetAvatarSnapshot(
            playerId, "", "", false, false, null, java.util.List.of(), java.util.Map.of(), java.util.Map.of(), "", 0L, java.util.List.of(), "", true
        ));
    }
}

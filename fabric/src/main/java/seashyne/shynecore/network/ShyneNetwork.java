package seashyne.shynecore.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import seashyne.shynecore.animation.AnimationRuntime;
import seashyne.shynecore.attachment.AttachmentRuntime;
import seashyne.shynecore.equipment.EquipmentRuntime;
import seashyne.shynecore.item.ShyneItemRuntime;
import seashyne.shynecore.model.BbModelRegistry;
import seashyne.shynecore.network.server.ShyneServerNetworking;
import seashyne.shynecore.power.PowerStateMachine;
import seashyne.shynecore.profile.PlayerProfileRuntime;
import seashyne.shynecore.skill.SkillExecutor;
import seashyne.shynecore.skill.SkillRegistry;

/**
 * Fabric server-side network coordinator for Shyne Creator.
 *
 * <p>Extends {@link ShyneNetworkCommon} to maintain source/binary compatibility with external callers.
 * Delegates internal networking logic to {@link ShyneServerNetworking} and binds Fabric's
 * {@link ServerPlayNetworking}, {@link ServerPlayConnectionEvents}, and {@link ServerTickEvents}.</p>
 */
public class ShyneNetwork extends ShyneNetworkCommon {

    public static final int PROTOCOL_VERSION = 19;

    private static boolean payloadsRegistered;

    private final ShyneServerNetworking serverNetworking;

    /**
     * Constructs the Fabric network coordinator and initializes server networking
     * with Fabric's {@link ServerPlayNetworking} packet dispatch callback.
     */
    public ShyneNetwork(BbModelRegistry registry, AnimationRuntime animationRuntime, AttachmentRuntime attachmentRuntime,
                        PowerStateMachine powerStateMachine, SkillExecutor skillExecutor, SkillRegistry skillRegistry,
                        PlayerProfileRuntime profileRuntime, EquipmentRuntime equipmentRuntime, ShyneItemRuntime itemRuntime) {
        this.serverNetworking = new ShyneServerNetworking(
            registry, animationRuntime, attachmentRuntime, powerStateMachine,
            skillExecutor, skillRegistry, profileRuntime, equipmentRuntime, itemRuntime,
            (player, payload) -> {
                if (ServerPlayNetworking.canSend(player, payload.type())) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        );
    }

    /**
     * Registers all custom packet payload types and codecs with Fabric's {@link PayloadTypeRegistry}.
     */
    public static void registerPayloads() {
        if (payloadsRegistered) return;
        for (CustomPacketPayload.Type<JsonPayload> payloadId : JSON_PAYLOAD_IDS.values()) {
            PayloadTypeRegistry.clientboundPlay().register(payloadId, JsonPayload.codec(payloadId));
        }
        PayloadTypeRegistry.serverboundPlay().register(PROTOCOL_HELLO_PAYLOAD, ProtocolHelloPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SKILL_KEY_PAYLOAD, SkillKeyPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EQUIP_SKILL_PAYLOAD, JsonPayload.codec(EQUIP_SKILL_PAYLOAD));
        PayloadTypeRegistry.serverboundPlay().register(AVATAR_VAR_SET_PAYLOAD, AvatarVarSetPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AVATAR_SNAPSHOT_PAYLOAD, JsonPayload.codec(AVATAR_SNAPSHOT_PAYLOAD));
        PayloadTypeRegistry.serverboundPlay().register(AVATAR_SYNC_REQUEST_PAYLOAD, AvatarSyncRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AVATAR_CHANNEL_PAYLOAD, AvatarChannelPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AVATAR_CHANNEL_PAYLOAD, AvatarChannelPayload.CODEC);
        payloadsRegistered = true;
    }

    /**
     * Attaches lifecycle event listeners and serverbound packet receivers to the running {@link MinecraftServer}.
     */
    public void bindServer(MinecraftServer server) {
        this.serverNetworking.bindServer(server);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            if (s == server) {
                this.serverNetworking.onPlayerJoin(handler.player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            if (s == server) {
                this.serverNetworking.onPlayerLeave(handler.player);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            this.serverNetworking.onServerTick(s);
        });

        ServerPlayNetworking.registerGlobalReceiver(PROTOCOL_HELLO_PAYLOAD, (payload, context) ->
            context.server().execute(() -> this.serverNetworking.completeHandshake(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SKILL_KEY_PAYLOAD, (payload, context) ->
            context.server().execute(() -> this.serverNetworking.handleSkillKey(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(EQUIP_SKILL_PAYLOAD, (payload, context) ->
            context.server().execute(() -> this.serverNetworking.handleEquipSkill(context.player(), payload.json())));
        ServerPlayNetworking.registerGlobalReceiver(AVATAR_VAR_SET_PAYLOAD, (payload, context) ->
            context.server().execute(() -> this.serverNetworking.handleAvatarVarSet(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AVATAR_SNAPSHOT_PAYLOAD, (payload, context) ->
            context.server().execute(() -> this.serverNetworking.handleAvatarSnapshot(context.player(), payload.json())));
        ServerPlayNetworking.registerGlobalReceiver(AVATAR_SYNC_REQUEST_PAYLOAD, (payload, context) ->
            context.server().execute(() -> this.serverNetworking.handleAvatarSyncRequest(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AVATAR_CHANNEL_PAYLOAD, (payload, context) ->
            context.server().execute(() -> this.serverNetworking.handleAvatarChannel(context.player(), payload)));
    }

    public ShyneServerNetworking serverNetworking() {
        return this.serverNetworking;
    }

    public void sendModelSync(ServerPlayer player) { serverNetworking.sendModelSync(player); }
    public void sendActiveSync(ServerPlayer player) { serverNetworking.sendActiveSync(player); }
    public void sendAttachmentSync(ServerPlayer player) { serverNetworking.sendAttachmentSync(player); }
    public void sendPowerSync(ServerPlayer player) { serverNetworking.sendPowerSync(player); }
    public void sendSkillSync(ServerPlayer player) { serverNetworking.sendSkillSync(player); }
    public void sendItemSync(ServerPlayer player) { serverNetworking.sendItemSync(player); }
    public void sendProfileSync(ServerPlayer player) { serverNetworking.sendProfileSync(player); }
    public void sendWeaponSync(ServerPlayer player) { serverNetworking.sendWeaponSync(player); }
    public void sendLoadoutSync(ServerPlayer player) { serverNetworking.sendLoadoutSync(player); }
    public void sendAvatarVarSync(ServerPlayer player) { serverNetworking.sendAvatarVarSync(player); }
    public void sendPowerConfigSync(ServerPlayer player) { serverNetworking.sendPowerConfigSync(player); }
    public void sendPlayerPresenceSync(ServerPlayer player) { serverNetworking.sendPlayerPresenceSync(player); }
    public void sendAvatarSnapshotSync(ServerPlayer player) { serverNetworking.sendAvatarSnapshotSync(player); }
    public void sendAvatarChannel(ServerPlayer player, AvatarChannelPayload payload) { serverNetworking.sendAvatarChannel(player, payload); }
}

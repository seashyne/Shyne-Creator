package seashyne.shynecore.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
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
 * NeoForge server-side network coordinator for Shyne Creator.
 *
 * <p>Extends {@link ShyneNetworkCommon} to maintain source/binary compatibility with external callers.
 * Delegates internal networking logic to {@link ShyneServerNetworking} and binds NeoForge's
 * {@link RegisterPayloadHandlersEvent}, {@link PacketDistributor}, and server lifecycle event listeners.</p>
 */
public class ShyneNetwork extends ShyneNetworkCommon {

    public static final int PROTOCOL_VERSION = 19;

    private static boolean payloadsRegistered;
    private static ShyneNetwork activeInstance;

    private final ShyneServerNetworking serverNetworking;

    /**
     * Constructs the NeoForge network coordinator and initializes server networking
     * with NeoForge's {@link PacketDistributor} packet dispatch callback.
     */
    public ShyneNetwork(BbModelRegistry registry, AnimationRuntime animationRuntime, AttachmentRuntime attachmentRuntime,
                        PowerStateMachine powerStateMachine, SkillExecutor skillExecutor, SkillRegistry skillRegistry,
                        PlayerProfileRuntime profileRuntime, EquipmentRuntime equipmentRuntime, ShyneItemRuntime itemRuntime) {
        this.serverNetworking = new ShyneServerNetworking(
            registry, animationRuntime, attachmentRuntime, powerStateMachine,
            skillExecutor, skillRegistry, profileRuntime, equipmentRuntime, itemRuntime,
            (player, payload) -> {
                if (NetworkRegistry.hasChannel(player.connection, payload.type().id())) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        );
        activeInstance = this;
    }

    /**
     * Registers all custom packet payload types, codecs, and bidirectional handlers
     * with NeoForge's {@link RegisterPayloadHandlersEvent}.
     *
     * @param modEventBus The NeoForge mod event bus
     */
    public static void registerPayloads(IEventBus modEventBus) {
        if (payloadsRegistered) return;
        payloadsRegistered = true;
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            var registrar = event.registrar(Integer.toString(PROTOCOL_VERSION)).optional();
            for (CustomPacketPayload.Type<JsonPayload> payloadId : JSON_PAYLOAD_IDS.values()) {
                registrar.playToClient(payloadId, JsonPayload.codec(payloadId),
                    seashyne.shynecore.client.network.ShyneClientNetworking::handlePayload);
            }
            registrar.playToServer(PROTOCOL_HELLO_PAYLOAD, ProtocolHelloPayload.CODEC, (payload, context) -> {
                if (activeInstance != null && context.player() instanceof ServerPlayer player) {
                    activeInstance.serverNetworking.completeHandshake(player, payload);
                }
            });
            registrar.playToServer(SKILL_KEY_PAYLOAD, SkillKeyPayload.CODEC, (payload, context) -> {
                if (activeInstance != null && context.player() instanceof ServerPlayer player) {
                    activeInstance.serverNetworking.handleSkillKey(player, payload);
                }
            });
            registrar.playToServer(EQUIP_SKILL_PAYLOAD, JsonPayload.codec(EQUIP_SKILL_PAYLOAD), (payload, context) -> {
                if (activeInstance != null && context.player() instanceof ServerPlayer player) {
                    activeInstance.serverNetworking.handleEquipSkill(player, payload.json());
                }
            });
            registrar.playToServer(AVATAR_VAR_SET_PAYLOAD, AvatarVarSetPayload.CODEC, (payload, context) -> {
                if (activeInstance != null && context.player() instanceof ServerPlayer player) {
                    activeInstance.serverNetworking.handleAvatarVarSet(player, payload);
                }
            });
            registrar.playToServer(AVATAR_SNAPSHOT_PAYLOAD, JsonPayload.codec(AVATAR_SNAPSHOT_PAYLOAD), (payload, context) -> {
                if (activeInstance != null && context.player() instanceof ServerPlayer player) {
                    activeInstance.serverNetworking.handleAvatarSnapshot(player, payload.json());
                }
            });
            registrar.playToServer(AVATAR_SYNC_REQUEST_PAYLOAD, AvatarSyncRequestPayload.CODEC, (payload, context) -> {
                if (activeInstance != null && context.player() instanceof ServerPlayer player) {
                    activeInstance.serverNetworking.handleAvatarSyncRequest(player, payload);
                }
            });
            registrar.playToClient(AVATAR_CHANNEL_PAYLOAD, AvatarChannelPayload.CODEC,
                seashyne.shynecore.client.network.ShyneClientNetworking::handleAvatarChannelPayload);
            registrar.playToServer(AVATAR_CHANNEL_PAYLOAD, AvatarChannelPayload.CODEC, (payload, context) -> {
                if (activeInstance != null && context.player() instanceof ServerPlayer player) {
                    activeInstance.serverNetworking.handleAvatarChannel(player, payload);
                }
            });
        });
    }

    public void bindServer(MinecraftServer server) {
        this.serverNetworking.bindServer(server);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                this.serverNetworking.onPlayerJoin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                this.serverNetworking.onPlayerLeave(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            this.serverNetworking.onServerTick(event.getServer());
        });
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

package seashyne.shynecore.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import seashyne.shynecore.network.ShyneNetwork;

/**
 * NeoForge-specific client networking adapter for Shyne Creator.
 *
 * <p>Binds NeoForge's {@link ClientPacketDistributor} and {@link NetworkRegistry} channel checks
 * to {@link ShyneClientNetworkingCommon}, registers network payload handlers executed on the render thread,
 * and attaches login/logout lifecycle listeners via {@link NeoForge#EVENT_BUS}.</p>
 */
public final class ShyneClientNetworking extends ShyneClientNetworkingCommon {

    private ShyneClientNetworking() {}

    /**
     * Initializes client networking for NeoForge.
     * Registers the client packet sender and network event listeners.
     */
    public static void init() {
        // Bind NeoForge client packet transmission adapter
        bindPacketSender(new ShyneClientPacketSender() {
            @Override
            public boolean canSend(CustomPacketPayload.Type<?> type) {
                var connection = Minecraft.getInstance().getConnection();
                return connection != null && NetworkRegistry.hasChannel(connection, type.id());
            }

            @Override
            public void send(CustomPacketPayload payload) {
                ClientPacketDistributor.sendToServer(payload);
            }
        });

        // Hook client network login and logout lifecycle events
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> onConnected());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> onDisconnected());
    }

    /**
     * Handler invoked by NeoForge network pipeline when receiving an avatar script channel payload.
     *
     * @param payload Inbound avatar channel payload
     * @param context NeoForge payload execution context
     */
    public static void handleAvatarChannelPayload(ShyneNetwork.AvatarChannelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleAvatarChannel(payload));
    }

    /**
     * Handler invoked by NeoForge network pipeline when receiving an inbound JSON payload.
     *
     * @param payload Inbound JSON payload
     * @param context NeoForge payload execution context
     */
    public static void handlePayload(ShyneNetwork.JsonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handlePayload(payload.type(), payload.json()));
    }
}

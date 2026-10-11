package seashyne.shynecore.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import seashyne.shynecore.network.ShyneNetwork;

/**
 * Fabric-specific client networking adapter for Shyne Creator.
 *
 * <p>Binds Fabric's {@link ClientPlayNetworking} dispatch API to {@link ShyneClientNetworkingCommon},
 * registers global payload receivers for clientbound JSON and avatar channel packets,
 * and attaches connection lifecycle listeners via {@link ClientPlayConnectionEvents}.</p>
 */
public final class ShyneClientNetworking extends ShyneClientNetworkingCommon {

    private ShyneClientNetworking() {}

    /**
     * Initializes the client networking layer for Fabric.
     * Hooks into Fabric networking registry and client connection lifecycle events.
     */
    public static void init() {
        // Bind Fabric client packet transmission adapter
        bindPacketSender(new ShyneClientPacketSender() {
            @Override
            public boolean canSend(CustomPacketPayload.Type<?> type) {
                return ClientPlayNetworking.canSend(type);
            }

            @Override
            public void send(CustomPacketPayload payload) {
                ClientPlayNetworking.send(payload);
            }
        });

        // Register clientbound global receivers for all server-pushed JSON payloads
        for (var payloadId : ShyneNetwork.JSON_PAYLOAD_IDS.values()) {
            ClientPlayNetworking.registerGlobalReceiver(payloadId, (payload, context) ->
                context.client().execute(() -> handlePayload(payloadId, payload.json()))
            );
        }

        // Register clientbound receiver for bidirectional avatar channel scripting
        ClientPlayNetworking.registerGlobalReceiver(ShyneNetwork.AVATAR_CHANNEL_PAYLOAD, (payload, context) ->
            context.client().execute(() -> handleAvatarChannel(payload))
        );

        // Hook client play connection join/disconnect events
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onConnected());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnected());
    }
}

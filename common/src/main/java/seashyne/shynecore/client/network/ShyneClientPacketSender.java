package seashyne.shynecore.client.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Interface abstracting clientbound-to-server packet transmission and channel capability checks.
 * Decouples the client networking engine from loader-specific dispatch mechanisms
 * (e.g. Fabric's ClientPlayNetworking vs NeoForge's ClientPacketDistributor).
 */
public interface ShyneClientPacketSender {
    /**
     * Checks if the client connection can currently send a payload of the given type.
     *
     * @param type The custom packet payload type
     * @return true if the channel is registered and available on the server
     */
    boolean canSend(CustomPacketPayload.Type<?> type);

    /**
     * Sends the packet payload to the server.
     *
     * @param payload The custom packet payload to send
     */
    void send(CustomPacketPayload payload);
}

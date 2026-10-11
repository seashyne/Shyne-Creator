package seashyne.shynecore.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Functional interface abstracting outbound packet dispatch from server to player.
 * Allows the common networking module to remain independent of mod loader APIs
 * (e.g. NeoForge's PacketDistributor vs Fabric's ServerPlayNetworking).
 */
@FunctionalInterface
public interface ShynePacketSender {
    /**
     * Sends the specified custom packet payload to the target player.
     *
     * @param player  The recipient server player
     * @param payload The custom packet payload to send
     */
    void send(ServerPlayer player, CustomPacketPayload payload);
}

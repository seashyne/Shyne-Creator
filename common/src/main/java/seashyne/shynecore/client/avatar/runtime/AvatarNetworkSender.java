package seashyne.shynecore.client.avatar.runtime;

import seashyne.shynecore.network.ShyneNetwork;

public interface AvatarNetworkSender {
    boolean sendAvatarSnapshot(ShyneNetwork.NetAvatarSnapshot snapshot, long transportRevision);
    boolean sendAvatarClear(String playerId, long transportRevision);
}

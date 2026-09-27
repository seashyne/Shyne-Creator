package seashyne.shynecore.client.avatar.runtime;

import net.minecraft.client.Minecraft;

public interface AvatarCloudDelegate {
    boolean restoreSelectedPublic(Minecraft client);
    void tick(Minecraft client);
    void clearActivePublic();
}

package seashyne.shynecore.client.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.avatar.AvatarRuntime;

/**
 * Intercepts chat messages received by the client and dispatches them to avatar runtimes.
 */
@Mixin(ChatListener.class)
public abstract class AvatarChatMixin {
    @Inject(method = "handlePlayerChatMessage", at = @At("HEAD"))
    private void shyne$dispatchPlayerChat(PlayerChatMessage message, GameProfile profile, ChatType.Bound bound, CallbackInfo ci) {
        String text = message != null ? message.decoratedContent().getString() : "";
        String senderUuid = profile != null && profile.id() != null ? profile.id().toString() : "";
        String senderName = profile != null && profile.name() != null ? profile.name() : "";
        AvatarRuntime.dispatchChatReceive(text, text, senderUuid, senderName);
    }

    @Inject(method = "handleDisguisedChatMessage", at = @At("HEAD"))
    private void shyne$dispatchDisguisedChat(Component message, ChatType.Bound bound, CallbackInfo ci) {
        String text = message != null ? message.getString() : "";
        AvatarRuntime.dispatchChatReceive(text, text, "", "");
    }

    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void shyne$dispatchSystemChat(Component message, boolean overlay, CallbackInfo ci) {
        if (!overlay) {
            String text = message != null ? message.getString() : "";
            AvatarRuntime.dispatchChatReceive(text, text, "system", "System");
        }
    }
}

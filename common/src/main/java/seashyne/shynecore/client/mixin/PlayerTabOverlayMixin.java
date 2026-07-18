package seashyne.shynecore.client.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import seashyne.shynecore.client.ui.ShyneTabStatusIcons;

@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void shynecore$prependStatusIcon(PlayerInfo playerInfo, CallbackInfoReturnable<Component> callback) {
        callback.setReturnValue(ShyneTabStatusIcons.decorate(playerInfo, callback.getReturnValue()));
    }
}

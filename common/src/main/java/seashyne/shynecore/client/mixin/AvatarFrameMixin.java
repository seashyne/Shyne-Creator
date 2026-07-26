package seashyne.shynecore.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.avatar.AvatarRuntime;

/** Dispatches Shyne render events from the real client frame, not the 20 Hz tick. */
@Mixin(Minecraft.class)
public abstract class AvatarFrameMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void shyne$beforeRenderFrame(boolean tick, CallbackInfo ci) {
        AvatarRuntime.renderFrameStart();
    }

    @Inject(method = "renderFrame", at = @At("TAIL"))
    private void shyne$afterRenderFrame(boolean tick, CallbackInfo ci) {
        AvatarRuntime.renderFrameEnd();
    }
}

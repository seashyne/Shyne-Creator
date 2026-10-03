package seashyne.shynecore.client.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.avatar.AvatarRuntime;

@Mixin(KeyboardHandler.class)
public abstract class AvatarRawInputMixin {
    @Inject(method = "keyPress", at = @At("TAIL"))
    private void shyne$dispatchKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        AvatarRuntime.dispatchKeyInput(event.key(), event.keycode(), action, event.modifiers());
    }

    @Inject(method = "charTyped", at = @At("TAIL"))
    private void shyne$dispatchCharacter(long window, CharacterEvent event, CallbackInfo ci) {
        AvatarRuntime.dispatchCharacterInput(event.codepoint(), 0);
    }
}

@Mixin(MouseHandler.class)
abstract class AvatarMouseInputMixin {
    @Inject(method = "onButton", at = @At("TAIL"))
    private void shyne$dispatchMouseButton(long window, MouseButtonInfo event, int action, CallbackInfo ci) {
        AvatarRuntime.dispatchMouseButton(event.button(), action, event.modifiers());
    }

    @Inject(method = "onScroll", at = @At("TAIL"))
    private void shyne$dispatchMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        AvatarRuntime.dispatchMouseScroll(horizontal, vertical);
    }

    @Inject(method = "onMove", at = @At("TAIL"))
    private void shyne$dispatchMouseMove(long window, double xpos, double ypos, double xrel, double yrel, CallbackInfo ci) {
        AvatarRuntime.dispatchMouseMove(xpos, ypos, xrel, yrel);
    }
}

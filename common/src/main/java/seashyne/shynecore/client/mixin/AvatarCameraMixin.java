package seashyne.shynecore.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.avatar.AvatarRuntime;

@Mixin(Camera.class)
public abstract class AvatarCameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow public abstract Vec3 position();
    @Shadow public abstract float xRot();
    @Shadow public abstract float yRot();
    @Shadow public abstract Vector3fc forwardVector();
    @Shadow public abstract Vector3fc upVector();
    @Shadow public abstract Vector3fc leftVector();

    @Inject(method = "update", at = @At("TAIL"))
    private void shyne$applyAvatarCamera(DeltaTracker tracker, CallbackInfo ci) {
        float offsetX = AvatarRuntime.cameraOffsetX();
        float offsetY = AvatarRuntime.cameraOffsetY();
        float offsetZ = AvatarRuntime.cameraOffsetZ();
        if (offsetX != 0 || offsetY != 0 || offsetZ != 0) {
            Vec3 current = position();
            Vector3fc left = leftVector(), up = upVector(), forward = forwardVector();
            setPosition(
                current.x + left.x() * offsetX + up.x() * offsetY + forward.x() * offsetZ,
                current.y + left.y() * offsetX + up.y() * offsetY + forward.y() * offsetZ,
                current.z + left.z() * offsetX + up.z() * offsetY + forward.z() * offsetZ
            );
        }
        float rotationX = AvatarRuntime.cameraRotationX();
        float rotationY = AvatarRuntime.cameraRotationY();
        if (rotationX != 0 || rotationY != 0) setRotation(yRot() + rotationY, xRot() + rotationX);
    }
}

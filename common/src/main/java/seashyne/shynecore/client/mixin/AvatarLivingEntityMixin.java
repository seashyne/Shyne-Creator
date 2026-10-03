package seashyne.shynecore.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.avatar.AvatarRuntime;

/**
 * Dispatches entity lifecycle and gameplay events (damage, totem of undying, item use)
 * to active Shyne and Figura-compatible avatars.
 */
@Mixin(LivingEntity.class)
public abstract class AvatarLivingEntityMixin {
    @Shadow public abstract ItemStack getItemInHand(InteractionHand hand);
    @Shadow public abstract ItemStack getUseItem();

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void shyne$dispatchDamageEvent(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Minecraft client = Minecraft.getInstance();
        boolean isLocal = client.player != null && self.getUUID().equals(client.player.getUUID());
        String msgId = source != null ? source.getMsgId() : "generic";
        Entity attacker = source != null ? source.getEntity() : null;
        String attackerId = attacker != null ? attacker.getUUID().toString() : "";
        AvatarRuntime.dispatchEntityDamage(1.0f, msgId, attackerId, isLocal);
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void shyne$dispatchEntityEvent(byte eventId, CallbackInfo ci) {
        if (eventId == EntityEvent.PROTECTED_FROM_DEATH) {
            LivingEntity self = (LivingEntity) (Object) this;
            Minecraft client = Minecraft.getInstance();
            boolean isLocal = client.player != null && self.getUUID().equals(client.player.getUUID());
            AvatarRuntime.dispatchTotemPop(self.getUUID().toString(), isLocal);
        }
    }

    @Inject(method = "startUsingItem", at = @At("HEAD"))
    private void shyne$dispatchItemUseStart(InteractionHand hand, CallbackInfo ci) {
        ItemStack stack = getItemInHand(hand);
        String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String handName = hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand";
        AvatarRuntime.dispatchItemUse(itemId, handName, "start", 0);
    }

    @Inject(method = "spawnItemParticles", at = @At("HEAD"))
    private void shyne$dispatchItemUseParticles(ItemStack stack, int count, CallbackInfo ci) {
        String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        AvatarRuntime.dispatchItemUse(itemId, "main_hand", "use", count);
    }

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void shyne$dispatchItemUseFinish(CallbackInfo ci) {
        ItemStack stack = getUseItem();
        String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        AvatarRuntime.dispatchItemUse(itemId, "main_hand", "finish", 0);
    }
}

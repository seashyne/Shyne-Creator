package seashyne.shynecore.client.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.render.VanillaRenderMask;

@Mixin(AvatarRenderer.class)
public abstract class PlayerModelMaskMixin {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void shyne$applyAvatarMask(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        VanillaRenderMask.bind(state, entity.getUUID());
        boolean player = VanillaRenderMask.visible(state, "PLAYER");
        if (!player) {
            // Keep the render-layer pass alive so Shyne's native model layer can
            // still draw, while the vanilla base model itself gets no render type.
            state.isInvisible = true;
            state.isInvisibleToPlayer = true;
            state.arrowCount = 0;
            state.stingerCount = 0;
            state.parrotOnLeftShoulder = null;
            state.parrotOnRightShoulder = null;
        }

        state.showHat &= VanillaRenderMask.visible(state, "HAT");
        state.showJacket &= VanillaRenderMask.visible(state, "JACKET");
        state.showLeftPants &= VanillaRenderMask.visible(state, "LEFT_PANTS");
        state.showRightPants &= VanillaRenderMask.visible(state, "RIGHT_PANTS");
        state.showLeftSleeve &= VanillaRenderMask.visible(state, "LEFT_SLEEVE");
        state.showRightSleeve &= VanillaRenderMask.visible(state, "RIGHT_SLEEVE");
        state.showCape &= VanillaRenderMask.visible(state, "CAPE");
        state.showExtraEars &= player;

        boolean armor = VanillaRenderMask.visible(state, "ARMOR");
        state.headEquipment = maskedArmor(state, state.headEquipment, EquipmentSlot.HEAD, "HELMET", armor);
        state.chestEquipment = maskedArmor(state, state.chestEquipment, EquipmentSlot.CHEST, "CHESTPLATE", armor);
        state.legsEquipment = maskedArmor(state, state.legsEquipment, EquipmentSlot.LEGS, "LEGGINGS", armor);
        state.feetEquipment = maskedArmor(state, state.feetEquipment, EquipmentSlot.FEET, "BOOTS", armor);

        // Vanilla's cape layer suppresses the cape while an Elytra is equipped.
        // Clearing only the vanilla Elytra here lets CAPE and ELYTRA remain
        // independently controllable without affecting chest armor.
        if (!VanillaRenderMask.visible(state, "ELYTRA") && state.chestEquipment.is(Items.ELYTRA)) {
            state.chestEquipment = ItemStack.EMPTY;
        }
    }

    private static ItemStack maskedArmor(
        AvatarRenderState state,
        ItemStack stack,
        EquipmentSlot slot,
        String key,
        boolean armorVisible
    ) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        if (HumanoidArmorLayer.shouldRender(stack, slot)
            && (!armorVisible || !VanillaRenderMask.visible(state, key))) return ItemStack.EMPTY;
        return stack;
    }
}

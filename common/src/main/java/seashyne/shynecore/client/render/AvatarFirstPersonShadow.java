package seashyne.shynecore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import seashyne.shynecore.attachment.AttachedModelState;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.UUID;

/**
 * Submits the local player's ground shadow while the camera is in first person.
 * Minecraft drops the camera entity during level extraction, so an avatar that replaces the
 * vanilla model would otherwise cast no shadow at all in first person.
 * ในมุมมองบุคคลที่หนึ่ง Minecraft ข้าม entity ของกล้อง ทำให้อวตารไม่มีเงา จึงส่งเงาเข้า pipeline เอง.
 */
public final class AvatarFirstPersonShadow {
    private AvatarFirstPersonShadow() {}

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelState) {
        if (poseStack == null || collector == null || levelState == null || !ShyneClientSettings.renderAttachments) return;
        CameraRenderState camera = levelState.cameraRenderState;
        if (camera == null || !camera.isFirstPerson || camera.pos == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;
        LocalPlayer player = client.player;
        if (player == null || player.isSpectator() || player.isInvisible()) return;

        AvatarState active = AvatarRuntime.active();
        UUID playerId = player.getUUID();
        if (active == null || !playerId.equals(active.boundEntityId())) return;
        AttachedModelState attachment = ClientAnimationState.getAttachment(playerId);
        if (attachment == null || !attachment.visible()) return;
        BbModelDefinition model = ClientAnimationState.getModel(attachment.modelId());
        if (model == null || !model.hasGeometry()) return;

        EntityRenderState state;
        try {
            state = client.getEntityRenderDispatcher().extractEntity(player, levelState.worldPartialTicks);
        } catch (RuntimeException | StackOverflowError failedExtraction) {
            return;
        }
        if (state == null || state.shadowRadius <= 0f || state.shadowPieces.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(state.x - camera.pos.x, state.y - camera.pos.y, state.z - camera.pos.z);
        collector.submitShadow(poseStack, state.shadowRadius, state.shadowPieces);
        poseStack.popPose();
    }
}

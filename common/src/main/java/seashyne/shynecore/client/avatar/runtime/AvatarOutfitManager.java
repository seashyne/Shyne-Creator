package seashyne.shynecore.client.avatar.runtime;

import net.minecraft.client.Minecraft;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarOutfit;
import seashyne.shynecore.client.avatar.AvatarOutfitLoader;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.render.BbModelTextures;
import seashyne.shynecore.model.BbModelDefinition;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class AvatarOutfitManager {
    private AvatarOutfitManager() {}

    public static List<AvatarOutfit> outfits(AvatarState state) {
        return state == null ? List.of() : state.outfits();
    }

    public static String selectedOutfitId(AvatarState state) {
        return state == null ? AvatarOutfitLoader.DEFAULT_OUTFIT : state.selectedOutfitId();
    }

    public static boolean selectOutfit(AvatarState state, BbModelDefinition model, String outfitId, Minecraft client, Runnable onScheduleSnapshot) {
        if (!applyOutfit(state, model, outfitId, true)) return false;
        if (onScheduleSnapshot != null) onScheduleSnapshot.run();
        return true;
    }

    public static void refreshOutfits(AvatarState state, BbModelDefinition model, Runnable onScheduleSnapshot) {
        if (state == null) return;
        String previousId = state.selectedOutfitId();
        byte[] previousTexture = state.selectedOutfitTexture();
        state.setOutfits(AvatarOutfitLoader.discover(state.rootDir()));
        if (!applyOutfit(state, model, state.selectedOutfitId(), false)) {
            applyOutfit(state, model, AvatarOutfitLoader.DEFAULT_OUTFIT, true);
        }
        if (!previousId.equalsIgnoreCase(state.selectedOutfitId()) || !Arrays.equals(previousTexture, state.selectedOutfitTexture())) {
            if (onScheduleSnapshot != null) onScheduleSnapshot.run();
        }
    }

    public static boolean applyOutfit(AvatarState state, BbModelDefinition model, String outfitId, boolean persist) {
        if (state == null || model == null) return false;
        String requested = outfitId == null || outfitId.isBlank() ? AvatarOutfitLoader.DEFAULT_OUTFIT : outfitId;
        if (AvatarOutfitLoader.DEFAULT_OUTFIT.equalsIgnoreCase(requested)) {
            BbModelTextures.clearOutfit(state.modelId());
            BbModelTextures.clearOutfit(model.modelId());
            state.selectOutfit(AvatarOutfitLoader.DEFAULT_OUTFIT, new byte[0]);
            if (persist) ShyneClientSettings.selectOutfit(state.avatarId(), AvatarOutfitLoader.DEFAULT_OUTFIT);
            return true;
        }

        AvatarOutfit outfit = state.outfits().stream()
            .filter(candidate -> candidate.id().equalsIgnoreCase(requested))
            .findFirst()
            .orElse(null);
        if (outfit == null || !outfit.valid()) return false;
        try {
            byte[] texture = BbModelTextures.installOutfit(model, outfit);
            state.selectOutfit(outfit.id(), texture);
            if (persist) ShyneClientSettings.selectOutfit(state.avatarId(), outfit.id());
            return true;
        } catch (IOException error) {
            ShyneCore.LOGGER.warn("[AvatarOutfit] Could not wear {}: {}", outfit.path(), error.getMessage());
            return false;
        }
    }
}

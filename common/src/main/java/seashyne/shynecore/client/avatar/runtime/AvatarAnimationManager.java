package seashyne.shynecore.client.avatar.runtime;

import seashyne.shynecore.animation.AnimationPlayback;
import seashyne.shynecore.client.avatar.AvatarAnimationLayer;
import seashyne.shynecore.client.avatar.AvatarEmoteDefinition;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AvatarAnimationManager {
    private AvatarAnimationManager() {}

    public static void playAnimation(AvatarState active, BbModelDefinition activeModel, String animationName) {
        playAnimation(active, activeModel, animationName, 1.0, 1.0, 0, null, 0, 0, List.of(), false);
    }

    public static void playAnimation(AvatarState active, BbModelDefinition activeModel, String animationName,
                                     double speed, double weight, int priority, Boolean loopOverride) {
        playAnimation(active, activeModel, animationName, speed, weight, priority, loopOverride, 0, 0, List.of(), false);
    }

    public static void playAnimation(AvatarState active, BbModelDefinition activeModel, String animationName,
                                     double speed, double weight, int priority, Boolean loopOverride,
                                     int fadeInTicks, int fadeOutTicks, List<String> mask, boolean additive) {
        playAnimation(active, activeModel, animationName, speed, weight, priority, loopOverride, fadeInTicks, fadeOutTicks, mask, additive, 0);
    }

    public static void playAnimation(AvatarState active, BbModelDefinition activeModel, String animationName,
                                     double speed, double weight, int priority, Boolean loopOverride,
                                     int fadeInTicks, int fadeOutTicks, List<String> mask, boolean additive, int transitionTicks) {
        if (active == null || active.boundEntityId() == null) return;
        if (animationName == null || animationName.isBlank()) return;
        var definition = activeModel == null ? null : activeModel.findAnimation(animationName);
        double length = definition == null || definition.lengthSeconds() <= 0 ? 2.0 : definition.lengthSeconds();
        boolean looping = definition == null || definition.looping();
        if (loopOverride != null) looping = loopOverride;
        int transition = Math.max(0, Math.min(1200, transitionTicks));
        long now = System.currentTimeMillis();
        if (transition > 0 && !additive) {
            active.animationLayers().replaceAll((key, layer) ->
                !layer.additive() && layer.priority() == priority && !key.equals(animationName.toLowerCase(Locale.ROOT))
                    ? layer.requestStop(now, transition) : layer);
        }
        active.animationLayers().put(animationName.toLowerCase(Locale.ROOT), new AvatarAnimationLayer(
            animationName, now, length, looping,
            Math.max(0.01, Math.min(8.0, speed)), Math.max(0.0, Math.min(1.0, weight)),
            Math.max(-1000, Math.min(1000, priority)),
            Math.max(transition, Math.max(0, Math.min(1200, fadeInTicks))), Math.max(transition, Math.max(0, Math.min(1200, fadeOutTicks))),
            mask == null ? List.of() : List.copyOf(mask), additive, 0L
        ));
        active.markAnimationLayersDirty();
        active.markSnapshotDirty();
        refreshCurrentAnimationPlayback(active, now);
    }

    public static boolean isAnimationPlaying(AvatarState active, String animationName) {
        return active != null && animationName != null && active.animationLayers().containsKey(animationName.toLowerCase(Locale.ROOT));
    }

    public static List<AvatarAnimationLayer> animationLayers(AvatarState active, UUID entityId) {
        if (active == null || entityId == null || !entityId.equals(active.boundEntityId())) return List.of();
        return active.sortedAnimationLayers();
    }

    public static void stopAnimation(AvatarState active, String animationName) {
        if (active == null || animationName == null) return;
        String key = animationName.toLowerCase(Locale.ROOT);
        AvatarAnimationLayer layer = active.animationLayers().get(key);
        if (layer != null && layer.fadeOutTicks() > 0) active.animationLayers().put(key, layer.requestStop(System.currentTimeMillis()));
        else active.animationLayers().remove(key);
        active.markAnimationLayersDirty();
        active.markSnapshotDirty();
        refreshCurrentAnimationPlayback(active, System.currentTimeMillis());
    }

    public static void pruneAnimationLayers(AvatarState active) {
        if (active == null || active.animationLayers().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (active.animationLayers().entrySet().removeIf(entry -> entry.getValue().finished(now))) {
            active.markAnimationLayersDirty();
            active.markSnapshotDirty();
            refreshCurrentAnimationPlayback(active, now);
        }
    }

    public static void refreshCurrentAnimationPlayback(AvatarState active, long now) {
        if (active == null) return;
        active.refreshCurrentAnimationFromLayers(now);
        publishAnimationPlayback(active, now);
    }

    public static void publishAnimationPlayback(AvatarState state, long now) {
        if (state == null) return;
        UUID entityId = state.boundEntityId();
        if (entityId == null) return;
        AvatarAnimationLayer representative = state.representativeAnimationLayer(now);
        if (representative == null) {
            ClientAnimationState.removeLocalPlayback(entityId);
            return;
        }
        ClientAnimationState.putLocalPlayback(new AnimationPlayback(
            entityId, "local-player", state.modelId(), representative.name(), representative.startedAtMillis(),
            representative.lengthSeconds(), true
        ));
    }

    public static void stopAll(AvatarState active) {
        if (active == null || active.boundEntityId() == null) return;
        active.animationLayers().clear();
        active.markAnimationLayersDirty();
        active.clearCurrentAnimation();
        ClientAnimationState.removeLocalPlayback(active.boundEntityId());
    }

    public static boolean playEmote(AvatarState active, BbModelDefinition activeModel, String emoteId) {
        if (active == null) return false;
        AvatarEmoteDefinition emote = active.findEmote(emoteId);
        if (emote == null) return false;
        playAnimation(active, activeModel, emote.animation());
        return true;
    }

    public static boolean triggerAnimationGraph(AvatarState active, BbModelDefinition activeModel, String trigger) {
        if (active == null || trigger == null || trigger.isBlank()) return false;
        String emoteId = active.animationGraph().resolve(trigger);
        if (emoteId == null || emoteId.isBlank()) return false;
        return playEmote(active, activeModel, emoteId);
    }
}

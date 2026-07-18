package seashyne.shynecore.animation;

import net.minecraft.world.entity.Entity;
import seashyne.shynecore.model.BbAnimationDefinition;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbModelRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AnimationRuntime {
    public interface Listener {
        void onPlay(AnimationPlayback playback);
        void onStop(UUID entityId);
    }

    private final BbModelRegistry modelRegistry;
    private final Map<UUID, AnimationPlayback> activeByEntity = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public AnimationRuntime(BbModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public boolean play(Entity entity, String modelId, String animationName) {
        if (entity == null) return false;
        BbModelDefinition model = modelRegistry.get(modelId);
        if (model == null) return false;
        BbAnimationDefinition animation = model.findAnimation(animationName);
        if (animation == null) return false;

        AnimationPlayback playback = new AnimationPlayback(
            entity.getUUID(),
            entity.getName().getString(),
            modelId,
            animation.name(),
            System.currentTimeMillis(),
            animation.lengthSeconds(),
            animation.looping()
        );
        activeByEntity.put(entity.getUUID(), playback);
        listeners.forEach(l -> l.onPlay(playback));
        return true;
    }

    public boolean stop(Entity entity) {
        return entity != null && stop(entity.getUUID());
    }

    public boolean stop(UUID entityId) {
        AnimationPlayback removed = activeByEntity.remove(entityId);
        if (removed != null) listeners.forEach(l -> l.onStop(entityId));
        return removed != null;
    }

    public Optional<AnimationPlayback> get(UUID entityId) {
        return Optional.ofNullable(activeByEntity.get(entityId));
    }

    public Collection<AnimationPlayback> allActive() {
        return Collections.unmodifiableCollection(activeByEntity.values());
    }

    public void pruneMissing(Set<UUID> liveEntityIds) {
        List<UUID> removed = new ArrayList<>();
        for (UUID entityId : activeByEntity.keySet()) {
            if (!liveEntityIds.contains(entityId)) removed.add(entityId);
        }
        removed.forEach(this::stop);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, AnimationPlayback> entry : activeByEntity.entrySet()) {
            AnimationPlayback playback = entry.getValue();
            if (!playback.isExpired(now) || !activeByEntity.remove(entry.getKey(), playback)) continue;
            for (Listener listener : listeners) {
                listener.onStop(entry.getKey());
            }
        }
    }
}

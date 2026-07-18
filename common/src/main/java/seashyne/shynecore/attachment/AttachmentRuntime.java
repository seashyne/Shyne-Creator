package seashyne.shynecore.attachment;

import net.minecraft.world.entity.Entity;
import seashyne.shynecore.model.BbModelRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AttachmentRuntime {
    public interface Listener {
        void onAttachmentSync(Collection<AttachedModelState> attachments);
    }

    private final BbModelRegistry modelRegistry;
    private final Map<UUID, AttachedModelState> attachedByEntity = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public AttachmentRuntime(BbModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public boolean attach(Entity entity, String modelId, float offsetX, float offsetY, float offsetZ, float scale, String anchorBone) {
        if (entity == null || modelRegistry.get(modelId) == null) return false;
        AttachedModelState state = new AttachedModelState(entity.getUUID(), entity.getName().getString(), modelId, offsetX, offsetY, offsetZ, scale, anchorBone == null ? "" : anchorBone, true);
        attachedByEntity.put(entity.getUUID(), state);
        notifyListeners();
        return true;
    }

    public boolean detach(Entity entity) {
        if (entity == null) return false;
        boolean removed = attachedByEntity.remove(entity.getUUID()) != null;
        if (removed) notifyListeners();
        return removed;
    }

    public Optional<AttachedModelState> get(UUID entityId) {
        return Optional.ofNullable(attachedByEntity.get(entityId));
    }

    public Collection<AttachedModelState> allAttached() {
        return List.copyOf(attachedByEntity.values());
    }

    public void pruneMissing(Set<UUID> liveEntityIds) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, AttachedModelState>> it = attachedByEntity.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, AttachedModelState> entry = it.next();
            if (!liveEntityIds.contains(entry.getKey())) {
                it.remove();
                changed = true;
            }
        }
        if (changed) notifyListeners();
    }

    private void notifyListeners() {
        Collection<AttachedModelState> snapshot = allAttached();
        listeners.forEach(l -> l.onAttachmentSync(snapshot));
    }
}

package seashyne.shynecore.summon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import seashyne.shynecore.animation.AnimationRuntime;
import seashyne.shynecore.attachment.AttachmentRuntime;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SummonRuntime {
    private final Map<UUID, SummonedEntityState> summonedByEntity = new ConcurrentHashMap<>();
    private final AttachmentRuntime attachmentRuntime;
    private final AnimationRuntime animationRuntime;

    public SummonRuntime(AttachmentRuntime attachmentRuntime, AnimationRuntime animationRuntime) {
        this.attachmentRuntime = attachmentRuntime;
        this.animationRuntime = animationRuntime;
    }

    public void track(SummonedEntityState state) {
        summonedByEntity.put(state.entityId(), state);
    }

    public Collection<SummonedEntityState> all() {
        return List.copyOf(summonedByEntity.values());
    }

    public void remove(UUID entityId) {
        summonedByEntity.remove(entityId);
    }

    public int despawnByOwner(Entity owner, ServerLevel world) {
        if (owner == null) return 0;
        int removed = 0;
        for (SummonedEntityState state : new ArrayList<>(summonedByEntity.values())) {
            if (!owner.getUUID().equals(state.ownerEntityId())) continue;
            Entity entity = world.getEntityInAnyDimension(state.entityId());
            expireNow(entity, state);
            removed++;
        }
        return removed;
    }

    public void tick(Iterable<ServerLevel> worlds) {
        long now = System.currentTimeMillis();
        for (SummonedEntityState state : new ArrayList<>(summonedByEntity.values())) {
            Entity entity = findEntity(worlds, state.entityId());
            if (entity == null) {
                summonedByEntity.remove(state.entityId());
                continue;
            }
            if (state.isExpired(now)) {
                expireNow(entity, state);
            }
        }
    }

    private Entity findEntity(Iterable<ServerLevel> worlds, UUID entityId) {
        for (ServerLevel world : worlds) {
            Entity entity = world.getEntityInAnyDimension(entityId);
            if (entity != null) return entity;
        }
        return null;
    }

    private void expireNow(Entity entity, SummonedEntityState state) {
        if (entity != null) {
            animationRuntime.stop(entity);
            if (state.detachModelOnExpire()) attachmentRuntime.detach(entity);
            if (state.discardEntityOnExpire()) entity.discard();
        }
        summonedByEntity.remove(state.entityId());
    }
}

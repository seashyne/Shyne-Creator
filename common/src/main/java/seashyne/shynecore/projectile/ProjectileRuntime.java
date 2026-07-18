package seashyne.shynecore.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import seashyne.shynecore.animation.AnimationRuntime;
import seashyne.shynecore.attachment.AttachmentRuntime;
import seashyne.shynecore.team.TeamRuntime;
import java.util.*; import java.util.concurrent.ConcurrentHashMap;

public class ProjectileRuntime {
    private final Map<UUID, SpellProjectileState> projectiles = new ConcurrentHashMap<>();
    private final AttachmentRuntime attachmentRuntime; private final AnimationRuntime animationRuntime; private final TeamRuntime teamRuntime;
    public ProjectileRuntime(AttachmentRuntime attachmentRuntime, AnimationRuntime animationRuntime, TeamRuntime teamRuntime) { this.attachmentRuntime = attachmentRuntime; this.animationRuntime = animationRuntime; this.teamRuntime = teamRuntime; }
    public void track(SpellProjectileState state) { projectiles.put(state.projectileEntityId(), state); }
    public Collection<SpellProjectileState> all() { return List.copyOf(projectiles.values()); }
    public void remove(UUID projectileEntityId) { projectiles.remove(projectileEntityId); }
    public void pruneMissing(Set<UUID> live) { projectiles.entrySet().removeIf(e -> !live.contains(e.getKey())); }
    public void tick(Iterable<ServerLevel> worlds) {
        long now = System.currentTimeMillis();
        for (SpellProjectileState state : projectiles.values()) {
            Entity projectile = findEntity(worlds, state.projectileEntityId());
            if (projectile == null) { projectiles.remove(state.projectileEntityId()); continue; }
            if (state.isExpired(now)) { expire(projectile, state); continue; }
            if (state.homing()) home(projectile, state, worlds);
            List<Entity> hits = findHitTargets(projectile, state); if (hits.isEmpty()) continue;
            int remainingPierce = state.pierceCount();
            for (Entity hit : hits) {
                if (state.hitEntities().contains(hit.getUUID())) continue;
                state.hitEntities().add(hit.getUUID());
                if (state.impactAnimation() != null && !state.impactAnimation().isBlank() && state.modelId() != null && !state.modelId().isBlank()) animationRuntime.play(hit, state.modelId(), state.impactAnimation());
                hit.hurt(projectile.damageSources().magic(), (float) state.damage());
                if (state.explodeRadius() > 0) explodeAround(projectile, hit, state);
                if (remainingPierce-- <= 0 && state.despawnOnImpact()) { expire(projectile, state); break; }
            }
        }
    }
    private void home(Entity projectile, SpellProjectileState state, Iterable<ServerLevel> worlds) {
        Entity owner = findEntity(worlds, state.ownerEntityId()); AABB box = projectile.getBoundingBox().inflate(12.0);
        List<Entity> entities = projectile.level().getEntities(projectile, box, e -> e != null && e.isAlive() && !e.getUUID().equals(state.ownerEntityId()));
        Entity best = null; double bestDist = Double.MAX_VALUE;
        for (Entity entity : entities) { if (owner != null && teamRuntime.areAllied(owner.getUUID(), entity.getUUID())) continue; double dist = projectile.distanceToSqr(entity); if (dist < bestDist) { bestDist = dist; best = entity; } }
        if (best == null) return; double dx = best.getX() - projectile.getX(), dy = (best.getY() + best.getBbHeight() * 0.5) - (projectile.getY() + projectile.getBbHeight() * 0.5), dz = best.getZ() - projectile.getZ(); double len = Math.max(0.001, Math.sqrt(dx*dx+dy*dy+dz*dz));
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.85).add(dx/len * state.homingStrength(), dy/len * state.homingStrength(), dz/len * state.homingStrength())); projectile.hurtMarked = true;
    }
    private List<Entity> findHitTargets(Entity projectile, SpellProjectileState state) {
        AABB box = projectile.getBoundingBox().inflate(state.hitboxRadius());
        List<Entity> entities = projectile.level().getEntities(projectile, box, e -> e != null && e.isAlive() && !e.getUUID().equals(state.ownerEntityId()));
        Entity owner = findEntity(projectile, state.ownerEntityId());
        List<Entity> out = new ArrayList<>();
        for (Entity entity : entities) { if (owner != null && teamRuntime.areAllied(owner.getUUID(), entity.getUUID())) continue; out.add(entity); } out.sort(Comparator.comparingDouble(projectile::distanceToSqr)); return out;
    }
    private void explodeAround(Entity projectile, Entity center, SpellProjectileState state) {
        AABB box = center.getBoundingBox().inflate(state.explodeRadius());
        List<Entity> entities = center.level().getEntities(center, box, e -> e != null && e.isAlive() && !e.getUUID().equals(state.ownerEntityId()));
        Entity owner = findEntity(center, state.ownerEntityId());
        for (Entity entity : entities) {
            if (owner != null && teamRuntime.areAllied(owner.getUUID(), entity.getUUID())) continue;
            entity.hurt(projectile.damageSources().magic(), (float) Math.max(1.0, state.damage() * 0.65));
        }
    }
    private void expire(Entity projectile, SpellProjectileState state) { animationRuntime.stop(projectile); attachmentRuntime.detach(projectile); projectile.discard(); projectiles.remove(state.projectileEntityId()); }
    private Entity findEntity(Iterable<ServerLevel> worlds, UUID entityId) { for (ServerLevel world : worlds) { Entity entity = world.getEntityInAnyDimension(entityId); if (entity != null) return entity; } return null; }
    private Entity findEntity(Entity origin, UUID entityId) {
        if (origin.level() instanceof ServerLevel serverWorld) return serverWorld.getEntityInAnyDimension(entityId);
        return null;
    }
}

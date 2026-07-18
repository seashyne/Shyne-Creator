package seashyne.shynecore.targeting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import seashyne.shynecore.summon.SummonRuntime;
import seashyne.shynecore.summon.SummonedEntityState;
import seashyne.shynecore.team.TeamRuntime;
import java.util.*; import java.util.concurrent.ConcurrentHashMap;

public class TargetingRuntime {
    private final SummonRuntime summonRuntime; private final TeamRuntime teamRuntime; private final Map<UUID, FamiliarTargetState> targetsBySummon = new ConcurrentHashMap<>();
    public TargetingRuntime(SummonRuntime summonRuntime, TeamRuntime teamRuntime) { this.summonRuntime = summonRuntime; this.teamRuntime = teamRuntime; }
    public Optional<FamiliarTargetState> get(UUID summonEntityId) { return Optional.ofNullable(targetsBySummon.get(summonEntityId)); }
    public Collection<FamiliarTargetState> all() { return List.copyOf(targetsBySummon.values()); }
    public void clear(UUID summonEntityId) { targetsBySummon.remove(summonEntityId); }
    public void pruneMissing(Set<UUID> liveEntityIds) { targetsBySummon.entrySet().removeIf(e -> !liveEntityIds.contains(e.getKey()) || !liveEntityIds.contains(e.getValue().ownerEntityId()) || !liveEntityIds.contains(e.getValue().targetEntityId())); }
    public void tick(Iterable<ServerLevel> worlds) {
        long now = System.currentTimeMillis();
        for (SummonedEntityState summon : summonRuntime.all()) {
            if (!isCombatCompanion(summon)) continue;
            Entity owner = findEntity(worlds, summon.ownerEntityId()); Entity familiar = findEntity(worlds, summon.entityId());
            if (owner == null || familiar == null) { targetsBySummon.remove(summon.entityId()); continue; }
            double range = summon.targetRange() > 0 ? summon.targetRange() : 16.0; double hitbox = summon.hitboxRadius() > 0 ? summon.hitboxRadius() : 1.25;
            Entity target = findNearestTarget(owner, familiar, range, summon.targetMode());
            if (target == null) { targetsBySummon.remove(summon.entityId()); orbitOwner(familiar, owner, summon.orbitRadius() > 0 ? summon.orbitRadius() : 1.75, summon.orbitSpeed() > 0 ? summon.orbitSpeed() : 0.18, now); continue; }
            targetsBySummon.put(summon.entityId(), new FamiliarTargetState(summon.entityId(), owner.getUUID(), target.getUUID(), target.getName().getString(), summon.targetMode(), range, hitbox, now));
            pursueWithOrbit(familiar, target, summon.orbitRadius() > 0 ? summon.orbitRadius() : 1.4, summon.orbitSpeed() > 0 ? summon.orbitSpeed() : 0.18, now);
        }
    }
    private boolean isCombatCompanion(SummonedEntityState summon) { String tag = summon.summonTag() == null ? "" : summon.summonTag().toLowerCase(Locale.ROOT); return tag.contains("familiar") || tag.contains("drone") || tag.contains("stand") || tag.contains("shield"); }
    private Entity findNearestTarget(Entity owner, Entity familiar, double range, String mode) {
        AABB box = familiar.getBoundingBox().inflate(range); List<Entity> entities = familiar.level().getEntities(familiar, box, e -> e != null && e.isAlive() && !e.getUUID().equals(owner.getUUID()) && !e.getUUID().equals(familiar.getUUID())); Entity best = null; double bestDist = Double.MAX_VALUE;
        for (Entity entity : entities) { if (!acceptMode(entity, mode)) continue; if (teamRuntime.areAllied(owner.getUUID(), entity.getUUID())) continue; double dist = familiar.distanceToSqr(entity); if (dist < bestDist) { bestDist = dist; best = entity; } }
        return best;
    }
    private boolean acceptMode(Entity entity, String mode) { if (mode == null || mode.isBlank() || mode.equalsIgnoreCase("nearest")) return true; String m = mode.toLowerCase(Locale.ROOT); if (m.equals("players")) return entity instanceof Player; if (m.equals("mobs")) return !(entity instanceof Player); return true; }
    private void orbitOwner(Entity familiar, Entity owner, double radius, double speed, long now) { double angle = (now / 150.0) * speed + (Math.abs(familiar.getUUID().hashCode()) % 360) * Math.PI / 180.0; nudgeToPoint(familiar, owner.getX() + Math.cos(angle) * radius, owner.getY() + owner.getBbHeight() * 0.7, owner.getZ() + Math.sin(angle) * radius, speed); }
    private void pursueWithOrbit(Entity familiar, Entity target, double radius, double speed, long now) { double angle = (now / 110.0) * speed + (Math.abs(familiar.getUUID().hashCode()) % 360) * Math.PI / 180.0; nudgeToPoint(familiar, target.getX() + Math.cos(angle) * radius, target.getY() + target.getBbHeight() * 0.65, target.getZ() + Math.sin(angle) * radius, speed * 1.25); }
    private void nudgeToPoint(Entity entity, double tx, double ty, double tz, double speed) { double dx = tx - entity.getX(), dy = ty - (entity.getY() + entity.getBbHeight() * 0.5), dz = tz - entity.getZ(); double len = Math.max(0.001, Math.sqrt(dx*dx+dy*dy+dz*dz)); entity.setDeltaMovement(dx/len * speed, dy/len * speed, dz/len * speed); entity.hurtMarked = true; }
    private Entity findEntity(Iterable<ServerLevel> worlds, UUID entityId) { for (ServerLevel world : worlds) { Entity entity = world.getEntityInAnyDimension(entityId); if (entity != null) return entity; } return null; }
}

package seashyne.shynecore.projectile;
import java.util.HashSet; import java.util.Set; import java.util.UUID;
public record SpellProjectileState(UUID projectileEntityId, UUID ownerEntityId, String projectileTag, String modelId, String flyAnimation, String impactAnimation, double speed, double hitboxRadius, double damage, long spawnedAtMillis, long expiresAtMillis, boolean despawnOnImpact, boolean homing, double homingStrength, int pierceCount, double explodeRadius, Set<UUID> hitEntities) {
    public SpellProjectileState { if (hitEntities == null) hitEntities = new HashSet<>(); }
    public boolean isExpired(long now) { return expiresAtMillis > 0 && now >= expiresAtMillis; }
}

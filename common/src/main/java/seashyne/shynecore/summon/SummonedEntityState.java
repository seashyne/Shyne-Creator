package seashyne.shynecore.summon;

import java.util.UUID;

public record SummonedEntityState(
    UUID entityId,
    UUID ownerEntityId,
    String ownerName,
    String entityType,
    String summonTag,
    String modelId,
    long spawnedAtMillis,
    long expiresAtMillis,
    boolean discardEntityOnExpire,
    boolean detachModelOnExpire,
    String idleAnimation,
    float offsetX,
    float offsetY,
    float offsetZ,
    float scale,
    String targetMode,
    double targetRange,
    double hitboxRadius,
    double orbitRadius,
    double orbitSpeed
) {
    public boolean isExpired(long now) {
        return expiresAtMillis > 0 && now >= expiresAtMillis;
    }
}

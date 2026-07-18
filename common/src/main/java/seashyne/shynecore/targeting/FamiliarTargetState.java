package seashyne.shynecore.targeting;

import java.util.UUID;

public record FamiliarTargetState(
    UUID summonEntityId,
    UUID ownerEntityId,
    UUID targetEntityId,
    String targetName,
    String mode,
    double range,
    double hitboxRadius,
    long updatedAtMillis
) {}

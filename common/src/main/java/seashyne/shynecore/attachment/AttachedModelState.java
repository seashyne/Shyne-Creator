package seashyne.shynecore.attachment;

import java.util.UUID;

public record AttachedModelState(
    UUID entityId,
    String entityName,
    String modelId,
    float offsetX,
    float offsetY,
    float offsetZ,
    float scale,
    String anchorBone,
    boolean visible
) {
    public AttachedModelState withVisibility(boolean nextVisible) {
        return new AttachedModelState(entityId, entityName, modelId, offsetX, offsetY, offsetZ, scale, anchorBone, nextVisible);
    }
}

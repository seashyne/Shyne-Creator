package seashyne.shynecore.model;

import java.util.List;

public record BbBoneAnimation(
    String boneUuid,
    List<BbKeyframe> rotation,
    List<BbKeyframe> position,
    List<BbKeyframe> scale,
    boolean rotationGlobal,
    boolean quaternionInterpolation
) {
    public BbBoneAnimation(String boneUuid, List<BbKeyframe> rotation, List<BbKeyframe> position, List<BbKeyframe> scale) {
        this(boneUuid, rotation, position, scale, false, false);
    }
}

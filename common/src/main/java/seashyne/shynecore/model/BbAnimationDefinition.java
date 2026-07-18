package seashyne.shynecore.model;

import java.util.List;
import java.util.Map;

public record BbAnimationDefinition(
    String name,
    double lengthSeconds,
    boolean looping,
    int animatorCount,
    Map<String, BbBoneAnimation> boneAnimations,
    List<String> affectedBones
) {}

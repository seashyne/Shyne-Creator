package seashyne.shynecore.skill;

import java.util.List;
import java.util.Map;

public record SkillDefinition(
    String skillId,
    String displayName,
    String description,
    SkillCastType castType,
    SkillSlot defaultSlot,
    double manaCost,
    int cooldownTicks,
    int comboWindowTicks,
    String modelId,
    String animation,
    String icon,
    SkillRequirement requirement,
    List<String> tags,
    Map<String, Object> payload
) {
    public boolean hasTag(String tag) {
        return tags != null && tags.stream().anyMatch(t -> t.equalsIgnoreCase(tag));
    }
}

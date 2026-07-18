package seashyne.shynecore.skill;

import java.util.List;

public record SkillRequirement(int minLevel, List<String> requiredSkills, String requiredWeaponTag) {
    public boolean isEmpty() {
        return minLevel <= 0 && (requiredSkills == null || requiredSkills.isEmpty()) && (requiredWeaponTag == null || requiredWeaponTag.isBlank());
    }
}

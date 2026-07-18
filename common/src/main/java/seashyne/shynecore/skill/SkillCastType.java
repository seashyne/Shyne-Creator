package seashyne.shynecore.skill;

public enum SkillCastType {
    INSTANT,
    PROJECTILE,
    CHANNEL,
    SUMMON,
    AURA,
    UTILITY;

    public static SkillCastType fromString(String value) {
        return tryParse(value).orElse(INSTANT);
    }

    public static java.util.Optional<SkillCastType> tryParse(String value) {
        if (value == null || value.isBlank()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(SkillCastType.valueOf(value.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }
}

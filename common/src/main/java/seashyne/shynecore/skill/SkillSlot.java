package seashyne.shynecore.skill;

public enum SkillSlot {
    PRIMARY,
    SECONDARY,
    UTILITY,
    ULTIMATE,
    PASSIVE_1,
    PASSIVE_2;

    public static SkillSlot fromString(String value) {
        return tryParse(value).orElse(PRIMARY);
    }

    public static java.util.Optional<SkillSlot> tryParse(String value) {
        if (value == null || value.isBlank()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(SkillSlot.valueOf(value.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }
}

package seashyne.shynecore.power;
import java.util.UUID;
public record PowerState(UUID entityId, String comboId, int stage, String branch, long startedAtMillis, long updatedAtMillis, long resetAtMillis, long cooldownEndsAtMillis, double mana, double maxMana, String currentAnimation, boolean locked) {
    public boolean isExpired(long now) { return resetAtMillis > 0 && now >= resetAtMillis; }
    public boolean onCooldown(long now) { return cooldownEndsAtMillis > 0 && now < cooldownEndsAtMillis; }
}

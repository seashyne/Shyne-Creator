package seashyne.shynecore.ai;

public class CooldownNode implements BehaviorNode {
    private final String key;
    private final long cooldownMs;
    private final BehaviorNode child;

    public CooldownNode(String key, long cooldownMs, BehaviorNode child) {
        this.key = key;
        this.cooldownMs = cooldownMs;
        this.child = child;
    }

    @Override
    public BehaviorStatus tick(BehaviorContext context) {
        long now = System.currentTimeMillis();
        long readyAt = ((Number) context.memory().getOrDefault(key, 0L)).longValue();
        if (readyAt > now) return BehaviorStatus.FAILURE;
        BehaviorStatus status = child.tick(context);
        if (status == BehaviorStatus.SUCCESS) context.memory().put(key, now + cooldownMs);
        return status;
    }
}

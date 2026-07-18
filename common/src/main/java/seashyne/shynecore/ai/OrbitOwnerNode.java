package seashyne.shynecore.ai;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class OrbitOwnerNode implements BehaviorNode {
    private final double radius;
    private final double speed;
    public OrbitOwnerNode(double radius, double speed) { this.radius = radius; this.speed = speed; }
    @Override
    public BehaviorStatus tick(BehaviorContext context) {
        Entity owner = context.owner();
        Entity self = context.self();
        if (owner == null || self == null) return BehaviorStatus.FAILURE;
        double angle = (System.currentTimeMillis() / 50.0) * speed;
        Vec3 target = new Vec3(owner.getX() + Math.cos(angle) * radius, owner.getY() + owner.getBbHeight() * 0.7, owner.getZ() + Math.sin(angle) * radius);
        self.setDeltaMovement(target.subtract(self.position()).scale(0.15));
        return BehaviorStatus.SUCCESS;
    }
}

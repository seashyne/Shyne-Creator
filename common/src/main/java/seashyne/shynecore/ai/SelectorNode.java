package seashyne.shynecore.ai;

import java.util.List;

public class SelectorNode implements BehaviorNode {
    private final List<BehaviorNode> children;
    public SelectorNode(List<BehaviorNode> children) { this.children = children; }
    @Override
    public BehaviorStatus tick(BehaviorContext context) {
        for (BehaviorNode child : children) {
            BehaviorStatus status = child.tick(context);
            if (status != BehaviorStatus.FAILURE) return status;
        }
        return BehaviorStatus.FAILURE;
    }
}

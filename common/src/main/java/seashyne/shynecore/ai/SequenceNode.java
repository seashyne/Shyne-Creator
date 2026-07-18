package seashyne.shynecore.ai;

import java.util.List;

public class SequenceNode implements BehaviorNode {
    private final List<BehaviorNode> children;
    public SequenceNode(List<BehaviorNode> children) { this.children = children; }
    @Override
    public BehaviorStatus tick(BehaviorContext context) {
        for (BehaviorNode child : children) {
            BehaviorStatus status = child.tick(context);
            if (status != BehaviorStatus.SUCCESS) return status;
        }
        return BehaviorStatus.SUCCESS;
    }
}

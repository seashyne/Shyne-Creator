package seashyne.shynecore.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class ShyneArtifactItem extends Item {
    public ShyneArtifactItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ShyneItemRuntime runtime = ShyneItems.runtime();
        if (runtime == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return runtime.use(player, hand);
    }
}

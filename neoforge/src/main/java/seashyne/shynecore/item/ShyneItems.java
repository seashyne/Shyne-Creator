package seashyne.shynecore.item;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;

public final class ShyneItems {
    public static final Identifier ARTIFACT_ID = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "artifact");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShyneCore.MOD_ID);
    public static final DeferredItem<ShyneArtifactItem> ARTIFACT =
        ITEMS.registerItem("artifact", ShyneArtifactItem::new, properties -> properties.stacksTo(64));

    private static volatile ShyneItemRuntime runtime;

    private ShyneItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static void bindRuntime(ShyneItemRuntime value) {
        runtime = value;
    }

    static ShyneItemRuntime runtime() {
        return runtime;
    }
}

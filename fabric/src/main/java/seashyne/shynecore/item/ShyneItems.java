package seashyne.shynecore.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import seashyne.shynecore.ShyneCore;

public final class ShyneItems {
    public static final Identifier ARTIFACT_ID = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "artifact");
    public static final ResourceKey<Item> ARTIFACT_KEY = ResourceKey.create(Registries.ITEM, ARTIFACT_ID);
    public static final Item ARTIFACT = Registry.register(
        BuiltInRegistries.ITEM,
        ARTIFACT_KEY,
        new ShyneArtifactItem(new Item.Properties().setId(ARTIFACT_KEY).stacksTo(64))
    );

    private static volatile ShyneItemRuntime runtime;

    private ShyneItems() {}

    public static void init() {
        // Class initialization performs the registry operation before registries freeze.
    }

    public static void bindRuntime(ShyneItemRuntime value) {
        runtime = value;
    }

    static ShyneItemRuntime runtime() {
        return runtime;
    }
}

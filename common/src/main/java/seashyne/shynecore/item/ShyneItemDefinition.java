package seashyne.shynecore.item;

import net.minecraft.world.item.Rarity;

import java.util.List;
import java.util.Map;

public record ShyneItemDefinition(
    String itemId,
    String displayName,
    List<String> description,
    String modelId,
    Rarity rarity,
    int maxStack,
    boolean glint,
    String useSkill,
    String weaponId,
    int cooldownTicks,
    boolean consumeOnUse,
    String sourcePack,
    Map<String, Object> payload
) {}

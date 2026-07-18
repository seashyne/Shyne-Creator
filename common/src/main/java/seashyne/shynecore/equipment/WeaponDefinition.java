package seashyne.shynecore.equipment;

import java.util.List;
import java.util.Map;

public record WeaponDefinition(
    String weaponId,
    String displayName,
    String itemId,
    String modelId,
    String classTag,
    List<String> grantedSkills,
    Map<String, Double> statModifiers,
    Map<String, Object> payload
) {}

package seashyne.shynecore.equipment;

import java.util.Map;
import java.util.UUID;

public record EquipmentLoadout(UUID entityId, String mainHandWeaponId, String offHandWeaponId, Map<String, String> slots, long updatedAtMillis) {}

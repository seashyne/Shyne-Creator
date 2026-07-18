package seashyne.shynecore.skill;

import net.minecraft.server.level.ServerPlayer;
import seashyne.shynecore.equipment.EquipmentLoadout;
import seashyne.shynecore.equipment.EquipmentRuntime;
import seashyne.shynecore.equipment.WeaponDefinition;
import seashyne.shynecore.loader.ShyneModLoader;
import seashyne.shynecore.power.CombatStatManager;
import seashyne.shynecore.power.PowerStateMachine;
import seashyne.shynecore.profile.PlayerProfile;
import seashyne.shynecore.profile.PlayerProfileRuntime;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Server-authoritative gateway for every player skill cast. */
public final class SkillExecutor {
    public enum Status { CAST, SCRIPT_ONLY, UNKNOWN_SKILL, REQUIREMENT_FAILED, NOT_ENOUGH_MANA, ON_COOLDOWN }

    private final SkillRegistry skills;
    private final PlayerProfileRuntime profiles;
    private final EquipmentRuntime equipment;
    private final CombatStatManager combat;
    private final PowerStateMachine powers;
    private final ShyneModLoader modLoader;

    public SkillExecutor(SkillRegistry skills, PlayerProfileRuntime profiles, EquipmentRuntime equipment,
                         CombatStatManager combat, PowerStateMachine powers, ShyneModLoader modLoader) {
        this.skills = skills;
        this.profiles = profiles;
        this.equipment = equipment;
        this.combat = combat;
        this.powers = powers;
        this.modLoader = modLoader;
    }

    public void ensurePlayer(ServerPlayer player) {
        combat.ensure(player);
        powers.syncResources(player.getUUID());
    }

    public Status execute(ServerPlayer player, String skillId, SkillSlot slot, String rawKey, int rawSlot) {
        String resolvedId = skillId == null ? "" : skillId.trim();
        SkillDefinition definition = skills.get(resolvedId);
        if (definition == null) {
            // Preserve script-defined skills: hooks can still implement them without inventing core costs.
            fireCastHook(player, resolvedId, slot, rawKey, rawSlot, null, false);
            return resolvedId.isBlank() ? Status.UNKNOWN_SKILL : Status.SCRIPT_ONLY;
        }

        if (!meetsRequirements(player, definition)) {
            fireRejectedHook(player, definition, "requirement_failed");
            return Status.REQUIREMENT_FAILED;
        }

        String cooldownKey = "skill:" + definition.skillId();
        CombatStatManager.UseResult use = combat.tryUse(player.getUUID(), cooldownKey, definition.manaCost(), definition.cooldownTicks());
        if (use != CombatStatManager.UseResult.SUCCESS) {
            powers.syncResources(player.getUUID());
            String reason = use == CombatStatManager.UseResult.ON_COOLDOWN ? "on_cooldown" : "not_enough_mana";
            fireRejectedHook(player, definition, reason);
            return use == CombatStatManager.UseResult.ON_COOLDOWN ? Status.ON_COOLDOWN : Status.NOT_ENOUGH_MANA;
        }

        int maxStage = definition.hasTag("combo") ? intPayload(definition, "max_combo_stage", 3) : 1;
        powers.recordSkillUse(player, definition.skillId(), slot.name().toLowerCase(Locale.ROOT), maxStage,
            definition.comboWindowTicks(), definition.modelId(), definition.animation());
        fireCastHook(player, definition.skillId(), slot, rawKey, rawSlot, definition, true);
        return Status.CAST;
    }

    private boolean meetsRequirements(ServerPlayer player, SkillDefinition definition) {
        SkillRequirement requirement = definition.requirement();
        if (requirement == null || requirement.isEmpty()) return true;
        PlayerProfile profile = profiles.getOrCreate(player);
        if (profile.level() < requirement.minLevel()) return false;
        if (requirement.requiredSkills() != null && !profile.unlockedSkills().containsAll(requirement.requiredSkills())) return false;
        return requirement.requiredWeaponTag() == null || requirement.requiredWeaponTag().isBlank()
            || hasWeaponTag(player, requirement.requiredWeaponTag());
    }

    private boolean hasWeaponTag(ServerPlayer player, String requiredTag) {
        EquipmentLoadout loadout = equipment.getLoadout(player.getUUID()).orElse(null);
        if (loadout == null) return false;
        return loadout.slots().values().stream()
            .map(equipment::getWeapon)
            .filter(java.util.Objects::nonNull)
            .map(WeaponDefinition::classTag)
            .anyMatch(tag -> tag != null && tag.equalsIgnoreCase(requiredTag));
    }

    private void fireCastHook(ServerPlayer player, String skillId, SkillSlot slot, String rawKey, int rawSlot,
                              SkillDefinition definition, boolean registered) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("player", player.getName().getString());
        args.put("entity", player.getName().getString());
        args.put("uuid", player.getStringUUID());
        args.put("skill", skillId);
        args.put("skill_id", skillId);
        args.put("skill_key", rawKey == null ? "" : rawKey);
        args.put("slot", slot.name().toLowerCase(Locale.ROOT));
        args.put("raw_slot", rawSlot);
        args.put("registered", registered);
        args.put("mana", combat.getMana(player.getUUID()));
        args.put("max_mana", combat.getMaxMana(player.getUUID()));
        args.put("mana_cost", definition == null ? 0.0 : definition.manaCost());
        args.put("cooldown_ticks", definition == null ? 0 : definition.cooldownTicks());
        modLoader.fireHook("on_skill_key", args);
    }

    private void fireRejectedHook(ServerPlayer player, SkillDefinition definition, String reason) {
        modLoader.fireHook("on_skill_rejected", Map.of(
            "player", player.getName().getString(), "uuid", player.getStringUUID(),
            "skill", definition.skillId(), "reason", reason,
            "mana", combat.getMana(player.getUUID()), "mana_cost", definition.manaCost(),
            "cooldown_remaining_ms", combat.getCooldownRemaining(player.getUUID(), "skill:" + definition.skillId())
        ));
    }

    private static int intPayload(SkillDefinition definition, String key, int fallback) {
        Object value = definition.payload() == null ? null : definition.payload().get(key);
        return value instanceof Number number ? Math.max(1, number.intValue()) : fallback;
    }
}

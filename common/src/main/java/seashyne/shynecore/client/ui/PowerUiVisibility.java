package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.client.state.CustomDeckManager;
import seashyne.shynecore.client.state.CustomDeckManager.ActionSlot;
import seashyne.shynecore.power.PowerState;
import seashyne.shynecore.profile.PlayerProfile;
import seashyne.shynecore.skill.SkillDefinition;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Keeps resource UI honest: a mana value is only meaningful while the active
 * avatar has at least one selected skill that actually spends mana.
 */
public final class PowerUiVisibility {
    private PowerUiVisibility() {}

    /**
     * Returns the local mana state only when it belongs to an active mana-using
     * loadout; otherwise returns {@code null} so callers render no placeholder.
     */
    public static PowerState activeManaState(Minecraft client) {
        if (client == null || client.player == null || AvatarRuntime.active() == null) return null;

        PowerState state = ClientAnimationState.getPowerState(client.player.getUUID());
        if (state == null || state.maxMana() <= 0.0) return null;

        return hasSelectedManaSkill(client) ? state : null;
    }

    private static boolean hasSelectedManaSkill(Minecraft client) {
        Set<String> skillIds = new LinkedHashSet<>();

        CustomDeckManager.ensureLoaded();
        for (ActionSlot slot : CustomDeckManager.activeSlots()) {
            if (slot.skillId() != null && !slot.skillId().isBlank()) skillIds.add(slot.skillId());
        }

        PlayerProfile profile = ClientAnimationState.getProfile(client.player.getUUID());
        if (profile != null) {
            for (String skillId : profile.equippedSkills().values()) {
                if (skillId != null && !skillId.isBlank()) skillIds.add(skillId);
            }
        }

        for (String skillId : skillIds) {
            SkillDefinition skill = ClientAnimationState.getSkill(skillId);
            if (skill != null && skill.manaCost() > 0.0) return true;
        }
        return false;
    }
}

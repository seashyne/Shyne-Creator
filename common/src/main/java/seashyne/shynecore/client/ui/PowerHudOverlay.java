package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.power.PowerState;
import seashyne.shynecore.profile.PlayerProfile;
import seashyne.shynecore.skill.SkillDefinition;
import seashyne.shynecore.skill.SkillSlot;

import java.util.Locale;
import java.util.Map;

/**
 * In-game HUD overlay displaying the player's mana resource and equipped ability slots.
 */
public final class PowerHudOverlay {
    private static final int ACCENT_CYAN = 0xFF3DD9E8;
    private static final int BG_BOX = 0xB00D1524;
    private static final int BORDER_BOX = 0xFF253B57;

    private PowerHudOverlay() {}

    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) return;

        int screenHeight = graphics.guiHeight();
        PowerState state = ClientAnimationState.getPowerState(mc.player.getUUID());
        double mana = state != null ? state.mana() : 100.0;
        double maxMana = state != null ? state.maxMana() : 100.0;

        int x = 10;
        int y = screenHeight - 22;
        int width = 96;
        int height = 10;

        graphics.fill(x, y, x + width, y + height, BG_BOX);
        float ratio = maxMana > 0 ? (float) Math.min(1.0, mana / maxMana) : 1f;
        graphics.fill(x + 1, y + 1, x + (int) ((width - 2) * ratio), y + height - 1, ACCENT_CYAN);
        graphics.outline(x, y, width, height, BORDER_BOX);

        String manaStr = String.format(Locale.ROOT, "%.0f / %.0f", mana, maxMana);
        graphics.text(mc.font, Component.literal(manaStr), x + (width - mc.font.width(manaStr)) / 2, y + 1, 0xFFFFFFFF, true);

        PlayerProfile profile = ClientAnimationState.getProfile(mc.player.getUUID());
        Map<String, String> equipped = profile != null ? profile.equippedSkills() : Map.of();
        SkillSlot[] quickSlots = {SkillSlot.PRIMARY, SkillSlot.SECONDARY, SkillSlot.UTILITY, SkillSlot.ULTIMATE};

        int slotY = y - 18;
        int slotSize = 16;
        int slotGap = 4;
        for (int i = 0; i < quickSlots.length; i++) {
            int sx = x + i * (slotSize + slotGap);
            graphics.fill(sx, slotY, sx + slotSize, slotY + slotSize, BG_BOX);
            graphics.outline(sx, slotY, slotSize, slotSize, BORDER_BOX);

            String skillId = equipped.get(quickSlots[i].name().toLowerCase(Locale.ROOT));
            SkillDefinition def = skillId != null ? ClientAnimationState.getSkill(skillId) : null;
            if (def != null) {
                String initial = def.displayName().isEmpty() ? "?" : def.displayName().substring(0, 1);
                graphics.text(mc.font, Component.literal(initial), sx + (slotSize - mc.font.width(initial)) / 2, slotY + 4, ACCENT_CYAN, false);
            }
        }
    }
}

package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.client.state.CustomDeckManager;
import seashyne.shynecore.client.render.SkillIconTextures;
import seashyne.shynecore.client.state.CustomDeckManager.ActionSlot;
import seashyne.shynecore.power.PowerState;
import seashyne.shynecore.skill.SkillDefinition;

import java.util.List;
import java.util.Locale;

/**
 * In-game HUD overlay that renders the player's active custom deck slots
 * and mana bar. Adapts to any number of dynamic buttons.
 */
public final class PowerHudOverlay {
    private static final int ACCENT_CYAN = 0xFF3DD9E8;
    private static final int BG_BOX = 0xB00D1524;
    private static final int BORDER_BOX = 0xFF253B57;
    private static final int KEY_BADGE_BG = 0xCC1E3050;
    private static final int HUD_MARGIN = 6;
    private static final int MAX_VISIBLE_ROWS = 3;

    private PowerHudOverlay() {}

    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        PowerState manaState = PowerUiVisibility.activeManaState(mc);

        // The deck itself is unbounded. The HUD uses up to three responsive rows and
        // renders a final "+N" cell when more actions exist; every hidden action remains
        // available through its keybind and the deck editor's scrollable list.
        CustomDeckManager.ensureLoaded();
        List<ActionSlot> slots = CustomDeckManager.activeSlots();
        int slotSize = screenWidth < 360 ? 18 : 22;
        int slotGap = 4;
        int badgeH = 10;
        int rowHeight = slotSize + badgeH + 5;
        int maxPerRow = Math.max(1, (screenWidth - HUD_MARGIN * 2 + slotGap) / (slotSize + slotGap));
        int maxRows = Math.max(1, Math.min(MAX_VISIBLE_ROWS, (screenHeight - 54) / rowHeight));
        int maxCells = maxPerRow * maxRows;
        boolean hasOverflow = slots.size() > maxCells && maxCells > 1;
        int actionCount = hasOverflow ? maxCells - 1 : Math.min(slots.size(), maxCells);
        int displayCount = actionCount + (hasOverflow ? 1 : 0);
        int displayedRows = displayCount == 0 ? 0 : (displayCount + maxPerRow - 1) / maxPerRow;
        int bottomSlotY = screenHeight - HUD_MARGIN - badgeH - 1 - slotSize;
        int deckTop = displayedRows == 0 ? screenHeight : bottomSlotY - (displayedRows - 1) * rowHeight;

        // ── Mana Bar ─────────────────────────────────────────────────────
        // A default 100/100 is not a real resource. Render only for a selected
        // mana-consuming skill, and keep it clear of action badges.
        if (manaState != null) {
            int barW = 96, barH = 10;
            int barX = 10, barY = displayedRows == 0 ? screenHeight - 22 : Math.max(HUD_MARGIN, deckTop - barH - 5);
            graphics.fill(barX, barY, barX + barW, barY + barH, BG_BOX);
            double mana = manaState.mana();
            double maxMana = manaState.maxMana();
            float ratio = (float) Math.min(1.0, Math.max(0.0, mana / maxMana));
            graphics.fill(barX + 1, barY + 1, barX + (int) ((barW - 2) * ratio), barY + barH - 1, ACCENT_CYAN);
            graphics.outline(barX, barY, barW, barH, BORDER_BOX);
            String manaStr = String.format(Locale.ROOT, "%.0f / %.0f", mana, maxMana);
            graphics.text(mc.font, Component.literal(manaStr),
                barX + (barW - mc.font.width(manaStr)) / 2, barY + 1, 0xFFFFFFFF, true);
        }

        // ── Dynamic Deck Slots ───────────────────────────────────────────
        for (int row = 0; row < displayedRows; row++) {
            int firstCell = row * maxPerRow;
            int cellsInRow = Math.min(maxPerRow, displayCount - firstCell);
            int rowWidth = cellsInRow * slotSize + (cellsInRow - 1) * slotGap;
            int startX = (screenWidth - rowWidth) / 2;
            int slotY = bottomSlotY - row * rowHeight;
            for (int column = 0; column < cellsInRow; column++) {
                int cell = firstCell + column;
                int slotX = startX + column * (slotSize + slotGap);
                if (cell < actionCount) {
                    renderActionSlot(graphics, mc, slots.get(cell), slotX, slotY, slotSize, badgeH);
                } else {
                    renderOverflowSlot(graphics, mc, slots.size() - actionCount, slotX, slotY, slotSize);
                }
            }
        }
    }

    private static void renderActionSlot(GuiGraphicsExtractor graphics, Minecraft mc, ActionSlot slot,
                                         int x, int y, int size, int badgeHeight) {
        graphics.fill(x, y, x + size, y + size, BG_BOX);
        graphics.outline(x, y, size, size, BORDER_BOX);

        SkillDefinition def = !slot.skillId().isBlank() ? ClientAnimationState.getSkill(slot.skillId()) : null;
        if (def != null) {
            if (!SkillIconTextures.drawSkillCentered(graphics, def.skillId(), x + 2, y + 2, size - 4, size - 4)) {
                String initial = def.displayName().isEmpty() ? "?" : def.displayName().substring(0, 1);
                int initialWidth = mc.font.width(initial);
                graphics.text(mc.font, Component.literal(initial), x + (size - initialWidth) / 2, y + 3, ACCENT_CYAN, false);
            }
        }

        String keyLabel = slot.keyName().isBlank() ? "?" : slot.keyName();
        if (keyLabel.length() > 3) keyLabel = keyLabel.substring(0, 3);
        int labelWidth = mc.font.width(keyLabel);
        int badgeWidth = Math.min(size + 6, Math.max(labelWidth + 4, 14));
        int badgeX = x + (size - badgeWidth) / 2;
        int badgeY = y + size + 1;
        graphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, KEY_BADGE_BG);
        graphics.text(mc.font, Component.literal(keyLabel),
            badgeX + (badgeWidth - labelWidth) / 2, badgeY + 1, 0xFFCCDDEE, false);
    }

    private static void renderOverflowSlot(GuiGraphicsExtractor graphics, Minecraft mc, int hiddenCount,
                                           int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, BG_BOX);
        graphics.outline(x, y, size, size, ACCENT_CYAN);
        String label = hiddenCount >= 10_000 ? "+" + (hiddenCount / 1_000) + "k" : "+" + hiddenCount;
        String clipped = mc.font.plainSubstrByWidth(label, size - 2);
        graphics.text(mc.font, Component.literal(clipped), x + (size - mc.font.width(clipped)) / 2, y + 7, ACCENT_CYAN, false);
    }
}

package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.client.state.ClientPowerConfig;
import seashyne.shynecore.power.PowerState;
import seashyne.shynecore.profile.PlayerProfile;
import seashyne.shynecore.skill.SkillDefinition;
import seashyne.shynecore.skill.SkillSlot;

import java.util.*;

/**
 * Screen for selecting and customizing player abilities independently from avatars and equipment.
 */
public class PowerDeckScreen extends Screen {
    private static final int CARD_BG = 0xD0101826;
    private static final int CARD_HOVER = 0xEE1A2B45;
    private static final int CARD_SELECTED = 0xFF1C3A5E;
    private static final int ACCENT_CYAN = 0xFF3DD9E8;
    private static final int ACCENT_GOLD = 0xFFFFD24D;
    private static final int COLOR_BANNED = 0xFFFF5C5C;
    private static final int TEXT_MUTED = 0xFF8FA0B5;

    private final Screen parent;
    private EditBox searchBox;
    private String activeCategory = "ALL";
    private SkillSlot selectedSlot = SkillSlot.PRIMARY;
    private String selectedSkillId = "";
    private int scrollOffset = 0;
    private final List<SkillDefinition> displayedSkills = new ArrayList<>();

    public PowerDeckScreen() {
        this(null);
    }

    public PowerDeckScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.power_deck.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int leftWidth = Math.max(180, this.width / 2 - 20);
        int searchWidth = leftWidth - 8;
        this.searchBox = new EditBox(this.font, 14, 42, searchWidth, 18, Component.literal("Search"));
        this.searchBox.setResponder(s -> refreshCatalog());
        this.addRenderableWidget(this.searchBox);

        // Filter category buttons
        String[] cats = {"ALL", "PRIMARY", "UTILITY", "ULTIMATE", "PASSIVE"};
        int btnW = Math.max(34, (searchWidth - (cats.length - 1) * 3) / cats.length);
        for (int i = 0; i < cats.length; i++) {
            String cat = cats[i];
            int bx = 14 + i * (btnW + 3);
            Button b = Button.builder(Component.literal(cat), btn -> {
                this.activeCategory = cat;
                playUiClick();
                refreshCatalog();
            }).bounds(bx, 64, btnW, 16).build();
            this.addRenderableWidget(b);
        }

        // Action Buttons on Right
        int rightX = leftWidth + 24;
        int rightWidth = this.width - rightX - 16;
        int actionY = this.height - 30;

        Button equipBtn = Button.builder(Component.literal("✦ Equip to " + selectedSlot.name()), btn -> {
            if (!selectedSkillId.isBlank()) {
                ShyneClientNetworking.sendEquipSkill(selectedSkillId, selectedSlot.name().toLowerCase(Locale.ROOT));
                playUiClick();
            }
        }).bounds(rightX, actionY, Math.max(100, rightWidth / 2 - 4), 20).build();
        this.addRenderableWidget(equipBtn);

        Button unequipBtn = Button.builder(Component.literal("✕ Unequip Slot"), btn -> {
            ShyneClientNetworking.sendEquipSkill("", selectedSlot.name().toLowerCase(Locale.ROOT));
            playUiClick();
        }).bounds(rightX + Math.max(100, rightWidth / 2 - 4) + 8, actionY, Math.max(80, rightWidth / 2 - 12), 20).build();
        this.addRenderableWidget(unequipBtn);

        refreshCatalog();
    }

    private void refreshCatalog() {
        displayedSkills.clear();
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        for (SkillDefinition skill : ClientAnimationState.allSkills()) {
            if (!query.isEmpty() && !skill.displayName().toLowerCase(Locale.ROOT).contains(query)
                && !skill.skillId().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            if (!activeCategory.equals("ALL")) {
                String slot = skill.defaultSlot() != null ? skill.defaultSlot().name() : "";
                if (!slot.equalsIgnoreCase(activeCategory) && !skill.hasTag(activeCategory)) continue;
            }
            displayedSkills.add(skill);
        }
        displayedSkills.sort(Comparator.comparing(SkillDefinition::displayName));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC40B111E);

        // Header Title & Mana Display
        graphics.text(this.font, Component.literal("✦ POWER DECK & ABILITIES"), 14, 12, ACCENT_CYAN, true);
        graphics.text(this.font, Component.literal("Decoupled powers — choose and equip freely"), 14, 25, TEXT_MUTED, false);

        renderManaBar(graphics);

        int leftWidth = Math.max(180, this.width / 2 - 20);
        int rightX = leftWidth + 24;
        int contentY = 84;
        int contentBottom = this.height - 38;

        renderCatalogList(graphics, 14, contentY, leftWidth - 8, contentBottom, mouseX, mouseY);
        renderSlotPanel(graphics, rightX, 42, this.width - rightX - 16, contentBottom, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderManaBar(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PowerState state = ClientAnimationState.getPowerState(mc.player.getUUID());
        double mana = state != null ? state.mana() : 100.0;
        double maxMana = state != null ? state.maxMana() : 100.0;
        int barWidth = 140;
        int barHeight = 12;
        int barX = this.width - barWidth - 16;
        int barY = 14;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF141E30);
        float fillRatio = maxMana > 0 ? (float) Math.min(1.0, mana / maxMana) : 1f;
        graphics.fill(barX + 1, barY + 1, barX + (int) ((barWidth - 2) * fillRatio), barY + barHeight - 1, ACCENT_CYAN);
        graphics.outline(barX, barY, barWidth, barHeight, 0xFF354E75);

        String manaText = String.format(Locale.ROOT, "Mana: %.0f / %.0f", mana, maxMana);
        graphics.text(this.font, Component.literal(manaText), barX + (barWidth - this.font.width(manaText)) / 2, barY + 2, 0xFFFFFFFF, true);
    }

    private void renderCatalogList(GuiGraphicsExtractor graphics, int x, int y, int w, int bottom, int mx, int my) {
        graphics.fill(x, y, x + w, bottom, 0x880C1422);
        graphics.outline(x, y, w, bottom - y, 0xFF243650);

        int cardHeight = 36;
        int visibleCount = (bottom - y) / (cardHeight + 4);
        int start = Math.max(0, Math.min(scrollOffset, Math.max(0, displayedSkills.size() - visibleCount)));

        for (int i = 0; i < visibleCount && (start + i) < displayedSkills.size(); i++) {
            SkillDefinition skill = displayedSkills.get(start + i);
            int cy = y + 4 + i * (cardHeight + 4);
            boolean isHovered = mx >= x + 2 && mx <= x + w - 2 && my >= cy && my <= cy + cardHeight;
            boolean isSelected = skill.skillId().equals(selectedSkillId);
            boolean isBanned = ClientPowerConfig.isBanned(skill.skillId());

            int bg = isSelected ? CARD_SELECTED : (isHovered ? CARD_HOVER : CARD_BG);
            graphics.fill(x + 2, cy, x + w - 2, cy + cardHeight, bg);
            graphics.outline(x + 2, cy, w - 4, cardHeight, isBanned ? COLOR_BANNED : (isSelected ? ACCENT_CYAN : 0xFF2B3F5C));

            int nameColor = isBanned ? COLOR_BANNED : (isSelected ? ACCENT_CYAN : 0xFFF0F5FF);
            graphics.text(this.font, Component.literal(skill.displayName()), x + 8, cy + 4, nameColor, false);

            String costText = String.format(Locale.ROOT, "Mana: %.0f | CD: %.1fs",
                skill.manaCost() * ClientPowerConfig.getGlobalManaCostMultiplier(),
                (skill.cooldownTicks() * ClientPowerConfig.getGlobalCooldownMultiplier()) / 20.0f);
            graphics.text(this.font, Component.literal(costText), x + 8, cy + 18, TEXT_MUTED, false);

            if (isBanned) {
                graphics.text(this.font, Component.literal("⛔ BANNED"), x + w - 74, cy + 4, COLOR_BANNED, true);
            }
        }
    }

    private void renderSlotPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int bottom, int mx, int my) {
        graphics.text(this.font, Component.literal("EQUIPPED LOADOUT SLOTS"), x, y, ACCENT_GOLD, true);
        Minecraft mc = Minecraft.getInstance();
        PlayerProfile profile = mc.player != null ? ClientAnimationState.getProfile(mc.player.getUUID()) : null;
        Map<String, String> equipped = profile != null ? profile.equippedSkills() : Map.of();

        SkillSlot[] slots = SkillSlot.values();
        int slotHeight = 32;
        int listY = y + 14;

        for (int i = 0; i < slots.length; i++) {
            SkillSlot slot = slots[i];
            int sy = listY + i * (slotHeight + 4);
            if (sy + slotHeight > bottom) break;

            boolean isHovered = mx >= x && mx <= x + w && my >= sy && my <= sy + slotHeight;
            boolean isTarget = slot == selectedSlot;
            String equippedSkillId = equipped.getOrDefault(slot.name().toLowerCase(Locale.ROOT), "");
            SkillDefinition equippedDef = !equippedSkillId.isBlank() ? ClientAnimationState.getSkill(equippedSkillId) : null;

            int bg = isTarget ? 0xFF193652 : (isHovered ? 0xFF15263C : 0xD00F1726);
            graphics.fill(x, sy, x + w, sy + slotHeight, bg);
            graphics.outline(x, sy, w, slotHeight, isTarget ? ACCENT_CYAN : 0xFF283B54);

            graphics.text(this.font, Component.literal("[" + slot.name() + "]"), x + 8, sy + 5, isTarget ? ACCENT_CYAN : 0xFF9FB2CC, true);

            String displayName = equippedDef != null ? equippedDef.displayName() : "— Empty —";
            int nameColor = equippedDef != null ? 0xFFFFFFFF : 0xFF62748E;
            graphics.text(this.font, Component.literal(displayName), x + 8, sy + 18, nameColor, false);

            if (isTarget) {
                graphics.text(this.font, Component.literal("▶ TARGET"), x + w - 64, sy + 11, ACCENT_CYAN, true);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        int leftWidth = Math.max(180, this.width / 2 - 20);
        int contentY = 84;
        int contentBottom = this.height - 38;

        // Check catalog clicks
        if (mx >= 14 && mx <= leftWidth + 6 && my >= contentY && my <= contentBottom) {
            int cardHeight = 36;
            int visibleCount = (contentBottom - contentY) / (cardHeight + 4);
            int start = Math.max(0, Math.min(scrollOffset, Math.max(0, displayedSkills.size() - visibleCount)));
            int index = (my - contentY - 4) / (cardHeight + 4);
            if (index >= 0 && (start + index) < displayedSkills.size()) {
                SkillDefinition clicked = displayedSkills.get(start + index);
                if (!ClientPowerConfig.isBanned(clicked.skillId())) {
                    selectedSkillId = clicked.skillId();
                    playUiClick();
                    return true;
                }
            }
        }

        // Check slot clicks
        int rightX = leftWidth + 24;
        int rightWidth = this.width - rightX - 16;
        int slotHeight = 32;
        int listY = 42 + 14;
        SkillSlot[] slots = SkillSlot.values();
        for (int i = 0; i < slots.length; i++) {
            int sy = listY + i * (slotHeight + 4);
            if (mx >= rightX && mx <= rightX + rightWidth && my >= sy && my <= sy + slotHeight) {
                selectedSlot = slots[i];
                playUiClick();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            scrollOffset = Math.max(0, scrollOffset - (int) verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // ESC
            if (this.parent != null && this.minecraft != null) {
                this.minecraft.gui.setScreen(this.parent);
            } else {
                this.onClose();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    private void playUiClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}

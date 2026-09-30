package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.client.state.ClientPowerConfig;
import seashyne.shynecore.client.state.CustomDeckManager;
import seashyne.shynecore.client.render.SkillIconTextures;
import seashyne.shynecore.client.state.CustomDeckManager.ActionSlot;
import seashyne.shynecore.client.state.CustomDeckManager.DeckPreset;
import seashyne.shynecore.power.PowerState;
import seashyne.shynecore.profile.PlayerProfile;
import seashyne.shynecore.skill.SkillDefinition;

import java.util.*;

/**
 * Fully dynamic Power Deck screen — players design their own action buttons,
 * bind any key, and switch between saved presets (decks).
 *
 * <p>Left panel: a searchable list of every available action. Right panel:
 * the player's own bindings. There are no required action categories or
 * pre-filled slots; choose an action, add it, then press the key to use.</p>
 */
public class PowerDeckScreen extends Screen {
    private static final int BG_DARK = 0xC40B111E;
    private static final int CARD_BG = 0xD0101826;
    private static final int CARD_HOVER = 0xEE1A2B45;
    private static final int CARD_SELECTED = 0xFF1C3A5E;
    private static final int ACCENT_CYAN = 0xFF3DD9E8;
    private static final int COLOR_BANNED = 0xFFFF5C5C;
    private static final int COLOR_LOCKED = 0xFFFFD24D;
    private static final int TEXT_MUTED = 0xFF8FA0B5;
    private static final int BTN_ADD = 0xFF1A7A44;
    private static final int BTN_DEL = 0xFF7A2020;
    private static final int KEY_BADGE_BG = 0xFF1E3050;
    private static final int KEY_BADGE_LISTEN = 0xFFFFAA00;

    private final Screen parent;
    private EditBox searchBox;
    private Button addActionButton;
    private Button replaceActionButton;
    private Button removeActionButton;
    private String selectedSkillId = "";
    private String selectedSlotId = "";
    private String listeningSlotId = null;   // slot waiting for keybind input
    private int catalogScroll = 0;
    private int deckScroll = 0;
    private final List<SkillDefinition> displayedSkills = new ArrayList<>();

    public PowerDeckScreen() { this(null); }

    public PowerDeckScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.power_deck.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CustomDeckManager.ensureLoaded();
        this.clearWidgets();
        int leftW = Math.max(200, this.width / 2 - 30);
        int clearWidth = 44;
        int searchW = Math.max(72, leftW - 12 - clearWidth);

        this.searchBox = new EditBox(this.font, 14, 48, searchW, 18, Component.translatable("screen.shyne_core.power_deck.search"));
        this.searchBox.setHint(Component.translatable("screen.shyne_core.power_deck.search"));
        this.searchBox.setResponder(s -> refreshCatalog());
        this.addRenderableWidget(this.searchBox);
        this.addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.ui.clear"), ignored -> clearSearch())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.ui.clear.tooltip")))
            .bounds(18 + searchW, 48, clearWidth, 18).build());

        refreshCatalog();

        int rightX = leftW + 24;
        int rightW = this.width - rightX - 16;
        int actionY = this.height - 30;

        boolean compactActions = rightW < 330;
        int addW = compactActions ? Math.max(24, rightW - 56) : 120;
        int replaceW = compactActions ? 24 : 106;
        int removeW = compactActions ? 24 : 76;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), btn -> closeToParent())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.ui.back.tooltip")))
            .bounds(14, actionY, 80, 20).build());

        addActionButton = Button.builder(Component.translatable(compactActions ? "screen.shyne_core.power_deck.add.short" : "screen.shyne_core.power_deck.add"), btn -> addSelectedAction())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.power_deck.add.tooltip")))
            .bounds(rightX, actionY, addW, 20).build();
        this.addRenderableWidget(addActionButton);

        replaceActionButton = Button.builder(Component.translatable(compactActions ? "screen.shyne_core.power_deck.replace.short" : "screen.shyne_core.power_deck.replace"), btn -> replaceSelectedAction())
            .tooltip(Tooltip.create(Component.translatable("screen.shyne_core.power_deck.replace.tooltip")))
            .bounds(rightX + addW + 4, actionY, replaceW, 20).build();
        this.addRenderableWidget(replaceActionButton);

        removeActionButton = Button.builder(Component.translatable(compactActions ? "screen.shyne_core.power_deck.remove.short" : "screen.shyne_core.power_deck.remove"), btn -> {
            if (!selectedSlotId.isBlank()) {
                CustomDeckManager.removeSlot(CustomDeckManager.activeDeckIndex(), selectedSlotId);
                selectedSlotId = "";
                updateActionButtons();
                playClick();
            }
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.power_deck.remove.tooltip"))).bounds(rightX + addW + replaceW + 8, actionY, removeW, 20).build();
        this.addRenderableWidget(removeActionButton);
        updateActionButtons();
    }

    private void refreshCatalog() {
        displayedSkills.clear();
        catalogScroll = 0;
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        for (SkillDefinition skill : ClientAnimationState.allSkills()) {
            if (!query.isEmpty() && !skill.displayName().toLowerCase(Locale.ROOT).contains(query)
                && !skill.skillId().toLowerCase(Locale.ROOT).contains(query)) continue;
            displayedSkills.add(skill);
        }
        displayedSkills.sort(Comparator.comparing(SkillDefinition::displayName));
        if ((selectedSkillId.isBlank() || displayedSkills.stream().noneMatch(skill -> skill.skillId().equals(selectedSkillId)))
            && !displayedSkills.isEmpty()) {
            selectedSkillId = displayedSkills.stream().filter(this::canAssign).map(SkillDefinition::skillId).findFirst().orElse("");
        }
        updateActionButtons();
    }

    private void clearSearch() {
        if (searchBox == null || searchBox.getValue().isEmpty()) return;
        searchBox.setValue("");
        refreshCatalog();
    }

    private void updateActionButtons() {
        SkillDefinition selected = ClientAnimationState.getSkill(selectedSkillId);
        boolean skillReady = canAssign(selected);
        if (addActionButton != null) addActionButton.active = skillReady;
        if (replaceActionButton != null) replaceActionButton.active = skillReady && !selectedSlotId.isBlank();
        if (removeActionButton != null) removeActionButton.active = !selectedSlotId.isBlank();
    }

    @Override public boolean isPauseScreen() { return false; }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, BG_DARK);
        graphics.text(this.font, Component.literal("✦ ").append(Component.translatable("screen.shyne_core.power_deck.title")), 14, 10, ACCENT_CYAN, true);
        graphics.text(this.font, Component.translatable("screen.shyne_core.power_deck.subtitle"), 14, 23, TEXT_MUTED, false);

        renderManaBar(graphics);

        int leftW = Math.max(200, this.width / 2 - 30);
        int rightX = leftW + 24;
        int contentY = 76;
        int contentBottom = this.height - 38;
        graphics.text(this.font, Component.translatable("screen.shyne_core.power_deck.available"), 14, 39, 0xFFF0F5FF, true);
        Component availableCount = Component.translatable("screen.shyne_core.power_deck.count", displayedSkills.size());
        graphics.text(this.font, availableCount, leftW + 6 - this.font.width(availableCount), 39, TEXT_MUTED, false);
        graphics.text(this.font, Component.translatable("screen.shyne_core.power_deck.deck"), rightX, 39, 0xFFF0F5FF, true);

        renderCatalog(graphics, 14, contentY, leftW - 8, contentBottom, mouseX, mouseY);
        renderDeckTabs(graphics, rightX, 50, this.width - rightX - 16);
        renderDeckSlots(graphics, rightX, 72, this.width - rightX - 16, contentBottom, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderManaBar(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PowerState state = ClientAnimationState.getPowerState(mc.player.getUUID());
        double mana = state != null ? state.mana() : 100.0;
        double maxMana = state != null ? state.maxMana() : 100.0;
        int barW = 140, barH = 12;
        int barX = this.width - barW - 16, barY = 14;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF141E30);
        float ratio = maxMana > 0 ? (float) Math.min(1.0, mana / maxMana) : 1f;
        graphics.fill(barX + 1, barY + 1, barX + (int) ((barW - 2) * ratio), barY + barH - 1, ACCENT_CYAN);
        graphics.outline(barX, barY, barW, barH, 0xFF354E75);
        String txt = String.format(Locale.ROOT, "Mana: %.0f / %.0f", mana, maxMana);
        graphics.text(this.font, Component.literal(txt), barX + (barW - this.font.width(txt)) / 2, barY + 2, 0xFFFFFFFF, true);
    }

    private void renderCatalog(GuiGraphicsExtractor g, int x, int y, int w, int bottom, int mx, int my) {
        g.fill(x, y, x + w, bottom, 0x880C1422);
        g.outline(x, y, w, bottom - y, 0xFF243650);
        int cardH = 36, gap = 4;
        int visible = (bottom - y) / (cardH + gap);
        int start = Math.max(0, Math.min(catalogScroll, Math.max(0, displayedSkills.size() - visible)));
        for (int i = 0; i < visible && (start + i) < displayedSkills.size(); i++) {
            SkillDefinition skill = displayedSkills.get(start + i);
            int cy = y + 4 + i * (cardH + gap);
            boolean hover = mx >= x + 2 && mx <= x + w - 2 && my >= cy && my <= cy + cardH;
            boolean sel = skill.skillId().equals(selectedSkillId);
            boolean banned = ClientPowerConfig.isBanned(skill.skillId());
            boolean locked = !banned && !canAssign(skill);
            int bg = sel ? CARD_SELECTED : (hover ? CARD_HOVER : CARD_BG);
            g.fill(x + 2, cy, x + w - 2, cy + cardH, bg);
            g.outline(x + 2, cy, w - 4, cardH, banned ? COLOR_BANNED : (locked ? COLOR_LOCKED : (sel ? ACCENT_CYAN : 0xFF2B3F5C)));
            int iconX = x + 6, iconY = cy + 6, iconSize = 24;
            g.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF0E1929);
            g.outline(iconX, iconY, iconSize, iconSize, 0xFF2B4B6C);
            if (!SkillIconTextures.drawSkillCentered(g, skill.skillId(), iconX + 2, iconY + 2, iconSize - 4, iconSize - 4)) {
                String initial = skill.displayName().isEmpty() ? "?" : skill.displayName().substring(0, 1);
                g.text(this.font, Component.literal(initial), iconX + (iconSize - this.font.width(initial)) / 2, iconY + 8, ACCENT_CYAN, false);
            }
            int nameColor = banned ? COLOR_BANNED : (locked ? COLOR_LOCKED : (sel ? ACCENT_CYAN : 0xFFF0F5FF));
            int textX = iconX + iconSize + 6;
            String displayName = this.font.plainSubstrByWidth(skill.displayName(), Math.max(8, w - (textX - x) - 74));
            g.text(this.font, Component.literal(displayName), textX, cy + 4, nameColor, false);
            String detail = skill.description() == null || skill.description().isBlank() ? "Ready to add" : skill.description();
            g.text(this.font, Component.literal(this.font.plainSubstrByWidth(detail, Math.max(8, w - (textX - x) - 8))), textX, cy + 18, TEXT_MUTED, false);
            if (banned) g.text(this.font, Component.literal("⛔ BANNED"), x + w - 74, cy + 4, COLOR_BANNED, true);
            else if (locked) g.text(this.font, Component.literal("LOCKED"), x + w - 47, cy + 4, COLOR_LOCKED, true);
        }
    }

    /** Mirrors the server's selection gate before a skill can be placed in a local action deck. */
    private boolean canAssign(SkillDefinition skill) {
        if (skill == null || ClientPowerConfig.isBanned(skill.skillId())) return false;
        if (ClientPowerConfig.isFreeSelection()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        PlayerProfile profile = ClientAnimationState.getProfile(mc.player.getUUID());
        return profile != null && profile.unlockedSkills().contains(skill.skillId());
    }

    private void renderDeckTabs(GuiGraphicsExtractor g, int x, int y, int w) {
        List<DeckPreset> decks = CustomDeckManager.allDecks();
        int tabW = deckTabWidth(w, decks.size());
        for (int i = 0; i < decks.size(); i++) {
            int tx = x + i * (tabW + 2);
            boolean active = i == CustomDeckManager.activeDeckIndex();
            g.fill(tx, y, tx + tabW, y + 16, active ? CARD_SELECTED : CARD_BG);
            g.outline(tx, y, tabW, 16, active ? ACCENT_CYAN : 0xFF2B3F5C);
            String name = decks.get(i).name();
            if (name.length() > 8) name = name.substring(0, 7) + "…";
            String clippedName = this.font.plainSubstrByWidth(name, Math.max(4, tabW - 8));
            g.text(this.font, Component.literal(clippedName), tx + 4, y + 4, active ? ACCENT_CYAN : TEXT_MUTED, false);
        }
        // "+ New" tab
        int newX = x + decks.size() * (tabW + 2);
        g.fill(newX, y, newX + 24, y + 16, BTN_ADD);
        g.text(this.font, Component.literal("+"), newX + 9, y + 4, 0xFFFFFFFF, true);
        if (decks.size() > 1) {
            int removeX = newX + 28;
            g.fill(removeX, y, removeX + 24, y + 16, BTN_DEL);
            g.text(this.font, Component.literal("−"), removeX + 8, y + 4, 0xFFFFFFFF, true);
        }
    }

    private static int deckTabWidth(int availableWidth, int deckCount) {
        int controlsWidth = 52; // add and remove buttons, including their gap
        // The trailing two-pixel gap before the add control is part of the tab strip too.
        int gaps = deckCount * 2;
        int availableTabs = Math.max(0, availableWidth - controlsWidth - gaps);
        return Math.min(80, Math.max(28, availableTabs / Math.max(1, deckCount)));
    }

    private void renderDeckSlots(GuiGraphicsExtractor g, int x, int y, int w, int bottom, int mx, int my) {
        DeckPreset deck = CustomDeckManager.activeDeck();
        List<ActionSlot> slots = deck.slots();
        int slotH = 38, gap = 4;
        int visible = (bottom - y) / (slotH + gap);
        int start = Math.max(0, Math.min(deckScroll, Math.max(0, slots.size() - visible)));
        for (int i = 0; i < visible && (start + i) < slots.size(); i++) {
            ActionSlot slot = slots.get(start + i);
            int sy = y + 4 + i * (slotH + gap);
            boolean hover = mx >= x && mx <= x + w && my >= sy && my <= sy + slotH;
            boolean sel = slot.id().equals(selectedSlotId);
            boolean listening = slot.id().equals(listeningSlotId);
            // Background
            int bg = sel ? CARD_SELECTED : (hover ? CARD_HOVER : CARD_BG);
            g.fill(x, sy, x + w, sy + slotH, bg);
            g.outline(x, sy, w, slotH, sel ? ACCENT_CYAN : 0xFF283B54);
            // Key badge (clickable area for rebind)
            int badgeW = 32, badgeH = 20;
            int badgeX = x + 6, badgeY = sy + 9;
            g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, listening ? KEY_BADGE_LISTEN : KEY_BADGE_BG);
            g.outline(badgeX, badgeY, badgeW, badgeH, listening ? 0xFFFFFFFF : ACCENT_CYAN);
            String keyLabel = listening ? "…" : (slot.keyName().isBlank() ? "?" : slot.keyName());
            int labelW = this.font.width(keyLabel);
            g.text(this.font, Component.literal(keyLabel), badgeX + (badgeW - labelW) / 2, badgeY + 6, listening ? 0xFF000000 : 0xFFFFFFFF, false);
            // Slot label and assigned skill; a synced package PNG is shown on the right when available.
            SkillDefinition def = !slot.skillId().isBlank() ? ClientAnimationState.getSkill(slot.skillId()) : null;
            int iconSize = 20;
            int iconX = x + w - iconSize - 6;
            int iconY = sy + (slotH - iconSize) / 2;
            int textWidth = Math.max(8, iconX - (x + 44) - 4);
            String slotLabel = this.font.plainSubstrByWidth(slot.label(), textWidth);
            g.text(this.font, Component.literal(slotLabel), x + 44, sy + 5, sel ? ACCENT_CYAN : 0xFFCCDDEE, true);
            String skillName = def != null ? def.displayName() : (slot.skillId().isBlank() ? "— choose an action —" : slot.skillId());
            String clippedSkillName = this.font.plainSubstrByWidth(skillName, textWidth);
            g.text(this.font, Component.literal(clippedSkillName), x + 44, sy + 20, def != null ? 0xFFAABBDD : 0xFF62748E, false);
            if (def != null) {
                g.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF0E1929);
                g.outline(iconX, iconY, iconSize, iconSize, sel ? ACCENT_CYAN : 0xFF2B4B6C);
                if (!SkillIconTextures.drawSkillCentered(g, def.skillId(), iconX + 2, iconY + 2, iconSize - 4, iconSize - 4)) {
                    String initial = def.displayName().isEmpty() ? "?" : def.displayName().substring(0, 1);
                    g.text(this.font, Component.literal(initial), iconX + (iconSize - this.font.width(initial)) / 2, iconY + 6, ACCENT_CYAN, false);
                }
            }
        }
        if (slots.isEmpty()) {
            g.text(this.font, Component.literal("Your action list is empty."), x + 8, y + 20, 0xFFF0F5FF, false);
            g.text(this.font, Component.literal("Choose an action on the left, then click [+ Add action]."), x + 8, y + 33, TEXT_MUTED, false);
        } else if (listeningSlotId != null) {
            g.text(this.font, Component.literal("Press a key now  •  Backspace clears"), x + 8, bottom - 14, 0xFFFFD24D, false);
        }
    }

    /** Adds the selected action and immediately starts the key-capture step. */
    private void addSelectedAction() {
        SkillDefinition selected = ClientAnimationState.getSkill(selectedSkillId);
        if (selected == null || !canAssign(selected)) return;
        ActionSlot slot = CustomDeckManager.addSlot(CustomDeckManager.activeDeckIndex());
        if (slot == null) return;
        CustomDeckManager.assignSkill(CustomDeckManager.activeDeckIndex(), slot.id(), selected.skillId());
        CustomDeckManager.renameSlot(CustomDeckManager.activeDeckIndex(), slot.id(), selected.displayName());
        selectedSlotId = slot.id();
        listeningSlotId = slot.id();
        updateActionButtons();
        playClick();
    }

    /** Replaces the action in a selected row without changing its existing key. */
    private void replaceSelectedAction() {
        SkillDefinition selected = ClientAnimationState.getSkill(selectedSkillId);
        if (selectedSlotId.isBlank() || selected == null || !canAssign(selected)) return;
        CustomDeckManager.assignSkill(CustomDeckManager.activeDeckIndex(), selectedSlotId, selected.skillId());
        CustomDeckManager.renameSlot(CustomDeckManager.activeDeckIndex(), selectedSlotId, selected.displayName());
        updateActionButtons();
        playClick();
    }

    // ── Input Handling ───────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();
        int leftW = Math.max(200, this.width / 2 - 30);
        int rightX = leftW + 24, rightW = this.width - rightX - 16;
        int contentY = 76, contentBottom = this.height - 38;

        // Catalog click
        if (mx >= 14 && mx <= leftW + 6 && my >= contentY && my <= contentBottom) {
            int cardH = 36, gap = 4;
            int visible = (contentBottom - contentY) / (cardH + gap);
            int start = Math.max(0, Math.min(catalogScroll, Math.max(0, displayedSkills.size() - visible)));
            int localY = my - contentY - 4;
            int idx = localY / (cardH + gap);
            if (localY >= 0 && localY % (cardH + gap) < cardH && idx >= 0 && (start + idx) < displayedSkills.size()) {
                SkillDefinition clicked = displayedSkills.get(start + idx);
                if (canAssign(clicked)) {
                    selectedSkillId = clicked.skillId();
                    updateActionButtons();
                    if (doubleClick) addSelectedAction();
                    playClick();
                    return true;
                }
            }
        }

        // Deck tab clicks
        if (my >= 50 && my <= 66) {
            List<DeckPreset> decks = CustomDeckManager.allDecks();
            int tabW = deckTabWidth(rightW, decks.size());
            for (int i = 0; i < decks.size(); i++) {
                int tx = rightX + i * (tabW + 2);
                if (mx >= tx && mx <= tx + tabW) {
                    CustomDeckManager.setActiveDeck(i);
                    deckScroll = 0;
                    selectedSlotId = "";
                    listeningSlotId = null;
                    updateActionButtons();
                    playClick();
                    return true;
                }
            }
            // "+ New" tab
            int newX = rightX + decks.size() * (tabW + 2);
            if (mx >= newX && mx <= newX + 24) {
                DeckPreset added = CustomDeckManager.addDeck(null);
                if (added != null) {
                    CustomDeckManager.setActiveDeck(CustomDeckManager.allDecks().size() - 1);
                    deckScroll = 0;
                    selectedSlotId = "";
                    listeningSlotId = null;
                    updateActionButtons();
                    playClick();
                }
                return true;
            }
            int removeX = newX + 28;
            if (decks.size() > 1 && mx >= removeX && mx <= removeX + 24) {
                if (CustomDeckManager.removeDeck(CustomDeckManager.activeDeckIndex())) {
                    deckScroll = 0;
                    selectedSlotId = "";
                    listeningSlotId = null;
                    updateActionButtons();
                    playClick();
                }
                return true;
            }
        }

        // Deck slot clicks
        if (mx >= rightX && mx <= rightX + rightW && my >= 72 && my <= contentBottom) {
            List<ActionSlot> slots = CustomDeckManager.activeDeck().slots();
            int slotH = 38, gap = 4;
            int visible = (contentBottom - 72) / (slotH + gap);
            int start = Math.max(0, Math.min(deckScroll, Math.max(0, slots.size() - visible)));
            int localY = my - 72 - 4;
            int idx = localY / (slotH + gap);
            if (localY >= 0 && localY % (slotH + gap) < slotH && idx >= 0 && (start + idx) < slots.size()) {
                ActionSlot slot = slots.get(start + idx);
                int badgeX = rightX + 6, badgeY = 72 + 4 + idx * (slotH + gap) + 9;
                // Key badge click → start listening
                if (mx >= badgeX && mx <= badgeX + 32 && my >= badgeY && my <= badgeY + 20) {
                    listeningSlotId = slot.id().equals(listeningSlotId) ? null : slot.id();
                    playClick();
                    return true;
                }
                // Rest of slot → select
                selectedSlotId = slot.id();
                listeningSlotId = null;
                updateActionButtons();
                playClick();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        int leftW = Math.max(200, this.width / 2 - 30);
        if (vAmount != 0) {
            if (mouseX < leftW + 6) {
                catalogScroll = Math.max(0, catalogScroll - (int) vAmount);
            } else {
                deckScroll = Math.max(0, deckScroll - (int) vAmount);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        // ESC always closes
        if (key == InputConstants.KEY_ESCAPE) {
            if (listeningSlotId != null) { listeningSlotId = null; return true; }
            closeToParent();
            return true;
        }
        // If listening for keybind, capture the key
        if (listeningSlotId != null) {
            if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                CustomDeckManager.bindKey(CustomDeckManager.activeDeckIndex(), listeningSlotId,
                    InputConstants.UNKNOWN.getValue(), "");
                listeningSlotId = null;
                playClick();
                return true;
            }
            InputConstants.Key inputKey = InputConstants.Type.KEYBOARD.getOrCreate(key);
            String keyName = inputKey.getDisplayName().getString();
            CustomDeckManager.bindKey(CustomDeckManager.activeDeckIndex(), listeningSlotId, key, keyName);
            listeningSlotId = null;
            playClick();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private void closeToParent() {
        if (this.parent != null && this.minecraft != null) this.minecraft.gui.setScreen(this.parent);
        else super.onClose();
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}

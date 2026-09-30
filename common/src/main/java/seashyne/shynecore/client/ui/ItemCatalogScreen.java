package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.render.SkillIconTextures;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.item.ShyneItemDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Native, server-synchronised browser for creator-defined Shyne items. */
public final class ItemCatalogScreen extends Screen {
    private static final int BG_DARK = 0xD20A111D;
    private static final int PANEL_BG = 0xD0142032;
    private static final int CARD_BG = 0xE0101927;
    private static final int CARD_HOVER = 0xEE1B2D48;
    private static final int CARD_SELECTED = 0xFF1B4065;
    private static final int ACCENT = 0xFF42D8E9;
    private static final int MUTED = 0xFF93A5BB;

    private final Screen parent;
    private final List<ShyneItemDefinition> displayedItems = new ArrayList<>();
    private EditBox searchBox;
    private String selectedItemId = "";
    private int catalogScroll;

    public ItemCatalogScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.items.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int leftW = Math.max(190, width / 2 - 34);
        int searchW = leftW - 8;
        searchBox = new EditBox(font, 14, 42, searchW, 18, Component.translatable("screen.shyne_core.items.search"));
        searchBox.setResponder(ignored -> refreshItems());
        addRenderableWidget(searchBox);

        refreshItems();
    }

    private void refreshItems() {
        displayedItems.clear();
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        for (ShyneItemDefinition item : ClientAnimationState.allItems()) {
            if (!query.isEmpty() && !item.displayName().toLowerCase(Locale.ROOT).contains(query)
                && !item.itemId().toLowerCase(Locale.ROOT).contains(query)
                && item.description().stream().noneMatch(line -> line.toLowerCase(Locale.ROOT).contains(query))) continue;
            displayedItems.add(item);
        }
        displayedItems.sort(Comparator.comparing(ShyneItemDefinition::displayName, String.CASE_INSENSITIVE_ORDER));
        if (!selectedItemId.isBlank() && displayedItems.stream().noneMatch(item -> item.itemId().equals(selectedItemId))) {
            selectedItemId = "";
        }
        if (selectedItemId.isBlank() && !displayedItems.isEmpty()) selectedItemId = displayedItems.getFirst().itemId();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BG_DARK);
        graphics.text(font, Component.literal("✦ ").append(Component.translatable("screen.shyne_core.items.title")), 14, 10, ACCENT, true);
        graphics.text(font, Component.translatable("screen.shyne_core.items.subtitle"), 14, 23, MUTED, false);

        int leftW = Math.max(190, width / 2 - 34);
        int detailX = leftW + 26;
        int contentY = 68;
        int contentBottom = height - 14;
        renderCatalog(graphics, 14, contentY, leftW - 8, contentBottom, mouseX, mouseY);
        renderDetail(graphics, detailX, 42, Math.max(120, width - detailX - 14), contentBottom);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCatalog(GuiGraphicsExtractor graphics, int x, int y, int width, int bottom, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, bottom, PANEL_BG);
        graphics.outline(x, y, width, bottom - y, 0xFF293E5A);
        int cardHeight = 40;
        int gap = 4;
        int visible = Math.max(1, (bottom - y - 4) / (cardHeight + gap));
        int start = Math.max(0, Math.min(catalogScroll, Math.max(0, displayedItems.size() - visible)));
        for (int index = 0; index < visible && start + index < displayedItems.size(); index++) {
            ShyneItemDefinition item = displayedItems.get(start + index);
            int cardY = y + 4 + index * (cardHeight + gap);
            boolean selected = item.itemId().equals(selectedItemId);
            boolean hovered = mouseX >= x + 2 && mouseX <= x + width - 2 && mouseY >= cardY && mouseY <= cardY + cardHeight;
            graphics.fill(x + 2, cardY, x + width - 2, cardY + cardHeight, selected ? CARD_SELECTED : (hovered ? CARD_HOVER : CARD_BG));
            graphics.outline(x + 2, cardY, width - 4, cardHeight, selected ? ACCENT : 0xFF2A405D);

            int iconX = x + 7;
            int iconY = cardY + 7;
            graphics.fill(iconX, iconY, iconX + 26, iconY + 26, 0xFF09131F);
            graphics.outline(iconX, iconY, 26, 26, selected ? ACCENT : 0xFF2A405D);
            if (!SkillIconTextures.drawItemCentered(graphics, item.itemId(), iconX + 2, iconY + 2, 22, 22)) {
                String initial = initial(item.displayName());
                graphics.text(font, Component.literal(initial), iconX + (26 - font.width(initial)) / 2, iconY + 8, ACCENT, false);
            }

            int textX = iconX + 33;
            int textWidth = Math.max(10, width - (textX - x) - 12);
            graphics.text(font, Component.literal(font.plainSubstrByWidth(item.displayName(), textWidth)), textX, cardY + 6,
                selected ? ACCENT : 0xFFF0F5FF, false);
            String meta = item.maxStack() > 1 ? "Stack x" + item.maxStack() : "Creator item";
            if (!item.useSkill().isBlank()) meta += "  •  Action";
            graphics.text(font, Component.literal(font.plainSubstrByWidth(meta, textWidth)), textX, cardY + 21, MUTED, false);
        }
        if (displayedItems.isEmpty()) {
            graphics.text(font, Component.translatable("screen.shyne_core.items.empty"), x + 10, y + 18, MUTED, false);
        }
    }

    private void renderDetail(GuiGraphicsExtractor graphics, int x, int y, int width, int bottom) {
        graphics.fill(x, y, x + width, bottom, PANEL_BG);
        graphics.outline(x, y, width, bottom - y, 0xFF2D4564);
        ShyneItemDefinition item = selectedItemId.isBlank() ? null : ClientAnimationState.getItem(selectedItemId);
        if (item == null) {
            graphics.text(font, Component.translatable("screen.shyne_core.items.select"), x + 14, y + 18, MUTED, false);
            return;
        }

        int iconSize = 54;
        int iconX = x + 14;
        int iconY = y + 14;
        graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF09131F);
        graphics.outline(iconX, iconY, iconSize, iconSize, ACCENT);
        if (!SkillIconTextures.drawItemCentered(graphics, item.itemId(), iconX + 5, iconY + 5, iconSize - 10, iconSize - 10)) {
            String initial = initial(item.displayName());
            graphics.text(font, Component.literal(initial), iconX + (iconSize - font.width(initial)) / 2, iconY + 20, ACCENT, true);
        }

        int nameX = iconX + iconSize + 10;
        int nameWidth = Math.max(12, x + width - nameX - 12);
        graphics.text(font, Component.literal(font.plainSubstrByWidth(item.displayName(), nameWidth)), nameX, iconY + 4, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("screen.shyne_core.items.creator_item"), nameX, iconY + 21, ACCENT, true);
        graphics.text(font, Component.literal(item.itemId()), nameX, iconY + 35, MUTED, false);

        int cursorY = iconY + iconSize + 14;
        for (String line : item.description()) {
            for (String wrapped : wrap(line, width - 28)) {
                if (cursorY > bottom - 66) break;
                graphics.text(font, Component.literal(wrapped), x + 14, cursorY, 0xFFD4DFEB, false);
                cursorY += 11;
            }
        }
        cursorY += 7;
        List<String> facts = new ArrayList<>();
        facts.add("Stack: " + item.maxStack() + (item.glint() ? "  •  Glint" : ""));
        if (!item.useSkill().isBlank()) facts.add("Use skill: " + item.useSkill());
        if (!item.weaponId().isBlank()) facts.add("Equips weapon: " + item.weaponId());
        if (item.cooldownTicks() > 0) facts.add(String.format(Locale.ROOT, "Cooldown: %.1fs", item.cooldownTicks() / 20.0f));
        if (item.consumeOnUse()) facts.add("Consumed on use");
        for (String fact : facts) {
            if (cursorY > bottom - 18) break;
            graphics.text(font, Component.literal(fact), x + 14, cursorY, MUTED, false);
            cursorY += 12;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int leftW = Math.max(190, width / 2 - 34);
        int contentY = 68;
        int contentBottom = height - 14;
        if (mouseX >= 14 && mouseX <= leftW + 6 && mouseY >= contentY && mouseY <= contentBottom) {
            int cardHeight = 40;
            int gap = 4;
            int visible = Math.max(1, (contentBottom - contentY - 4) / (cardHeight + gap));
            int start = Math.max(0, Math.min(catalogScroll, Math.max(0, displayedItems.size() - visible)));
            int localY = mouseY - contentY - 4;
            int index = localY / (cardHeight + gap);
            if (localY >= 0 && localY % (cardHeight + gap) < cardHeight && index >= 0 && start + index < displayedItems.size()) {
                selectedItemId = displayedItems.get(start + index).itemId();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && mouseX < Math.max(190, width / 2 - 34) + 6) {
            catalogScroll = Math.max(0, catalogScroll - (int) verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (parent != null && minecraft != null) minecraft.gui.setScreen(parent);
            else onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private List<String> wrap(String value, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String remaining = value == null ? "" : value.trim();
        while (!remaining.isEmpty()) {
            String line = font.plainSubstrByWidth(remaining, Math.max(12, maxWidth));
            if (line.isEmpty()) break;
            lines.add(line);
            remaining = remaining.substring(line.length()).stripLeading();
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static String initial(String value) {
        return value == null || value.isBlank() ? "?" : value.substring(0, 1).toUpperCase(Locale.ROOT);
    }

}

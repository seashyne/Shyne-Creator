package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
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

        // Top Studio Navigation Bar
        ShyneNavBar.addTabs(
            this.minecraft,
            this,
            this.parent,
            ShyneNavBar.Section.ITEMS,
            this.width - 14,
            10,
            this.width - 240,
            this::addRenderableWidget
        );

        int leftW = Math.max(190, width / 2 - 34);
        int clearWidth = 44;
        int searchW = Math.max(72, leftW - 12 - clearWidth);
        searchBox = new EditBox(font, 14, 50, searchW, 18, Component.translatable("screen.shyne_core.items.search"));
        searchBox.setHint(Component.translatable("screen.shyne_core.items.search"));
        searchBox.setResponder(ignored -> refreshItems());
        addRenderableWidget(searchBox);

        addRenderableWidget(ShyneButton.ghost(Component.translatable("screen.shyne_core.ui.clear"), ignored -> clearSearch())
            .tooltipText(Component.translatable("screen.shyne_core.ui.clear.tooltip"))
            .bounds(18 + searchW, 50, clearWidth, 18));

        addRenderableWidget(ShyneButton.secondary(Component.translatable("gui.back"), ignored -> closeToParent())
            .tooltipText(Component.translatable("screen.shyne_core.ui.back.tooltip"))
            .bounds(14, height - 30, 80, 20));

        refreshItems();
    }

    private void refreshItems() {
        displayedItems.clear();
        catalogScroll = 0;
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

    private void clearSearch() {
        if (searchBox == null || searchBox.getValue().isEmpty()) return;
        searchBox.setValue("");
        refreshItems();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, ShyneTheme.BACKDROP);
        graphics.text(font, Component.literal("✦ ").append(Component.translatable("screen.shyne_core.items.title")), 14, 10, ShyneTheme.ACCENT, true);
        graphics.text(font, Component.translatable("screen.shyne_core.items.subtitle"), 14, 23, ShyneTheme.TEXT_MUTED, false);

        int leftW = Math.max(190, width / 2 - 34);
        int detailX = leftW + 26;
        int contentY = 76;
        int contentBottom = height - 38;

        // Sleek panel backgrounds
        ShyneTheme.drawPanel(graphics, 10, 36, leftW + 6, height - 72);
        ShyneTheme.drawPanel(graphics, detailX - 6, 36, width - detailX - 6, height - 72);

        graphics.text(font, Component.translatable("screen.shyne_core.items.collection"), 14, 40, ShyneTheme.TEXT_TITLE, true);
        Component count = Component.translatable("screen.shyne_core.items.count", displayedItems.size());
        graphics.text(font, count, leftW + 6 - font.width(count), 40, ShyneTheme.TEXT_MUTED, false);
        graphics.text(font, Component.translatable("screen.shyne_core.items.details"), detailX, 40, ShyneTheme.TEXT_TITLE, true);
        renderCatalog(graphics, 14, contentY, leftW - 8, contentBottom, mouseX, mouseY);
        renderDetail(graphics, detailX, 50, Math.max(120, width - detailX - 14), contentBottom);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCatalog(GuiGraphicsExtractor graphics, int x, int y, int width, int bottom, int mouseX, int mouseY) {
        int cardHeight = 40;
        int gap = 4;
        int visible = Math.max(1, (bottom - y - 4) / (cardHeight + gap));
        int start = Math.max(0, Math.min(catalogScroll, Math.max(0, displayedItems.size() - visible)));
        for (int index = 0; index < visible && start + index < displayedItems.size(); index++) {
            ShyneItemDefinition item = displayedItems.get(start + index);
            int cardY = y + 4 + index * (cardHeight + gap);
            boolean selected = item.itemId().equals(selectedItemId);
            boolean hovered = mouseX >= x + 2 && mouseX <= x + width - 2 && mouseY >= cardY && mouseY <= cardY + cardHeight;
            int bg = selected ? ShyneTheme.CARD_SELECTED : (hovered ? ShyneTheme.CARD_HOVER : ShyneTheme.CARD_BG);
            int border = selected ? ShyneTheme.ACCENT : ShyneTheme.BORDER_SUBTLE;
            ShyneTheme.drawCard(graphics, x + 2, cardY, width - 4, cardHeight, bg, border);

            int iconX = x + 7;
            int iconY = cardY + 7;
            graphics.fill(iconX, iconY, iconX + 26, iconY + 26, 0xFF09131F);
            graphics.outline(iconX, iconY, 26, 26, selected ? ShyneTheme.ACCENT : ShyneTheme.BORDER_SUBTLE);
            if (!SkillIconTextures.drawItemCentered(graphics, item.itemId(), iconX + 2, iconY + 2, 22, 22)) {
                String initial = initial(item.displayName());
                graphics.text(font, Component.literal(initial), iconX + (26 - font.width(initial)) / 2, iconY + 8, ShyneTheme.ACCENT, false);
            }

            int textX = iconX + 33;
            int textWidth = Math.max(10, width - (textX - x) - 12);
            graphics.text(font, Component.literal(font.plainSubstrByWidth(item.displayName(), textWidth)), textX, cardY + 6,
                selected ? ShyneTheme.ACCENT : 0xFFF0F5FF, false);
            String meta = item.maxStack() > 1 ? "Stack x" + item.maxStack() : "Creator item";
            if (!item.useSkill().isBlank()) meta += "  •  Action";
            graphics.text(font, Component.literal(font.plainSubstrByWidth(meta, textWidth)), textX, cardY + 20,
                ShyneTheme.TEXT_MUTED, false);
        }
        if (displayedItems.isEmpty()) {
            graphics.text(font, Component.translatable("screen.shyne_core.items.empty"), x + 10, y + 18, ShyneTheme.TEXT_MUTED, false);
        }
        if (displayedItems.size() > visible) {
            ShyneTheme.drawScrollbar(graphics, x + width - 4, y + 4, bottom - 4, start, Math.max(1, displayedItems.size() - visible), visible, displayedItems.size());
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
        int contentY = 76;
        int contentBottom = height - 38;
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
            closeToParent();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private void closeToParent() {
        if (parent != null && minecraft != null) minecraft.gui.setScreen(parent);
        else super.onClose();
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

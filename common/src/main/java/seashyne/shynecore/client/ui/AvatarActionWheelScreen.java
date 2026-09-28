package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import seashyne.shynecore.client.avatar.AvatarAction;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.config.ShyneClientSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unified 8-slot action wheel for avatar actions and Shyne controls.
 *
 * <p>Provides smooth circular mouse navigation, slot hover highlights,
 * item/glyph icons, toggle badges, and mouse-scroll page navigation.</p>
 */
public class AvatarActionWheelScreen extends Screen {
    private static final int SLOTS_PER_PAGE = 8;
    private static final int SURFACE_BG = 0xC00B1222;
    private static final int SURFACE_CARD = 0xD8121E32;
    private static final int SURFACE_HOVER = 0xEE1A2B45;
    private static final int ACCENT_CYAN = 0xFF3DD9E8;
    private static final int ACCENT_GLOW = 0xFF5CEBFA;
    private static final int TOGGLE_ON = 0xFF4AE39C;
    private static final int TOGGLE_OFF = 0xFF6D7D93;
    private static final int TEXT_MUTED = 0xFF8FA0B5;

    private final Screen parent;
    private int currentPage;
    private int totalPages;
    private String avatarName = "";
    private final List<Page> pages = new ArrayList<>();
    private int hoveredSlot = -1;

    public AvatarActionWheelScreen() {
        this(null);
    }

    public AvatarActionWheelScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.action_wheel.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pages.clear();
        hoveredSlot = -1;

        AvatarState state = AvatarRuntime.active();
        if (state == null) {
            avatarName = Component.translatable("screen.shyne_core.palette.no_avatar").getString();
        } else {
            avatarName = AvatarRuntime.catalog().stream()
                .filter(entry -> entry.id().equalsIgnoreCase(state.avatarId()))
                .map(entry -> entry.name().isBlank() ? state.avatarId() : entry.name())
                .findFirst()
                .orElse(state.avatarId());

            Map<String, List<AvatarAction>> actionsByPage = state.actionsByPage();
            for (Map.Entry<String, List<AvatarAction>> entry : actionsByPage.entrySet()) {
                List<AvatarAction> list = entry.getValue();
                for (int start = 0; start < list.size(); start += SLOTS_PER_PAGE) {
                    int end = Math.min(start + SLOTS_PER_PAGE, list.size());
                    pages.add(new Page(entry.getKey(), List.copyOf(list.subList(start, end))));
                }
            }
        }

        pages.add(new Page(Component.translatable("screen.shyne_core.action_wheel.controls").getString(), systemActions(state != null)));

        totalPages = Math.max(1, pages.size());
        if (currentPage >= totalPages) currentPage = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int wheelRadius = wheelRadius();

        List<AvatarAction> currentActions = getCurrentActions();
        updateHoveredSlot(cx, cy, wheelRadius, mouseX, mouseY, currentActions.size());

        drawCenterHub(graphics, cx, cy, mouseX, mouseY);
        drawRadialSlots(graphics, cx, cy, wheelRadius, currentActions);
        drawPageInformation(graphics, cx, cy, wheelRadius, currentActions);
        if (totalPages > 1) {
            graphics.fill(this.width - 27, 4, this.width - 4, 26, SURFACE_CARD);
            graphics.outline(this.width - 27, 4, 23, 22, ACCENT_CYAN);
            graphics.text(this.font, Component.literal("⚙"), this.width - 21, 11, ACCENT_CYAN, true);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private int wheelRadius() {
        return Math.min(95, Math.max(48, (this.height - 84) / 2));
    }

    private void updateHoveredSlot(int cx, int cy, int radius, int mouseX, int mouseY, int actionCount) {
        hoveredSlot = -1;
        for (int slot = 0; slot < actionCount; slot++) {
            double angle = -Math.PI / 2.0 + slot * (2.0 * Math.PI / SLOTS_PER_PAGE);
            int x = cx + (int) (Math.cos(angle) * radius);
            int y = cy + (int) (Math.sin(angle) * radius);
            if (mouseX >= x - 27 && mouseX < x + 27 && mouseY >= y - 17 && mouseY < y + 17) {
                hoveredSlot = slot;
                return;
            }
        }
    }

    private void drawCenterHub(GuiGraphicsExtractor graphics, int cx, int cy, int mouseX, int mouseY) {
        int hubRadius = 36;
        graphics.fill(cx - hubRadius, cy - hubRadius, cx + hubRadius, cy + hubRadius, SURFACE_BG);
        graphics.outline(cx - hubRadius, cy - hubRadius, hubRadius * 2, hubRadius * 2,
            hoveredSlot >= 0 ? 0x773DD9E8 : 0x553DD9E8);

        boolean showAvatar = ShyneClientSettings.actionWheelCenterAvatar
            && this.minecraft != null && this.minecraft.player != null;
        if (showAvatar) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics, cx - 31, cy - 33, cx + 31, cy + 33,
                28, 0.0625F, mouseX, mouseY, this.minecraft.player
            );
        } else {
            Component name = Component.literal(this.font.plainSubstrByWidth(avatarName, 62));
            graphics.text(this.font, name, cx - this.font.width(name) / 2, cy - 4, ACCENT_CYAN, true);
        }
    }

    private void drawPageInformation(GuiGraphicsExtractor graphics, int cx, int cy, int radius,
                                     List<AvatarAction> actions) {
        AvatarAction selected = hoveredSlot >= 0 && hoveredSlot < actions.size() ? actions.get(hoveredSlot) : null;
        String heading = selected == null ? avatarName : selected.title();
        String detail = selected == null ? pages.get(currentPage).name() : selected.description();
        if (selected != null && selected.isToggle()) detail = (selected.isToggled() ? "● ON  " : "○ OFF  ") + detail;
        if (selected != null && selected.localOnly()) {
            detail += "  ·  " + Component.translatable("screen.shyne_core.palette.local_only").getString();
        }
        int top = Math.max(3, cy - radius - 39);
        Component title = Component.literal(this.font.plainSubstrByWidth(heading, this.width - 12));
        Component subtitle = Component.literal(this.font.plainSubstrByWidth(detail, this.width - 12));
        graphics.text(this.font, title, cx - this.font.width(title) / 2, top, ACCENT_CYAN, true);
        graphics.text(this.font, subtitle, cx - this.font.width(subtitle) / 2, top + 11, TEXT_MUTED, false);

        String footer = totalPages > 1
            ? "‹  " + (currentPage + 1) + "/" + totalPages + "  " + pages.get(currentPage).name() + "  ›"
            : pages.get(currentPage).name();
        Component page = Component.literal(this.font.plainSubstrByWidth(footer, this.width - 12));
        graphics.text(this.font, page, cx - this.font.width(page) / 2,
            Math.min(this.height - 11, cy + radius + 22), 0xFFF0F4FC, true);
    }

    private void drawRadialSlots(GuiGraphicsExtractor graphics, int cx, int cy, int radius, List<AvatarAction> currentActions) {
        int cardW = 54;
        int cardH = 34;

        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            double angle = -Math.PI / 2.0 + (i * (2.0 * Math.PI / SLOTS_PER_PAGE));
            int slotX = cx + (int) (Math.cos(angle) * radius);
            int slotY = cy + (int) (Math.sin(angle) * radius);
            int left = slotX - cardW / 2;
            int top = slotY - cardH / 2;

            boolean hasAction = i < currentActions.size();
            boolean hovered = (i == hoveredSlot);

            int bg = hovered ? SURFACE_HOVER : SURFACE_CARD;
            int border = hovered ? ACCENT_GLOW : (hasAction ? 0x663DD9E8 : 0x223DD9E8);

            graphics.fill(left, top, left + cardW, top + cardH, bg);
            graphics.outline(left, top, cardW, cardH, border);

            if (hasAction) {
                AvatarAction action = currentActions.get(i);
                renderActionIcon(graphics, action, left + 4, top + (cardH - 16) / 2);

                String label = trimTitle(action.title(), cardW - 24);
                int textColor = action.color() != null ? action.color() : (hovered ? 0xFFFFFFFF : 0xFFE0E6F0);
                graphics.text(this.font, Component.literal(label), left + 22, top + 8, textColor, true);

                if (action.isToggle()) {
                    int dotColor = action.isToggled() ? TOGGLE_ON : TOGGLE_OFF;
                    graphics.fill(left + cardW - 7, top + 3, left + cardW - 3, top + 7, dotColor);
                }
            } else {
                graphics.text(this.font, Component.literal("·"), left + cardW / 2 - 2, top + cardH / 2 - 4, 0x446D7D93, false);
            }
        }
    }

    private List<AvatarAction> systemActions(boolean hasAvatar) {
        List<AvatarAction> actions = new ArrayList<>();
        actions.add(systemAction("screen.shyne_core.avatars", "screen.shyne_core.avatars.tooltip", "player_head",
            () -> openScreen(new AvatarManagerScreen(this))));
        if (hasAvatar) {
            actions.add(systemAction("screen.shyne_core.avatars.outfit", "screen.shyne_core.avatars.outfit.tooltip", "leather_chestplate",
                () -> openScreen(new AvatarOutfitScreen(this))));
        }
        actions.add(systemAction("screen.shyne_core.settings", "screen.shyne_core.action_wheel.settings_hint", "comparator",
            () -> openScreen(new ShyneSettingsScreen(this))));
        if (hasAvatar) {
            actions.add(systemAction("screen.shyne_core.action_wheel.reload", "screen.shyne_core.action_wheel.reload_hint", "clock",
                () -> {
                    if (this.minecraft != null) {
                        AvatarRuntime.reloadActive(this.minecraft);
                        this.minecraft.gui.setScreen(new AvatarActionWheelScreen(parent));
                    }
                }));
        }
        return List.copyOf(actions);
    }

    private AvatarAction systemAction(String titleKey, String descriptionKey, String icon, Runnable callback) {
        return new AvatarAction(titleKey, Component.translatable(titleKey).getString(),
            Component.translatable(descriptionKey).getString(), "system", icon, false, false, callback);
    }

    private void openScreen(Screen screen) {
        if (this.minecraft != null) this.minecraft.gui.setScreen(screen);
    }

    private void renderActionIcon(GuiGraphicsExtractor graphics, AvatarAction action, int x, int y) {
        String icon = action.icon();
        if (!icon.isBlank() && (icon.contains(":") || BuiltInRegistries.ITEM.containsKey(Identifier.tryParse("minecraft:" + icon)))) {
            String fullId = icon.contains(":") ? icon : "minecraft:" + icon;
            Identifier id = Identifier.tryParse(fullId);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id).orElseThrow().value());
                if (!stack.isEmpty()) {
                    graphics.item(stack, x, y);
                    return;
                }
            }
        }

        String glyph = resolveGlyph(icon, action.title());
        graphics.text(this.font, Component.literal(glyph), x + 3, y + 4, ACCENT_CYAN, true);
    }

    private String resolveGlyph(String icon, String title) {
        String key = (icon + " " + title).toLowerCase(Locale.ROOT);
        if (key.contains("roar") || key.contains("sound") || key.contains("music") || key.contains("audio")) return "♪";
        if (key.contains("wave") || key.contains("hand") || key.contains("arm")) return "✋";
        if (key.contains("dance") || key.contains("jump") || key.contains("emote")) return "★";
        if (key.contains("ear") || key.contains("tail") || key.contains("physics")) return "≋";
        if (key.contains("sword") || key.contains("attack") || key.contains("strike")) return "⚔";
        if (key.contains("shield") || key.contains("armor") || key.contains("defend")) return "◆";
        if (key.contains("heart") || key.contains("love") || key.contains("heal")) return "♥";
        if (key.contains("toggle") || key.contains("setting") || key.contains("config")) return "⚙";
        return "✦";
    }

    private String trimTitle(String title, int maxWidth) {
        if (this.font.width(title) <= maxWidth) return title;
        while (!title.isEmpty() && this.font.width(title + "..") > maxWidth) {
            title = title.substring(0, title.length() - 1);
        }
        return title + "..";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (totalPages > 1 && event.button() == 0 && event.x() >= this.width - 27 && event.y() >= 4 && event.y() < 26) {
            currentPage = totalPages - 1;
            hoveredSlot = -1;
            playUiScrollSound();
            return true;
        }
        if (totalPages > 1 && event.button() == 0 && event.y() >= this.height - 24) {
            if (event.x() < this.width / 3) changePage(-1);
            else if (event.x() > this.width * 2 / 3) changePage(1);
            else return super.mouseClicked(event, doubleClick);
            return true;
        }
        List<AvatarAction> currentActions = getCurrentActions();
        updateHoveredSlot(this.width / 2, this.height / 2, wheelRadius(),
            (int) event.x(), (int) event.y(), currentActions.size());
        if (hoveredSlot >= 0 && hoveredSlot < currentActions.size()) {
            AvatarAction action = currentActions.get(hoveredSlot);
            playUiClickSound();

            if (event.button() == 0) { // Left Click
                if (action.isToggle()) {
                    action.setToggled(!action.isToggled());
                }
                action.callback().run();
                if (action.closeOnUse()) {
                    this.onClose();
                }
                return true;
            } else if (event.button() == 1 && action.hasSecondaryCallback()) { // Right Click
                action.secondaryCallback().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (totalPages > 1 && verticalAmount != 0) {
            changePage(verticalAmount < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (totalPages > 1 && keyCode == 258) {
            currentPage = totalPages - 1;
            hoveredSlot = -1;
            playUiScrollSound();
            return true;
        }
        if (totalPages > 1 && (keyCode == 262 || keyCode == 267)) {
            changePage(1);
            return true;
        }
        if (totalPages > 1 && (keyCode == 263 || keyCode == 266)) {
            changePage(-1);
            return true;
        }
        // Number keys 1-8 quickly trigger radial slots
        if (keyCode >= 49 && keyCode <= 56) {
            int slot = keyCode - 49;
            List<AvatarAction> currentActions = getCurrentActions();
            if (slot < currentActions.size()) {
                AvatarAction action = currentActions.get(slot);
                playUiClickSound();
                if (action.isToggle()) action.setToggled(!action.isToggled());
                action.callback().run();
                if (action.closeOnUse()) this.onClose();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private List<AvatarAction> getCurrentActions() {
        if (pages.isEmpty() || currentPage >= pages.size()) return List.of();
        return pages.get(currentPage).actions;
    }

    private void changePage(int direction) {
        currentPage = (currentPage + direction + totalPages) % totalPages;
        hoveredSlot = -1;
        playUiScrollSound();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    private void playUiClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void playUiScrollSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.3F));
    }

    private record Page(String name, List<AvatarAction> actions) {}
}

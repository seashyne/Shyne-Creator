package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 8-slot Radial Action Wheel GUI for avatars.
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
            avatarName = "";
            totalPages = 1;
            return;
        }

        avatarName = AvatarRuntime.catalog().stream()
            .filter(entry -> entry.id().equalsIgnoreCase(state.avatarId()))
            .map(entry -> entry.name().isBlank() ? state.avatarId() : entry.name())
            .findFirst()
            .orElse(state.avatarId());

        Map<String, List<AvatarAction>> actionsByPage = state.actionsByPage();
        if (actionsByPage.isEmpty()) {
            pages.add(new Page("main", List.of()));
        } else {
            for (Map.Entry<String, List<AvatarAction>> entry : actionsByPage.entrySet()) {
                List<AvatarAction> list = entry.getValue();
                if (list.isEmpty()) {
                    pages.add(new Page(entry.getKey(), List.of()));
                } else {
                    for (int start = 0; start < list.size(); start += SLOTS_PER_PAGE) {
                        int end = Math.min(start + SLOTS_PER_PAGE, list.size());
                        pages.add(new Page(entry.getKey(), list.subList(start, end)));
                    }
                }
            }
        }

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
        int wheelRadius = Math.min(95, Math.max(70, this.height / 3));

        List<AvatarAction> currentActions = getCurrentActions();
        updateHoveredSlot(cx, cy, mouseX, mouseY, currentActions.size());

        drawCenterHub(graphics, cx, cy, currentActions);
        drawRadialSlots(graphics, cx, cy, wheelRadius, currentActions);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void updateHoveredSlot(int cx, int cy, int mouseX, int mouseY, int actionCount) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist >= 35 && dist <= 145 && actionCount > 0) {
            double angle = Math.atan2(dy, dx);
            double normalized = (angle + Math.PI / 2.0 + 2.0 * Math.PI) % (2.0 * Math.PI);
            int slot = (int) Math.round(normalized / (2.0 * Math.PI / SLOTS_PER_PAGE)) % SLOTS_PER_PAGE;
            hoveredSlot = slot < actionCount ? slot : -1;
        } else {
            hoveredSlot = -1;
        }
    }

    private void drawCenterHub(GuiGraphicsExtractor graphics, int cx, int cy, List<AvatarAction> currentActions) {
        int hubRadius = 38;
        graphics.fill(cx - hubRadius, cy - hubRadius, cx + hubRadius, cy + hubRadius, SURFACE_BG);
        graphics.outline(cx - hubRadius, cy - hubRadius, hubRadius * 2, hubRadius * 2, 0x553DD9E8);

        if (hoveredSlot >= 0 && hoveredSlot < currentActions.size()) {
            AvatarAction action = currentActions.get(hoveredSlot);
            Component title = Component.literal(action.title());
            int titleColor = action.hoverColor() != null ? action.hoverColor() : ACCENT_CYAN;
            graphics.text(this.font, title, cx - this.font.width(title) / 2, cy - 18, titleColor, true);

            if (!action.description().isBlank()) {
                Component desc = Component.literal(action.description());
                graphics.text(this.font, desc, cx - this.font.width(desc) / 2, cy - 6, TEXT_MUTED, false);
            }

            if (action.isToggle()) {
                String status = action.isToggled() ? "● ACTIVE" : "○ INACTIVE";
                int color = action.isToggled() ? TOGGLE_ON : TOGGLE_OFF;
                graphics.text(this.font, Component.literal(status), cx - this.font.width(status) / 2, cy + 6, color, true);
            } else {
                String hint = action.hasSecondaryCallback() ? "L-Click: Use  •  R-Click" : "Click to activate";
                graphics.text(this.font, Component.literal(hint), cx - this.font.width(hint) / 2, cy + 7, TEXT_MUTED, false);
            }
        } else {
            String nameText = avatarName.isBlank() ? "Shyne Avatar" : avatarName;
            Component nameComp = Component.literal(nameText);
            graphics.text(this.font, nameComp, cx - this.font.width(nameComp) / 2, cy - 14, 0xFFF0F4FC, true);

            String pageText = currentActions.isEmpty() ? "No Actions" : "Page " + (currentPage + 1) + "/" + totalPages;
            graphics.text(this.font, Component.literal(pageText), cx - this.font.width(pageText) / 2, cy - 2, ACCENT_CYAN, false);

            String scrollHint = totalPages > 1 ? "Scroll: Change Page" : "Select Action";
            graphics.text(this.font, Component.literal(scrollHint), cx - this.font.width(scrollHint) / 2, cy + 10, TEXT_MUTED, false);
        }
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
        List<AvatarAction> currentActions = getCurrentActions();
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
            if (verticalAmount < 0) {
                currentPage = (currentPage + 1) % totalPages;
            } else {
                currentPage = (currentPage - 1 + totalPages) % totalPages;
            }
            playUiScrollSound();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
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

    private void playUiClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void playUiScrollSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.3F));
    }

    private record Page(String name, List<AvatarAction> actions) {}
}

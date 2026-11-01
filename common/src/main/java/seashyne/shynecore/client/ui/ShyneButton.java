package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * Sleek flat studio button replacing vanilla stone widgets across Shyne Creator.
 * Automatically ellipsizes overflowing text, renders crisp hover/focus borders,
 * and supports semantic variants (primary, secondary, ghost, toggle, tab, danger).
 */
public final class ShyneButton extends Button {
    public enum Variant {
        PRIMARY,
        SECONDARY,
        GHOST,
        TOGGLE,
        DANGER,
        TAB,
        SUCCESS_PILL
    }

    private final Variant variant;
    private final BooleanSupplier toggleState;
    private final boolean tabActive;

    public ShyneButton(
        int x,
        int y,
        int width,
        int height,
        Component message,
        OnPress onPress,
        Variant variant,
        BooleanSupplier toggleState,
        boolean tabActive
    ) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.variant = variant == null ? Variant.SECONDARY : variant;
        this.toggleState = toggleState;
        this.tabActive = tabActive;
    }

    public static ShyneButton primary(Component message, OnPress onPress) {
        return new ShyneButton(0, 0, 80, 20, message, onPress, Variant.PRIMARY, null, false);
    }

    public static ShyneButton secondary(Component message, OnPress onPress) {
        return new ShyneButton(0, 0, 80, 20, message, onPress, Variant.SECONDARY, null, false);
    }

    public static ShyneButton ghost(Component message, OnPress onPress) {
        return new ShyneButton(0, 0, 80, 20, message, onPress, Variant.GHOST, null, false);
    }

    public static ShyneButton danger(Component message, OnPress onPress) {
        return new ShyneButton(0, 0, 80, 20, message, onPress, Variant.DANGER, null, false);
    }

    public static ShyneButton successPill(Component message, OnPress onPress) {
        return new ShyneButton(0, 0, 80, 20, message, onPress, Variant.SUCCESS_PILL, null, false);
    }

    public static ShyneButton toggle(Component message, BooleanSupplier state, OnPress onPress) {
        return new ShyneButton(0, 0, 80, 20, message, onPress, Variant.TOGGLE, state, false);
    }

    public static ShyneButton tab(Component message, boolean activeTab, OnPress onPress) {
        ShyneButton btn = new ShyneButton(0, 0, 72, 20, message, onPress, Variant.TAB, null, activeTab);
        btn.active = !activeTab;
        return btn;
    }

    public static ShyneButton tab(Component message, OnPress onPress, boolean activeTab) {
        return tab(message, activeTab, onPress);
    }

    public ShyneButton bounds(int x, int y, int width, int height) {
        this.setX(x);
        this.setY(y);
        this.setWidth(Math.max(12, width));
        this.setHeight(Math.max(12, height));
        return this;
    }

    public ShyneButton tooltipText(Component tooltip) {
        if (tooltip != null) {
            this.setTooltip(Tooltip.create(tooltip));
        }
        return this;
    }

    public ShyneButton activeState(boolean enabled) {
        this.active = enabled;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = this.isHoveredOrFocused() && this.active;

        int fill;
        int border;
        int textColor;
        boolean drawBottomAccent = false;
        boolean drawTopHighlight = false;
        boolean toggledOn = toggleState != null && toggleState.getAsBoolean();

        if (variant == Variant.TAB && tabActive) {
            fill = 0xFF142E46;
            border = 0xAA3DD9E8;
            textColor = ShyneTheme.ACCENT_BRIGHT;
            drawBottomAccent = true;
        } else if (variant == Variant.SUCCESS_PILL && !this.active) {
            fill = 0x264AE39C;
            border = 0x994AE39C;
            textColor = ShyneTheme.SUCCESS;
        } else if (!this.active) {
            fill = 0x880C1524;
            border = 0x33354A66;
            textColor = 0xFF5E738E;
        } else {
            switch (variant) {
                case PRIMARY -> {
                    fill = hovered ? 0xFF1C4662 : 0xFF15364E;
                    border = hovered ? ShyneTheme.ACCENT_BRIGHT : ShyneTheme.BORDER_ACCENT;
                    textColor = 0xFFFFFFFF;
                    drawTopHighlight = true;
                }
                case GHOST -> {
                    fill = hovered ? 0xB816263E : 0x480E192B;
                    border = hovered ? 0x8846C4D6 : 0x283B5270;
                    textColor = hovered ? ShyneTheme.ACCENT_BRIGHT : ShyneTheme.TEXT_SECONDARY;
                }
                case TOGGLE -> {
                    fill = toggledOn
                        ? (hovered ? 0xFF183C4E : 0xFF132F40)
                        : (hovered ? 0xF0192A44 : 0xE0101B2E);
                    border = toggledOn
                        ? (hovered ? ShyneTheme.ACCENT_BRIGHT : 0xBB4AE39C)
                        : (hovered ? ShyneTheme.BORDER_HOVER : ShyneTheme.BORDER_SUBTLE);
                    textColor = toggledOn ? 0xFFFFFFFF : (hovered ? ShyneTheme.TEXT_PRIMARY : ShyneTheme.TEXT_SECONDARY);
                }
                case DANGER -> {
                    fill = hovered ? 0xFF421A26 : 0xE82B131D;
                    border = hovered ? 0xFFFF8FA0 : 0xAAFF6B7F;
                    textColor = hovered ? 0xFFFFFFFF : 0xFFFFB8C3;
                }
                case TAB -> {
                    fill = hovered ? 0xCC16263E : 0x660D1728;
                    border = hovered ? 0x6646C4D6 : 0x283B5270;
                    textColor = hovered ? ShyneTheme.TEXT_PRIMARY : ShyneTheme.TEXT_SECONDARY;
                }
                case SUCCESS_PILL -> {
                    fill = hovered ? 0xFF174438 : 0xFF12352C;
                    border = 0xCC4AE39C;
                    textColor = ShyneTheme.SUCCESS;
                }
                default -> {
                    fill = hovered ? 0xF41B2D48 : 0xE8121F34;
                    border = hovered ? ShyneTheme.BORDER_HOVER : ShyneTheme.BORDER_SUBTLE;
                    textColor = hovered ? 0xFFFFFFFF : ShyneTheme.TEXT_PRIMARY;
                }
            }
        }

        graphics.fill(x, y, x + w, y + h, fill);
        graphics.outline(x, y, w, h, border);
        if (drawTopHighlight && w > 4) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, 0x4472F5FF);
        }
        if (drawBottomAccent) {
            graphics.fill(x + 1, y + h - 2, x + w - 1, y + h, ShyneTheme.ACCENT);
        }

        Font font = Minecraft.getInstance().font;
        int leftPad = 5;
        if (variant == Variant.TOGGLE && w >= 34) {
            int dotColor = !this.active ? 0xFF43556D : (toggledOn ? ShyneTheme.SUCCESS : 0xFF566A84);
            int dotY = y + (h - 4) / 2;
            graphics.fill(x + 6, dotY, x + 10, dotY + 4, dotColor);
            leftPad = 14;
        }

        int maxTextWidth = Math.max(4, w - leftPad - 5);
        String raw = getMessage().getString();
        String fitted = ShyneTheme.ellipsize(font, raw, maxTextWidth);
        int textWidth = font.width(fitted);
        int textX = variant == Variant.TOGGLE && w >= 34
            ? x + leftPad + Math.max(0, (maxTextWidth - textWidth) / 2)
            : x + Math.max(3, (w - textWidth) / 2);
        int textY = y + (h - 8) / 2;
        graphics.text(font, Component.literal(fitted), textX, textY, textColor, false);
    }
}

package seashyne.shynecore.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shared visual tokens and precision drawing helpers for the Shyne Studio UI.
 * Uses a dark-obsidian and electric-cyan palette with crisp borders and zero
 * bulky vanilla stone chrome.
 */
public final class ShyneTheme {
    public static final int BACKDROP = 0xBC040812;
    public static final int PANEL_SHADOW = 0x70000000;
    public static final int PANEL_BG = 0xF50A1220;
    public static final int PANEL_HEADER = 0xFF0E182B;
    public static final int PANEL_INSET = 0xFF070E1A;

    public static final int CARD_BG = 0xEC111D30;
    public static final int CARD_ALT = 0xD80E1929;
    public static final int CARD_HOVER = 0xF6182A44;
    public static final int CARD_ACTIVE = 0xFF14314A;

    public static final int BORDER_SUBTLE = 0x553B5270;
    public static final int BORDER_HOVER = 0xB846C4D6;
    public static final int BORDER_ACCENT = 0xEE3DD9E8;

    public static final int ACCENT = 0xFF3DD9E8;
    public static final int ACCENT_BRIGHT = 0xFF72F5FF;
    public static final int ACCENT_SOFT = 0x2A3DD9E8;

    public static final int SUCCESS = 0xFF4AE39C;
    public static final int SUCCESS_SOFT = 0x264AE39C;
    public static final int WARNING = 0xFFFFC66D;
    public static final int WARNING_SOFT = 0x28FFC66D;
    public static final int DANGER = 0xFFFF6B7F;
    public static final int DANGER_SOFT = 0x28FF6B7F;

    public static final int TEXT_PRIMARY = 0xFFF4F8FF;
    public static final int TEXT_SECONDARY = 0xFF94A7C2;
    public static final int TEXT_DIM = 0xFF5D718C;
    public static final int TEXT_MUTED = TEXT_SECONDARY;
    public static final int TEXT_TITLE = TEXT_PRIMARY;
    public static final int CARD_SELECTED = CARD_ACTIVE;

    private ShyneTheme() {}

    public static void drawBackdrop(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fill(0, 0, width, height, BACKDROP);
    }

    public static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        drawPanel(graphics, x, y, width, height, 38);
    }

    public static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int headerHeight) {
        graphics.fill(x + 3, y + 4, x + width + 3, y + height + 4, PANEL_SHADOW);
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        if (headerHeight > 0 && headerHeight < height) {
            graphics.fill(x + 1, y + 2, x + width - 1, y + headerHeight, PANEL_HEADER);
            graphics.fill(x + 1, y + headerHeight, x + width - 1, y + headerHeight + 1, 0x383DD9E8);
        }
        graphics.outline(x, y, width, height, BORDER_SUBTLE);
        graphics.fill(x, y, x + width, y + 2, ACCENT);
    }

    public static void drawCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                boolean hovered, boolean active) {
        int bg = active ? CARD_ACTIVE : (hovered ? CARD_HOVER : CARD_BG);
        int border = active ? BORDER_ACCENT : (hovered ? BORDER_HOVER : BORDER_SUBTLE);
        graphics.fill(x, y, x + width, y + height, bg);
        graphics.outline(x, y, width, height, border);
        if (active) {
            graphics.fill(x, y, x + 3, y + height, ACCENT);
        }
    }

    public static void drawCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                int bg, int border) {
        graphics.fill(x, y, x + width, y + height, bg);
        graphics.outline(x, y, width, height, border);
    }

    public static void drawInset(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int borderColor) {
        graphics.fill(x, y, x + width, y + height, PANEL_INSET);
        graphics.outline(x, y, width, height, borderColor);
    }

    public static int drawBadge(GuiGraphicsExtractor graphics, Font font, String label,
                                int x, int y, int fillColor, int borderColor, int textColor) {
        if (label == null || label.isEmpty()) return 0;
        int w = font.width(label) + 10;
        int h = 13;
        graphics.fill(x, y, x + w, y + h, fillColor);
        graphics.outline(x, y, w, h, borderColor);
        graphics.text(font, Component.literal(label), x + 5, y + 3, textColor, false);
        return w;
    }

    public static void drawScrollbar(GuiGraphicsExtractor graphics, int trackX, int trackTop, int trackBottom,
                                     int scrollOffset, int maxScroll, int visibleCount, int totalCount) {
        int trackHeight = trackBottom - trackTop;
        if (trackHeight <= 10 || maxScroll <= 0 || totalCount <= 0) return;
        graphics.fill(trackX, trackTop, trackX + 3, trackBottom, 0x333B5270);
        int thumbHeight = Math.max(10, trackHeight * Math.max(1, visibleCount) / Math.max(1, totalCount));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        int thumbY = trackTop + Math.round((trackHeight - thumbHeight) * (Math.min(scrollOffset, maxScroll) / (float) maxScroll));
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, ACCENT);
    }


    public static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String suffix = "…";
        int suffixWidth = font.width(suffix);
        if (maxWidth <= suffixWidth) {
            return font.plainSubstrByWidth(text, maxWidth);
        }
        String trimmed = font.plainSubstrByWidth(text, maxWidth - suffixWidth);
        return trimmed + suffix;
    }
}

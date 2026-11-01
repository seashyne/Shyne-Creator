package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.config.ShyneClientSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Unified top navigation bar connecting all major Shyne Studio workspaces
 * (Avatars, Action Wheel, Powers, Items, Server Players, Cloud, and Settings)
 * without cluttering the Minecraft pause menu or creating deep screen stacks.
 */
public final class ShyneNavBar {
    public enum Section {
        AVATARS("screen.shyne_core.nav.avatars", "screen.shyne_core.avatars.tooltip"),
        WHEEL("screen.shyne_core.nav.wheel", "screen.shyne_core.avatars.actions.tooltip"),
        POWERS("screen.shyne_core.nav.powers", "screen.shyne_core.pause_powers_button.tooltip"),
        ITEMS("screen.shyne_core.nav.items", "screen.shyne_core.pause_items_button.tooltip"),
        PLAYERS("screen.shyne_core.nav.players", "screen.shyne_core.players.open.tooltip"),
        CLOUD("screen.shyne_core.nav.cloud", "screen.shyne_core.cloud.open.tooltip"),
        SETTINGS("screen.shyne_core.nav.settings", "screen.shyne_core.pause_button.tooltip");

        public final String labelKey;
        public final String tooltipKey;

        Section(String labelKey, String tooltipKey) {
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
        }
    }

    private ShyneNavBar() {}

    /**
     * Builds right-aligned top navigation tabs and returns the X coordinate of the
     * leftmost tab so headers can safely clip their titles without overlap.
     */
    public static int addTabs(
        Minecraft mc,
        Screen currentScreen,
        Screen rootParent,
        Section activeSection,
        int rightEdgeX,
        int y,
        int maxAvailableWidth,
        Consumer<ShyneButton> widgetAdder
    ) {
        if (mc == null || maxAvailableWidth < 120) return rightEdgeX;
        Font font = mc.font;
        Screen targetParent = unwrapRootParent(rootParent);

        Section[] sections = Section.values();
        int gap = 3;
        int totalGaps = gap * (sections.length - 1);
        int[] widths = new int[sections.length];
        int totalDesired = totalGaps;
        for (int i = 0; i < sections.length; i++) {
            String text = Component.translatable(sections[i].labelKey).getString();
            widths[i] = Math.max(36, Math.min(76, font.width(text) + 12));
            totalDesired += widths[i];
        }

        if (totalDesired > maxAvailableWidth) {
            float scale = Math.max(0.45f, (maxAvailableWidth - totalGaps) / (float) Math.max(1, totalDesired - totalGaps));
            for (int i = 0; i < sections.length; i++) {
                widths[i] = Math.max(24, Math.round(widths[i] * scale));
            }
        }

        int totalActual = totalGaps;
        for (int w : widths) totalActual += w;
        int startX = rightEdgeX - totalActual;
        int cursorX = startX;

        boolean inWorld = mc.getConnection() != null && mc.level != null;
        List<ShyneButton> built = new ArrayList<>(sections.length);
        for (int i = 0; i < sections.length; i++) {
            Section section = sections[i];
            boolean isActive = section == activeSection;
            Component label = Component.translatable(section.labelKey);
            ShyneButton tab = ShyneButton.tab(label, isActive, btn -> navigate(mc, currentScreen, targetParent, section))
                .bounds(cursorX, y, widths[i], 18)
                .tooltipText(Component.translatable(section.tooltipKey));
            if (section == Section.PLAYERS && !inWorld && !isActive) {
                tab.activeState(false);
            }
            built.add(tab);
            cursorX += widths[i] + gap;
        }

        for (ShyneButton btn : built) {
            widgetAdder.accept(btn);
        }
        return startX;
    }

    public static Screen unwrapRootParent(Screen candidate) {
        if (candidate instanceof AvatarManagerScreen
            || candidate instanceof PowerDeckScreen
            || candidate instanceof ItemCatalogScreen
            || candidate instanceof ServerPlayersScreen
            || candidate instanceof ShyneSettingsScreen
            || candidate instanceof CloudAvatarLibraryScreen
            || candidate instanceof AvatarActionWheelScreen) {
            return null;
        }
        return candidate;
    }

    private static void navigate(Minecraft mc, Screen currentScreen, Screen rootParent, Section section) {
        if (mc == null) return;
        Screen next = switch (section) {
            case AVATARS -> new AvatarManagerScreen(rootParent);
            case WHEEL -> new AvatarActionWheelScreen(currentScreen);
            case POWERS -> new PowerDeckScreen(rootParent);
            case ITEMS -> new ItemCatalogScreen(rootParent);
            case PLAYERS -> new ServerPlayersScreen(rootParent);
            case CLOUD -> ShyneClientSettings.cloudEnabled
                ? new CloudAvatarLibraryScreen(rootParent)
                : new ShyneStatusScreen(currentScreen);
            case SETTINGS -> new ShyneSettingsScreen(rootParent);
        };
        mc.gui.setScreen(next);
    }
}

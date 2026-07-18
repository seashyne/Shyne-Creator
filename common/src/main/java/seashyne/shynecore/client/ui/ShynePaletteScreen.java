package seashyne.shynecore.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.avatar.AvatarAction;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ShynePaletteScreen extends Screen {
    private static final int SURFACE = 0xF20B1222;
    private static final int SURFACE_RAISED = 0xE817263D;
    private static final int ACCENT = 0xFF3DD9E8;
    private static final int TEXT_MUTED = 0xFF91A0B7;

    private final Screen parent;
    private final int pageIndex;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int columns;
    private int actionTop;
    private int tileWidth;
    private int currentPage;
    private int totalPages;
    private String avatarName = "";
    private String pageName = "actions";
    private List<AvatarAction> visibleActions = List.of();

    public ShynePaletteScreen() {
        this(null, 0);
    }

    public ShynePaletteScreen(Screen parent) {
        this(parent, 0);
    }

    private ShynePaletteScreen(Screen parent, int pageIndex) {
        super(Component.translatable("screen.shyne_core.palette.title"));
        this.parent = parent;
        this.pageIndex = Math.max(0, pageIndex);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(600, Math.max(240, this.width - 20));
        panelHeight = Math.min(300, Math.max(172, this.height - 20));
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        columns = panelWidth >= 500 ? 3 : panelWidth >= 320 ? 2 : 1;
        actionTop = panelY + 55;
        int gap = 8;
        tileWidth = (panelWidth - 32 - gap * (columns - 1)) / columns;

        AvatarState state = AvatarRuntime.active();
        if (state == null) {
            Button empty = Button.builder(Component.translatable("screen.shyne_core.palette.no_avatar"), ignored -> {})
                .bounds(panelX + 16, actionTop, panelWidth - 32, 22).build();
            empty.active = false;
            addRenderableWidget(empty);
            addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.palette.close"), btn -> onClose())
                .bounds(panelX + 16, panelY + panelHeight - 28, panelWidth - 32, 20).build());
            return;
        }

        avatarName = AvatarRuntime.catalog().stream()
            .filter(entry -> entry.id().equalsIgnoreCase(state.avatarId()))
            .map(entry -> entry.name().isBlank() ? state.avatarId() : entry.name())
            .findFirst()
            .orElse(state.avatarId());
        int footerY = panelY + panelHeight - 28;
        int rows = Math.max(1, (footerY - actionTop - 8) / 38);
        int capacity = columns * rows;
        List<Map.Entry<String, List<AvatarAction>>> actionGroups = new ArrayList<>(state.actionsByPage().entrySet());
        if (actionGroups.isEmpty()) actionGroups.add(Map.entry("actions", List.of()));
        List<PalettePage> pages = new ArrayList<>();
        for (Map.Entry<String, List<AvatarAction>> group : actionGroups) {
            if (group.getValue().isEmpty()) {
                pages.add(new PalettePage(group.getKey(), List.of()));
                continue;
            }
            int groupPages = (group.getValue().size() + capacity - 1) / capacity;
            for (int start = 0; start < group.getValue().size(); start += capacity) {
                int end = Math.min(start + capacity, group.getValue().size());
                int groupPage = start / capacity + 1;
                String name = groupPages > 1 ? group.getKey() + "  " + groupPage + "/" + groupPages : group.getKey();
                pages.add(new PalettePage(name, group.getValue().subList(start, end)));
            }
        }
        currentPage = Math.min(pageIndex, pages.size() - 1);
        totalPages = pages.size();
        PalettePage page = pages.get(currentPage);
        pageName = page.name();
        visibleActions = page.actions();
        for (int i = 0; i < visibleActions.size(); i++) {
            AvatarAction action = visibleActions.get(i);
            int column = i % columns;
            int row = i / columns;
            int tileX = panelX + 16 + column * (tileWidth + gap);
            int tileY = actionTop + row * 38;
            String tooltip = action.description().isBlank() ? action.title() : action.description();
            if (action.localOnly()) tooltip += "  •  " + Component.translatable("screen.shyne_core.palette.local_only").getString();
            addRenderableWidget(Button.builder(Component.literal(action.title()), btn -> runAction(action))
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .bounds(tileX + 34, tileY, tileWidth - 34, 30).build());
        }

        Button previous = Button.builder(Component.literal("‹"), btn -> openPage(currentPage - 1))
            .bounds(panelX + 16, footerY, 36, 20).build();
        previous.active = currentPage > 0;
        addRenderableWidget(previous);

        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.palette.close"), btn -> onClose())
            .bounds(panelX + 58, footerY, panelWidth - 116, 20).build());

        Button next = Button.builder(Component.literal("›"), btn -> openPage(currentPage + 1))
            .bounds(panelX + panelWidth - 52, footerY, 36, 20).build();
        next.active = currentPage + 1 < totalPages;
        addRenderableWidget(next);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xA8030710);
        graphics.fill(panelX + 3, panelY + 4, panelX + panelWidth + 3, panelY + panelHeight + 4, 0x66000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SURFACE);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, ACCENT);

        Component heading = avatarName.isBlank() ? Component.translatable("screen.shyne_core.palette.title") : Component.literal(avatarName);
        graphics.text(this.font, heading, panelX + 16, panelY + 14, 0xFFF4F7FC, true);
        String section = translatedPageName(pageName) + (totalPages > 1 ? "  •  " + (currentPage + 1) + "/" + totalPages : "");
        graphics.text(this.font, Component.literal(section), panelX + 16, panelY + 31, TEXT_MUTED, false);
        graphics.fill(panelX + 16, panelY + 46, panelX + panelWidth - 16, panelY + 47, 0x443DD9E8);

        if (visibleActions.isEmpty() && !avatarName.isBlank()) {
            Component empty = Component.translatable("screen.shyne_core.palette.empty");
            graphics.text(this.font, empty, panelX + (panelWidth - this.font.width(empty)) / 2, actionTop + 12, TEXT_MUTED, false);
        }
        int gap = 8;
        for (int i = 0; i < visibleActions.size(); i++) {
            AvatarAction action = visibleActions.get(i);
            int column = i % columns;
            int row = i / columns;
            int tileX = panelX + 16 + column * (tileWidth + gap);
            int tileY = actionTop + row * 38;
            graphics.fill(tileX, tileY, tileX + 30, tileY + 30, SURFACE_RAISED);
            graphics.outline(tileX, tileY, 30, 30, 0x8841D7E5);
            Component icon = Component.literal(iconGlyph(action));
            graphics.text(this.font, icon, tileX + (30 - this.font.width(icon)) / 2, tileY + 11, ACCENT, true);
            if (action.localOnly()) graphics.fill(tileX + 23, tileY + 2, tileX + 28, tileY + 7, 0xFF78DDB6);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private String iconGlyph(AvatarAction action) {
        String icon = action.icon().isBlank() ? action.id() : action.icon();
        String key = icon.toLowerCase(Locale.ROOT);
        if (key.contains("bolt") || key.contains("lightning") || key.contains("speed")) return "⚡";
        if (key.contains("shield") || key.contains("guard") || key.contains("armor")) return "◆";
        if (key.contains("heart") || key.contains("heal") || key.contains("health")) return "♥";
        if (key.contains("music") || key.contains("sound") || key.contains("voice")) return "♪";
        if (key.contains("fire") || key.contains("flame")) return "▲";
        if (key.contains("wave") || key.contains("water")) return "≈";
        if (key.contains("star") || key.contains("ultimate")) return "★";
        return "✦";
    }

    private String translatedPageName(String rawName) {
        String raw = rawName == null || rawName.isBlank() ? "actions" : rawName;
        String suffix = "";
        int pageMarker = raw.lastIndexOf("  ");
        if (pageMarker > 0) {
            suffix = raw.substring(pageMarker);
            raw = raw.substring(0, pageMarker);
        }
        String key = switch (raw.toLowerCase(Locale.ROOT)) {
            case "main" -> "screen.shyne_core.palette.page.main";
            case "actions", "action" -> "screen.shyne_core.palette.page.actions";
            default -> "";
        };
        return (key.isBlank() ? raw : Component.translatable(key).getString()) + suffix;
    }

    private void runAction(AvatarAction action) {
        action.callback().run();
        if (action.closeOnUse()) onClose();
    }

    private void openPage(int newPage) {
        if (this.minecraft != null) this.minecraft.gui.setScreen(new ShynePaletteScreen(parent, newPage));
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record PalettePage(String name, List<AvatarAction> actions) {}
}

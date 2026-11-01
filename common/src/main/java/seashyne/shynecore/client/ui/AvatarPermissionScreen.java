package seashyne.shynecore.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.config.ShyneClientSettings;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Clean, scrollable capability review and approval screen for local Avatars.
 * Prioritizes requested permissions with full translated titles, risk badges,
 * and inline descriptions while keeping unrequested engine capabilities tucked
 * behind an optional filter toggle.
 */
public final class AvatarPermissionScreen extends Screen {
    private static final int ROW_HEIGHT = 36;
    private static final int CARD_HEIGHT = 32;

    private final Screen parent;
    private final String avatarId;
    private final String avatarName;
    private final Set<AvatarPermission> requestedPermissions;
    private final EnumSet<AvatarPermission> approved = EnumSet.noneOf(AvatarPermission.class);
    private final Runnable onApplied;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listX;
    private int listWidth;
    private int listTop;
    private int listBottom;
    private int visibleRows;
    private int scrollOffset;
    private int maxScroll;
    private boolean showUnused;

    private List<AvatarPermission> displayedPermissions = List.of();
    private final List<AbstractWidget> rowToggles = new ArrayList<>();
    private ShyneButton unusedFilterBtn;

    public AvatarPermissionScreen(Screen parent, String avatarId, String avatarName, Set<AvatarPermission> requestedPermissions) {
        this(parent, avatarId, avatarName, requestedPermissions, null);
    }

    public AvatarPermissionScreen(Screen parent, String avatarId, String avatarName, Set<AvatarPermission> requestedPermissions, Runnable onApplied) {
        super(Component.translatable("screen.shyne_core.permissions.local_title"));
        this.parent = parent;
        this.avatarId = avatarId == null ? "" : avatarId;
        this.avatarName = avatarName == null || avatarName.isBlank() ? this.avatarId : avatarName;
        this.requestedPermissions = requestedPermissions == null ? Set.of() : Set.copyOf(requestedPermissions);
        this.onApplied = onApplied;

        if (ShyneClientSettings.hasAvatarPermissionDecision(this.avatarId)) {
            approved.addAll(ShyneClientSettings.approvedAvatarPermissions(this.avatarId, this.requestedPermissions));
        } else {
            approved.addAll(this.requestedPermissions);
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        rowToggles.clear();

        panelWidth = Math.min(580, Math.max(300, width - 20));
        panelHeight = Math.min(368, Math.max(240, height - 16));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        listX = panelX + 14;
        listWidth = panelWidth - 28;
        listTop = panelY + 72;
        int footerY = panelY + panelHeight - 28;
        listBottom = footerY - 8;
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);

        // Quick preset toolbar
        int toolbarY = panelY + 46;
        int gap = 4;
        int presetW = Math.min(78, Math.max(52, (listWidth - 124) / 4));

        addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.permissions.safe_only"), btn -> {
            approved.clear();
            for (AvatarPermission p : requestedPermissions) {
                if (!p.dangerous()) approved.add(p);
            }
            rebuildRowToggles();
        }).bounds(listX, toolbarY, presetW, 18)
            .tooltipText(Component.translatable("screen.shyne_core.permissions.safe_only.tooltip")));

        addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.permissions.allow_all"), btn -> {
            approved.addAll(requestedPermissions);
            rebuildRowToggles();
        }).bounds(listX + (presetW + gap), toolbarY, presetW, 18)
            .tooltipText(Component.translatable("screen.shyne_core.permissions.allow_all.tooltip")));

        addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.permissions.deny_all"), btn -> {
            approved.clear();
            rebuildRowToggles();
        }).bounds(listX + (presetW + gap) * 2, toolbarY, presetW, 18)
            .tooltipText(Component.translatable("screen.shyne_core.permissions.deny_all.tooltip")));

        addRenderableWidget(ShyneButton.ghost(Component.translatable("screen.shyne_core.reset"), btn -> {
            ShyneClientSettings.resetAvatarPermissions(avatarId);
            approved.clear();
            approved.addAll(requestedPermissions);
            rebuildRowToggles();
        }).bounds(listX + (presetW + gap) * 3, toolbarY, Math.max(46, presetW - 10), 18)
            .tooltipText(Component.translatable("screen.shyne_core.reset.tooltip")));

        int unusedCount = AvatarPermission.values().length - requestedPermissions.size();
        if (unusedCount > 0) {
            int filterW = Math.min(118, Math.max(76, listWidth - (presetW + gap) * 3 - Math.max(46, presetW - 10) - 6));
            unusedFilterBtn = ShyneButton.toggle(unusedFilterLabel(unusedCount), () -> showUnused, btn -> {
                showUnused = !showUnused;
                scrollOffset = 0;
                unusedFilterBtn.setMessage(unusedFilterLabel(unusedCount));
                rebuildRowToggles();
            }).bounds(listX + listWidth - filterW, toolbarY, filterW, 18);
            addRenderableWidget(unusedFilterBtn);
        }

        // Footer actions
        addRenderableWidget(ShyneButton.primary(Component.translatable("screen.shyne_core.permissions.save_apply"), btn -> applyDecision())
            .bounds(panelX + panelWidth - 208, footerY, 108, 20));
        addRenderableWidget(ShyneButton.secondary(Component.translatable("gui.cancel"), btn -> onClose())
            .bounds(panelX + panelWidth - 94, footerY, 80, 20));

        rebuildRowToggles();
    }

    private Component unusedFilterLabel(int unusedCount) {
        return showUnused
            ? Component.translatable("screen.shyne_core.permissions.hide_unused")
            : Component.translatable("screen.shyne_core.permissions.show_unused", unusedCount);
    }

    private void rebuildRowToggles() {
        for (AbstractWidget widget : rowToggles) {
            removeWidget(widget);
        }
        rowToggles.clear();

        List<AvatarPermission> ordered = new ArrayList<>();
        // 1. Requested standard
        for (AvatarPermission p : AvatarPermission.values()) {
            if (requestedPermissions.contains(p) && !p.dangerous()) ordered.add(p);
        }
        // 2. Requested sensitive
        for (AvatarPermission p : AvatarPermission.values()) {
            if (requestedPermissions.contains(p) && p.dangerous()) ordered.add(p);
        }
        // 3. Unused capabilities (only when toggled on)
        if (showUnused) {
            for (AvatarPermission p : AvatarPermission.values()) {
                if (!requestedPermissions.contains(p)) ordered.add(p);
            }
        }
        displayedPermissions = List.copyOf(ordered);
        maxScroll = Math.max(0, displayedPermissions.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int toggleW = 84;
        int cardWidth = listWidth - (maxScroll > 0 ? 7 : 0);
        int end = Math.min(displayedPermissions.size(), scrollOffset + visibleRows);

        for (int i = scrollOffset; i < end; i++) {
            AvatarPermission perm = displayedPermissions.get(i);
            boolean requested = requestedPermissions.contains(perm);
            int rowY = listTop + (i - scrollOffset) * ROW_HEIGHT;

            Component btnLabel = !requested
                ? Component.translatable("screen.shyne_core.permissions.unused")
                : Component.translatable(approved.contains(perm)
                    ? "screen.shyne_core.permissions.allowed"
                    : "screen.shyne_core.permissions.blocked");

            ShyneButton toggle = ShyneButton.toggle(
                btnLabel,
                () -> approved.contains(perm),
                btn -> {
                    if (!approved.remove(perm)) approved.add(perm);
                    rebuildRowToggles();
                }
            ).bounds(listX + cardWidth - toggleW - 6, rowY + 6, toggleW, 20)
                .tooltipText(Component.translatable(perm.descriptionKey()))
                .activeState(requested);

            rowToggles.add(toggle);
            addRenderableWidget(toggle);
        }
    }

    private void applyDecision() {
        Set<AvatarPermission> decision = Set.copyOf(approved);
        ShyneClientSettings.decideAvatarPermissions(avatarId, decision);
        AvatarState active = AvatarRuntime.active();
        if (active != null && active.avatarId().equalsIgnoreCase(avatarId)) {
            active.setGrantedPermissions(decision);
        }
        if (onApplied != null) onApplied.run();
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ShyneTheme.drawBackdrop(graphics, width, height);
        ShyneTheme.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight, 38);

        graphics.text(
            font,
            Component.literal("✦ ").append(Component.translatable("screen.shyne_core.permissions.local_title")),
            panelX + 14,
            panelY + 9,
            ShyneTheme.TEXT_PRIMARY,
            true
        );
        String avatarSubtitle = avatarName.equalsIgnoreCase(avatarId)
            ? avatarName
            : (avatarName + "  ·  " + avatarId);
        graphics.text(
            font,
            Component.literal(ShyneTheme.ellipsize(font, avatarSubtitle, panelWidth - 28)),
            panelX + 14,
            panelY + 22,
            ShyneTheme.TEXT_SECONDARY,
            false
        );

        if (displayedPermissions.isEmpty()) {
            ShyneTheme.drawCard(graphics, listX, listTop, listWidth, 38, false, false);
            String noneMsg = ShyneTheme.ellipsize(font, Component.translatable("screen.shyne_core.permissions.none").getString(), listWidth - 20);
            graphics.text(font, Component.literal("✓ " + noneMsg), listX + 12, listTop + 14, ShyneTheme.SUCCESS, false);
        } else {
            int cardWidth = listWidth - (maxScroll > 0 ? 7 : 0);
            int end = Math.min(displayedPermissions.size(), scrollOffset + visibleRows);
            for (int i = scrollOffset; i < end; i++) {
                AvatarPermission perm = displayedPermissions.get(i);
                boolean requested = requestedPermissions.contains(perm);
                boolean isAllowed = requested && approved.contains(perm);
                int y = listTop + (i - scrollOffset) * ROW_HEIGHT;
                boolean hovered = mouseX >= listX && mouseX < listX + cardWidth && mouseY >= y && mouseY < y + CARD_HEIGHT;

                ShyneTheme.drawCard(graphics, listX, y, cardWidth, CARD_HEIGHT, hovered, isAllowed);

                int textMax = Math.max(60, cardWidth - 102);
                String name = ShyneTheme.ellipsize(font, Component.translatable(perm.translationKey()).getString(), textMax - 74);
                int titleColor = !requested ? ShyneTheme.TEXT_DIM : ShyneTheme.TEXT_PRIMARY;
                graphics.text(font, Component.literal(name), listX + 10, y + 5, titleColor, false);

                // Standard vs Sensitive badge
                String badge = Component.translatable(perm.dangerous()
                    ? "screen.shyne_core.permissions.sensitive_badge"
                    : "screen.shyne_core.permissions.standard_badge").getString();
                int badgeX = listX + 14 + font.width(name);
                if (badgeX + font.width(badge) + 12 < listX + cardWidth - 94) {
                    ShyneTheme.drawBadge(
                        graphics,
                        font,
                        badge,
                        badgeX,
                        y + 3,
                        perm.dangerous() ? ShyneTheme.WARNING_SOFT : ShyneTheme.ACCENT_SOFT,
                        perm.dangerous() ? 0x99FFC66D : 0x883DD9E8,
                        perm.dangerous() ? ShyneTheme.WARNING : ShyneTheme.ACCENT_BRIGHT
                    );
                }

                String desc = ShyneTheme.ellipsize(font, Component.translatable(perm.descriptionKey()).getString(), textMax);
                graphics.text(font, Component.literal(desc), listX + 10, y + 19, !requested ? ShyneTheme.TEXT_DIM : ShyneTheme.TEXT_SECONDARY, false);
            }

            if (maxScroll > 0) {
                ShyneTheme.drawScrollbar(
                    graphics,
                    listX + listWidth - 3,
                    listTop,
                    listBottom,
                    scrollOffset,
                    maxScroll,
                    visibleRows,
                    displayedPermissions.size()
                );
            }
        }

        int footerDividerY = panelY + panelHeight - 34;
        graphics.fill(panelX + 14, footerDividerY, panelX + panelWidth - 14, footerDividerY + 1, 0x333B5270);
        String summary = approved.size() + " / " + requestedPermissions.size() + " "
            + Component.translatable("screen.shyne_core.permissions.allowed").getString().toLowerCase(java.util.Locale.ROOT);
        graphics.text(font, Component.literal(summary), panelX + 14, panelY + panelHeight - 22, ShyneTheme.TEXT_SECONDARY, false);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && maxScroll > 0) {
            int next = Math.max(0, Math.min(maxScroll, scrollOffset + (verticalAmount < 0 ? 1 : -1)));
            if (next != scrollOffset) {
                scrollOffset = next;
                rebuildRowToggles();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

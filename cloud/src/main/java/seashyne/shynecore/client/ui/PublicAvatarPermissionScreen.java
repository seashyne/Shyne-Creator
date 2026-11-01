package seashyne.shynecore.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.ShyneCloudClient;
import seashyne.shynecore.client.config.ShyneClientSettings;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Explicit capability grant shown before a Public Avatar is opened.
 * Grants are scoped to share id + package hash so an update requires review.
 */
public final class PublicAvatarPermissionScreen extends Screen {
    private static final int ROW_HEIGHT = 36;
    private static final int CARD_HEIGHT = 32;

    private final Screen parent;
    private final ShyneCloudClient.CloudAvatar avatar;
    private final Runnable onApproved;
    private final EnumSet<AvatarPermission> approved = EnumSet.noneOf(AvatarPermission.class);

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

    private List<AvatarPermission> requestedList = List.of();
    private final List<AbstractWidget> rowToggles = new ArrayList<>();

    public PublicAvatarPermissionScreen(
        Screen parent,
        ShyneCloudClient.CloudAvatar avatar,
        Runnable onApproved
    ) {
        super(Component.translatable("screen.shyne_core.permissions.title"));
        this.parent = parent;
        this.avatar = avatar;
        this.onApproved = onApproved;

        if (ShyneClientSettings.hasPublicPermissionDecision(avatar.shareId(), avatar.packageHash())) {
            approved.addAll(ShyneClientSettings.approvedPublicPermissions(avatar.shareId(), avatar.packageHash()));
        } else {
            avatar.permissions().stream().filter(permission -> !permission.dangerous()).forEach(approved::add);
        }
        approved.retainAll(avatar.permissions());
    }

    @Override
    protected void init() {
        clearWidgets();
        rowToggles.clear();

        panelWidth = Math.min(540, Math.max(300, width - 20));
        panelHeight = Math.min(350, Math.max(230, height - 16));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        listX = panelX + 14;
        listWidth = panelWidth - 28;
        listTop = panelY + 62;
        int footerY = panelY + panelHeight - 28;
        listBottom = footerY - 8;
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);

        List<AvatarPermission> ordered = new ArrayList<>();
        for (AvatarPermission p : AvatarPermission.values()) {
            if (avatar.permissions().contains(p) && !p.dangerous()) ordered.add(p);
        }
        for (AvatarPermission p : AvatarPermission.values()) {
            if (avatar.permissions().contains(p) && p.dangerous()) ordered.add(p);
        }
        requestedList = List.copyOf(ordered);

        addRenderableWidget(ShyneButton.primary(Component.translatable("screen.shyne_core.permissions.allow"), button -> approve())
            .bounds(listX, footerY, Math.min(186, listWidth / 2), 20));
        addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.nav.settings"), button -> {
            if (minecraft != null) minecraft.gui.setScreen(new ShyneSettingsScreen(this));
        }).bounds(panelX + panelWidth - 178, footerY, 80, 20));
        addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.permissions.cancel"), button -> onClose())
            .bounds(panelX + panelWidth - 94, footerY, 80, 20));

        rebuildRowToggles();
    }

    private void rebuildRowToggles() {
        for (AbstractWidget widget : rowToggles) {
            removeWidget(widget);
        }
        rowToggles.clear();

        maxScroll = Math.max(0, requestedList.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int toggleW = 84;
        int cardWidth = listWidth - (maxScroll > 0 ? 7 : 0);
        int end = Math.min(requestedList.size(), scrollOffset + visibleRows);

        for (int i = scrollOffset; i < end; i++) {
            AvatarPermission perm = requestedList.get(i);
            int rowY = listTop + (i - scrollOffset) * ROW_HEIGHT;
            Component label = Component.translatable(approved.contains(perm)
                ? "screen.shyne_core.permissions.allowed"
                : "screen.shyne_core.permissions.blocked");

            ShyneButton toggle = ShyneButton.toggle(label, () -> approved.contains(perm), btn -> {
                if (!approved.remove(perm)) approved.add(perm);
                rebuildRowToggles();
            }).bounds(listX + cardWidth - toggleW - 6, rowY + 6, toggleW, 20)
                .tooltipText(Component.translatable(perm.descriptionKey()));

            rowToggles.add(toggle);
            addRenderableWidget(toggle);
        }
    }

    private void approve() {
        Set<AvatarPermission> decision = Set.copyOf(approved);
        ShyneClientSettings.decidePublicPermissions(avatar.shareId(), avatar.packageHash(), decision);
        if (minecraft != null) minecraft.gui.setScreen(parent);
        onApproved.run();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ShyneTheme.drawBackdrop(graphics, width, height);
        ShyneTheme.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight, 52);

        graphics.text(
            font,
            Component.literal("✦ ").append(Component.translatable("screen.shyne_core.permissions.title")),
            panelX + 14,
            panelY + 9,
            ShyneTheme.TEXT_PRIMARY,
            true
        );
        String sub = ShyneTheme.ellipsize(
            font,
            Component.translatable("screen.shyne_core.permissions.subtitle", avatar.name(), avatar.ownerName()).getString(),
            panelWidth - 28
        );
        graphics.text(font, Component.literal(sub), panelX + 14, panelY + 23, ShyneTheme.TEXT_SECONDARY, false);
        String scope = ShyneTheme.ellipsize(
            font,
            Component.translatable("screen.shyne_core.permissions.scope").getString(),
            panelWidth - 28
        );
        graphics.text(font, Component.literal(scope), panelX + 14, panelY + 36, ShyneTheme.TEXT_DIM, false);

        if (requestedList.isEmpty()) {
            ShyneTheme.drawCard(graphics, listX, listTop, listWidth, 38, false, false);
            graphics.text(font, Component.translatable("screen.shyne_core.permissions.none"), listX + 12, listTop + 14, ShyneTheme.SUCCESS, false);
        } else {
            int cardWidth = listWidth - (maxScroll > 0 ? 7 : 0);
            int end = Math.min(requestedList.size(), scrollOffset + visibleRows);
            for (int i = scrollOffset; i < end; i++) {
                AvatarPermission perm = requestedList.get(i);
                boolean isAllowed = approved.contains(perm);
                int y = listTop + (i - scrollOffset) * ROW_HEIGHT;
                boolean hovered = mouseX >= listX && mouseX < listX + cardWidth && mouseY >= y && mouseY < y + CARD_HEIGHT;

                ShyneTheme.drawCard(graphics, listX, y, cardWidth, CARD_HEIGHT, hovered, isAllowed);
                int textMax = Math.max(60, cardWidth - 102);
                String name = ShyneTheme.ellipsize(font, Component.translatable(perm.translationKey()).getString(), textMax - 74);
                graphics.text(font, Component.literal(name), listX + 10, y + 5, ShyneTheme.TEXT_PRIMARY, false);

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
                graphics.text(font, Component.literal(desc), listX + 10, y + 19, ShyneTheme.TEXT_SECONDARY, false);
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
                    requestedList.size()
                );
            }
        }

        int footerDividerY = panelY + panelHeight - 34;
        graphics.fill(panelX + 14, footerDividerY, panelX + panelWidth - 14, footerDividerY + 1, 0x333B5270);
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

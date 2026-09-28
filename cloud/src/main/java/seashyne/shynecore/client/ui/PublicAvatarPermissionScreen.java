package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.ShyneCloudClient;
import seashyne.shynecore.client.config.ShyneClientSettings;

import java.util.EnumSet;
import java.util.Set;

/**
 * Explicit capability grant shown before a Public Avatar is opened.
 * Grants are scoped to share id + package hash so an update requires review.
 */
public final class PublicAvatarPermissionScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 300;

    private final Screen parent;
    private final ShyneCloudClient.CloudAvatar avatar;
    private final Runnable onApproved;
    private final EnumSet<AvatarPermission> approved = EnumSet.noneOf(AvatarPermission.class);
    private int panelX;
    private int panelY;

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
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = Math.max(10, (height - PANEL_HEIGHT) / 2);
        int y = panelY + 82;
        for (AvatarPermission permission : AvatarPermission.values()) {
            if (!avatar.permissions().contains(permission)) continue;
            addRenderableWidget(Button.builder(permissionLabel(permission), button -> {
                if (!approved.remove(permission)) approved.add(permission);
                rebuildWidgets();
            }).tooltip(Tooltip.create(Component.translatable(permission.descriptionKey())))
                .bounds(panelX + 18, y, PANEL_WIDTH - 36, 20).build());
            y += 25;
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.permissions.allow"), button -> approve())
            .bounds(panelX + 18, panelY + PANEL_HEIGHT - 34, 180, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.settings"), button -> {
            if (minecraft != null) minecraft.gui.setScreen(new ShyneSettingsScreen(this));
        }).bounds(panelX + 204, panelY + PANEL_HEIGHT - 34, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.permissions.cancel"), button -> onClose())
            .bounds(panelX + 310, panelY + PANEL_HEIGHT - 34, 102, 20).build());
    }

    private Component permissionLabel(AvatarPermission permission) {
        String marker = approved.contains(permission) ? "✓ " : "□ ";
        Component name = Component.translatable(permission.translationKey());
        if (permission.dangerous()) {
            return Component.literal(marker).append(Component.literal("⚠ ").withStyle(ChatFormatting.GOLD)).append(name);
        }
        return Component.literal(marker).append(name);
    }

    private void approve() {
        Set<AvatarPermission> decision = Set.copyOf(approved);
        ShyneClientSettings.decidePublicPermissions(avatar.shareId(), avatar.packageHash(), decision);
        if (minecraft != null) minecraft.gui.setScreen(parent);
        onApproved.run();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB0030918);
        graphics.fillGradient(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF20A1630, 0xF2071026);
        graphics.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF22D7E8);
        graphics.text(font, Component.translatable("screen.shyne_core.permissions.title").withStyle(ChatFormatting.AQUA),
            panelX + 18, panelY + 16, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("screen.shyne_core.permissions.subtitle", avatar.name(), avatar.ownerName()),
            panelX + 18, panelY + 34, 0xFFB7C6DB, false);
        graphics.text(font, Component.translatable("screen.shyne_core.permissions.scope"),
            panelX + 18, panelY + 50, 0xFF8195B4, false);
        if (avatar.permissions().isEmpty()) {
            graphics.text(font, Component.translatable("screen.shyne_core.permissions.none"),
                panelX + 18, panelY + 90, 0xFF79D8B2, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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

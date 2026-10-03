package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
 * Granular avatar capability review and approval screen.
 * Allows inspecting, approving, or restricting individual features (particle, sound,
 * nameplate, custom camera, dangerous chat/tab alterations, network channels) per avatar.
 */
public final class AvatarPermissionScreen extends Screen {
    private static final int PANEL_WIDTH = 540;
    private static final int PANEL_HEIGHT = 340;

    private final Screen parent;
    private final String avatarId;
    private final String avatarName;
    private final Set<AvatarPermission> requestedPermissions;
    private final EnumSet<AvatarPermission> approved = EnumSet.noneOf(AvatarPermission.class);
    private final Runnable onApplied;

    private int panelX;
    private int panelY;

    public AvatarPermissionScreen(Screen parent, String avatarId, String avatarName, Set<AvatarPermission> requestedPermissions) {
        this(parent, avatarId, avatarName, requestedPermissions, null);
    }

    public AvatarPermissionScreen(Screen parent, String avatarId, String avatarName, Set<AvatarPermission> requestedPermissions, Runnable onApplied) {
        super(Component.translatable("screen.shyne_core.permissions.title"));
        this.parent = parent;
        this.avatarId = avatarId == null ? "" : avatarId;
        this.avatarName = avatarName == null || avatarName.isBlank() ? this.avatarId : avatarName;
        this.requestedPermissions = requestedPermissions == null ? Set.of() : Set.copyOf(requestedPermissions);
        this.onApplied = onApplied;

        if (ShyneClientSettings.hasAvatarPermissionDecision(this.avatarId)) {
            approved.addAll(ShyneClientSettings.approvedAvatarPermissions(this.avatarId, this.requestedPermissions));
        } else {
            // By default, grant all requested permissions
            approved.addAll(this.requestedPermissions);
        }
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = Math.max(8, (height - PANEL_HEIGHT) / 2);

        // Quick action buttons at top
        int buttonWidth = 100;
        int actionY = panelY + 44;
        addRenderableWidget(Button.builder(Component.literal("Safe Only"), button -> {
            approved.clear();
            for (AvatarPermission p : requestedPermissions) {
                if (!p.dangerous()) approved.add(p);
            }
            rebuildWidgets();
        }).tooltip(Tooltip.create(Component.literal("Allow all safe features; deny sensitive ones")))
            .bounds(panelX + 16, actionY, buttonWidth, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Allow All"), button -> {
            approved.addAll(requestedPermissions);
            rebuildWidgets();
        }).tooltip(Tooltip.create(Component.literal("Grant all capabilities requested by this avatar")))
            .bounds(panelX + 122, actionY, buttonWidth, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Deny All"), button -> {
            approved.clear();
            rebuildWidgets();
        }).tooltip(Tooltip.create(Component.literal("Disable all optional capabilities for this avatar")))
            .bounds(panelX + 228, actionY, buttonWidth, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            ShyneClientSettings.resetAvatarPermissions(avatarId);
            approved.clear();
            approved.addAll(requestedPermissions);
            rebuildWidgets();
        }).tooltip(Tooltip.create(Component.literal("Reset to manifest default")))
            .bounds(panelX + 334, actionY, 80, 18).build());

        // Split into Safe (left) and Dangerous (right) columns
        List<AvatarPermission> safeList = new ArrayList<>();
        List<AvatarPermission> dangerousList = new ArrayList<>();
        for (AvatarPermission p : AvatarPermission.values()) {
            if (p.dangerous()) dangerousList.add(p);
            else safeList.add(p);
        }

        int colWidth = (PANEL_WIDTH - 48) / 2;
        int leftX = panelX + 16;
        int rightX = panelX + 24 + colWidth;

        // Render toggles for Safe permissions
        int safeY = panelY + 84;
        for (AvatarPermission p : safeList) {
            boolean requested = requestedPermissions.contains(p);
            Button toggle = Button.builder(permissionToggleLabel(p, requested), btn -> {
                if (!approved.remove(p)) approved.add(p);
                rebuildWidgets();
            }).tooltip(Tooltip.create(Component.translatable(p.descriptionKey())))
                .bounds(leftX, safeY, colWidth, 20).build();
            if (!requested) toggle.active = false;
            addRenderableWidget(toggle);
            safeY += 23;
        }

        // Render toggles for Sensitive permissions
        int dangY = panelY + 84;
        for (AvatarPermission p : dangerousList) {
            boolean requested = requestedPermissions.contains(p);
            Button toggle = Button.builder(permissionToggleLabel(p, requested), btn -> {
                if (!approved.remove(p)) approved.add(p);
                rebuildWidgets();
            }).tooltip(Tooltip.create(Component.translatable(p.descriptionKey())))
                .bounds(rightX, dangY, colWidth, 20).build();
            if (!requested) toggle.active = false;
            addRenderableWidget(toggle);
            dangY += 23;
        }

        // Footer buttons
        int footerY = panelY + PANEL_HEIGHT - 28;
        addRenderableWidget(Button.builder(Component.literal("Save & Apply"), button -> applyDecision())
            .bounds(panelX + PANEL_WIDTH - 216, footerY, 110, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(panelX + PANEL_WIDTH - 100, footerY, 84, 20).build());
    }

    private Component permissionToggleLabel(AvatarPermission permission, boolean requested) {
        if (!requested) {
            return Component.literal("- ").append(Component.translatable(permission.translationKey())).withStyle(ChatFormatting.DARK_GRAY);
        }
        boolean active = approved.contains(permission);
        String box = active ? "[✓] " : "[  ] ";
        Component label = Component.literal(box).append(Component.translatable(permission.translationKey()));
        if (permission.dangerous()) {
            return Component.literal(box).append(Component.literal("⚠ ").withStyle(ChatFormatting.GOLD))
                .append(Component.translatable(permission.translationKey()).copy().withStyle(active ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
        return label.copy().withStyle(active ? ChatFormatting.WHITE : ChatFormatting.GRAY);
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
        graphics.fill(0, 0, width, height, 0xB0030918);
        graphics.fillGradient(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF20A1630, 0xF2071026);
        graphics.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF22D7E8);

        graphics.text(font, Component.translatable("screen.shyne_core.permissions.title").copy().withStyle(ChatFormatting.AQUA),
            panelX + 16, panelY + 12, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Avatar: " + avatarName + " (" + avatarId + ")"),
            panelX + 16, panelY + 28, 0xFF91A7C6, false);

        int colWidth = (PANEL_WIDTH - 48) / 2;
        graphics.text(font, Component.literal("Standard Features").withStyle(ChatFormatting.AQUA),
            panelX + 16, panelY + 70, 0xFFFFFFFF, false);
        graphics.text(font, Component.literal("Sensitive / Protected Features").withStyle(ChatFormatting.GOLD),
            panelX + 24 + colWidth, panelY + 70, 0xFFFFFFFF, false);

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

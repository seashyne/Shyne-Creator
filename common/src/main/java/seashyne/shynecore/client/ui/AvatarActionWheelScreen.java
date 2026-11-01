package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarAction;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.render.AvatarActionTextureResolver;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Unified 8-slot radial Action Wheel for Figura & Shyne 2.0 avatars plus Shyne Studio controls.
 *
 * <p>Supports live {@code action_wheel:setPage(...)} sub-menu transitions without closing,
 * custom {@code .bbmodel}/{@code .png} action sprites, {@code toggleTitle}/{@code toggleTexture}/{@code toggleColor},
 * sparse 1..8 Figura slot assignments, polar 8-wedge hover detection, {@code onRightClick},
 * {@code onScroll}, and both Hold-Key-and-Release and Quick-Tap click workflows.</p>
 */
public class AvatarActionWheelScreen extends Screen {
    private static final int SLOTS_PER_PAGE = 8;
    private static final long HOLD_RELEASE_THRESHOLD_MS = 210L;

    private final Screen parent;
    private final int triggerKeyCode;
    private final long openedAtMillis;
    private boolean initialTriggerReleaseHandled;

    private boolean controlsMode;
    private int nativePageIndex;
    private int overflowSliceIndex;
    private String avatarName = "";
    private int hoveredSlot = -1;
    private final AvatarAction[] slotActions = new AvatarAction[SLOTS_PER_PAGE];
    private String currentPageTitle = "";
    private int currentSliceCount = 1;
    private final Deque<String> pageHistory = new ArrayDeque<>();

    private ShyneButton backPageBtn;
    private ShyneButton modeToggleBtn;

    public AvatarActionWheelScreen() {
        this(null, -1);
    }

    public AvatarActionWheelScreen(Screen parent) {
        this(parent, -1);
    }

    public AvatarActionWheelScreen(Screen parent, int triggerKeyCode) {
        super(Component.translatable("screen.shyne_core.action_wheel.title"));
        this.parent = parent;
        this.triggerKeyCode = triggerKeyCode;
        this.openedAtMillis = System.currentTimeMillis();
        this.initialTriggerReleaseHandled = triggerKeyCode == -1;
    }

    @Override
    protected void init() {
        clearWidgets();
        AvatarState state = AvatarRuntime.active();
        boolean hasAvatarActions = state != null && !state.actionsByPage().isEmpty();
        if (!hasAvatarActions) {
            controlsMode = true;
        }

        resolveAvatarDisplayName(state);

        backPageBtn = ShyneButton.secondary(
            Component.translatable("screen.shyne_core.action_wheel.back"),
            btn -> navigateBackPage()
        ).bounds(10, 8, 76, 18);
        addRenderableWidget(backPageBtn);

        modeToggleBtn = ShyneButton.secondary(modeButtonLabel(), btn -> toggleMode())
            .bounds(this.width - 114, 8, 104, 18)
            .tooltipText(Component.translatable("screen.shyne_core.action_wheel.settings_hint"));
        addRenderableWidget(modeToggleBtn);

        refreshActiveSlots();
    }

    private Component modeButtonLabel() {
        return Component.translatable(controlsMode
            ? "screen.shyne_core.action_wheel.mode_controls"
            : "screen.shyne_core.action_wheel.mode_avatar");
    }

    private void toggleMode() {
        AvatarState state = AvatarRuntime.active();
        boolean hasAvatarActions = state != null && !state.actionsByPage().isEmpty();
        if (!hasAvatarActions) {
            controlsMode = true;
            return;
        }
        controlsMode = !controlsMode;
        overflowSliceIndex = 0;
        hoveredSlot = -1;
        if (modeToggleBtn != null) {
            modeToggleBtn.setMessage(modeButtonLabel());
        }
        playUiScrollSound();
        refreshActiveSlots();
    }

    private void resolveAvatarDisplayName(AvatarState state) {
        if (state == null) {
            avatarName = Component.translatable("screen.shyne_core.palette.no_avatar").getString();
            return;
        }
        avatarName = AvatarRuntime.catalog().stream()
            .filter(entry -> entry.id().equalsIgnoreCase(state.avatarId()))
            .map(entry -> entry.name().isBlank() ? state.avatarId() : entry.name())
            .findFirst()
            .orElse(state.avatarId());
    }

    private void refreshActiveSlots() {
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            slotActions[i] = null;
        }

        AvatarState state = AvatarRuntime.active();
        resolveAvatarDisplayName(state);

        if (controlsMode || state == null || state.actionsByPage().isEmpty()) {
            controlsMode = true;
            List<AvatarAction> sys = systemActions(state != null);
            for (int i = 0; i < Math.min(SLOTS_PER_PAGE, sys.size()); i++) {
                slotActions[i] = sys.get(i);
            }
            currentPageTitle = Component.translatable("screen.shyne_core.action_wheel.controls").getString();
            currentSliceCount = 1;
            if (backPageBtn != null) backPageBtn.visible = false;
            if (modeToggleBtn != null) {
                modeToggleBtn.setMessage(modeButtonLabel());
                modeToggleBtn.activeState(state != null && !state.actionsByPage().isEmpty());
            }
            return;
        }

        Map<String, List<AvatarAction>> byPage = state.actionsByPage();
        String explicitFiguraPage = state.activeActionPage();
        List<AvatarAction> pageActions;

        if (!explicitFiguraPage.isBlank()) {
            // Figura explicit page routing (action_wheel:setPage)
            pageActions = findPageActionsIgnoreCase(byPage, explicitFiguraPage);
            currentPageTitle = formatPageTitle(explicitFiguraPage);
            layoutActionsIntoSlots(pageActions);
            if (backPageBtn != null) {
                backPageBtn.visible = !pageHistory.isEmpty();
            }
        } else {
            // Native Shyne multi-page scroll mode
            List<Map.Entry<String, List<AvatarAction>>> entries = new ArrayList<>(byPage.entrySet());
            if (entries.isEmpty()) {
                currentPageTitle = "";
                currentSliceCount = 1;
            } else {
                nativePageIndex = Math.floorMod(nativePageIndex, entries.size());
                Map.Entry<String, List<AvatarAction>> chosen = entries.get(nativePageIndex);
                currentPageTitle = formatPageTitle(chosen.getKey());
                layoutActionsIntoSlots(chosen.getValue());
                currentSliceCount = Math.max(currentSliceCount, entries.size());
            }
            if (backPageBtn != null) backPageBtn.visible = false;
        }

        if (modeToggleBtn != null) {
            modeToggleBtn.setMessage(modeButtonLabel());
            modeToggleBtn.activeState(true);
        }
    }

    private List<AvatarAction> findPageActionsIgnoreCase(Map<String, List<AvatarAction>> byPage, String targetPage) {
        List<AvatarAction> exact = byPage.get(targetPage);
        if (exact != null) return exact;
        for (Map.Entry<String, List<AvatarAction>> entry : byPage.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(targetPage)) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    private String formatPageTitle(String rawPage) {
        if (rawPage == null || rawPage.isBlank() || "main".equalsIgnoreCase(rawPage)) {
            return Component.translatable("screen.shyne_core.palette.page.main").getString();
        }
        if (rawPage.matches("(?i)page_\\d+")) {
            return "Page " + rawPage.substring(5);
        }
        return rawPage;
    }

    private void layoutActionsIntoSlots(List<AvatarAction> actions) {
        if (actions == null || actions.isEmpty()) {
            currentSliceCount = 1;
            overflowSliceIndex = 0;
            return;
        }

        boolean hasExplicitSlots = actions.stream().anyMatch(a -> a.slotIndex() >= 0 && a.slotIndex() < SLOTS_PER_PAGE);
        if (hasExplicitSlots && actions.size() <= SLOTS_PER_PAGE) {
            currentSliceCount = 1;
            overflowSliceIndex = 0;
            List<AvatarAction> unslotted = new ArrayList<>();
            for (AvatarAction action : actions) {
                int idx = action.slotIndex();
                if (idx >= 0 && idx < SLOTS_PER_PAGE && slotActions[idx] == null) {
                    slotActions[idx] = action;
                } else {
                    unslotted.add(action);
                }
            }
            int nextFree = 0;
            for (AvatarAction extra : unslotted) {
                while (nextFree < SLOTS_PER_PAGE && slotActions[nextFree] != null) nextFree++;
                if (nextFree >= SLOTS_PER_PAGE) break;
                slotActions[nextFree++] = extra;
            }
            return;
        }

        currentSliceCount = Math.max(1, (actions.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        overflowSliceIndex = Math.floorMod(overflowSliceIndex, currentSliceCount);
        int start = overflowSliceIndex * SLOTS_PER_PAGE;
        int end = Math.min(actions.size(), start + SLOTS_PER_PAGE);
        for (int i = start; i < end; i++) {
            slotActions[i - start] = actions.get(i);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        refreshActiveSlots();
        graphics.fill(0, 0, this.width, this.height, 0x92040812);

        int cx = this.width / 2;
        int cy = this.height / 2 + 2;
        int wheelRadius = wheelRadius();

        updateHoveredSlot(cx, cy, wheelRadius, mouseX, mouseY);

        drawCenterHub(graphics, cx, cy, mouseX, mouseY);
        drawRadialSlots(graphics, cx, cy, wheelRadius);
        drawHeaderAndFooter(graphics, cx, cy, wheelRadius);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private int wheelRadius() {
        return Math.min(102, Math.max(54, (this.height - 88) / 2));
    }

    private void updateHoveredSlot(int cx, int cy, int radius, int mouseX, int mouseY) {
        // Avoid stealing hover when pointing at top bar buttons
        if (mouseY < 30 && (mouseX < 92 || mouseX > this.width - 120)) {
            hoveredSlot = -1;
            return;
        }
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.hypot(dx, dy);
        if (dist < 32.0 || dist > radius + 56.0) {
            hoveredSlot = -1;
            return;
        }
        // True polar 8-wedge detection starting from top (-PI/2) clockwise
        double angle = Math.atan2(dy, dx) + (Math.PI / 2.0) + (Math.PI / SLOTS_PER_PAGE);
        while (angle < 0) angle += Math.PI * 2.0;
        while (angle >= Math.PI * 2.0) angle -= Math.PI * 2.0;
        int sector = (int) Math.floor(angle / (2.0 * Math.PI / SLOTS_PER_PAGE)) % SLOTS_PER_PAGE;
        hoveredSlot = sector;
    }

    private void drawCenterHub(GuiGraphicsExtractor graphics, int cx, int cy, int mouseX, int mouseY) {
        int hubRadius = 34;
        boolean anyHovered = hoveredSlot >= 0 && slotActions[hoveredSlot] != null;
        graphics.fill(cx - hubRadius - 2, cy - hubRadius - 2, cx + hubRadius + 2, cy + hubRadius + 2, 0x55000000);
        graphics.fill(cx - hubRadius, cy - hubRadius, cx + hubRadius, cy + hubRadius, ShyneTheme.PANEL_BG);
        graphics.outline(
            cx - hubRadius,
            cy - hubRadius,
            hubRadius * 2,
            hubRadius * 2,
            anyHovered ? ShyneTheme.ACCENT_BRIGHT : ShyneTheme.BORDER_ACCENT
        );

        boolean showAvatar = ShyneClientSettings.actionWheelCenterAvatar
            && this.minecraft != null && this.minecraft.player != null;
        if (showAvatar) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics, cx - 30, cy - 31, cx + 30, cy + 31,
                26, 0.0625F, mouseX, mouseY, this.minecraft.player
            );
        } else {
            String shortName = ShyneTheme.ellipsize(this.font, avatarName, 60);
            graphics.text(this.font, Component.literal(shortName), cx - this.font.width(shortName) / 2, cy - 4, ShyneTheme.ACCENT, true);
        }
    }

    private void drawRadialSlots(GuiGraphicsExtractor graphics, int cx, int cy, int radius) {
        int cardW = 72;
        int cardH = 36;
        AvatarState state = AvatarRuntime.active();
        BbModelDefinition activeModel = AvatarRuntime.activeModel();

        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            double angle = -Math.PI / 2.0 + (i * (2.0 * Math.PI / SLOTS_PER_PAGE));
            int slotX = cx + (int) Math.round(Math.cos(angle) * radius);
            int slotY = cy + (int) Math.round(Math.sin(angle) * radius);
            int left = slotX - cardW / 2;
            int top = slotY - cardH / 2;

            AvatarAction action = slotActions[i];
            boolean hasAction = action != null;
            boolean hovered = (i == hoveredSlot);

            int accentColor = resolveSlotAccentColor(action, hovered);
            int bg = !hasAction
                ? (hovered ? 0xA8101A2C : 0x780B1322)
                : (hovered ? ShyneTheme.CARD_HOVER : ShyneTheme.CARD_BG);
            int border = !hasAction
                ? (hovered ? 0x553B5270 : 0x263B5270)
                : (hovered ? ShyneTheme.ACCENT_BRIGHT : accentColor);

            if (hasAction) {
                graphics.fill(left + 2, top + 2, left + cardW + 2, top + cardH + 2, 0x50000000);
            }
            graphics.fill(left, top, left + cardW, top + cardH, bg);
            graphics.outline(left, top, cardW, cardH, border);

            if (hasAction) {
                // Left color/toggle strip using the action's Figura segment tint
                int stripColor = resolveStripColor(action, hovered);
                graphics.fill(left + 1, top + 1, left + 4, top + cardH - 1, stripColor);

                boolean hasLabel = !action.title().isBlank();
                int iconX = hasLabel ? left + 7 : left + (cardW - 18) / 2;
                int iconY = top + (cardH - 18) / 2;
                renderActionIcon(graphics, state, activeModel, action, iconX, iconY);

                if (hasLabel) {
                    int maxLabelWidth = cardW - 30 - (action.isToggle() ? 6 : 0);
                    String label = ShyneTheme.ellipsize(this.font, action.title(), maxLabelWidth);
                    int textColor = hovered ? 0xFFFFFFFF : ShyneTheme.TEXT_PRIMARY;
                    graphics.text(this.font, Component.literal(label), left + 27, top + (cardH - 8) / 2, textColor, true);
                }

                if (action.isToggle()) {
                    int dotColor = action.isToggled() ? ShyneTheme.SUCCESS : 0xFF5A6E87;
                    graphics.fill(left + cardW - 7, top + 4, left + cardW - 3, top + 8, dotColor);
                }
            } else {
                String slotNum = Integer.toString(i + 1);
                graphics.text(this.font, Component.literal(slotNum), left + (cardW - this.font.width(slotNum)) / 2, top + (cardH - 8) / 2, 0x306D829E, false);
            }
        }
    }

    private int resolveSlotAccentColor(AvatarAction action, boolean hovered) {
        if (action == null) return 0x263B5270;
        if (hovered && action.hoverColor() != null) {
            return ensureVisibleBorder(action.hoverColor());
        }
        if (action.isToggle() && action.isToggled()) {
            return action.toggleColor() != null ? ensureVisibleBorder(action.toggleColor()) : 0xCC4AE39C;
        }
        if (action.color() != null) {
            return ensureVisibleBorder(action.color());
        }
        return 0x773DD9E8;
    }

    private int resolveStripColor(AvatarAction action, boolean hovered) {
        if (action.isToggle()) {
            if (action.isToggled()) {
                return action.toggleColor() != null && (action.toggleColor() & 0x00FFFFFF) != 0
                    ? (0xFF000000 | (action.toggleColor() & 0x00FFFFFF))
                    : ShyneTheme.SUCCESS;
            }
            return 0xFF475B73;
        }
        if (hovered && action.hoverColor() != null) {
            return 0xFF000000 | (action.hoverColor() & 0x00FFFFFF);
        }
        if (action.color() != null && (action.color() & 0x00FFFFFF) != 0) {
            return 0xFF000000 | (action.color() & 0x00FFFFFF);
        }
        return hovered ? ShyneTheme.ACCENT_BRIGHT : ShyneTheme.ACCENT;
    }

    private int ensureVisibleBorder(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        if (r + g + b < 75) {
            // Very dark Figura segment colors (e.g. toggleColor(0,0,0)) get a subtle slate border instead of vanishing
            return 0x884A607A;
        }
        return 0xCC000000 | (r << 16) | (g << 8) | b;
    }

    private void drawHeaderAndFooter(GuiGraphicsExtractor graphics, int cx, int cy, int radius) {
        AvatarAction selected = hoveredSlot >= 0 ? slotActions[hoveredSlot] : null;
        String heading = selected == null
            ? avatarName
            : (selected.title().isBlank() ? selected.displayTitle() : selected.title());

        StringBuilder detailBuilder = new StringBuilder();
        if (selected == null) {
            detailBuilder.append(currentPageTitle);
            if (!pageHistory.isEmpty()) {
                detailBuilder.append("  ·  ").append(Component.translatable("screen.shyne_core.action_wheel.right_click_back").getString());
            }
        } else {
            if (selected.isToggle()) {
                detailBuilder.append(selected.isToggled() ? "● ON" : "○ OFF");
            }
            if (!selected.description().isBlank()) {
                if (!detailBuilder.isEmpty()) detailBuilder.append("  ·  ");
                detailBuilder.append(selected.description());
            }
            if (selected.hasSecondaryCallback()) {
                if (!detailBuilder.isEmpty()) detailBuilder.append("  ·  ");
                detailBuilder.append("Right-click");
            }
            if (selected.hasScrollCallback()) {
                if (!detailBuilder.isEmpty()) detailBuilder.append("  ·  ");
                detailBuilder.append("Scroll ↕");
            }
            if (selected.localOnly()) {
                if (!detailBuilder.isEmpty()) detailBuilder.append("  ·  ");
                detailBuilder.append(Component.translatable("screen.shyne_core.palette.local_only").getString());
            }
            if (detailBuilder.isEmpty()) {
                detailBuilder.append("Slot ").append(hoveredSlot + 1);
            }
        }

        int topY = Math.max(6, cy - radius - 42);
        String fittedHeading = ShyneTheme.ellipsize(this.font, heading, Math.max(80, this.width - 230));
        String fittedDetail = ShyneTheme.ellipsize(this.font, detailBuilder.toString(), Math.max(100, this.width - 140));
        graphics.text(this.font, Component.literal(fittedHeading), cx - this.font.width(fittedHeading) / 2, topY, ShyneTheme.ACCENT_BRIGHT, true);
        graphics.text(this.font, Component.literal(fittedDetail), cx - this.font.width(fittedDetail) / 2, topY + 11, ShyneTheme.TEXT_SECONDARY, false);

        int footerY = Math.min(this.height - 14, cy + radius + 24);
        String footerText = currentPageTitle;
        if (!controlsMode && currentSliceCount > 1) {
            footerText = "Scroll ↕  •  " + currentPageTitle;
        }
        String fittedFooter = ShyneTheme.ellipsize(this.font, footerText, this.width - 24);
        graphics.text(this.font, Component.literal(fittedFooter), cx - this.font.width(fittedFooter) / 2, footerY, ShyneTheme.TEXT_PRIMARY, true);
    }

    private void renderActionIcon(
        GuiGraphicsExtractor graphics,
        AvatarState state,
        BbModelDefinition activeModel,
        AvatarAction action,
        int x,
        int y
    ) {
        // 1. Custom Figura / Shyne texture sprite (.bbmodel embedded or .png file)
        if (action.hasTexture()) {
            AvatarActionTextureResolver.Sprite sprite = AvatarActionTextureResolver.resolve(state, activeModel, action.texture());
            if (sprite != null) {
                int u = action.texW() > 0 && action.texH() > 0 ? action.texU() : 0;
                int v = action.texW() > 0 && action.texH() > 0 ? action.texV() : 0;
                int regW = action.texW() > 0 ? action.texW() : sprite.width();
                int regH = action.texH() > 0 ? action.texH() : sprite.height();
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    sprite.id(),
                    x + 1,
                    y + 1,
                    u,
                    v,
                    16,
                    16,
                    regW,
                    regH,
                    sprite.width(),
                    sprite.height()
                );
                return;
            }
        }

        // 2. Minecraft ItemStack icon
        String icon = action.icon();
        if (!icon.isBlank()) {
            String fullId = icon.contains(":") ? icon : "minecraft:" + icon;
            Identifier id = Identifier.tryParse(fullId);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id).orElseThrow().value());
                if (!stack.isEmpty()) {
                    graphics.item(stack, x + 1, y + 1);
                    return;
                }
            }
        }

        // 3. Semantic glyph fallback
        String glyph = resolveGlyph(icon, action.displayTitle());
        graphics.text(this.font, Component.literal(glyph), x + 5, y + 5, ShyneTheme.ACCENT, true);
    }

    private String resolveGlyph(String icon, String title) {
        String key = (icon + " " + title).toLowerCase(Locale.ROOT);
        if (key.contains("return") || key.contains("back")) return "↩";
        if (key.contains("stop") || key.contains("cancel") || key.contains("clear")) return "✕";
        if (key.contains("skin") || key.contains("outfit") || key.contains("wardrobe")) return "◈";
        if (key.contains("roar") || key.contains("sound") || key.contains("music") || key.contains("audio")) return "♪";
        if (key.contains("wave") || key.contains("hand") || key.contains("arm")) return "✋";
        if (key.contains("dance") || key.contains("jump") || key.contains("emote") || key.contains("kick") || key.contains("sit")) return "★";
        if (key.contains("ear") || key.contains("tail") || key.contains("physics")) return "≋";
        if (key.contains("sword") || key.contains("trident") || key.contains("weapon") || key.contains("attack")) return "⚔";
        if (key.contains("shield") || key.contains("armor") || key.contains("permission")) return "◆";
        if (key.contains("heart") || key.contains("love") || key.contains("heal")) return "♥";
        if (key.contains("toggle") || key.contains("setting") || key.contains("config")) return "⚙";
        return "✦";
    }

    private List<AvatarAction> systemActions(boolean hasAvatar) {
        List<AvatarAction> actions = new ArrayList<>();
        actions.add(systemAction("screen.shyne_core.avatars", "screen.shyne_core.avatars.tooltip", "player_head",
            () -> openScreen(new AvatarManagerScreen(parent))));
        if (hasAvatar) {
            actions.add(systemAction("screen.shyne_core.avatars.outfit", "screen.shyne_core.avatars.outfit.tooltip", "leather_chestplate",
                () -> openScreen(new AvatarOutfitScreen(parent))));
            actions.add(systemAction("screen.shyne_core.avatars.permissions", "screen.shyne_core.avatars.permissions.tooltip", "shield",
                () -> {
                    AvatarState active = AvatarRuntime.active();
                    if (active != null) {
                        openScreen(new AvatarPermissionScreen(parent, active.avatarId(), avatarName, active.requestedPermissions()));
                    }
                }));
        }
        actions.add(systemAction("screen.shyne_core.avatars.importer", "screen.shyne_core.avatars.importer.tooltip", "writable_book",
            () -> openScreen(new AvatarImporterScreen(parent))));
        actions.add(systemAction("screen.shyne_core.power_deck.title", "screen.shyne_core.pause_powers_button.tooltip", "blaze_powder",
            () -> openScreen(new PowerDeckScreen(parent))));
        actions.add(systemAction("screen.shyne_core.settings", "screen.shyne_core.action_wheel.settings_hint", "comparator",
            () -> openScreen(new ShyneSettingsScreen(parent))));
        if (hasAvatar) {
            actions.add(systemAction("screen.shyne_core.action_wheel.reload", "screen.shyne_core.action_wheel.reload_hint", "clock",
                () -> {
                    if (this.minecraft != null) {
                        AvatarRuntime.reloadActive(this.minecraft);
                        pageHistory.clear();
                        refreshActiveSlots();
                    }
                }));
        }
        return List.copyOf(actions);
    }

    private AvatarAction systemAction(String titleKey, String descriptionKey, String icon, Runnable callback) {
        return new AvatarAction(titleKey, Component.translatable(titleKey).getString(),
            Component.translatable(descriptionKey).getString(), "system", icon, false, true, callback);
    }

    private void openScreen(Screen screen) {
        if (this.minecraft != null) this.minecraft.gui.setScreen(screen);
    }

    private boolean executeSlotPrimary(int slot, boolean fromHoldRelease) {
        if (slot < 0 || slot >= SLOTS_PER_PAGE) return false;
        AvatarAction action = slotActions[slot];
        if (action == null) return false;

        playUiClickSound();
        AvatarState state = AvatarRuntime.active();
        String beforePage = state != null ? state.activeActionPage() : "";

        if (action.isToggle()) {
            action.setToggled(!action.isToggled());
        }
        try {
            action.callback().run();
        } catch (Throwable t) {
            ShyneCore.LOGGER.error("[AvatarActionWheel] Action '{}' primary callback error: {}", action.id(), t.getMessage(), t);
        }

        // If callback opened another GUI screen (e.g. Manage Avatars), do not overwrite it!
        if (this.minecraft != null && this.minecraft.gui.screen() != this) {
            return true;
        }

        String afterPage = state != null ? state.activeActionPage() : "";
        boolean switchedPage = !Objects.equals(beforePage, afterPage);
        if (switchedPage && !beforePage.isBlank() && !afterPage.isBlank()) {
            recordPageTransition(beforePage, afterPage);
            overflowSliceIndex = 0;
        }

        refreshActiveSlots();

        if (action.closeOnUse()) {
            onClose();
        } else if (fromHoldRelease && !switchedPage && !action.isToggle()) {
            onClose();
        }
        return true;
    }

    private boolean executeSlotSecondary(int slot) {
        if (slot < 0 || slot >= SLOTS_PER_PAGE) return false;
        AvatarAction action = slotActions[slot];
        if (action == null || !action.hasSecondaryCallback()) return false;

        playUiClickSound();
        AvatarState state = AvatarRuntime.active();
        String beforePage = state != null ? state.activeActionPage() : "";
        try {
            action.secondaryCallback().run();
        } catch (Throwable t) {
            ShyneCore.LOGGER.error("[AvatarActionWheel] Action '{}' secondary callback error: {}", action.id(), t.getMessage(), t);
        }
        String afterPage = state != null ? state.activeActionPage() : "";
        if (!Objects.equals(beforePage, afterPage) && !beforePage.isBlank() && !afterPage.isBlank()) {
            recordPageTransition(beforePage, afterPage);
            overflowSliceIndex = 0;
        }
        refreshActiveSlots();
        return true;
    }

    private void recordPageTransition(String fromPage, String toPage) {
        if (!pageHistory.isEmpty() && pageHistory.peek().equalsIgnoreCase(toPage)) {
            pageHistory.pop();
            return;
        }
        if (pageHistory.stream().anyMatch(p -> p.equalsIgnoreCase(toPage))) {
            while (!pageHistory.isEmpty() && !pageHistory.peek().equalsIgnoreCase(toPage)) {
                pageHistory.pop();
            }
            if (!pageHistory.isEmpty()) pageHistory.pop();
            return;
        }
        pageHistory.push(fromPage);
    }

    private boolean navigateBackPage() {
        AvatarState state = AvatarRuntime.active();
        if (state == null || pageHistory.isEmpty()) return false;
        String previous = pageHistory.pop();
        state.setActiveActionPage(previous);
        overflowSliceIndex = 0;
        playUiScrollSound();
        refreshActiveSlots();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        int cx = this.width / 2;
        int cy = this.height / 2 + 2;
        updateHoveredSlot(cx, cy, wheelRadius(), (int) event.x(), (int) event.y());

        if (event.button() == 0 && hoveredSlot >= 0 && slotActions[hoveredSlot] != null) {
            return executeSlotPrimary(hoveredSlot, false);
        }
        if (event.button() == 1) {
            if (hoveredSlot >= 0 && slotActions[hoveredSlot] != null && slotActions[hoveredSlot].hasSecondaryCallback()) {
                return executeSlotSecondary(hoveredSlot);
            }
            if (!controlsMode && navigateBackPage()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int cx = this.width / 2;
        int cy = this.height / 2 + 2;
        updateHoveredSlot(cx, cy, wheelRadius(), (int) mouseX, (int) mouseY);

        if (hoveredSlot >= 0 && slotActions[hoveredSlot] != null && slotActions[hoveredSlot].hasScrollCallback()) {
            int dir = verticalAmount > 0 ? 1 : -1;
            try {
                slotActions[hoveredSlot].onScroll(dir);
            } catch (Throwable t) {
                ShyneCore.LOGGER.error("[AvatarActionWheel] Action '{}' scroll callback error: {}", slotActions[hoveredSlot].id(), t.getMessage(), t);
            }
            playUiScrollSound();
            refreshActiveSlots();
            return true;
        }

        AvatarState state = AvatarRuntime.active();
        if (!controlsMode && state != null) {
            if (state.activeActionPage().isBlank() && state.actionsByPage().size() > 1) {
                nativePageIndex += (verticalAmount < 0 ? 1 : -1);
                hoveredSlot = -1;
                playUiScrollSound();
                refreshActiveSlots();
                return true;
            } else if (currentSliceCount > 1) {
                overflowSliceIndex += (verticalAmount < 0 ? 1 : -1);
                hoveredSlot = -1;
                playUiScrollSound();
                refreshActiveSlots();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (initialTriggerReleaseHandled && triggerKeyCode != -1 && keyCode == triggerKeyCode) {
            onClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_TAB) {
            toggleMode();
            return true;
        }
        if (keyCode == InputConstants.KEY_BACKSPACE && !controlsMode && navigateBackPage()) {
            return true;
        }
        if (keyCode >= 49 && keyCode <= 56) {
            int slot = keyCode - 49;
            if (slotActions[slot] != null) {
                return executeSlotPrimary(slot, false);
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (!initialTriggerReleaseHandled && triggerKeyCode != -1 && event.key() == triggerKeyCode) {
            initialTriggerReleaseHandled = true;
            long heldMs = System.currentTimeMillis() - openedAtMillis;
            if (heldMs >= HOLD_RELEASE_THRESHOLD_MS) {
                if (hoveredSlot >= 0 && slotActions[hoveredSlot] != null) {
                    executeSlotPrimary(hoveredSlot, true);
                } else {
                    onClose();
                }
                return true;
            }
        }
        return super.keyReleased(event);
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
}

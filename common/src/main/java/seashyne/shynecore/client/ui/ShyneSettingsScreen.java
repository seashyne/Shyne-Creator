package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.RemoteAvatarResourceBudget;
import seashyne.shynecore.client.avatar.ShyneStatusClient;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.ui.ShyneSettingsControls.Category;
import seashyne.shynecore.client.ui.ShyneSettingsControls.ScreenAction;
import seashyne.shynecore.client.ui.ShyneSettingsControls.Setting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ShyneSettingsScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "textures/gui/shyne_creator_logo.png");
    private static final int GAP = 8;

    private final Screen parent;
    private Category category = Category.INTERFACE;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int navWidth;
    private int contentX;
    private int contentWidth;
    private int rowY;
    private boolean compact;
    private int contentScroll;
    private int maxContentScroll;

    public ShyneSettingsScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(700, Math.max(300, this.width - 24));
        panelHeight = Math.min(420, Math.max(248, this.height - 24));
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        compact = panelWidth < 420;

        if (compact) {
            navWidth = panelWidth - 16;
            contentX = panelX + 8;
            contentWidth = panelWidth - 16;
            Category[] categories = Category.values();
            int tabWidth = (navWidth - GAP * (categories.length - 1)) / categories.length;
            int navY = panelY + 51;
            for (int i = 0; i < categories.length; i++) {
                addCategoryButton(categories[i], panelX + 8 + (tabWidth + GAP) * i, navY, tabWidth);
            }
            rowY = panelY + 79;
        } else {
            navWidth = Math.min(146, Math.max(104, panelWidth / 4));
            contentX = panelX + navWidth + GAP;
            contentWidth = panelWidth - navWidth - GAP - 8;
            int navY = panelY + 57;
            Category[] categories = Category.values();
            for (int i = 0; i < categories.length; i++) {
                addCategoryButton(categories[i], panelX + 8, navY + i * 24, navWidth - 16);
            }
            rowY = panelY + 84;
        }

        List<Setting> settings = settingsFor(category);
        List<ScreenAction> actions = actionsFor(category);
        int actionRows = actions.isEmpty() ? 0 :
            ((category == Category.CREATOR && compact) || (category == Category.AVATAR && actions.size() > 2))
                ? (actions.size() + 1) / 2 : actions.size();
        int contentHeight = actionRows == 0 ? settings.size() * 36
            : actionStartFor(category, settings.size()) - rowY + actionRows * 24;
        maxContentScroll = Math.max(0, contentHeight - (contentBottom() - rowY));
        contentScroll = Math.min(contentScroll, maxContentScroll);
        for (int i = 0; i < settings.size(); i++) {
            Setting setting = settings.get(i);
            int y = rowY + i * 36 - contentScroll;
            if (visibleRow(y, 32)) addToggle(setting, contentX + contentWidth - 62, y + 6, 54);
        }

        int actionStart = actionStartFor(category, settings.size()) - contentScroll;
        for (int i = 0; i < actions.size(); i++) {
            ScreenAction action = actions.get(i);
            int actionX = contentX;
            int actionY = actionStart + i * 24;
            int actionWidth = contentWidth;
            if ((category == Category.CREATOR && compact) || (category == Category.AVATAR && actions.size() > 2)) {
                actionWidth = (contentWidth - GAP) / 2;
                actionX = contentX + (i % 2) * (actionWidth + GAP);
                actionY = actionStart + (i / 2) * 24;
            }
            if (!visibleRow(actionY, 20)) continue;
            Button button = Button.builder(action.label().get(), pressed -> {
                action.run().run();
                pressed.setMessage(action.label().get());
            })
                .tooltip(Tooltip.create(action.tooltip().get()))
                .bounds(actionX, actionY, actionWidth, 20).build();
            button.active = action.enabled().getAsBoolean();
            addRenderableWidget(button);
        }

        int footerY = panelY + panelHeight - 28;
        int innerWidth = panelWidth - 16;
        int footerGap = 4;
        int avatarsWidth = Math.max(88, innerWidth * 36 / 100);
        int resetWidth = Math.max(64, innerWidth * 25 / 100);
        int doneWidth = innerWidth - avatarsWidth - resetWidth - footerGap * 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.avatars"), btn -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(new AvatarManagerScreen(this));
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.avatars.tooltip")))
            .bounds(panelX + 8, footerY, avatarsWidth, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.reset"), btn -> {
            ShyneClientSettings.resetDefaults();
            rebuildWidgets();
        }).tooltip(Tooltip.create(Component.translatable("screen.shyne_core.reset.tooltip")))
            .bounds(panelX + 8 + avatarsWidth + footerGap, footerY, resetWidth, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> onClose())
            .bounds(panelX + 8 + avatarsWidth + resetWidth + footerGap * 2, footerY, doneWidth, 20).build());
    }

    private void addCategoryButton(Category value, int x, int y, int width) {
        Component label = Component.translatable(compact ? value.compactTranslationKey : value.translationKey)
            .withStyle(value == category ? ChatFormatting.AQUA : ChatFormatting.WHITE);
        Button button = Button.builder(label, btn -> {
            category = value;
            contentScroll = 0;
            rebuildWidgets();
        }).tooltip(Tooltip.create(Component.translatable(value.descriptionKey))).bounds(x, y, width, 20).build();
        button.active = value != category;
        addRenderableWidget(button);
    }

    private void addToggle(Setting setting, int x, int y, int width) {
        boolean enabled = setting.getter().getAsBoolean();
        Button button = Button.builder(toggleLabel(enabled), btn -> {
            boolean next = !setting.getter().getAsBoolean();
            setting.setter().set(next);
            ShyneClientSettings.save();
            btn.setMessage(toggleLabel(next));
        }).tooltip(Tooltip.create(Component.translatable(setting.descriptionKey())))
            .bounds(x, y, width, 20).build();
        addRenderableWidget(button);
    }

    private Component toggleLabel(boolean value) {
        return Component.literal(value ? "ON" : "OFF")
            .withStyle(value ? ChatFormatting.AQUA : ChatFormatting.GRAY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88030918);
        graphics.fillGradient(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF20A1630, 0xF2071026);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xFF22D7E8);
        if (compact) {
            int tabWidth = (navWidth - GAP * (Category.values().length - 1)) / Category.values().length;
            int selectedX = panelX + 8 + category.ordinal() * (tabWidth + GAP);
            graphics.fill(selectedX, panelY + 73, selectedX + tabWidth, panelY + 76, 0xFF22D7E8);
        } else {
            graphics.fill(panelX + navWidth, panelY + 48, panelX + navWidth + 1, panelY + panelHeight - 36, 0x5532D8EA);
            int selectedCategoryY = panelY + 57 + category.ordinal() * 24;
            graphics.fill(panelX + 4, selectedCategoryY, panelX + 7, selectedCategoryY + 20, 0xFF22D7E8);
        }

        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, panelX + 9, panelY + 8, 0, 0, 32, 32, 1280, 1280);
        graphics.text(this.font, Component.translatable("screen.shyne_core.title").withStyle(ChatFormatting.AQUA), panelX + 48, panelY + 9, 0xFFFFFFFF, true);
        graphics.text(this.font, Component.translatable("screen.shyne_core.subtitle"), panelX + 48, panelY + 24, 0xFF91A7C6, false);
        graphics.fill(panelX + 8, panelY + 46, panelX + panelWidth - 8, panelY + 47, 0x6632D8EA);

        if (!compact) {
            graphics.text(this.font, Component.translatable(category.translationKey).withStyle(ChatFormatting.AQUA), contentX + 6, panelY + 53, 0xFFFFFFFF, false);
            String categoryDescription = this.font.plainSubstrByWidth(Component.translatable(category.descriptionKey).getString(), contentWidth - 12);
            graphics.text(this.font, Component.literal(categoryDescription), contentX + 6, panelY + 66, 0xFF8195B4, false);
        }
        if (category == Category.CREATOR) {
            graphics.fill(contentX, rowY, contentX + contentWidth, rowY + 24, 0x2419BFD1);
            graphics.text(this.font,
                Component.translatable("screen.shyne_core.creator.by", ShyneCreatorInfo.CREATOR_NAME),
                contentX + 7, rowY + 3, 0xFFF3F7FF, false);
            graphics.text(this.font,
                Component.translatable("screen.shyne_core.creator.version", ShyneCore.VERSION),
                contentX + 7, rowY + 14, 0xFF8195B4, false);
        }
        List<Setting> settings = settingsFor(category);
        for (int i = 0; i < settings.size(); i++) {
            Setting setting = settings.get(i);
            int y = rowY + i * 36 - contentScroll;
            if (!visibleRow(y, 32)) continue;
            if (i % 2 == 0) graphics.fill(contentX, y, contentX + contentWidth, y + 32, 0x2419BFD1);
            graphics.text(this.font, Component.translatable(setting.nameKey()), contentX + 7, y + 5, 0xFFF3F7FF, false);
            String description = this.font.plainSubstrByWidth(Component.translatable(setting.descriptionKey()).getString(), contentWidth - 78);
            graphics.text(this.font, Component.literal(description), contentX + 7, y + 18, 0xFF8195B4, false);
        }

        List<ScreenAction> actions = actionsFor(category);
        if (!actions.isEmpty()) {
            int actionStart = actionStartFor(category, settings.size()) - contentScroll;
            if (category != Category.CREATOR && actionStart >= rowY + 10 && actionStart < contentBottom()) {
                graphics.text(this.font, Component.translatable("screen.shyne_core.settings.quick_actions"), contentX + 5, actionStart - 10, 0xFF8195B4, false);
            }
        }

        Component hint = maxContentScroll > 0
            ? Component.translatable("screen.shyne_core.settings.scroll_hint")
            : Component.literal("✓ ").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("screen.shyne_core.saved_hint").withStyle(ChatFormatting.GRAY));
        graphics.text(this.font, hint, panelX + 10, panelY + panelHeight - 42, 0xFF7188A8, false);
        graphics.fill(panelX + 8, panelY + panelHeight - 34, panelX + panelWidth - 8, panelY + panelHeight - 33, 0x4432D8EA);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private List<Setting> settingsFor(Category value) {
        return switch (value) {
            case INTERFACE -> List.of(
                new Setting("setting.shyne_core.hints", "setting.shyne_core.hints.desc", () -> ShyneClientSettings.renderUiHints, v -> ShyneClientSettings.renderUiHints = v),
                new Setting("setting.shyne_core.effects", "setting.shyne_core.effects.desc", () -> ShyneClientSettings.autoPlayVisuals, v -> ShyneClientSettings.autoPlayVisuals = v),
                new Setting("setting.shyne_core.combat_hud", "setting.shyne_core.combat_hud.desc", () -> ShyneClientSettings.combatHudEnabled, v -> ShyneClientSettings.combatHudEnabled = v),
                new Setting("setting.shyne_core.action_wheel_avatar", "setting.shyne_core.action_wheel_avatar.desc", () -> ShyneClientSettings.actionWheelCenterAvatar, v -> ShyneClientSettings.actionWheelCenterAvatar = v)
            );
            case AVATAR -> List.of(
                new Setting("setting.shyne_core.avatar_models", "setting.shyne_core.avatar_models.desc", () -> ShyneClientSettings.renderAttachments, v -> ShyneClientSettings.renderAttachments = v),
                new Setting("setting.shyne_core.avatar_powers", "setting.shyne_core.avatar_powers.desc", () -> ShyneClientSettings.avatarPowersEnabled, v -> ShyneClientSettings.avatarPowersEnabled = v),
                new Setting("setting.shyne_core.item_actions", "setting.shyne_core.item_actions.desc", () -> ShyneClientSettings.itemActionsEnabled, v -> ShyneClientSettings.itemActionsEnabled = v),
                new Setting("setting.shyne_core.signature_weapons", "setting.shyne_core.signature_weapons.desc", () -> ShyneClientSettings.signatureWeaponsEnabled, v -> ShyneClientSettings.signatureWeaponsEnabled = v),
                new Setting("setting.shyne_core.audio_stream", "setting.shyne_core.audio_stream.desc", () -> ShyneClientSettings.audioStreamEnabled, v -> ShyneClientSettings.audioStreamEnabled = v)
            );
            case CLOUD -> List.of(
                new Setting("setting.shyne_core.status", "setting.shyne_core.status.desc", () -> ShyneClientSettings.cloudEnabled, v -> ShyneClientSettings.cloudEnabled = v)
            );
            case ADVANCED -> List.of(
                new Setting("setting.shyne_core.debug", "setting.shyne_core.debug.desc", () -> ShyneClientSettings.renderDebugLines, v -> ShyneClientSettings.renderDebugLines = v)
            );
            case CREATOR -> List.of();
        };
    }

    private List<ScreenAction> actionsFor(Category value) {
        return switch (value) {
            case INTERFACE -> List.of(
                new ScreenAction("screen.shyne_core.inputs.title", "screen.shyne_core.inputs.tooltip", () -> openScreen(new AvatarInputSettingsScreen(this)), () -> true),
                new ScreenAction("screen.shyne_core.power_deck.title", "screen.shyne_core.pause_powers_button.tooltip", () -> openScreen(new PowerDeckScreen(this)), () -> true)
            );
            case ADVANCED -> List.of(
                new ScreenAction(
                    "setting.shyne_core.remote_budget",
                    "setting.shyne_core.remote_budget.desc",
                    this::cycleRemoteAvatarBudget,
                    () -> true,
                    this::remoteAvatarBudgetLabel
                ),
                new ScreenAction("screen.shyne_core.inputs.title", "screen.shyne_core.inputs.tooltip", () -> openScreen(new AvatarInputSettingsScreen(this)), () -> true),
                new ScreenAction("screen.shyne_core.profiler.title", "screen.shyne_core.profiler.tooltip", () -> openScreen(new AvatarProfilerScreen(this)), () -> true)
            );
            case AVATAR -> {
                List<ScreenAction> actions = new ArrayList<>();
                actions.add(new ScreenAction("screen.shyne_core.avatars", "screen.shyne_core.avatars.tooltip", () -> openScreen(new AvatarManagerScreen(this)), () -> true));
                if (AvatarRuntime.active() != null) {
                    actions.add(new ScreenAction("screen.shyne_core.avatars.outfit", "screen.shyne_core.avatars.outfit.tooltip", () -> openScreen(new AvatarOutfitScreen(this)), () -> true));
                }
                actions.add(new ScreenAction("screen.shyne_core.avatars.actions", "screen.shyne_core.avatars.actions.tooltip", () -> openScreen(new AvatarActionWheelScreen(this)), () -> true));
                actions.add(new ScreenAction("screen.shyne_core.power_deck.title", "screen.shyne_core.pause_powers_button.tooltip", () -> openScreen(new PowerDeckScreen(this)), () -> true));
                actions.add(new ScreenAction("screen.shyne_core.items.title", "screen.shyne_core.pause_items_button.tooltip", () -> openScreen(new ItemCatalogScreen(this)), () -> true));
                actions.add(new ScreenAction(
                    "setting.shyne_core.audio_volume",
                    "setting.shyne_core.audio_volume.desc",
                    this::cycleAudioVolume,
                    () -> ShyneClientSettings.audioStreamEnabled,
                    this::audioVolumeLabel
                ));
                actions.add(new ScreenAction("screen.shyne_core.avatars.folder", "screen.shyne_core.avatars.folder.tooltip", this::openAvatarFolder, () -> true));
                yield actions;
            }
            case CLOUD -> List.of(
                new ScreenAction("screen.shyne_core.avatars.status.settings", "screen.shyne_core.avatars.status.settings.tooltip", () -> openScreen(new ShyneStatusScreen(this)), () -> true),
                new ScreenAction("screen.shyne_core.avatars.status.check", "screen.shyne_core.avatars.status.check.tooltip", this::checkCloud, () -> !ShyneStatusClient.lastResult().working()),
                new ScreenAction("screen.shyne_core.cloud.open", "screen.shyne_core.cloud.open.tooltip", () -> openScreen(new CloudAvatarLibraryScreen(this)), () -> ShyneClientSettings.cloudEnabled)
            );
            case CREATOR -> ShyneCreatorInfo.links().stream()
                .map(link -> new ScreenAction(
                    link.labelKey(),
                    link.tooltipKey(),
                    () -> openUrl(link.uri()),
                    () -> true,
                    () -> creatorLinkLabel(link),
                    () -> Component.translatable(link.tooltipKey())
                        .append(Component.literal("\n" + link.uri()).withStyle(ChatFormatting.GRAY))
                ))
                .toList();
        };
    }

    private Component creatorLinkLabel(ShyneCreatorInfo.Link link) {
        Component label = Component.translatable(link.labelKey());
        if (!compact) {
            label = label.copy()
                .append(Component.literal("  ·  " + link.displayUrl()).withStyle(ChatFormatting.GRAY));
        }
        return label;
    }

    private int actionStartFor(Category value, int settingCount) {
        if (value == Category.CREATOR) return rowY + 28;
        return rowY + settingCount * 36 + (settingCount == 0 ? 0 : 5);
    }

    private int contentBottom() {
        return panelY + panelHeight - 50;
    }

    private boolean visibleRow(int y, int height) {
        return y >= rowY && y + height <= contentBottom();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxContentScroll > 0 && mouseX >= contentX && mouseX < contentX + contentWidth
            && mouseY >= rowY && mouseY < contentBottom() && verticalAmount != 0) {
            contentScroll = Math.max(0, Math.min(maxContentScroll,
                contentScroll + (verticalAmount < 0 ? 36 : -36)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void cycleRemoteAvatarBudget() {
        RemoteAvatarResourceBudget.Preset current = RemoteAvatarResourceBudget.Preset.fromId(
            ShyneClientSettings.remoteAvatarBudgetPreset
        );
        RemoteAvatarResourceBudget.Preset[] presets = RemoteAvatarResourceBudget.Preset.values();
        RemoteAvatarResourceBudget.Preset next = presets[(current.ordinal() + 1) % presets.length];
        ShyneClientSettings.setRemoteAvatarBudgetPreset(next.id());
    }

    private Component remoteAvatarBudgetLabel() {
        RemoteAvatarResourceBudget.Preset preset = RemoteAvatarResourceBudget.Preset.fromId(
            ShyneClientSettings.remoteAvatarBudgetPreset
        );
        return Component.translatable("setting.shyne_core.remote_budget")
            .append(Component.literal(": "))
            .append(Component.translatable("setting.shyne_core.remote_budget." + preset.id()));
    }

    private void cycleAudioVolume() {
        float current = ShyneClientSettings.audioStreamVolume;
        float next = current < 0.49f ? 0.5f : current < 0.74f ? 0.75f : current < 0.99f ? 1.0f : current < 1.49f ? 1.5f : 0.25f;
        ShyneClientSettings.audioStreamVolume = next;
        ShyneClientSettings.save();
    }

    private Component audioVolumeLabel() {
        int percent = Math.round(ShyneClientSettings.audioStreamVolume * 100.0f);
        return Component.translatable("setting.shyne_core.audio_volume").append(Component.literal(": " + percent + "%"));
    }

    private void openScreen(Screen screen) {
        if (this.minecraft != null) this.minecraft.gui.setScreen(screen);
    }

    private void openAvatarFolder() {
        Path folder = AvatarLoader.avatarsDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(folder);
            Blaze3D.openPath(folder);
        } catch (IOException exception) {
            ShyneCore.LOGGER.warn("[ShyneCreator] Could not open Avatar folder: {}", exception.getMessage());
        }
    }

    private void openUrl(java.net.URI uri) {
        try {
            Blaze3D.openUri(uri);
        } catch (RuntimeException exception) {
            ShyneCore.LOGGER.warn("[ShyneCreator] Could not open creator URL {}: {}", uri, exception.getMessage());
        }
    }

    private void checkCloud() {
        if (this.minecraft == null) return;
        var client = this.minecraft;
        ShyneStatusClient.check().whenComplete((result, error) -> client.execute(this::rebuildWidgets));
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        ShyneClientSettings.save();
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

}

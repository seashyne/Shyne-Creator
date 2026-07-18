package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
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
import seashyne.shynecore.client.avatar.ShyneStatusClient;
import seashyne.shynecore.client.config.ShyneClientSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

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

    public ShyneSettingsScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(520, Math.max(280, this.width - 16));
        panelHeight = Math.min(320, Math.max(224, this.height - 16));
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        compact = panelWidth < 420;

        if (compact) {
            navWidth = panelWidth - 16;
            contentX = panelX + 8;
            contentWidth = panelWidth - 16;
            int tabWidth = (navWidth - GAP * 3) / 4;
            int navY = panelY + 51;
            addCategoryButton(Category.INTERFACE, panelX + 8, navY, tabWidth);
            addCategoryButton(Category.AVATAR, panelX + 8 + tabWidth + GAP, navY, tabWidth);
            addCategoryButton(Category.CLOUD, panelX + 8 + (tabWidth + GAP) * 2, navY, tabWidth);
            addCategoryButton(Category.ADVANCED, panelX + 8 + (tabWidth + GAP) * 3, navY, tabWidth);
            rowY = panelY + 79;
        } else {
            navWidth = Math.min(118, Math.max(92, panelWidth / 4));
            contentX = panelX + navWidth + GAP;
            contentWidth = panelWidth - navWidth - GAP - 8;
            int navY = panelY + 57;
            addCategoryButton(Category.INTERFACE, panelX + 8, navY, navWidth - 16);
            addCategoryButton(Category.AVATAR, panelX + 8, navY + 24, navWidth - 16);
            addCategoryButton(Category.CLOUD, panelX + 8, navY + 48, navWidth - 16);
            addCategoryButton(Category.ADVANCED, panelX + 8, navY + 72, navWidth - 16);
            rowY = panelY + 57;
        }

        List<Setting> settings = settingsFor(category);
        for (int i = 0; i < settings.size(); i++) {
            Setting setting = settings.get(i);
            int y = rowY + i * 36;
            addToggle(setting, contentX + contentWidth - 62, y + 6, 54);
        }

        List<ScreenAction> actions = actionsFor(category);
        int actionStart = rowY + settings.size() * 36 + (settings.isEmpty() ? 0 : 5);
        for (int i = 0; i < actions.size(); i++) {
            ScreenAction action = actions.get(i);
            Button button = Button.builder(Component.translatable(action.nameKey), ignored -> action.run.run())
                .tooltip(Tooltip.create(Component.translatable(action.descriptionKey)))
                .bounds(contentX, actionStart + i * 24, contentWidth, 20).build();
            button.active = action.enabled.getAsBoolean();
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
        Component label = Component.translatable(value.translationKey)
            .withStyle(value == category ? ChatFormatting.AQUA : ChatFormatting.WHITE);
        Button button = Button.builder(label, btn -> {
            category = value;
            rebuildWidgets();
        }).bounds(x, y, width, 20).build();
        button.active = value != category;
        addRenderableWidget(button);
    }

    private void addToggle(Setting setting, int x, int y, int width) {
        boolean enabled = setting.getter.getAsBoolean();
        Button button = Button.builder(toggleLabel(enabled), btn -> {
            boolean next = !setting.getter.getAsBoolean();
            setting.setter.set(next);
            ShyneClientSettings.save();
            btn.setMessage(toggleLabel(next));
        }).tooltip(Tooltip.create(Component.translatable(setting.descriptionKey)))
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
            int tabWidth = (navWidth - GAP * 3) / 4;
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
            graphics.text(this.font, Component.translatable(category.translationKey).withStyle(ChatFormatting.AQUA), contentX + 6, panelY + 48, 0xFFFFFFFF, false);
        }
        List<Setting> settings = settingsFor(category);
        for (int i = 0; i < settings.size(); i++) {
            Setting setting = settings.get(i);
            int y = rowY + i * 36;
            if (i % 2 == 0) graphics.fill(contentX, y, contentX + contentWidth, y + 32, 0x2419BFD1);
            graphics.text(this.font, Component.translatable(setting.nameKey), contentX + 7, y + 5, 0xFFF3F7FF, false);
            String description = this.font.plainSubstrByWidth(Component.translatable(setting.descriptionKey).getString(), contentWidth - 78);
            graphics.text(this.font, Component.literal(description), contentX + 7, y + 18, 0xFF8195B4, false);
        }

        List<ScreenAction> actions = actionsFor(category);
        if (!actions.isEmpty()) {
            int actionStart = rowY + settings.size() * 36 + (settings.isEmpty() ? 0 : 5);
            graphics.text(this.font, Component.translatable("screen.shyne_core.settings.quick_actions"), contentX + 5, actionStart - 10, 0xFF8195B4, false);
        }

        graphics.text(this.font, Component.literal("✓ ").withStyle(ChatFormatting.AQUA)
            .append(Component.translatable("screen.shyne_core.saved_hint").withStyle(ChatFormatting.GRAY)), panelX + 10, panelY + panelHeight - 42, 0xFF7188A8, false);
        graphics.fill(panelX + 8, panelY + panelHeight - 34, panelX + panelWidth - 8, panelY + panelHeight - 33, 0x4432D8EA);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private List<Setting> settingsFor(Category value) {
        return switch (value) {
            case INTERFACE -> List.of(
                new Setting("setting.shyne_core.hints", "setting.shyne_core.hints.desc", () -> ShyneClientSettings.renderUiHints, v -> ShyneClientSettings.renderUiHints = v),
                new Setting("setting.shyne_core.effects", "setting.shyne_core.effects.desc", () -> ShyneClientSettings.autoPlayVisuals, v -> ShyneClientSettings.autoPlayVisuals = v)
            );
            case AVATAR -> List.of(
                new Setting("setting.shyne_core.avatar_models", "setting.shyne_core.avatar_models.desc", () -> ShyneClientSettings.renderAttachments, v -> ShyneClientSettings.renderAttachments = v)
            );
            case CLOUD -> List.of(
                new Setting("setting.shyne_core.status", "setting.shyne_core.status.desc", () -> ShyneClientSettings.cloudEnabled, v -> ShyneClientSettings.cloudEnabled = v)
            );
            case ADVANCED -> List.of(
                new Setting("setting.shyne_core.debug", "setting.shyne_core.debug.desc", () -> ShyneClientSettings.renderDebugLines, v -> ShyneClientSettings.renderDebugLines = v)
            );
        };
    }

    private List<ScreenAction> actionsFor(Category value) {
        return switch (value) {
            case INTERFACE, ADVANCED -> List.of();
            case AVATAR -> List.of(
                new ScreenAction("screen.shyne_core.avatars", "screen.shyne_core.avatars.tooltip", () -> openScreen(new AvatarManagerScreen(this)), () -> true),
                new ScreenAction("screen.shyne_core.inputs.title", "screen.shyne_core.inputs.tooltip", () -> openScreen(new AvatarInputSettingsScreen(this)), () -> true),
                new ScreenAction("screen.shyne_core.avatars.outfit", "screen.shyne_core.avatars.outfit.tooltip", () -> openScreen(new AvatarOutfitScreen(this)), () -> AvatarRuntime.active() != null),
                new ScreenAction("screen.shyne_core.avatars.actions", "screen.shyne_core.avatars.actions.tooltip", () -> openScreen(new ShynePaletteScreen(this)), () -> AvatarRuntime.active() != null && !AvatarRuntime.active().actions().isEmpty()),
                new ScreenAction("screen.shyne_core.avatars.folder", "screen.shyne_core.avatars.folder.tooltip", this::openAvatarFolder, () -> true)
            );
            case CLOUD -> List.of(
                new ScreenAction("screen.shyne_core.avatars.status.settings", "screen.shyne_core.avatars.status.settings.tooltip", () -> openScreen(new ShyneStatusScreen(this)), () -> true),
                new ScreenAction("screen.shyne_core.avatars.status.check", "screen.shyne_core.avatars.status.check.tooltip", this::checkCloud, () -> !ShyneStatusClient.lastResult().working()),
                new ScreenAction("screen.shyne_core.cloud.open", "screen.shyne_core.cloud.open.tooltip", () -> openScreen(new CloudAvatarLibraryScreen(this)), () -> ShyneClientSettings.cloudEnabled)
            );
        };
    }

    private void openScreen(Screen screen) {
        if (this.minecraft != null) this.minecraft.gui.setScreen(screen);
    }

    private void openAvatarFolder() {
        Path folder = AvatarLoader.avatarsDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(folder);
            Util.getPlatform().openPath(folder);
        } catch (IOException exception) {
            ShyneCore.LOGGER.warn("[ShyneCreator] Could not open Avatar folder: {}", exception.getMessage());
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

    private enum Category {
        INTERFACE("screen.shyne_core.category.interface"),
        AVATAR("screen.shyne_core.category.avatar"),
        CLOUD("screen.shyne_core.category.cloud"),
        ADVANCED("screen.shyne_core.category.advanced");

        private final String translationKey;

        Category(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private record Setting(String nameKey, String descriptionKey, BooleanSupplier getter, BooleanSetter setter) {}

    private record ScreenAction(String nameKey, String descriptionKey, Runnable run, BooleanSupplier enabled) {}

    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
    }
}

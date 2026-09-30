package seashyne.shynecore.client.ui;

import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Shared data descriptions for the settings screen's categories, toggles, and links. */
final class ShyneSettingsControls {
    private ShyneSettingsControls() {}

    enum Category {
        INTERFACE("screen.shyne_core.category.interface", "screen.shyne_core.category.interface.short", "screen.shyne_core.category.interface.desc"),
        AVATAR("screen.shyne_core.category.avatar", "screen.shyne_core.category.avatar.short", "screen.shyne_core.category.avatar.desc"),
        CLOUD("screen.shyne_core.category.cloud", "screen.shyne_core.category.cloud.short", "screen.shyne_core.category.cloud.desc"),
        ADVANCED("screen.shyne_core.category.advanced", "screen.shyne_core.category.advanced.short", "screen.shyne_core.category.advanced.desc"),
        CREATOR("screen.shyne_core.category.creator", "screen.shyne_core.category.creator.short", "screen.shyne_core.category.creator.desc");

        final String translationKey;
        final String compactTranslationKey;
        final String descriptionKey;

        Category(String translationKey, String compactTranslationKey, String descriptionKey) {
            this.translationKey = translationKey;
            this.compactTranslationKey = compactTranslationKey;
            this.descriptionKey = descriptionKey;
        }
    }

    record Setting(String nameKey, String descriptionKey, BooleanSupplier getter, BooleanSetter setter) {}

    record ScreenAction(
        String nameKey,
        String descriptionKey,
        Runnable run,
        BooleanSupplier enabled,
        Supplier<Component> label,
        Supplier<Component> tooltip
    ) {
        ScreenAction(String nameKey, String descriptionKey, Runnable run, BooleanSupplier enabled) {
            this(nameKey, descriptionKey, run, enabled,
                () -> Component.translatable(nameKey), () -> Component.translatable(descriptionKey));
        }

        ScreenAction(String nameKey, String descriptionKey, Runnable run,
                     BooleanSupplier enabled, Supplier<Component> label) {
            this(nameKey, descriptionKey, run, enabled, label,
                () -> Component.translatable(descriptionKey));
        }
    }

    @FunctionalInterface
    interface BooleanSetter {
        void set(boolean value);
    }
}

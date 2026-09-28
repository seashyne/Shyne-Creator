package seashyne.shynecore.client.ui;

import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Shared data descriptions for the settings screen's categories, toggles, and links. */
final class ShyneSettingsControls {
    private ShyneSettingsControls() {}

    enum Category {
        INTERFACE("screen.shyne_core.category.interface", "screen.shyne_core.category.interface.short"),
        AVATAR("screen.shyne_core.category.avatar", "screen.shyne_core.category.avatar.short"),
        CLOUD("screen.shyne_core.category.cloud", "screen.shyne_core.category.cloud.short"),
        ADVANCED("screen.shyne_core.category.advanced", "screen.shyne_core.category.advanced.short"),
        CREATOR("screen.shyne_core.category.creator", "screen.shyne_core.category.creator.short");

        final String translationKey;
        final String compactTranslationKey;

        Category(String translationKey, String compactTranslationKey) {
            this.translationKey = translationKey;
            this.compactTranslationKey = compactTranslationKey;
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

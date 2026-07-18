package seashyne.shynecore.client.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.client.avatar.AvatarRuntime;

public final class ShyneKeybinds {
    private static KeyMapping openSettings;
    private static KeyMapping castLight;
    private static KeyMapping castHeavy;
    private static KeyMapping castUtility;
    private static KeyMapping castFinisher;
    private static KeyMapping openShynePalette;
    private static KeyMapping openAvatarManager;

    private ShyneKeybinds() {}

    public static void init() {
        openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.open_settings", GLFW.GLFW_KEY_O, KeyMapping.Category.MISC));
        castLight = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.cast_light", GLFW.GLFW_KEY_Z, KeyMapping.Category.GAMEPLAY));
        castHeavy = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.cast_heavy", GLFW.GLFW_KEY_X, KeyMapping.Category.GAMEPLAY));
        castUtility = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.cast_utility", GLFW.GLFW_KEY_C, KeyMapping.Category.GAMEPLAY));
        castFinisher = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.cast_finisher", GLFW.GLFW_KEY_V, KeyMapping.Category.GAMEPLAY));
        openShynePalette = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.open_palette", GLFW.GLFW_KEY_G, KeyMapping.Category.MISC));
        openAvatarManager = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.avatar_manager", GLFW.GLFW_KEY_H, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(new ShyneSettingsScreen(mc.gui.screen()));
            }
            while (castLight.consumeClick()) ShyneClientNetworking.sendSkillKey("light", 1);
            while (castHeavy.consumeClick()) ShyneClientNetworking.sendSkillKey("heavy", 2);
            while (castUtility.consumeClick()) ShyneClientNetworking.sendSkillKey("utility", 3);
            while (castFinisher.consumeClick()) ShyneClientNetworking.sendSkillKey("finisher", 4);
            while (openShynePalette.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (AvatarRuntime.active() != null) mc.gui.setScreen(new ShynePaletteScreen());
            }
            while (openAvatarManager.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(new AvatarManagerScreen(mc.gui.screen()));
            }
        });
    }
}

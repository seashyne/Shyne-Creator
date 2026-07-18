package seashyne.shynecore.client.ui;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
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

    public static void init(IEventBus modEventBus) {
        openSettings = new KeyMapping("key.shyne_core.open_settings", GLFW.GLFW_KEY_O, KeyMapping.Category.MISC);
        castLight = new KeyMapping("key.shyne_core.cast_light", GLFW.GLFW_KEY_Z, KeyMapping.Category.GAMEPLAY);
        castHeavy = new KeyMapping("key.shyne_core.cast_heavy", GLFW.GLFW_KEY_X, KeyMapping.Category.GAMEPLAY);
        castUtility = new KeyMapping("key.shyne_core.cast_utility", GLFW.GLFW_KEY_C, KeyMapping.Category.GAMEPLAY);
        castFinisher = new KeyMapping("key.shyne_core.cast_finisher", GLFW.GLFW_KEY_V, KeyMapping.Category.GAMEPLAY);
        openShynePalette = new KeyMapping("key.shyne_core.open_palette", GLFW.GLFW_KEY_G, KeyMapping.Category.MISC);
        openAvatarManager = new KeyMapping("key.shyne_core.avatar_manager", GLFW.GLFW_KEY_H, KeyMapping.Category.MISC);

        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(openSettings);
            event.register(castLight);
            event.register(castHeavy);
            event.register(castUtility);
            event.register(castFinisher);
            event.register(openShynePalette);
            event.register(openAvatarManager);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft client = Minecraft.getInstance();
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

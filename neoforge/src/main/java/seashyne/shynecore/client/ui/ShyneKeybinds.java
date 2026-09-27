package seashyne.shynecore.client.ui;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.blaze3d.platform.InputConstants;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.client.avatar.AvatarRuntime;

public final class ShyneKeybinds {
    private static KeyMapping openSettings;
    private static KeyMapping castLight;
    private static KeyMapping castHeavy;
    private static KeyMapping castUtility;
    private static KeyMapping castFinisher;
    private static KeyMapping openShynePalette;
    private static KeyMapping openActionWheel;
    private static KeyMapping openAvatarManager;
    private static KeyMapping reloadAvatar;

    private ShyneKeybinds() {}

    public static void init(IEventBus modEventBus) {
        openSettings = new KeyMapping("key.shyne_core.open_settings", InputConstants.KEY_O, KeyMapping.Category.MISC);
        castLight = new KeyMapping("key.shyne_core.cast_light", InputConstants.KEY_Z, KeyMapping.Category.GAMEPLAY);
        castHeavy = new KeyMapping("key.shyne_core.cast_heavy", InputConstants.KEY_X, KeyMapping.Category.GAMEPLAY);
        castUtility = new KeyMapping("key.shyne_core.cast_utility", InputConstants.KEY_C, KeyMapping.Category.GAMEPLAY);
        castFinisher = new KeyMapping("key.shyne_core.cast_finisher", InputConstants.KEY_V, KeyMapping.Category.GAMEPLAY);
        openShynePalette = new KeyMapping("key.shyne_core.open_palette", InputConstants.KEY_G, KeyMapping.Category.MISC);
        openActionWheel = new KeyMapping("key.shyne_core.action_wheel", InputConstants.KEY_B, KeyMapping.Category.MISC);
        openAvatarManager = new KeyMapping("key.shyne_core.avatar_manager", InputConstants.KEY_H, KeyMapping.Category.MISC);
        reloadAvatar = new KeyMapping("key.shyne_core.reload_avatar", InputConstants.KEY_F10, KeyMapping.Category.MISC);

        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(openSettings);
            event.register(castLight);
            event.register(castHeavy);
            event.register(castUtility);
            event.register(castFinisher);
            event.register(openShynePalette);
            event.register(openActionWheel);
            event.register(openAvatarManager);
            event.register(reloadAvatar);
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
            while (openActionWheel.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (AvatarRuntime.active() != null) mc.gui.setScreen(new AvatarActionWheelScreen());
            }
            while (openAvatarManager.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(new AvatarManagerScreen(mc.gui.screen()));
            }
            while (reloadAvatar.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                long start = System.currentTimeMillis();
                boolean ok = AvatarRuntime.reloadActive(mc);
                long elapsed = System.currentTimeMillis() - start;
                if (mc.player != null) {
                    if (ok) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[Shyne] Avatar reloaded in " + elapsed + " ms!§r"));
                    } else {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Shyne] Failed to reload avatar! Check logs.§r"));
                    }
                }
            }
        });
    }
}

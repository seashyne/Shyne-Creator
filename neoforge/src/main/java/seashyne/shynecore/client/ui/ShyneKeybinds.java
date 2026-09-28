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
    private static KeyMapping castLight;
    private static KeyMapping castHeavy;
    private static KeyMapping castUtility;
    private static KeyMapping castFinisher;
    private static KeyMapping openActionWheel;
    private static KeyMapping openPowerDeck;
    private static KeyMapping openPowerWheel;

    private ShyneKeybinds() {}

    public static void init(IEventBus modEventBus) {
        castLight = new KeyMapping("key.shyne_core.cast_light", InputConstants.KEY_Z, KeyMapping.Category.GAMEPLAY);
        castHeavy = new KeyMapping("key.shyne_core.cast_heavy", InputConstants.KEY_X, KeyMapping.Category.GAMEPLAY);
        castUtility = new KeyMapping("key.shyne_core.cast_utility", InputConstants.KEY_C, KeyMapping.Category.GAMEPLAY);
        castFinisher = new KeyMapping("key.shyne_core.cast_finisher", InputConstants.KEY_V, KeyMapping.Category.GAMEPLAY);
        openActionWheel = new KeyMapping("key.shyne_core.action_wheel", InputConstants.KEY_G, KeyMapping.Category.MISC);
        openPowerDeck = new KeyMapping("key.shyne_core.power_deck", InputConstants.KEY_K, KeyMapping.Category.GAMEPLAY);
        openPowerWheel = new KeyMapping("key.shyne_core.power_wheel", InputConstants.KEY_B, KeyMapping.Category.GAMEPLAY);

        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(castLight);
            event.register(castHeavy);
            event.register(castUtility);
            event.register(castFinisher);
            event.register(openActionWheel);
            event.register(openPowerDeck);
            event.register(openPowerWheel);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            while (castLight.consumeClick()) ShyneClientNetworking.sendSkillKey("light", 1);
            while (castHeavy.consumeClick()) ShyneClientNetworking.sendSkillKey("heavy", 2);
            while (castUtility.consumeClick()) ShyneClientNetworking.sendSkillKey("utility", 3);
            while (castFinisher.consumeClick()) ShyneClientNetworking.sendSkillKey("finisher", 4);
            while (openActionWheel.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null) mc.gui.setScreen(new AvatarActionWheelScreen());
            }
            while (openPowerDeck.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null) mc.gui.setScreen(new PowerDeckScreen());
            }
            while (openPowerWheel.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null) mc.gui.setScreen(new PowerRadialWheelScreen());
            }
        });
    }
}

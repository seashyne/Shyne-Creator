package seashyne.shynecore.client.ui;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.blaze3d.platform.InputConstants;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.client.state.CustomDeckManager;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ShyneKeybinds {
    private static KeyMapping castLight;
    private static KeyMapping castHeavy;
    private static KeyMapping castUtility;
    private static KeyMapping castFinisher;
    private static KeyMapping openActionWheel;
    private static KeyMapping openPowerDeck;
    private static KeyMapping openPowerWheel;
    private static final Set<Integer> pendingCustomDeckKeyPresses = new LinkedHashSet<>();

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
        // The input event preserves short taps that may begin and end between client ticks.
        // We defer dispatch to the tick so a key that opens a screen cannot also cast a deck skill.
        NeoForge.EVENT_BUS.addListener(InputEvent.Key.class, ShyneKeybinds::queueCustomDeckKeyPress);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            while (castLight.consumeClick()) {
                if (!hasCustomDeckBinding(castLight)) ShyneClientNetworking.sendSkillKey("light", 1);
            }
            while (castHeavy.consumeClick()) {
                if (!hasCustomDeckBinding(castHeavy)) ShyneClientNetworking.sendSkillKey("heavy", 2);
            }
            while (castUtility.consumeClick()) {
                if (!hasCustomDeckBinding(castUtility)) ShyneClientNetworking.sendSkillKey("utility", 3);
            }
            while (castFinisher.consumeClick()) {
                if (!hasCustomDeckBinding(castFinisher)) ShyneClientNetworking.sendSkillKey("finisher", 4);
            }
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
            dispatchCustomDeckKeyPresses(Minecraft.getInstance());
        });
    }

    private static void queueCustomDeckKeyPress(InputEvent.Key event) {
        if (event.getAction() != InputConstants.PRESS) return;
        Minecraft client = Minecraft.getInstance();
        if (!canUseCustomDeck(client)) {
            pendingCustomDeckKeyPresses.clear();
            return;
        }
        int keyCode = event.getKey();
        if (CustomDeckManager.hasActiveSkillBoundToKey(keyCode)) {
            pendingCustomDeckKeyPresses.add(keyCode);
        }
    }

    private static void dispatchCustomDeckKeyPresses(Minecraft client) {
        if (!canUseCustomDeck(client)) {
            pendingCustomDeckKeyPresses.clear();
            return;
        }
        if (pendingCustomDeckKeyPresses.isEmpty()) return;

        Set<Integer> keyPresses = new LinkedHashSet<>(pendingCustomDeckKeyPresses);
        pendingCustomDeckKeyPresses.clear();
        for (int keyCode : keyPresses) {
            String skillId = CustomDeckManager.activeSkillBoundToKey(keyCode);
            if (skillId != null && !skillId.isBlank()) {
                ShyneClientNetworking.sendCustomDeckSkill(skillId);
            }
        }
    }

    private static boolean hasCustomDeckBinding(KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return false;
        return CustomDeckManager.hasActiveSkillBoundToKey(InputConstants.getKey(mapping.saveString()).getValue());
    }

    private static boolean canUseCustomDeck(Minecraft client) {
        return client.player != null
            && client.gui.screen() == null
            && client.gui.overlay() == null
            && client.isWindowActive();
    }
}

package seashyne.shynecore.client.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.client.state.CustomDeckManager;
import seashyne.shynecore.client.state.CustomDeckManager.ActionSlot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ShyneKeybinds {
    private static KeyMapping castLight;
    private static KeyMapping castHeavy;
    private static KeyMapping castUtility;
    private static KeyMapping castFinisher;
    private static KeyMapping openActionWheel;
    private static KeyMapping openPowerDeck;
    private static KeyMapping openPowerWheel;
    /** Raw keys currently held for the active custom deck; used to turn polling into press events. */
    private static final Set<Integer> heldCustomDeckKeys = new LinkedHashSet<>();

    private ShyneKeybinds() {}

    public static void init() {
        castLight = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.cast_light", InputConstants.KEY_Z, KeyMapping.Category.GAMEPLAY));
        castHeavy = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.cast_heavy", InputConstants.KEY_X, KeyMapping.Category.GAMEPLAY));
        castUtility = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.cast_utility", InputConstants.KEY_C, KeyMapping.Category.GAMEPLAY));
        castFinisher = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.cast_finisher", InputConstants.KEY_V, KeyMapping.Category.GAMEPLAY));
        openActionWheel = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.action_wheel", InputConstants.KEY_G, KeyMapping.Category.MISC));
        openPowerDeck = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.power_deck", InputConstants.KEY_K, KeyMapping.Category.GAMEPLAY));
        openPowerWheel = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shyne_core.power_wheel", InputConstants.KEY_B, KeyMapping.Category.GAMEPLAY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CustomDeckManager.ensureLoaded();
            dispatchLegacyCast(castLight, "light", 1);
            dispatchLegacyCast(castHeavy, "heavy", 2);
            dispatchLegacyCast(castUtility, "utility", 3);
            dispatchLegacyCast(castFinisher, "finisher", 4);
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

            dispatchCustomDeckKeys(client);
        });
    }

    /**
     * Preserves the original fixed-slot controls unless an assigned action in the active custom
     * deck owns the same physical key. The click is still consumed in that case, so the legacy
     * packet cannot be emitted alongside the dynamic action.
     */
    private static void dispatchLegacyCast(KeyMapping mapping, String legacySkill, int legacySlot) {
        while (mapping.consumeClick()) {
            if (!hasCustomDeckBinding(mapping)) {
                ShyneClientNetworking.sendSkillKey(legacySkill, legacySlot);
            }
        }
    }

    /** Resolves a registered mapping's stored key string to the raw code used by the dynamic deck. */
    private static boolean hasCustomDeckBinding(KeyMapping mapping) {
        return mapping != null
            && !mapping.isUnbound()
            && CustomDeckManager.hasActiveSkillBoundToKey(InputConstants.getKey(mapping.saveString()).getValue());
    }

    /**
     * Converts raw key-state polling into one custom-deck action per key press. Dynamic bindings
     * are intentionally not registered as vanilla {@link KeyMapping}s because players can add,
     * remove, and rebind slots after client startup.
     */
    private static void dispatchCustomDeckKeys(Minecraft client) {
        List<ActionSlot> slots = CustomDeckManager.activeSlots();
        Set<Integer> boundKeys = new LinkedHashSet<>();
        for (ActionSlot slot : slots) {
            if (!slot.skillId().isBlank() && slot.keyCode() != InputConstants.UNKNOWN.getValue()) {
                boundKeys.add(slot.keyCode());
            }
        }

        // Discard stale state immediately when the active deck or its bindings change.
        heldCustomDeckKeys.retainAll(boundKeys);
        if (boundKeys.isEmpty()) return;

        boolean canDispatch = client.player != null
            && client.gui.screen() == null
            && client.gui.overlay() == null
            && client.isWindowActive();
        Set<Integer> processedKeys = new LinkedHashSet<>();
        for (ActionSlot slot : slots) {
            if (slot.skillId().isBlank() || slot.keyCode() == InputConstants.UNKNOWN.getValue()) continue;
            int keyCode = slot.keyCode();
            if (!processedKeys.add(keyCode)) continue;

            boolean down = InputConstants.isKeyDown(keyCode);
            boolean justPressed = down && heldCustomDeckKeys.add(keyCode);
            if (!down) {
                heldCustomDeckKeys.remove(keyCode);
                continue;
            }
            if (canDispatch && justPressed) {
                // The first assigned slot wins when a player deliberately creates a duplicate key.
                ShyneClientNetworking.sendCustomDeckSkill(slot.skillId());
            }
        }
    }
}

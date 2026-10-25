package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbModelParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PinkDeltaruneDirectTest {

    @Test
    void testPinkDeltaruneAvatarLifecycle() throws Exception {
        Path scratchDir = Path.of("C:\\Users\\seash\\.gemini\\antigravity\\brain\\a8507e56-cb46-4357-8de5-ac3fa1ed5d0c\\scratch\\pink_deltarune");
        if (!Files.isDirectory(scratchDir)) {
            System.out.println("Scratch dir does not exist, skipping");
            return;
        }

        Path modelPath = scratchDir.resolve("model.bbmodel");
        Path scriptPath = scratchDir.resolve("script.lua");

        BbModelDefinition model = BbModelParser.parse(modelPath, "custom");
        AvatarState state = new AvatarState(
            "pink_deltarune", "model", scratchDir, false,
            Set.of(AvatarPermission.SOUND, AvatarPermission.PARTICLE, AvatarPermission.CHAT_NAMEPLATE),
            Set.of(AvatarPermission.SOUND, AvatarPermission.PARTICLE, AvatarPermission.CHAT_NAMEPLATE)
        );
        state.bindEntity(java.util.UUID.randomUUID());

        ClientLuaAvatarRuntime runtime = new ClientLuaAvatarRuntime(state, model, scriptPath);
        boolean loaded = runtime.load();
        System.out.println("Runtime loaded: " + loaded);
        if (!loaded && runtime.lastLoadError() != null) {
            System.err.println("Load error: " + runtime.lastLoadError().cleanMessage());
        }
        assertTrue(loaded, "Expected script.lua to load without errors");

        // Verify multiple events.tick and events.render listeners were registered without overwriting
        int tickListeners = runtime.eval("return events.TICK:getRegisteredCount()").toint();
        int renderListeners = runtime.eval("return events.RENDER:getRegisteredCount()").toint();
        System.out.println("TICK listeners registered: " + tickListeners);
        System.out.println("RENDER listeners registered: " + renderListeners);
        assertTrue(tickListeners >= 12, "Expected at least 12 tick listeners registered (all physics parts + script.lua), got: " + tickListeners);
        assertTrue(renderListeners >= 12, "Expected at least 12 render listeners registered (all physics parts + script.lua), got: " + renderListeners);

        System.out.println("Dispatching entity_init...");
        runtime.entityInit(null);

        for (int i = 1; i <= 5; i++) {
            System.out.println("Dispatching tick " + i + "...");
            runtime.tick(null);
            System.out.println("Dispatching render " + i + "...");
            runtime.render(0.5f, "OTHER");
        }

        System.out.println("Dispatching item_render for trident...");
        runtime.itemRender("minecraft:trident", "THIRD_PERSON", false);

        System.out.println("Dispatching item_render for snowball...");
        runtime.itemRender("minecraft:snowball", "THIRD_PERSON", false);

        System.out.println("All dispatches executed cleanly without errors!");
    }
}

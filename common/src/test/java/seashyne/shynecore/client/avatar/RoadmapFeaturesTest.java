package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seashyne.shynecore.client.avatar.importer.AvatarDragDropInstaller;
import seashyne.shynecore.client.compat.FirstPersonModelCompat;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbModelParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class RoadmapFeaturesTest {

    private static final String SAMPLE_BBMODEL = """
        {
          "meta": { "format_version": "4.10", "model_format": "free", "box_uv": false },
          "name": "roadmap_hero",
          "resolution": { "width": 64, "height": 64 },
          "elements": [
            {
              "name": "head_cube",
              "uuid": "c-head",
              "from": [-4, 24, -4],
              "to": [4, 32, 4],
              "faces": { "north": { "uv": [0, 0, 8, 8], "texture": 0 } }
            },
            {
              "name": "tail_cube",
              "uuid": "c-tail",
              "from": [-1, 8, 2],
              "to": [1, 12, 8],
              "faces": { "north": { "uv": [0, 0, 2, 4], "texture": 0 } }
            }
          ],
          "outliner": [
            {
              "name": "Head",
              "uuid": "b-head",
              "origin": [0, 24, 0],
              "children": [
                "c-head",
                {
                  "name": "HairBangs",
                  "uuid": "b-hair",
                  "origin": [0, 30, -4],
                  "children": []
                }
              ]
            },
            {
              "name": "Body",
              "uuid": "b-body",
              "origin": [0, 24, 0],
              "children": [
                {
                  "name": "Tail",
                  "uuid": "b-tail",
                  "origin": [0, 12, 2],
                  "children": ["c-tail"]
                },
                {
                  "name": "Cape",
                  "uuid": "b-cape",
                  "origin": [0, 24, 2],
                  "children": []
                }
              ]
            }
          ],
          "textures": [
            { "name": "skin.png", "width": 64, "height": 64, "source": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==" }
          ],
          "animations": [
            {
              "name": "idle",
              "loop": "loop",
              "length": 1.0,
              "animators": {}
            },
            {
              "name": "walk",
              "loop": "loop",
              "length": 1.0,
              "animators": {}
            }
          ]
        }
        """;

    @Test
    void phase1UserLayerPrefsToggleAndPersist(@TempDir Path tempDir) throws Exception {
        AvatarUserLayerPrefs.setCustomPrefsPathForTest(tempDir.resolve("avatar_layer_prefs.json"));
        try {
            Set<AvatarPermission> allPerms = EnumSet.allOf(AvatarPermission.class);
            AvatarState state = new AvatarState(
                "wing_hero", "avatar:wing_hero", tempDir, true,
                allPerms, allPerms
            );

            assertTrue(AvatarUserLayerPrefs.isLayerVisible(state, AvatarUserLayerPrefs.LAYER_ELYTRA));
            assertFalse(AvatarUserLayerPrefs.toggleLayer(state, AvatarUserLayerPrefs.LAYER_ELYTRA));
            assertFalse(AvatarUserLayerPrefs.isGroupVisible(state, "ELYTRA"));

            assertFalse(AvatarUserLayerPrefs.toggleLayer(state, AvatarUserLayerPrefs.LAYER_ARMOR));
            assertFalse(AvatarUserLayerPrefs.isGroupVisible(state, "ARMOR"));
            assertFalse(AvatarUserLayerPrefs.isGroupVisible(state, "HELMET"));
            assertFalse(AvatarUserLayerPrefs.isGroupVisible(state, "CHESTPLATE"));

            // Create a fresh AvatarState and verify saved prefs re-apply automatically
            AvatarState reloadedState = new AvatarState(
                "wing_hero", "avatar:wing_hero", tempDir, true,
                allPerms, allPerms
            );
            AvatarUserLayerPrefs.applySavedPrefs(reloadedState);
            assertFalse(AvatarUserLayerPrefs.isGroupVisible(reloadedState, "ELYTRA"));
            assertFalse(AvatarUserLayerPrefs.isGroupVisible(reloadedState, "ARMOR"));
            assertTrue(AvatarUserLayerPrefs.isGroupVisible(reloadedState, "CAPE"));
        } finally {
            AvatarUserLayerPrefs.setCustomPrefsPathForTest(null);
        }
    }

    @Test
    void phase1DragDropInstallerSupportsBbmodelZipAndFolder(@TempDir Path tempDir) throws Exception {
        Path avatarsDir = tempDir.resolve("avatars");
        Path incomingDir = tempDir.resolve("incoming");
        Files.createDirectories(incomingDir);

        // 1. Standalone .bbmodel file
        Path standaloneBb = incomingDir.resolve("CuteFox.bbmodel");
        Files.writeString(standaloneBb, SAMPLE_BBMODEL, StandardCharsets.UTF_8);

        // 2. Folder containing a .bbmodel
        Path rawFolder = incomingDir.resolve("FolderAvatar");
        Files.createDirectories(rawFolder);
        Files.writeString(rawFolder.resolve("model.bbmodel"), SAMPLE_BBMODEL, StandardCharsets.UTF_8);

        // 3. .zip archive containing a .bbmodel
        Path zipFile = incomingDir.resolve("ZippedAvatar.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("model.bbmodel"));
            zos.write(SAMPLE_BBMODEL.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        var result = AvatarDragDropInstaller.installDroppedFiles(List.of(standaloneBb, rawFolder, zipFile), avatarsDir);
        assertEquals(3, result.installedCount(), "All 3 dropped items should be installed");
        assertTrue(Files.isRegularFile(avatarsDir.resolve("cutefox").resolve("avatar.json")));
        assertTrue(Files.isRegularFile(avatarsDir.resolve("FolderAvatar").resolve("avatar.json")));
        assertTrue(Files.isRegularFile(avatarsDir.resolve("ZippedAvatar").resolve("avatar.json")));
    }

    @Test
    void phase2FirstPersonModelCompatIdentifiesHeadHierarchy(@TempDir Path tempDir) throws Exception {
        Path modelFile = tempDir.resolve("hero.bbmodel");
        Files.writeString(modelFile, SAMPLE_BBMODEL, StandardCharsets.UTF_8);
        BbModelDefinition model = BbModelParser.parse(modelFile, "hero");

        var headBone = model.findBoneByUuid("b-head");
        var hairBone = model.findBoneByUuid("b-hair");
        var bodyBone = model.findBoneByUuid("b-body");
        var tailBone = model.findBoneByUuid("b-tail");

        assertNotNull(headBone);
        assertNotNull(hairBone);
        assertNotNull(bodyBone);
        assertNotNull(tailBone);

        assertTrue(FirstPersonModelCompat.isHeadOrDescendantBone(model, headBone, null), "Head root should be identified");
        assertTrue(FirstPersonModelCompat.isHeadOrDescendantBone(model, hairBone, null), "Child of Head (HairBangs) should be identified");
        assertFalse(FirstPersonModelCompat.isHeadOrDescendantBone(model, bodyBone, null), "Body bone should not be masked as head");
        assertFalse(FirstPersonModelCompat.isHeadOrDescendantBone(model, tailBone, null), "Tail bone should not be masked as head");
    }

    @Test
    void phase3BuiltInFiguraLibrariesLoadViaRequire(@TempDir Path tempDir) throws Exception {
        assertTrue(ShyneLibraryManager.isBuiltIn("GSAnimBlend"));
        assertTrue(ShyneLibraryManager.isBuiltIn("libs.GSAnimBlend"));
        assertTrue(ShyneLibraryManager.isBuiltIn("EZAnims"));
        assertTrue(ShyneLibraryManager.isBuiltIn("KattArmor"));

        Path modelFile = tempDir.resolve("hero.bbmodel");
        Files.writeString(modelFile, SAMPLE_BBMODEL, StandardCharsets.UTF_8);
        BbModelDefinition model = BbModelParser.parse(modelFile, "hero").withModelId("avatar:hero");

        String luaScript = """
            local gs = require("libs.GSAnimBlend")
            local ez = require("EZAnims")
            local katt = require("KattArmor")()

            animations.hero.idle:blendTime(6, 10):blendCurve(gs.easeInOutSine)
            ez:setBlendTime(5)
            ez(animations.hero)
            katt.Armor.Helmet:addParts(models.hero.Head.HairBangs)
            _G.TEST_GS_CURVE = gs.easeInOutSine(0.5)
            _G.TEST_BLEND_IN, _G.TEST_BLEND_OUT = animations.hero.idle:getBlendTime()
            """;
        Path scriptFile = tempDir.resolve("main.lua");
        Files.writeString(scriptFile, luaScript, StandardCharsets.UTF_8);

        Set<AvatarPermission> allPerms = EnumSet.allOf(AvatarPermission.class);
        AvatarState state = new AvatarState("hero", "avatar:hero", tempDir, true, allPerms, allPerms);
        state.bindEntity(UUID.randomUUID());
        ClientLuaAvatarRuntime runtime = new ClientLuaAvatarRuntime(state, model, scriptFile);
        assertTrue(runtime.load(), "Script requiring GSAnimBlend, EZAnims, and KattArmor should load cleanly");
        assertEquals("0.5", runtime.evalToString("_G.TEST_GS_CURVE"));
        assertEquals("6", runtime.evalToString("_G.TEST_BLEND_IN"));
        assertEquals("10", runtime.evalToString("_G.TEST_BLEND_OUT"));
    }

    @Test
    void phase4SmartPhysicsCrouchLiftAndAntiClipClamp(@TempDir Path tempDir) throws Exception {
        Path modelFile = tempDir.resolve("hero.bbmodel");
        Files.writeString(modelFile, SAMPLE_BBMODEL, StandardCharsets.UTF_8);
        BbModelDefinition model = BbModelParser.parse(modelFile, "hero").withModelId("avatar:hero");

        Set<AvatarPermission> allPerms = EnumSet.allOf(AvatarPermission.class);
        AvatarState standingState = new AvatarState("hero", "avatar:hero", tempDir, true, allPerms, allPerms);
        AvatarState crouchingState = new AvatarState("hero", "avatar:hero", tempDir, true, allPerms, allPerms);

        AvatarPhysicsController standingPhysics = new AvatarPhysicsController(model);
        AvatarPhysicsController crouchingPhysics = new AvatarPhysicsController(model);
        standingPhysics.setBonePhysics("model.Body.Tail", new AvatarPhysicsController.PhysicsConfig(0.18, 0.78, 0.0, 44.0, 0.5, 0.5, "tail"));
        crouchingPhysics.setBonePhysics("model.Body.Tail", new AvatarPhysicsController.PhysicsConfig(0.18, 0.78, 0.0, 44.0, 0.5, 0.5, "tail"));

        var standingSignals = new AvatarPhysicsController.Signals(0, 0, 0, 0, 0, true, false, 100L, false, false);
        var crouchingSignals = new AvatarPhysicsController.Signals(0, 0, 0, 0, 0, true, false, 100L, true, false);

        for (int i = 0; i < 15; i++) {
            standingPhysics.tick(standingSignals, standingState);
            crouchingPhysics.tick(crouchingSignals, crouchingState);
        }

        AvatarPartState standingTail = standingState.parts().get("model.Body.Tail");
        AvatarPartState crouchingTail = crouchingState.parts().get("model.Body.Tail");
        assertNotNull(standingTail);
        assertNotNull(crouchingTail);
        assertTrue(Math.abs(crouchingTail.additiveRotX() - standingTail.additiveRotX()) > 10.0f,
            "Crouching should lift the tail significantly compared to standing to prevent ground clipping");

        // Test backward velocity anti-clipping on Cape
        AvatarState backwardState = new AvatarState("hero", "avatar:hero", tempDir, true, allPerms, allPerms);
        AvatarPhysicsController backwardPhysics = new AvatarPhysicsController(model);
        backwardPhysics.setBonePhysics("model.Body.Cape", new AvatarPhysicsController.PhysicsConfig(0.2, 0.7, 0.0, 45.0, 0.5, 0.5, "cloth"));
        var backwardSignals = new AvatarPhysicsController.Signals(-1.2, 0, -0.5, 0, 0, true, false, 100L, false, false);
        for (int i = 0; i < 15; i++) {
            backwardPhysics.tick(backwardSignals, backwardState);
        }
        AvatarPartState capePart = backwardState.parts().get("model.Body.Cape");
        assertNotNull(capePart);
        assertTrue(capePart.additiveRotX() >= -5.01f,
            "Cape pitch should be clamped >= -5 degrees so it never clips through the player's chest: actual=" + capePart.additiveRotX());
    }
}

package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbModelParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarLoaderTest {
    @TempDir Path temp;

    @Test
    void nameOnlyManifestUsesLatestStandardDefaults() throws Exception {
        Path root = temp.resolve("Deep-ShyneCore");
        Files.createDirectories(root);
        Files.writeString(root.resolve("avatar.json"), "{\"name\":\"Deep\"}");
        Files.writeString(root.resolve("model.bbmodel"), "{}");

        AvatarManifest manifest = AvatarLoader.loadManifest(root);

        assertAll(
            () -> assertEquals("2.0", manifest.standard()),
            () -> assertEquals("2.0", manifest.api()),
            () -> assertTrue(manifest.automaticApi()),
            () -> assertTrue(manifest.apiRequirements().isEmpty()),
            () -> assertEquals("deep-shynecore", manifest.id()),
            () -> assertEquals("Deep", manifest.name()),
            () -> assertEquals("1.0.0", manifest.version()),
            () -> assertEquals("", manifest.main()),
            () -> assertFalse(manifest.hasScript()),
            () -> assertEquals("model.bbmodel", manifest.model()),
            () -> assertEquals("accessory", manifest.profile()),
            () -> assertFalse(manifest.replaceVanilla()),
            () -> assertTrue(manifest.onlineSync()),
            () -> assertFalse(manifest.firstPersonMasking()),
            () -> assertFalse(manifest.localCamera()),
            () -> assertEquals("manifest", manifest.textureSyncMode()),
            () -> assertTrue(manifest.behavior().automatic()),
            () -> assertTrue(manifest.textures().isEmpty()),
            () -> assertTrue(manifest.permissions().isEmpty())
        );
    }

    @Test
    void explicitNativeLuaUsesApiTwoAndSemanticRequirementsAreChecked() throws Exception {
        Path root = temp.resolve("api-avatar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("script.lua"), "return true");
        Files.writeString(root.resolve("model.bbmodel"), "{}");
        Files.writeString(root.resolve("avatar.json"), """
            {"standard":"2.0","name":"Modern","main":"script.lua","api":"2.0","requires":{"behavior":">=2.0","rig":">=1.3"}}
            """);
        AvatarManifest modern = AvatarLoader.loadManifest(root);
        assertEquals("2.0", modern.api());
        assertTrue(modern.hasScript());
        assertEquals(">=2.0", modern.apiRequirements().get("behavior"));

        Files.writeString(root.resolve("avatar.json"), """
            {"name":"Too New","main":"script.lua","api":"2.0","requires":{"behavior":">=3.0"}}
            """);
        IOException error = assertThrows(IOException.class, () -> AvatarLoader.loadManifest(root));
        assertTrue(error.getMessage().contains("requires >=3.0"));
    }

    @Test
    void parsesKnownPublicPermissionsAndRejectsUnknownOnes() throws Exception {
        Path root = temp.resolve("permission-avatar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("script.lua"), "return true");
        Files.writeString(root.resolve("model.bbmodel"), "{}");
        Files.writeString(root.resolve("avatar.json"), """
            {"name":"Permission Test","permissions":["particle","camera","command","hud_render","world_render"]}
            """);

        AvatarManifest manifest = AvatarLoader.loadManifest(root);
        assertEquals(
            java.util.Set.of(AvatarPermission.PARTICLE, AvatarPermission.CAMERA, AvatarPermission.COMMAND,
                AvatarPermission.HUD_RENDER, AvatarPermission.WORLD_RENDER),
            manifest.permissions()
        );

        Files.writeString(root.resolve("avatar.json"), """
            {"name":"Permission Test","permissions":["filesystem"]}
            """);
        IOException error = assertThrows(IOException.class, () -> AvatarLoader.loadManifest(root));
        assertTrue(error.getMessage().contains("unsupported avatar permission"));
    }

    @Test
    void explicitUnsupportedStandardStillFails() throws Exception {
        Path root = temp.resolve("future-avatar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("avatar.json"), "{\"standard\":\"3.0\",\"name\":\"Future\"}");
        Files.writeString(root.resolve("model.bbmodel"), "{}");

        IOException error = assertThrows(IOException.class, () -> AvatarLoader.loadManifest(root));
        assertTrue(error.getMessage().contains("expected 2.0"));
    }

    @Test
    void compatibilityModesAreRejectedByStandardTwo() throws Exception {
        Path root = temp.resolve("compat-avatar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("script.lua"), "return true");
        Files.writeString(root.resolve("model.bbmodel"), "{}");
        Files.writeString(root.resolve("avatar.json"), "{\"name\":\"Compat\",\"compatibility\":\"legacy\"}");

        IOException error = assertThrows(IOException.class, () -> AvatarLoader.loadManifest(root));
        assertTrue(error.getMessage().contains("compatibility modes are not supported"));
    }

    @Test
    void legacyApiSelectorsAreRejectedByStandardTwo() throws Exception {
        Path root = temp.resolve("legacy-api-avatar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("model.bbmodel"), "{}");
        Files.writeString(root.resolve("avatar.json"), "{\"name\":\"Legacy API\",\"api\":\"1.3\"}");

        IOException oldStandard = assertThrows(IOException.class, () -> AvatarLoader.loadManifest(root));
        assertTrue(oldStandard.getMessage().contains("unsupported Shyne Lua API 1.3"));

        Files.writeString(root.resolve("avatar.json"), "{\"name\":\"Legacy Field\",\"api_version\":1}");
        IOException oldField = assertThrows(IOException.class, () -> AvatarLoader.loadManifest(root));
        assertTrue(oldField.getMessage().contains("api_version is not supported"));
    }

    @Test
    void profileAndDeclarativeBehaviorSupplyRuntimeDefaults() throws Exception {
        Path root = temp.resolve("full-body-avatar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("model.bbmodel"), "{}");
        Files.writeString(root.resolve("avatar.json"), """
            {
              "standard":"2.0",
              "name":"Full Body",
              "profile":"full_body",
              "behavior":{"animations":{"idle":"Idle","walk":["Walk","Move"]},"blend_ticks":8,"blink":false}
            }
            """);

        AvatarManifest manifest = AvatarLoader.loadManifest(root);
        assertTrue(manifest.replaceVanilla());
        assertTrue(manifest.firstPersonMasking());
        assertTrue(manifest.localCamera());
        assertEquals(8, manifest.behavior().blendTicks());
        assertEquals(java.util.List.of("Walk", "Move"), manifest.behavior().candidates("walk"));
        assertFalse(manifest.behavior().blink().enabled());
    }

    @Test
    void invalidBehaviorPresetAndStateAreRejectedByLoader() throws Exception {
        Path root = temp.resolve("invalid-behavior-avatar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("model.bbmodel"), "{}");
        Files.writeString(root.resolve("avatar.json"), "{\"name\":\"Bad Preset\",\"behavior\":\"automatic\"}");
        IOException preset = assertThrows(IOException.class, () -> AvatarLoader.loadManifest(root));
        assertTrue(preset.getMessage().contains("unsupported behavior preset"));

        Files.writeString(root.resolve("avatar.json"), """
            {"name":"Bad State","behavior":{"animations":{"jump":"Jump"}}}
            """);
        IOException state = assertThrows(IOException.class, () -> AvatarLoader.loadManifest(root));
        assertTrue(state.getMessage().contains("unsupported behavior animation state"));
    }

    @Test
    void validatorRejectsConfiguredBlinkThatCannotResolve() throws Exception {
        Path root = temp.resolve("invalid-blink-avatar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("avatar.json"), """
            {"name":"Bad Blink","behavior":{"blink":"Blnik"}}
            """);
        Files.writeString(root.resolve("model.bbmodel"), """
            {
              "resolution":{"width":16,"height":16},
              "textures":[],
              "elements":[],
              "outliner":[],
              "animations":[{"name":"Idle","length":1.0,"loop":"loop","animators":{}}]
            }
            """);

        AvatarManifest manifest = AvatarLoader.loadManifest(root);
        BbModelDefinition model = BbModelParser.parse(root.resolve(manifest.model()), manifest.id());
        List<AvatarValidationReport.Issue> issues = new ArrayList<>();
        AvatarValidator.validateBehavior(model, manifest.behavior(), manifest.model(), issues);

        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("behavior_blink_missing")));
    }

    @Test
    void generatedBlockbenchOutfitNamesArePresentedAsReadableLabels() throws Exception {
        assertEquals("Outfit 1", AvatarOutfitLoader.displayName("1000012674", 1));
        assertEquals("formal outfit", AvatarOutfitLoader.displayName("formal_outfit", 2));
        assertEquals("Outfit", AvatarOutfitLoader.displayName("___", 3));

        Path root = temp.resolve("numeric-outfit-avatar");
        Path outfitFolder = Files.createDirectories(root.resolve("outfit"));
        assertTrue(ImageIO.write(new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB), "png", outfitFolder.resolve("1000012674.png").toFile()));

        AvatarOutfit outfit = AvatarOutfitLoader.discover(root).getFirst();
        assertEquals("1000012674", outfit.id());
        assertEquals("Outfit 1", outfit.name());
        assertEquals(AvatarOutfit.Mode.OVERLAY, outfit.mode());
        assertEquals(AvatarOutfit.Mode.OVERLAY, AvatarOutfitLoader.outfitMode("glow.overlay"));
        assertEquals(AvatarOutfit.Mode.REPLACE, AvatarOutfitLoader.outfitMode("full.replace"));
        assertEquals("glow", AvatarOutfitLoader.displayName("glow.overlay", 2));
        assertEquals("full", AvatarOutfitLoader.displayName("full.replace", 2));
    }

    @Test
    void figuraImportStringShortcutLoadsDefaultsAndDiscoversAssets() throws Exception {
        Path root = temp.resolve("figura-avatar-test");
        Files.createDirectories(root);
        Files.writeString(root.resolve("avatar.json"), """
            {
              "name": "Figura Test Avatar",
              "import": "figura",
              "compatibility": {"legacy": true}
            }
            """);
        Files.writeString(root.resolve("custom_model.bbmodel"), "{}");
        Files.writeString(root.resolve("texture.png"), "dummy-png");
        Files.writeString(root.resolve("script.lua"), "print('hello figura')");

        AvatarManifest manifest = AvatarLoader.loadManifest(root);

        assertTrue(manifest.isFiguraImport());
        assertEquals("figura", manifest.importSource());
        assertEquals("full_body", manifest.profile());
        assertEquals("custom_model.bbmodel", manifest.model());
        assertEquals("script.lua", manifest.main());
        assertTrue(manifest.textures().contains("texture.png"));
        assertTrue(manifest.permissions().contains(AvatarPermission.PARTICLE));
        assertTrue(manifest.permissions().contains(AvatarPermission.SOUND));
        assertTrue(manifest.permissions().contains(AvatarPermission.CAMERA));
    }

    @Test
    void figuraImportObjectSpecOverridesProfileAndDiscoversMainLua() throws Exception {
        Path root = temp.resolve("figura-obj-test");
        Files.createDirectories(root);
        Files.writeString(root.resolve("avatar.json"), """
            {
              "name": "Figura Obj Test",
              "import": {
                "type": "figura",
                "profile": "accessory",
                "replace_vanilla": true
              }
            }
            """);
        Files.writeString(root.resolve("model.bbmodel"), "{}");
        Files.writeString(root.resolve("main.lua"), "-- main script");

        AvatarManifest manifest = AvatarLoader.loadManifest(root);

        assertTrue(manifest.isFiguraImport());
        assertEquals("accessory", manifest.profile());
        assertTrue(manifest.replaceVanilla());
        assertEquals("main.lua", manifest.main());
    }

    @Test
    void figuraStandardPropertyTriggersFiguraImport() throws Exception {
        Path root = temp.resolve("figura-standard-test");
        Files.createDirectories(root);
        Files.writeString(root.resolve("avatar.json"), """
            {
              "name": "Figura Standard",
              "standard": "figura"
            }
            """);
        Files.writeString(root.resolve("model.bbmodel"), "{}");

        AvatarManifest manifest = AvatarLoader.loadManifest(root);

        assertTrue(manifest.isFiguraImport());
        assertEquals("figura", manifest.importSource());
        assertEquals("full_body", manifest.profile());
    }

    @Test
    void nonFiguraAvatarRejectsCompatibilityMode() throws Exception {
        Path root = temp.resolve("non-figura-compat-test");
        Files.createDirectories(root);
        Files.writeString(root.resolve("avatar.json"), """
            {
              "name": "Non Figura",
              "compatibility": {"legacy": true}
            }
            """);
        Files.writeString(root.resolve("model.bbmodel"), "{}");

        IOException ex = assertThrows(IOException.class, () -> AvatarLoader.loadManifest(root));
        assertTrue(ex.getMessage().contains("compatibility modes are not supported"));
    }
}

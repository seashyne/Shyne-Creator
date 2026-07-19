package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarLoaderTest {
    @TempDir Path temp;

    @Test
    void nameOnlyManifestUsesLatestStandardDefaults() throws Exception {
        Path root = temp.resolve("Deep-ShyneCore");
        Files.createDirectories(root);
        Files.writeString(root.resolve("avatar.json"), "{\"name\":\"Deep\"}");
        Files.writeString(root.resolve("script.lua"), "return true");
        Files.writeString(root.resolve("model.bbmodel"), "{}");

        AvatarManifest manifest = AvatarLoader.loadManifest(root);

        assertAll(
            () -> assertEquals(1, manifest.apiVersion()),
            () -> assertEquals("1.1", manifest.api()),
            () -> assertTrue(manifest.automaticApi()),
            () -> assertTrue(manifest.apiRequirements().isEmpty()),
            () -> assertEquals("deep-shynecore", manifest.id()),
            () -> assertEquals("Deep", manifest.name()),
            () -> assertEquals("1.0.0", manifest.version()),
            () -> assertEquals("script.lua", manifest.main()),
            () -> assertEquals("model.bbmodel", manifest.model()),
            () -> assertTrue(manifest.replaceVanilla()),
            () -> assertTrue(manifest.onlineSync()),
            () -> assertTrue(manifest.firstPersonMasking()),
            () -> assertTrue(manifest.localCamera()),
            () -> assertEquals("manifest", manifest.textureSyncMode()),
            () -> assertTrue(manifest.textures().isEmpty()),
            () -> assertTrue(manifest.permissions().isEmpty())
        );
    }

    @Test
    void legacyVersionLocksOnePointZeroAndSemanticRequirementsAreChecked() throws Exception {
        Path root = temp.resolve("api-avatar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("script.lua"), "return true");
        Files.writeString(root.resolve("model.bbmodel"), "{}");
        Files.writeString(root.resolve("avatar.json"), "{\"name\":\"Legacy\",\"api_version\":1}");

        AvatarManifest legacy = AvatarLoader.loadManifest(root);
        assertEquals("1.0", legacy.api());
        assertFalse(legacy.automaticApi());

        Files.writeString(root.resolve("avatar.json"), """
            {"name":"Modern","api":"1.1","requires":{"render":">=1.1","scheduler":"^1.1"}}
            """);
        AvatarManifest modern = AvatarLoader.loadManifest(root);
        assertEquals("1.1", modern.api());
        assertEquals(">=1.1", modern.apiRequirements().get("render"));

        Files.writeString(root.resolve("avatar.json"), """
            {"name":"Too New","api":"1.1","requires":{"render":">=2.0"}}
            """);
        IOException error = assertThrows(IOException.class, () -> AvatarLoader.loadManifest(root));
        assertTrue(error.getMessage().contains("requires >=2.0"));
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
    void explicitUnsupportedApiVersionStillFails() throws Exception {
        Path root = temp.resolve("future-avatar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("avatar.json"), "{\"name\":\"Future\",\"api_version\":99}");
        Files.writeString(root.resolve("script.lua"), "return true");
        Files.writeString(root.resolve("model.bbmodel"), "{}");

        IOException error = assertThrows(IOException.class, () -> AvatarLoader.loadManifest(root));
        assertTrue(error.getMessage().contains("unsupported avatar api_version 99"));
    }
}

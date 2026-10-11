package seashyne.shynecore.client.avatar.importer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seashyne.shynecore.client.avatar.AvatarPermission;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AvatarImporterTest {

    @Test
    void testConvertFiguraProjectWithAttributionAndPermissions(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("my_figura_avatar");
        Files.createDirectories(projectDir);

        // 1. Create a minimal bbmodel
        String minimalBbmodel = """
            {
              "meta": { "model_format": "free" },
              "name": "Dragon Knight",
              "elements": [
                {
                  "name": "body",
                  "from": [0, 0, 0],
                  "to": [4, 12, 4],
                  "type": "cube"
                }
              ],
              "outliner": [
                {
                  "name": "root",
                  "uuid": "00000000-0000-0000-0000-000000000001",
                  "origin": [0, 0, 0],
                  "children": [0]
                }
              ],
              "textures": [],
              "animations": []
            }
            """;
        Files.writeString(projectDir.resolve("model.bbmodel"), minimalBbmodel, StandardCharsets.UTF_8);

        // 2. Create a script using sounds, particles, and pings
        String luaScript = """
            events.TICK:register(function()
                sounds:playSound("minecraft:entity.dragon.growl", player:getPos())
                particles:newParticle("minecraft:flame", player:getPos())
                pings.sendHello()
            end)
            """;
        Files.writeString(projectDir.resolve("avatar.lua"), luaScript, StandardCharsets.UTF_8);

        // 3. Create metadata.json with author
        String metadata = """
            {
              "name": "Dragon Knight",
              "authors": ["MasterDragon", "SeaShyne"],
              "description": "Epic dragon warrior avatar."
            }
            """;
        Files.writeString(projectDir.resolve("metadata.json"), metadata, StandardCharsets.UTF_8);

        // Analyze before convert
        AvatarImportReport reportBefore = AvatarImporter.analyze(projectDir);
        assertEquals(AvatarImportReport.SourceType.FIGURA_AVATAR, reportBefore.sourceType());
        assertTrue(reportBefore.author().contains("MasterDragon"));
        assertTrue(reportBefore.scriptStats().detectedApis().contains("sounds"));
        assertTrue(reportBefore.scriptStats().detectedApis().contains("particles"));
        assertTrue(reportBefore.scriptStats().detectedApis().contains("pings"));

        // Convert in-place
        AvatarImporter.ConversionResult result = AvatarImporter.convert(projectDir);
        assertTrue(result.success(), "Conversion should succeed: " + result.message());

        // Verify generated avatar.json
        Path manifestPath = projectDir.resolve("avatar.json");
        assertTrue(Files.isRegularFile(manifestPath));
        JsonObject manifest = JsonParser.parseString(Files.readString(manifestPath)).getAsJsonObject();

        assertTrue("standard".equals(manifest.get("standard").getAsString()) || "2.0".equals(manifest.get("standard").getAsString()));
        assertEquals("Dragon Knight", manifest.get("name").getAsString());
        assertEquals("model.bbmodel", manifest.get("model").getAsString());
        assertEquals("avatar.lua", manifest.get("main").getAsString());
        assertEquals("figura", manifest.get("importSource").getAsString());
        assertTrue(manifest.get("author").getAsString().contains("MasterDragon"));

        // Verify auto-declared permissions
        assertTrue(manifest.has("permissions"));
        List<String> perms = new ArrayList<>();
        manifest.getAsJsonArray("permissions").forEach(el -> perms.add(el.getAsString()));
        assertTrue(perms.contains("sound"), "Should auto-declare sound permission");
        assertTrue(perms.contains("particle"), "Should auto-declare particle permission");
        assertTrue(perms.contains("network"), "Should auto-declare network permission");

        // Verify attribution block
        assertTrue(manifest.has("attribution"));
        JsonObject attr = manifest.getAsJsonObject("attribution");
        assertTrue(attr.get("originalAuthor").getAsString().contains("MasterDragon"));
        assertEquals("Figura", attr.get("sourcePlatform").getAsString());
        assertEquals("FIGURA_AVATAR", attr.get("sourceType").getAsString());
        assertTrue(attr.has("convertedBy"));
        assertTrue(attr.has("convertedAt"));
        assertTrue(attr.has("notice"));

        // Verify ATTRIBUTION.md
        Path attrMd = projectDir.resolve("ATTRIBUTION.md");
        assertTrue(Files.isRegularFile(attrMd));
        String attrContent = Files.readString(attrMd);
        assertTrue(attrContent.contains("Dragon Knight"));
        assertTrue(attrContent.contains("MasterDragon"));
        assertTrue(attrContent.contains("Figura"));
        assertTrue(attrContent.contains("clean-room"));

        // Verify avatar_import_report.md
        Path reportMd = projectDir.resolve("avatar_import_report.md");
        assertTrue(Files.isRegularFile(reportMd));
    }

    @Test
    void testScanUnimportedAndFindAvatarDirectory(@TempDir Path tempDir) throws IOException {
        Path avatarsRoot = tempDir.resolve("avatars");
        Files.createDirectories(avatarsRoot);

        // 1. Unimported folder with bbmodel
        Path unimportedFolder = avatarsRoot.resolve("raw_model");
        Files.createDirectories(unimportedFolder);
        Files.writeString(unimportedFolder.resolve("model.bbmodel"), "{}", StandardCharsets.UTF_8);

        // 2. Already imported folder with avatar.json
        Path importedFolder = avatarsRoot.resolve("valid_avatar");
        Files.createDirectories(importedFolder);
        Files.writeString(importedFolder.resolve("avatar.json"), "{\"standard\":\"2.0\",\"id\":\"valid_avatar\",\"name\":\"Valid Avatar\"}", StandardCharsets.UTF_8);

        // 3. Dot directory (should be ignored)
        Path dotFolder = avatarsRoot.resolve(".extracted");
        Files.createDirectories(dotFolder);
        Files.writeString(dotFolder.resolve("model.bbmodel"), "{}", StandardCharsets.UTF_8);

        // Test scanUnimported
        List<Path> unimported = AvatarImporter.scanUnimported(avatarsRoot);
        assertEquals(1, unimported.size());
        assertEquals("raw_model", unimported.get(0).getFileName().toString());

        // Test findAvatarDirectory
        Optional<Path> foundByName = AvatarImporter.findAvatarDirectory(avatarsRoot, "raw_model");
        assertTrue(foundByName.isPresent());
        assertEquals(unimportedFolder.toRealPath(), foundByName.get().toRealPath());

        Optional<Path> foundById = AvatarImporter.findAvatarDirectory(avatarsRoot, "valid_avatar");
        assertTrue(foundById.isPresent());
        assertEquals(importedFolder.toRealPath(), foundById.get().toRealPath());

        Optional<Path> notFound = AvatarImporter.findAvatarDirectory(avatarsRoot, "non_existent");
        assertTrue(notFound.isEmpty());
    }

    @Test
    void testAvatarCommandHelperExecution(@TempDir Path tempDir) throws IOException {
        Path avatarsRoot = tempDir.resolve("avatars");
        Files.createDirectories(avatarsRoot);

        List<String> messages = new ArrayList<>();

        // Test list unimported when empty
        int code = AvatarCommandHelper.executeListUnimported(avatarsRoot, comp -> messages.add(comp.getString()));
        assertEquals(1, code);
        assertTrue(messages.get(messages.size() - 1).contains("No unimported"));

        // Create an unimported candidate
        Path raw = avatarsRoot.resolve("samurai");
        Files.createDirectories(raw);
        Files.writeString(raw.resolve("model.bbmodel"), "{\"name\":\"Samurai\",\"elements\":[],\"outliner\":[]}", StandardCharsets.UTF_8);

        // Test diagnose on missing target
        messages.clear();
        int diagMissing = AvatarCommandHelper.executeDiagnose(avatarsRoot, "random_target_xyz", comp -> messages.add(comp.getString()));
        assertEquals(0, diagMissing);
        assertTrue(messages.get(0).contains("not found"));

        // Test diagnose on candidate
        messages.clear();
        int diagSuccess = AvatarCommandHelper.executeDiagnose(avatarsRoot, "samurai", comp -> messages.add(comp.getString()));
        assertEquals(1, diagSuccess);
        assertTrue(messages.stream().anyMatch(m -> m.contains("Diagnostics")));
    }
}

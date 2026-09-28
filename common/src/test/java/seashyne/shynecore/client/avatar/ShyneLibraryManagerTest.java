package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ShyneLibraryManagerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ShyneLibraryManager.setCustomLibsDir(tempDir.resolve("libs"));
    }

    @AfterEach
    void tearDown() {
        ShyneLibraryManager.setCustomLibsDir(null);
    }

    @Test
    void builtInLibrariesAreRecognized() {
        assertTrue(ShyneLibraryManager.isBuiltIn("classic"));
        assertTrue(ShyneLibraryManager.isBuiltIn("tween"));
        assertTrue(ShyneLibraryManager.isBuiltIn("inspect"));
        assertTrue(ShyneLibraryManager.isBuiltIn("noise"));
        assertTrue(ShyneLibraryManager.isBuiltIn("vector"));
        assertTrue(ShyneLibraryManager.isBuiltIn("signal"));
        assertTrue(ShyneLibraryManager.isBuiltIn("color"));
        assertTrue(ShyneLibraryManager.isBuiltIn("timer"));
        assertFalse(ShyneLibraryManager.isBuiltIn("non_existent_fake_lib_xyz"));
    }

    @Test
    void resolvesCachedLibraryFromCustomLibsDir() throws IOException {
        Path libsDir = tempDir.resolve("libs");
        Files.createDirectories(libsDir);
        Path testLib = libsDir.resolve("custom_physics.lua");
        Files.writeString(testLib, "return { name = 'custom_physics' }");

        Path resolved = ShyneLibraryManager.resolveCachedLibrary(null, "custom_physics");
        assertNotNull(resolved);
        assertEquals(testLib.toAbsolutePath().normalize(), resolved.toAbsolutePath().normalize());

        List<String> installed = ShyneLibraryManager.listInstalledCachedLibraries();
        assertTrue(installed.contains("custom_physics"));
    }

    @Test
    void candidateDirsIncludeAvatarParentAndGrandParent() {
        Path avatarRoot = tempDir.resolve("shyne-mods").resolve("avatars").resolve("my_avatar");
        List<Path> candidateDirs = ShyneLibraryManager.getCandidateLibraryDirs(avatarRoot);

        assertNotNull(candidateDirs);
        assertFalse(candidateDirs.isEmpty());
        // Verify primary libs dir is present
        assertTrue(candidateDirs.contains(tempDir.resolve("libs").toAbsolutePath().normalize()));
        assertTrue(candidateDirs.contains(tempDir.resolve("shyne-mods").resolve("libraries")));
    }

    @Test
    void resolvesNestedModuleFromCacheAndSharedLibraries() throws IOException {
        Path nested = tempDir.resolve("libs").resolve("ui").resolve("hud.lua");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "return {}");
        assertEquals(nested, ShyneLibraryManager.resolveCachedLibrary(null, "ui.hud"));
        assertEquals(nested, ShyneLibraryManager.resolveCachedLibrary(null, "ui/hud"));
        assertTrue(ShyneLibraryManager.listInstalledCachedLibraries().contains("ui.hud"));

        Path avatarRoot = tempDir.resolve("shyne-mods/avatars/my_avatar");
        Path shared = tempDir.resolve("shyne-mods/libraries/physics/init.lua");
        Files.createDirectories(shared.getParent());
        Files.writeString(shared, "return {}");
        assertEquals(shared, ShyneLibraryManager.resolveCachedLibrary(avatarRoot, "physics"));
    }

    @Test
    void rejectsModuleNamesThatCouldEscapeLibraryFolders() {
        assertTrue(ShyneLibraryManager.isValidModuleName("skills/jump"));
        assertTrue(ShyneLibraryManager.isValidModuleName("ui.hud"));
        for (String invalid : List.of("../secret", "ui//hud", "ui..hud", "/absolute", "C:\\secret")) {
            assertFalse(ShyneLibraryManager.isValidModuleName(invalid));
            assertNull(ShyneLibraryManager.resolveCachedLibrary(null, invalid));
        }
    }
}

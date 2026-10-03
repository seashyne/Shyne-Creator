package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luaj.vm2.LuaValue;
import seashyne.shynecore.client.avatar.compat.AvatarCompatibilityMatrix;
import seashyne.shynecore.client.avatar.importer.AvatarImportReport;
import seashyne.shynecore.client.avatar.importer.AvatarImporter;
import seashyne.shynecore.client.avatar.linter.AvatarLintIssue;
import seashyne.shynecore.client.avatar.linter.AvatarScriptLinter;
import seashyne.shynecore.client.avatar.template.AvatarTemplateManager;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbModelParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end compatibility test suite validating real avatar project structures,
 * Blockbench/Figura importer diagnostics, script linter, templates, and runtime hot-reload.
 */
public class RealAvatarCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void testTemplateScaffoldingAndValidation() throws IOException {
        List<AvatarTemplateManager.TemplateInfo> templates = AvatarTemplateManager.listTemplates();
        assertFalse(templates.isEmpty());

        for (AvatarTemplateManager.TemplateInfo template : templates) {
            Path avatarRoot = AvatarTemplateManager.scaffold(template.id(), tempDir, template.id() + "_avatar", template.displayName());
            assertTrue(Files.isDirectory(avatarRoot));
            assertTrue(Files.isRegularFile(avatarRoot.resolve("avatar.json")));
            assertTrue(Files.isRegularFile(avatarRoot.resolve("model.bbmodel")));
            assertTrue(Files.isRegularFile(avatarRoot.resolve("main.lua")));
            assertTrue(Files.isRegularFile(avatarRoot.resolve("README.md")));

            // Parse model
            BbModelDefinition model = BbModelParser.parse(avatarRoot.resolve("model.bbmodel"), "test");
            assertNotNull(model);
            assertFalse(model.bones().isEmpty());
            assertFalse(model.cubes().isEmpty());

            // Run importer analysis
            AvatarImportReport report = AvatarImporter.analyze(avatarRoot);
            assertNotNull(report);
            assertEquals(AvatarImportReport.SourceType.SHYNE_AVATAR, report.sourceType());
            assertTrue(report.isReady(), "Template " + template.id() + " should be ready out of the box");
            assertEquals(0, report.errorCount(), "Template " + template.id() + " should have 0 errors");

            String markdown = report.toMarkdown();
            assertTrue(markdown.contains(template.displayName()));
            assertTrue(markdown.contains("Model & Texture Metrics"));
        }
    }

    @Test
    void testLinterDetectsBlockedGlobalsAndMissingPermissions() {
        String badScript = """
            -- Test bad script
            local file = io.open("test.txt", "w")
            os.execute("calc")
            dofile("secret.lua")
            network.send("custom:chan", "payload")
            data:set("key", 123)
            nameplate.CHAT:setText("Prefix")
            """;

        Set<AvatarPermission> noPermissions = Set.of();
        List<AvatarLintIssue> issues = AvatarScriptLinter.lintScript("test.lua", badScript, noPermissions);

        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> "BLOCKED_IO".equals(i.code()) && i.line() == 2));
        assertTrue(issues.stream().anyMatch(i -> "BLOCKED_OS".equals(i.code()) && i.line() == 3));
        assertTrue(issues.stream().anyMatch(i -> "BLOCKED_LOADFILE".equals(i.code()) && i.line() == 4));
        assertTrue(issues.stream().anyMatch(i -> "MISSING_PERMISSION_NETWORK".equals(i.code()) && i.line() == 5));
        assertTrue(issues.stream().anyMatch(i -> "MISSING_PERMISSION_DATA".equals(i.code()) && i.line() == 6));
        assertTrue(issues.stream().anyMatch(i -> "MISSING_PERMISSION_CHAT_NAMEPLATE".equals(i.code()) && i.line() == 7));
    }

    @Test
    void testLinterDetectsSyntaxErrors() {
        String syntaxErrorScript = """
            function bad()
                if true then
                    print("missing end")
            """;

        List<AvatarLintIssue> issues = AvatarScriptLinter.lintScript("broken.lua", syntaxErrorScript, Set.of());
        assertTrue(issues.stream().anyMatch(i -> "SYNTAX_ERROR".equals(i.code())));
    }

    @Test
    void testRealAvatarRuntimeExecution() throws IOException {
        Path avatarDir = AvatarTemplateManager.scaffold("figura_compat", tempDir, "compat_wizard", "Compat Wizard");
        BbModelDefinition model = BbModelParser.parse(avatarDir.resolve("model.bbmodel"), "compat_wizard");

        AvatarState state = new AvatarState(
            "compat_wizard",
            "avatar:compat_wizard",
            avatarDir,
            false,
            Set.of(AvatarPermission.NAMEPLATE, AvatarPermission.CHAT_NAMEPLATE, AvatarPermission.TAB_LIST_NAMEPLATE, AvatarPermission.DATA_STORAGE, AvatarPermission.NETWORK),
            Set.of(AvatarPermission.NAMEPLATE, AvatarPermission.CHAT_NAMEPLATE, AvatarPermission.TAB_LIST_NAMEPLATE, AvatarPermission.DATA_STORAGE, AvatarPermission.NETWORK)
        );

        ClientLuaAvatarRuntime runtime = new ClientLuaAvatarRuntime(state, model, avatarDir.resolve("main.lua"));
        boolean loaded = runtime.load();
        if (!loaded && runtime.lastLoadError() != null) {
            System.err.println("LOAD ERROR: " + runtime.lastLoadError().formatted());
        }
        assertTrue(loaded, "figura_compat starter avatar must load successfully in runtime: " + (runtime.lastLoadError() != null ? runtime.lastLoadError().formatted() : ""));

        // Verify Nameplate styling and badge set by script
        assertTrue(state.nameplateText().contains("Compat Wizard"));
        assertTrue(state.nameplateText().contains("✦"));

        // Verify safe data storage was modified and persisted
        LuaValue stored = runtime.eval("data:get('login_count')");
        assertEquals(1, stored.toint());

        // Verify typed pings definition exists
        LuaValue pingDef = runtime.eval("pings.get_schema('wave')");
        assertTrue(pingDef.istable());
        assertEquals("string", pingDef.get(1).tojstring());
        assertEquals("number", pingDef.get(2).tojstring());

        // Test event dispatching
        runtime.itemUse("diamond_sword", "main_hand", "use", 1);
        runtime.damage(5.0f, "player", "uuid", true);
        runtime.input("mouse_scroll", 0, 0, 0, 0, 0.0, 1.0, "");
        runtime.input("key_press", 32, 57, 1, 0, 0.0, 0.0, "");

        runtime.dispose();
    }

    @Test
    void testLinePreciseHotReloadErrorReporting() throws IOException {
        Path avatarDir = AvatarTemplateManager.scaffold("minimal", tempDir, "reload_error_avatar", "Reload Avatar");
        Path scriptPath = avatarDir.resolve("main.lua");

        // Intentionally inject an error on line 4
        String brokenCode = """
            -- Line 1
            -- Line 2
            -- Line 3
            local foo = nil; foo:invalidMethodCall()
            """;
        Files.writeString(scriptPath, brokenCode);

        BbModelDefinition model = BbModelParser.parse(avatarDir.resolve("model.bbmodel"), "reload_error_avatar");
        AvatarState state = new AvatarState("reload_error_avatar", "avatar:reload_error_avatar", avatarDir, false, Set.of(), Set.of());

        ClientLuaAvatarRuntime runtime = new ClientLuaAvatarRuntime(state, model, scriptPath);
        boolean loaded = runtime.load();
        assertFalse(loaded, "Broken script must fail loading");

        var err = runtime.lastLoadError();
        assertNotNull(err, "Must record lastLoadError");
        assertEquals("main.lua", err.file());
        assertEquals(4, err.line(), "Error line must be precisely 4");
        assertTrue(err.formatted().contains("main.lua:4"), "Formatted error must contain main.lua:4");

        // Test AvatarActivationResult helper
        AvatarActivationResult result = AvatarActivationResult.failure("reload_error_avatar", err.formatted(), err.file(), err.line(), err.cleanMessage());
        assertFalse(result.success());
        assertEquals(4, result.errorLine());
        assertEquals("main.lua", result.errorFile());
        assertTrue(result.formattedError().startsWith("main.lua:4 - "));

        runtime.dispose();
    }

    @Test
    void testCompatibilityMatrixRegistryAndDoc() {
        var entries = AvatarCompatibilityMatrix.allEntries();
        assertFalse(entries.isEmpty());

        for (AvatarCompatibilityMatrix.Category cat : AvatarCompatibilityMatrix.Category.values()) {
            var catEntries = AvatarCompatibilityMatrix.byCategory(cat);
            assertFalse(catEntries.isEmpty(), "Category " + cat.name() + " must have registered entries");
        }

        String markdown = AvatarCompatibilityMatrix.generateMarkdownDocument();
        assertNotNull(markdown);
        assertTrue(markdown.contains("P1 Events"));
        assertTrue(markdown.contains("P2 Model & Render Contexts"));
        assertTrue(markdown.contains("P3 Nameplate 2.0"));
        assertTrue(markdown.contains("P4 Safe Creator Data & JSON"));
        assertTrue(markdown.contains("P5 Permissions & Quotas"));
        assertTrue(markdown.contains("P6 Network API & Typed Pings"));
        assertTrue(markdown.contains("P7 Creator Workflow"));
        assertTrue(markdown.contains("Support Tiers"));

        // Verify that 0 APIs are marked as Unsupported or Partial
        assertEquals(0, AvatarCompatibilityMatrix.byStatus(AvatarCompatibilityMatrix.SupportStatus.UNSUPPORTED).size(),
            "There must be 0 Unsupported APIs in the compatibility matrix");
        assertEquals(0, AvatarCompatibilityMatrix.byStatus(AvatarCompatibilityMatrix.SupportStatus.PARTIAL).size(),
            "There must be 0 Partial APIs in the compatibility matrix");

        try {
            Path file = Path.of("..", "docs", "api", "API_COMPATIBILITY_MATRIX.md");
            if (!Files.exists(file)) file = Path.of("docs", "api", "API_COMPATIBILITY_MATRIX.md");
            if (!Files.exists(file)) file = Path.of("..", "API_COMPATIBILITY_MATRIX.md");
            if (!Files.exists(file)) file = Path.of("API_COMPATIBILITY_MATRIX.md");
            if (Files.exists(file)) {
                Files.writeString(file, markdown);
            }
        } catch (Exception ignored) {}
    }

    @Test
    void testNewlySupportedApisEndToEnd() throws IOException {
        Path avatarDir = AvatarTemplateManager.scaffold("figura_compat", tempDir, "compat_full", "Compat Full");
        BbModelDefinition model = BbModelParser.parse(avatarDir.resolve("model.bbmodel"), "compat_full");

        AvatarState state = new AvatarState(
            "compat_full",
            "avatar:compat_full",
            avatarDir,
            false,
            Set.of(
                AvatarPermission.WORLD_EDIT,
                AvatarPermission.COMMAND,
                AvatarPermission.HUD_RENDER,
                AvatarPermission.DATA_STORAGE,
                AvatarPermission.NETWORK,
                AvatarPermission.CAMERA
            ),
            Set.of(
                AvatarPermission.WORLD_EDIT,
                AvatarPermission.COMMAND,
                AvatarPermission.HUD_RENDER,
                AvatarPermission.DATA_STORAGE,
                AvatarPermission.NETWORK,
                AvatarPermission.CAMERA
            )
        );

        ClientLuaAvatarRuntime runtime = new ClientLuaAvatarRuntime(state, model, avatarDir.resolve("main.lua"));
        assertTrue(runtime.load());

        // 1. world.setBlock and world.setTime with world_edit permission
        LuaValue setBlockRes = runtime.eval("return world.setBlock(10, 64, 10, 'minecraft:stone')");
        assertTrue(setBlockRes.toboolean());
        LuaValue setTimeRes = runtime.eval("return world.setTime(6000)");
        assertTrue(setTimeRes.toboolean());

        // 2. host:sendChat with command permission
        LuaValue sendChatRes = runtime.eval("return host:sendChat('/say Hello from Avatar')");
        assertTrue(sendChatRes.toboolean());

        // 3. renderer:setPostShader and renderer:getPostShader with hud_render permission
        runtime.eval("renderer:setPostShader('minecraft:shaders/post/creeper.json')");
        assertEquals("minecraft:shaders/post/creeper.json", state.postShader());
        LuaValue getShaderRes = runtime.eval("return renderer:getPostShader()");
        assertEquals("minecraft:shaders/post/creeper.json", getShaderRes.tojstring());
        runtime.eval("renderer:setPostShader(nil)");
        assertNull(state.postShader());

        // 4. figuraMetatables exposed
        LuaValue metaVector3 = runtime.eval("return figuraMetatables.Vector3 ~= nil");
        assertTrue(metaVector3.toboolean());
        LuaValue metaItemStack = runtime.eval("return figuraMetatables.ItemStack ~= nil");
        assertTrue(metaItemStack.toboolean());

        // 5. Sandboxed io.open file read/write with data permission
        runtime.eval("""
            local f = io.open('test_output.txt', 'w')
            f:write('Hello sandboxed IO')
            f:close()
        """);
        Path writtenFile = avatarDir.resolve("test_output.txt");
        assertTrue(Files.exists(writtenFile));
        assertEquals("Hello sandboxed IO", Files.readString(writtenFile));

        LuaValue readContent = runtime.eval("""
            local f = io.open('test_output.txt', 'r')
            local c = f:read('*a')
            f:close()
            return c
        """);
        assertEquals("Hello sandboxed IO", readContent.tojstring());

        // 6. Safe os functions
        LuaValue osTime = runtime.eval("return os.time()");
        assertTrue(osTime.tonumber().todouble() > 0);
        LuaValue osDate = runtime.eval("return os.date('%Y')");
        assertFalse(osDate.tojstring().isBlank());
        LuaValue osClock = runtime.eval("return os.clock() >= 0");
        assertTrue(osClock.toboolean());
        LuaValue osEnv = runtime.eval("return os.getenv('AVATAR_NAME')");
        assertEquals("compat_full", osEnv.tojstring());

        // 7. server_packets.raw with network permission
        LuaValue rawPacketRes = runtime.eval("return server_packets.raw('shyne:channel', 'ping_data')");
        assertTrue(rawPacketRes.toboolean());

        runtime.dispose();

        // 8. Test permission denials without permissions
        AvatarState unprivilegedState = new AvatarState(
            "compat_restricted",
            "avatar:compat_restricted",
            avatarDir,
            false,
            Set.of(),
            Set.of()
        );
        ClientLuaAvatarRuntime unprivilegedRuntime = new ClientLuaAvatarRuntime(unprivilegedState, model, avatarDir.resolve("main.lua"));
        assertTrue(unprivilegedRuntime.load());

        assertThrows(Exception.class, () -> unprivilegedRuntime.eval("world.setBlock(0, 0, 0, 'air')"));
        assertThrows(Exception.class, () -> unprivilegedRuntime.eval("host:sendChat('hello')"));
        assertThrows(Exception.class, () -> unprivilegedRuntime.eval("renderer:setPostShader('blur')"));
        assertThrows(Exception.class, () -> unprivilegedRuntime.eval("io.open('test.txt', 'w')"));

        unprivilegedRuntime.dispose();
    }
}

package seashyne.shynecore.client.avatar.linter;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.compiler.LuaC;
import seashyne.shynecore.client.avatar.AvatarManifest;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.runtime.AvatarScriptErrorParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Static analyzer for avatar Lua scripts. Detects syntax errors, sandboxed/blocked
 * library calls, deprecated/unsupported Figura APIs, and missing permissions in avatar.json.
 */
public final class AvatarScriptLinter {

    private static final Pattern BLOCKED_IO = Pattern.compile("\\b(io\\.[a-zA-Z0-9_]+|\\bio\\b)\\b");
    private static final Pattern BLOCKED_OS = Pattern.compile("\\b(os\\.[a-zA-Z0-9_]+|\\bos\\b)\\b");
    private static final Pattern BLOCKED_PKG = Pattern.compile("\\b(package\\.[a-zA-Z0-9_]+|\\bpackage\\b)\\b");
    private static final Pattern BLOCKED_LOADFILE = Pattern.compile("\\b(dofile|loadfile)\\b");
    private static final Pattern BLOCKED_RAWSET_G = Pattern.compile("rawset\\s*\\(\\s*_G\\b");

    private static final Pattern FIGURA_METATABLES = Pattern.compile("\\bfiguraMetatables\\b");
    private static final Pattern DEPRECATED_CAMERA = Pattern.compile("\\bclient\\.setCameraPos\\b");
    private static final Pattern BLOCKED_SET_BLOCK = Pattern.compile("\\bworld\\.setBlock\\b");
    private static final Pattern BLOCKED_SET_TIME = Pattern.compile("\\bworld\\.setTime\\b");
    private static final Pattern UNSUPPORTED_HOST_CHAT = Pattern.compile("\\bhost:sendChat\\b");
    private static final Pattern UNSUPPORTED_POST_SHADER = Pattern.compile("\\brenderer:setPostShader\\b");

    private static final Pattern PERM_NETWORK = Pattern.compile("\\b(network\\.send|server_packets|\\bnet\\b)");
    private static final Pattern PERM_DATA = Pattern.compile("\\bdata:(set|get|save|load)\\b");
    private static final Pattern PERM_CHAT_NAMEPLATE = Pattern.compile("\\bnameplate\\.CHAT\\b");
    private static final Pattern PERM_LIST_NAMEPLATE = Pattern.compile("\\bnameplate\\.LIST\\b");
    private static final Pattern PERM_CUSTOM_SOUND = Pattern.compile("\\b(sounds\\.playCustomSound|sounds:custom)\\b");
    private static final Pattern PERM_CAMERA = Pattern.compile("\\bcamera\\.(setPos|setRot|setFov)\\b");

    private AvatarScriptLinter() {}

    /**
     * Lints all Lua scripts in the given avatar root directory against its manifest.
     */
    public static List<AvatarLintIssue> lintAvatar(Path rootDir, AvatarManifest manifest) {
        if (rootDir == null || !Files.isDirectory(rootDir)) return List.of();

        List<AvatarLintIssue> issues = new ArrayList<>();
        Set<AvatarPermission> permissions = manifest != null ? manifest.permissions() : Set.of();

        try (Stream<Path> stream = Files.walk(rootDir, 6)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lua"))
                  .sorted()
                  .forEach(luaPath -> {
                      String relPath = rootDir.relativize(luaPath).toString().replace('\\', '/');
                      try {
                          String content = Files.readString(luaPath, StandardCharsets.UTF_8);
                          issues.addAll(lintScript(relPath, content, permissions));
                      } catch (IOException e) {
                          issues.add(new AvatarLintIssue(
                              AvatarLintIssue.Severity.ERROR,
                              relPath,
                              -1,
                              "READ_ERROR",
                              "Could not read Lua script: " + e.getMessage(),
                              "Check file permissions and encoding."
                          ));
                      }
                  });
        } catch (Exception e) {
            issues.add(new AvatarLintIssue(
                AvatarLintIssue.Severity.ERROR,
                "project",
                -1,
                "SCAN_ERROR",
                "Failed to scan scripts directory: " + e.getMessage(),
                null
            ));
        }

        return issues;
    }

    /**
     * Lints a single Lua script content string.
     */
    public static List<AvatarLintIssue> lintScript(String fileName, String content, Set<AvatarPermission> declaredPermissions) {
        List<AvatarLintIssue> issues = new ArrayList<>();
        if (content == null || content.isBlank()) return issues;

        // 1. Syntax check using LuaC compiler
        checkSyntax(fileName, content, issues);

        // 2. Line-by-line static analysis
        String[] lines = content.split("\r?\n", -1);
        boolean inBlockComment = false;

        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("--[[")) {
                inBlockComment = true;
            }
            if (inBlockComment) {
                if (trimmed.contains("]]")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (trimmed.startsWith("--")) {
                continue; // single line comment
            }

            // Strip trailing comments
            int commentIdx = line.indexOf("--");
            String codeOnly = commentIdx >= 0 ? line.substring(0, commentIdx) : line;

            // Check blocked globals
            if (BLOCKED_IO.matcher(codeOnly).find()) {
                issues.add(new AvatarLintIssue(
                    AvatarLintIssue.Severity.ERROR,
                    fileName,
                    lineNumber,
                    "BLOCKED_IO",
                    "Direct filesystem access with 'io' is blocked in the avatar sandbox.",
                    "Use safe 'resources', 'json', or 'data' APIs instead."
                ));
            }
            if (BLOCKED_OS.matcher(codeOnly).find()) {
                issues.add(new AvatarLintIssue(
                    AvatarLintIssue.Severity.ERROR,
                    fileName,
                    lineNumber,
                    "BLOCKED_OS",
                    "Operating system calls via 'os' are blocked for security.",
                    "Remove OS calls; use avatar state or player/world properties."
                ));
            }
            if (BLOCKED_PKG.matcher(codeOnly).find()) {
                issues.add(new AvatarLintIssue(
                    AvatarLintIssue.Severity.ERROR,
                    fileName,
                    lineNumber,
                    "BLOCKED_PACKAGE",
                    "Dynamic package table manipulation is restricted.",
                    "Use standard require(\"module\") with relative avatar paths."
                ));
            }
            if (BLOCKED_LOADFILE.matcher(codeOnly).find()) {
                issues.add(new AvatarLintIssue(
                    AvatarLintIssue.Severity.ERROR,
                    fileName,
                    lineNumber,
                    "BLOCKED_LOADFILE",
                    "dofile/loadfile is blocked. Scripts must be loaded via require().",
                    "Replace dofile/loadfile with require(\"module_name\")."
                ));
            }
            if (BLOCKED_RAWSET_G.matcher(codeOnly).find()) {
                issues.add(new AvatarLintIssue(
                    AvatarLintIssue.Severity.ERROR,
                    fileName,
                    lineNumber,
                    "RAWSET_GLOBAL",
                    "Modifying the global environment table directly (_G) is prohibited.",
                    "Assign to local variables or use module return tables."
                ));
            }

            // Check deprecated / unsupported Figura APIs
            if (FIGURA_METATABLES.matcher(codeOnly).find()) {
                issues.add(new AvatarLintIssue(
                    AvatarLintIssue.Severity.WARNING,
                    fileName,
                    lineNumber,
                    "FIGURA_METATABLES",
                    "figuraMetatables is not supported in Shyne.",
                    "Use standard Lua metatables or Shyne vectors/matrices."
                ));
            }
            if (DEPRECATED_CAMERA.matcher(codeOnly).find()) {
                issues.add(new AvatarLintIssue(
                    AvatarLintIssue.Severity.WARNING,
                    fileName,
                    lineNumber,
                    "DEPRECATED_CAMERA",
                    "client.setCameraPos is deprecated.",
                    "Use camera.setPos(...) instead (requires 'local_camera' permission)."
                ));
            }
            if (BLOCKED_SET_BLOCK.matcher(codeOnly).find()) {
                issues.add(new AvatarLintIssue(
                    AvatarLintIssue.Severity.ERROR,
                    fileName,
                    lineNumber,
                    "BLOCKED_WORLD_MOD",
                    "world.setBlock is blocked. Client avatars cannot modify world blocks.",
                    "Use visual avatar model blocks or particles instead."
                ));
            }
            if (BLOCKED_SET_TIME.matcher(codeOnly).find()) {
                issues.add(new AvatarLintIssue(
                    AvatarLintIssue.Severity.WARNING,
                    fileName,
                    lineNumber,
                    "BLOCKED_WORLD_TIME",
                    "world.setTime is blocked. Time is synchronized by the server.",
                    "Query world.getTime() for read-only time data."
                ));
            }
            if (UNSUPPORTED_HOST_CHAT.matcher(codeOnly).find()) {
                issues.add(new AvatarLintIssue(
                    AvatarLintIssue.Severity.WARNING,
                    fileName,
                    lineNumber,
                    "UNSUPPORTED_HOST_CHAT",
                    "host:sendChat is not supported in Shyne sandbox.",
                    "Use network channels or chat events instead."
                ));
            }
            if (UNSUPPORTED_POST_SHADER.matcher(codeOnly).find()) {
                issues.add(new AvatarLintIssue(
                    AvatarLintIssue.Severity.WARNING,
                    fileName,
                    lineNumber,
                    "UNSUPPORTED_POST_SHADER",
                    "renderer:setPostShader is not supported in the standard pipeline.",
                    "Use custom material dynamic textures or render context callbacks."
                ));
            }

            // Check missing permissions
            if (PERM_NETWORK.matcher(codeOnly).find()) {
                if (declaredPermissions != null && !declaredPermissions.contains(AvatarPermission.NETWORK)) {
                    issues.add(new AvatarLintIssue(
                        AvatarLintIssue.Severity.WARNING,
                        fileName,
                        lineNumber,
                        "MISSING_PERMISSION_NETWORK",
                        "Script uses network communication, but 'network' permission is not declared.",
                        "Add \"network\" to \"permissions\" array in avatar.json."
                    ));
                }
            }
            if (PERM_DATA.matcher(codeOnly).find()) {
                if (declaredPermissions != null && !declaredPermissions.contains(AvatarPermission.DATA_STORAGE)) {
                    issues.add(new AvatarLintIssue(
                        AvatarLintIssue.Severity.WARNING,
                        fileName,
                        lineNumber,
                        "MISSING_PERMISSION_DATA",
                        "Script uses persistent data storage, but 'data' permission is not declared.",
                        "Add \"data\" to \"permissions\" array in avatar.json."
                    ));
                }
            }
            if (PERM_CHAT_NAMEPLATE.matcher(codeOnly).find()) {
                if (declaredPermissions != null && !declaredPermissions.contains(AvatarPermission.CHAT_NAMEPLATE)) {
                    issues.add(new AvatarLintIssue(
                        AvatarLintIssue.Severity.WARNING,
                        fileName,
                        lineNumber,
                        "MISSING_PERMISSION_CHAT_NAMEPLATE",
                        "Script modifies chat nameplate, but 'chat_nameplate' permission is not declared.",
                        "Add \"chat_nameplate\" to \"permissions\" array in avatar.json."
                    ));
                }
            }
            if (PERM_LIST_NAMEPLATE.matcher(codeOnly).find()) {
                if (declaredPermissions != null && !declaredPermissions.contains(AvatarPermission.TAB_LIST_NAMEPLATE)) {
                    issues.add(new AvatarLintIssue(
                        AvatarLintIssue.Severity.WARNING,
                        fileName,
                        lineNumber,
                        "MISSING_PERMISSION_LIST_NAMEPLATE",
                        "Script modifies tab list nameplate, but 'tab_list_nameplate' permission is not declared.",
                        "Add \"tab_list_nameplate\" to \"permissions\" array in avatar.json."
                    ));
                }
            }
            if (PERM_CUSTOM_SOUND.matcher(codeOnly).find()) {
                if (declaredPermissions != null && !declaredPermissions.contains(AvatarPermission.SOUND)) {
                    issues.add(new AvatarLintIssue(
                        AvatarLintIssue.Severity.WARNING,
                        fileName,
                        lineNumber,
                        "MISSING_PERMISSION_CUSTOM_SOUNDS",
                        "Script plays custom sounds, but 'sound' permission is not declared.",
                        "Add \"sound\" to \"permissions\" array in avatar.json."
                    ));
                }
            }
            if (PERM_CAMERA.matcher(codeOnly).find()) {
                if (declaredPermissions != null && !declaredPermissions.contains(AvatarPermission.CAMERA)) {
                    issues.add(new AvatarLintIssue(
                        AvatarLintIssue.Severity.WARNING,
                        fileName,
                        lineNumber,
                        "MISSING_PERMISSION_CAMERA",
                        "Script alters camera transform, but 'camera' permission is not declared.",
                        "Add \"camera\" to \"permissions\" array in avatar.json."
                    ));
                }
            }
        }

        return issues;
    }

    private static void checkSyntax(String fileName, String content, List<AvatarLintIssue> issues) {
        try {
            LuaC.instance.compile(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), fileName);
        } catch (LuaError error) {
            var parsed = AvatarScriptErrorParser.parse(error, fileName);
            issues.add(new AvatarLintIssue(
                AvatarLintIssue.Severity.ERROR,
                parsed.file(),
                parsed.line(),
                "SYNTAX_ERROR",
                parsed.cleanMessage(),
                "Fix syntax error at line " + parsed.line()
            ));
        } catch (Exception e) {
            issues.add(new AvatarLintIssue(
                AvatarLintIssue.Severity.ERROR,
                fileName,
                -1,
                "COMPILE_ERROR",
                "Compilation error: " + e.getMessage(),
                null
            ));
        }
    }
}

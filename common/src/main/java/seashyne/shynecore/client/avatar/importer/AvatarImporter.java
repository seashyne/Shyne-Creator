package seashyne.shynecore.client.avatar.importer;

import com.google.gson.*;
import seashyne.shynecore.client.avatar.AvatarManifest;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.linter.AvatarLintIssue;
import seashyne.shynecore.client.avatar.linter.AvatarScriptLinter;
import seashyne.shynecore.model.BbAnimationDefinition;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbModelParser;
import seashyne.shynecore.model.BbTextureDefinition;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Inspects, diagnoses, and converts Figura and Blockbench projects into Shyne Avatar Standard 2.0 packages.
 */
public final class AvatarImporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Pattern API_EVENTS = Pattern.compile("\\bevents\\.");
    private static final Pattern API_MODELS = Pattern.compile("\\bmodels\\.");
    private static final Pattern API_ANIMATIONS = Pattern.compile("\\banimations\\.");
    private static final Pattern API_NAMEPLATE = Pattern.compile("\\bnameplate\\.");
    private static final Pattern API_PINGS = Pattern.compile("\\bpings");
    private static final Pattern API_NETWORK = Pattern.compile("\\b(network\\.|server_packets|\\bnet\\b)");
    private static final Pattern API_DATA = Pattern.compile("\\bdata:(set|get|save|load)\\b");
    private static final Pattern API_JSON = Pattern.compile("\\bjson\\.(encode|decode)\\b");
    private static final Pattern API_RESOURCES = Pattern.compile("\\bresources:(has|read|texture)\\b");
    private static final Pattern API_ACTION_WHEEL = Pattern.compile("\\baction_wheel\\b");
    private static final Pattern API_KEYBIND = Pattern.compile("\\bkeybind\\b");
    private static final Pattern API_SOUNDS = Pattern.compile("\\bsounds\\b");
    private static final Pattern API_PARTICLES = Pattern.compile("\\bparticles\\b");

    private AvatarImporter() {}

    /**
     * Inspects an avatar directory and generates a comprehensive diagnostic report.
     */
    public static AvatarImportReport analyze(Path rootDir) {
        if (rootDir == null || !Files.isDirectory(rootDir)) {
            return emptyReport(rootDir != null ? rootDir.getFileName().toString() : "unknown", "Directory does not exist");
        }

        JsonObject manifestJson = null;
        Path manifestPath = rootDir.resolve("avatar.json");
        if (Files.isRegularFile(manifestPath)) {
            try (Reader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
                manifestJson = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception ignored) {}
        }

        JsonObject metadataJson = null;
        Path metadataPath = rootDir.resolve("metadata.json");
        if (Files.isRegularFile(metadataPath)) {
            try (Reader reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
                metadataJson = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception ignored) {}
        }
        boolean hasMetadata = metadataJson != null || Files.isRegularFile(metadataPath);

        // Find primary model
        Path modelPath = findPrimaryModel(rootDir, manifestJson);

        // Find primary script
        Path scriptPath = findPrimaryScript(rootDir, manifestJson);

        // Determine Source Type
        AvatarImportReport.SourceType sourceType = determineSourceType(manifestJson, hasMetadata, scriptPath, modelPath);

        String avatarId = extractAvatarId(rootDir, manifestJson, metadataJson);
        String avatarName = extractAvatarName(rootDir, manifestJson, metadataJson);
        String author = extractAuthor(manifestJson, metadataJson);

        // Parse Model Stats
        AvatarImportReport.ModelStats modelStats = analyzeModel(modelPath, avatarId);
        String modelFormat = extractModelFormat(modelPath);

        // Parse Script Stats
        AvatarImportReport.ScriptStats scriptStats = analyzeScripts(rootDir);

        // Build mock/partial manifest for permission linting
        Set<AvatarPermission> declaredPermissions = extractPermissions(manifestJson);
        AvatarManifest mockManifest = new AvatarManifest(
            "2.0", avatarId, avatarName, "1.0.0",
            scriptPath != null ? rootDir.relativize(scriptPath).toString() : null,
            modelPath != null ? rootDir.relativize(modelPath).toString() : "model.bbmodel",
            false, false, "", false, false, "direct", null, List.of(),
            declaredPermissions, "2.0", true, Map.of(), "custom", null
        );

        // Lint scripts
        List<AvatarLintIssue> issues = new ArrayList<>(AvatarScriptLinter.lintAvatar(rootDir, mockManifest));

        // Additional manifest/model validations
        validateManifestAndModel(manifestJson, modelPath, modelStats, issues);

        // Grade calculation & recommendations
        AvatarImportReport.CompatibilityGrade grade = calculateGrade(issues, modelStats);
        List<String> recommendations = generateRecommendations(sourceType, grade, issues, modelStats, scriptStats, declaredPermissions);

        return new AvatarImportReport(
            sourceType, avatarId, avatarName, author, modelFormat,
            modelStats, scriptStats, issues, grade, recommendations
        );
    }

    private static Path findPrimaryModel(Path rootDir, JsonObject manifestJson) {
        if (manifestJson != null && manifestJson.has("model") && manifestJson.get("model").isJsonPrimitive()) {
            Path explicit = rootDir.resolve(manifestJson.get("model").getAsString());
            if (Files.isRegularFile(explicit)) return explicit;
        }
        try (Stream<Path> stream = Files.list(rootDir)) {
            return stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
                         .findFirst()
                         .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Path findPrimaryScript(Path rootDir, JsonObject manifestJson) {
        if (manifestJson != null && manifestJson.has("main") && manifestJson.get("main").isJsonPrimitive()) {
            Path explicit = rootDir.resolve(manifestJson.get("main").getAsString());
            if (Files.isRegularFile(explicit)) return explicit;
        }
        Path standardMain = rootDir.resolve("main.lua");
        if (Files.isRegularFile(standardMain)) return standardMain;
        Path avatarLua = rootDir.resolve("avatar.lua");
        if (Files.isRegularFile(avatarLua)) return avatarLua;
        Path scriptLua = rootDir.resolve("script.lua");
        if (Files.isRegularFile(scriptLua)) return scriptLua;

        try (Stream<Path> stream = Files.list(rootDir)) {
            return stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lua"))
                         .findFirst()
                         .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static AvatarImportReport.SourceType determineSourceType(JsonObject manifest, boolean hasMetadata, Path script, Path model) {
        if (manifest != null && manifest.has("standard")) {
            String std = manifest.get("standard").getAsString();
            if ("standard".equalsIgnoreCase(std) || "2.0".equals(std) || "shyne".equalsIgnoreCase(std)) {
                return AvatarImportReport.SourceType.SHYNE_AVATAR;
            }
        }
        if (manifest != null && (manifest.has("profile") || manifest.has("behavior") || manifest.has("api"))) {
            return AvatarImportReport.SourceType.SHYNE_AVATAR;
        }
        if (hasMetadata || (manifest != null && (manifest.has("authors") || manifest.has("ignoredFiles") || manifest.has("autoAnimate")))) {
            return AvatarImportReport.SourceType.FIGURA_AVATAR;
        }
        if (script != null && script.getFileName().toString().equalsIgnoreCase("avatar.lua")) {
            return AvatarImportReport.SourceType.FIGURA_AVATAR;
        }
        if (manifest != null && manifest.has("name") && !manifest.has("standard")) {
            return AvatarImportReport.SourceType.FIGURA_AVATAR;
        }
        if (model != null) {
            return AvatarImportReport.SourceType.BLOCKBENCH_MODEL;
        }
        return AvatarImportReport.SourceType.UNKNOWN;
    }

    private static String extractAvatarId(Path rootDir, JsonObject manifest, JsonObject metadata) {
        if (manifest != null && manifest.has("id") && manifest.get("id").isJsonPrimitive()) {
            return manifest.get("id").getAsString();
        }
        if (metadata != null && metadata.has("id") && metadata.get("id").isJsonPrimitive()) {
            return metadata.get("id").getAsString();
        }
        String folderName = rootDir.getFileName().toString();
        return folderName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private static String extractAvatarName(Path rootDir, JsonObject manifest, JsonObject metadata) {
        if (manifest != null && manifest.has("name") && manifest.get("name").isJsonPrimitive()) {
            return manifest.get("name").getAsString();
        }
        if (metadata != null && metadata.has("name") && metadata.get("name").isJsonPrimitive()) {
            return metadata.get("name").getAsString();
        }
        return rootDir.getFileName().toString();
    }

    private static String extractAuthor(JsonObject manifest, JsonObject metadata) {
        String fromManifest = extractAuthorSingle(manifest);
        if (!fromManifest.isBlank()) return fromManifest;
        return extractAuthorSingle(metadata);
    }

    private static String extractAuthorSingle(JsonObject json) {
        if (json == null) return "";
        if (json.has("author") && json.get("author").isJsonPrimitive()) {
            return json.get("author").getAsString();
        }
        if (json.has("authors")) {
            JsonElement authors = json.get("authors");
            if (authors.isJsonArray()) {
                List<String> list = new ArrayList<>();
                for (JsonElement e : authors.getAsJsonArray()) {
                    if (e.isJsonPrimitive()) list.add(e.getAsString());
                }
                return String.join(", ", list);
            } else if (authors.isJsonPrimitive()) {
                return authors.getAsString();
            }
        }
        return "";
    }

    private static Set<AvatarPermission> extractPermissions(JsonObject manifest) {
        if (manifest == null || !manifest.has("permissions") || !manifest.get("permissions").isJsonArray()) {
            return Set.of();
        }
        Set<AvatarPermission> set = new HashSet<>();
        for (JsonElement el : manifest.getAsJsonArray("permissions")) {
            if (el.isJsonPrimitive()) {
                AvatarPermission.fromId(el.getAsString()).ifPresent(set::add);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    private static AvatarImportReport.ModelStats analyzeModel(Path modelPath, String avatarId) {
        if (modelPath == null || !Files.isRegularFile(modelPath)) {
            return new AvatarImportReport.ModelStats(0, 0, 0, 0, 0, 0, 0, 0, 0L, 0, 0, List.of(), Set.of());
        }

        try {
            BbModelDefinition def = BbModelParser.parse(modelPath, avatarId);
            int cubes = def.cubes().size();
            int meshes = def.meshes().size();
            int bones = def.bones().size();
            int roots = (int) def.bones().stream().filter(b -> b.parentUuid() == null).count();
            int maxDepth = computeMaxBoneDepth(def);

            int textures = def.textures().size();
            int maxWidth = def.textures().stream().mapToInt(BbTextureDefinition::width).max().orElse(0);
            int maxHeight = def.textures().stream().mapToInt(BbTextureDefinition::height).max().orElse(0);
            long totalBytes = Files.size(modelPath);

            int animCount = def.animations().size();
            List<String> animNames = def.animations().stream().map(BbAnimationDefinition::name).toList();
            int totalKeyframes = 0;
            Set<String> easingTypes = new HashSet<>();

            // Inspect raw bbmodel JSON for keyframes and easing
            try (Reader r = Files.newBufferedReader(modelPath, StandardCharsets.UTF_8)) {
                JsonObject raw = JsonParser.parseReader(r).getAsJsonObject();
                if (raw.has("animations") && raw.get("animations").isJsonArray()) {
                    for (JsonElement animEl : raw.getAsJsonArray("animations")) {
                        if (animEl.isJsonObject() && animEl.getAsJsonObject().has("animators")) {
                            JsonObject animators = animEl.getAsJsonObject().getAsJsonObject("animators");
                            for (var entry : animators.entrySet()) {
                                if (entry.getValue().isJsonObject()) {
                                    JsonObject animator = entry.getValue().getAsJsonObject();
                                    if (animator.has("keyframes") && animator.get("keyframes").isJsonArray()) {
                                        JsonArray kfs = animator.getAsJsonArray("keyframes");
                                        totalKeyframes += kfs.size();
                                        for (JsonElement kf : kfs) {
                                            if (kf.isJsonObject() && kf.getAsJsonObject().has("interpolation")) {
                                                easingTypes.add(kf.getAsJsonObject().get("interpolation").getAsString());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            return new AvatarImportReport.ModelStats(
                cubes, meshes, bones, roots, maxDepth,
                textures, maxWidth, maxHeight, totalBytes,
                animCount, totalKeyframes, animNames, easingTypes
            );
        } catch (Exception e) {
            return new AvatarImportReport.ModelStats(0, 0, 0, 0, 0, 0, 0, 0, 0L, 0, 0, List.of(), Set.of());
        }
    }

    private static int computeMaxBoneDepth(BbModelDefinition def) {
        if (def == null || def.bones().isEmpty()) return 0;
        Map<String, String> parentMap = new HashMap<>();
        for (BbBoneDefinition b : def.bones()) {
            if (b.parentUuid() != null) {
                parentMap.put(b.uuid(), b.parentUuid());
            }
        }
        int max = 0;
        for (BbBoneDefinition b : def.bones()) {
            int depth = 1;
            String cur = b.uuid();
            while (parentMap.containsKey(cur) && depth < 32) {
                cur = parentMap.get(cur);
                depth++;
            }
            if (depth > max) max = depth;
        }
        return max;
    }

    private static String extractModelFormat(Path modelPath) {
        if (modelPath == null || !Files.isRegularFile(modelPath)) return "None";
        try (Reader r = Files.newBufferedReader(modelPath, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            if (obj.has("meta") && obj.get("meta").isJsonObject()) {
                JsonObject meta = obj.getAsJsonObject("meta");
                if (meta.has("model_format")) {
                    return meta.get("model_format").getAsString();
                }
            }
        } catch (Exception ignored) {}
        return "Blockbench (Generic)";
    }

    private static AvatarImportReport.ScriptStats analyzeScripts(Path rootDir) {
        List<String> files = new ArrayList<>();
        int lines = 0;
        Set<String> apis = new TreeSet<>();

        try (Stream<Path> stream = Files.walk(rootDir, 6)) {
            List<Path> luaPaths = stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lua"))
                .sorted()
                .toList();

            for (Path lp : luaPaths) {
                String rel = rootDir.relativize(lp).toString().replace('\\', '/');
                files.add(rel);
                try {
                    List<String> scriptLines = Files.readAllLines(lp, StandardCharsets.UTF_8);
                    lines += scriptLines.size();
                    for (String line : scriptLines) {
                        if (API_EVENTS.matcher(line).find()) apis.add("events");
                        if (API_MODELS.matcher(line).find()) apis.add("models");
                        if (API_ANIMATIONS.matcher(line).find()) apis.add("animations");
                        if (API_NAMEPLATE.matcher(line).find()) apis.add("nameplate");
                        if (API_PINGS.matcher(line).find()) apis.add("pings");
                        if (API_NETWORK.matcher(line).find()) apis.add("network");
                        if (API_DATA.matcher(line).find()) apis.add("data");
                        if (API_JSON.matcher(line).find()) apis.add("json");
                        if (API_RESOURCES.matcher(line).find()) apis.add("resources");
                        if (API_ACTION_WHEEL.matcher(line).find()) apis.add("action_wheel");
                        if (API_KEYBIND.matcher(line).find()) apis.add("keybind");
                        if (API_SOUNDS.matcher(line).find()) apis.add("sounds");
                        if (API_PARTICLES.matcher(line).find()) apis.add("particles");
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return new AvatarImportReport.ScriptStats(files.size(), lines, files, apis);
    }

    private static void validateManifestAndModel(JsonObject manifest, Path modelPath, AvatarImportReport.ModelStats modelStats, List<AvatarLintIssue> issues) {
        if (manifest == null) {
            issues.add(new AvatarLintIssue(
                AvatarLintIssue.Severity.WARNING,
                "avatar.json",
                -1,
                "MISSING_MANIFEST",
                "No avatar.json manifest found. Avatar requires standard manifest.",
                "Generate avatar.json with 'standard': '2.0'."
            ));
        }
        if (modelPath == null) {
            issues.add(new AvatarLintIssue(
                AvatarLintIssue.Severity.ERROR,
                "project",
                -1,
                "MISSING_MODEL",
                "No .bbmodel 3D model found in avatar root directory.",
                "Export your avatar as a Blockbench .bbmodel file."
            ));
        }
        if (modelStats.maxTextureWidth() > 512 || modelStats.maxTextureHeight() > 512) {
            issues.add(new AvatarLintIssue(
                AvatarLintIssue.Severity.WARNING,
                "textures",
                -1,
                "TEXTURE_BUDGET_EXCEEDED",
                String.format("Texture resolution %dx%d exceeds recommended 512x512 budget.", modelStats.maxTextureWidth(), modelStats.maxTextureHeight()),
                "Downscale texture to 512x512 or lower to prevent GPU memory warnings."
            ));
        }
        if (modelStats.cubeCount() > 512) {
            issues.add(new AvatarLintIssue(
                AvatarLintIssue.Severity.WARNING,
                "model",
                -1,
                "HIGH_CUBE_COUNT",
                String.format("Cube count (%d) exceeds multiplayer recommended budget (256).", modelStats.cubeCount()),
                "Optimize geometry or combine hidden faces in Blockbench."
            ));
        }
    }

    private static AvatarImportReport.CompatibilityGrade calculateGrade(List<AvatarLintIssue> issues, AvatarImportReport.ModelStats modelStats) {
        long errors = issues.stream().filter(i -> i.severity() == AvatarLintIssue.Severity.ERROR).count();
        if (errors > 0) {
            return AvatarImportReport.CompatibilityGrade.D_INCOMPATIBLE;
        }

        long warnings = issues.stream().filter(i -> i.severity() == AvatarLintIssue.Severity.WARNING).count();
        boolean permissionIssues = issues.stream().anyMatch(i -> i.code().startsWith("MISSING_PERMISSION"));

        if (permissionIssues) {
            return AvatarImportReport.CompatibilityGrade.C_NEEDS_MODIFICATIONS;
        }
        if (warnings > 0 || modelStats.maxTextureWidth() > 512) {
            return AvatarImportReport.CompatibilityGrade.B_COMPATIBLE_WITH_WARNINGS;
        }
        return AvatarImportReport.CompatibilityGrade.A_PERFECT;
    }

    private static List<String> generateRecommendations(
        AvatarImportReport.SourceType sourceType,
        AvatarImportReport.CompatibilityGrade grade,
        List<AvatarLintIssue> issues,
        AvatarImportReport.ModelStats modelStats,
        AvatarImportReport.ScriptStats scriptStats,
        Set<AvatarPermission> declaredPermissions
    ) {
        List<String> recs = new ArrayList<>();

        if (sourceType == AvatarImportReport.SourceType.FIGURA_AVATAR) {
            recs.add("This avatar was detected as a Figura package. Shyne supports Figura Lua APIs out of the box with zero code changes in standard mode.");
        }

        for (AvatarLintIssue issue : issues) {
            if (issue.severity() == AvatarLintIssue.Severity.ERROR) {
                recs.add("Fix error in " + issue.file() + ": " + issue.message() + " (" + issue.recommendation() + ")");
            }
        }

        // Collect suggested permissions
        Set<String> missingPerms = new LinkedHashSet<>();
        for (AvatarLintIssue issue : issues) {
            if (issue.code().startsWith("MISSING_PERMISSION_")) {
                String perm = issue.code().substring("MISSING_PERMISSION_".length()).toLowerCase(Locale.ROOT);
                missingPerms.add(perm);
            }
        }
        if (!missingPerms.isEmpty()) {
            recs.add("Declare required permissions in avatar.json: [\"" + String.join("\", \"", missingPerms) + "\"]");
        }

        if (modelStats.maxTextureWidth() > 512 || modelStats.maxTextureHeight() > 512) {
            recs.add("Downscale textures to 512x512 to comply with Shyne quota limits without warnings.");
        }

        if (recs.isEmpty()) {
            recs.add("All model and script checks passed. Ready to play!");
        }

        return recs;
    }

    public record ConversionOptions(
        boolean generateCredits,
        boolean declareAutoPermissions,
        boolean overwriteExistingManifest,
        String customAuthor,
        String customLicense
    ) {
        public static final ConversionOptions DEFAULT = new ConversionOptions(true, true, false, null, null);
    }

    public record ConversionResult(
        boolean success,
        Path targetDir,
        AvatarImportReport report,
        String message
    ) {
        public static ConversionResult success(Path targetDir, AvatarImportReport report, String message) {
            return new ConversionResult(true, targetDir, report, message);
        }

        public static ConversionResult failure(Path targetDir, AvatarImportReport report, String message) {
            return new ConversionResult(false, targetDir, report, message);
        }
    }

    public static ConversionResult convert(Path sourceDir) {
        return convert(sourceDir, sourceDir, ConversionOptions.DEFAULT);
    }

    public static ConversionResult convert(Path sourceDir, Path targetDir, ConversionOptions options) {
        if (sourceDir == null || !Files.isDirectory(sourceDir)) {
            return ConversionResult.failure(sourceDir, emptyReport("unknown", "Source directory does not exist"), "Source directory does not exist");
        }
        if (options == null) {
            options = ConversionOptions.DEFAULT;
        }

        Path effectiveDir = targetDir != null ? targetDir : sourceDir;
        try {
            if (!effectiveDir.equals(sourceDir)) {
                Files.createDirectories(effectiveDir);
                copyDirectoryRecursively(sourceDir, effectiveDir);
            }

            AvatarImportReport report = analyze(effectiveDir);
            if (report.errorCount() > 0 && report.modelStats().cubeCount() == 0 && report.modelStats().meshCount() == 0) {
                if (report.issues().stream().anyMatch(i -> "MISSING_MODEL".equals(i.code()))) {
                    return ConversionResult.failure(effectiveDir, report, "Cannot import avatar: No 3D model (.bbmodel) found in project.");
                }
            }

            Path manifestPath = effectiveDir.resolve("avatar.json");
            JsonObject manifestJson = null;
            if (Files.isRegularFile(manifestPath) && !options.overwriteExistingManifest()) {
                try (Reader r = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
                    manifestJson = JsonParser.parseReader(r).getAsJsonObject();
                } catch (Exception ignored) {}
            }
            if (manifestJson == null) {
                manifestJson = new JsonObject();
            }

            String avatarId = report.avatarId();
            if (avatarId == null || avatarId.isBlank() || "unknown".equalsIgnoreCase(avatarId)) {
                avatarId = effectiveDir.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            }

            String avatarName = report.avatarName();
            if (avatarName == null || avatarName.isBlank() || "unknown".equalsIgnoreCase(avatarName)) {
                avatarName = effectiveDir.getFileName().toString();
            }

            String author = options.customAuthor();
            if (author == null || author.isBlank()) {
                author = report.author();
            }
            if (author == null || author.isBlank()) {
                author = "Unknown Creator";
            }

            String license = options.customLicense();
            if (license == null || license.isBlank()) {
                license = "Custom / All Rights Reserved to Original Creator";
            }

            Path modelPath = findPrimaryModel(effectiveDir, manifestJson);
            String modelRel = modelPath != null ? effectiveDir.relativize(modelPath).toString().replace('\\', '/') : "model.bbmodel";

            Path scriptPath = findPrimaryScript(effectiveDir, manifestJson);
            String scriptRel = scriptPath != null ? effectiveDir.relativize(scriptPath).toString().replace('\\', '/') : null;

            manifestJson.addProperty("standard", "standard");
            manifestJson.addProperty("id", avatarId);
            manifestJson.addProperty("name", avatarName);
            if (!manifestJson.has("version")) manifestJson.addProperty("version", "1.0.0");
            manifestJson.addProperty("author", author);

            if (!manifestJson.has("description") || manifestJson.get("description").getAsString().isBlank()) {
                manifestJson.addProperty("description", "Converted from " + report.sourceType() + " with Shyne Creator Importer.");
            }

            manifestJson.addProperty("model", modelRel);
            if (scriptRel != null) {
                manifestJson.addProperty("main", scriptRel);
            }
            if (!manifestJson.has("replaceVanilla")) manifestJson.addProperty("replaceVanilla", false);
            if (!manifestJson.has("onlineSync")) manifestJson.addProperty("onlineSync", true);

            String importSourceStr = report.sourceType() == AvatarImportReport.SourceType.FIGURA_AVATAR ? "figura"
                : (report.sourceType() == AvatarImportReport.SourceType.BLOCKBENCH_MODEL ? "blockbench" : "custom");
            manifestJson.addProperty("importSource", importSourceStr);

            // Auto-permissions declaration
            Set<String> perms = new LinkedHashSet<>();
            if (manifestJson.has("permissions") && manifestJson.get("permissions").isJsonArray()) {
                for (JsonElement el : manifestJson.getAsJsonArray("permissions")) {
                    if (el.isJsonPrimitive()) perms.add(el.getAsString().toLowerCase(Locale.ROOT));
                }
            }

            if (options.declareAutoPermissions()) {
                Set<String> apis = report.scriptStats().detectedApis();
                if (apis.contains("particles")) perms.add(AvatarPermission.PARTICLE.id());
                if (apis.contains("sounds")) perms.add(AvatarPermission.SOUND.id());
                if (apis.contains("nameplate")) perms.add(AvatarPermission.NAMEPLATE.id());
                if (apis.contains("data")) perms.add(AvatarPermission.DATA_STORAGE.id());
                if (apis.contains("network") || apis.contains("pings")) perms.add(AvatarPermission.NETWORK.id());
                if (apis.contains("camera")) perms.add(AvatarPermission.CAMERA.id());

                for (AvatarLintIssue issue : report.issues()) {
                    if (issue.code().startsWith("MISSING_PERMISSION_")) {
                        String perm = issue.code().substring("MISSING_PERMISSION_".length()).toLowerCase(Locale.ROOT);
                        perms.add(perm);
                    }
                }
            }

            JsonArray permissionsArray = new JsonArray();
            for (String p : perms) {
                permissionsArray.add(p);
            }
            manifestJson.add("permissions", permissionsArray);

            // Creator Attribution Block
            JsonObject attribution = new JsonObject();
            attribution.addProperty("originalAuthor", author);
            attribution.addProperty("sourcePlatform", report.sourceType() == AvatarImportReport.SourceType.FIGURA_AVATAR ? "Figura" : (report.sourceType() == AvatarImportReport.SourceType.BLOCKBENCH_MODEL ? "Blockbench" : "Custom"));
            attribution.addProperty("sourceType", report.sourceType().name());
            attribution.addProperty("convertedBy", "Shyne Creator Importer 2.13.0");
            attribution.addProperty("convertedAt", java.time.Instant.now().toString());
            attribution.addProperty("license", license);
            attribution.addProperty("notice", "All intellectual property, 3D geometry, textures, animations, and scripts belong to the original creator. Converted for compatibility with Shyne Creator MultiLoader runtime.");
            manifestJson.add("attribution", attribution);

            // Save avatar.json
            Files.writeString(manifestPath, GSON.toJson(manifestJson), StandardCharsets.UTF_8);

            // Write ATTRIBUTION.md if requested
            if (options.generateCredits()) {
                StringBuilder cred = new StringBuilder();
                cred.append("# Avatar Creator Attribution & License Notice\n\n");
                cred.append("- **Avatar Name:** ").append(avatarName).append(" (`").append(avatarId).append("`)\n");
                cred.append("- **Original Creator / Author:** ").append(author).append("\n");
                cred.append("- **Source Platform:** ").append(attribution.get("sourcePlatform").getAsString()).append("\n");
                cred.append("- **Import Tool:** Shyne Creator Importer 2.13.0 (Avatar Standard 2.0)\n");
                cred.append("- **License:** ").append(license).append("\n\n");
                cred.append("## Copyright & Attribution Policy\n\n");
                cred.append("This avatar was converted for compatibility with the Shyne Creator MultiLoader runtime.\n");
                cred.append("All copyrights, intellectual property, 3D geometry, textures, animations, and scripts\n");
                cred.append("remain the property of the original creator.\n\n");
                cred.append("The Shyne runtime operates as a clean-room compatibility layer providing multi-loader\n");
                cred.append("support across Fabric and NeoForge without copying or distributing third-party proprietary binaries.\n");
                Files.writeString(effectiveDir.resolve("ATTRIBUTION.md"), cred.toString(), StandardCharsets.UTF_8);
            }

            // Export markdown report
            Files.writeString(effectiveDir.resolve("avatar_import_report.md"), report.toMarkdown(), StandardCharsets.UTF_8);

            // Re-run analysis on the freshly converted package
            AvatarImportReport updatedReport = analyze(effectiveDir);
            return ConversionResult.success(effectiveDir, updatedReport, "Successfully imported '" + avatarName + "' into Shyne Avatar Standard 2.0!");
        } catch (Exception e) {
            return ConversionResult.failure(effectiveDir, emptyReport(effectiveDir.getFileName().toString(), e.getMessage()), "Conversion failed: " + e.getMessage());
        }
    }

    private static void copyDirectoryRecursively(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path src : stream.toList()) {
                if (src.getFileName().toString().startsWith(".") || src.getFileName().toString().equalsIgnoreCase(".extracted")) {
                    continue;
                }
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else if (Files.isRegularFile(src)) {
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static List<Path> scanUnimported(Path avatarsDir) {
        if (avatarsDir == null || !Files.isDirectory(avatarsDir)) {
            return List.of();
        }
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> stream = Files.list(avatarsDir)) {
            for (Path sub : stream.filter(Files::isDirectory).toList()) {
                String name = sub.getFileName().toString();
                if (name.startsWith(".") || name.equalsIgnoreCase(".extracted")) continue;
                Path manifest = sub.resolve("avatar.json");
                if (!Files.isRegularFile(manifest)) {
                    boolean hasContent = false;
                    try (Stream<Path> contentStream = Files.walk(sub, 2)) {
                        hasContent = contentStream.anyMatch(p -> {
                            String fn = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return fn.endsWith(".bbmodel") || fn.endsWith(".lua");
                        });
                    } catch (Exception ignored) {}
                    if (hasContent) {
                        candidates.add(sub);
                    }
                }
            }
        } catch (Exception ignored) {}
        candidates.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(candidates);
    }

    public static Optional<Path> findAvatarDirectory(Path avatarsDir, String query) {
        if (avatarsDir == null || query == null || query.isBlank() || !Files.isDirectory(avatarsDir)) {
            return Optional.empty();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        Path direct = avatarsDir.resolve(query.trim());
        if (Files.isDirectory(direct)) return Optional.of(direct);

        try (Stream<Path> stream = Files.list(avatarsDir)) {
            for (Path sub : stream.filter(Files::isDirectory).toList()) {
                String folderName = sub.getFileName().toString().toLowerCase(Locale.ROOT);
                if (folderName.equals(q)) return Optional.of(sub);

                Path manifestPath = sub.resolve("avatar.json");
                if (Files.isRegularFile(manifestPath)) {
                    try (Reader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("id") && json.get("id").getAsString().equalsIgnoreCase(q)) {
                            return Optional.of(sub);
                        }
                        if (json.has("name") && json.get("name").getAsString().equalsIgnoreCase(q)) {
                            return Optional.of(sub);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    public static Path exportReport(Path avatarDir) throws IOException {
        if (avatarDir == null || !Files.isDirectory(avatarDir)) {
            throw new IOException("Avatar directory does not exist");
        }
        AvatarImportReport report = analyze(avatarDir);
        Path reportPath = avatarDir.resolve("avatar_import_report.md");
        Files.writeString(reportPath, report.toMarkdown(), StandardCharsets.UTF_8);
        return reportPath;
    }

    private static AvatarImportReport emptyReport(String id, String problem) {
        return new AvatarImportReport(
            AvatarImportReport.SourceType.UNKNOWN,
            id, id, "Unknown", "Unknown",
            new AvatarImportReport.ModelStats(0, 0, 0, 0, 0, 0, 0, 0, 0L, 0, 0, List.of(), Set.of()),
            new AvatarImportReport.ScriptStats(0, 0, List.of(), Set.of()),
            List.of(new AvatarLintIssue(AvatarLintIssue.Severity.ERROR, "project", -1, "NOT_FOUND", problem, null)),
            AvatarImportReport.CompatibilityGrade.D_INCOMPATIBLE,
            List.of(problem)
        );
    }
}

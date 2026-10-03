package seashyne.shynecore.client.avatar.importer;

import seashyne.shynecore.client.avatar.linter.AvatarLintIssue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Structured diagnostic report generated when importing or inspecting an avatar
 * from Figura or Blockbench into Shyne Creator.
 */
public record AvatarImportReport(
    SourceType sourceType,
    String avatarId,
    String avatarName,
    String author,
    String modelFormat,
    ModelStats modelStats,
    ScriptStats scriptStats,
    List<AvatarLintIssue> issues,
    CompatibilityGrade grade,
    List<String> recommendations
) {
    public enum SourceType {
        SHYNE_AVATAR,
        FIGURA_AVATAR,
        BLOCKBENCH_MODEL,
        UNKNOWN
    }

    public enum CompatibilityGrade {
        A_PERFECT("A (Ready to Play)", "Fully compatible with Shyne multi-loader runtime."),
        B_COMPATIBLE_WITH_WARNINGS("B (Compatible with Minor Warnings)", "Works well, but check warnings for optimal performance."),
        C_NEEDS_MODIFICATIONS("C (Needs Modifications)", "Requires permission declaration or script adjustments."),
        D_INCOMPATIBLE("D (Critical Errors)", "Contains syntax errors or prohibited operations.");

        private final String label;
        private final String description;

        CompatibilityGrade(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() { return label; }
        public String description() { return description; }
    }

    public record ModelStats(
        int cubeCount,
        int meshCount,
        int boneCount,
        int rootBoneCount,
        int maxDepth,
        int textureCount,
        int maxTextureWidth,
        int maxTextureHeight,
        long totalTextureBytes,
        int animationCount,
        int totalKeyframes,
        List<String> animationNames,
        Set<String> easingTypes
    ) {}

    public record ScriptStats(
        int scriptCount,
        int totalLines,
        List<String> scriptFiles,
        Set<String> detectedApis
    ) {}

    public boolean isReady() {
        return grade == CompatibilityGrade.A_PERFECT || grade == CompatibilityGrade.B_COMPATIBLE_WITH_WARNINGS;
    }

    public long errorCount() {
        return issues.stream().filter(i -> i.severity() == AvatarLintIssue.Severity.ERROR).count();
    }

    public long warningCount() {
        return issues.stream().filter(i -> i.severity() == AvatarLintIssue.Severity.WARNING).count();
    }

    /**
     * Formats a concise summary string for logging or quick inspection.
     */
    public String toSummaryText() {
        return String.format(
            "Avatar Import Report [%s]: %s (%s) | Grade: %s | Cubes: %d | Bones: %d | Anims: %d | Textures: %d | Scripts: %d (%d lines) | Errors: %d | Warnings: %d",
            sourceType, avatarName, avatarId, grade.label(),
            modelStats.cubeCount(), modelStats.boneCount(), modelStats.animationCount(),
            modelStats.textureCount(), scriptStats.scriptCount(), scriptStats.totalLines(),
            errorCount(), warningCount()
        );
    }

    /**
     * Generates a comprehensive GitHub Flavored Markdown report.
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Shyne Avatar Import Report: ").append(avatarName).append(" (`").append(avatarId).append("`)\n\n");
        sb.append("**Source Type:** ").append(sourceType).append("  \n");
        sb.append("**Author:** ").append(author.isBlank() ? "Unknown" : author).append("  \n");
        sb.append("**Model Format:** ").append(modelFormat).append("  \n");
        sb.append("**Compatibility Grade:** **").append(grade.label()).append("** (").append(grade.description()).append(")  \n\n");

        sb.append("## 1. Model & Texture Metrics\n\n");
        sb.append("| Metric | Value | Budget / Recommendation |\n");
        sb.append("|---|---|---|\n");
        sb.append("| **Cubes** | ").append(modelStats.cubeCount()).append(" | ≤ 256 recommended |\n");
        sb.append("| **Meshes** | ").append(modelStats.meshCount()).append(" | ≤ 16 recommended |\n");
        sb.append("| **Bones** | ").append(modelStats.boneCount()).append(" (Roots: ").append(modelStats.rootBoneCount()).append(", Max Depth: ").append(modelStats.maxDepth()).append(") | Clean hierarchy |\n");
        sb.append("| **Textures** | ").append(modelStats.textureCount()).append(" | ≤ 8 textures |\n");
        sb.append("| **Max Texture Size** | ").append(modelStats.maxTextureWidth()).append("x").append(modelStats.maxTextureHeight()).append(" | ≤ 512x512 budget |\n");
        sb.append("| **Animations** | ").append(modelStats.animationCount()).append(" (Keyframes: ").append(modelStats.totalKeyframes()).append(") | Loop / Playback |\n");
        if (!modelStats.animationNames().isEmpty()) {
            sb.append("| **Animation List** | `").append(String.join("`, `", modelStats.animationNames())).append("` | |\n");
        }
        if (!modelStats.easingTypes().isEmpty()) {
            sb.append("| **Detected Easing** | `").append(String.join("`, `", modelStats.easingTypes())).append("` | Linear, Bezier, Step |\n");
        }
        sb.append("\n");

        sb.append("## 2. Script Metrics & API Detection\n\n");
        sb.append("- **Lua Scripts:** ").append(scriptStats.scriptCount()).append(" files, ").append(scriptStats.totalLines()).append(" lines of code\n");
        if (!scriptStats.scriptFiles().isEmpty()) {
            sb.append("- **Files:** `").append(String.join("`, `", scriptStats.scriptFiles())).append("`\n");
        }
        if (!scriptStats.detectedApis().isEmpty()) {
            sb.append("- **Detected APIs:** `").append(String.join("`, `", scriptStats.detectedApis())).append("`\n");
        }
        sb.append("\n");

        sb.append("## 3. Linter Diagnostic Findings (").append(issues.size()).append(")\n\n");
        if (issues.isEmpty()) {
            sb.append("✨ No issues or warnings found! Clean avatar scripts.\n\n");
        } else {
            sb.append("| Severity | Location | Code | Message | Suggestion |\n");
            sb.append("|---|---|---|---|---|\n");
            for (AvatarLintIssue issue : issues) {
                String loc = issue.file() + (issue.line() > 0 ? ":" + issue.line() : "");
                String rec = issue.recommendation() == null ? "" : issue.recommendation();
                sb.append("| ").append(issue.severity()).append(" | `").append(loc).append("` | `")
                  .append(issue.code()).append("` | ").append(issue.message()).append(" | ").append(rec).append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("## 4. Recommendations & Actions\n\n");
        if (recommendations.isEmpty()) {
            sb.append("Ready to equip in Shyne Creator! Use `/shyne avatar switch ").append(avatarId).append("` or the Avatar Manager Screen.\n");
        } else {
            for (String rec : recommendations) {
                sb.append("- ").append(rec).append("\n");
            }
        }

        return sb.toString();
    }

    public void saveReport(Path destination) throws IOException {
        if (destination != null) {
            Path parent = destination.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(destination, toMarkdown(), StandardCharsets.UTF_8);
        }
    }
}

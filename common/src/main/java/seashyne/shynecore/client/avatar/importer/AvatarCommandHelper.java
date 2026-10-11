package seashyne.shynecore.client.avatar.importer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.avatar.AvatarCatalogEntry;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.avatar.linter.AvatarLintIssue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Shared command execution logic for /avatar commands across Fabric and NeoForge.
 */
public final class AvatarCommandHelper {
    private AvatarCommandHelper() {}

    public static Path safeAvatarsDir() {
        try {
            return AvatarLoader.avatarsDir();
        } catch (Throwable t) {
            return Path.of("shyne-mods", "avatars");
        }
    }

    public static int executeDiagnoseActive(Consumer<Component> sender) {
        AvatarState active = AvatarRuntime.active();
        if (active == null || active.rootDir() == null) {
            sender.accept(Component.literal("§c[Shyne] No avatar currently active. Use /avatar diagnose <folder> or select an avatar in Shyne Manager.§r"));
            return 0;
        }
        return printDiagnosis(active.rootDir(), sender);
    }

    public static int executeDiagnose(String targetName, Consumer<Component> sender) {
        return executeDiagnose(safeAvatarsDir(), targetName, sender);
    }

    public static int executeDiagnose(Path avatarsDir, String targetName, Consumer<Component> sender) {
        if (targetName == null || targetName.isBlank()) {
            return executeDiagnoseActive(sender);
        }

        Optional<Path> found = AvatarImporter.findAvatarDirectory(avatarsDir, targetName);
        if (found.isEmpty()) {
            // Check in loaded catalog
            for (AvatarCatalogEntry entry : AvatarRuntime.catalog()) {
                if (entry.id().equalsIgnoreCase(targetName) || entry.name().equalsIgnoreCase(targetName)) {
                    found = Optional.of(entry.root());
                    break;
                }
            }
        }

        if (found.isEmpty()) {
            sender.accept(Component.literal("§c[Shyne] Avatar folder or ID '" + targetName + "' not found in shyne-mods/avatars/.§r"));
            return 0;
        }

        return printDiagnosis(found.get(), sender);
    }

    private static int printDiagnosis(Path dir, Consumer<Component> sender) {
        AvatarImportReport report = AvatarImporter.analyze(dir);
        AvatarImportReport.CompatibilityGrade grade = report.grade();

        String gradeColor = switch (grade) {
            case A_PERFECT -> "§a§l";
            case B_COMPATIBLE_WITH_WARNINGS -> "§e§l";
            case C_NEEDS_MODIFICATIONS -> "§6§l";
            case D_INCOMPATIBLE -> "§c§l";
        };

        sender.accept(Component.literal("§b═════════════ [Shyne Avatar Diagnostics] ═════════════§r"));
        sender.accept(Component.literal("§fAvatar: §b" + report.avatarName() + " §7(ID: §f" + report.avatarId() + "§7) | Source: §e" + report.sourceType()));
        sender.accept(Component.literal("§fAuthor: §d" + (report.author().isBlank() ? "Unknown" : report.author()) + " §7| Format: §7" + report.modelFormat()));
        sender.accept(Component.literal("§fCompatibility Grade: " + gradeColor + grade.label() + " §7- " + grade.description()));

        var m = report.modelStats();
        sender.accept(Component.literal("§7• Model: §f" + m.cubeCount() + " cubes, §f" + m.meshCount() + " meshes, §f" + m.boneCount() + " bones, §f" + m.textureCount() + " textures (" + m.maxTextureWidth() + "x" + m.maxTextureHeight() + ")"));

        var s = report.scriptStats();
        sender.accept(Component.literal("§7• Scripts: §f" + s.scriptCount() + " files, §f" + s.totalLines() + " lines §7| APIs: §f" + (s.detectedApis().isEmpty() ? "none" : String.join(", ", s.detectedApis()))));

        if (!report.issues().isEmpty()) {
            sender.accept(Component.literal("§6Issues / Warnings (" + report.issues().size() + "):§r"));
            int shown = 0;
            for (AvatarLintIssue issue : report.issues()) {
                if (shown++ >= 4) {
                    sender.accept(Component.literal("§7  ... and " + (report.issues().size() - 4) + " more issues (see avatar_import_report.md)"));
                    break;
                }
                String color = issue.severity() == AvatarLintIssue.Severity.ERROR ? "§c× " : "§e! ";
                sender.accept(Component.literal("  " + color + issue.code() + ": §7" + issue.message()));
            }
        }

        if (!report.recommendations().isEmpty()) {
            sender.accept(Component.literal("§aRecommendation: §f" + report.recommendations().get(0)));
        }
        sender.accept(Component.literal("§b═══════════════════════════════════════════════════════§r"));
        return 1;
    }

    public static int executeImport(String folderName, Consumer<Component> sender) {
        return executeImport(safeAvatarsDir(), folderName, sender);
    }

    public static int executeImport(Path avatarsDir, String folderName, Consumer<Component> sender) {
        if (folderName == null || folderName.isBlank()) {
            sender.accept(Component.literal("§c[Shyne] Usage: /avatar import <folder_name>§r"));
            return 0;
        }

        Optional<Path> found = AvatarImporter.findAvatarDirectory(avatarsDir, folderName);
        if (found.isEmpty()) {
            sender.accept(Component.literal("§c[Shyne] Folder '" + folderName + "' not found in " + avatarsDir.toString() + "§r"));
            return 0;
        }

        Path target = found.get();
        sender.accept(Component.literal("§e[Shyne] Converting and importing avatar in '" + target.getFileName() + "'...§r"));

        AvatarImporter.ConversionResult result = AvatarImporter.convert(target);
        if (result.success()) {
            AvatarImportReport report = result.report();
            sender.accept(Component.literal("§a[Shyne] Successfully converted '§f" + report.avatarName() + "§a' into Shyne Standard 2.0!§r"));
            sender.accept(Component.literal("§7• Creator Attribution preserved for: §e" + (report.author().isBlank() ? "Unknown Creator" : report.author()) + "§7 in ATTRIBUTION.md"));
            sender.accept(Component.literal("§7• Generated standard avatar.json and exported diagnostics report."));
            try {
                AvatarRuntime.refreshCatalogAsync(true);
            } catch (Throwable ignored) {}
            return 1;
        } else {
            sender.accept(Component.literal("§c[Shyne] Import failed: " + result.message() + "§r"));
            return 0;
        }
    }

    public static int executeListUnimported(Consumer<Component> sender) {
        return executeListUnimported(safeAvatarsDir(), sender);
    }

    public static int executeListUnimported(Path avatarsDir, Consumer<Component> sender) {
        List<Path> unimported = AvatarImporter.scanUnimported(avatarsDir);
        if (unimported.isEmpty()) {
            sender.accept(Component.literal("§a[Shyne] No unimported Figura or Blockbench packages found in shyne-mods/avatars/.§r"));
            return 1;
        }

        sender.accept(Component.literal("§b[Shyne] Found " + unimported.size() + " candidate avatar folder(s) ready to import:§r"));
        for (Path path : unimported) {
            String name = path.getFileName().toString();
            sender.accept(Component.literal("§7• §f" + name + " §7(Run §e/avatar import " + name + "§7 to convert)§r"));
        }
        return 1;
    }

    public static int executeExportReport(Consumer<Component> sender) {
        AvatarState active = AvatarRuntime.active();
        if (active == null || active.rootDir() == null) {
            sender.accept(Component.literal("§c[Shyne] No avatar currently active. Select an avatar first or use /avatar diagnose <folder>.§r"));
            return 0;
        }

        try {
            Path reportPath = AvatarImporter.exportReport(active.rootDir());
            sender.accept(Component.literal("§a[Shyne] Exported markdown report to: §f" + reportPath.getFileName() + "§r"));
            return 1;
        } catch (IOException e) {
            sender.accept(Component.literal("§c[Shyne] Failed to export report: " + e.getMessage() + "§r"));
            return 0;
        }
    }
}

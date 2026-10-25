package seashyne.shynecore.client.avatar.importer;

import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarZipExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Handles drag-and-drop installation of avatar folders, {@code .zip} archives, and standalone
 * {@code .bbmodel} files dropped directly onto Shyne Creator screens.
 *
 * <p>Execution Context: Client Thread / Async Worker.</p>
 *
 * ระบบติดตั้ง Avatar แบบลากไฟล์วางลงหน้าต่างเกม รองรับทั้งไฟล์ .zip, โฟลเดอร์ Figura/Shyne,
 * และไฟล์ .bbmodel เดี่ยวๆ พร้อมสร้าง avatar.json ให้อัตโนมัติ.
 */
public final class AvatarDragDropInstaller {
    private AvatarDragDropInstaller() {}

    public record DropResult(int installedCount, List<String> installedNames, List<String> errors) {
        public boolean hasInstalled() { return installedCount > 0; }
        public String message() {
            if (installedCount > 0) {
                return "Installed " + installedCount + " avatar(s): " + String.join(", ", installedNames);
            }
            if (!errors.isEmpty()) {
                return "Drop failed: " + errors.get(0);
            }
            return "No valid avatar files dropped (.zip, .bbmodel, or folder).";
        }
    }

    /**
     * Installs dropped files/directories into the default avatars directory.
     * ติดตั้งรายการไฟล์หรือโฟลเดอร์ที่ถูกลากวางเข้าสู่โฟลเดอร์ avatars เป้าหมาย.
     */
    public static DropResult installDroppedFiles(List<Path> droppedPaths) {
        return installDroppedFiles(droppedPaths, AvatarLoader.avatarsDir());
    }

    public static DropResult installDroppedFiles(List<Path> droppedPaths, Path avatarsDir) {
        if (droppedPaths == null || droppedPaths.isEmpty() || avatarsDir == null) {
            return new DropResult(0, List.of(), List.of());
        }

        List<String> installed = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            Files.createDirectories(avatarsDir);
        } catch (IOException e) {
            return new DropResult(0, List.of(), List.of("Could not create avatars directory: " + e.getMessage()));
        }

        for (Path source : droppedPaths) {
            if (source == null || !Files.exists(source)) continue;
            try {
                Path normalizedSource = source.toAbsolutePath().normalize();
                String fileName = normalizedSource.getFileName() != null ? normalizedSource.getFileName().toString() : "";
                String lower = fileName.toLowerCase(Locale.ROOT);

                if (Files.isDirectory(normalizedSource)) {
                    String folderName = sanitizeFolderName(fileName);
                    Path destDir = resolveUniqueDir(avatarsDir, folderName);
                    copyDirectory(normalizedSource, destDir);
                    if (!Files.isRegularFile(destDir.resolve("avatar.json"))) {
                        AvatarImporter.convert(destDir);
                    }
                    installed.add(destDir.getFileName().toString());
                } else if (lower.endsWith(".zip")) {
                    String rawName = fileName.substring(0, fileName.length() - 4);
                    String safeName = rawName.replaceAll("[^a-zA-Z0-9_.-]", "_");
                    if (safeName.isBlank()) safeName = "unnamed_avatar";
                    AvatarZipExtractor.extractIfZip(normalizedSource, avatarsDir);
                    Path extractedDir = avatarsDir.resolve(safeName);
                    if (Files.isDirectory(extractedDir) && !Files.isRegularFile(extractedDir.resolve("avatar.json"))) {
                        AvatarImporter.convert(extractedDir);
                    }
                    installed.add(safeName);
                } else if (lower.endsWith(".bbmodel")) {
                    String baseName = sanitizeFolderName(fileName.substring(0, fileName.length() - ".bbmodel".length())).toLowerCase(Locale.ROOT);
                    Path destDir = resolveUniqueDir(avatarsDir, baseName);
                    Files.createDirectories(destDir);
                    Path destModel = destDir.resolve("model.bbmodel");
                    Files.copy(normalizedSource, destModel, StandardCopyOption.REPLACE_EXISTING);
                    AvatarImporter.convert(destDir);
                    installed.add(destDir.getFileName().toString());
                } else {
                    errors.add("Unsupported file type: " + fileName + " (drop .zip, .bbmodel, or folder)");
                }
            } catch (Exception e) {
                ShyneCore.LOGGER.warn("[AvatarDragDrop] Failed to install {}: {}", source, e.getMessage());
                errors.add(source.getFileName() + ": " + e.getMessage());
            }
        }

        return new DropResult(installed.size(), List.copyOf(installed), List.copyOf(errors));
    }

    private static String sanitizeFolderName(String raw) {
        if (raw == null || raw.isBlank()) return "imported_avatar";
        String cleaned = raw.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.isBlank() ? "imported_avatar" : cleaned;
    }

    private static Path resolveUniqueDir(Path parentDir, String baseName) {
        Path candidate = parentDir.resolve(baseName).normalize();
        if (!Files.exists(candidate)) return candidate;
        for (int i = 2; i < 1000; i++) {
            Path numbered = parentDir.resolve(baseName + "_" + i).normalize();
            if (!Files.exists(numbered)) return numbered;
        }
        return parentDir.resolve(baseName + "_" + System.currentTimeMillis()).normalize();
    }

    private static void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            for (Path sourcePath : stream.toList()) {
                Path rel = sourceDir.relativize(sourcePath);
                Path destPath = targetDir.resolve(rel).normalize();
                if (!destPath.startsWith(targetDir.toAbsolutePath().normalize())) continue;
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(destPath);
                } else if (Files.isRegularFile(sourcePath)) {
                    Files.createDirectories(destPath.getParent());
                    Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}

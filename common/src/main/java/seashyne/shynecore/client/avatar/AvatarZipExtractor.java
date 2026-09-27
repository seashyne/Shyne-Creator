package seashyne.shynecore.client.avatar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts and caches Avatar .zip archives into temporary disk folders
 * so the standard Avatar loader, file watchers, and Lua runtimes can access them seamlessly.
 */
public final class AvatarZipExtractor {
    private AvatarZipExtractor() {}

    public static Path extractIfZip(Path zipPath, Path cacheDir) {
        if (zipPath == null || !Files.isRegularFile(zipPath)) return null;
        String fileName = zipPath.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) return null;

        String rawName = fileName.substring(0, fileName.length() - 4);
        String safeName = rawName.replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (safeName.isBlank()) safeName = "unnamed_avatar";

        Path targetDir = cacheDir.resolve(safeName).toAbsolutePath().normalize();
        try {
            long zipModified = Files.getLastModifiedTime(zipPath).toMillis();
            Path marker = targetDir.resolve(".zip_timestamp");

            boolean needsExtract = true;
            if (Files.isDirectory(targetDir) && Files.isRegularFile(marker)) {
                try {
                    long cachedTime = Long.parseLong(Files.readString(marker).trim());
                    if (cachedTime == zipModified) {
                        needsExtract = false;
                    }
                } catch (Exception ignored) {
                    needsExtract = true;
                }
            }

            if (needsExtract) {
                Files.createDirectories(targetDir);
                cleanDirectory(targetDir);
                Files.createDirectories(targetDir);

                try (InputStream fileIn = Files.newInputStream(zipPath);
                     ZipInputStream zipIn = new ZipInputStream(fileIn)) {
                    ZipEntry entry;
                    while ((entry = zipIn.getNextEntry()) != null) {
                        String name = entry.getName();
                        Path entryPath = targetDir.resolve(name).normalize();
                        if (!entryPath.startsWith(targetDir)) {
                            // Zip slip protection
                            continue;
                        }
                        if (entry.isDirectory()) {
                            Files.createDirectories(entryPath);
                        } else {
                            if (entryPath.getParent() != null) {
                                Files.createDirectories(entryPath.getParent());
                            }
                            try (OutputStream out = Files.newOutputStream(entryPath)) {
                                zipIn.transferTo(out);
                            }
                        }
                        zipIn.closeEntry();
                    }
                }
                Files.writeString(marker, String.valueOf(zipModified));
            }

            // Find valid avatar root (either targetDir or single wrapped root folder)
            if (Files.isRegularFile(targetDir.resolve("avatar.json"))) {
                return targetDir;
            }
            try (var subDirs = Files.list(targetDir)) {
                for (Path sub : subDirs.filter(Files::isDirectory).toList()) {
                    if (Files.isRegularFile(sub.resolve("avatar.json"))) {
                        return sub;
                    }
                }
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void cleanDirectory(Path dir) {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a))
                  .forEach(p -> {
                      try {
                          Files.deleteIfExists(p);
                      } catch (IOException ignored) {}
                  });
        } catch (IOException ignored) {}
    }
}

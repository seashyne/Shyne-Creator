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

                long totalExtractedBytes = 0;
                int totalEntries = 0;
                final long maxBytes = 128L * 1024L * 1024L; // 128 MB maximum uncompressed size
                final int maxEntries = 1024; // 1024 files maximum

                try (InputStream fileIn = Files.newInputStream(zipPath);
                     ZipInputStream zipIn = new ZipInputStream(fileIn)) {
                    ZipEntry entry;
                    byte[] buffer = new byte[8192];
                    while ((entry = zipIn.getNextEntry()) != null) {
                        totalEntries++;
                        if (totalEntries > maxEntries) {
                            throw new IOException("Avatar archive contains too many files (> " + maxEntries + ")");
                        }
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
                                int read;
                                while ((read = zipIn.read(buffer)) != -1) {
                                    totalExtractedBytes += read;
                                    if (totalExtractedBytes > maxBytes) {
                                        throw new IOException("Avatar archive exceeded maximum uncompressed size of 128 MB");
                                    }
                                    out.write(buffer, 0, read);
                                }
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

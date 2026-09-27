package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class AvatarZipExtractorTest {

    @Test
    void testExtractValidAvatarZip(@TempDir Path tempDir) throws IOException {
        Path zipFile = tempDir.resolve("my_avatar.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("avatar.json"));
            zos.write("{\"id\":\"test.zip_avatar\",\"name\":\"Zip Avatar\"}".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("script.lua"));
            zos.write("print('hello from zip')".getBytes());
            zos.closeEntry();
        }

        Path cacheDir = tempDir.resolve(".extracted");
        Path root = AvatarZipExtractor.extractIfZip(zipFile, cacheDir);

        assertNotNull(root, "Root should be extracted and returned");
        assertTrue(Files.isRegularFile(root.resolve("avatar.json")));
        assertTrue(Files.isRegularFile(root.resolve("script.lua")));

        // Calling extractIfZip again should use cached version without error
        Path cachedRoot = AvatarZipExtractor.extractIfZip(zipFile, cacheDir);
        assertEquals(root, cachedRoot);
    }

    @Test
    void testIgnoreNonZipFiles(@TempDir Path tempDir) throws IOException {
        Path textFile = tempDir.resolve("not_a_zip.txt");
        Files.writeString(textFile, "some text");

        Path cacheDir = tempDir.resolve(".extracted");
        assertNull(AvatarZipExtractor.extractIfZip(textFile, cacheDir));
    }
}

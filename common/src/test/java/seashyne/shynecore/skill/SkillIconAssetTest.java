package seashyne.shynecore.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SkillIconAssetTest {
    @TempDir Path temp;

    @Test
    void resolvesVerifiedPngIconDeclaredByPackageManifest() throws IOException {
        Path root = temp.resolve("arcane-pack");
        Path skill = root.resolve("skills/arc_bolt.json");
        Path icon = root.resolve("assets/icons/arc_bolt.png");
        Files.createDirectories(skill.getParent());
        Files.createDirectories(icon.getParent());
        Files.writeString(root.resolve("shyne-package.json"), """
            {"format":"shyne_asset_package","format_version":1,"id":"arcane_pack","assets":[
              {"id":"arc_bolt","type":"png_icon","path":"assets/icons/arc_bolt.png"}
            ]}
            """);
        Files.writeString(skill, "{}");
        Files.write(icon, pngHeader(64, 64));

        SkillIconAsset.Resolution result = SkillIconAsset.resolve(skill, "arc_bolt");

        assertTrue(result.hasAsset(), result.error());
        assertEquals("arc_bolt", result.asset().assetId());
        assertEquals("assets/icons/arc_bolt.png", result.asset().relativePath());
        assertEquals(64, result.asset().width());
        assertEquals(64, result.asset().height());
        assertFalse(result.asset().contentBase64().isBlank());
    }

    @Test
    void rejectsPackageTraversalAndNonPngAssets() throws IOException {
        Path root = temp.resolve("unsafe-pack");
        Path skill = root.resolve("skills/unsafe.json");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "{}");
        Files.writeString(root.resolve("shyne-package.json"), """
            {"format":"shyne_asset_package","format_version":1,"id":"unsafe_pack","assets":[
              {"id":"unsafe","type":"png_icon","path":"assets/icons/../secret.svg"}
            ]}
            """);

        SkillIconAsset.Resolution result = SkillIconAsset.resolve(skill, "unsafe");

        assertFalse(result.hasAsset());
        assertTrue(result.hasError());
    }

    @Test
    void rejectsIconSymlinkThatEscapesPackage() throws IOException {
        Path root = temp.resolve("linked-pack");
        Path skill = root.resolve("skills/linked.json");
        Path iconLink = root.resolve("assets/icons/linked.png");
        Path outside = temp.resolve("outside.png");
        Files.createDirectories(skill.getParent());
        Files.createDirectories(iconLink.getParent());
        Files.writeString(skill, "{}");
        Files.write(outside, pngHeader(64, 64));
        Files.writeString(root.resolve("shyne-package.json"), """
            {"format":"shyne_asset_package","format_version":1,"id":"linked_pack","assets":[
              {"id":"linked","type":"png_icon","path":"assets/icons/linked.png"}
            ]}
            """);
        try {
            Files.createSymbolicLink(iconLink, outside);
        } catch (UnsupportedOperationException | IOException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable on this test filesystem.");
        }

        SkillIconAsset.Resolution result = SkillIconAsset.resolve(skill, "linked");

        assertFalse(result.hasAsset());
        assertTrue(result.hasError());
    }

    private static byte[] pngHeader(int width, int height) {
        byte[] bytes = new byte[24];
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        bytes[11] = 13;
        bytes[12] = 'I'; bytes[13] = 'H'; bytes[14] = 'D'; bytes[15] = 'R';
        writeInt(bytes, 16, width);
        writeInt(bytes, 20, height);
        return bytes;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}

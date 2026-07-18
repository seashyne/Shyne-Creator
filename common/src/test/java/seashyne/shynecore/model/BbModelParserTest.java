package seashyne.shynecore.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class BbModelParserTest {
    @TempDir Path temp;

    @Test
    void readsFiguraDataPointsAndDiscoversArbitrarilyNamedTexture() throws Exception {
        Path texture = temp.resolve("nested/anything-at-all.png");
        Files.createDirectories(texture.getParent());
        Files.write(texture, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});
        Path modelFile = temp.resolve("avatar.bbmodel");
        Files.writeString(modelFile, """
            {
              "name":"Parser Fixture",
              "resolution":{"width":64,"height":32},
              "outliner":[{"name":"root","uuid":"root-bone","origin":[0,0,0],"children":[]}],
              "animations":[{"name":"turn","length":1,"loop":"loop","animators":{"root-bone":{"rotation":[
                {"time":0.5,"data_points":[{"x":12,"y":-34,"z":56}],"easing":"easeInOut"}
              ]}}}]
            }
            """);

        BbModelDefinition model = BbModelParser.parse(modelFile, "test");

        assertEquals("nested/anything-at-all.png", model.primaryTextureRelativePath());
        BbKeyframe key = model.findAnimation("turn").boneAnimations().get("root-bone").rotation().getFirst();
        assertAll(
            () -> assertEquals(0.5f, key.time()),
            () -> assertEquals(12f, key.x()),
            () -> assertEquals(-34f, key.y()),
            () -> assertEquals(56f, key.z()),
            () -> assertEquals("easeInOut", key.easing())
        );
    }

    @Test
    void rejectsAmbiguousTextureNamesInsteadOfChoosingRandomly() throws Exception {
        Files.createDirectories(temp.resolve("one"));
        Files.createDirectories(temp.resolve("two"));
        Files.write(temp.resolve("one/skin.png"), new byte[] {1});
        Files.write(temp.resolve("two/skin.png"), new byte[] {2});
        Path modelFile = temp.resolve("avatar.bbmodel");
        Files.writeString(modelFile, """
            {"textures":[{"id":"0","name":"skin.png","relative_path":"missing/skin.png"}]}
            """);

        IOException error = assertThrows(IOException.class, () -> BbModelParser.parse(modelFile, "test"));
        assertTrue(error.getMessage().contains("Ambiguous texture 'skin.png'"));
    }
}

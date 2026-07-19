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
    void readsFiguraSharedAnimatorKeyframeArray() throws Exception {
        Path modelFile = temp.resolve("figura.bbmodel");
        Files.writeString(modelFile, """
            {
              "outliner":[{"name":"Tail1","uuid":"tail-bone","origin":[0,0,0],"children":[]}],
              "animations":[{"name":"small","length":1,"loop":"loop","animators":{"tail-bone":{"keyframes":[
                {"channel":"rotation","time":0,"data_points":[{"x":"60","y":"0","z":"0"}],"interpolation":"linear"},
                {"channel":"position","time":0.25,"data_points":[{"x":"0","y":"1","z":"1"}],"interpolation":"linear"}
              ]}}}]
            }
            """);

        BbBoneAnimation animation = BbModelParser.parse(modelFile, "test")
            .findAnimation("small").boneAnimations().get("tail-bone");

        assertAll(
            () -> assertEquals(60f, animation.rotation().getFirst().x()),
            () -> assertEquals(0.25f, animation.position().getFirst().time()),
            () -> assertEquals(1f, animation.position().getFirst().y()),
            () -> assertTrue(animation.scale().isEmpty())
        );
    }

    @Test
    void preservesExpressionsPrePostValuesAndBezierHandles() throws Exception {
        Path modelFile = temp.resolve("curves.bbmodel");
        Files.writeString(modelFile, """
            {
              "outliner":[{"name":"Tail","uuid":"tail","origin":[0,0,0],"children":[]}],
              "animations":[{"name":"wave","length":1,"loop":"loop","animators":{"tail":{"keyframes":[
                {"channel":"rotation","time":0,"data_points":[
                  {"x":"v.pitch","y":"0","z":"0"},
                  {"x":"Math.sin(q.anim_time*180)","y":"1","z":"2"}
                ],"interpolation":"bezier","bezier_right_time":[0.1,0.2,0.3],"bezier_right_value":[4,5,6]}
              ]}}}]
            }
            """);

        BbKeyframe key = BbModelParser.parse(modelFile, "test")
            .findAnimation("wave").boneAnimations().get("tail").rotation().getFirst();

        assertAll(
            () -> assertEquals("v.pitch", key.pre().x()),
            () -> assertEquals("Math.sin(q.anim_time*180)", key.post().x()),
            () -> assertEquals("bezier", key.easing()),
            () -> assertEquals(0.2f, key.bezier().rightTimeY()),
            () -> assertEquals(6f, key.bezier().rightValueZ())
        );
    }

    @Test
    void migratesPreFiveAnimationAxesLikeCurrentBlockbench() throws Exception {
        Path modelFile = temp.resolve("legacy.bbmodel");
        Files.writeString(modelFile, """
            {
              "meta":{"format_version":"4.10","model_format":"free"},
              "outliner":[{"name":"root","uuid":"root","origin":[0,0,0],"children":[]}],
              "animations":[{"name":"legacy","length":1,"animators":{"root":{"keyframes":[
                {"channel":"rotation","time":0,"data_points":[{"x":"v.pitch+10","y":"20","z":"30"}]},
                {"channel":"position","time":0,"data_points":[{"x":"4","y":"5","z":"6"}]}
              ]}}}]
            }
            """);

        BbBoneAnimation animation = BbModelParser.parse(modelFile, "test")
            .findAnimation("legacy").boneAnimations().get("root");

        assertAll(
            () -> assertEquals("-(v.pitch+10)", animation.rotation().getFirst().pre().x()),
            () -> assertEquals(-20f, animation.rotation().getFirst().y()),
            () -> assertEquals(30f, animation.rotation().getFirst().z()),
            () -> assertEquals(-4f, animation.position().getFirst().x()),
            () -> assertEquals(5f, animation.position().getFirst().y())
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

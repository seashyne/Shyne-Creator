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
    void resolvesBlockbenchFiveGroupDefinitionsReferencedByOutlinerUuid() throws Exception {
        Path modelFile = temp.resolve("blockbench-five.bbmodel");
        Files.writeString(modelFile, """
            {
              "meta":{"format_version":"5.0"},
              "groups":[
                {"name":"root","uuid":"root","origin":[0,0,0],"children":[]},
                {"name":"Tail","uuid":"tail","origin":[0,13,1.5],"children":[]},
                {"name":"TailSegment1","uuid":"segment","origin":[0,13,2.5],"parent_type":"Body","children":[]}
              ],
              "outliner":[{"uuid":"root","children":[{"uuid":"tail","children":["cube-0",{"uuid":"segment","children":["cube-1"]}]}]}],
              "elements":[
                {"type":"cube","uuid":"cube-0","from":[-0.5,12.5,1.5],"to":[0.5,13.5,3.5]},
                {"type":"cube","uuid":"cube-1","from":[-0.5,12.5,2.5],"to":[0.5,13.5,9.5]}
              ]
            }
            """);

        BbModelDefinition model = BbModelParser.parse(modelFile, "test");
        BbBoneDefinition tail = model.findBoneByUuid("tail");
        BbBoneDefinition segment = model.findBoneByUuid("segment");

        assertAll(
            () -> assertEquals("Tail", tail.name()),
            () -> assertEquals(13f, tail.pivotY()),
            () -> assertEquals(1.5f, tail.pivotZ()),
            () -> assertEquals("tail", segment.parentUuid()),
            () -> assertEquals("Tail", segment.parentName()),
            () -> assertEquals(2.5f, segment.pivotZ()),
            () -> assertEquals("Body", segment.parentType()),
            () -> assertEquals("tail", model.cubes().get(0).parentBoneUuid()),
            () -> assertEquals("segment", model.cubes().get(1).parentBoneUuid())
        );
    }

    @Test
    void preservesBlockbenchParentTypeForAutomaticVanillaBinding() throws Exception {
        Path modelFile = temp.resolve("bunny-ears.bbmodel");
        Files.writeString(modelFile, """
            {"outliner":[{"name":"BunnyEars","uuid":"ears","parent_type":"Head","shyne_role":"ears","shyne_tags":["accessory","cosmetic"],"origin":[0,24,0],"children":[]}]}
            """);

        BbModelDefinition model = BbModelParser.parse(modelFile, "test");

        assertAll(
            () -> assertEquals("Head", model.findBoneByUuid("ears").parentType()),
            () -> assertEquals("ears", model.findBoneByUuid("ears").role()),
            () -> assertEquals(java.util.List.of("accessory", "cosmetic"), model.findBoneByUuid("ears").tags())
        );
    }

    @Test
    void preservesNativePhysicsPresetFromGroupDefinition() throws Exception {
        Path modelFile = temp.resolve("physics.bbmodel");
        Files.writeString(modelFile, """
            {
              "groups":[{"name":"Tail","uuid":"tail","shyne_physics":"tail","children":[]}],
              "outliner":[{"uuid":"tail","children":[]}]
            }
            """);

        BbModelDefinition model = BbModelParser.parse(modelFile, "test");

        assertEquals("tail", model.findBoneByUuid("tail").physicsPreset());
    }

    @Test
    void parsesFreeMeshGeometryUvsTransformVisibilityAndParent() throws Exception {
        Path modelFile = temp.resolve("mesh.bbmodel");
        Files.writeString(modelFile, """
            {
              "resolution":{"width":64,"height":32},
              "textures":[{"id":"0","name":"skin.png","relative_path":"skin.png"}],
              "outliner":[{"name":"Head","uuid":"head","origin":[0,24,0],"children":["mesh-id"]}],
              "elements":[{
                "type":"mesh","uuid":"mesh-id","name":"EarMesh",
                "origin":[1,25,-2],"rotation":[10,20,30],"visibility":false,"export":true,
                "vertices":{"a":[0,0,0],"b":[2,0,0],"c":[2,3,0],"d":[0,3,0],"e":[1,4,0]},
                "faces":{"front":{"vertices":["a","b","c","d","e"],"uv":{
                  "a":[1,2],"b":[3,4],"c":[5,6],"d":[7,8],"e":[9,10]
                },"texture":0}}
              }]
            }
            """);

        BbModelDefinition model = BbModelParser.parse(modelFile, "test");
        BbMeshDefinition mesh = model.meshes().getFirst();
        BbMeshFaceDefinition face = mesh.faces().getFirst();

        assertAll(
            () -> assertEquals(1, model.meshes().size()),
            () -> assertTrue(model.cubes().isEmpty()),
            () -> assertTrue(model.hasGeometry()),
            () -> assertEquals("head", mesh.parentBoneUuid()),
            () -> assertEquals(1f, mesh.originX()),
            () -> assertEquals(20f, mesh.rotationY()),
            () -> assertFalse(mesh.visible()),
            () -> assertEquals(2f, mesh.vertex("b").x()),
            () -> assertEquals(java.util.List.of("a", "b", "c", "d", "e"), face.vertexIds()),
            () -> assertEquals(9f, face.uv("e").u()),
            () -> assertEquals(10f, face.uv("e").v()),
            () -> assertEquals(0, face.textureIndex()),
            () -> assertTrue(face.enabled()),
            () -> assertEquals("model.Head.EarMesh", model.meshPath(mesh))
        );
    }

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
    void preservesV4AnimationAxesLikeFiguraV4Loader() throws Exception {
        Path modelFile = temp.resolve("v4.bbmodel");
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
            () -> assertEquals("v.pitch+10", animation.rotation().getFirst().pre().x()),
            () -> assertEquals(20f, animation.rotation().getFirst().y()),
            () -> assertEquals(30f, animation.rotation().getFirst().z()),
            () -> assertEquals(4f, animation.position().getFirst().x()),
            () -> assertEquals(5f, animation.position().getFirst().y())
        );
    }

    @Test
    void convertsV5AnimationAxesLikeFiguraV5Loader() throws Exception {
        Path modelFile = temp.resolve("v5.bbmodel");
        Files.writeString(modelFile, """
            {
              "meta":{"format_version":"5.0","model_format":"free"},
              "groups":[{"name":"root","uuid":"root","origin":[0,0,0],"children":[]}],
              "outliner":[{"uuid":"root","children":[]}],
              "animations":[{"name":"modern","length":1,"animators":{"root":{"keyframes":[
                {"channel":"rotation","time":0,"data_points":[{"x":"v.pitch+10","y":"20","z":"30"}]},
                {"channel":"position","time":0,"data_points":[{"x":"4","y":"5","z":"6"}]}
              ]}}}]
            }
            """);

        BbBoneAnimation animation = BbModelParser.parse(modelFile, "test")
            .findAnimation("modern").boneAnimations().get("root");

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

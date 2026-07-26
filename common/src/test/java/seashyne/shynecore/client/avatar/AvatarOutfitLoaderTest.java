package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AvatarOutfitLoaderTest {
    @TempDir
    Path temp;

    @Test
    void plainPngIsOverlayAndReplacementRequiresAnExplicitSuffix() throws Exception {
        Path outfitDir = Files.createDirectories(temp.resolve("outfit"));
        writeSolidPng(outfitDir.resolve("1000012674.png"), 0x00000000);
        writeSolidPng(outfitDir.resolve("battle-REPLACE.PNG"), 0xFF102030);
        writeSolidPng(outfitDir.resolve("casual_replace.png"), 0xFF203040);
        writeSolidPng(outfitDir.resolve("formal.replace.png"), 0xFF304050);
        writeSolidPng(outfitDir.resolve("replacement.png"), 0xFF405060);

        List<AvatarOutfit> outfits = AvatarOutfitLoader.discover(temp);

        assertAll(
            () -> assertEquals(5, outfits.size()),
            () -> assertOutfit(outfits, "1000012674", "Outfit 1", AvatarOutfit.Mode.OVERLAY),
            () -> assertOutfit(outfits, "battle-replace", "battle", AvatarOutfit.Mode.REPLACE),
            () -> assertOutfit(outfits, "casual_replace", "casual", AvatarOutfit.Mode.REPLACE),
            () -> assertOutfit(outfits, "formal.replace", "formal", AvatarOutfit.Mode.REPLACE),
            () -> assertOutfit(outfits, "replacement", "replacement", AvatarOutfit.Mode.OVERLAY),
            () -> assertEquals(AvatarOutfit.Mode.OVERLAY, AvatarOutfitLoader.outfitMode("glow.overlay")),
            () -> assertEquals(AvatarOutfit.Mode.OVERLAY, AvatarOutfitLoader.outfitMode("replace"))
        );
    }

    @Test
    void overlayPreservesBasePixelsWhileReplacementKeepsTransparency() throws Exception {
        Path base = temp.resolve("base.png");
        Path outfitDir = Files.createDirectories(temp.resolve("outfit"));
        writeSolidPng(base, 0xFFFF0000);
        writeSolidPng(outfitDir.resolve("jacket.png"), 0x00000000);
        writeSolidPng(outfitDir.resolve("ghost.replace.png"), 0x00000000);

        List<AvatarOutfit> outfits = AvatarOutfitLoader.discover(temp);
        AvatarOutfit overlay = find(outfits, "jacket");
        AvatarOutfit replacement = find(outfits, "ghost.replace");

        BufferedImage composited = decode(AvatarOutfitLoader.compositedPng(overlay, base, 32, 32));
        BufferedImage replaced = decode(AvatarOutfitLoader.scaledPng(replacement, 32, 32));

        assertAll(
            () -> assertEquals(0xFFFF0000, composited.getRGB(0, 0)),
            () -> assertEquals(0x00000000, replaced.getRGB(0, 0))
        );
    }

    @Test
    void selectingOutfitDoesNotResetLuaOrAnimationState() {
        AvatarState state = new AvatarState(
            "wardrobe-test",
            "wardrobe-test:model",
            temp,
            true,
            Set.of(),
            Set.of()
        );
        state.vars().put("lua_counter", 7L);
        state.setCurrentAnimation("Walk");
        long animationStartedAt = state.currentAnimationStartedAtMillis();
        state.setAnimationParameter("speed", 0.75);
        AvatarAnimationLayer layer = new AvatarAnimationLayer(
            "Walk",
            animationStartedAt,
            1.0,
            true,
            1.0,
            1.0,
            0,
            2,
            2,
            List.of(),
            false,
            0L
        );
        state.animationLayers().put("locomotion", layer);
        state.clearAnimationParametersDirty();

        byte[] texture = {1, 2, 3, 4};
        state.selectOutfit("jacket", texture);

        assertAll(
            () -> assertEquals("jacket", state.selectedOutfitId()),
            () -> assertArrayEquals(texture, state.selectedOutfitTexture()),
            () -> assertEquals(7L, state.vars().get("lua_counter")),
            () -> assertEquals("Walk", state.currentAnimation()),
            () -> assertEquals(animationStartedAt, state.currentAnimationStartedAtMillis()),
            () -> assertEquals(0.75, state.animationParameter("speed", 0.0)),
            () -> assertSame(layer, state.animationLayers().get("locomotion")),
            () -> assertFalse(state.areAnimationParametersDirty()),
            () -> assertTrue(state.isSnapshotDirty())
        );
    }

    private static void assertOutfit(
        List<AvatarOutfit> outfits,
        String id,
        String name,
        AvatarOutfit.Mode mode
    ) {
        AvatarOutfit outfit = find(outfits, id);
        assertAll(
            () -> assertEquals(name, outfit.name()),
            () -> assertEquals(mode, outfit.mode()),
            () -> assertTrue(outfit.valid())
        );
    }

    private static AvatarOutfit find(List<AvatarOutfit> outfits, String id) {
        return outfits.stream()
            .filter(outfit -> outfit.id().equals(id))
            .findFirst()
            .orElseThrow();
    }

    private static BufferedImage decode(byte[] bytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new AssertionError("expected a readable PNG");
        return image;
    }

    private static void writeSolidPng(Path path, int argb) throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, argb);
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()));
    }
}

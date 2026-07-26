package seashyne.shynecore.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AvatarIconSizingTest {
    @Test
    void iconDimensionsKeepSmallImagesAndBoundLargeOnes() {
        assertDimensions(20, 20, 20, 20);
        assertDimensions(1_254, 1_254, 128, 128);
        assertDimensions(1_254, 627, 128, 64);
        assertDimensions(627, 1_254, 64, 128);
    }

    @Test
    void invalidDimensionsNeverProduceAnUploadableTexture() {
        assertDimensions(0, 128, 0, 0);
        assertDimensions(128, 0, 0, 0);
    }

    private static void assertDimensions(int width, int height, int expectedWidth, int expectedHeight) {
        AvatarIconSizing.Dimensions dimensions = AvatarIconSizing.fit(width, height);
        assertEquals(expectedWidth, dimensions.width());
        assertEquals(expectedHeight, dimensions.height());
    }
}

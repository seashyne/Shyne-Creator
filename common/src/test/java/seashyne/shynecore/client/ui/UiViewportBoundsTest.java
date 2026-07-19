package seashyne.shynecore.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiViewportBoundsTest {
    @Test
    void clipsPreviewToVisibleViewport() {
        UiViewportBounds bounds = UiViewportBounds.clip(-5, 10, 120, 90, 100, 80);
        assertEquals(new UiViewportBounds(0, 10, 100, 80), bounds);
        assertTrue(bounds.drawable());
    }

    @Test
    void rejectsPreviewThatIsCompletelyBelowViewport() {
        UiViewportBounds bounds = UiViewportBounds.clip(10, 90, 92, 120, 100, 80);
        assertEquals(new UiViewportBounds(10, 80, 92, 80), bounds);
        assertFalse(bounds.drawable());
    }
}

package seashyne.shynecore.client.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleMenuButtonLayoutTest {
    @Test
    void appendsToTheRightOfVanillaIconRow() {
        var widgets = List.of(
            new TitleMenuButtonLayout.Bounds(126, 180, 20, 20),
            new TitleMenuButtonLayout.Bounds(150, 180, 20, 20),
            new TitleMenuButtonLayout.Bounds(174, 180, 20, 20),
            new TitleMenuButtonLayout.Bounds(110, 204, 98, 20),
            new TitleMenuButtonLayout.Bounds(212, 204, 98, 20)
        );

        assertEquals(
            new TitleMenuButtonLayout.Position(198, 180),
            TitleMenuButtonLayout.findPosition(320, 260, widgets).orElseThrow()
        );
    }

    @Test
    void usesLeftSideWhenAnotherWidgetOccupiesTheRightSlot() {
        var widgets = List.of(
            new TitleMenuButtonLayout.Bounds(126, 180, 20, 20),
            new TitleMenuButtonLayout.Bounds(150, 180, 20, 20),
            new TitleMenuButtonLayout.Bounds(174, 180, 20, 20),
            new TitleMenuButtonLayout.Bounds(198, 178, 98, 24)
        );

        assertEquals(
            new TitleMenuButtonLayout.Position(102, 180),
            TitleMenuButtonLayout.findPosition(320, 260, widgets).orElseThrow()
        );
    }

    @Test
    void doesNotGuessAPlacementWithoutAnIconRow() {
        assertTrue(TitleMenuButtonLayout.findPosition(
            320,
            260,
            List.of(new TitleMenuButtonLayout.Bounds(110, 204, 98, 20))
        ).isEmpty());
    }
}

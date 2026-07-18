package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

final class AvatarStatePerformanceTest {
    @Test
    void sortedAnimationLayersAreReusedUntilTheLayerSetChanges() {
        AvatarState state = new AvatarState(
            "performance-test",
            "avatar:performance-test",
            Path.of("performance-test"),
            true,
            Set.of(),
            Set.of()
        );
        state.animationLayers().put("high", layer("high", 20));
        state.animationLayers().put("low", layer("low", -10));
        state.markAnimationLayersDirty();

        List<AvatarAnimationLayer> first = state.sortedAnimationLayers();
        List<AvatarAnimationLayer> cached = state.sortedAnimationLayers();

        assertEquals(List.of("low", "high"), first.stream().map(AvatarAnimationLayer::name).toList());
        assertSame(first, cached);

        state.animationLayers().put("middle", layer("middle", 0));
        state.markAnimationLayersDirty();
        List<AvatarAnimationLayer> refreshed = state.sortedAnimationLayers();

        assertNotSame(first, refreshed);
        assertEquals(List.of("low", "middle", "high"), refreshed.stream().map(AvatarAnimationLayer::name).toList());
    }

    private static AvatarAnimationLayer layer(String name, int priority) {
        return new AvatarAnimationLayer(name, 1L, 1.0, true, 1.0, 1.0, priority, 0, 0, List.of(), false, 0L);
    }
}

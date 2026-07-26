package seashyne.shynecore.avatar;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AvatarValueValidatorTest {
    @Test
    void acceptsBoundedJsonValues() {
        assertTrue(AvatarValueValidator.isSafe(Map.of(
            "enabled", true,
            "strength", 0.75,
            "colors", List.of("blue", "white")
        )));
    }

    @Test
    void rejectsNullCyclesAndNonFiniteNumbers() {
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("bad", null);
        assertFalse(AvatarValueValidator.isSafe(withNull));

        Map<String, Object> cycle = new LinkedHashMap<>();
        cycle.put("self", cycle);
        assertFalse(AvatarValueValidator.isSafe(cycle));
        assertFalse(AvatarValueValidator.isSafe(Double.NaN));
    }

    @Test
    void rejectsOversizedContainersAndDepth() {
        List<Object> oversized = new ArrayList<>();
        for (int i = 0; i <= AvatarValueValidator.MAX_CONTAINER_ENTRIES; i++) oversized.add(i);
        assertFalse(AvatarValueValidator.isSafe(oversized));

        Object nested = "leaf";
        for (int i = 0; i <= AvatarValueValidator.MAX_DEPTH; i++) nested = List.of(nested);
        assertFalse(AvatarValueValidator.isSafe(nested));
    }
}

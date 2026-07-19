package seashyne.shynecore.model;

import org.junit.jupiter.api.Test;
import seashyne.shynecore.client.render.AnimationExpressionContext;
import seashyne.shynecore.client.render.ShyneExpressionEngine;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ShyneExpressionEngineTest {
    @Test
    void evaluatesBlockbenchMathAndLuaParameters() {
        AnimationExpressionContext context = new AnimationExpressionContext(
            0.5, 0.2, 15, -5, true, true, Map.of("tail_strength", 2.0, "pitch", 4.0)
        );

        double value = ShyneExpressionEngine.evaluate(
            "q(...) + v.pitch + Math.sin(q.anim_time*180) * v.strength", context, 3
        );

        assertEquals(9.0, value, 0.0001);
    }

    @Test
    void reportsUnsupportedFunctionsBeforeRuntime() {
        String problem = ShyneExpressionEngine.validate("dangerous.system(1)");
        assertFalse(problem.isBlank());
        assertTrue(problem.contains("Unknown function"));
    }
}

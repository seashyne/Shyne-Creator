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

    @Test
    void evaluatesFiguraProviderReturnWithoutExecutingLua() {
        AnimationExpressionContext context = new AnimationExpressionContext(
            0.5, 0, 0, 0, false, false, Map.of("pitch", 4.0, "strength", 2.0, "normal", 1.0)
        );

        double value = ShyneExpressionEngine.evaluate(
            "local t = require(\"scripts.Anims\") return\n0+t.pitch+(math.sin(t.time*180)*t.strength*t.normal)",
            context,
            0
        );

        assertEquals(6.0, value, 0.0001);
    }

    @Test
    void evaluatesSharkTailMolangExpressionsWithoutMolangLua() {
        AnimationExpressionContext context = new AnimationExpressionContext(
            0.5, 0, 0, 0, false, false, Map.of()
        );

        double rootX = ShyneExpressionEngine.evaluate(
            "Math.cos(q.anim_time*360/1)*0.5-30", context, 0
        );
        double segmentY = ShyneExpressionEngine.evaluate(
            "Math.sin((q.anim_time-0.1)*360/2)*15", context, 0
        );

        assertEquals(-30.5, rootX, 0.0001);
        assertEquals(Math.sin(Math.toRadians(72)) * 15, segmentY, 0.0001);
    }
}

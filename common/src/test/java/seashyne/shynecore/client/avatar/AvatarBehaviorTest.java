package seashyne.shynecore.client.avatar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import seashyne.shynecore.model.BbAnimationDefinition;
import seashyne.shynecore.model.BbModelDefinition;

final class AvatarBehaviorTest {
    @Test
    void parsesDeclarativeSlotsAutoplayAndBlink() {
        AvatarBehavior behavior = AvatarBehavior.parse(JsonParser.parseString("""
            {
              "preset": "auto",
              "autoplay": ["Ear Wiggle"],
              "animations": {"idle": "Idle", "walk": ["Walk", "Move"]},
              "blend_ticks": 7,
              "blink": {"animation": "Blink", "min_ticks": 3, "max_ticks": 5}
            }
            """));

        assertEquals(List.of("Idle"), behavior.candidates("idle"));
        assertEquals(List.of("Walk", "Move"), behavior.candidates("WALK"));
        assertEquals(List.of("Ear Wiggle"), behavior.autoplay());
        assertEquals(7, behavior.blendTicks());
        assertEquals(3, behavior.blink().minTicks());
        assertTrue(behavior.automatic());
        assertFalse(AvatarBehavior.parse(JsonParser.parseString("\"manual\"")).automatic());
    }

    @Test
    void controllerSelectsPlayerStateAndLayersAmbientAndBlink() {
        AvatarBehavior behavior = new AvatarBehavior(
            "auto", List.of("Ear Wiggle"), Map.of("idle", List.of("Idle"), "walk", List.of("Walk")), 5,
            new AvatarBehavior.Blink(true, List.of("Blink"), 1, 1)
        );
        AvatarAutoAnimationController controller = new AvatarAutoAnimationController(model(), behavior, 1L);

        AvatarAutoAnimationController.Update idle = controller.tick(AvatarAutoAnimationController.Signals.IDLE);
        assertTrue(idle.plays().stream().anyMatch(play -> play.animation().equals("Ear Wiggle") && play.additive()));
        assertTrue(idle.plays().stream().anyMatch(play -> play.animation().equals("Idle") && play.priority() == 0));
        assertTrue(idle.plays().stream().anyMatch(play -> play.animation().equals("Blink") && !play.loop()));

        var moving = new AvatarAutoAnimationController.Signals(true, false, false, false, false, false, false, false);
        AvatarAutoAnimationController.Update walk = controller.tick(moving);
        assertTrue(walk.plays().stream().anyMatch(play -> play.animation().equals("Walk") && play.transitionTicks() == 5));
        assertEquals("Walk", controller.activeStateAnimation());
    }

    @Test
    void profilesHaveSafeModelFirstDefaults() {
        assertFalse(AvatarProfile.parse(null).replaceVanilla());
        assertTrue(AvatarProfile.parse("full-body").replaceVanilla());
        assertEquals("custom", AvatarProfile.parse("custom").id());
        assertFalse(AvatarProfile.parse("custom").replaceVanilla());
        assertEquals(AvatarProfile.CUSTOM, AvatarProfile.parse("merling"));
    }

    @Test
    void rejectsUnknownPresetAndAnimationState() {
        assertThrows(IllegalArgumentException.class, () -> AvatarBehavior.parse(JsonParser.parseString("\"automatic\"")));
        assertThrows(IllegalArgumentException.class, () -> AvatarBehavior.parse(JsonParser.parseString("""
            {"animations":{"jump":"Jump"}}
            """)));
    }

    @Test
    void oneShotLayersDoNotReplacePersistentCurrentAnimation() {
        AvatarState state = new AvatarState("test", "test:model", Path.of("."), false, Set.of(), Set.of());
        long now = 10_000L;
        state.animationLayers().put("idle", new AvatarAnimationLayer(
            "Idle", now - 100L, 1.0, true, 1.0, 1.0, 0, 0, 0, List.of(), false, 0L
        ));
        state.animationLayers().put("blink", new AvatarAnimationLayer(
            "Blink", now, 0.2, false, 1.0, 1.0, 50, 0, 0, List.of(), false, 0L
        ));

        state.refreshCurrentAnimationFromLayers(now);

        assertEquals("Idle", state.currentAnimation());
        assertEquals(now - 100L, state.currentAnimationStartedAtMillis());
    }

    private static BbModelDefinition model() {
        List<BbAnimationDefinition> animations = List.of(
            animation("Idle", true), animation("Walk", true), animation("Ear Wiggle", true), animation("Blink", false)
        );
        return new BbModelDefinition("test", "test", "Test", Path.of("model.bbmodel"), 4, 16, 16, "", List.of(), List.of(), List.of(), animations);
    }

    private static BbAnimationDefinition animation(String name, boolean looping) {
        return new BbAnimationDefinition(name, 1.0, looping, 0, Map.of(), List.of());
    }
}

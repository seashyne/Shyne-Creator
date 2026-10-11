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
    void controllerFallsBackToIdleWhenMovingInWaterWithoutDedicatedSwimAnimation() {
        BbModelDefinition modelOnlyIdle = new BbModelDefinition(
            "tail", "tail", "Tail", Path.of("model.bbmodel"), 4, 16, 16, "", List.of(), List.of(), List.of(),
            List.of(animation("Idle", true))
        );
        AvatarAutoAnimationController controller = new AvatarAutoAnimationController(modelOnlyIdle, AvatarBehavior.AUTO, 1L);

        var inWaterMoving = new AvatarAutoAnimationController.Signals(true, false, false, false, true, false, false, false);
        AvatarAutoAnimationController.Update update = controller.tick(inWaterMoving);
        assertEquals("Idle", controller.activeStateAnimation());
        assertTrue(update.plays().stream().anyMatch(play -> play.animation().equals("Idle")));
    }

    @Test
    void controllerPlaysAutoplayEvenWhenNamedIdleWithoutConflict() {
        BbModelDefinition modelOnlyIdle = new BbModelDefinition(
            "tail", "tail", "Tail", Path.of("model.bbmodel"), 4, 16, 16, "", List.of(), List.of(), List.of(),
            List.of(animation("Idle", true))
        );
        AvatarBehavior behavior = new AvatarBehavior("auto", List.of("Idle"), Map.of(), 5, AvatarBehavior.Blink.auto());
        AvatarAutoAnimationController controller = new AvatarAutoAnimationController(modelOnlyIdle, behavior, 1L);

        AvatarAutoAnimationController.Update update = controller.tick(AvatarAutoAnimationController.Signals.IDLE);
        assertTrue(update.plays().stream().anyMatch(play -> play.animation().equals("Idle") && play.additive()));
        // Since "Idle" was claimed as ambient autoplay, state animation does not duplicate it
        assertEquals(null, controller.activeStateAnimation());

        var inWaterMoving = new AvatarAutoAnimationController.Signals(true, false, false, false, true, false, false, false);
        AvatarAutoAnimationController.Update waterUpdate = controller.tick(inWaterMoving);
        assertTrue(waterUpdate.stops().isEmpty());
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

    @Test
    void figuraSpecialBonesHiddenByDefault() {
        assertTrue(seashyne.shynecore.client.avatar.bridge.AvatarModelHelper.isSpecialFiguraHiddenByDefault("Skull"));
        assertTrue(seashyne.shynecore.client.avatar.bridge.AvatarModelHelper.isSpecialFiguraHiddenByDefault("skull"));
        assertTrue(seashyne.shynecore.client.avatar.bridge.AvatarModelHelper.isSpecialFiguraHiddenByDefault("Portrait"));
        assertTrue(seashyne.shynecore.client.avatar.bridge.AvatarModelHelper.isSpecialFiguraHiddenByDefault("portrait"));
        assertFalse(seashyne.shynecore.client.avatar.bridge.AvatarModelHelper.isSpecialFiguraHiddenByDefault("Head"));
        assertFalse(seashyne.shynecore.client.avatar.bridge.AvatarModelHelper.isSpecialFiguraHiddenByDefault("root"));
    }

    @Test
    void controllerAutoInfersAccessoryAnimationsAsAmbientAutoplay() {
        BbModelDefinition model = new BbModelDefinition(
            "seashyne", "seashyne", "SeaShyne", Path.of("model.bbmodel"), 4, 16, 16, "", List.of(), List.of(), List.of(),
            List.of(animationWithBones("Idle", true, "Bone_F", "Bone_B", "Fin_L", "Fin_R"))
        );
        AvatarAutoAnimationController controller = new AvatarAutoAnimationController(model, AvatarBehavior.AUTO, 1L);

        AvatarAutoAnimationController.Update update = controller.tick(AvatarAutoAnimationController.Signals.IDLE);
        assertTrue(update.plays().stream().anyMatch(play -> play.animation().equals("Idle") && play.additive()));
        assertEquals(null, controller.activeStateAnimation());

        var inWaterMoving = new AvatarAutoAnimationController.Signals(true, false, false, false, true, false, false, false);
        AvatarAutoAnimationController.Update waterUpdate = controller.tick(inWaterMoving);
        assertTrue(waterUpdate.stops().isEmpty());
    }

    @Test
    void controllerLeavesHumanoidLimbAnimationsInStateSlots() {
        BbModelDefinition model = new BbModelDefinition(
            "humanoid", "humanoid", "Humanoid", Path.of("model.bbmodel"), 4, 16, 16, "", List.of(), List.of(), List.of(),
            List.of(
                animationWithBones("Idle", true, "Body", "LeftArm"),
                animationWithBones("Walk", true, "Body", "LeftLeg", "RightLeg"),
                animationWithBones("Ear Wiggle", true, "Ear_L", "Ear_R")
            )
        );
        AvatarAutoAnimationController controller = new AvatarAutoAnimationController(model, AvatarBehavior.AUTO, 1L);

        AvatarAutoAnimationController.Update idle = controller.tick(AvatarAutoAnimationController.Signals.IDLE);
        assertTrue(idle.plays().stream().anyMatch(play -> play.animation().equals("Ear Wiggle") && play.additive()));
        assertTrue(idle.plays().stream().anyMatch(play -> play.animation().equals("Idle") && !play.additive()));
        assertEquals("Idle", controller.activeStateAnimation());

        var moving = new AvatarAutoAnimationController.Signals(true, false, false, false, false, false, false, false);
        AvatarAutoAnimationController.Update walk = controller.tick(moving);
        assertEquals("Walk", controller.activeStateAnimation());
        assertTrue(walk.plays().stream().anyMatch(play -> play.animation().equals("Walk")));
        assertFalse(walk.stops().contains("Ear Wiggle"));
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

    private static BbAnimationDefinition animationWithBones(String name, boolean looping, String... bones) {
        Map<String, seashyne.shynecore.model.BbBoneAnimation> anims = new java.util.LinkedHashMap<>();
        for (String b : bones) {
            anims.put(b, new seashyne.shynecore.model.BbBoneAnimation(b, List.of(), List.of(), List.of(), false, false));
        }
        return new BbAnimationDefinition(name, 1.0, looping, anims.size(), Map.copyOf(anims), List.of(bones));
    }
}

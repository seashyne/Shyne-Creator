package seashyne.shynecore.client.avatar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import seashyne.shynecore.model.BbAnimationDefinition;
import seashyne.shynecore.model.BbModelDefinition;

/**
 * Loader-independent state selector for Standard 2.0 model-first avatars.
 * Platform runtimes translate the returned commands into their animation layer API.
 */
public final class AvatarAutoAnimationController {
    private static final Map<String, List<String>> DEFAULT_SLOTS = Map.ofEntries(
        Map.entry("idle", List.of("Idle", "Stand", "Standing")),
        Map.entry("walk", List.of("Walk", "Move")),
        Map.entry("sprint", List.of("Sprint", "Run", "Walk")),
        Map.entry("swim", List.of("Swim", "Swimming", "Mermaid Swim", "mermaid_swim")),
        Map.entry("crouch", List.of("Crouch", "Crouching", "Crawl")),
        Map.entry("sleep", List.of("Sleep", "Sleeping", "Sleepy")),
        Map.entry("fly", List.of("Elytra", "Fly", "Fall Flying", "fall_flying")),
        Map.entry("sit", List.of("Sit", "Mount", "SitAnim", "sitanim"))
    );

    private static final Set<String> HUMANOID_LIMBS = Set.of(
        "head", "body", "torso", "chest", "waist",
        "leftarm", "rightarm", "leftleg", "rightleg"
    );

    private static final java.util.regex.Pattern ACCESSORY_PATTERN = java.util.regex.Pattern.compile(
        ".*(tail|ear|wing|fin|halo|horn|wiggle|wag|ambient|breath|float|spin|sparkle).*",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private final AvatarBehavior behavior;
    private final Map<String, String> animationNames;
    private final Map<String, String> slots;
    private final List<String> autoplay;
    private final String blinkAnimation;
    private final Random random;
    private boolean initialized;
    private String activeStateAnimation;
    private int blinkTicks;

    public AvatarAutoAnimationController(BbModelDefinition model, AvatarBehavior behavior, long seed) {
        this.behavior = behavior == null ? AvatarBehavior.AUTO : behavior;
        this.animationNames = indexAnimations(model);
        this.autoplay = resolveAll(resolveAutoplayList(model, this.behavior));
        this.slots = resolveSlots(this.behavior, this.autoplay);
        this.blinkAnimation = firstExisting(this.behavior.blink().animations());
        this.random = new Random(seed);
        this.blinkTicks = nextBlinkDelay();
    }

    public Update tick(Signals signals) {
        if (!behavior.automatic()) return Update.NONE;
        List<Play> plays = new ArrayList<>();
        List<String> stops = new ArrayList<>();
        if (!initialized) {
            initialized = true;
            for (String animation : autoplay) {
                plays.add(Play.ambient(animation));
            }
        }

        String desired = select(signals == null ? Signals.IDLE : signals);
        if (!same(activeStateAnimation, desired)) {
            if (activeStateAnimation != null && (desired == null || behavior.blendTicks() == 0)) stops.add(activeStateAnimation);
            activeStateAnimation = desired;
            if (desired != null) plays.add(Play.state(desired, behavior.blendTicks()));
        }

        AvatarBehavior.Blink blink = behavior.blink();
        if (blink.enabled() && blinkAnimation != null && !(signals != null && signals.sleeping())) {
            blinkTicks--;
            if (blinkTicks <= 0) {
                plays.add(Play.blink(blinkAnimation));
                blinkTicks = nextBlinkDelay();
            }
        }
        return plays.isEmpty() && stops.isEmpty() ? Update.NONE : new Update(List.copyOf(plays), List.copyOf(stops));
    }

    public String activeStateAnimation() { return activeStateAnimation; }

    private String select(Signals signals) {
        if (signals.sleeping()) return fallback("sleep", "idle");
        if (signals.fallFlying()) return fallback("fly", "idle");
        if (signals.mounted()) return fallback("sit", "idle");
        if (signals.swimming() || signals.inWater()) return fallback("swim", signals.moving() ? "walk" : "idle", "idle");
        if (signals.crouching()) return fallback("crouch", signals.moving() ? "walk" : "idle", "idle");
        if (signals.moving() && signals.sprinting()) return fallback("sprint", "walk", "idle");
        if (signals.moving()) return fallback("walk", "idle");
        return slots.get("idle");
    }

    private String fallback(String... states) {
        for (String state : states) {
            String animation = slots.get(state);
            if (animation != null) return animation;
        }
        return null;
    }

    private Map<String, String> resolveSlots(AvatarBehavior definition, List<String> resolvedAutoplay) {
        Set<String> autoplaySet = new LinkedHashSet<>();
        if (resolvedAutoplay != null) {
            for (String name : resolvedAutoplay) {
                if (name != null) autoplaySet.add(normalize(name));
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        DEFAULT_SLOTS.forEach((state, defaults) -> {
            List<String> configured = definition.candidates(state);
            String selected = firstExisting(configured.isEmpty() ? defaults : configured);
            if (selected != null && !autoplaySet.contains(normalize(selected))) {
                result.put(state, selected);
            }
        });
        definition.animations().forEach((state, candidates) -> {
            String selected = firstExisting(candidates);
            if (selected != null && !autoplaySet.contains(normalize(selected))) {
                result.put(state.toLowerCase(Locale.ROOT), selected);
            }
        });
        return Map.copyOf(result);
    }

    private List<String> resolveAutoplayList(BbModelDefinition model, AvatarBehavior definition) {
        List<String> result = new ArrayList<>(definition.autoplay());
        if (model == null || model.animations() == null || !definition.automatic()) {
            return List.copyOf(result);
        }

        Set<String> explicitlyConfigured = new LinkedHashSet<>();
        definition.autoplay().forEach(name -> explicitlyConfigured.add(normalize(name)));
        definition.animations().values().forEach(candidates -> {
            for (String c : candidates) explicitlyConfigured.add(normalize(c));
        });
        if (definition.blink().enabled()) {
            for (String b : definition.blink().animations()) explicitlyConfigured.add(normalize(b));
        }

        Map<String, String> boneMap = new LinkedHashMap<>();
        if (model.bones() != null) {
            for (var bone : model.bones()) {
                if (bone != null && bone.uuid() != null && bone.name() != null) {
                    boneMap.put(bone.uuid(), bone.name());
                }
            }
        }

        boolean hasExplicitWalk = model.animations().stream().anyMatch(a -> {
            if (a == null || a.name() == null) return false;
            String n = normalize(a.name());
            return n.equals("walk") || n.equals("move") || n.equals("run") || n.equals("sprint");
        });

        for (BbAnimationDefinition anim : model.animations()) {
            if (anim == null || anim.name() == null) continue;
            String normAnimName = normalize(anim.name());
            if (explicitlyConfigured.contains(normAnimName)) continue;

            if (isAccessoryAnimation(anim, boneMap, hasExplicitWalk, model.animations().size())) {
                if (!result.contains(anim.name())) {
                    result.add(anim.name());
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean isAccessoryAnimation(
        BbAnimationDefinition anim,
        Map<String, String> boneMap,
        boolean hasExplicitWalk,
        int totalAnimationsCount
    ) {
        String animName = anim.name();
        if (animName == null || animName.isBlank()) return false;
        String normAnim = normalize(animName);

        Set<String> boneKeys = new LinkedHashSet<>();
        if (anim.boneAnimations() != null) boneKeys.addAll(anim.boneAnimations().keySet());
        if (anim.affectedBones() != null) boneKeys.addAll(anim.affectedBones());

        boolean hasHumanoidLimb = false;
        boolean hasAccessoryBoneMatch = false;

        for (String boneKey : boneKeys) {
            String boneName = boneMap.getOrDefault(boneKey, boneKey);
            String normBone = normalize(boneName);
            if (HUMANOID_LIMBS.contains(normBone)) {
                hasHumanoidLimb = true;
                break;
            }
            if (ACCESSORY_PATTERN.matcher(boneName).matches()) {
                hasAccessoryBoneMatch = true;
            }
        }

        if (hasHumanoidLimb) {
            return false;
        }

        boolean animMatchesAccessory = ACCESSORY_PATTERN.matcher(animName).matches();

        if (animMatchesAccessory || hasAccessoryBoneMatch) {
            if ((normAnim.equals("idle") || normAnim.equals("stand") || normAnim.equals("standing")) && hasExplicitWalk) {
                return false;
            }
            return true;
        }

        if (totalAnimationsCount == 1 && !boneKeys.isEmpty()) {
            return true;
        }

        if (!boneKeys.isEmpty() && !DEFAULT_SLOTS.containsKey(normAnim) && !normAnim.equals("move") && !normAnim.equals("run")) {
            return true;
        }

        return false;
    }

    private List<String> resolveAll(List<String> names) {
        List<String> result = new ArrayList<>();
        for (String name : names) {
            String resolved = firstExisting(List.of(name));
            if (resolved != null && !result.contains(resolved)) result.add(resolved);
        }
        return List.copyOf(result);
    }

    private String firstExisting(List<String> candidates) {
        for (String candidate : candidates) {
            String exact = animationNames.get(normalize(candidate));
            if (exact != null) return exact;
        }
        return null;
    }

    private int nextBlinkDelay() {
        AvatarBehavior.Blink blink = behavior.blink();
        int span = Math.max(0, blink.maxTicks() - blink.minTicks());
        return blink.minTicks() + (span == 0 ? 0 : random.nextInt(span + 1));
    }

    private static Map<String, String> indexAnimations(BbModelDefinition model) {
        Map<String, String> result = new LinkedHashMap<>();
        if (model != null && model.animations() != null) {
            for (BbAnimationDefinition animation : model.animations()) {
                if (animation != null && animation.name() != null) result.putIfAbsent(normalize(animation.name()), animation.name());
            }
        }
        return Map.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : right != null && left.equalsIgnoreCase(right);
    }

    public record Signals(
        boolean moving,
        boolean sprinting,
        boolean crouching,
        boolean swimming,
        boolean inWater,
        boolean sleeping,
        boolean fallFlying,
        boolean mounted
    ) {
        public static final Signals IDLE = new Signals(false, false, false, false, false, false, false, false);
    }

    public record Play(
        String animation,
        boolean loop,
        int priority,
        int fadeInTicks,
        int fadeOutTicks,
        int transitionTicks,
        boolean additive
    ) {
        static Play state(String name, int blend) { return new Play(name, true, 0, blend, blend, blend, false); }
        static Play ambient(String name) { return new Play(name, true, -20, 5, 5, 0, true); }
        static Play blink(String name) { return new Play(name, false, 50, 1, 2, 1, false); }
    }

    public record Update(List<Play> plays, List<String> stops) {
        public static final Update NONE = new Update(List.of(), List.of());
    }
}

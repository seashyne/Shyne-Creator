package seashyne.shynecore.power;

import net.minecraft.world.entity.Entity;
import seashyne.shynecore.animation.AnimationRuntime;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Tracks combo/form state. Mana and cooldown authority lives in CombatStatManager. */
public class PowerStateMachine {
    public interface Listener { void onPowerStatesSync(Collection<PowerState> states); }

    private final Map<UUID, PowerState> states = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AnimationRuntime animationRuntime;
    private final CombatStatManager combatStats;

    public PowerStateMachine(AnimationRuntime animationRuntime, CombatStatManager combatStats) {
        this.animationRuntime = animationRuntime;
        this.combatStats = combatStats;
    }

    public void addListener(Listener listener) { listeners.add(listener); }
    public Optional<PowerState> get(UUID entityId) { return Optional.ofNullable(states.get(entityId)); }
    public Collection<PowerState> all() { return List.copyOf(states.values()); }

    public PowerState advance(Entity entity, String comboId, String branch, int maxStage, int resetTicks,
                              String modelId, String animationPrefix) {
        UUID id = entity.getUUID();
        combatStats.ensure(id);
        String normalizedBranch = branch == null || branch.isBlank() ? "light" : branch;
        int nextStage = nextStage(id, comboId, normalizedBranch, Math.max(1, maxStage));
        String animation = animationPrefix + normalizedBranch + "_" + nextStage;
        return record(entity, comboId, normalizedBranch, nextStage, resetTicks, modelId, animation);
    }

    public PowerState recordSkillUse(Entity entity, String skillId, String branch, int maxStage, int comboWindowTicks,
                                     String modelId, String animation) {
        String normalizedBranch = branch == null || branch.isBlank() ? "primary" : branch;
        int nextStage = nextStage(entity.getUUID(), skillId, normalizedBranch, Math.max(1, maxStage));
        return record(entity, skillId, normalizedBranch, nextStage, Math.max(1, comboWindowTicks), modelId, animation);
    }

    private PowerState record(Entity entity, String comboId, String branch, int stage, int resetTicks,
                              String modelId, String animation) {
        UUID id = entity.getUUID();
        long now = System.currentTimeMillis();
        PowerState previous = states.get(id);
        if (modelId != null && !modelId.isBlank() && animation != null && !animation.isBlank()) {
            animationRuntime.play(entity, modelId, animation);
        }
        PowerState next = new PowerState(id, comboId, stage, branch,
            previous == null || previous.stage() == 0 ? now : previous.startedAtMillis(), now,
            now + Math.max(1, resetTicks) * 50L, combatStats.getLatestCooldownExpiry(id),
            combatStats.getMana(id), combatStats.getMaxMana(id), animation == null ? "" : animation, false);
        states.put(id, next);
        notifyListeners();
        return next;
    }

    private int nextStage(UUID id, String comboId, String branch, int maxStage) {
        long now = System.currentTimeMillis();
        PowerState previous = states.get(id);
        if (previous != null && !previous.isExpired(now) && Objects.equals(previous.comboId(), comboId)
            && Objects.equals(previous.branch(), branch)) {
            return Math.min(maxStage, previous.stage() + 1);
        }
        return 1;
    }

    public void setMana(UUID entityId, double mana, double maxMana) {
        combatStats.setMaxMana(entityId, maxMana);
        combatStats.setMana(entityId, mana);
        syncResources(entityId);
    }

    public void syncResources(UUID entityId) {
        combatStats.ensure(entityId);
        long now = System.currentTimeMillis();
        PowerState previous = states.get(entityId);
        states.put(entityId, new PowerState(entityId,
            previous == null ? "" : previous.comboId(), previous == null ? 0 : previous.stage(),
            previous == null ? "" : previous.branch(), previous == null ? now : previous.startedAtMillis(), now,
            previous == null ? 0 : previous.resetAtMillis(), combatStats.getLatestCooldownExpiry(entityId),
            combatStats.getMana(entityId), combatStats.getMaxMana(entityId),
            previous == null ? "" : previous.currentAnimation(), previous != null && previous.locked()));
        notifyListeners();
    }

    public void reset(UUID entityId) {
        PowerState previous = states.get(entityId);
        if (previous == null) return;
        long now = System.currentTimeMillis();
        states.put(entityId, new PowerState(entityId, "", 0, "", now, now, 0,
            combatStats.getLatestCooldownExpiry(entityId), combatStats.getMana(entityId), combatStats.getMaxMana(entityId), "", false));
        notifyListeners();
    }

    public void pruneMissing(Set<UUID> live) {
        boolean changed = states.entrySet().removeIf(e -> !live.contains(e.getKey()));
        combatStats.pruneMissing(live);
        if (changed) notifyListeners();
    }

    public void tick() {
        combatStats.tick();
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (var entry : states.entrySet()) {
            PowerState state = entry.getValue();
            boolean reset = state.isExpired(now) && state.stage() > 0;
            double mana = combatStats.getMana(entry.getKey());
            double maxMana = combatStats.getMaxMana(entry.getKey());
            long cooldown = combatStats.getLatestCooldownExpiry(entry.getKey());
            if (!reset && Math.abs(mana - state.mana()) < 0.0001 && maxMana == state.maxMana()
                && cooldown == state.cooldownEndsAtMillis()) continue;
            states.put(entry.getKey(), new PowerState(state.entityId(), reset ? "" : state.comboId(), reset ? 0 : state.stage(),
                reset ? "" : state.branch(), state.startedAtMillis(), now, reset ? 0 : state.resetAtMillis(), cooldown,
                mana, maxMana, reset ? "" : state.currentAnimation(), state.locked()));
            changed = true;
        }
        if (changed) notifyListeners();
    }

    private void notifyListeners() {
        Collection<PowerState> snapshot = all();
        for (Listener listener : listeners) listener.onPowerStatesSync(snapshot);
    }
}

package seashyne.shynecore.power;

import net.minecraft.world.entity.Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CombatStatManager {
    public enum UseResult { SUCCESS, NOT_ENOUGH_MANA, ON_COOLDOWN }

    private final Map<UUID, Double> mana = new ConcurrentHashMap<>();
    private final Map<UUID, Double> maxMana = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private static final double DEFAULT_MAX_MANA = 100.0;
    private static final double REGEN_PER_TICK = 0.15;

    public void ensure(UUID id) {
        maxMana.putIfAbsent(id, DEFAULT_MAX_MANA);
        mana.putIfAbsent(id, maxMana.get(id));
        cooldowns.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
    }

    public void ensure(Entity entity) { ensure(entity.getUUID()); }
    public double getMana(UUID id) { ensure(id); return mana.getOrDefault(id, DEFAULT_MAX_MANA); }
    public double getMaxMana(UUID id) { ensure(id); return maxMana.getOrDefault(id, DEFAULT_MAX_MANA); }
    public void setMaxMana(UUID id, double value) { maxMana.put(id, Math.max(1.0, value)); mana.put(id, Math.min(getMana(id), getMaxMana(id))); }
    public void setMana(UUID id, double value) { ensure(id); mana.put(id, Math.max(0.0, Math.min(getMaxMana(id), value))); }

    public UseResult tryUse(UUID id, String cooldownKey, double manaCost, int cooldownTicks) {
        ensure(id);
        if (isOnCooldown(id, cooldownKey)) return UseResult.ON_COOLDOWN;
        if (getMana(id) + 1e-6 < Math.max(0.0, manaCost)) return UseResult.NOT_ENOUGH_MANA;
        mana.put(id, Math.max(0.0, getMana(id) - Math.max(0.0, manaCost)));
        if (cooldownTicks > 0) cooldowns.get(id).put(cooldownKey, System.currentTimeMillis() + cooldownTicks * 50L);
        return UseResult.SUCCESS;
    }

    private boolean isOnCooldown(UUID id, String key) {
        ensure(id);
        Long expires = cooldowns.get(id).get(key);
        return expires != null && expires > System.currentTimeMillis();
    }

    public long getCooldownRemaining(UUID id, String key) {
        ensure(id);
        Long expires = cooldowns.get(id).get(key);
        return expires == null ? 0L : Math.max(0L, expires - System.currentTimeMillis());
    }

    public long getLatestCooldownExpiry(UUID id) {
        ensure(id);
        long now = System.currentTimeMillis();
        return cooldowns.get(id).values().stream().filter(expires -> expires > now).mapToLong(Long::longValue).max().orElse(0L);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : mana.keySet()) {
            double cur = mana.getOrDefault(id, DEFAULT_MAX_MANA);
            double max = getMaxMana(id);
            if (cur < max) {
                mana.put(id, Math.min(max, cur + REGEN_PER_TICK));
            }
            Map<String, Long> cds = cooldowns.get(id);
            if (cds != null) cds.entrySet().removeIf(e -> e.getValue() <= now);
        }
    }

    public void pruneMissing(Set<UUID> live) {
        mana.keySet().removeIf(id -> !live.contains(id));
        maxMana.keySet().removeIf(id -> !live.contains(id));
        cooldowns.keySet().removeIf(id -> !live.contains(id));
    }

}

package seashyne.shynecore.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import seashyne.shynecore.diagnostics.ContentDiagnostics;
import seashyne.shynecore.skill.SkillDefinition;
import seashyne.shynecore.skill.SkillRegistry;
import seashyne.shynecore.skill.SkillSlot;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns persistent player progression and validates it against the active skill registry.
 *
 * <p>Mutations replace immutable profile records and notify listeners so saving
 * and network synchronization observe the same state.</p>
 */
public class PlayerProfileRuntime {
    public interface Listener { void onProfileSync(Collection<PlayerProfile> profiles); }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PROFILE_LIST = new TypeToken<List<PlayerProfile>>() {}.getType();

    private final Path saveFile;
    private final SkillRegistry skillRegistry;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ContentDiagnostics diagnostics;

    public PlayerProfileRuntime(Path saveFile, SkillRegistry skillRegistry) {
        this(saveFile, skillRegistry, null);
    }

    public PlayerProfileRuntime(Path saveFile, SkillRegistry skillRegistry, ContentDiagnostics diagnostics) {
        this.saveFile = saveFile;
        this.skillRegistry = skillRegistry;
        this.diagnostics = diagnostics;
    }

    public void addListener(Listener listener) { listeners.add(listener); }
    public Collection<PlayerProfile> all() { return List.copyOf(profiles.values()); }
    public Optional<PlayerProfile> get(UUID playerId) { return Optional.ofNullable(profiles.get(playerId)); }

    public PlayerProfile getOrCreate(ServerPlayer player) {
        return profiles.computeIfAbsent(player.getUUID(), id -> defaultProfile(player));
    }

    public void ensurePlayer(ServerPlayer player) {
        profiles.putIfAbsent(player.getUUID(), defaultProfile(player));
        notifyListeners();
    }

    public void addExperience(ServerPlayer player, long xp) {
        PlayerProfile current = getOrCreate(player);
        long totalXp = current.experience() + Math.max(0, xp);
        int level = current.level();
        int skillPoints = current.skillPoints();
        int statPoints = current.statPoints();
        while (totalXp >= xpForLevel(level + 1)) {
            level++;
            skillPoints += 1;
            statPoints += 2;
        }
        profiles.put(player.getUUID(), new PlayerProfile(player.getUUID(), player.getName().getString(), level, totalXp, statPoints, skillPoints, current.playerClass(), current.unlockedSkills(), current.equippedSkills(), current.attributes(), current.teamId(), System.currentTimeMillis()));
        notifyListeners();
    }

    public boolean unlockSkill(ServerPlayer player, String skillId) {
        SkillDefinition definition = skillRegistry.get(skillId);
        if (definition == null) return false;
        PlayerProfile current = getOrCreate(player);
        if (current.unlockedSkills().contains(skillId)) return true;
        if (current.skillPoints() <= 0) return false;
        if (definition.requirement() != null) {
            if (current.level() < definition.requirement().minLevel()) return false;
            for (String required : definition.requirement().requiredSkills()) if (!current.unlockedSkills().contains(required)) return false;
        }
        List<String> unlocked = new ArrayList<>(current.unlockedSkills());
        unlocked.add(skillId);
        profiles.put(player.getUUID(), new PlayerProfile(player.getUUID(), player.getName().getString(), current.level(), current.experience(), current.statPoints(), Math.max(0, current.skillPoints() - 1), current.playerClass(), List.copyOf(unlocked), current.equippedSkills(), current.attributes(), current.teamId(), System.currentTimeMillis()));
        notifyListeners();
        return true;
    }

    public boolean equipSkill(ServerPlayer player, SkillSlot slot, String skillId) {
        if (slot == null || skillRegistry.get(skillId) == null) return false;
        PlayerProfile current = getOrCreate(player);
        if (!current.unlockedSkills().contains(skillId)) return false;
        Map<String, String> equipped = new HashMap<>(current.equippedSkills());
        equipped.put(slot.name().toLowerCase(), skillId);
        profiles.put(player.getUUID(), new PlayerProfile(player.getUUID(), player.getName().getString(), current.level(), current.experience(), current.statPoints(), current.skillPoints(), current.playerClass(), current.unlockedSkills(), Map.copyOf(equipped), current.attributes(), current.teamId(), System.currentTimeMillis()));
        notifyListeners();
        return true;
    }

    public String equippedSkill(UUID playerId, SkillSlot slot) {
        PlayerProfile current = profiles.get(playerId);
        return current == null ? "" : current.equippedSkills().getOrDefault(slot.name().toLowerCase(), "");
    }

    public void load() throws IOException {
        if (diagnostics != null) diagnostics.clearSource("profile");
        if (!Files.exists(saveFile)) return;
        List<PlayerProfile> loaded;
        try {
            loaded = GSON.fromJson(Files.readString(saveFile), PROFILE_LIST);
        } catch (Exception e) {
            if (diagnostics != null) diagnostics.error("profile", "profiles", saveFile, "Could not parse profiles.json: " + e.getMessage(), "Fix the JSON format or delete the file to regenerate it.");
            throw new IOException("Could not parse profiles", e);
        }
        if (loaded == null) return;
        profiles.clear();
        for (PlayerProfile profile : loaded) profiles.put(profile.playerId(), profile);
        validateProfiles();
    }

    public void save() throws IOException {
        Files.createDirectories(saveFile.getParent());
        Files.writeString(saveFile, GSON.toJson(all()));
    }

    public int validateProfiles() {
        int changed = 0;
        if (diagnostics != null) diagnostics.clearSource("profile");
        Map<UUID, PlayerProfile> sanitized = new LinkedHashMap<>();
        for (PlayerProfile profile : all()) {
            PlayerProfile next = sanitizeProfile(profile);
            sanitized.put(next.playerId(), next);
            if (!next.equals(profile)) changed++;
        }
        profiles.clear();
        profiles.putAll(sanitized);
        notifyListeners();
        return changed;
    }

    private PlayerProfile defaultProfile(ServerPlayer player) {
        return new PlayerProfile(player.getUUID(), player.getName().getString(), 1, 0, 0, 1, "adventurer", List.of(), Map.of(), Map.of("vitality", 0, "focus", 0, "power", 0), "", System.currentTimeMillis());
    }

    private long xpForLevel(int level) {
        return Math.max(0, (long) level * level * 100L);
    }

    private void notifyListeners() {
        Collection<PlayerProfile> snapshot = all();
        for (Listener listener : listeners) listener.onProfileSync(snapshot);
    }

    private PlayerProfile sanitizeProfile(PlayerProfile profile) {
        String sourceId = profile.playerName() == null || profile.playerName().isBlank() ? profile.playerId().toString() : profile.playerName();
        int level = Math.max(1, profile.level());
        long experience = Math.max(0, profile.experience());
        int statPoints = Math.max(0, profile.statPoints());
        int skillPoints = Math.max(0, profile.skillPoints());

        LinkedHashSet<String> unlocked = new LinkedHashSet<>();
        List<String> unlockedInput = profile.unlockedSkills() == null ? List.of() : profile.unlockedSkills();
        for (String skillId : unlockedInput) {
            if (skillId == null || skillId.isBlank()) continue;
            SkillDefinition definition = skillRegistry.get(skillId);
            if (definition == null) {
                if (diagnostics != null) diagnostics.warn("profile", sourceId, saveFile, "Removed unknown unlocked skill '" + skillId + "'.", "Reload content or add the missing skill JSON back.");
                continue;
            }
            unlocked.add(skillId);
        }

        Map<String, String> equipped = new LinkedHashMap<>();
        Map<String, String> equippedInput = profile.equippedSkills() == null ? Map.of() : profile.equippedSkills();
        for (Map.Entry<String, String> entry : equippedInput.entrySet()) {
            String slotKey = entry.getKey();
            String skillId = entry.getValue();
            Optional<SkillSlot> slot = SkillSlot.tryParse(slotKey);
            if (slot.isEmpty()) {
                if (diagnostics != null) diagnostics.warn("profile", sourceId, saveFile, "Removed invalid equipped slot '" + slotKey + "'.", "Use primary, secondary, utility, ultimate, passive_1, or passive_2.");
                continue;
            }
            if (skillId == null || skillId.isBlank() || !unlocked.contains(skillId)) {
                if (diagnostics != null) diagnostics.warn("profile", sourceId, saveFile, "Removed equipped skill '" + skillId + "' from slot '" + slotKey + "'.", "Unlock the skill first, then equip it again.");
                continue;
            }
            equipped.put(slot.get().name().toLowerCase(Locale.ROOT), skillId);
        }

        return new PlayerProfile(
            profile.playerId(),
            profile.playerName(),
            level,
            experience,
            statPoints,
            skillPoints,
            profile.playerClass() == null || profile.playerClass().isBlank() ? "adventurer" : profile.playerClass(),
            List.copyOf(unlocked),
            Map.copyOf(equipped),
            profile.attributes() == null ? Map.of("vitality", 0, "focus", 0, "power", 0) : profile.attributes(),
            profile.teamId() == null ? "" : profile.teamId(),
            System.currentTimeMillis()
        );
    }
}

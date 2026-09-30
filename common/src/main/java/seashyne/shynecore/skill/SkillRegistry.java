package seashyne.shynecore.skill;

import com.google.gson.*;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.diagnostics.ContentDiagnostics;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Discovers declarative skill definitions from external {@code shyne-mods} packs.
 *
 * <p>Each scan rebuilds the registry from disk so removed or invalid definitions
 * cannot remain active from an older scan.</p>
 */
public class SkillRegistry {
    public interface Listener { void onSkillRegistrySync(Collection<SkillDefinition> skills); }

    private static final Gson GSON = new GsonBuilder().create();
    /** Retain only a bounded amount of icon source data on a dedicated server. */
    private static final long MAX_ICON_REGISTRY_BYTES = 4L * 1024L * 1024L;
    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();
    private final Map<String, SkillIconAsset> iconAssets = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ContentDiagnostics diagnostics;
    private long iconRegistryBytes;

    public SkillRegistry() {
        this(null);
    }

    public SkillRegistry(ContentDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public void addListener(Listener listener) { listeners.add(listener); }
    public Collection<SkillDefinition> all() { return List.copyOf(skills.values()); }
    public SkillDefinition get(String skillId) { return skills.get(skillId); }
    /** Returns the verified, package-local PNG attached to a skill, if one was declared. */
    public SkillIconAsset iconAsset(String skillId) { return iconAssets.get(skillId); }
    public boolean contains(String skillId) { return skills.containsKey(skillId); }
    public synchronized void clear() { skills.clear(); iconAssets.clear(); iconRegistryBytes = 0; notifyListeners(); }

    public void discover(Path shyneModsDir) {
        skills.clear();
        iconAssets.clear();
        iconRegistryBytes = 0;
        if (diagnostics != null) diagnostics.clearSource("skill");
        if (shyneModsDir == null || !Files.isDirectory(shyneModsDir)) {
            notifyListeners();
            return;
        }
        try (Stream<Path> paths = Files.walk(shyneModsDir)) {
            paths
                .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                .filter(p -> p.toString().replace('\\', '/').contains("/skills/"))
                .sorted()
                .forEach(path -> register(path, false));
        } catch (IOException e) {
            ShyneCore.LOGGER.error("[SkillRegistry] Failed to discover skills: {}", e.getMessage());
            if (diagnostics != null) {
                diagnostics.error("skill", "registry", shyneModsDir, "Failed to discover skill files: " + e.getMessage(), "Check that your shyne-mods folders are readable.");
            }
        }
        notifyListeners();
    }

    public Optional<SkillDefinition> register(Path path) {
        return register(path, true);
    }

    private Optional<SkillDefinition> register(Path path, boolean syncNow) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            SkillDefinition def = parse(root, path);
            if (def.skillId().isBlank()) {
                if (diagnostics != null) diagnostics.error("skill", inferId(path), path, "Skill definition has a blank skill_id.", "Set skill_id to a unique string like mage.arc_bolt.");
                return Optional.empty();
            }
            SkillIconAsset.Resolution icon = SkillIconAsset.resolve(path, def.icon());
            if (icon.hasAsset()) {
                if (!putIcon(def.skillId(), icon.asset()) && diagnostics != null) {
                    diagnostics.warn("skill", def.skillId(), path,
                        "Skill icon was ignored because the registry PNG budget is full.",
                        "Keep total icon PNG data under " + (MAX_ICON_REGISTRY_BYTES / (1024 * 1024)) + " MiB.");
                }
            } else {
                removeIcon(def.skillId());
                if (icon.hasError() && diagnostics != null) {
                    diagnostics.warn("skill", def.skillId(), path, "Skill icon was ignored: " + icon.error(),
                        "Declare a png_icon in shyne-package.json and point icon to its asset id.");
                }
            }
            SkillDefinition existing = skills.put(def.skillId(), def);
            if (existing != null && diagnostics != null) {
                diagnostics.warn("skill", def.skillId(), path, "Duplicate skill_id replaced an earlier definition.", "Rename one of the skills so each skill_id is unique.");
            }
            if (syncNow) notifyListeners();
            return Optional.of(def);
        } catch (Exception e) {
            ShyneCore.LOGGER.warn("[SkillRegistry] Could not load {}: {}", path, e.getMessage());
            if (diagnostics != null) diagnostics.error("skill", inferId(path), path, "Could not load skill JSON: " + e.getMessage(), "Validate the JSON syntax and required fields.");
            return Optional.empty();
        }
    }

    private SkillDefinition parse(JsonObject root, Path path) {
        String skillId = getString(root, "skill_id", inferId(path));
        if (skillId.isBlank() && diagnostics != null) {
            diagnostics.error("skill", inferId(path), path, "skill_id is missing.", "Add skill_id to the skill JSON file.");
        }
        JsonObject requirementObj = root.has("requirement") && root.get("requirement").isJsonObject() ? root.getAsJsonObject("requirement") : new JsonObject();
        String castTypeRaw = getString(root, "cast_type", "instant");
        SkillCastType castType = SkillCastType.tryParse(castTypeRaw).orElse(SkillCastType.INSTANT);
        if (diagnostics != null && SkillCastType.tryParse(castTypeRaw).isEmpty() && !castTypeRaw.isBlank()) {
            diagnostics.warn("skill", skillId, path, "Unknown cast_type '" + castTypeRaw + "' - defaulted to INSTANT.", "Use one of: instant, projectile, channel, summon, aura, utility.");
        }
        String defaultSlotRaw = getString(root, "default_slot", "primary");
        SkillSlot defaultSlot = SkillSlot.tryParse(defaultSlotRaw).orElse(SkillSlot.PRIMARY);
        if (diagnostics != null && SkillSlot.tryParse(defaultSlotRaw).isEmpty() && !defaultSlotRaw.isBlank()) {
            diagnostics.warn("skill", skillId, path, "Unknown default_slot '" + defaultSlotRaw + "' - defaulted to PRIMARY.", "Use one of: primary, secondary, utility, ultimate, passive_1, passive_2.");
        }
        double manaCost = Math.max(0.0, getDouble(root, "mana_cost", 0.0));
        if (diagnostics != null && manaCost != getDouble(root, "mana_cost", 0.0)) {
            diagnostics.warn("skill", skillId, path, "mana_cost was negative and was clamped to 0.", "Use a non-negative mana_cost.");
        }
        int cooldownTicks = Math.max(0, getInt(root, "cooldown_ticks", 0));
        int comboWindowTicks = Math.max(0, getInt(root, "combo_window_ticks", 20));
        SkillRequirement requirement = new SkillRequirement(
            Math.max(0, getInt(requirementObj, "min_level", 0)),
            getStrings(requirementObj, "required_skills"),
            getString(requirementObj, "required_weapon_tag", "")
        );
        Map<String, Object> payload = root.has("payload") && root.get("payload").isJsonObject()
            ? GSON.fromJson(root.getAsJsonObject("payload"), Map.class)
            : Map.of();
        return new SkillDefinition(
            skillId,
            getString(root, "display_name", skillId),
            getString(root, "description", ""),
            castType,
            defaultSlot,
            manaCost,
            cooldownTicks,
            comboWindowTicks,
            getString(root, "model_id", ""),
            getString(root, "animation", ""),
            getString(root, "icon", ""),
            requirement,
            getStrings(root, "tags"),
            payload
        );
    }

    private String inferId(Path path) {
        String filename = path.getFileName().toString();
        return filename.endsWith(".json") ? filename.substring(0, filename.length() - 5) : filename;
    }

    private void notifyListeners() {
        Collection<SkillDefinition> snapshot = all();
        for (Listener listener : listeners) listener.onSkillRegistrySync(snapshot);
    }

    private synchronized boolean putIcon(String skillId, SkillIconAsset next) {
        if (skillId == null || next == null) return false;
        SkillIconAsset previous = iconAssets.get(skillId);
        long withoutPrevious = iconRegistryBytes - (previous == null ? 0L : previous.byteSize());
        if (next.byteSize() > MAX_ICON_REGISTRY_BYTES - Math.max(0L, withoutPrevious)) {
            if (previous != null) {
                iconAssets.remove(skillId);
                iconRegistryBytes = Math.max(0L, withoutPrevious);
            }
            return false;
        }
        iconAssets.put(skillId, next);
        iconRegistryBytes = Math.max(0L, withoutPrevious) + next.byteSize();
        return true;
    }

    private synchronized void removeIcon(String skillId) {
        SkillIconAsset removed = iconAssets.remove(skillId);
        if (removed != null) iconRegistryBytes = Math.max(0L, iconRegistryBytes - removed.byteSize());
    }

    private static String getString(JsonObject obj, String key, String def) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        try { return obj != null && obj.has(key) ? obj.get(key).getAsInt() : def; } catch (Exception ignored) { return def; }
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        try { return obj != null && obj.has(key) ? obj.get(key).getAsDouble() : def; } catch (Exception ignored) { return def; }
    }

    private static List<String> getStrings(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement e : obj.getAsJsonArray(key)) values.add(e.getAsString());
        return values;
    }
}

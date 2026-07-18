package seashyne.shynecore.equipment;

import com.google.gson.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.diagnostics.ContentDiagnostics;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EquipmentRuntime {
    public interface Listener {
        void onWeaponRegistrySync(Collection<WeaponDefinition> weapons);
        void onLoadoutSync(Collection<EquipmentLoadout> loadouts);
    }

    private static final Gson GSON = new GsonBuilder().create();
    private final Map<String, WeaponDefinition> weapons = new ConcurrentHashMap<>();
    private final Map<UUID, EquipmentLoadout> loadouts = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ContentDiagnostics diagnostics;

    public EquipmentRuntime() {
        this(null);
    }

    public EquipmentRuntime(ContentDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public void addListener(Listener listener) { listeners.add(listener); }
    public Collection<WeaponDefinition> allWeapons() { return List.copyOf(weapons.values()); }
    public Collection<EquipmentLoadout> allLoadouts() { return List.copyOf(loadouts.values()); }
    public WeaponDefinition getWeapon(String weaponId) { return weapons.get(weaponId); }
    public Optional<EquipmentLoadout> getLoadout(UUID entityId) { return Optional.ofNullable(loadouts.get(entityId)); }
    public void clearWeapons() { weapons.clear(); notifyWeaponSync(); }

    public void discover(Path shyneModsDir) {
        weapons.clear();
        if (diagnostics != null) diagnostics.clearSource("weapon");
        if (shyneModsDir == null || !Files.isDirectory(shyneModsDir)) {
            notifyWeaponSync();
            return;
        }
        try {
            Files.walk(shyneModsDir)
                .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                .filter(p -> p.toString().replace('\\', '/').contains("/weapons/"))
                .sorted()
                .forEach(path -> registerWeapon(path, false));
        } catch (IOException e) {
            ShyneCore.LOGGER.error("[EquipmentRuntime] Failed to discover weapons: {}", e.getMessage());
            if (diagnostics != null) diagnostics.error("weapon", "registry", shyneModsDir, "Failed to discover weapon files: " + e.getMessage(), "Check that your shyne-mods folders are readable.");
        }
        notifyWeaponSync();
    }

    public Optional<WeaponDefinition> registerWeapon(Path path) {
        return registerWeapon(path, true);
    }

    private Optional<WeaponDefinition> registerWeapon(Path path, boolean syncNow) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String weaponId = getString(root, "weapon_id", path.getFileName().toString().replace(".json", ""));
            if (weaponId.isBlank()) {
                if (diagnostics != null) diagnostics.error("weapon", inferId(path), path, "weapon_id is missing.", "Add weapon_id to the weapon JSON file.");
                return Optional.empty();
            }
            String itemId = getString(root, "item_id", "minecraft:stick");
            Identifier parsedItemId = Identifier.tryParse(itemId);
            if (parsedItemId == null && diagnostics != null) {
                diagnostics.warn("weapon", weaponId, path, "Unknown item_id '" + itemId + "' - defaulted to minecraft:stick.", "Use a valid namespaced item id like minecraft:diamond_sword.");
                itemId = "minecraft:stick";
            }
            WeaponDefinition definition = new WeaponDefinition(
                weaponId,
                getString(root, "display_name", weaponId),
                itemId,
                getString(root, "model_id", ""),
                getString(root, "class_tag", ""),
                getStrings(root, "granted_skills"),
                root.has("stat_modifiers") && root.get("stat_modifiers").isJsonObject() ? GSON.fromJson(root.getAsJsonObject("stat_modifiers"), Map.class) : Map.of(),
                root.has("payload") && root.get("payload").isJsonObject() ? GSON.fromJson(root.getAsJsonObject("payload"), Map.class) : Map.of()
            );
            WeaponDefinition existing = weapons.put(weaponId, definition);
            if (existing != null && diagnostics != null) {
                diagnostics.warn("weapon", weaponId, path, "Duplicate weapon_id replaced an earlier definition.", "Rename one of the weapons so each weapon_id is unique.");
            }
            if (syncNow) notifyWeaponSync();
            return Optional.of(definition);
        } catch (Exception e) {
            ShyneCore.LOGGER.warn("[EquipmentRuntime] Could not load {}: {}", path, e.getMessage());
            if (diagnostics != null) diagnostics.error("weapon", inferId(path), path, "Could not load weapon JSON: " + e.getMessage(), "Validate the JSON syntax and required fields.");
            return Optional.empty();
        }
    }

    public boolean equipWeapon(ServerPlayer player, String slot, String weaponId) {
        if (player == null || weaponId == null || weaponId.isBlank() || getWeapon(weaponId) == null) return false;
        EquipmentLoadout current = loadouts.getOrDefault(player.getUUID(), new EquipmentLoadout(player.getUUID(), "", "", Map.of(), System.currentTimeMillis()));
        Map<String, String> slots = new HashMap<>(current.slots());
        String normalizedSlot = normalizeSlot(slot);
        slots.put(normalizedSlot, weaponId);
        String main = normalizedSlot.equals("mainhand") ? weaponId : current.mainHandWeaponId();
        String off = normalizedSlot.equals("offhand") ? weaponId : current.offHandWeaponId();
        loadouts.put(player.getUUID(), new EquipmentLoadout(player.getUUID(), main, off, Map.copyOf(slots), System.currentTimeMillis()));
        notifyLoadoutSync();
        return true;
    }

    private void notifyWeaponSync() {
        Collection<WeaponDefinition> snapshot = allWeapons();
        for (Listener listener : listeners) listener.onWeaponRegistrySync(snapshot);
    }

    private void notifyLoadoutSync() {
        Collection<EquipmentLoadout> snapshot = allLoadouts();
        for (Listener listener : listeners) listener.onLoadoutSync(snapshot);
    }

    private String inferId(Path path) {
        String filename = path.getFileName().toString();
        return filename.endsWith(".json") ? filename.substring(0, filename.length() - 5) : filename;
    }

    private String normalizeSlot(String slot) {
        if (slot == null || slot.isBlank()) return "mainhand";
        String normalized = slot.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mainhand", "main", "weapon", "primary" -> "mainhand";
            case "offhand", "off", "secondary" -> "offhand";
            default -> normalized;
        };
    }

    private static String getString(JsonObject obj, String key, String def) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    private static List<String> getStrings(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement e : obj.getAsJsonArray(key)) values.add(e.getAsString());
        return values;
    }
}

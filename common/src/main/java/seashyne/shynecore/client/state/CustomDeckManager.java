package seashyne.shynecore.client.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import seashyne.shynecore.ShyneCore;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side manager for dynamic, user-defined action decks.
 *
 * <p>Unlike the fixed {@code SkillSlot} enum, this system allows players to create
 * any number of custom action buttons, each with its own keybind and skill assignment.
 * Multiple deck presets can be saved and switched at runtime.</p>
 */
public final class CustomDeckManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SAVE_TYPE = new TypeToken<SavedDeckData>() {}.getType();

    private static final List<DeckPreset> decks = new CopyOnWriteArrayList<>();
    private static int activeDeckIndex = 0;
    private static boolean loaded = false;

    private CustomDeckManager() {}

    // ── Data Structures ──────────────────────────────────────────────────

    /** A single action slot within a deck. */
    public static final class ActionSlot {
        private String id;
        private String label;
        private String skillId;
        private int keyCode;
        private String keyName;

        public ActionSlot(String id, String label, String skillId, int keyCode, String keyName) {
            this.id = id;
            this.label = label;
            this.skillId = skillId;
            this.keyCode = keyCode;
            this.keyName = keyName;
        }

        public String id() { return id; }
        public String label() { return label; }
        public String skillId() { return skillId; }
        public int keyCode() { return keyCode; }
        public String keyName() { return keyName; }

        public void setLabel(String label) { this.label = label; }
        public void setSkillId(String skillId) { this.skillId = skillId; }
        public void setKeyCode(int keyCode) { this.keyCode = keyCode; }
        public void setKeyName(String keyName) { this.keyName = keyName; }
    }

    /** A named collection of action slots. */
    public static final class DeckPreset {
        private String name;
        private final List<ActionSlot> slots;

        public DeckPreset(String name) {
            this.name = name;
            this.slots = new ArrayList<>();
        }

        public String name() { return name; }
        public void setName(String name) { this.name = name; }
        public List<ActionSlot> slots() { return Collections.unmodifiableList(slots); }
    }

    /** Serialization wrapper. */
    private record SavedDeckData(int activeDeck, List<SavedDeck> decks) {}
    private record SavedDeck(String name, List<SavedSlot> slots) {}
    private record SavedSlot(String id, String label, String skillId, int keyCode, String keyName) {}

    // ── Lifecycle ────────────────────────────────────────────────────────

    /** Ensures config is loaded from disk. Safe to call multiple times. */
    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load();
        if (decks.isEmpty()) {
            decks.add(createDefaultDeck("Deck 1"));
        }
    }

    /** Force reload from disk. */
    public static void reload() {
        loaded = false;
        decks.clear();
        activeDeckIndex = 0;
        ensureLoaded();
    }

    // ── Deck Management ──────────────────────────────────────────────────

    public static List<DeckPreset> allDecks() {
        ensureLoaded();
        return Collections.unmodifiableList(decks);
    }

    public static int activeDeckIndex() {
        ensureLoaded();
        return activeDeckIndex;
    }

    public static DeckPreset activeDeck() {
        ensureLoaded();
        if (decks.isEmpty()) decks.add(createDefaultDeck("Deck 1"));
        if (activeDeckIndex < 0 || activeDeckIndex >= decks.size()) activeDeckIndex = 0;
        return decks.get(activeDeckIndex);
    }

    public static void setActiveDeck(int index) {
        ensureLoaded();
        if (index >= 0 && index < decks.size()) {
            activeDeckIndex = index;
            save();
        }
    }

    public static DeckPreset addDeck(String name) {
        ensureLoaded();
        String safeName = name == null || name.isBlank() ? "Deck " + (decks.size() + 1) : name.trim();
        DeckPreset preset = new DeckPreset(safeName);
        decks.add(preset);
        save();
        return preset;
    }

    public static boolean removeDeck(int index) {
        ensureLoaded();
        if (decks.size() <= 1 || index < 0 || index >= decks.size()) return false;
        decks.remove(index);
        if (index < activeDeckIndex) activeDeckIndex--;
        if (activeDeckIndex >= decks.size()) activeDeckIndex = decks.size() - 1;
        save();
        return true;
    }

    public static void renameDeck(int index, String newName) {
        ensureLoaded();
        if (index >= 0 && index < decks.size() && newName != null && !newName.isBlank()) {
            decks.get(index).setName(newName.trim());
            save();
        }
    }

    // ── Slot Management ──────────────────────────────────────────────────

    public static ActionSlot addSlot(int deckIndex) {
        ensureLoaded();
        if (deckIndex < 0 || deckIndex >= decks.size()) return null;
        DeckPreset deck = decks.get(deckIndex);
        // Keep the complete UUID: action decks are intentionally unbounded, so an abbreviated
        // 32-bit identifier would eventually make collisions likely for long-lived configs.
        String slotId = UUID.randomUUID().toString();
        ActionSlot slot = new ActionSlot(slotId, "Action " + (deck.slots.size() + 1), "",
            InputConstants.UNKNOWN.getValue(), "");
        deck.slots.add(slot);
        save();
        return slot;
    }

    public static boolean removeSlot(int deckIndex, String slotId) {
        ensureLoaded();
        if (deckIndex < 0 || deckIndex >= decks.size() || slotId == null) return false;
        boolean removed = decks.get(deckIndex).slots.removeIf(s -> s.id().equals(slotId));
        if (removed) save();
        return removed;
    }

    public static void assignSkill(int deckIndex, String slotId, String skillId) {
        ActionSlot slot = findSlot(deckIndex, slotId);
        if (slot != null) {
            slot.setSkillId(skillId == null ? "" : skillId);
            save();
        }
    }

    public static void bindKey(int deckIndex, String slotId, int keyCode, String keyName) {
        ActionSlot slot = findSlot(deckIndex, slotId);
        if (slot != null) {
            // A physical key should select one unambiguous action. Rebinding it moves the key
            // from any older slot in the same deck instead of making one press cast multiple skills.
            for (ActionSlot other : decks.get(deckIndex).slots) {
                if (other != slot && other.keyCode() == keyCode
                    && keyCode != InputConstants.UNKNOWN.getValue()) {
                    other.setKeyCode(InputConstants.UNKNOWN.getValue());
                    other.setKeyName("");
                }
            }
            slot.setKeyCode(keyCode);
            slot.setKeyName(keyName == null ? "" : keyName);
            save();
        }
    }

    public static void renameSlot(int deckIndex, String slotId, String label) {
        ActionSlot slot = findSlot(deckIndex, slotId);
        if (slot != null && label != null && !label.isBlank()) {
            slot.setLabel(label.trim());
            save();
        }
    }

    public static ActionSlot findSlot(int deckIndex, String slotId) {
        ensureLoaded();
        if (deckIndex < 0 || deckIndex >= decks.size() || slotId == null) return null;
        for (ActionSlot slot : decks.get(deckIndex).slots()) {
            if (slot.id().equals(slotId)) return slot;
        }
        return null;
    }

    /** Returns the active deck's slots for HUD and tick dispatch. */
    public static List<ActionSlot> activeSlots() {
        return activeDeck().slots();
    }

    /**
     * Returns the skill assigned to a key in the active deck, or an empty string when that key
     * is not an actionable binding. This lets loader adapters give a custom action precedence
     * over the old fixed-slot key mappings without exposing mutable deck internals.
     */
    public static String activeSkillBoundToKey(int keyCode) {
        if (keyCode == InputConstants.UNKNOWN.getValue()) return "";
        for (ActionSlot slot : activeSlots()) {
            if (slot.keyCode() == keyCode && !slot.skillId().isBlank()) return slot.skillId();
        }
        return "";
    }

    public static boolean hasActiveSkillBoundToKey(int keyCode) {
        return !activeSkillBoundToKey(keyCode).isBlank();
    }

    // ── Default Deck ─────────────────────────────────────────────────────

    private static DeckPreset createDefaultDeck(String name) {
        // A fresh install starts clean. Existing saved decks are never altered,
        // but new players should choose actions and keys that make sense to them.
        return new DeckPreset(name);
    }

    // ── Persistence ──────────────────────────────────────────────────────

    private static void load() {
        Path path = configPath();
        if (!Files.isRegularFile(path)) return;
        try {
            SavedDeckData data = GSON.fromJson(Files.readString(path), SAVE_TYPE);
            if (data == null || data.decks() == null) return;
            decks.clear();
            for (SavedDeck sd : data.decks()) {
                DeckPreset deck = new DeckPreset(sd.name());
                if (sd.slots() != null) {
                    for (SavedSlot ss : sd.slots()) {
                        deck.slots.add(new ActionSlot(ss.id(), ss.label(), ss.skillId(), ss.keyCode(), ss.keyName()));
                    }
                }
                decks.add(deck);
            }
            activeDeckIndex = Math.max(0, Math.min(data.activeDeck(), decks.size() - 1));
        } catch (Exception error) {
            ShyneCore.LOGGER.warn("[CustomDeck] Failed to load {}: {}", path, error.getMessage());
        }
    }

    static void save() {
        Path path = configPath();
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        List<SavedDeck> savedDecks = new ArrayList<>();
        for (DeckPreset deck : decks) {
            List<SavedSlot> savedSlots = new ArrayList<>();
            for (ActionSlot slot : deck.slots()) {
                savedSlots.add(new SavedSlot(slot.id(), slot.label(), slot.skillId(), slot.keyCode(), slot.keyName()));
            }
            savedDecks.add(new SavedDeck(deck.name(), savedSlots));
        }
        SavedDeckData data = new SavedDeckData(activeDeckIndex, savedDecks);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(tmp, GSON.toJson(data, SAVE_TYPE));
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            ShyneCore.LOGGER.warn("[CustomDeck] Failed to save {}: {}", path, error.getMessage());
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config").resolve("shyne-creator").resolve("custom-decks.json");
    }
}

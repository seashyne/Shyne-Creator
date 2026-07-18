package seashyne.shynecore.item;

import com.google.gson.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.diagnostics.ContentDiagnostics;
import seashyne.shynecore.equipment.EquipmentRuntime;
import seashyne.shynecore.loader.ShyneModLoader;
import seashyne.shynecore.skill.SkillExecutor;
import seashyne.shynecore.skill.SkillSlot;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ShyneItemRuntime {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final String DATA_ID = "shyne_item_id";

    private final Map<String, ShyneItemDefinition> definitions = new ConcurrentHashMap<>();
    private final SkillExecutor skillExecutor;
    private final EquipmentRuntime equipmentRuntime;
    private final ShyneModLoader modLoader;
    private final ContentDiagnostics diagnostics;

    public ShyneItemRuntime(SkillExecutor skillExecutor, EquipmentRuntime equipmentRuntime,
                            ShyneModLoader modLoader, ContentDiagnostics diagnostics) {
        this.skillExecutor = skillExecutor;
        this.equipmentRuntime = equipmentRuntime;
        this.modLoader = modLoader;
        this.diagnostics = diagnostics;
    }

    public Collection<ShyneItemDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public ShyneItemDefinition get(String itemId) {
        return itemId == null ? null : definitions.get(itemId.toLowerCase(Locale.ROOT));
    }

    public void discover(Path shyneModsDir) {
        definitions.clear();
        if (diagnostics != null) diagnostics.clearSource("item");
        if (shyneModsDir == null || !Files.isDirectory(shyneModsDir)) return;
        try (var paths = Files.walk(shyneModsDir)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .filter(path -> path.toString().replace('\\', '/').contains("/items/"))
                .sorted()
                .forEach(path -> register(path, shyneModsDir));
        } catch (IOException error) {
            ShyneCore.LOGGER.error("[ShyneItemRuntime] Failed to discover items: {}", error.getMessage());
            if (diagnostics != null) diagnostics.error("item", "registry", shyneModsDir,
                "Failed to discover item files: " + error.getMessage(), "Check that the shyne-mods folders are readable.");
        }
    }

    private void register(Path path, Path shyneModsDir) {
        String inferredId = path.getFileName().toString().replaceFirst("\\.json$", "");
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String itemId = string(root, "item_id", inferredId).toLowerCase(Locale.ROOT);
            if (!SAFE_ID.matcher(itemId).matches()) throw new IOException("item_id must match " + SAFE_ID.pattern());
            String modelId = string(root, "model", "shyne_creator:artifact");
            if (Identifier.tryParse(modelId) == null) throw new IOException("model must be a valid namespaced id");
            String sourcePack = sourcePack(path, shyneModsDir);
            ShyneItemDefinition definition = new ShyneItemDefinition(
                itemId,
                string(root, "display_name", itemId),
                strings(root, "description"),
                modelId,
                rarity(string(root, "rarity", "common")),
                clamp(integer(root, "max_stack", 1), 1, 64),
                bool(root, "glint", false),
                string(root, "use_skill", ""),
                string(root, "weapon_id", ""),
                Math.max(0, integer(root, "cooldown_ticks", 0)),
                bool(root, "consume_on_use", false),
                sourcePack,
                root.has("payload") && root.get("payload").isJsonObject()
                    ? GSON.fromJson(root.getAsJsonObject("payload"), Map.class) : Map.of()
            );
            ShyneItemDefinition previous = definitions.put(itemId, definition);
            if (previous != null && diagnostics != null) diagnostics.warn("item", itemId, path,
                "Duplicate item_id replaced an earlier definition.", "Use a unique item_id in every content pack.");
        } catch (Exception error) {
            ShyneCore.LOGGER.warn("[ShyneItemRuntime] Could not load {}: {}", path, error.getMessage());
            if (diagnostics != null) diagnostics.error("item", inferredId, path,
                "Could not load item JSON: " + error.getMessage(), "Validate the item JSON and its ids.");
        }
    }

    public ItemStack createStack(String itemId, int count) {
        ShyneItemDefinition definition = get(itemId);
        if (definition == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(ShyneItems.ARTIFACT, Math.min(Math.max(1, count), definition.maxStack()));
        CompoundTag tag = new CompoundTag();
        tag.putString(DATA_ID, definition.itemId());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(definition.displayName()).withStyle(definition.rarity().color()));
        stack.set(DataComponents.RARITY, definition.rarity());
        stack.set(DataComponents.MAX_STACK_SIZE, definition.maxStack());
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, definition.glint());
        Identifier model = Identifier.tryParse(definition.modelId());
        if (model != null) stack.set(DataComponents.ITEM_MODEL, model);
        if (!definition.description().isEmpty()) {
            List<Component> lore = definition.description().stream()
                .map(line -> (Component) Component.literal(line).withStyle(ChatFormatting.GRAY))
                .toList();
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    public boolean give(ServerPlayer player, String itemId, int count) {
        if (player == null) return false;
        ShyneItemDefinition definition = get(itemId);
        if (definition == null) return false;
        int remaining = Math.max(1, count);
        while (remaining > 0) {
            int batch = Math.min(remaining, definition.maxStack());
            ItemStack stack = createStack(itemId, batch);
            if (!player.getInventory().add(stack)) player.drop(stack, false, true);
            remaining -= batch;
        }
        ShyneCore.LOGGER.info("[ShyneItemRuntime] Gave {}x {} to {}", count, definition.itemId(), player.getName().getString());
        return true;
    }

    public ShyneItemDefinition resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != ShyneItems.ARTIFACT) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        return get(data.copyTag().getStringOr(DATA_ID, ""));
    }

    public InteractionResult use(net.minecraft.world.entity.player.Player rawPlayer, InteractionHand hand) {
        if (!(rawPlayer instanceof ServerPlayer player)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        ShyneItemDefinition definition = resolve(stack);
        if (definition == null) return InteractionResult.FAIL;
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;

        if (!definition.weaponId().isBlank()) {
            String slot = hand == InteractionHand.OFF_HAND ? "offhand" : "mainhand";
            if (!equipmentRuntime.equipWeapon(player, slot, definition.weaponId())) {
                player.sendOverlayMessage(Component.literal("Unknown Shyne weapon: " + definition.weaponId()).withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
        }

        SkillExecutor.Status status = SkillExecutor.Status.SCRIPT_ONLY;
        if (!definition.useSkill().isBlank()) {
            status = skillExecutor.execute(player, definition.useSkill(), SkillSlot.PRIMARY,
                "item:" + definition.itemId(), hand == InteractionHand.OFF_HAND ? 2 : 1);
            if (status != SkillExecutor.Status.CAST && status != SkillExecutor.Status.SCRIPT_ONLY) {
                player.sendOverlayMessage(Component.literal("Power unavailable: " + readable(status)).withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
        }

        Map<String, Object> hook = new LinkedHashMap<>();
        hook.put("player", player.getName().getString());
        hook.put("uuid", player.getStringUUID());
        hook.put("item_id", definition.itemId());
        hook.put("skill_id", definition.useSkill());
        hook.put("weapon_id", definition.weaponId());
        hook.put("hand", hand == InteractionHand.OFF_HAND ? "offhand" : "mainhand");
        hook.put("payload", definition.payload());
        modLoader.fireHookIn(definition.sourcePack(), "on_item_use", hook);

        if (definition.cooldownTicks() > 0) player.getCooldowns().addCooldown(stack, definition.cooldownTicks());
        if (definition.consumeOnUse() && !player.hasInfiniteMaterials()) stack.shrink(1);
        player.swing(hand);
        return InteractionResult.SUCCESS_SERVER;
    }

    private String sourcePack(Path path, Path root) {
        Path normalized = path.toAbsolutePath().normalize();
        for (ShyneModLoader.ModInfo mod : modLoader.getLoadedMods()) {
            if (mod.path != null && normalized.startsWith(mod.path.toAbsolutePath().normalize())) return mod.id;
        }
        try {
            Path relative = root.toAbsolutePath().normalize().relativize(normalized);
            return relative.getNameCount() > 0 ? relative.getName(0).toString() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String readable(SkillExecutor.Status status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static Rarity rarity(String value) {
        try { return Rarity.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return Rarity.COMMON; }
    }

    private static String string(JsonObject root, String key, String fallback) {
        return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject root, String key, int fallback) {
        try { return root.has(key) ? root.get(key).getAsInt() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        try { return root.has(key) ? root.get(key).getAsBoolean() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static List<String> strings(JsonObject root, String key) {
        if (!root.has(key)) return List.of();
        JsonElement value = root.get(key);
        if (value.isJsonPrimitive()) return List.of(value.getAsString());
        if (!value.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement entry : value.getAsJsonArray()) if (entry.isJsonPrimitive()) result.add(entry.getAsString());
        return List.copyOf(result);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

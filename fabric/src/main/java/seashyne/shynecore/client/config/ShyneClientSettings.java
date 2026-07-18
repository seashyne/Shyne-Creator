package seashyne.shynecore.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarPermission;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;

public final class ShyneClientSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("shyne-creator-client.json");
    private static final String DEFAULT_CLOUD_ENDPOINT = "https://shyne-avatar-cloud.jirayut-wh.workers.dev";

    public static boolean renderAttachments = true;
    public static boolean renderDebugLines = false;
    public static boolean renderUiHints = true;
    public static boolean autoPlayVisuals = true;
    public static String selectedAvatarId = "";
    public static Map<String, String> selectedOutfits = new HashMap<>();
    public static Map<String, Set<String>> publicAvatarPermissionDecisions = new HashMap<>();
    public static boolean cloudEnabled = false;
    public static String cloudEndpoint = DEFAULT_CLOUD_ENDPOINT;
    public static int avatarMaxBones = 4096;
    public static int avatarMaxCubes = 16384;
    public static int avatarMaxAnimations = 512;
    public static int avatarMaxTextures = 256;
    public static int avatarMaxTextureSize = 8192;

    private ShyneClientSettings() {}

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            renderAttachments = data.renderAttachments;
            renderDebugLines = data.renderDebugLines;
            renderUiHints = data.renderUiHints;
            autoPlayVisuals = data.autoPlayVisuals;
            selectedAvatarId = data.selectedAvatarId == null ? "" : data.selectedAvatarId;
            selectedOutfits = data.selectedOutfits == null ? new HashMap<>() : new HashMap<>(data.selectedOutfits);
            publicAvatarPermissionDecisions = sanitizePermissionDecisions(data.publicAvatarPermissionDecisions);
            cloudEnabled = data.cloudEnabled;
            cloudEndpoint = data.cloudEndpoint == null || data.cloudEndpoint.isBlank() ? DEFAULT_CLOUD_ENDPOINT : data.cloudEndpoint;
            avatarMaxBones = positive(data.avatarMaxBones, 4096);
            avatarMaxCubes = positive(data.avatarMaxCubes, 16384);
            avatarMaxAnimations = positive(data.avatarMaxAnimations, 512);
            avatarMaxTextures = positive(data.avatarMaxTextures, 256);
            avatarMaxTextureSize = positive(data.avatarMaxTextureSize, 8192);
        } catch (Exception e) {
            ShyneCore.LOGGER.warn("[ShyneCreator] Could not load client settings: {}", e.getMessage());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(snapshot(), writer);
            }
        } catch (IOException e) {
            ShyneCore.LOGGER.warn("[ShyneCreator] Could not save client settings: {}", e.getMessage());
        }
    }

    public static void resetDefaults() {
        renderAttachments = true;
        renderDebugLines = false;
        renderUiHints = true;
        autoPlayVisuals = true;
        cloudEnabled = false;
        publicAvatarPermissionDecisions.clear();
        avatarMaxBones = 4096;
        avatarMaxCubes = 16384;
        avatarMaxAnimations = 512;
        avatarMaxTextures = 256;
        avatarMaxTextureSize = 8192;
        save();
    }

    public static String selectedOutfit(String avatarId) {
        if (avatarId == null || avatarId.isBlank()) return "@default";
        return selectedOutfits.getOrDefault(avatarId.toLowerCase(java.util.Locale.ROOT), "@default");
    }

    public static void selectOutfit(String avatarId, String outfitId) {
        if (avatarId == null || avatarId.isBlank()) return;
        selectedOutfits.put(
            avatarId.toLowerCase(java.util.Locale.ROOT),
            outfitId == null || outfitId.isBlank() ? "@default" : outfitId
        );
        save();
    }

    public static boolean hasPublicPermissionDecision(String shareId, String packageHash) {
        return publicAvatarPermissionDecisions.containsKey(permissionDecisionKey(shareId, packageHash));
    }

    public static Set<AvatarPermission> approvedPublicPermissions(String shareId, String packageHash) {
        Set<String> stored = publicAvatarPermissionDecisions.get(permissionDecisionKey(shareId, packageHash));
        if (stored == null || stored.isEmpty()) return Set.of();
        EnumSet<AvatarPermission> approved = EnumSet.noneOf(AvatarPermission.class);
        for (String id : stored) AvatarPermission.fromId(id).ifPresent(approved::add);
        return Set.copyOf(approved);
    }

    public static void decidePublicPermissions(String shareId, String packageHash, Set<AvatarPermission> approved) {
        String key = permissionDecisionKey(shareId, packageHash);
        Set<String> ids = new HashSet<>();
        if (approved != null) {
            for (AvatarPermission permission : approved) ids.add(permission.id());
        }
        publicAvatarPermissionDecisions.put(key, ids);
        save();
    }

    public static void forgetPublicPermissionDecision(String shareId, String packageHash) {
        publicAvatarPermissionDecisions.remove(permissionDecisionKey(shareId, packageHash));
        save();
    }

    private static Data snapshot() {
        Data data = new Data();
        data.renderAttachments = renderAttachments;
        data.renderDebugLines = renderDebugLines;
        data.renderUiHints = renderUiHints;
        data.autoPlayVisuals = autoPlayVisuals;
        data.selectedAvatarId = selectedAvatarId;
        data.selectedOutfits = new HashMap<>(selectedOutfits);
        data.publicAvatarPermissionDecisions = copyPermissionDecisions(publicAvatarPermissionDecisions);
        data.cloudEnabled = cloudEnabled;
        data.cloudEndpoint = cloudEndpoint;
        data.avatarMaxBones = avatarMaxBones;
        data.avatarMaxCubes = avatarMaxCubes;
        data.avatarMaxAnimations = avatarMaxAnimations;
        data.avatarMaxTextures = avatarMaxTextures;
        data.avatarMaxTextureSize = avatarMaxTextureSize;
        return data;
    }

    private static String permissionDecisionKey(String shareId, String packageHash) {
        String safeShare = shareId == null ? "" : shareId.trim().toLowerCase(java.util.Locale.ROOT);
        String safeHash = packageHash == null ? "" : packageHash.trim().toLowerCase(java.util.Locale.ROOT);
        return safeShare + ":" + safeHash;
    }

    private static Map<String, Set<String>> sanitizePermissionDecisions(Map<String, Set<String>> input) {
        Map<String, Set<String>> result = new HashMap<>();
        if (input == null) return result;
        for (Map.Entry<String, Set<String>> entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            Set<String> safe = new HashSet<>();
            for (String id : entry.getValue()) {
                AvatarPermission.fromId(id).ifPresent(permission -> safe.add(permission.id()));
            }
            result.put(entry.getKey(), safe);
        }
        return result;
    }

    private static Map<String, Set<String>> copyPermissionDecisions(Map<String, Set<String>> input) {
        Map<String, Set<String>> copy = new HashMap<>();
        input.forEach((key, value) -> copy.put(key, value == null ? Set.of() : new HashSet<>(value)));
        return copy;
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static final class Data {
        boolean renderAttachments = true;
        boolean renderDebugLines = false;
        boolean renderUiHints = true;
        boolean autoPlayVisuals = true;
        String selectedAvatarId = "";
        Map<String, String> selectedOutfits = new HashMap<>();
        Map<String, Set<String>> publicAvatarPermissionDecisions = new HashMap<>();
        boolean cloudEnabled = false;
        String cloudEndpoint = DEFAULT_CLOUD_ENDPOINT;
        int avatarMaxBones = 4096;
        int avatarMaxCubes = 16384;
        int avatarMaxAnimations = 512;
        int avatarMaxTextures = 256;
        int avatarMaxTextureSize = 8192;
    }
}

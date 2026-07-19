package seashyne.shynecore.client.avatar;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Mutable, thread-visible state for the currently active Avatar.
 *
 * <p>This object is shared by Lua callbacks, networking and rendering. Concurrent
 * maps and dirty flags keep those paths safe without rebuilding every frame.</p>
 */
public final class AvatarState {
    private final String avatarId;
    private final String modelId;
    private final Path rootDir;
    private final boolean replaceVanilla;
    private final boolean publicShare;
    private final Set<AvatarPermission> requestedPermissions;
    private final Set<AvatarPermission> grantedPermissions;
    private String apiStandard = ShyneApiStandard.LATEST;
    private boolean automaticApi = true;
    private Map<String, String> apiRequirements = Map.of();
    private boolean firstPersonMasking;
    private boolean localCameraOnly;
    private boolean hideHeadInFirstPerson;
    private float cameraOffsetX, cameraOffsetY, cameraOffsetZ;
    private float cameraRotationX, cameraRotationY, cameraRotationZ;
    private String nameplateText = "";
    private boolean nameplateVisible = true;
    private String textureSyncMode = "manifest";
    private String syncedSchemaPath = "";
    private final Map<String, Object> vars = new ConcurrentHashMap<>();
    private final Map<String, Object> syncedVars = new ConcurrentHashMap<>();
    private final Map<String, Double> animationParameters = new ConcurrentHashMap<>();
    private final Map<String, AvatarPartState> parts = new ConcurrentHashMap<>();
    private final Map<String, String> pathAliases = new ConcurrentHashMap<>();
    private final Map<String, Boolean> vanillaVisibility = new ConcurrentHashMap<>();
    private final List<AvatarAction> actions = new ArrayList<>();
    private final Map<String, AvatarEmoteDefinition> emotes = new ConcurrentHashMap<>();
    private final AvatarAnimationGraph animationGraph = new AvatarAnimationGraph();
    private final Map<String, AvatarAnimationLayer> animationLayers = new ConcurrentHashMap<>();
    private volatile List<AvatarAnimationLayer> sortedAnimationLayers = List.of();
    private volatile boolean animationLayersDirty = true;
    private final AvatarSyncPolicy syncPolicy = new AvatarSyncPolicy();
    private List<AvatarOutfit> outfits = List.of();
    private String selectedOutfitId = AvatarOutfitLoader.DEFAULT_OUTFIT;
    private byte[] selectedOutfitTexture = new byte[0];
    private UUID boundEntityId;
    private volatile boolean syncedDirty;
    private volatile boolean animationParametersDirty;
    private volatile boolean snapshotDirty = true;
    private String currentAnimation = "";
    private long currentAnimationStartedAtMillis;

    public AvatarState(
        String avatarId,
        String modelId,
        Path rootDir,
        boolean replaceVanilla,
        Set<AvatarPermission> requestedPermissions,
        Set<AvatarPermission> grantedPermissions
    ) {
        this.avatarId = avatarId;
        this.modelId = modelId;
        this.rootDir = rootDir;
        this.replaceVanilla = replaceVanilla;
        this.publicShare = ShyneSecureAvatar.isRuntimePath(rootDir);
        this.requestedPermissions = requestedPermissions == null ? Set.of() : Set.copyOf(requestedPermissions);
        this.grantedPermissions = grantedPermissions == null ? Set.of() : Set.copyOf(grantedPermissions);
        vanillaVisibility.put("PLAYER", !replaceVanilla);
        vanillaVisibility.put("HEAD", true);
        vanillaVisibility.put("BODY", true);
        vanillaVisibility.put("LEFT_ARM", true);
        vanillaVisibility.put("RIGHT_ARM", true);
        vanillaVisibility.put("LEFT_LEG", true);
        vanillaVisibility.put("RIGHT_LEG", true);
        vanillaVisibility.put("HAT", true);
        vanillaVisibility.put("JACKET", true);
        vanillaVisibility.put("LEFT_SLEEVE", true);
        vanillaVisibility.put("RIGHT_SLEEVE", true);
        vanillaVisibility.put("LEFT_PANTS", true);
        vanillaVisibility.put("RIGHT_PANTS", true);
        vanillaVisibility.put("CAPE", true);
    }

    public String avatarId() { return avatarId; }
    public String modelId() { return modelId; }
    public Path rootDir() { return rootDir; }
    public boolean replaceVanilla() { return replaceVanilla; }
    public boolean publicShare() { return publicShare; }
    public Set<AvatarPermission> requestedPermissions() { return requestedPermissions; }
    public Set<AvatarPermission> grantedPermissions() { return grantedPermissions; }
    public String apiStandard() { return apiStandard; }
    public boolean automaticApi() { return automaticApi; }
    public Map<String, String> apiRequirements() { return apiRequirements; }
    public void setApiContract(String standard, boolean automatic, Map<String, String> requirements) {
        this.apiStandard = standard == null || standard.isBlank() ? ShyneApiStandard.LATEST : standard;
        this.automaticApi = automatic;
        this.apiRequirements = requirements == null ? Map.of() : Map.copyOf(requirements);
    }
    public boolean permissionAllowed(AvatarPermission permission) {
        return !publicShare || (requestedPermissions.contains(permission) && grantedPermissions.contains(permission));
    }
    public Map<String, Object> vars() { return vars; }
    public boolean firstPersonMasking() { return firstPersonMasking; }
    public void setFirstPersonMasking(boolean value) { this.firstPersonMasking = value; this.snapshotDirty = true; }
    public boolean localCameraOnly() { return localCameraOnly; }
    public void setLocalCameraOnly(boolean value) { this.localCameraOnly = value; }
    public boolean hideHeadInFirstPerson() { return hideHeadInFirstPerson; }
    public void setHideHeadInFirstPerson(boolean value) { this.hideHeadInFirstPerson = value; this.snapshotDirty = true; }
    public void setCameraOffset(float x, float y, float z) { cameraOffsetX=x; cameraOffsetY=y; cameraOffsetZ=z; }
    public void setCameraRotation(float x, float y, float z) { cameraRotationX=x; cameraRotationY=y; cameraRotationZ=z; }
    public float cameraOffsetX() { return cameraOffsetX; }
    public float cameraOffsetY() { return cameraOffsetY; }
    public float cameraOffsetZ() { return cameraOffsetZ; }
    public float cameraRotationX() { return cameraRotationX; }
    public float cameraRotationY() { return cameraRotationY; }
    public float cameraRotationZ() { return cameraRotationZ; }
    public String nameplateText() { return nameplateText; }
    public boolean nameplateVisible() { return nameplateVisible; }
    public void setNameplate(String text, boolean visible) { nameplateText = text == null ? "" : text.substring(0, Math.min(128, text.length())); nameplateVisible = visible; snapshotDirty = true; }
    public String textureSyncMode() { return textureSyncMode; }
    public void setTextureSyncMode(String value) { this.textureSyncMode = value == null || value.isBlank() ? "manifest" : value; this.snapshotDirty = true; }
    public String syncedSchemaPath() { return syncedSchemaPath; }
    public void setSyncedSchemaPath(String value) { this.syncedSchemaPath = value == null ? "" : value; }
    public Map<String, Object> syncedVars() { return syncedVars; }
    public Map<String, Double> animationParameters() { return animationParameters; }
    public void setAnimationParameter(String name, double value) {
        if (name == null || name.isBlank() || !Double.isFinite(value)) return;
        String key = normalizeAnimationParameter(name);
        double bounded = Math.max(-1_000_000.0, Math.min(1_000_000.0, value));
        Double previous = animationParameters.put(key, bounded);
        if (previous == null || Math.abs(previous - bounded) > 0.0001) animationParametersDirty = true;
    }
    public double animationParameter(String name, double fallback) {
        if (name == null || name.isBlank()) return fallback;
        return animationParameters.getOrDefault(normalizeAnimationParameter(name), fallback);
    }
    public void clearAnimationParameter(String name) {
        if (name != null && animationParameters.remove(normalizeAnimationParameter(name)) != null) animationParametersDirty = true;
    }
    public boolean areAnimationParametersDirty() { return animationParametersDirty; }
    public void clearAnimationParametersDirty() { animationParametersDirty = false; }
    public Map<String, AvatarPartState> parts() { return parts; }
    public Map<String, Boolean> vanillaVisibility() { return vanillaVisibility; }
    public List<AvatarAction> actions() { return actions; }
    public Map<String, AvatarEmoteDefinition> emotes() { return emotes; }
    public AvatarAnimationGraph animationGraph() { return animationGraph; }
    public Map<String, AvatarAnimationLayer> animationLayers() { return animationLayers; }
    public List<AvatarAnimationLayer> sortedAnimationLayers() {
        if (!animationLayersDirty) return sortedAnimationLayers;
        synchronized (animationLayers) {
            if (animationLayersDirty) {
                sortedAnimationLayers = animationLayers.values().stream()
                    .sorted(java.util.Comparator.comparingInt(AvatarAnimationLayer::priority))
                    .toList();
                animationLayersDirty = false;
            }
            return sortedAnimationLayers;
        }
    }
    public void markAnimationLayersDirty() { animationLayersDirty = true; }
    public AvatarSyncPolicy syncPolicy() { return syncPolicy; }
    public List<AvatarOutfit> outfits() { return outfits; }
    public void setOutfits(List<AvatarOutfit> value) { this.outfits = value == null ? List.of() : List.copyOf(value); }
    public String selectedOutfitId() { return selectedOutfitId; }
    public byte[] selectedOutfitTexture() { return selectedOutfitTexture.clone(); }
    public void selectOutfit(String outfitId, byte[] texture) {
        this.selectedOutfitId = outfitId == null || outfitId.isBlank() ? AvatarOutfitLoader.DEFAULT_OUTFIT : outfitId;
        this.selectedOutfitTexture = texture == null ? new byte[0] : texture.clone();
        this.snapshotDirty = true;
    }
    public UUID boundEntityId() { return boundEntityId; }
    public void bindEntity(UUID id) { this.boundEntityId = id; }
    public boolean isSyncedDirty() { return syncedDirty; }
    public void markSyncedDirty() { this.syncedDirty = true; }
    public void clearSyncedDirty() { this.syncedDirty = false; }
    public boolean isSnapshotDirty() { return snapshotDirty; }
    public void markSnapshotDirty() { this.snapshotDirty = true; }
    public void clearSnapshotDirty() { this.snapshotDirty = false; }
    public String currentAnimation() { return currentAnimation; }
    public long currentAnimationStartedAtMillis() { return currentAnimationStartedAtMillis; }
    public void setCurrentAnimation(String currentAnimation) {
        this.currentAnimation = currentAnimation == null ? "" : currentAnimation;
        this.currentAnimationStartedAtMillis = System.currentTimeMillis();
        this.snapshotDirty = true;
    }
    public void clearCurrentAnimation() {
        this.currentAnimation = "";
        this.currentAnimationStartedAtMillis = 0L;
        this.snapshotDirty = true;
    }

    private static String normalizeAnimationParameter(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("v.") ? normalized.substring(2) : normalized;
    }

    public void aliasPath(String alias, String canonical) {
        if (alias == null || alias.isBlank() || canonical == null || canonical.isBlank()) return;
        pathAliases.put(alias, canonical);
        pathAliases.put(alias.toLowerCase(), canonical);
    }

    public String resolvePath(String path) {
        if (path == null || path.isBlank()) return path;
        return pathAliases.getOrDefault(path, pathAliases.getOrDefault(path.toLowerCase(), path));
    }

    public AvatarPartState getPart(String path) {
        return parts.computeIfAbsent(resolvePath(path), k -> new AvatarPartState());
    }

    public void registerAction(AvatarAction action) {
        if (action == null) return;
        synchronized (actions) {
            actions.removeIf(existing -> existing.id().equalsIgnoreCase(action.id()) && existing.page().equalsIgnoreCase(action.page()));
            actions.add(action);
        }
    }

    public void registerEmote(AvatarEmoteDefinition emote) {
        if (emote == null || emote.id() == null || emote.id().isBlank()) return;
        emotes.put(emote.id(), emote);
    }

    public AvatarEmoteDefinition findEmote(String emoteId) {
        if (emoteId == null || emoteId.isBlank()) return null;
        return emotes.get(emoteId);
    }

    public Map<String, List<AvatarAction>> actionsByPage() {
        Map<String, List<AvatarAction>> pages = new LinkedHashMap<>();
        synchronized (actions) {
            for (AvatarAction action : actions) {
                pages.computeIfAbsent(action.page(), ignored -> new ArrayList<>()).add(action);
            }
        }
        return pages;
    }
}

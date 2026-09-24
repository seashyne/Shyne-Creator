package seashyne.shynecore.client.avatar;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.animation.AnimationPlayback;
import seashyne.shynecore.attachment.AttachedModelState;
import seashyne.shynecore.avatar.AvatarAnimationClock;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.render.BbModelTextures;
import seashyne.shynecore.client.render.AvatarBoneTransformRegistry;
import seashyne.shynecore.client.render.AvatarRenderContext;
import seashyne.shynecore.client.profiler.AvatarProfiler;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbModelParser;
import seashyne.shynecore.network.ShyneNetwork;
import seashyne.shynecore.voice.ShyneMicrophoneState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class AvatarRuntime {
    private static final int POSE_SNAPSHOT_INTERVAL_TICKS = 2;
    private static final long MIN_SNAPSHOT_SEND_INTERVAL_NANOS = 80_000_000L;
    private static AvatarState active;
    private static AvatarManifest activeManifest;
    private static BbModelDefinition activeModel;
    private static ClientLuaAvatarRuntime script;
    private static AvatarAutoAnimationController animationController;
    private static AvatarPhysicsController physicsController;
    private static Player physicsPlayer;
    private static Object physicsLevel;
    private static volatile List<AvatarCatalogEntry> catalog = List.of();
    private static CompletableFuture<List<AvatarCatalogEntry>> catalogRefresh = CompletableFuture.completedFuture(List.of());
    private static long lastCatalogRefreshMillis;
    private static boolean initialized;
    private static int snapshotTicks;
    private static boolean snapshotAssetsSent;
    private static long forceModelSnapshotUntilMillis;
    private static long lastSnapshotAttemptAtNanos;
    private static long nextSnapshotTransportRevision;
    private static final NavigableMap<Long, SentSnapshotRevision> awaitingSnapshotAcks = new TreeMap<>();
    private static String pendingAvatarClearPlayerId;
    private static long pendingAvatarClearRevision;
    private static ShyneMicrophoneState.Snapshot lastMicrophoneSnapshot;
    private static long lastMicrophoneEventNanos;
    private static AvatarActivationResult lastActivation = AvatarActivationResult.success("", "Ready");
    public static final String VANILLA_SELECTION = "@vanilla";

    private AvatarRuntime() {}

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            snapshotAssetsSent = false;
            lastSnapshotAttemptAtNanos = 0L;
            nextSnapshotTransportRevision = 0L;
            awaitingSnapshotAcks.clear();
            pendingAvatarClearPlayerId = null;
            pendingAvatarClearRevision = 0L;
            if (active != null) active.markSnapshotDirty();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            snapshotAssetsSent = false;
            lastSnapshotAttemptAtNanos = 0L;
            nextSnapshotTransportRevision = 0L;
            awaitingSnapshotAcks.clear();
            pendingAvatarClearPlayerId = null;
            pendingAvatarClearRevision = 0L;
            AvatarBoneTransformRegistry.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            applySnapshotAcknowledgements(client.player.getUUID());
            flushPendingAvatarClear();
            ShyneCloudClient.tick(client);
            if (!initialized) {
                initialized = true;
                activateFirstAvailable(client);
            }
            tickAutomaticAnimations(client.player);
            tickNativePhysics(client.player);
            if (script != null) {
                script.tick(client);
                dispatchMicrophoneEvent();
            }
            pruneAnimationLayers();
            renderHook();
            if (active != null && activeManifest != null && activeManifest.onlineSync()) {
                snapshotTicks++;
                if (snapshotTicks >= 100 || active.isSnapshotDirty()
                    || (snapshotTicks >= POSE_SNAPSHOT_INTERVAL_TICKS && active.isPoseDirty())
                    || (snapshotTicks >= 5 && active.areAnimationParametersDirty())) {
                    if (sendLocalSnapshot(client)) {
                        snapshotTicks = 0;
                    }
                }
            }
            syncAttachment(client);
        });
    }

    public static AvatarState active() { return active; }
    public static AvatarManifest manifest() { return activeManifest; }
    public static BbModelDefinition activeModel() { return activeModel; }
    public static List<AvatarCatalogEntry> catalog() { return catalog; }
    public static List<AvatarOutfit> outfits() { return active == null ? List.of() : active.outfits(); }
    public static String selectedOutfitId() { return active == null ? AvatarOutfitLoader.DEFAULT_OUTFIT : active.selectedOutfitId(); }
    public static AvatarActivationResult lastActivation() { return lastActivation; }
    public static boolean shouldHideLocalPlayer() { return active != null && active.replaceVanilla(); }
    public static boolean shouldMaskFirstPerson() { return active != null && active.firstPersonMasking(); }
    public static boolean shouldUseLocalOnlyCamera() { return active != null && active.localCameraOnly(); }
    public static boolean shouldHideHeadInFirstPerson() { return active != null && active.hideHeadInFirstPerson(); }
    public static float cameraOffsetX() { return active == null ? 0f : active.cameraOffsetX(); }
    public static float cameraOffsetY() { return active == null ? 0f : active.cameraOffsetY(); }
    public static float cameraOffsetZ() { return active == null ? 0f : active.cameraOffsetZ(); }
    public static float cameraRotationX() { return active == null ? 0f : active.cameraRotationX(); }
    public static float cameraRotationY() { return active == null ? 0f : active.cameraRotationY(); }
    public static String localNameplateText() { return active == null ? "" : active.nameplateText(); }
    public static boolean localNameplateVisible() { return active == null || active.nameplateVisible(); }
    public static boolean isVanillaPartVisible(String key) { return isVanillaPartVisible(null, key); }
    public static boolean isVanillaPartVisible(UUID playerId, String key) {
        String canonical = VanillaVisibilityKeys.normalize(key);
        if (!ShyneClientSettings.renderAttachments) return true;
        Minecraft client = Minecraft.getInstance();
        UUID localId = client.player == null ? null : client.player.getUUID();
        boolean localRequest = playerId == null || playerId.equals(localId)
            || (active != null && playerId.equals(active.boundEntityId()));
        if (localRequest) {
            if (active == null) return true;
            return resolvedVanillaVisibility(active.vanillaVisibility(), active.replaceVanilla(), canonical);
        }
        if (ShyneClientSettings.isRemoteAvatarHidden(playerId)) return true;
        RemoteAvatarState remote = ClientAnimationState.getRemoteAvatar(playerId);
        if (remote == null) return true;
        return resolvedVanillaVisibility(remote.vanillaVisibility(), remote.replaceVanilla(), canonical);
    }

    private static boolean resolvedVanillaVisibility(Map<String, Boolean> visibility, boolean replaceVanilla, String key) {
        if (!VanillaVisibilityKeys.PLAYER.equals(key)
            && !VanillaVisibilityKeys.isVisible(visibility, replaceVanilla, VanillaVisibilityKeys.PLAYER)) return false;
        return VanillaVisibilityKeys.isVisible(visibility, replaceVanilla, key);
    }

    public static void activateFirstAvailable(Minecraft client) {
        if (ShyneCloudClient.restoreSelectedPublic(client)) return;
        refreshCatalog();
        String preferred = ShyneClientSettings.selectedAvatarId;
        if (VANILLA_SELECTION.equals(preferred)) {
            deactivate(client);
            return;
        }
        if (preferred != null && !preferred.isBlank() && switchAvatar(preferred, client).success()) return;
        for (AvatarCatalogEntry entry : catalog) {
            if (entry.valid()) {
                switchAvatar(entry.id(), client);
                return;
            }
        }
        lastActivation = AvatarActivationResult.failure("", catalog.isEmpty() ? "No avatars installed" : "No valid avatars found");
    }

    public static void refreshCatalog() {
        catalog = AvatarLoader.discoverCatalog();
        lastCatalogRefreshMillis = System.currentTimeMillis();
    }

    /**
     * Coalesces file-system scans so opening or rebuilding a screen never blocks
     * the render thread on every click.
     */
    public static synchronized CompletableFuture<List<AvatarCatalogEntry>> refreshCatalogAsync() {
        return refreshCatalogAsync(false);
    }

    public static synchronized CompletableFuture<List<AvatarCatalogEntry>> refreshCatalogAsync(boolean force) {
        long now = System.currentTimeMillis();
        if (!catalogRefresh.isDone()) return catalogRefresh;
        if (!force && now - lastCatalogRefreshMillis < 1_500L) return CompletableFuture.completedFuture(catalog);
        catalogRefresh = CompletableFuture.supplyAsync(AvatarLoader::discoverCatalog)
            .thenApply(entries -> {
                catalog = entries;
                lastCatalogRefreshMillis = System.currentTimeMillis();
                return entries;
            })
            .exceptionally(error -> {
                ShyneCore.LOGGER.warn("[AvatarRuntime] Background catalog refresh failed: {}", error.getMessage());
                return catalog;
            });
        return catalogRefresh;
    }

    public static boolean activateAvatar(String avatarId, Minecraft client) {
        return switchAvatar(avatarId, client).success();
    }

    public static AvatarActivationResult switchAvatar(String avatarId, Minecraft client) {
        if (avatarId == null || avatarId.isBlank()) {
            lastActivation = AvatarActivationResult.failure("", "Avatar id is required");
            return lastActivation;
        }
        AvatarCatalogEntry found = findCatalogEntry(avatarId);
        if (found == null) {
            refreshCatalog();
            found = findCatalogEntry(avatarId);
        }
        if (found != null) {
            AvatarCatalogEntry entry = found;
            if (entry.id().equalsIgnoreCase(avatarId) || entry.name().equalsIgnoreCase(avatarId)) {
                if (!entry.valid()) {
                    lastActivation = AvatarActivationResult.failure(entry.id(), entry.problem());
                    return lastActivation;
                }
                try {
                    return activate(entry.root(), client);
                } catch (Exception e) {
                    ShyneCore.LOGGER.error("[AvatarRuntime] Could not activate avatar {}: {}", avatarId, e.getMessage(), e);
                    lastActivation = AvatarActivationResult.failure(entry.id(), safeMessage(e));
                    return lastActivation;
                }
            }
        }
        lastActivation = AvatarActivationResult.failure(avatarId, "Avatar not found");
        return lastActivation;
    }

    public static AvatarActivationResult switchAvatar(AvatarCatalogEntry entry, Minecraft client) {
        if (entry == null) {
            lastActivation = AvatarActivationResult.failure("", "Avatar is required");
            return lastActivation;
        }
        if (!entry.valid()) {
            lastActivation = AvatarActivationResult.failure(entry.id(), entry.problem());
            return lastActivation;
        }
        try {
            return activate(entry.root(), client);
        } catch (Exception error) {
            ShyneCore.LOGGER.error("[AvatarRuntime] Could not activate avatar {}: {}", entry.id(), error.getMessage(), error);
            lastActivation = AvatarActivationResult.failure(entry.id(), safeMessage(error));
            return lastActivation;
        }
    }

    private static AvatarCatalogEntry findCatalogEntry(String avatarId) {
        for (AvatarCatalogEntry entry : catalog) {
            if (entry.id().equalsIgnoreCase(avatarId) || entry.name().equalsIgnoreCase(avatarId)) return entry;
        }
        return null;
    }

    public static boolean reloadActive(Minecraft client) {
        if (active == null) {
            activateFirstAvailable(client);
            return active != null;
        }
        try {
            return activate(active.rootDir(), client).success();
        } catch (Exception e) {
            ShyneCore.LOGGER.error("[AvatarRuntime] Could not reload active avatar {}: {}", active.avatarId(), e.getMessage(), e);
            lastActivation = AvatarActivationResult.failure(active.avatarId(), safeMessage(e));
            return false;
        }
    }

    public static AvatarActivationResult activate(Path root, Minecraft client) throws IOException {
        var manifest = AvatarLoader.loadManifest(root);
        String modelId = "avatar:" + manifest.id();
        Path modelPath = AvatarLoader.resolveAvatarFile(root, manifest.model());
        Path scriptPath = manifest.hasScript() ? AvatarLoader.resolveAvatarFile(root, manifest.main()) : null;
        BbModelDefinition model = BbModelParser.parse(modelPath, manifest.id()).withModelId(modelId);
        validateModel(model, root);
        validateDeclaredTextures(model, manifest);
        boolean publicShare = ShyneSecureAvatar.isRuntimePath(root);
        Set<AvatarPermission> grantedPermissions;
        if (publicShare) {
            ShyneSecureAvatar.RuntimePermissions runtimePermissions = ShyneSecureAvatar.runtimePermissions(root);
            if (runtimePermissions == null) throw new IOException("Public Avatar permission context is missing");
            if (!runtimePermissions.requested().equals(manifest.permissions())) {
                throw new IOException("avatar.json permissions do not match the signed Public Share manifest");
            }
            grantedPermissions = runtimePermissions.approved();
        } else {
            grantedPermissions = manifest.permissions();
        }
        AvatarState nextState = new AvatarState(
            manifest.id(),
            modelId,
            root.toAbsolutePath().normalize(),
            manifest.replaceVanilla(),
            manifest.permissions(),
            grantedPermissions
        );
        // Preserve the resolved semantic API contract for Lua introspection and diagnostics.
        nextState.setApiContract(manifest.api(), manifest.automaticApi(), manifest.apiRequirements());
        nextState.setOutfits(AvatarOutfitLoader.discover(root));
        nextState.setFirstPersonMasking(manifest.firstPersonMasking());
        nextState.setLocalCameraOnly(manifest.localCamera());
        nextState.setTextureSyncMode(manifest.textureSyncMode());
        nextState.configureSyncedSchema(manifest.syncedSchema(), AvatarSyncedSchema.load(root, manifest.syncedSchema()));
        nextState.syncPolicy().setAllowRemoteSnapshot(manifest.onlineSync());
        nextState.syncPolicy().setAllowRemoteVars(manifest.onlineSync());
        nextState.bindEntity(client.player != null ? client.player.getUUID() : UUID.randomUUID());
        indexModelPaths(model, nextState);
        ClientLuaAvatarRuntime nextScript = null;
        if (scriptPath != null) {
            nextScript = new ClientLuaAvatarRuntime(nextState, model, scriptPath);
            if (!nextScript.load()) {
                nextScript.dispose();
                throw new IOException("Lua script failed to load; previous avatar was kept");
            }
        }
        long animationSeed = manifest.id().hashCode();
        if (client.player != null) animationSeed ^= client.player.getUUID().getMostSignificantBits() ^ client.player.getUUID().getLeastSignificantBits();
        AvatarAutoAnimationController nextController = new AvatarAutoAnimationController(model, manifest.behavior(), animationSeed);
        AvatarPhysicsController nextPhysicsController = new AvatarPhysicsController(model);

        Path previousRoot = active == null ? null : active.rootDir();
        cleanupActive();
        if (previousRoot != null && !previousRoot.toAbsolutePath().normalize().equals(root.toAbsolutePath().normalize())) {
            ShyneSecureAvatar.releaseRuntime(previousRoot);
        }
        activeManifest = manifest;
        activeModel = model;
        active = nextState;
        if (manifest.onlineSync()) {
            pendingAvatarClearPlayerId = null;
            pendingAvatarClearRevision = 0L;
        }
        script = nextScript;
        animationController = nextController;
        physicsController = nextPhysicsController;
        AvatarProfiler.activate(manifest.id(), root, model);
        if (nextScript != null) AvatarProfiler.record(AvatarProfiler.Category.LUA_LOAD, nextScript.loadElapsedNanos());
        snapshotAssetsSent = false;
        snapshotTicks = 0;
        ClientAnimationState.putLocalModel(modelId, model);
        publishAnimationPlayback(active, System.currentTimeMillis());
        String savedOutfit = ShyneClientSettings.selectedOutfit(manifest.id());
        if (!applyOutfit(savedOutfit, false)) {
            applyOutfit(AvatarOutfitLoader.DEFAULT_OUTFIT, true);
        }
        if (script != null) script.entityInit(client);
        syncAttachment(client);
        active.markSnapshotDirty();
        if (manifest.onlineSync()) sendLocalSnapshot(client);
        else if (client.player != null) requestAvatarClear(client.player.getStringUUID());
        if (!ShyneSecureAvatar.isRuntimePath(root)) {
            ShyneCloudClient.clearActivePublic();
            ShyneClientSettings.selectedAvatarId = manifest.id();
        }
        ShyneClientSettings.save();
        lastActivation = AvatarActivationResult.success(manifest.id(), "Avatar activated");
        ShyneCore.LOGGER.info("[AvatarRuntime] Activated avatar {} from {}", manifest.id(), root);
        return lastActivation;
    }

    public static AvatarActivationResult deactivate(Minecraft client) {
        String previous = active == null ? "" : active.avatarId();
        Path previousRoot = active == null ? null : active.rootDir();
        cleanupActive();
        ShyneSecureAvatar.releaseRuntime(previousRoot);
        ShyneCloudClient.clearActivePublic();
        ShyneClientSettings.selectedAvatarId = VANILLA_SELECTION;
        ShyneClientSettings.save();
        if (client != null && client.player != null) requestAvatarClear(client.player.getStringUUID());
        lastActivation = AvatarActivationResult.success(previous, "Using vanilla player model");
        ShyneCore.LOGGER.info("[AvatarRuntime] Deactivated avatar; using vanilla player model");
        return lastActivation;
    }

    private static void cleanupActive() {
        AvatarState previous = active;
        ClientLuaAvatarRuntime previousScript = script;
        if (previousScript != null) previousScript.dispose();
        if (previous != null) {
            BbModelTextures.clearOutfit(previous.modelId());
            if (activeModel != null) BbModelTextures.clearOutfit(activeModel.modelId());
            UUID entityId = previous.boundEntityId();
            ClientAnimationState.removeLocalPlayback(entityId);
            ClientAnimationState.removeLocalAttachment(entityId);
            ClientAnimationState.clearAvatarPartStates(entityId, previous.modelId());
            ClientAnimationState.removeLocalModel(previous.modelId());
            AvatarBoneTransformRegistry.clearEntity(entityId);
        }
        active = null;
        activeManifest = null;
        activeModel = null;
        script = null;
        animationController = null;
        physicsController = null;
        physicsPlayer = null;
        physicsLevel = null;
        lastMicrophoneSnapshot = null;
        lastMicrophoneEventNanos = 0L;
        snapshotAssetsSent = false;
        forceModelSnapshotUntilMillis = 0L;
        awaitingSnapshotAcks.clear();
        snapshotTicks = 0;
        AvatarProfiler.clear();
    }

    public static boolean selectOutfit(String outfitId, Minecraft client) {
        if (!applyOutfit(outfitId, true)) return false;
        scheduleOutfitSnapshot(client);
        return true;
    }

    private static void scheduleOutfitSnapshot(Minecraft client) {
        snapshotAssetsSent = false;
        snapshotTicks = 0;
        forceModelSnapshotUntilMillis = System.currentTimeMillis() + 1_500L;
        active.markSnapshotDirty();
        if (client != null && activeManifest != null && activeManifest.onlineSync()) sendLocalSnapshot(client);
    }

    public static void refreshOutfits() {
        if (active == null) return;
        String previousId = active.selectedOutfitId();
        byte[] previousTexture = active.selectedOutfitTexture();
        active.setOutfits(AvatarOutfitLoader.discover(active.rootDir()));
        if (!applyOutfit(active.selectedOutfitId(), false)) {
            applyOutfit(AvatarOutfitLoader.DEFAULT_OUTFIT, true);
        }
        if (!previousId.equalsIgnoreCase(active.selectedOutfitId()) || !Arrays.equals(previousTexture, active.selectedOutfitTexture())) {
            scheduleOutfitSnapshot(Minecraft.getInstance());
        }
    }

    private static boolean applyOutfit(String outfitId, boolean persist) {
        if (active == null || activeModel == null) return false;
        String requested = outfitId == null || outfitId.isBlank() ? AvatarOutfitLoader.DEFAULT_OUTFIT : outfitId;
        if (AvatarOutfitLoader.DEFAULT_OUTFIT.equalsIgnoreCase(requested)) {
            BbModelTextures.clearOutfit(active.modelId());
            BbModelTextures.clearOutfit(activeModel.modelId());
            active.selectOutfit(AvatarOutfitLoader.DEFAULT_OUTFIT, new byte[0]);
            if (persist) ShyneClientSettings.selectOutfit(active.avatarId(), AvatarOutfitLoader.DEFAULT_OUTFIT);
            return true;
        }

        AvatarOutfit outfit = active.outfits().stream()
            .filter(candidate -> candidate.id().equalsIgnoreCase(requested))
            .findFirst()
            .orElse(null);
        if (outfit == null || !outfit.valid()) return false;
        try {
            byte[] texture = BbModelTextures.installOutfit(activeModel, outfit);
            active.selectOutfit(outfit.id(), texture);
            if (persist) ShyneClientSettings.selectOutfit(active.avatarId(), outfit.id());
            return true;
        } catch (IOException error) {
            ShyneCore.LOGGER.warn("[AvatarOutfit] Could not wear {}: {}", outfit.path(), error.getMessage());
            return false;
        }
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static void dispatchMicrophoneEvent() {
        if (script == null) return;
        if (active != null && !active.permissionAllowed(AvatarPermission.MICROPHONE)) return;
        ShyneMicrophoneState.Snapshot current = ShyneMicrophoneState.snapshot();
        long now = System.nanoTime();
        boolean stateChanged = lastMicrophoneSnapshot == null
            || current.available() != lastMicrophoneSnapshot.available()
            || current.speaking() != lastMicrophoneSnapshot.speaking()
            || current.muted() != lastMicrophoneSnapshot.muted()
            || current.whispering() != lastMicrophoneSnapshot.whispering()
            || Math.abs(current.level() - lastMicrophoneSnapshot.level()) >= 0.02D;
        boolean speakingRefresh = current.speaking() && now - lastMicrophoneEventNanos >= 50_000_000L;
        if (!stateChanged && !speakingRefresh) return;
        script.microphone(current);
        lastMicrophoneSnapshot = current;
        lastMicrophoneEventNanos = now;
    }

    private static void tickAutomaticAnimations(Player player) {
        if (animationController == null || player == null) return;
        var velocity = player.getDeltaMovement();
        boolean moving = Math.abs(velocity.x) + Math.abs(velocity.z) > 0.015;
        var signals = new AvatarAutoAnimationController.Signals(
            moving,
            player.isSprinting(),
            player.isCrouching(),
            player.isSwimming(),
            player.isInWater(),
            player.isSleeping(),
            player.isFallFlying(),
            player.getVehicle() != null
        );
        AvatarAutoAnimationController.Update update = animationController.tick(signals);
        for (String animation : update.stops()) stopAnimation(animation);
        for (AvatarAutoAnimationController.Play play : update.plays()) {
            playAnimation(
                play.animation(), 1.0, 1.0, play.priority(), play.loop(),
                play.fadeInTicks(), play.fadeOutTicks(), List.of(), play.additive(), play.transitionTicks()
            );
        }
    }

    private static void tickNativePhysics(Player player) {
        if (physicsController == null || active == null || player == null || !physicsController.active()) return;
        if (physicsPlayer != player || physicsLevel != player.level()) {
            physicsController.reset();
            physicsPlayer = player;
            physicsLevel = player.level();
        }
        var velocity = player.getDeltaMovement();
        physicsController.tick(new AvatarPhysicsController.Signals(
            velocity.x, velocity.y, velocity.z,
            player.yBodyRot, player.getXRot(), player.onGround(), player.isInWater(),
            player.level().getGameTime()
        ), active);
    }

    private static void validateModel(BbModelDefinition model, Path avatarRoot) throws IOException {
        if (model.bones().size() > ShyneClientSettings.avatarMaxBones) throw new IOException("model has more than configured avatarMaxBones (" + ShyneClientSettings.avatarMaxBones + ")");
        if (model.cubes().size() + model.meshes().size() > ShyneClientSettings.avatarMaxCubes) throw new IOException("model has more render elements than configured avatarMaxCubes (" + ShyneClientSettings.avatarMaxCubes + ")");
        if (model.animations().size() > ShyneClientSettings.avatarMaxAnimations) throw new IOException("model has more than configured avatarMaxAnimations (" + ShyneClientSettings.avatarMaxAnimations + ")");
        if (model.textures().size() > ShyneClientSettings.avatarMaxTextures) throw new IOException("model has more than configured avatarMaxTextures (" + ShyneClientSettings.avatarMaxTextures + ")");
        int maxTextureSize = ShyneClientSettings.avatarMaxTextureSize;
        if (model.textureWidth() <= 0 || model.textureWidth() > maxTextureSize || model.textureHeight() <= 0 || model.textureHeight() > maxTextureSize) {
            throw new IOException("model texture size must be between 1 and configured avatarMaxTextureSize (" + maxTextureSize + ")");
        }
        Path safeRoot = avatarRoot.toRealPath();
        Path modelRoot = model.sourceFile().toAbsolutePath().normalize().getParent();
        if (modelRoot == null) throw new IOException("model folder is invalid");
        for (var texture : model.textures()) {
            if (texture.relativePath() == null || texture.relativePath().isBlank()) continue;
            Path texturePath = modelRoot.resolve(texture.relativePath().replace('/', java.io.File.separatorChar)).normalize();
            if (!texturePath.startsWith(safeRoot)) throw new IOException("texture escapes avatar folder: " + texture.relativePath());
        }
    }

    private static void validateDeclaredTextures(BbModelDefinition model, AvatarManifest manifest) throws IOException {
        if (manifest.isFiguraImport() || manifest.textures() == null || manifest.textures().isEmpty()) return;
        Set<String> declared = new HashSet<>();
        for (String texture : manifest.textures()) declared.add(normalizeTextureName(texture));
        for (var texture : model.textures()) {
            String relative = normalizeTextureName(texture.relativePath());
            if (!declared.contains(relative)) throw new IOException("model texture is not declared in avatar.json: " + texture.relativePath());
        }
    }

    private static String normalizeTextureName(String value) {
        if (value == null || value.isBlank()) return "";
        return Path.of(value.replace('/', java.io.File.separatorChar)).normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static void indexModelPaths(BbModelDefinition model, AvatarState state) {
        Map<String, Integer> boneNames = new HashMap<>();
        for (BbBoneDefinition bone : model.bones()) {
            boneNames.merge(bone.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        for (BbBoneDefinition bone : model.bones()) {
            String path = model.bonePath(bone.uuid());
            state.aliasPath(path, path);
            state.aliasPath(path.toLowerCase(Locale.ROOT), path);
            state.aliasPath(bone.uuid(), path);
            if (boneNames.getOrDefault(bone.name().toLowerCase(Locale.ROOT), 0) == 1) {
                state.aliasPath(bone.name(), path);
                state.aliasPath("model." + bone.name(), path);
            }
        }
        Map<String, Integer> elementNames = new HashMap<>();
        for (var cube : model.cubes()) elementNames.merge(cube.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        for (var mesh : model.meshes()) elementNames.merge(mesh.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        for (var cube : model.cubes()) {
            String cubePath = model.cubePath(cube);
            state.aliasPath(cubePath, cubePath);
            state.aliasPath(cubePath.toLowerCase(Locale.ROOT), cubePath);
            if (elementNames.getOrDefault(cube.name().toLowerCase(Locale.ROOT), 0) == 1) {
                state.aliasPath(cube.name(), cubePath);
                state.aliasPath("model." + cube.name(), cubePath);
            }
        }
        for (var mesh : model.meshes()) {
            String meshPath = model.meshPath(mesh);
            state.aliasPath(meshPath, meshPath);
            state.aliasPath(meshPath.toLowerCase(Locale.ROOT), meshPath);
            state.aliasPath(mesh.uuid(), meshPath);
            if (elementNames.getOrDefault(mesh.name().toLowerCase(Locale.ROOT), 0) == 1) {
                state.aliasPath(mesh.name(), meshPath);
                state.aliasPath("model." + mesh.name(), meshPath);
            }
        }
    }

    public static void syncAttachment(Minecraft client) {
        if (client.player == null || active == null) return;
        Player player = client.player;
        UUID playerId = player.getUUID();
        if (!playerId.equals(active.boundEntityId())) active.bindEntity(playerId);
        AttachedModelState current = ClientAnimationState.getAttachment(playerId);
        if (current != null && current.visible() && active.modelId().equals(current.modelId())
            && current.offsetX() == 0f && current.offsetY() == 0f && current.offsetZ() == 0f
            && current.scale() == 1f && (current.anchorBone() == null || current.anchorBone().isEmpty())) return;
        ClientAnimationState.putLocalAttachment(new AttachedModelState(playerId, player.getName().getString(), active.modelId(), 0f, 0f, 0f, 1f, "", true));
    }

    public static void playAnimation(String animationName) {
        playAnimation(animationName, 1.0, 1.0, 0, null, 0, 0, List.of(), false);
    }

    public static void playAnimation(String animationName, double speed, double weight, int priority, Boolean loopOverride) {
        playAnimation(animationName, speed, weight, priority, loopOverride, 0, 0, List.of(), false);
    }

    public static void playAnimation(String animationName, double speed, double weight, int priority, Boolean loopOverride,
                                     int fadeInTicks, int fadeOutTicks, List<String> mask, boolean additive) {
        playAnimation(animationName, speed, weight, priority, loopOverride, fadeInTicks, fadeOutTicks, mask, additive, 0);
    }

    public static void playAnimation(String animationName, double speed, double weight, int priority, Boolean loopOverride,
                                     int fadeInTicks, int fadeOutTicks, List<String> mask, boolean additive, int transitionTicks) {
        if (active == null || active.boundEntityId() == null) return;
        if (animationName == null || animationName.isBlank()) return;
        var definition = activeModel == null ? null : activeModel.findAnimation(animationName);
        double length = definition == null || definition.lengthSeconds() <= 0 ? 2.0 : definition.lengthSeconds();
        boolean looping = definition == null || definition.looping();
        if (loopOverride != null) looping = loopOverride;
        int transition = Math.max(0, Math.min(1200, transitionTicks));
        long now = System.currentTimeMillis();
        if (transition > 0 && !additive) {
            active.animationLayers().replaceAll((key, layer) ->
                !layer.additive() && layer.priority() == priority && !key.equals(animationName.toLowerCase(Locale.ROOT))
                    ? layer.requestStop(now, transition) : layer);
        }
        active.animationLayers().put(animationName.toLowerCase(Locale.ROOT), new AvatarAnimationLayer(
            animationName, now, length, looping,
            Math.max(0.01, Math.min(8.0, speed)), Math.max(0.0, Math.min(1.0, weight)),
            Math.max(-1000, Math.min(1000, priority)),
            Math.max(transition, Math.max(0, Math.min(1200, fadeInTicks))), Math.max(transition, Math.max(0, Math.min(1200, fadeOutTicks))),
            mask == null ? List.of() : List.copyOf(mask), additive, 0L
        ));
        active.markAnimationLayersDirty();
        active.markSnapshotDirty();
        refreshCurrentAnimationPlayback(now);
    }

    public static boolean isAnimationPlaying(String animationName) {
        return active != null && animationName != null && active.animationLayers().containsKey(animationName.toLowerCase(Locale.ROOT));
    }

    public static List<AvatarAnimationLayer> animationLayers(UUID entityId) {
        if (active == null || entityId == null || !entityId.equals(active.boundEntityId())) return List.of();
        return active.sortedAnimationLayers();
    }

    public static void stopAnimation(String animationName) {
        if (active == null || animationName == null) return;
        String key = animationName.toLowerCase(Locale.ROOT);
        AvatarAnimationLayer layer = active.animationLayers().get(key);
        if (layer != null && layer.fadeOutTicks() > 0) active.animationLayers().put(key, layer.requestStop(System.currentTimeMillis()));
        else active.animationLayers().remove(key);
        active.markAnimationLayersDirty();
        active.markSnapshotDirty();
        refreshCurrentAnimationPlayback(System.currentTimeMillis());
    }

    private static void pruneAnimationLayers() {
        if (active == null || active.animationLayers().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (active.animationLayers().entrySet().removeIf(entry -> entry.getValue().finished(now))) {
            active.markAnimationLayersDirty();
            active.markSnapshotDirty();
            refreshCurrentAnimationPlayback(now);
        }
    }

    private static void refreshCurrentAnimationPlayback(long now) {
        if (active == null) return;
        active.refreshCurrentAnimationFromLayers(now);
        publishAnimationPlayback(active, now);
    }

    static void publishAnimationPlayback(AvatarState state, long now) {
        if (state == null || active != state) return;
        UUID entityId = state.boundEntityId();
        if (entityId == null) return;
        AvatarAnimationLayer representative = state.representativeAnimationLayer(now);
        if (representative == null) {
            ClientAnimationState.removeLocalPlayback(entityId);
            return;
        }
        ClientAnimationState.putLocalPlayback(new AnimationPlayback(
            entityId, "local-player", state.modelId(), representative.name(), representative.startedAtMillis(),
            representative.lengthSeconds(), true
        ));
    }

    public static void stopAnimation() {
        if (active == null || active.boundEntityId() == null) return;
        active.animationLayers().clear();
        active.markAnimationLayersDirty();
        active.clearCurrentAnimation();
        ClientAnimationState.removeLocalPlayback(active.boundEntityId());
    }

    public static boolean playEmote(String emoteId) {
        if (active == null) return false;
        AvatarEmoteDefinition emote = active.findEmote(emoteId);
        if (emote == null) return false;
        playAnimation(emote.animation());
        return true;
    }

    public static boolean triggerAnimationGraph(String trigger) {
        if (active == null || trigger == null || trigger.isBlank()) return false;
        String emoteId = active.animationGraph().resolve(trigger);
        if (emoteId == null || emoteId.isBlank()) return false;
        return playEmote(emoteId);
    }

    /** Keeps the network/render mirror current at tick rate. Frame events are dispatched separately. */
    public static void renderHook() {
        syncActivePartStates();
    }

    /** Called by {@code AvatarFrameMixin} at the real render-frame boundary. */
    public static void renderFrameStart() {
        Minecraft client = Minecraft.getInstance();
        if (script == null) return;
        float delta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        String context = AvatarRenderContext.current(client);
        if (client.level != null) script.worldRender(delta);
        script.render(delta, context);
        // Render callbacks may change pose channels. Publish those changes before
        // Minecraft extracts/submits this frame's avatar model.
        syncActivePartStates();
    }

    /** Called after GUI/world submission so scripts can observe the completed frame. */
    public static void renderFrameEnd() {
        Minecraft client = Minecraft.getInstance();
        if (script == null) return;
        float delta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        String context = AvatarRenderContext.current(client);
        script.postRender(delta, context);
        if (client.level != null) script.postWorldRender(delta);
    }

    private static void syncActivePartStates() {
        if (active == null) return;
        if (activeModel != null && ClientAnimationState.getModel(active.modelId()) != activeModel) {
            ClientAnimationState.putLocalModel(active.modelId(), activeModel);
        }
        UUID entityId = active.boundEntityId();
        for (var entry : active.parts().entrySet()) {
            ClientAnimationState.updateLocalAvatarPartState(entityId, active.modelId(), entry.getKey(), entry.getValue());
        }
    }

    public static ShyneNetwork.NetAvatarSnapshot buildLocalSnapshot(Minecraft client) {
        return buildLocalSnapshot(client, true);
    }

    private static boolean sendLocalSnapshot(Minecraft client) {
        AvatarState sendingState = active;
        if (sendingState == null) return false;
        long nowNanos = System.nanoTime();
        if (lastSnapshotAttemptAtNanos != 0L && nowNanos - lastSnapshotAttemptAtNanos < MIN_SNAPSHOT_SEND_INTERVAL_NANOS) return false;
        long snapshotRevision = sendingState.captureSnapshotRevision();
        long poseRevision = sendingState.capturePoseRevision();
        long animationParametersRevision = sendingState.captureAnimationParametersRevision();
        long syncedRevision = sendingState.captureSyncedRevision();
        boolean includeModel = !snapshotAssetsSent || System.currentTimeMillis() < forceModelSnapshotUntilMillis;
        long transportRevision = nextTransportRevision();
        lastSnapshotAttemptAtNanos = nowNanos;
        boolean sent = ShyneClientNetworking.sendAvatarSnapshot(buildLocalSnapshot(client, includeModel), transportRevision);
        if (sent) {
            awaitingSnapshotAcks.put(transportRevision, new SentSnapshotRevision(
                sendingState, snapshotRevision, poseRevision, animationParametersRevision, syncedRevision, includeModel
            ));
            while (awaitingSnapshotAcks.size() > 64) awaitingSnapshotAcks.pollFirstEntry();
        }
        return sent;
    }

    private static void requestAvatarClear(String playerId) {
        if (playerId == null || playerId.isBlank()) return;
        if (!playerId.equals(pendingAvatarClearPlayerId)) {
            pendingAvatarClearPlayerId = playerId;
            pendingAvatarClearRevision = 0L;
        }
        flushPendingAvatarClear();
    }

    private static void flushPendingAvatarClear() {
        String playerId = pendingAvatarClearPlayerId;
        if (playerId == null || playerId.isBlank()) return;
        long nowNanos = System.nanoTime();
        if (lastSnapshotAttemptAtNanos != 0L && nowNanos - lastSnapshotAttemptAtNanos < MIN_SNAPSHOT_SEND_INTERVAL_NANOS) return;
        long transportRevision = nextTransportRevision();
        lastSnapshotAttemptAtNanos = nowNanos;
        // Keep the first outstanding revision as the acknowledgement floor.
        // Replacing it on every retry can make the clear chase newer ACKs
        // forever when the network round trip is longer than the retry period.
        if (ShyneClientNetworking.sendAvatarClear(playerId, transportRevision)
            && pendingAvatarClearRevision == 0L) {
            pendingAvatarClearRevision = transportRevision;
        }
    }

    private static void applySnapshotAcknowledgements(UUID playerId) {
        long acknowledged = ClientAnimationState.acknowledgedAvatarSnapshotRevision(playerId);
        if (acknowledged <= 0L) return;
        Iterator<Map.Entry<Long, SentSnapshotRevision>> iterator = awaitingSnapshotAcks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, SentSnapshotRevision> entry = iterator.next();
            if (entry.getKey() > acknowledged) break;
            SentSnapshotRevision sent = entry.getValue();
            sent.state().acknowledgeSnapshotRevision(sent.snapshotRevision());
            sent.state().acknowledgePoseRevision(sent.poseRevision());
            sent.state().acknowledgeAnimationParametersRevision(sent.animationParametersRevision());
            sent.state().acknowledgeSyncedRevision(sent.syncedRevision());
            if (sent.includedModel() && sent.state() == active) snapshotAssetsSent = true;
            iterator.remove();
        }
        if (pendingAvatarClearRevision > 0L && acknowledged >= pendingAvatarClearRevision) {
            pendingAvatarClearPlayerId = null;
            pendingAvatarClearRevision = 0L;
        }
    }

    private static long nextTransportRevision() {
        if (nextSnapshotTransportRevision == Long.MAX_VALUE) nextSnapshotTransportRevision = 0L;
        return ++nextSnapshotTransportRevision;
    }

    private record SentSnapshotRevision(
        AvatarState state,
        long snapshotRevision,
        long poseRevision,
        long animationParametersRevision,
        long syncedRevision,
        boolean includedModel
    ) {}

    private static ShyneNetwork.NetAvatarSnapshot buildLocalSnapshot(Minecraft client, boolean includeModel) {
        if (active == null || activeModel == null) return null;
        long snapshotNowMillis = System.currentTimeMillis();
        String playerId = client.player == null ? UUID.randomUUID().toString() : client.player.getStringUUID();
        List<ShyneNetwork.NetAvatarPart> parts = new ArrayList<>();
        Map<String, AvatarPartState> visibleParts = active.syncPolicy().filterParts(active.parts());
        for (var entry : visibleParts.entrySet()) {
            AvatarPartState p = entry.getValue();
            parts.add(new ShyneNetwork.NetAvatarPart(entry.getKey(), p.visible(), p.posX(), p.posY(), p.posZ(), p.rotX(), p.rotY(), p.rotZ(), p.scaleX(), p.scaleY(), p.scaleZ(), p.positionControlled(), p.rotationControlled(), p.scaleControlled(), p.additiveRotX(), p.additiveRotY(), p.additiveRotZ(), p.additiveRotationControlled(), p.colorArgb(), p.emissive(), p.vanillaParent(), p.vanillaParentControlled(), p.vanillaAttachmentMode()));
        }
        return new ShyneNetwork.NetAvatarSnapshot(
            playerId,
            active.avatarId(),
            active.modelId(),
            active.replaceVanilla(),
            activeManifest == null || activeManifest.onlineSync(),
            includeModel ? toNetModel(activeModel) : null,
            parts,
            new LinkedHashMap<>(active.syncPolicy().filterVanillaVisibility(active.vanillaVisibility())),
            new LinkedHashMap<>(active.syncPolicy().filterSyncedVars(active.syncedVars())),
            active.currentAnimation(),
            active.currentAnimation().isBlank() ? 0L : AvatarAnimationClock.encodeAge(snapshotNowMillis, active.currentAnimationStartedAtMillis()),
            active.animationLayers().values().stream().map(layer -> new ShyneNetwork.NetAvatarAnimation(
                layer.name(), AvatarAnimationClock.encodeAge(snapshotNowMillis, layer.startedAtMillis()), layer.lengthSeconds(), layer.looping(), layer.speed(), layer.weight(), layer.priority(),
                layer.fadeInTicks(), layer.fadeOutTicks(), layer.mask(), layer.additive(), AvatarAnimationClock.encodeOptionalAge(snapshotNowMillis, layer.stoppingAtMillis())
            )).toList(),
            Map.copyOf(active.animationParameters()),
            active.nameplateText(),
            active.nameplateVisible()
        );
    }

    private static ShyneNetwork.NetModelDefinition toNetModel(BbModelDefinition m) {
        List<ShyneNetwork.NetTextureDefinition> textures = new ArrayList<>();
        byte[] outfitTexture = active == null ? new byte[0] : active.selectedOutfitTexture();
        for (int i = 0; i < m.textures().size(); i++) {
            ShyneNetwork.NetTextureDefinition texture = i == 0 && outfitTexture.length > 0
                ? BbModelTextures.outfitTexture(m, outfitTexture)
                : ShyneNetwork.toNetTexture(m, m.textures().get(i));
            if (texture == null) texture = ShyneNetwork.toNetTexture(m, m.textures().get(i));
            if (texture != null) textures.add(texture);
        }
        return new ShyneNetwork.NetModelDefinition(
            m.modelId(), m.sourceModId(), m.displayName(), m.formatVersion(), m.textureWidth(), m.textureHeight(), m.primaryTextureRelativePath(),
            List.copyOf(textures),
            m.bones().stream().map(b -> new ShyneNetwork.NetBoneDefinition(b.uuid(), b.name(), b.parentName(), b.parentUuid(), b.parentType(), b.role(), b.tags(), b.physicsPreset(), b.cubeCount(), b.pivotX(), b.pivotY(), b.pivotZ(), b.rotationX(), b.rotationY(), b.rotationZ(), b.visible(), b.childBoneUuids())).toList(),
            m.cubes().stream().map(c -> new ShyneNetwork.NetCubeDefinition(c.name(), c.parentBoneUuid(), c.fromX(), c.fromY(), c.fromZ(), c.toX(), c.toY(), c.toZ(), c.originX(), c.originY(), c.originZ(), c.rotationX(), c.rotationY(), c.rotationZ(), c.inflate(),
                c.faces().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> new ShyneNetwork.NetFaceUvDefinition(e.getValue().u1(), e.getValue().v1(), e.getValue().u2(), e.getValue().v2(), e.getValue().rotation(), e.getValue().textureIndex(), e.getValue().enabled()))),
                c.textureIndex(), c.mirror(), c.visible())).toList(),
            m.meshes().stream().map(ShyneNetwork.NetMeshDefinition::from).toList(),
            m.animations().stream().map(a -> new ShyneNetwork.NetAnimationDefinition(a.name(), a.lengthSeconds(), a.looping(), a.animatorCount(),
                a.boneAnimations().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> {
                    var v = e.getValue();
                    return new ShyneNetwork.NetBoneAnimation(v.boneUuid(), toNetKeys(v.rotation()), toNetKeys(v.position()), toNetKeys(v.scale()), v.rotationGlobal(), v.quaternionInterpolation());
                })), a.affectedBones())).toList()
        );
    }

    private static List<ShyneNetwork.NetKeyframe> toNetKeys(List<seashyne.shynecore.model.BbKeyframe> keys) {
        return keys.stream().map(k -> new ShyneNetwork.NetKeyframe(k.time(), k.pre(), k.post(), k.easing(), k.bezier())).toList();
    }
}

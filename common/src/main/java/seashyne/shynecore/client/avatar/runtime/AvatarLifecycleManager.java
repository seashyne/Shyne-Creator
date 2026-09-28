package seashyne.shynecore.client.avatar.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.attachment.AttachedModelState;
import seashyne.shynecore.client.avatar.*;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.profiler.AvatarProfiler;
import seashyne.shynecore.client.render.AvatarBoneTransformRegistry;
import seashyne.shynecore.client.render.BbModelTextures;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbModelParser;
import seashyne.shynecore.voice.ShyneMicrophoneState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class AvatarLifecycleManager {
    public static final String VANILLA_SELECTION = "@vanilla";

    private AvatarState active;
    private AvatarManifest activeManifest;
    private BbModelDefinition activeModel;
    private ClientLuaAvatarRuntime script;
    private AvatarAutoAnimationController animationController;
    private AvatarPhysicsController physicsController;
    private Player physicsPlayer;
    private Object physicsLevel;
    private volatile List<AvatarCatalogEntry> catalog = List.of();
    private CompletableFuture<List<AvatarCatalogEntry>> catalogRefresh = CompletableFuture.completedFuture(List.of());
    private long lastCatalogRefreshMillis;
    private ShyneMicrophoneState.Snapshot lastMicrophoneSnapshot;
    private long lastMicrophoneEventNanos;
    private AvatarActivationResult lastActivation = AvatarActivationResult.success("", "Ready");

    public AvatarState active() { return active; }
    public AvatarManifest manifest() { return activeManifest; }
    public BbModelDefinition activeModel() { return activeModel; }
    public ClientLuaAvatarRuntime script() { return script; }
    public AvatarPhysicsController physicsController() { return physicsController; }
    public List<AvatarCatalogEntry> catalog() { return catalog; }
    public AvatarActivationResult lastActivation() { return lastActivation; }

    public void refreshCatalog() {
        catalog = AvatarLoader.discoverCatalog();
        lastCatalogRefreshMillis = System.currentTimeMillis();
    }

    public synchronized CompletableFuture<List<AvatarCatalogEntry>> refreshCatalogAsync(boolean force) {
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

    public AvatarCatalogEntry findCatalogEntry(String avatarId) {
        for (AvatarCatalogEntry entry : catalog) {
            if (entry.id().equalsIgnoreCase(avatarId) || entry.name().equalsIgnoreCase(avatarId)) return entry;
        }
        return null;
    }

    public void activateFirstAvailable(Minecraft client, AvatarCloudDelegate cloudDelegate, AvatarSnapshotSync snapshotSync, AvatarNetworkSender networkSender) {
        if (cloudDelegate != null && cloudDelegate.restoreSelectedPublic(client)) return;
        refreshCatalog();
        String preferred = ShyneClientSettings.selectedAvatarId;
        if (VANILLA_SELECTION.equals(preferred)) {
            deactivate(client, snapshotSync, networkSender, cloudDelegate);
            return;
        }
        if (preferred != null && !preferred.isBlank() && switchAvatar(preferred, client, snapshotSync, networkSender, cloudDelegate).success()) return;
        for (AvatarCatalogEntry entry : catalog) {
            if (entry.valid()) {
                switchAvatar(entry.id(), client, snapshotSync, networkSender, cloudDelegate);
                return;
            }
        }
        lastActivation = AvatarActivationResult.failure("", catalog.isEmpty() ? "No avatars installed" : "No valid avatars found");
    }

    public AvatarActivationResult switchAvatar(String avatarId, Minecraft client, AvatarSnapshotSync snapshotSync, AvatarNetworkSender networkSender, AvatarCloudDelegate cloudDelegate) {
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
                    return activate(entry.root(), client, snapshotSync, networkSender, cloudDelegate);
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

    public AvatarActivationResult switchAvatar(AvatarCatalogEntry entry, Minecraft client, AvatarSnapshotSync snapshotSync, AvatarNetworkSender networkSender, AvatarCloudDelegate cloudDelegate) {
        if (entry == null) {
            lastActivation = AvatarActivationResult.failure("", "Avatar is required");
            return lastActivation;
        }
        if (!entry.valid()) {
            lastActivation = AvatarActivationResult.failure(entry.id(), entry.problem());
            return lastActivation;
        }
        try {
            return activate(entry.root(), client, snapshotSync, networkSender, cloudDelegate);
        } catch (Exception error) {
            ShyneCore.LOGGER.error("[AvatarRuntime] Could not activate avatar {}: {}", entry.id(), error.getMessage(), error);
            lastActivation = AvatarActivationResult.failure(entry.id(), safeMessage(error));
            return lastActivation;
        }
    }

    public boolean reloadActive(Minecraft client, AvatarSnapshotSync snapshotSync, AvatarNetworkSender networkSender, AvatarCloudDelegate cloudDelegate) {
        if (active == null) {
            activateFirstAvailable(client, cloudDelegate, snapshotSync, networkSender);
            return active != null;
        }
        try {
            return activate(active.rootDir(), client, snapshotSync, networkSender, cloudDelegate).success();
        } catch (Exception e) {
            ShyneCore.LOGGER.error("[AvatarRuntime] Could not reload active avatar {}: {}", active.avatarId(), e.getMessage(), e);
            lastActivation = AvatarActivationResult.failure(active.avatarId(), safeMessage(e));
            return false;
        }
    }

    public AvatarActivationResult activate(Path root, Minecraft client, AvatarSnapshotSync snapshotSync, AvatarNetworkSender networkSender, AvatarCloudDelegate cloudDelegate) throws IOException {
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

        AvatarState nextState = new AvatarState(manifest.id(), modelId, root, manifest.replaceVanilla(), manifest.permissions(), grantedPermissions);
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
        cleanupActive(snapshotSync);
        if (previousRoot != null && !previousRoot.toAbsolutePath().normalize().equals(root.toAbsolutePath().normalize())) {
            ShyneSecureAvatar.releaseRuntime(previousRoot);
        }
        activeManifest = manifest;
        activeModel = model;
        active = nextState;
        script = nextScript;
        animationController = nextController;
        physicsController = nextPhysicsController;
        AvatarProfiler.activate(manifest.id(), root, model);
        if (nextScript != null) AvatarProfiler.record(AvatarProfiler.Category.LUA_LOAD, nextScript.loadElapsedNanos());
        snapshotSync.resetAssetsSent();
        ClientAnimationState.putLocalModel(modelId, model);
        AvatarAnimationManager.publishAnimationPlayback(active, System.currentTimeMillis());
        String savedOutfit = ShyneClientSettings.selectedOutfit(manifest.id());
        if (!AvatarOutfitManager.applyOutfit(active, activeModel, savedOutfit, false)) {
            AvatarOutfitManager.applyOutfit(active, activeModel, AvatarOutfitLoader.DEFAULT_OUTFIT, true);
        }
        if (script != null) script.entityInit(client);
        syncAttachment(client);
        active.markSnapshotDirty();
        if (manifest.onlineSync()) snapshotSync.sendLocalSnapshot(client, active, manifest, activeModel, networkSender);
        else if (client.player != null) snapshotSync.requestAvatarClear(client.player.getStringUUID(), networkSender);
        if (!ShyneSecureAvatar.isRuntimePath(root)) {
            if (cloudDelegate != null) cloudDelegate.clearActivePublic();
            ShyneClientSettings.selectedAvatarId = manifest.id();
        }
        ShyneClientSettings.save();
        lastActivation = AvatarActivationResult.success(manifest.id(), "Avatar activated");
        ShyneCore.LOGGER.info("[AvatarRuntime] Activated avatar {} from {}", manifest.id(), root);
        return lastActivation;
    }

    public AvatarActivationResult deactivate(Minecraft client, AvatarSnapshotSync snapshotSync, AvatarNetworkSender networkSender, AvatarCloudDelegate cloudDelegate) {
        String previous = active == null ? "" : active.avatarId();
        Path previousRoot = active == null ? null : active.rootDir();
        cleanupActive(snapshotSync);
        ShyneSecureAvatar.releaseRuntime(previousRoot);
        if (cloudDelegate != null) cloudDelegate.clearActivePublic();
        ShyneClientSettings.selectedAvatarId = VANILLA_SELECTION;
        ShyneClientSettings.save();
        if (client != null && client.player != null) snapshotSync.requestAvatarClear(client.player.getStringUUID(), networkSender);
        lastActivation = AvatarActivationResult.success(previous, "Using vanilla player model");
        ShyneCore.LOGGER.info("[AvatarRuntime] Deactivated avatar; using vanilla player model");
        return lastActivation;
    }

    public void cleanupActive(AvatarSnapshotSync snapshotSync) {
        AvatarState previous = active;
        ClientLuaAvatarRuntime previousScript = script;
        if (previousScript != null) previousScript.dispose();
        seashyne.shynecore.client.avatar.sound.AvatarCustomSoundManager.clear();
        seashyne.shynecore.client.avatar.sound.AvatarAudioStreamManager.clear();
        if (physicsController != null && previous != null) {
            physicsController.clearDynamicNodes(previous);
        }
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
        if (snapshotSync != null) snapshotSync.resetAssetsSent();
        AvatarProfiler.clear();
    }

    public void syncAttachment(Minecraft client) {
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

    public void tickAutomaticAnimations(Player player, BbModelDefinition model) {
        if (animationController == null || player == null || active == null) return;
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
        for (String animation : update.stops()) AvatarAnimationManager.stopAnimation(active, animation);
        for (AvatarAutoAnimationController.Play play : update.plays()) {
            AvatarAnimationManager.playAnimation(
                active, model,
                play.animation(), 1.0, 1.0, play.priority(), play.loop(),
                play.fadeInTicks(), play.fadeOutTicks(), List.of(), play.additive(), play.transitionTicks()
            );
        }
    }

    public void tickNativePhysics(Player player) {
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

    public void dispatchMicrophoneEvent() {
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

    public static void validateModel(BbModelDefinition model, Path avatarRoot) throws IOException {
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

    public static void validateDeclaredTextures(BbModelDefinition model, AvatarManifest manifest) throws IOException {
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

    public static void indexModelPaths(BbModelDefinition model, AvatarState state) {
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

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}

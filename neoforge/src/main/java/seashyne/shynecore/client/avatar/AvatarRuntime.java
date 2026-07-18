package seashyne.shynecore.client.avatar;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.animation.AnimationPlayback;
import seashyne.shynecore.attachment.AttachedModelState;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.render.BbModelTextures;
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

public final class AvatarRuntime {
    private static AvatarState active;
    private static AvatarManifest activeManifest;
    private static BbModelDefinition activeModel;
    private static ClientLuaAvatarRuntime script;
    private static List<AvatarCatalogEntry> catalog = List.of();
    private static boolean initialized;
    private static int snapshotTicks;
    private static boolean snapshotAssetsSent;
    private static long forceModelSnapshotUntilMillis;
    private static ShyneMicrophoneState.Snapshot lastMicrophoneSnapshot;
    private static long lastMicrophoneEventNanos;
    private static AvatarActivationResult lastActivation = AvatarActivationResult.success("", "Ready");
    public static final String VANILLA_SELECTION = "@vanilla";

    private AvatarRuntime() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> {
            snapshotAssetsSent = false;
            if (active != null) active.markSnapshotDirty();
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> snapshotAssetsSent = false);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            ShyneCloudClient.tick(client);
            if (!initialized) {
                initialized = true;
                activateFirstAvailable(client);
            }
            if (script != null) {
                script.tick(client);
                dispatchMicrophoneEvent();
            }
            pruneAnimationLayers();
            renderHook();
            if (active != null && active.isSyncedDirty()) {
                ShyneClientNetworking.sendAvatarVars(active.avatarId(), active.syncPolicy().filterSyncedVars(active.syncedVars()));
                active.clearSyncedDirty();
            }
            if (active != null && activeManifest != null && activeManifest.onlineSync()) {
                snapshotTicks++;
                if (snapshotTicks >= 100 || active.isSnapshotDirty()) {
                    snapshotTicks = 0;
                    sendLocalSnapshot(client);
                    active.clearSnapshotDirty();
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
        if (playerId != null) {
            Boolean remote = ClientAnimationState.getRemoteVanillaVisibility(playerId, key);
            if (remote != null) return remote;
        }
        if (active == null) return true;
        return active.vanillaVisibility().getOrDefault(key, !shouldHideLocalPlayer());
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
    }

    public static boolean activateAvatar(String avatarId, Minecraft client) {
        return switchAvatar(avatarId, client).success();
    }

    public static AvatarActivationResult switchAvatar(String avatarId, Minecraft client) {
        if (avatarId == null || avatarId.isBlank()) {
            lastActivation = AvatarActivationResult.failure("", "Avatar id is required");
            return lastActivation;
        }
        refreshCatalog();
        for (AvatarCatalogEntry entry : catalog) {
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
        Path scriptPath = AvatarLoader.resolveAvatarFile(root, manifest.main());
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
        nextState.setOutfits(AvatarOutfitLoader.discover(root));
        nextState.setFirstPersonMasking(manifest.firstPersonMasking());
        nextState.setLocalCameraOnly(manifest.localCamera());
        nextState.setTextureSyncMode(manifest.textureSyncMode());
        nextState.setSyncedSchemaPath(manifest.syncedSchema());
        nextState.syncPolicy().setAllowRemoteSnapshot(manifest.onlineSync());
        nextState.syncPolicy().setAllowRemoteVars(manifest.onlineSync());
        nextState.bindEntity(client.player != null ? client.player.getUUID() : UUID.randomUUID());
        indexModelPaths(model, nextState);
        ClientLuaAvatarRuntime nextScript = new ClientLuaAvatarRuntime(nextState, scriptPath);
        if (!nextScript.load()) {
            nextScript.dispose();
            throw new IOException("Lua script failed to load; previous avatar was kept");
        }

        Path previousRoot = active == null ? null : active.rootDir();
        cleanupActive();
        if (previousRoot != null && !previousRoot.toAbsolutePath().normalize().equals(root.toAbsolutePath().normalize())) {
            ShyneSecureAvatar.releaseRuntime(previousRoot);
        }
        activeManifest = manifest;
        activeModel = model;
        active = nextState;
        script = nextScript;
        AvatarProfiler.activate(manifest.id(), root, model);
        AvatarProfiler.record(AvatarProfiler.Category.LUA_LOAD, nextScript.loadElapsedNanos());
        snapshotAssetsSent = false;
        snapshotTicks = 0;
        ClientAnimationState.putLocalModel(modelId, model);
        String savedOutfit = ShyneClientSettings.selectedOutfit(manifest.id());
        if (!applyOutfit(savedOutfit, false)) {
            applyOutfit(AvatarOutfitLoader.DEFAULT_OUTFIT, true);
        }
        script.entityInit(client);
        syncAttachment(client);
        active.markSnapshotDirty();
        if (manifest.onlineSync()) sendLocalSnapshot(client);
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
        if (client != null && client.player != null) ShyneClientNetworking.sendAvatarClear(client.player.getStringUUID());
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
        }
        active = null;
        activeManifest = null;
        activeModel = null;
        script = null;
        lastMicrophoneSnapshot = null;
        lastMicrophoneEventNanos = 0L;
        snapshotAssetsSent = false;
        forceModelSnapshotUntilMillis = 0L;
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

    private static void validateModel(BbModelDefinition model, Path avatarRoot) throws IOException {
        if (model.bones().size() > ShyneClientSettings.avatarMaxBones) throw new IOException("model has more than configured avatarMaxBones (" + ShyneClientSettings.avatarMaxBones + ")");
        if (model.cubes().size() > ShyneClientSettings.avatarMaxCubes) throw new IOException("model has more than configured avatarMaxCubes (" + ShyneClientSettings.avatarMaxCubes + ")");
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
        if (manifest.textures() == null || manifest.textures().isEmpty()) return;
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
        Set<String> children = new HashSet<>();
        for (BbBoneDefinition bone : model.bones()) {
            children.addAll(bone.childBoneUuids());
            state.aliasPath(bone.uuid(), "model." + bone.name());
        }
        for (BbBoneDefinition bone : model.bones()) {
            if (!children.contains(bone.uuid())) {
                aliasRecursive(model, state, bone, "model." + bone.name());
            }
        }
        for (var cube : model.cubes()) {
            String cubePath = model.cubePath(cube);
            state.aliasPath(cubePath, cubePath);
            state.aliasPath("models." + cubePath, cubePath);
            state.aliasPath("model." + cube.name(), cubePath);
        }
    }

    private static void aliasRecursive(BbModelDefinition model, AvatarState state, BbBoneDefinition bone, String path) {
        state.aliasPath(path, path);
        state.aliasPath(bone.name(), path);
        state.aliasPath("model." + bone.name(), path);
        state.aliasPath(path.toLowerCase(), path);
        state.aliasPath("models." + path, path);
        for (String childId : bone.childBoneUuids()) {
            BbBoneDefinition child = model.findBoneByUuid(childId);
            if (child != null) aliasRecursive(model, state, child, path + "." + child.name());
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
        if (active == null || active.boundEntityId() == null) return;
        if (animationName == null || animationName.isBlank()) return;
        active.setCurrentAnimation(animationName);
        var definition = activeModel == null ? null : activeModel.findAnimation(animationName);
        double length = definition == null || definition.lengthSeconds() <= 0 ? 2.0 : definition.lengthSeconds();
        boolean looping = definition == null || definition.looping();
        if (loopOverride != null) looping = loopOverride;
        active.animationLayers().put(animationName.toLowerCase(Locale.ROOT), new AvatarAnimationLayer(
            animationName, System.currentTimeMillis(), length, looping,
            Math.max(0.01, Math.min(8.0, speed)), Math.max(0.0, Math.min(1.0, weight)),
            Math.max(-1000, Math.min(1000, priority)),
            Math.max(0, Math.min(1200, fadeInTicks)), Math.max(0, Math.min(1200, fadeOutTicks)),
            mask == null ? List.of() : List.copyOf(mask), additive, 0L
        ));
        active.markAnimationLayersDirty();
        ClientAnimationState.putLocalPlayback(new AnimationPlayback(active.boundEntityId(), "local-player", active.modelId(), animationName, System.currentTimeMillis(), length, looping));
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
        if (animationName.equalsIgnoreCase(active.currentAnimation())) {
            active.clearCurrentAnimation();
            if (active.boundEntityId() != null) ClientAnimationState.removeLocalPlayback(active.boundEntityId());
        }
    }

    private static void pruneAnimationLayers() {
        if (active == null || active.animationLayers().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (active.animationLayers().entrySet().removeIf(entry -> entry.getValue().finished(now))) {
            active.markAnimationLayersDirty();
        }
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

    public static void renderHook() {
        if (script != null) script.render();
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

    private static void sendLocalSnapshot(Minecraft client) {
        boolean includeModel = !snapshotAssetsSent || System.currentTimeMillis() < forceModelSnapshotUntilMillis;
        if (ShyneClientNetworking.sendAvatarSnapshot(buildLocalSnapshot(client, includeModel))) snapshotAssetsSent = true;
    }

    private static ShyneNetwork.NetAvatarSnapshot buildLocalSnapshot(Minecraft client, boolean includeModel) {
        if (active == null || activeModel == null) return null;
        String playerId = client.player == null ? UUID.randomUUID().toString() : client.player.getStringUUID();
        List<ShyneNetwork.NetAvatarPart> parts = new ArrayList<>();
        Map<String, AvatarPartState> visibleParts = active.syncPolicy().filterParts(active.parts());
        for (var entry : visibleParts.entrySet()) {
            AvatarPartState p = entry.getValue();
            parts.add(new ShyneNetwork.NetAvatarPart(entry.getKey(), p.visible(), p.posX(), p.posY(), p.posZ(), p.rotX(), p.rotY(), p.rotZ(), p.scaleX(), p.scaleY(), p.scaleZ(), p.positionControlled(), p.rotationControlled(), p.scaleControlled(), p.colorArgb(), p.emissive()));
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
            active.currentAnimationStartedAtMillis(),
            active.animationLayers().values().stream().map(layer -> new ShyneNetwork.NetAvatarAnimation(
                layer.name(), layer.startedAtMillis(), layer.lengthSeconds(), layer.looping(), layer.speed(), layer.weight(), layer.priority(),
                layer.fadeInTicks(), layer.fadeOutTicks(), layer.mask(), layer.additive(), layer.stoppingAtMillis()
            )).toList(),
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
            m.bones().stream().map(b -> new ShyneNetwork.NetBoneDefinition(b.uuid(), b.name(), b.parentName(), b.parentUuid(), b.cubeCount(), b.pivotX(), b.pivotY(), b.pivotZ(), b.rotationX(), b.rotationY(), b.rotationZ(), b.childBoneUuids())).toList(),
            m.cubes().stream().map(c -> new ShyneNetwork.NetCubeDefinition(c.name(), c.parentBoneUuid(), c.fromX(), c.fromY(), c.fromZ(), c.toX(), c.toY(), c.toZ(), c.originX(), c.originY(), c.originZ(), c.rotationX(), c.rotationY(), c.rotationZ(), c.inflate(),
                c.faces().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> new ShyneNetwork.NetFaceUvDefinition(e.getValue().u1(), e.getValue().v1(), e.getValue().u2(), e.getValue().v2(), e.getValue().rotation(), e.getValue().textureIndex(), e.getValue().enabled()))),
                c.textureIndex(), c.mirror())).toList(),
            m.animations().stream().map(a -> new ShyneNetwork.NetAnimationDefinition(a.name(), a.lengthSeconds(), a.looping(), a.animatorCount(),
                a.boneAnimations().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> {
                    var v = e.getValue();
                    return new ShyneNetwork.NetBoneAnimation(v.boneUuid(), toNetKeys(v.rotation()), toNetKeys(v.position()), toNetKeys(v.scale()));
                })), a.affectedBones())).toList()
        );
    }

    private static List<ShyneNetwork.NetKeyframe> toNetKeys(List<seashyne.shynecore.model.BbKeyframe> keys) {
        return keys.stream().map(k -> new ShyneNetwork.NetKeyframe(k.time(), k.x(), k.y(), k.z(), k.easing())).toList();
    }
}

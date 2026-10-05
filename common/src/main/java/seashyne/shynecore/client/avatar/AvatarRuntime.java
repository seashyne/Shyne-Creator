package seashyne.shynecore.client.avatar;

import net.minecraft.client.Minecraft;
import seashyne.shynecore.client.avatar.runtime.*;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.render.AvatarBoneTransformRegistry;
import seashyne.shynecore.client.render.AvatarRenderContext;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.network.ShyneNetwork;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Facade coordinating local avatar activation, animation layers, outfits,
 * and multiplayer network synchronization.
 */
public final class AvatarRuntime {
    public static final String VANILLA_SELECTION = AvatarLifecycleManager.VANILLA_SELECTION;

    private static final AvatarLifecycleManager lifecycle = new AvatarLifecycleManager();
    private static final AvatarSnapshotSync snapshotSync = new AvatarSnapshotSync();
    private static AvatarNetworkSender networkSender;
    private static AvatarCloudDelegate cloudDelegate;
    private static boolean initialized;

    private AvatarRuntime() {}

    public static void setNetworkSender(AvatarNetworkSender sender) { networkSender = sender; }
    public static void setCloudDelegate(AvatarCloudDelegate delegate) { cloudDelegate = delegate; }

    public static void init() {
        // Platform adapters register networkSender, cloudDelegate, and tick/network hooks
    }

    public static void onJoin() {
        snapshotSync.resetOnConnection(lifecycle.active());
    }

    public static void onDisconnect() {
        snapshotSync.clearOnDisconnect();
        AvatarBoneTransformRegistry.clear();
    }

    public static void tick(Minecraft client) {
        if (client == null || client.player == null) return;
        snapshotSync.applySnapshotAcknowledgements(client.player.getUUID(), lifecycle.active());
        snapshotSync.flushPendingAvatarClear(networkSender);
        if (cloudDelegate != null) cloudDelegate.tick(client);
        if (!initialized) {
            initialized = true;
            activateFirstAvailable(client);
        }
        lifecycle.tickAutomaticAnimations(client.player, lifecycle.activeModel());
        lifecycle.tickNativePhysics(client.player);
        if (lifecycle.script() != null) {
            lifecycle.script().tick(client);
            lifecycle.dispatchMicrophoneEvent();
        }
        AvatarAnimationManager.pruneAnimationLayers(lifecycle.active());
        renderHook();
        snapshotSync.tick(client, lifecycle.active(), lifecycle.manifest(), lifecycle.activeModel(), networkSender);
        lifecycle.syncAttachment(client);
        AvatarFileWatcher.tick(client);
        seashyne.shynecore.client.avatar.sound.AvatarCustomSoundManager.cleanupStoppedSources();
        seashyne.shynecore.client.avatar.sound.AvatarAudioStreamManager.tick();
    }

    // Accessors
    public static AvatarState active() { return lifecycle.active(); }
    public static AvatarManifest manifest() { return lifecycle.manifest(); }
    public static BbModelDefinition activeModel() { return lifecycle.activeModel(); }
    public static ClientLuaAvatarRuntime script() { return lifecycle.script(); }
    public static AvatarPhysicsController physicsController() { return lifecycle.physicsController(); }
    public static String eval(String expression) {
        ClientLuaAvatarRuntime s = lifecycle.script();
        return s != null ? s.evalToString(expression) : "No active avatar script.";
    }
    public static List<AvatarCatalogEntry> catalog() { return lifecycle.catalog(); }
    public static List<AvatarOutfit> outfits() { return AvatarOutfitManager.outfits(lifecycle.active()); }
    public static String selectedOutfitId() { return AvatarOutfitManager.selectedOutfitId(lifecycle.active()); }
    public static AvatarActivationResult lastActivation() { return lifecycle.lastActivation(); }

    public static boolean shouldHideLocalPlayer() { return lifecycle.active() != null && lifecycle.active().replaceVanilla(); }
    public static boolean shouldMaskFirstPerson() { return lifecycle.active() != null && (lifecycle.active().firstPersonMasking() || lifecycle.active().firstPersonArm()); }
    public static boolean firstPersonArmEnabled() { return lifecycle.active() != null && lifecycle.active().firstPersonArm(); }
    public static void setBonePhysics(String path, AvatarPhysicsController.PhysicsConfig config) {
        if (lifecycle.physicsController() != null) {
            lifecycle.physicsController().setBonePhysics(path, config);
        }
    }
    public static void removeBonePhysics(String path) {
        if (lifecycle.physicsController() != null && lifecycle.active() != null) {
            lifecycle.physicsController().removeBonePhysics(path, lifecycle.active());
        }
    }
    public static AvatarPhysicsController.PhysicsConfig getBonePhysics(String path) {
        return lifecycle.physicsController() != null ? lifecycle.physicsController().getBonePhysics(path) : null;
    }
    public static boolean shouldUseLocalOnlyCamera() { return lifecycle.active() != null && lifecycle.active().localCameraOnly(); }
    public static boolean shouldHideHeadInFirstPerson() { return lifecycle.active() != null && lifecycle.active().hideHeadInFirstPerson(); }
    public static float cameraOffsetX() { return lifecycle.active() == null ? 0f : lifecycle.active().cameraOffsetX(); }
    public static float cameraOffsetY() { return lifecycle.active() == null ? 0f : lifecycle.active().cameraOffsetY(); }
    public static float cameraOffsetZ() { return lifecycle.active() == null ? 0f : lifecycle.active().cameraOffsetZ(); }
    public static float cameraRotationX() { return lifecycle.active() == null ? 0f : lifecycle.active().cameraRotationX(); }
    public static float cameraRotationY() { return lifecycle.active() == null ? 0f : lifecycle.active().cameraRotationY(); }
    public static boolean cameraPivotControlled() { return lifecycle.active() != null && lifecycle.active().cameraPivotControlled(); }
    public static float cameraPivotX() { return lifecycle.active() == null ? 0f : lifecycle.active().cameraPivotX(); }
    public static float cameraPivotY() { return lifecycle.active() == null ? 0f : lifecycle.active().cameraPivotY(); }
    public static float cameraPivotZ() { return lifecycle.active() == null ? 0f : lifecycle.active().cameraPivotZ(); }
    public static boolean cameraAbsoluteRotationControlled() {
        return lifecycle.active() != null && lifecycle.active().cameraAbsoluteRotationControlled();
    }
    public static float cameraAbsoluteRotationX() {
        return lifecycle.active() == null ? 0f : lifecycle.active().cameraAbsoluteRotationX();
    }
    public static float cameraAbsoluteRotationY() {
        return lifecycle.active() == null ? 0f : lifecycle.active().cameraAbsoluteRotationY();
    }
    public static float cameraFovMultiplier() {
        return lifecycle.active() == null ? Float.NaN : lifecycle.active().cameraFovMultiplier();
    }
    public static String localNameplateText() { return lifecycle.active() == null ? "" : lifecycle.active().nameplateText(); }
    public static boolean localNameplateVisible() { return lifecycle.active() == null || lifecycle.active().nameplateVisible(); }
    public static AvatarNameplateStyle localNameplateStyle() { return lifecycle.active() == null ? AvatarNameplateStyle.DEFAULT : lifecycle.active().nameplateStyle(); }
    public static String localChatNameplateText() {
        var active = lifecycle.active();
        return active != null && active.permissionAllowed(AvatarPermission.CHAT_NAMEPLATE) ? active.chatNameplateText() : "";
    }
    public static boolean localChatNameplateVisible() {
        var active = lifecycle.active();
        return active == null || active.chatNameplateVisible();
    }
    public static AvatarNameplateStyle localChatNameplateStyle() {
        var active = lifecycle.active();
        return active != null && active.permissionAllowed(AvatarPermission.CHAT_NAMEPLATE) ? active.chatNameplateStyle() : AvatarNameplateStyle.DEFAULT;
    }
    public static String localListNameplateText() {
        var active = lifecycle.active();
        return active != null && active.permissionAllowed(AvatarPermission.TAB_LIST_NAMEPLATE) ? active.listNameplateText() : "";
    }
    public static boolean localListNameplateVisible() {
        var active = lifecycle.active();
        return active == null || active.listNameplateVisible();
    }
    public static AvatarNameplateStyle localListNameplateStyle() {
        var active = lifecycle.active();
        return active != null && active.permissionAllowed(AvatarPermission.TAB_LIST_NAMEPLATE) ? active.listNameplateStyle() : AvatarNameplateStyle.DEFAULT;
    }

    public static void dispatchKeyInput(int key, int scanCode, int action, int modifiers) {
        String type = action == 0 ? "key_release" : action == 2 ? "key_repeat" : "key_press";
        lifecycle.dispatchInputEvent(type, key, scanCode, action, modifiers, 0.0, 0.0, "");
    }

    public static void dispatchCharacterInput(int codePoint, int modifiers) {
        lifecycle.dispatchInputEvent("char_typed", codePoint, 0, 1, modifiers, 0.0, 0.0, new String(Character.toChars(codePoint)));
    }

    public static void dispatchMouseButton(int button, int action, int modifiers) {
        lifecycle.dispatchInputEvent(action == 0 ? "mouse_release" : "mouse_press", button, 0, action, modifiers, 0.0, 0.0, "");
    }

    public static void dispatchMouseScroll(double horizontal, double vertical) {
        lifecycle.dispatchInputEvent("mouse_scroll", 0, 0, 0, 0, horizontal, vertical, "");
    }

    public static void dispatchMouseMove(double xpos, double ypos, double xrel, double yrel) {
        lifecycle.dispatchInputEvent("mouse_move", 0, 0, 0, 0, xpos, ypos, "");
    }

    public static void dispatchItemUse(String itemId, String hand, String action, int particleCount) {
        lifecycle.dispatchItemUseEvent(itemId, hand, action, particleCount);
    }

    public static void dispatchChatReceive(String text, String json, String senderUuid, String senderName) {
        lifecycle.dispatchChatReceiveEvent(text, json, senderUuid, senderName);
    }

    public static void dispatchEntityDamage(float amount, String sourceType, String attackerId, boolean isLocalPlayer) {
        lifecycle.dispatchEntityDamageEvent(amount, sourceType, attackerId, isLocalPlayer);
    }

    public static void dispatchTotemPop(String entityId, boolean isLocalPlayer) {
        lifecycle.dispatchTotemPopEvent(entityId, isLocalPlayer);
    }

    public static void channelPacket(String senderUuid, String channel, String payloadJson) {
        lifecycle.channelPacket(senderUuid, channel, payloadJson);
    }

    public static boolean isVanillaPartVisible(String key) { return isVanillaPartVisible(null, key); }
    public static boolean isVanillaPartVisible(UUID playerId, String key) {
        String canonical = VanillaVisibilityKeys.normalize(key);
        if (!ShyneClientSettings.renderAttachments) return true;
        Minecraft client = Minecraft.getInstance();
        UUID localId = client.player == null ? null : client.player.getUUID();
        AvatarState active = lifecycle.active();
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
        if (VanillaVisibilityKeys.isPlayerBodyPart(key)
            && !VanillaVisibilityKeys.isVisible(visibility, replaceVanilla, VanillaVisibilityKeys.PLAYER)) return false;
        return VanillaVisibilityKeys.isVisible(visibility, replaceVanilla, key);
    }

    // Catalog & Activation
    public static void activateFirstAvailable(Minecraft client) {
        lifecycle.activateFirstAvailable(client, cloudDelegate, snapshotSync, networkSender);
    }

    public static void refreshCatalog() {
        lifecycle.refreshCatalog();
    }

    public static CompletableFuture<List<AvatarCatalogEntry>> refreshCatalogAsync() {
        return lifecycle.refreshCatalogAsync(false);
    }

    public static CompletableFuture<List<AvatarCatalogEntry>> refreshCatalogAsync(boolean force) {
        return lifecycle.refreshCatalogAsync(force);
    }

    public static boolean activateAvatar(String avatarId, Minecraft client) {
        return switchAvatar(avatarId, client).success();
    }

    public static AvatarActivationResult switchAvatar(String avatarId, Minecraft client) {
        return lifecycle.switchAvatar(avatarId, client, snapshotSync, networkSender, cloudDelegate);
    }

    public static AvatarActivationResult switchAvatar(AvatarCatalogEntry entry, Minecraft client) {
        return lifecycle.switchAvatar(entry, client, snapshotSync, networkSender, cloudDelegate);
    }

    public static boolean reloadActive(Minecraft client) {
        return lifecycle.reloadActive(client, snapshotSync, networkSender, cloudDelegate);
    }

    public static AvatarActivationResult activate(Path root, Minecraft client) throws IOException {
        return lifecycle.activate(root, client, snapshotSync, networkSender, cloudDelegate);
    }

    public static AvatarActivationResult deactivate(Minecraft client) {
        return lifecycle.deactivate(client, snapshotSync, networkSender, cloudDelegate);
    }

    // Outfits
    public static boolean selectOutfit(String outfitId, Minecraft client) {
        return AvatarOutfitManager.selectOutfit(lifecycle.active(), lifecycle.activeModel(), outfitId, client, () -> {
            snapshotSync.scheduleForceModelSnapshot(1_500L);
            if (lifecycle.active() != null) lifecycle.active().markSnapshotDirty();
            if (client != null && lifecycle.manifest() != null && lifecycle.manifest().onlineSync()) {
                snapshotSync.sendLocalSnapshot(client, lifecycle.active(), lifecycle.manifest(), lifecycle.activeModel(), networkSender);
            }
        });
    }

    public static void refreshOutfits() {
        AvatarOutfitManager.refreshOutfits(lifecycle.active(), lifecycle.activeModel(), () -> {
            snapshotSync.scheduleForceModelSnapshot(1_500L);
            if (lifecycle.active() != null) lifecycle.active().markSnapshotDirty();
            if (lifecycle.manifest() != null && lifecycle.manifest().onlineSync()) {
                snapshotSync.sendLocalSnapshot(Minecraft.getInstance(), lifecycle.active(), lifecycle.manifest(), lifecycle.activeModel(), networkSender);
            }
        });
    }

    public static void syncAttachment(Minecraft client) {
        lifecycle.syncAttachment(client);
    }

    // Animation & Emotes
    public static void playAnimation(String animationName) {
        AvatarAnimationManager.playAnimation(lifecycle.active(), lifecycle.activeModel(), animationName);
    }

    public static void playAnimation(String animationName, double speed, double weight, int priority, Boolean loopOverride) {
        AvatarAnimationManager.playAnimation(lifecycle.active(), lifecycle.activeModel(), animationName, speed, weight, priority, loopOverride);
    }

    public static void playAnimation(String animationName, double speed, double weight, int priority, Boolean loopOverride,
                                     int fadeInTicks, int fadeOutTicks, List<String> mask, boolean additive) {
        AvatarAnimationManager.playAnimation(lifecycle.active(), lifecycle.activeModel(), animationName, speed, weight, priority, loopOverride, fadeInTicks, fadeOutTicks, mask, additive);
    }

    public static void playAnimation(String animationName, double speed, double weight, int priority, Boolean loopOverride,
                                     int fadeInTicks, int fadeOutTicks, List<String> mask, boolean additive, int transitionTicks) {
        AvatarAnimationManager.playAnimation(lifecycle.active(), lifecycle.activeModel(), animationName, speed, weight, priority, loopOverride, fadeInTicks, fadeOutTicks, mask, additive, transitionTicks);
    }

    public static boolean isAnimationPlaying(String animationName) {
        return AvatarAnimationManager.isAnimationPlaying(lifecycle.active(), animationName);
    }

    public static List<AvatarAnimationLayer> animationLayers(UUID entityId) {
        return AvatarAnimationManager.animationLayers(lifecycle.active(), entityId);
    }

    public static void stopAnimation(String animationName) {
        AvatarAnimationManager.stopAnimation(lifecycle.active(), animationName);
    }

    public static void stopAnimation() {
        AvatarAnimationManager.stopAll(lifecycle.active());
    }

    public static boolean playEmote(String emoteId) {
        return AvatarAnimationManager.playEmote(lifecycle.active(), lifecycle.activeModel(), emoteId);
    }

    public static boolean triggerAnimationGraph(String trigger) {
        return AvatarAnimationManager.triggerAnimationGraph(lifecycle.active(), lifecycle.activeModel(), trigger);
    }

    // Render hooks
    public static void renderHook() {
        syncActivePartStates();
    }

    public static void renderFrameStart() {
        Minecraft client = Minecraft.getInstance();
        ClientLuaAvatarRuntime script = lifecycle.script();
        if (script == null) return;
        float delta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        String context = AvatarRenderContext.current(client);
        if (client.level != null) script.worldRender(delta);
        script.render(delta, context);
        syncActivePartStates();
    }

    public static void renderFrameEnd() {
        Minecraft client = Minecraft.getInstance();
        ClientLuaAvatarRuntime script = lifecycle.script();
        if (script == null) return;
        float delta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        String context = AvatarRenderContext.current(client);
        script.postRender(delta, context);
        if (client.level != null) script.postWorldRender(delta);
    }

    private static void syncActivePartStates() {
        AvatarState active = lifecycle.active();
        BbModelDefinition activeModel = lifecycle.activeModel();
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
        return snapshotSync.buildLocalSnapshot(client, lifecycle.active(), lifecycle.manifest(), lifecycle.activeModel(), true);
    }
}

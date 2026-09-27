package seashyne.shynecore.client.avatar.runtime;

import net.minecraft.client.Minecraft;
import seashyne.shynecore.avatar.AvatarAnimationClock;
import seashyne.shynecore.client.avatar.AvatarAnimationLayer;
import seashyne.shynecore.client.avatar.AvatarManifest;
import seashyne.shynecore.client.avatar.AvatarPartState;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.render.BbModelTextures;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbKeyframe;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.network.ShyneNetwork;

import java.util.*;

public final class AvatarSnapshotSync {
    private static final int POSE_SNAPSHOT_INTERVAL_TICKS = 2;
    private static final long MIN_SNAPSHOT_SEND_INTERVAL_NANOS = 80_000_000L;

    private int snapshotTicks;
    private boolean snapshotAssetsSent;
    private long forceModelSnapshotUntilMillis;
    private long lastSnapshotAttemptAtNanos;
    private long nextSnapshotTransportRevision;
    private final NavigableMap<Long, SentSnapshotRevision> awaitingSnapshotAcks = new TreeMap<>();
    private String pendingAvatarClearPlayerId;
    private long pendingAvatarClearRevision;

    public void resetOnConnection(AvatarState active) {
        snapshotAssetsSent = false;
        lastSnapshotAttemptAtNanos = 0L;
        nextSnapshotTransportRevision = 0L;
        awaitingSnapshotAcks.clear();
        pendingAvatarClearPlayerId = null;
        pendingAvatarClearRevision = 0L;
        if (active != null) active.markSnapshotDirty();
    }

    public void clearOnDisconnect() {
        snapshotAssetsSent = false;
        lastSnapshotAttemptAtNanos = 0L;
        nextSnapshotTransportRevision = 0L;
        awaitingSnapshotAcks.clear();
        pendingAvatarClearPlayerId = null;
        pendingAvatarClearRevision = 0L;
    }

    public void resetAssetsSent() {
        snapshotAssetsSent = false;
        snapshotTicks = 0;
        awaitingSnapshotAcks.clear();
    }

    public void scheduleForceModelSnapshot(long durationMillis) {
        snapshotAssetsSent = false;
        snapshotTicks = 0;
        forceModelSnapshotUntilMillis = System.currentTimeMillis() + durationMillis;
    }

    public void tick(Minecraft client, AvatarState active, AvatarManifest manifest, BbModelDefinition model, AvatarNetworkSender sender) {
        if (active == null || manifest == null || !manifest.onlineSync()) return;
        snapshotTicks++;
        if (snapshotTicks >= 100 || active.isSnapshotDirty()
            || (snapshotTicks >= POSE_SNAPSHOT_INTERVAL_TICKS && active.isPoseDirty())
            || (snapshotTicks >= 5 && active.areAnimationParametersDirty())) {
            if (sendLocalSnapshot(client, active, manifest, model, sender)) {
                snapshotTicks = 0;
            }
        }
    }

    public boolean sendLocalSnapshot(Minecraft client, AvatarState active, AvatarManifest manifest, BbModelDefinition model, AvatarNetworkSender sender) {
        if (active == null || sender == null) return false;
        long nowNanos = System.nanoTime();
        if (lastSnapshotAttemptAtNanos != 0L && nowNanos - lastSnapshotAttemptAtNanos < MIN_SNAPSHOT_SEND_INTERVAL_NANOS) return false;
        long snapshotRevision = active.captureSnapshotRevision();
        long poseRevision = active.capturePoseRevision();
        long animationParametersRevision = active.captureAnimationParametersRevision();
        long syncedRevision = active.captureSyncedRevision();
        boolean includeModel = !snapshotAssetsSent || System.currentTimeMillis() < forceModelSnapshotUntilMillis;
        long transportRevision = nextTransportRevision();
        lastSnapshotAttemptAtNanos = nowNanos;
        boolean sent = sender.sendAvatarSnapshot(buildLocalSnapshot(client, active, manifest, model, includeModel), transportRevision);
        if (sent) {
            awaitingSnapshotAcks.put(transportRevision, new SentSnapshotRevision(
                active, snapshotRevision, poseRevision, animationParametersRevision, syncedRevision, includeModel
            ));
            while (awaitingSnapshotAcks.size() > 64) awaitingSnapshotAcks.pollFirstEntry();
        }
        return sent;
    }

    public void requestAvatarClear(String playerId, AvatarNetworkSender sender) {
        if (playerId == null || playerId.isBlank()) return;
        if (!playerId.equals(pendingAvatarClearPlayerId)) {
            pendingAvatarClearPlayerId = playerId;
            pendingAvatarClearRevision = 0L;
        }
        flushPendingAvatarClear(sender);
    }

    public void flushPendingAvatarClear(AvatarNetworkSender sender) {
        String playerId = pendingAvatarClearPlayerId;
        if (playerId == null || playerId.isBlank() || sender == null) return;
        long nowNanos = System.nanoTime();
        if (lastSnapshotAttemptAtNanos != 0L && nowNanos - lastSnapshotAttemptAtNanos < MIN_SNAPSHOT_SEND_INTERVAL_NANOS) return;
        long transportRevision = nextTransportRevision();
        lastSnapshotAttemptAtNanos = nowNanos;
        if (sender.sendAvatarClear(playerId, transportRevision)
            && pendingAvatarClearRevision == 0L) {
            pendingAvatarClearRevision = transportRevision;
        }
    }

    public void applySnapshotAcknowledgements(UUID playerId, AvatarState active) {
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

    private long nextTransportRevision() {
        if (nextSnapshotTransportRevision == Long.MAX_VALUE) nextSnapshotTransportRevision = 0L;
        return ++nextSnapshotTransportRevision;
    }

    public ShyneNetwork.NetAvatarSnapshot buildLocalSnapshot(Minecraft client, AvatarState active, AvatarManifest manifest, BbModelDefinition model, boolean includeModel) {
        if (active == null || model == null) return null;
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
            manifest == null || manifest.onlineSync(),
            includeModel ? toNetModel(active, model) : null,
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

    private static ShyneNetwork.NetModelDefinition toNetModel(AvatarState active, BbModelDefinition m) {
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

    private static List<ShyneNetwork.NetKeyframe> toNetKeys(List<BbKeyframe> keys) {
        return keys.stream().map(k -> new ShyneNetwork.NetKeyframe(k.time(), k.pre(), k.post(), k.easing(), k.bezier())).toList();
    }

    private record SentSnapshotRevision(
        AvatarState state,
        long snapshotRevision,
        long poseRevision,
        long animationParametersRevision,
        long syncedRevision,
        boolean includedModel
    ) {}
}

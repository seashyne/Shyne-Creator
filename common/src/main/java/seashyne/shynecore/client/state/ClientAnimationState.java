package seashyne.shynecore.client.state;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.animation.AnimationPlayback;
import seashyne.shynecore.attachment.AttachedModelState;
import seashyne.shynecore.avatar.AvatarAnimationClock;
import seashyne.shynecore.client.avatar.AvatarPartState;
import seashyne.shynecore.client.avatar.AvatarAnimationLayer;
import seashyne.shynecore.client.avatar.RemoteAvatarState;
import seashyne.shynecore.client.avatar.RemoteAvatarResourceBudget;
import seashyne.shynecore.client.avatar.VanillaVisibilityKeys;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.render.BbModelTextures;
import seashyne.shynecore.equipment.EquipmentLoadout;
import seashyne.shynecore.equipment.WeaponDefinition;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.network.ShyneNetwork;
import seashyne.shynecore.power.PowerState;
import seashyne.shynecore.profile.PlayerProfile;
import seashyne.shynecore.skill.SkillCastType;
import seashyne.shynecore.skill.SkillDefinition;
import seashyne.shynecore.skill.SkillRequirement;
import seashyne.shynecore.skill.SkillSlot;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of server-authoritative Shyne state.
 *
 * <p>Network handlers replace concurrent snapshots while render hooks perform
 * lock-free reads. Local previews use the same maps as multiplayer state.</p>
 */
public final class ClientAnimationState {
    private static final long REMOTE_POSE_INTERPOLATION_MS = 100L;
    private static final Gson GSON = new Gson();
    private static final Type MODEL_SYNC_TYPE = new TypeToken<ShyneNetwork.ModelSyncPayload>(){}.getType();
    private static final Type ACTIVE_SYNC_TYPE = new TypeToken<ShyneNetwork.ActiveSyncPayload>(){}.getType();
    private static final Type ATTACHMENT_SYNC_TYPE = new TypeToken<ShyneNetwork.AttachmentSyncPayload>(){}.getType();
    private static final Type POWER_SYNC_TYPE = new TypeToken<ShyneNetwork.PowerSyncPayload>(){}.getType();
    private static final Type SKILL_SYNC_TYPE = new TypeToken<ShyneNetwork.SkillSyncPayload>(){}.getType();
    private static final Type PROFILE_SYNC_TYPE = new TypeToken<ShyneNetwork.ProfileSyncPayload>(){}.getType();
    private static final Type WEAPON_SYNC_TYPE = new TypeToken<ShyneNetwork.WeaponSyncPayload>(){}.getType();
    private static final Type LOADOUT_SYNC_TYPE = new TypeToken<ShyneNetwork.LoadoutSyncPayload>(){}.getType();
    private static final Type PLAYBACK_TYPE = new TypeToken<ShyneNetwork.NetPlayback>(){}.getType();
    private static final Type STOP_TYPE = new TypeToken<ShyneNetwork.StopPayload>(){}.getType();
    private static final Type AVATAR_VAR_SYNC_TYPE = new TypeToken<ShyneNetwork.AvatarVarSyncPayload>(){}.getType();
    private static final Type AVATAR_SNAPSHOT_SYNC_TYPE = new TypeToken<ShyneNetwork.AvatarSnapshotSyncPayload>(){}.getType();

    private static final Map<String, BbModelDefinition> SERVER_MODELS = new ConcurrentHashMap<>();
    private static final Map<String, BbModelDefinition> LOCAL_AVATAR_MODELS = new ConcurrentHashMap<>();
    private static final Map<String, BbModelDefinition> REMOTE_AVATAR_MODELS = new ConcurrentHashMap<>();
    private static final Map<UUID, AnimationPlayback> SERVER_PLAYBACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, AnimationPlayback> AVATAR_PLAYBACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, AttachedModelState> SERVER_ATTACHMENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, AttachedModelState> AVATAR_ATTACHMENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, PowerState> POWER_STATES = new ConcurrentHashMap<>();
    private static final Map<String, SkillDefinition> SKILLS = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerProfile> PROFILES = new ConcurrentHashMap<>();
    private static final Map<String, WeaponDefinition> WEAPONS = new ConcurrentHashMap<>();
    private static final Map<UUID, EquipmentLoadout> LOADOUTS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, AvatarPartState>> AVATAR_PARTS = new ConcurrentHashMap<>();
    private static final Map<RemotePartKey, RemotePartTransition> REMOTE_PART_TRANSITIONS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> AVATAR_SYNCED_VARS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Boolean>> REMOTE_VANILLA_VISIBILITY = new ConcurrentHashMap<>();
    private static final Map<UUID, RemoteAvatarState> REMOTE_AVATARS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<AvatarAnimationLayer>> REMOTE_ANIMATION_LAYERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Double>> REMOTE_ANIMATION_PARAMETERS = new ConcurrentHashMap<>();
    private static final Map<UUID, RemoteNameplate> REMOTE_NAMEPLATES = new ConcurrentHashMap<>();
    private static final Map<UUID, RemoteAvatarResourceBudget.Decision> REMOTE_AVATAR_REJECTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, VanillaPartTransform>> VANILLA_TRANSFORMS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LOCAL_SNAPSHOT_ACKS = new ConcurrentHashMap<>();

    private ClientAnimationState() {}

    public static void handleModelSync(String json) {
        ShyneNetwork.ModelSyncPayload payload = GSON.fromJson(json, MODEL_SYNC_TYPE);
        SERVER_MODELS.clear();
        if (payload != null && payload.models() != null) for (ShyneNetwork.NetModelDefinition model : payload.models()) {
            BbModelTextures.installSynced(model);
            SERVER_MODELS.put(model.modelId(), model.toRuntime());
        }
    }
    public static void handleActiveSync(String json) {
        ShyneNetwork.ActiveSyncPayload payload = GSON.fromJson(json, ACTIVE_SYNC_TYPE);
        SERVER_PLAYBACKS.clear();
        if (payload != null && payload.playbacks() != null) for (ShyneNetwork.NetPlayback pb : payload.playbacks()) {
            UUID entityId = UUID.fromString(pb.entityId());
            if (!ShyneClientSettings.isRemotePlayerBlocked(entityId)) SERVER_PLAYBACKS.put(entityId, toRuntime(pb));
        }
    }
    public static void handleAttachmentSync(String json) {
        ShyneNetwork.AttachmentSyncPayload payload = GSON.fromJson(json, ATTACHMENT_SYNC_TYPE);
        SERVER_ATTACHMENTS.clear();
        if (payload != null && payload.attachments() != null) for (ShyneNetwork.NetAttachment attachment : payload.attachments()) {
            UUID entityId = UUID.fromString(attachment.entityId());
            if (!ShyneClientSettings.isRemotePlayerBlocked(entityId)) SERVER_ATTACHMENTS.put(entityId, attachment.toRuntime());
        }
    }
    public static void handlePowerSync(String json) {
        ShyneNetwork.PowerSyncPayload payload = GSON.fromJson(json, POWER_SYNC_TYPE);
        POWER_STATES.clear();
        if (payload != null && payload.states() != null) for (ShyneNetwork.NetPowerState state : payload.states()) POWER_STATES.put(UUID.fromString(state.entityId()), state.toRuntime());
    }
    public static void handleSkillSync(String json) {
        ShyneNetwork.SkillSyncPayload payload = GSON.fromJson(json, SKILL_SYNC_TYPE);
        SKILLS.clear();
        if (payload != null && payload.skills() != null) {
            for (ShyneNetwork.NetSkillDefinition skill : payload.skills()) {
                SKILLS.put(skill.skillId(), new SkillDefinition(
                    skill.skillId(), skill.displayName(), "", SkillCastType.fromString(skill.castType()), SkillSlot.fromString(skill.defaultSlot()),
                    skill.manaCost(), skill.cooldownTicks(), 0, skill.modelId(), skill.animation(), "", new SkillRequirement(0, List.of(), ""),
                    skill.tags() == null ? List.of() : skill.tags(), Map.of()
                ));
            }
        }
    }
    public static void handleProfileSync(String json) {
        ShyneNetwork.ProfileSyncPayload payload = GSON.fromJson(json, PROFILE_SYNC_TYPE);
        PROFILES.clear();
        if (payload != null && payload.profiles() != null) {
            for (ShyneNetwork.NetPlayerProfile profile : payload.profiles()) {
                PROFILES.put(UUID.fromString(profile.playerId()), new PlayerProfile(
                    UUID.fromString(profile.playerId()), profile.playerName(), profile.level(), profile.experience(), profile.statPoints(), profile.skillPoints(),
                    profile.playerClass(), profile.unlockedSkills() == null ? List.of() : profile.unlockedSkills(), profile.equippedSkills() == null ? Map.of() : profile.equippedSkills(),
                    profile.attributes() == null ? Map.of() : profile.attributes(), profile.teamId(), profile.updatedAtMillis()
                ));
            }
        }
    }
    public static void handleWeaponSync(String json) {
        ShyneNetwork.WeaponSyncPayload payload = GSON.fromJson(json, WEAPON_SYNC_TYPE);
        WEAPONS.clear();
        if (payload != null && payload.weapons() != null) {
            for (ShyneNetwork.NetWeaponDefinition weapon : payload.weapons()) {
                WEAPONS.put(weapon.weaponId(), new WeaponDefinition(weapon.weaponId(), weapon.displayName(), weapon.itemId(), weapon.modelId(), weapon.classTag(), weapon.grantedSkills() == null ? List.of() : weapon.grantedSkills(), weapon.statModifiers() == null ? Map.of() : weapon.statModifiers(), Map.of()));
            }
        }
    }
    public static void handleLoadoutSync(String json) {
        ShyneNetwork.LoadoutSyncPayload payload = GSON.fromJson(json, LOADOUT_SYNC_TYPE);
        LOADOUTS.clear();
        if (payload != null && payload.loadouts() != null) {
            for (ShyneNetwork.NetEquipmentLoadout loadout : payload.loadouts()) {
                LOADOUTS.put(UUID.fromString(loadout.entityId()), new EquipmentLoadout(UUID.fromString(loadout.entityId()), loadout.mainHandWeaponId(), loadout.offHandWeaponId(), loadout.slots() == null ? Map.of() : loadout.slots(), loadout.updatedAtMillis()));
            }
        }
    }
    public static void handlePlay(String json) {
        ShyneNetwork.NetPlayback pb = GSON.fromJson(json, PLAYBACK_TYPE);
        if (pb != null) {
            UUID entityId = UUID.fromString(pb.entityId());
            if (!ShyneClientSettings.isRemotePlayerBlocked(entityId)) SERVER_PLAYBACKS.put(entityId, toRuntime(pb));
        }
    }
    public static void handleStop(String json) {
        ShyneNetwork.StopPayload payload = GSON.fromJson(json, STOP_TYPE);
        if (payload != null) SERVER_PLAYBACKS.remove(UUID.fromString(payload.entityId()));
    }
    public static void handleAvatarVarSync(String json) {
        ShyneNetwork.AvatarVarSyncPayload payload;
        try {
            payload = GSON.fromJson(json, AVATAR_VAR_SYNC_TYPE);
        } catch (RuntimeException | StackOverflowError malformed) {
            ShyneCore.LOGGER.warn("[ShyneNetwork] Rejected malformed avatar variable sync: {}", malformed.getMessage());
            return;
        }
        if (payload == null || payload.vars() == null) return;
        for (ShyneNetwork.NetAvatarVars vars : payload.vars()) {
            if (vars == null || vars.playerId() == null) continue;
            UUID playerId;
            try {
                playerId = UUID.fromString(vars.playerId());
            } catch (IllegalArgumentException malformedId) {
                continue;
            }
            if (!ShyneClientSettings.shouldLoadRemoteAvatar(playerId)) {
                AVATAR_SYNCED_VARS.remove(vars.playerId());
                continue;
            }
            Map<String, Object> map = new ConcurrentHashMap<>();
            copyNonNullEntries(vars.values(), map);
            AVATAR_SYNCED_VARS.put(vars.playerId(), map);
            RemoteAvatarState remote = REMOTE_AVATARS.get(playerId);
            if (remote != null) {
                REMOTE_AVATARS.put(remote.playerId(), new RemoteAvatarState(remote.playerId(), remote.avatarId(), remote.modelId(), remote.replaceVanilla(), remote.parts(), remote.vanillaVisibility(), map, remote.currentAnimation(), remote.animationStartedAtMillis()));
            }
        }
    }
    public static void handleAvatarSnapshotSync(String json) {
        ShyneNetwork.AvatarSnapshotSyncPayload payload;
        try {
            payload = GSON.fromJson(json, AVATAR_SNAPSHOT_SYNC_TYPE);
        } catch (RuntimeException | StackOverflowError malformed) {
            ShyneCore.LOGGER.warn("[ShyneNetwork] Rejected malformed avatar snapshot sync: {}", malformed.getMessage());
            return;
        }
        if (payload == null || payload.avatars() == null) return;
        Minecraft client = Minecraft.getInstance();
        UUID localPlayerId = client.player == null ? null : client.player.getUUID();
        for (ShyneNetwork.NetAvatarSnapshot snapshot : payload.avatars()) {
            try {
            if (snapshot == null || snapshot.playerId() == null) continue;
            UUID playerId = UUID.fromString(snapshot.playerId());
            // The local runtime is authoritative for its own frame. Applying the
            // server echo here can rewind native physics by one network interval.
            if (playerId.equals(localPlayerId)) {
                if (payload.revision() > 0L) LOCAL_SNAPSHOT_ACKS.merge(playerId, payload.revision(), Math::max);
                continue;
            }
            if (!ShyneClientSettings.shouldLoadRemoteAvatar(playerId)) {
                if (ShyneClientSettings.isRemotePlayerBlocked(playerId)) removeBlockedRemotePlayer(playerId);
                else removeRemoteAvatar(playerId);
                continue;
            }
            if (!snapshot.onlineSync()) {
                removeRemoteAvatar(playerId);
                continue;
            }
            if (snapshot.model() != null) {
                RemoteAvatarResourceBudget.Decision budget = RemoteAvatarResourceBudget.evaluate(remoteModelMetrics(snapshot.model()));
                if (!budget.allowed()) {
                    rejectRemoteAvatar(playerId, snapshot.modelId(), budget);
                    continue;
                }
                REMOTE_AVATAR_REJECTIONS.remove(playerId);
                BbModelTextures.installSynced(snapshot.model());
                REMOTE_AVATAR_MODELS.put(snapshot.modelId(), snapshot.model().toRuntime());
            } else if (REMOTE_AVATAR_REJECTIONS.containsKey(playerId)) {
                // A lightweight follow-up snapshot cannot make a previously
                // rejected model safe. Wait for a full model (for example after
                // changing the preset and requesting a resync) before retrying.
                continue;
            }
            RemoteAvatarState previousRemote = REMOTE_AVATARS.get(playerId);
            if (previousRemote != null && !previousRemote.modelId().equals(snapshot.modelId())) {
                clearAvatarPartStates(playerId, previousRemote.modelId());
                removeRemoteModelIfUnused(previousRemote.modelId(), playerId);
            }
            long receivedAtMillis = System.currentTimeMillis();
            String currentAnimation = snapshot.currentAnimation() == null ? "" : snapshot.currentAnimation();
            long animationStartedAtMillis = currentAnimation.isBlank()
                ? 0L
                : AvatarAnimationClock.decodeAge(receivedAtMillis, snapshot.animationStartedAtMillis());
            Map<String, AvatarPartState> parts = new ConcurrentHashMap<>();
            if (snapshot.parts() != null) {
                for (ShyneNetwork.NetAvatarPart part : snapshot.parts()) {
                    AvatarPartState state = new AvatarPartState();
                    state.setVisible(part.visible());
                    if (part.positionControlled() || nonZero(part.posX()) || nonZero(part.posY()) || nonZero(part.posZ())) {
                        state.setPosition(part.posX(), part.posY(), part.posZ());
                    }
                    if (part.rotationControlled() || nonZero(part.rotX()) || nonZero(part.rotY()) || nonZero(part.rotZ())) {
                        state.setRotation(part.rotX(), part.rotY(), part.rotZ());
                    }
                    if (part.scaleControlled() || nonOne(part.scaleX()) || nonOne(part.scaleY()) || nonOne(part.scaleZ())) {
                        state.setScale(part.scaleX(), part.scaleY(), part.scaleZ());
                    }
                    if (part.additiveRotationControlled() || nonZero(part.additiveRotX()) || nonZero(part.additiveRotY()) || nonZero(part.additiveRotZ())) {
                        state.setAdditiveRotation(part.additiveRotX(), part.additiveRotY(), part.additiveRotZ());
                    }
                    if (part.vanillaParentControlled()) state.setVanillaParent(part.vanillaParent(), part.vanillaAttachmentMode());
                    if (part.colorArgb() != 0xFFFFFFFF || part.emissive()) state.setRenderState(part.colorArgb(), part.emissive());
                    parts.put(part.path(), state);
                    setAvatarPartState(playerId, snapshot.modelId(), part.path(), state);
                }
            }
            Map<String, AvatarPartState> mirroredParts = AVATAR_PARTS.get(playerId + "|" + snapshot.modelId());
            if (mirroredParts != null) mirroredParts.keySet().removeIf(path -> !parts.containsKey(path));
            REMOTE_PART_TRANSITIONS.keySet().removeIf(key -> key.entityId.equals(playerId)
                && key.modelId.equals(snapshot.modelId()) && !parts.containsKey(key.path));
            AVATAR_ATTACHMENTS.put(playerId, new AttachedModelState(playerId, snapshot.avatarId(), snapshot.modelId(), 0f, 0f, 0f, 1f, "", true));
            List<AvatarAnimationLayer> animationLayers = snapshot.animationLayers() == null ? List.of() : snapshot.animationLayers().stream()
                .map(layer -> new AvatarAnimationLayer(
                    layer.name(), AvatarAnimationClock.decodeAge(receivedAtMillis, layer.startedAtMillis()), layer.lengthSeconds(), layer.looping(), layer.speed(), layer.weight(), layer.priority(),
                    layer.fadeInTicks(), layer.fadeOutTicks(), layer.mask(), layer.additive(), AvatarAnimationClock.decodeOptionalAge(receivedAtMillis, layer.stoppingAtMillis())
                ))
                .sorted(Comparator.comparingInt(AvatarAnimationLayer::priority))
                .toList();
            REMOTE_ANIMATION_LAYERS.put(playerId, animationLayers);
            // Layer snapshots carry the exact loop/length metadata. The legacy
            // playback fallback has neither, so never let it outlive a one-shot
            // layer such as Blink. It is used only for old snapshots with no layers.
            if (!animationLayers.isEmpty()) {
                AVATAR_PLAYBACKS.remove(playerId);
            } else if (!currentAnimation.isBlank()) {
                AVATAR_PLAYBACKS.put(playerId, new AnimationPlayback(playerId, snapshot.avatarId(), snapshot.modelId(), currentAnimation, animationStartedAtMillis, 2.0, true));
            } else {
                AVATAR_PLAYBACKS.remove(playerId);
            }
            Map<String, Double> animationParameters = new HashMap<>();
            copyNonNullEntries(snapshot.animationParameters(), animationParameters);
            REMOTE_ANIMATION_PARAMETERS.put(playerId, Map.copyOf(animationParameters));
            REMOTE_NAMEPLATES.put(playerId, new RemoteNameplate(snapshot.nameplateText() == null ? "" : snapshot.nameplateText(), snapshot.nameplateVisible()));
            Map<String, Boolean> visibility = new ConcurrentHashMap<>();
            copyNonNullEntries(snapshot.vanillaVisibility(), visibility);
            REMOTE_VANILLA_VISIBILITY.put(playerId, visibility);
            Map<String, Object> syncedVars = new ConcurrentHashMap<>();
            copyNonNullEntries(snapshot.syncedVars(), syncedVars);
            AVATAR_SYNCED_VARS.put(snapshot.playerId(), syncedVars);
            REMOTE_AVATARS.put(playerId, new RemoteAvatarState(playerId, snapshot.avatarId(), snapshot.modelId(), snapshot.replaceVanilla(), parts, visibility, syncedVars, currentAnimation, animationStartedAtMillis));
            } catch (RuntimeException | StackOverflowError malformedSnapshot) {
                ShyneCore.LOGGER.warn("[ShyneNetwork] Rejected malformed peer avatar snapshot: {}", malformedSnapshot.getMessage());
            }
        }
    }

    public static void putLocalModel(String modelId, BbModelDefinition model) { LOCAL_AVATAR_MODELS.put(modelId, model); }
    public static void putLocalAttachment(AttachedModelState state) { AVATAR_ATTACHMENTS.put(state.entityId(), state); }
    public static void putLocalPlayback(AnimationPlayback playback) { AVATAR_PLAYBACKS.put(playback.entityId(), playback); }
    public static void putVanillaTransforms(UUID entityId, Map<String, VanillaPartTransform> transforms) {
        if (entityId == null) return;
        VANILLA_TRANSFORMS.put(entityId, transforms == null ? Map.of() : Map.copyOf(transforms));
    }
    public static VanillaPartTransform getVanillaTransform(UUID entityId, String part) {
        if (entityId == null || part == null) return VanillaPartTransform.IDENTITY;
        Map<String, VanillaPartTransform> transforms = VANILLA_TRANSFORMS.getOrDefault(entityId, Map.of());
        String requested = normalizeVanillaPart(part);
        VanillaPartTransform direct = transforms.get(requested);
        if (direct != null) return direct;
        return transforms.getOrDefault(vanillaTransformAlias(requested), VanillaPartTransform.IDENTITY);
    }
    public static void removeLocalModel(String modelId) { if (modelId != null) LOCAL_AVATAR_MODELS.remove(modelId); }
    public static void removeLocalAttachment(UUID entityId) { if (entityId != null) AVATAR_ATTACHMENTS.remove(entityId); }
    public static void removeLocalPlayback(UUID entityId) { AVATAR_PLAYBACKS.remove(entityId); }
    public static void clearAvatarPartStates(UUID entityId, String modelId) {
        if (entityId != null && modelId != null) {
            AVATAR_PARTS.remove(entityId + "|" + modelId);
            REMOTE_PART_TRANSITIONS.keySet().removeIf(key -> key.entityId.equals(entityId) && key.modelId.equals(modelId));
        }
    }
    public static void removeRemoteAvatar(UUID playerId) {
        if (playerId == null) return;
        REMOTE_AVATAR_REJECTIONS.remove(playerId);
        RemoteAvatarState remote = REMOTE_AVATARS.remove(playerId);
        AVATAR_ATTACHMENTS.remove(playerId);
        AVATAR_PLAYBACKS.remove(playerId);
        REMOTE_VANILLA_VISIBILITY.remove(playerId);
        REMOTE_ANIMATION_LAYERS.remove(playerId);
        REMOTE_ANIMATION_PARAMETERS.remove(playerId);
        REMOTE_NAMEPLATES.remove(playerId);
        VANILLA_TRANSFORMS.remove(playerId);
        AVATAR_SYNCED_VARS.remove(playerId.toString());
        REMOTE_PART_TRANSITIONS.keySet().removeIf(key -> key.entityId.equals(playerId));
        if (remote != null) {
            AVATAR_PARTS.remove(playerId + "|" + remote.modelId());
            removeRemoteModelIfUnused(remote.modelId(), playerId);
        }
    }
    public static void removeBlockedRemotePlayer(UUID playerId) {
        removeRemoteAvatar(playerId);
        if (playerId != null) {
            SERVER_ATTACHMENTS.remove(playerId);
            SERVER_PLAYBACKS.remove(playerId);
        }
    }

    private static void removeRemoteModelIfUnused(String modelId, UUID excludedPlayerId) {
        if (modelId == null || modelId.isBlank()) return;
        boolean used = REMOTE_AVATARS.values().stream().anyMatch(other ->
            !other.playerId().equals(excludedPlayerId) && modelId.equals(other.modelId())
        );
        if (!used) {
            REMOTE_AVATAR_MODELS.remove(modelId);
            BbModelTextures.clearSyncedModel(modelId);
        }
    }
    private static void rejectRemoteAvatar(UUID playerId, String modelId, RemoteAvatarResourceBudget.Decision decision) {
        removeRemoteAvatar(playerId);
        REMOTE_AVATAR_REJECTIONS.put(playerId, decision);
        ShyneCore.LOGGER.warn(
            "[AvatarBudget] Using vanilla fallback for {} model {}: {}={} exceeds {} (preset={})",
            playerId, modelId, decision.resourceId(), decision.actual(), decision.limit(), decision.preset().id()
        );
    }

    private static RemoteAvatarResourceBudget.Metrics remoteModelMetrics(ShyneNetwork.NetModelDefinition model) {
        if (model == null) return RemoteAvatarResourceBudget.Metrics.EMPTY;

        long cubes = sizeOf(model.cubes());
        long triangles = saturatedMultiply(cubes, 12L);
        long meshVertices = 0L;
        if (model.meshes() != null) {
            for (ShyneNetwork.NetMeshDefinition mesh : model.meshes()) {
                if (mesh == null) continue;
                meshVertices = saturatedAdd(meshVertices, sizeOf(mesh.vertices()));
                if (mesh.faces() == null) continue;
                for (ShyneNetwork.NetMeshFaceDefinition face : mesh.faces()) {
                    if (face == null) continue;
                    triangles = saturatedAdd(triangles, Math.max(0L, sizeOf(face.vertexIds()) - 2L));
                }
            }
        }

        long keyframes = 0L;
        if (model.animations() != null) {
            for (ShyneNetwork.NetAnimationDefinition animation : model.animations()) {
                if (animation == null || animation.boneAnimations() == null) continue;
                for (ShyneNetwork.NetBoneAnimation boneAnimation : animation.boneAnimations().values()) {
                    if (boneAnimation == null) continue;
                    keyframes = saturatedAdd(keyframes, sizeOf(boneAnimation.rotation()));
                    keyframes = saturatedAdd(keyframes, sizeOf(boneAnimation.position()));
                    keyframes = saturatedAdd(keyframes, sizeOf(boneAnimation.scale()));
                }
            }
        }

        long textureMemoryBytes = 0L;
        long textureTransferBytes = 0L;
        long maxTextureDimension = 0L;
        if (model.textures() != null) {
            for (ShyneNetwork.NetTextureDefinition texture : model.textures()) {
                if (texture == null) continue;
                long width = Math.max(0L, texture.width());
                long height = Math.max(0L, texture.height());
                maxTextureDimension = Math.max(maxTextureDimension, Math.max(width, height));
                textureMemoryBytes = saturatedAdd(
                    textureMemoryBytes,
                    saturatedMultiply(saturatedMultiply(width, height), 4L)
                );
                textureTransferBytes = saturatedAdd(textureTransferBytes, estimatedBase64Bytes(texture.contentBase64()));
            }
        }

        return new RemoteAvatarResourceBudget.Metrics(
            triangles,
            meshVertices,
            sizeOf(model.bones()),
            cubes,
            sizeOf(model.meshes()),
            sizeOf(model.animations()),
            keyframes,
            sizeOf(model.textures()),
            maxTextureDimension,
            textureMemoryBytes,
            textureTransferBytes
        );
    }

    private static long estimatedBase64Bytes(String encoded) {
        if (encoded == null || encoded.isEmpty()) return 0L;
        long length = encoded.length();
        long result = saturatedMultiply(length / 4L, 3L);
        int remainder = encoded.length() & 3;
        if (remainder == 2) result = saturatedAdd(result, 1L);
        else if (remainder == 3) result = saturatedAdd(result, 2L);
        if (encoded.endsWith("==")) result = Math.max(0L, result - 2L);
        else if (encoded.endsWith("=")) result = Math.max(0L, result - 1L);
        return result;
    }

    private static long sizeOf(Collection<?> values) {
        return values == null ? 0L : values.size();
    }

    private static long sizeOf(Map<?, ?> values) {
        return values == null ? 0L : values.size();
    }

    private static long saturatedAdd(long left, long right) {
        if (left < 0L || right < 0L || left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left < 0L || right < 0L) return Long.MAX_VALUE;
        if (left == 0L || right == 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
    public static void setAvatarPartState(UUID entityId, String modelId, String path, AvatarPartState state) {
        if (entityId == null || modelId == null || path == null || state == null) return;
        Map<String, AvatarPartState> parts = AVATAR_PARTS.computeIfAbsent(entityId.toString() + "|" + modelId, k -> new ConcurrentHashMap<>());
        AvatarPartState previous = parts.get(path);
        if (previous == null) {
            parts.put(path, state.copy());
            return;
        }
        if (!previous.sameValues(state)) {
            RemotePartKey key = new RemotePartKey(entityId, modelId, path);
            AvatarPartState rendered = interpolated(key, previous);
            AvatarPartState target = state.copy();
            parts.put(path, target);
            REMOTE_PART_TRANSITIONS.put(key, new RemotePartTransition(rendered.copy(), target, System.currentTimeMillis()));
        }
    }
    public static void updateLocalAvatarPartState(UUID entityId, String modelId, String path, AvatarPartState state) {
        if (entityId == null || modelId == null || path == null || state == null) return;
        Map<String, AvatarPartState> parts = AVATAR_PARTS.computeIfAbsent(entityId + "|" + modelId, k -> new ConcurrentHashMap<>());
        REMOTE_PART_TRANSITIONS.remove(new RemotePartKey(entityId, modelId, path));
        AvatarPartState previous = parts.get(path);
        if (previous == null || !previous.sameValues(state)) parts.put(path, state.copy());
    }
    private static boolean nonZero(float value) { return Math.abs(value) > 0.0001f; }
    private static boolean nonOne(float value) { return Math.abs(value - 1f) > 0.0001f; }
    public static AvatarPartState getAvatarPartState(UUID entityId, String modelId, String path) {
        if (entityId == null || modelId == null || path == null) return null;
        Map<String, AvatarPartState> map = AVATAR_PARTS.get(entityId.toString() + "|" + modelId);
        if (map == null) return null;
        AvatarPartState state = map.get(path);
        if (state != null) return interpolated(new RemotePartKey(entityId, modelId, path), state);
        String probe = path;
        while (probe.contains(".")) {
            probe = probe.substring(probe.indexOf('.') + 1);
            state = map.get(probe);
            if (state != null) return interpolated(new RemotePartKey(entityId, modelId, probe), state);
        }
        String lower = path.toLowerCase(Locale.ROOT);
        state = map.get(lower);
        return state == null ? null : interpolated(new RemotePartKey(entityId, modelId, lower), state);
    }
    public static Collection<AnimationPlayback> allPlaybacks() {
        Map<UUID, AnimationPlayback> merged = new LinkedHashMap<>(SERVER_PLAYBACKS);
        merged.putAll(AVATAR_PLAYBACKS);
        return List.copyOf(merged.values());
    }
    public static Collection<AttachedModelState> allAttachments() {
        Map<UUID, AttachedModelState> merged = new LinkedHashMap<>(SERVER_ATTACHMENTS);
        merged.putAll(AVATAR_ATTACHMENTS);
        return List.copyOf(merged.values());
    }
    public static AttachedModelState getAttachment(UUID entityId) {
        AttachedModelState avatar = AVATAR_ATTACHMENTS.get(entityId);
        return avatar == null ? SERVER_ATTACHMENTS.get(entityId) : avatar;
    }
    public static AnimationPlayback getPlayback(UUID entityId) {
        AnimationPlayback avatar = AVATAR_PLAYBACKS.get(entityId);
        return avatar == null ? SERVER_PLAYBACKS.get(entityId) : avatar;
    }
    public static BbModelDefinition getModel(String modelId) {
        BbModelDefinition model = LOCAL_AVATAR_MODELS.get(modelId);
        if (model == null) model = REMOTE_AVATAR_MODELS.get(modelId);
        return model == null ? SERVER_MODELS.get(modelId) : model;
    }
    public static long acknowledgedAvatarSnapshotRevision(UUID playerId) {
        return playerId == null ? 0L : LOCAL_SNAPSHOT_ACKS.getOrDefault(playerId, 0L);
    }
    public static void clearRemoteSession() {
        for (UUID playerId : List.copyOf(REMOTE_AVATARS.keySet())) removeRemoteAvatar(playerId);
        REMOTE_AVATAR_MODELS.clear();
        SERVER_MODELS.clear();
        SERVER_PLAYBACKS.clear();
        AVATAR_PLAYBACKS.clear();
        SERVER_ATTACHMENTS.clear();
        AVATAR_ATTACHMENTS.clear();
        POWER_STATES.clear();
        SKILLS.clear();
        PROFILES.clear();
        WEAPONS.clear();
        LOADOUTS.clear();
        AVATAR_PARTS.clear();
        REMOTE_PART_TRANSITIONS.clear();
        AVATAR_SYNCED_VARS.clear();
        REMOTE_VANILLA_VISIBILITY.clear();
        REMOTE_ANIMATION_LAYERS.clear();
        REMOTE_ANIMATION_PARAMETERS.clear();
        REMOTE_NAMEPLATES.clear();
        REMOTE_AVATAR_REJECTIONS.clear();
        VANILLA_TRANSFORMS.clear();
        LOCAL_SNAPSHOT_ACKS.clear();
        BbModelTextures.clearRemoteSyncedTextures();
    }
    public static PowerState getPowerState(UUID entityId) { return POWER_STATES.get(entityId); }
    public static SkillDefinition getSkill(String skillId) { return SKILLS.get(skillId); }
    public static PlayerProfile getProfile(UUID playerId) { return PROFILES.get(playerId); }
    public static WeaponDefinition getWeapon(String weaponId) { return WEAPONS.get(weaponId); }
    public static EquipmentLoadout getLoadout(UUID entityId) { return LOADOUTS.get(entityId); }
    public static Object getAvatarSyncedVar(String playerId, String key) {
        Map<String, Object> map = AVATAR_SYNCED_VARS.get(playerId);
        return map == null ? null : map.get(key);
    }
    public static Boolean getRemoteVanillaVisibility(UUID playerId, String key) {
        Map<String, Boolean> map = REMOTE_VANILLA_VISIBILITY.get(playerId);
        return VanillaVisibilityKeys.find(map, key);
    }
    private static String normalizeVanillaPart(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }
    private static String vanillaTransformAlias(String part) {
        return switch (part) {
            case "HAT", "HELMET", "HEADITEM", "SKULL", "CUSTOMHEAD" -> "HEAD";
            case "TORSO", "JACKET", "CAPE", "ELYTRA", "ARMOR", "CHESTPLATE", "LEGGINGS" -> "BODY";
            case "LEFTSLEEVE", "LEFTITEM", "LEFTHANDITEM" -> "LEFTARM";
            case "RIGHTSLEEVE", "RIGHTITEM", "RIGHTHANDITEM", "HELDITEMS" -> "RIGHTARM";
            case "LEFTPANTS", "LEFTBOOT" -> "LEFTLEG";
            case "RIGHTPANTS", "RIGHTBOOT", "BOOTS" -> "RIGHTLEG";
            // MAINHAND/OFFHAND are published by the renderer using the player's
            // actual dominant arm. Keep them distinct rather than guessing here.
            default -> part;
        };
    }
    public static RemoteAvatarState getRemoteAvatar(UUID playerId) { return REMOTE_AVATARS.get(playerId); }
    public static Collection<RemoteAvatarState> allRemoteAvatars() { return List.copyOf(REMOTE_AVATARS.values()); }
    public static RemoteAvatarResourceBudget.Decision getRemoteAvatarRejection(UUID playerId) {
        return playerId == null ? null : REMOTE_AVATAR_REJECTIONS.get(playerId);
    }
    public static List<AvatarAnimationLayer> getRemoteAnimationLayers(UUID playerId) {
        long now = System.currentTimeMillis();
        List<AvatarAnimationLayer> layers = REMOTE_ANIMATION_LAYERS.getOrDefault(playerId, List.of());
        boolean hasFinished = false;
        for (AvatarAnimationLayer layer : layers) {
            if (layer.finished(now)) {
                hasFinished = true;
                break;
            }
        }
        if (!hasFinished) return layers;
        List<AvatarAnimationLayer> active = layers.stream().filter(layer -> !layer.finished(now)).toList();
        REMOTE_ANIMATION_LAYERS.put(playerId, active);
        return active;
    }
    public static Map<String, Double> getRemoteAnimationParameters(UUID playerId) {
        return REMOTE_ANIMATION_PARAMETERS.getOrDefault(playerId, Map.of());
    }
    public static String getRemoteNameplateText(UUID playerId) {
        if (ShyneClientSettings.isRemoteAvatarHidden(playerId) || ShyneClientSettings.isRemoteAvatarMuted(playerId)) return "";
        RemoteNameplate value = REMOTE_NAMEPLATES.get(playerId);
        return value == null ? "" : value.text();
    }
    public static Boolean getRemoteNameplateVisible(UUID playerId) {
        if (ShyneClientSettings.isRemoteAvatarHidden(playerId) || ShyneClientSettings.isRemoteAvatarMuted(playerId)) return null;
        RemoteNameplate value = REMOTE_NAMEPLATES.get(playerId);
        return value == null ? null : value.visible();
    }
    private static AvatarPartState interpolated(RemotePartKey key, AvatarPartState target) {
        RemotePartTransition transition = REMOTE_PART_TRANSITIONS.get(key);
        if (transition == null) return target;
        float alpha = (System.currentTimeMillis() - transition.startedAtMillis) / (float) REMOTE_POSE_INTERPOLATION_MS;
        if (alpha >= 1f) {
            REMOTE_PART_TRANSITIONS.remove(key, transition);
            return target;
        }
        return AvatarPartState.interpolate(transition.previous, transition.target, alpha);
    }
    private static AnimationPlayback toRuntime(ShyneNetwork.NetPlayback pb) {
        return new AnimationPlayback(UUID.fromString(pb.entityId()), pb.entityName(), pb.modelId(), pb.animationName(),
            AvatarAnimationClock.decodeAge(System.currentTimeMillis(), pb.startedAtMillis()), pb.lengthSeconds(), pb.looping());
    }
    private static <K, V> void copyNonNullEntries(Map<K, V> source, Map<K, V> target) {
        if (source == null || target == null) return;
        for (Map.Entry<K, V> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) target.put(entry.getKey(), entry.getValue());
        }
    }
    private record RemotePartKey(UUID entityId, String modelId, String path) {}
    private record RemotePartTransition(AvatarPartState previous, AvatarPartState target, long startedAtMillis) {}
    private record RemoteNameplate(String text, boolean visible) {}
}

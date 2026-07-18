package seashyne.shynecore.client.state;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import seashyne.shynecore.animation.AnimationPlayback;
import seashyne.shynecore.attachment.AttachedModelState;
import seashyne.shynecore.client.avatar.AvatarPartState;
import seashyne.shynecore.client.avatar.AvatarAnimationLayer;
import seashyne.shynecore.client.avatar.RemoteAvatarState;
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

    private static final Map<String, BbModelDefinition> MODELS = new ConcurrentHashMap<>();
    private static final Map<UUID, AnimationPlayback> PLAYBACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, AttachedModelState> ATTACHMENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, PowerState> POWER_STATES = new ConcurrentHashMap<>();
    private static final Map<String, SkillDefinition> SKILLS = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerProfile> PROFILES = new ConcurrentHashMap<>();
    private static final Map<String, WeaponDefinition> WEAPONS = new ConcurrentHashMap<>();
    private static final Map<UUID, EquipmentLoadout> LOADOUTS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, AvatarPartState>> AVATAR_PARTS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> AVATAR_SYNCED_VARS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Boolean>> REMOTE_VANILLA_VISIBILITY = new ConcurrentHashMap<>();
    private static final Map<UUID, RemoteAvatarState> REMOTE_AVATARS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<AvatarAnimationLayer>> REMOTE_ANIMATION_LAYERS = new ConcurrentHashMap<>();
    private static final Map<UUID, RemoteNameplate> REMOTE_NAMEPLATES = new ConcurrentHashMap<>();

    private ClientAnimationState() {}

    public static void handleModelSync(String json) {
        ShyneNetwork.ModelSyncPayload payload = GSON.fromJson(json, MODEL_SYNC_TYPE);
        MODELS.clear();
        if (payload != null && payload.models() != null) for (ShyneNetwork.NetModelDefinition model : payload.models()) {
            BbModelTextures.installSynced(model);
            MODELS.put(model.modelId(), model.toRuntime());
        }
    }
    public static void handleActiveSync(String json) {
        ShyneNetwork.ActiveSyncPayload payload = GSON.fromJson(json, ACTIVE_SYNC_TYPE);
        PLAYBACKS.clear();
        if (payload != null && payload.playbacks() != null) for (ShyneNetwork.NetPlayback pb : payload.playbacks()) PLAYBACKS.put(UUID.fromString(pb.entityId()), toRuntime(pb));
    }
    public static void handleAttachmentSync(String json) {
        ShyneNetwork.AttachmentSyncPayload payload = GSON.fromJson(json, ATTACHMENT_SYNC_TYPE);
        ATTACHMENTS.clear();
        if (payload != null && payload.attachments() != null) for (ShyneNetwork.NetAttachment attachment : payload.attachments()) ATTACHMENTS.put(UUID.fromString(attachment.entityId()), attachment.toRuntime());
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
        if (pb != null) PLAYBACKS.put(UUID.fromString(pb.entityId()), toRuntime(pb));
    }
    public static void handleStop(String json) {
        ShyneNetwork.StopPayload payload = GSON.fromJson(json, STOP_TYPE);
        if (payload != null) PLAYBACKS.remove(UUID.fromString(payload.entityId()));
    }
    public static void handleAvatarVarSync(String json) {
        ShyneNetwork.AvatarVarSyncPayload payload = GSON.fromJson(json, AVATAR_VAR_SYNC_TYPE);
        if (payload == null || payload.vars() == null) return;
        for (ShyneNetwork.NetAvatarVars vars : payload.vars()) {
            Map<String, Object> map = new ConcurrentHashMap<>();
            if (vars.values() != null) map.putAll(vars.values());
            AVATAR_SYNCED_VARS.put(vars.playerId(), map);
            RemoteAvatarState remote = REMOTE_AVATARS.get(UUID.fromString(vars.playerId()));
            if (remote != null) {
                REMOTE_AVATARS.put(remote.playerId(), new RemoteAvatarState(remote.playerId(), remote.avatarId(), remote.modelId(), remote.replaceVanilla(), remote.parts(), remote.vanillaVisibility(), map, remote.currentAnimation(), remote.animationStartedAtMillis()));
            }
        }
    }
    public static void handleAvatarSnapshotSync(String json) {
        ShyneNetwork.AvatarSnapshotSyncPayload payload = GSON.fromJson(json, AVATAR_SNAPSHOT_SYNC_TYPE);
        if (payload == null || payload.avatars() == null) return;
        for (ShyneNetwork.NetAvatarSnapshot snapshot : payload.avatars()) {
            UUID playerId = UUID.fromString(snapshot.playerId());
            if (!snapshot.onlineSync()) {
                removeRemoteAvatar(playerId);
                continue;
            }
            if (snapshot.model() != null) {
                BbModelTextures.installSynced(snapshot.model());
                MODELS.put(snapshot.modelId(), snapshot.model().toRuntime());
            }
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
                    if (part.colorArgb() != 0xFFFFFFFF || part.emissive()) state.setRenderState(part.colorArgb(), part.emissive());
                    parts.put(part.path(), state);
                    setAvatarPartState(playerId, snapshot.modelId(), part.path(), state);
                }
            }
            ATTACHMENTS.put(playerId, new AttachedModelState(playerId, snapshot.avatarId(), snapshot.modelId(), 0f, 0f, 0f, 1f, "", true));
            if (snapshot.currentAnimation() != null && !snapshot.currentAnimation().isBlank()) {
                PLAYBACKS.put(playerId, new AnimationPlayback(playerId, snapshot.avatarId(), snapshot.modelId(), snapshot.currentAnimation(), snapshot.animationStartedAtMillis(), 2.0, true));
            }
            List<AvatarAnimationLayer> animationLayers = snapshot.animationLayers() == null ? List.of() : snapshot.animationLayers().stream()
                .map(layer -> new AvatarAnimationLayer(
                    layer.name(), layer.startedAtMillis(), layer.lengthSeconds(), layer.looping(), layer.speed(), layer.weight(), layer.priority(),
                    layer.fadeInTicks(), layer.fadeOutTicks(), layer.mask(), layer.additive(), layer.stoppingAtMillis()
                ))
                .sorted(Comparator.comparingInt(AvatarAnimationLayer::priority))
                .toList();
            REMOTE_ANIMATION_LAYERS.put(playerId, animationLayers);
            REMOTE_NAMEPLATES.put(playerId, new RemoteNameplate(snapshot.nameplateText() == null ? "" : snapshot.nameplateText(), snapshot.nameplateVisible()));
            Map<String, Boolean> visibility = snapshot.vanillaVisibility() == null ? Map.of() : new ConcurrentHashMap<>(snapshot.vanillaVisibility());
            REMOTE_VANILLA_VISIBILITY.put(playerId, visibility);
            Map<String, Object> syncedVars = snapshot.syncedVars() == null ? Map.of() : new ConcurrentHashMap<>(snapshot.syncedVars());
            AVATAR_SYNCED_VARS.put(snapshot.playerId(), syncedVars);
            REMOTE_AVATARS.put(playerId, new RemoteAvatarState(playerId, snapshot.avatarId(), snapshot.modelId(), snapshot.replaceVanilla(), parts, visibility, syncedVars, snapshot.currentAnimation(), snapshot.animationStartedAtMillis()));
        }
    }

    public static void putLocalModel(String modelId, BbModelDefinition model) { MODELS.put(modelId, model); }
    public static void putLocalAttachment(AttachedModelState state) { ATTACHMENTS.put(state.entityId(), state); }
    public static void putLocalPlayback(AnimationPlayback playback) { PLAYBACKS.put(playback.entityId(), playback); }
    public static void removeLocalModel(String modelId) { if (modelId != null) MODELS.remove(modelId); }
    public static void removeLocalAttachment(UUID entityId) { if (entityId != null) ATTACHMENTS.remove(entityId); }
    public static void removeLocalPlayback(UUID entityId) { PLAYBACKS.remove(entityId); }
    public static void clearAvatarPartStates(UUID entityId, String modelId) {
        if (entityId != null && modelId != null) AVATAR_PARTS.remove(entityId + "|" + modelId);
    }
    public static void removeRemoteAvatar(UUID playerId) {
        if (playerId == null) return;
        RemoteAvatarState remote = REMOTE_AVATARS.remove(playerId);
        ATTACHMENTS.remove(playerId);
        PLAYBACKS.remove(playerId);
        REMOTE_VANILLA_VISIBILITY.remove(playerId);
        REMOTE_ANIMATION_LAYERS.remove(playerId);
        REMOTE_NAMEPLATES.remove(playerId);
        AVATAR_SYNCED_VARS.remove(playerId.toString());
        if (remote != null) AVATAR_PARTS.remove(playerId + "|" + remote.modelId());
    }
    public static void setAvatarPartState(UUID entityId, String modelId, String path, AvatarPartState state) {
        Map<String, AvatarPartState> parts = AVATAR_PARTS.computeIfAbsent(entityId.toString() + "|" + modelId, k -> new ConcurrentHashMap<>());
        AvatarPartState previous = parts.get(path);
        if (previous == null || !previous.sameValues(state)) parts.put(path, state);
    }
    public static void updateLocalAvatarPartState(UUID entityId, String modelId, String path, AvatarPartState state) {
        if (entityId == null || modelId == null || path == null || state == null) return;
        Map<String, AvatarPartState> parts = AVATAR_PARTS.computeIfAbsent(entityId + "|" + modelId, k -> new ConcurrentHashMap<>());
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
        if (state != null) return state;
        String probe = path;
        while (probe.contains(".")) {
            probe = probe.substring(probe.indexOf('.') + 1);
            state = map.get(probe);
            if (state != null) return state;
        }
        return map.get(path.toLowerCase(Locale.ROOT));
    }
    public static Collection<AnimationPlayback> allPlaybacks() { return List.copyOf(PLAYBACKS.values()); }
    public static Collection<AttachedModelState> allAttachments() { return List.copyOf(ATTACHMENTS.values()); }
    public static AttachedModelState getAttachment(UUID entityId) { return ATTACHMENTS.get(entityId); }
    public static AnimationPlayback getPlayback(UUID entityId) { return PLAYBACKS.get(entityId); }
    public static BbModelDefinition getModel(String modelId) { return MODELS.get(modelId); }
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
        return map == null ? null : map.get(key);
    }
    public static RemoteAvatarState getRemoteAvatar(UUID playerId) { return REMOTE_AVATARS.get(playerId); }
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
    public static String getRemoteNameplateText(UUID playerId) { RemoteNameplate value = REMOTE_NAMEPLATES.get(playerId); return value == null ? "" : value.text(); }
    public static Boolean getRemoteNameplateVisible(UUID playerId) { RemoteNameplate value = REMOTE_NAMEPLATES.get(playerId); return value == null ? null : value.visible(); }
    private static AnimationPlayback toRuntime(ShyneNetwork.NetPlayback pb) { return new AnimationPlayback(UUID.fromString(pb.entityId()), pb.entityName(), pb.modelId(), pb.animationName(), pb.startedAtMillis(), pb.lengthSeconds(), pb.looping()); }
    private record RemoteNameplate(String text, boolean visible) {}
}

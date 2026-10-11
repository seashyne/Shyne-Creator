package seashyne.shynecore.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.animation.AnimationPlayback;
import seashyne.shynecore.attachment.AttachedModelState;
import seashyne.shynecore.avatar.AvatarAnimationClock;
import seashyne.shynecore.equipment.EquipmentLoadout;
import seashyne.shynecore.equipment.WeaponDefinition;
import seashyne.shynecore.item.ItemPresentation;
import seashyne.shynecore.item.ShyneItemDefinition;
import seashyne.shynecore.model.*;
import seashyne.shynecore.power.PowerState;
import seashyne.shynecore.profile.PlayerProfile;
import seashyne.shynecore.skill.SkillDefinition;
import seashyne.shynecore.skill.SkillIconAsset;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shared network foundation for Shyne Creator multiloader architecture.
 * Contains protocol definitions, network channels, capability flags,
 * serialization codecs, rate limiter classes, and data transfer objects (DTOs).
 *
 * <p>Both Fabric and NeoForge loader adapters extend this class to provide
 * transparent, backward-compatible access to network constants and records.</p>
 */
public class ShyneNetworkCommon {

    // =========================================================================
    // Protocol Version & Feature Capabilities
    // =========================================================================
    public static final int PROTOCOL_VERSION = 19;
    public static final String CAP_SERVER_AUTHORITATIVE_GAMEPLAY = "gameplay.server_authoritative";
    public static final String CAP_DYNAMIC_ACTION_DECK = "gameplay.dynamic_action_deck_v1";
    public static final String CAP_SKILL_ICON_ASSETS = "content.skill_icon_assets_v1";
    public static final String CAP_ITEM_CATALOG = "content.item_catalog_v1";
    public static final String CAP_ITEM_BB_PRESENTATION = "content.item_bb_presentation_v1";
    /** Sentinel carried in {@link SkillKeyPayload} for a client-configured action-deck cast. */
    public static final int DYNAMIC_ACTION_DECK_SLOT = -1;
    public static final String CAP_CONTENT_REGISTRY_SYNC = "content.registry_sync";
    public static final String CAP_AVATAR_PEER_SNAPSHOT = "avatar.peer_snapshot_v2";
    public static final String CAP_PLAYER_TAB_STATUS = "player.tab_status_v1";
    public static final String CAP_AVATAR_SNAPSHOT_REQUEST = "avatar.snapshot_request_v1";
    public static final String CAP_AVATAR_RECIPIENT_SUBSCRIPTIONS = "avatar.recipient_subscriptions_v1";
    public static final String CAP_AVATAR_BONE_PHYSICS = "avatar.bone_physics_v1";
    public static final String CAP_PACKET_COMPRESSION = "network.compression_v1";
    public static final String CAP_AVATAR_CHANNELS = "avatar.channels_v1";
    public static final List<String> SERVER_CAPABILITIES = List.of(
        CAP_SERVER_AUTHORITATIVE_GAMEPLAY,
        CAP_DYNAMIC_ACTION_DECK,
        CAP_SKILL_ICON_ASSETS,
        CAP_ITEM_CATALOG,
        CAP_ITEM_BB_PRESENTATION,
        CAP_CONTENT_REGISTRY_SYNC,
        CAP_AVATAR_PEER_SNAPSHOT,
        CAP_PLAYER_TAB_STATUS,
        CAP_AVATAR_SNAPSHOT_REQUEST,
        CAP_AVATAR_RECIPIENT_SUBSCRIPTIONS,
        CAP_AVATAR_BONE_PHYSICS,
        CAP_PACKET_COMPRESSION,
        CAP_AVATAR_CHANNELS
    );

    // =========================================================================
    // Network Channel Identifiers (ResourceLocations / Identifiers)
    // =========================================================================
    public static final Identifier PROTOCOL_HELLO = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "protocol_hello");
    public static final Identifier PROTOCOL_STATUS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "protocol_status");
    public static final Identifier SYNC_MODELS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_models");
    public static final Identifier PLAY_ANIMATION = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "play_animation");
    public static final Identifier STOP_ANIMATION = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "stop_animation");
    public static final Identifier SYNC_ACTIVE = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_active");
    public static final Identifier SYNC_ATTACHMENTS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_attachments");
    public static final Identifier SYNC_POWER = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_power");
    public static final Identifier SYNC_SKILLS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_skills");
    public static final Identifier SYNC_ITEMS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_items");
    public static final Identifier SYNC_PROFILES = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_profiles");
    public static final Identifier SYNC_WEAPONS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_weapons");
    public static final Identifier SYNC_LOADOUTS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_loadouts");
    public static final Identifier SKILL_KEY = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "skill_key");
    public static final Identifier SYNC_AVATAR_VARS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_avatar_vars");
    public static final Identifier AVATAR_VAR_SET = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "avatar_var_set");
    public static final Identifier AVATAR_SNAPSHOT = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "avatar_snapshot");
    public static final Identifier SYNC_AVATAR_SNAPSHOTS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_avatar_snapshots");
    public static final Identifier SYNC_PLAYER_PRESENCE = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_player_presence");
    public static final Identifier AVATAR_SYNC_REQUEST = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "avatar_sync_request");
    public static final Identifier EQUIP_SKILL = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "equip_skill");
    public static final Identifier SYNC_POWER_CONFIG = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_power_config");
    public static final Identifier AVATAR_CHANNEL = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "avatar_channel");

    // =========================================================================
    // Custom Packet Payload Types
    // =========================================================================
    public static final CustomPacketPayload.Type<ProtocolHelloPayload> PROTOCOL_HELLO_PAYLOAD = new CustomPacketPayload.Type<>(PROTOCOL_HELLO);
    public static final CustomPacketPayload.Type<JsonPayload> PROTOCOL_STATUS_PAYLOAD = new CustomPacketPayload.Type<>(PROTOCOL_STATUS);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_MODELS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_MODELS);
    public static final CustomPacketPayload.Type<JsonPayload> PLAY_ANIMATION_PAYLOAD = new CustomPacketPayload.Type<>(PLAY_ANIMATION);
    public static final CustomPacketPayload.Type<JsonPayload> STOP_ANIMATION_PAYLOAD = new CustomPacketPayload.Type<>(STOP_ANIMATION);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_ACTIVE_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_ACTIVE);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_ATTACHMENTS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_ATTACHMENTS);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_POWER_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_POWER);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_SKILLS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_SKILLS);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_ITEMS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_ITEMS);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_PROFILES_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_PROFILES);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_WEAPONS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_WEAPONS);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_LOADOUTS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_LOADOUTS);
    public static final CustomPacketPayload.Type<SkillKeyPayload> SKILL_KEY_PAYLOAD = new CustomPacketPayload.Type<>(SKILL_KEY);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_AVATAR_VARS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_AVATAR_VARS);
    public static final CustomPacketPayload.Type<AvatarVarSetPayload> AVATAR_VAR_SET_PAYLOAD = new CustomPacketPayload.Type<>(AVATAR_VAR_SET);
    public static final CustomPacketPayload.Type<JsonPayload> AVATAR_SNAPSHOT_PAYLOAD = new CustomPacketPayload.Type<>(AVATAR_SNAPSHOT);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_AVATAR_SNAPSHOTS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_AVATAR_SNAPSHOTS);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_PLAYER_PRESENCE_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_PLAYER_PRESENCE);
    public static final CustomPacketPayload.Type<AvatarSyncRequestPayload> AVATAR_SYNC_REQUEST_PAYLOAD = new CustomPacketPayload.Type<>(AVATAR_SYNC_REQUEST);
    public static final CustomPacketPayload.Type<JsonPayload> EQUIP_SKILL_PAYLOAD = new CustomPacketPayload.Type<>(EQUIP_SKILL);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_POWER_CONFIG_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_POWER_CONFIG);
    public static final CustomPacketPayload.Type<AvatarChannelPayload> AVATAR_CHANNEL_PAYLOAD = new CustomPacketPayload.Type<>(AVATAR_CHANNEL);

    // =========================================================================
    // Serialization Config & Security Bounds
    // =========================================================================
    public static final Map<Identifier, CustomPacketPayload.Type<JsonPayload>> JSON_PAYLOAD_IDS = createJsonPayloadIds();
    public static final Gson GSON = new GsonBuilder().serializeNulls().create();
    public static final double MAX_AVATAR_TRACKING_DISTANCE = 160.0;
    public static final double MAX_AVATAR_TRACKING_DISTANCE_SQR = MAX_AVATAR_TRACKING_DISTANCE * MAX_AVATAR_TRACKING_DISTANCE;
    public static final int MAX_TEXTURE_BYTES = ShyneNetworkValidator.MAX_TEXTURE_BYTES;
    public static final int MAX_AVATAR_TEXTURE_BYTES = ShyneNetworkValidator.MAX_AVATAR_TEXTURE_BYTES;
    public static final int MAX_AVATAR_JSON_CHARS = ShyneNetworkValidator.MAX_AVATAR_JSON_CHARS;
    public static final int MAX_AVATAR_SUBSCRIPTION_COMMAND_BYTES = ShyneNetworkValidator.MAX_AVATAR_SUBSCRIPTION_COMMAND_BYTES;
    public static final int MAX_AVATAR_PARTS = ShyneNetworkValidator.MAX_AVATAR_PARTS;
    public static final int MAX_SYNCED_VARS = ShyneNetworkValidator.MAX_SYNCED_VARS;
    public static final int MAX_MODEL_CUBES = ShyneNetworkValidator.MAX_MODEL_CUBES;
    public static final int MAX_MODEL_MESHES = ShyneNetworkValidator.MAX_MODEL_MESHES;
    public static final int MAX_MODEL_MESH_VERTICES = ShyneNetworkValidator.MAX_MODEL_MESH_VERTICES;
    public static final int MAX_MODEL_MESH_FACES = ShyneNetworkValidator.MAX_MODEL_MESH_FACES;
    public static final int MAX_MODEL_BONES = ShyneNetworkValidator.MAX_MODEL_BONES;
    public static final int MAX_MODEL_ANIMATIONS = ShyneNetworkValidator.MAX_MODEL_ANIMATIONS;
    public static final int MAX_MODEL_TEXTURES = ShyneNetworkValidator.MAX_MODEL_TEXTURES;

    public static NetTextureDefinition toNetTexture(BbModelDefinition model, BbTextureDefinition texture) {
        byte[] bytes = ShyneNetworkValidator.readTextureBytes(model, texture);
        String hash = bytes == null ? "" : HexFormat.of().formatHex(ShyneNetworkValidator.sha256(bytes));
        String base64 = bytes == null ? "" : Base64.getEncoder().encodeToString(bytes);
        return new NetTextureDefinition(texture.id(), texture.name(), texture.relativePath(), texture.width(), texture.height(), hash, base64);
    }

    public static Map<Identifier, CustomPacketPayload.Type<JsonPayload>> createJsonPayloadIds() {
        Map<Identifier, CustomPacketPayload.Type<JsonPayload>> ids = new LinkedHashMap<>();
        ids.put(PROTOCOL_STATUS, PROTOCOL_STATUS_PAYLOAD);
        ids.put(SYNC_MODELS, SYNC_MODELS_PAYLOAD);
        ids.put(PLAY_ANIMATION, PLAY_ANIMATION_PAYLOAD);
        ids.put(STOP_ANIMATION, STOP_ANIMATION_PAYLOAD);
        ids.put(SYNC_ACTIVE, SYNC_ACTIVE_PAYLOAD);
        ids.put(SYNC_ATTACHMENTS, SYNC_ATTACHMENTS_PAYLOAD);
        ids.put(SYNC_POWER, SYNC_POWER_PAYLOAD);
        ids.put(SYNC_SKILLS, SYNC_SKILLS_PAYLOAD);
        ids.put(SYNC_ITEMS, SYNC_ITEMS_PAYLOAD);
        ids.put(SYNC_PROFILES, SYNC_PROFILES_PAYLOAD);
        ids.put(SYNC_WEAPONS, SYNC_WEAPONS_PAYLOAD);
        ids.put(SYNC_LOADOUTS, SYNC_LOADOUTS_PAYLOAD);
        ids.put(SYNC_AVATAR_VARS, SYNC_AVATAR_VARS_PAYLOAD);
        ids.put(AVATAR_SNAPSHOT, AVATAR_SNAPSHOT_PAYLOAD);
        ids.put(SYNC_AVATAR_SNAPSHOTS, SYNC_AVATAR_SNAPSHOTS_PAYLOAD);
        ids.put(SYNC_PLAYER_PRESENCE, SYNC_PLAYER_PRESENCE_PAYLOAD);
        ids.put(EQUIP_SKILL, EQUIP_SKILL_PAYLOAD);
        ids.put(SYNC_POWER_CONFIG, SYNC_POWER_CONFIG_PAYLOAD);
        return Map.copyOf(ids);
    }

    public static class ByteRateLimiter extends seashyne.shynecore.network.ByteRateLimiter {
        public ByteRateLimiter(long capacityBytes, long refillBytesPerSecond, long nowNanos) {
            super(capacityBytes, refillBytesPerSecond, nowNanos);
        }
    }

    public static class AvatarSubscriptions extends seashyne.shynecore.network.AvatarSubscriptions {}

    // =========================================================================
    // Network Data Transfer Objects (DTO Records)
    // =========================================================================
    public record ModelSyncPayload(List<NetModelDefinition> models) {}
    public record ActiveSyncPayload(List<NetPlayback> playbacks) {}
    public record AttachmentSyncPayload(List<NetAttachment> attachments) {}
    public record PowerSyncPayload(List<NetPowerState> states) {}
    public record SkillSyncPayload(List<NetSkillDefinition> skills) {}
    public record ItemSyncPayload(List<NetItemDefinition> items) {}
    public record ProfileSyncPayload(List<NetPlayerProfile> profiles) {}
    public record WeaponSyncPayload(List<NetWeaponDefinition> weapons) {}
    public record LoadoutSyncPayload(List<NetEquipmentLoadout> loadouts) {}
    public record AvatarVarSyncPayload(List<NetAvatarVars> vars) {}
    public record AvatarSnapshotSyncPayload(List<NetAvatarSnapshot> avatars, long revision) {
        public AvatarSnapshotSyncPayload(List<NetAvatarSnapshot> avatars) { this(avatars, 0L); }
    }
    public record PlayerPresenceSyncPayload(List<NetPlayerPresence> players) {}
    public record ProtocolStatus(boolean accepted, int protocolVersion, String modVersion, String message, List<String> capabilities) {}
    public record StopPayload(String entityId) {}
    public record EquipRequest(String skillId, String slot) {}

    /** startedAtMillis is an elapsed age on the wire, never an absolute clock. */
    public record NetPlayback(String entityId, String entityName, String modelId, String animationName, long startedAtMillis, double lengthSeconds, boolean looping) {
        public static NetPlayback from(AnimationPlayback playback) {
            return new NetPlayback(playback.entityId().toString(), playback.entityName(), playback.modelId(), playback.animationName(),
                AvatarAnimationClock.encodeAge(System.currentTimeMillis(), playback.startedAtMillis()), playback.lengthSeconds(), playback.looping());
        }
    }
    public record NetAttachment(String entityId, String entityName, String modelId, float offsetX, float offsetY, float offsetZ, float scale, String anchorBone, boolean visible) {
        public static NetAttachment from(AttachedModelState state) {
            return new NetAttachment(state.entityId().toString(), state.entityName(), state.modelId(), state.offsetX(), state.offsetY(), state.offsetZ(), state.scale(), state.anchorBone(), state.visible());
        }
        public AttachedModelState toRuntime() {
            return new AttachedModelState(UUID.fromString(entityId), entityName, modelId, offsetX, offsetY, offsetZ, scale, anchorBone, visible);
        }
    }
    public record NetPowerState(String entityId, String comboId, int stage, String branch, long startedAtMillis, long updatedAtMillis, long resetAtMillis, long cooldownEndsAtMillis, double mana, double maxMana, String currentAnimation, boolean locked) {
        public static NetPowerState from(PowerState state) {
            return new NetPowerState(state.entityId().toString(), state.comboId(), state.stage(), state.branch(), state.startedAtMillis(), state.updatedAtMillis(), state.resetAtMillis(), state.cooldownEndsAtMillis(), state.mana(), state.maxMana(), state.currentAnimation(), state.locked());
        }
        public PowerState toRuntime() {
            return new PowerState(UUID.fromString(entityId), comboId, stage, branch, startedAtMillis, updatedAtMillis, resetAtMillis, cooldownEndsAtMillis, mana, maxMana, currentAnimation, locked);
        }
    }
    public record NetPngIcon(String assetId, String relativePath, String contentHash, int width, int height, String contentBase64) {
        public static NetPngIcon from(SkillIconAsset asset) {
            return asset == null ? null : new NetPngIcon(asset.assetId(), asset.relativePath(), asset.contentHash(), asset.width(), asset.height(), asset.contentBase64());
        }
    }
    public record NetSkillDefinition(String skillId, String displayName, String castType, String defaultSlot, double manaCost, int cooldownTicks, String modelId, String animation, String icon, NetPngIcon iconAsset, List<String> tags) {
        public static NetSkillDefinition from(SkillDefinition def) { return from(def, null); }
        public static NetSkillDefinition from(SkillDefinition def, SkillIconAsset asset) {
            return new NetSkillDefinition(def.skillId(), def.displayName(), def.castType().name(), def.defaultSlot().name(), def.manaCost(), def.cooldownTicks(), def.modelId(), def.animation(), def.icon(), NetPngIcon.from(asset), def.tags());
        }
    }
    public record NetItemDefinition(String itemId, String displayName, List<String> description, String icon, String modelId, ItemPresentation presentation,
                                    String rarity, int maxStack, boolean glint, String useSkill, String weaponId,
                                    int cooldownTicks, boolean consumeOnUse, NetPngIcon iconAsset) {
        public static NetItemDefinition from(ShyneItemDefinition def, SkillIconAsset asset) {
            return new NetItemDefinition(def.itemId(), def.displayName(), def.description(), def.icon(), def.modelId(), def.presentation(),
                def.rarity().name(), def.maxStack(), def.glint(), def.useSkill(), def.weaponId(), def.cooldownTicks(),
                def.consumeOnUse(), NetPngIcon.from(asset));
        }
    }
    public record NetPlayerProfile(String playerId, String playerName, int level, long experience, int statPoints, int skillPoints, String playerClass, List<String> unlockedSkills, Map<String, String> equippedSkills, Map<String, Integer> attributes, String teamId, long updatedAtMillis) {
        public static NetPlayerProfile from(PlayerProfile profile) {
            return new NetPlayerProfile(profile.playerId().toString(), profile.playerName(), profile.level(), profile.experience(), profile.statPoints(), profile.skillPoints(), profile.playerClass(), profile.unlockedSkills(), profile.equippedSkills(), profile.attributes(), profile.teamId(), profile.updatedAtMillis());
        }
    }
    public record NetWeaponDefinition(String weaponId, String displayName, String itemId, String modelId, String classTag, List<String> grantedSkills, Map<String, Double> statModifiers) {
        public static NetWeaponDefinition from(WeaponDefinition definition) {
            return new NetWeaponDefinition(definition.weaponId(), definition.displayName(), definition.itemId(), definition.modelId(), definition.classTag(), definition.grantedSkills(), definition.statModifiers());
        }
    }
    public record NetEquipmentLoadout(String entityId, String mainHandWeaponId, String offHandWeaponId, Map<String, String> slots, long updatedAtMillis) {
        public static NetEquipmentLoadout from(EquipmentLoadout loadout) {
            return new NetEquipmentLoadout(loadout.entityId().toString(), loadout.mainHandWeaponId(), loadout.offHandWeaponId(), loadout.slots(), loadout.updatedAtMillis());
        }
    }
    /**
     * Serializable part state for another Shyne client. Direct transforms replace
     * their animation channel; additive rotation is intentionally a separate
     * layer for springs/physics, so it must never be merged into {@code rotX/Y/Z}.
     */
    public record NetAvatarPart(
        String path, boolean visible,
        float posX, float posY, float posZ,
        float rotX, float rotY, float rotZ,
        float scaleX, float scaleY, float scaleZ,
        boolean positionControlled, boolean rotationControlled, boolean scaleControlled,
        float additiveRotX, float additiveRotY, float additiveRotZ, boolean additiveRotationControlled,
        int colorArgb, boolean emissive,
        String vanillaParent, boolean vanillaParentControlled, String vanillaAttachmentMode,
        boolean visibilityControlled
    ) {
        public NetAvatarPart(
            String path, boolean visible,
            float posX, float posY, float posZ,
            float rotX, float rotY, float rotZ,
            float scaleX, float scaleY, float scaleZ,
            boolean positionControlled, boolean rotationControlled, boolean scaleControlled,
            float additiveRotX, float additiveRotY, float additiveRotZ, boolean additiveRotationControlled,
            int colorArgb, boolean emissive,
            String vanillaParent, boolean vanillaParentControlled, String vanillaAttachmentMode
        ) {
            this(path, visible, posX, posY, posZ, rotX, rotY, rotZ, scaleX, scaleY, scaleZ, positionControlled, rotationControlled, scaleControlled, additiveRotX, additiveRotY, additiveRotZ, additiveRotationControlled, colorArgb, emissive, vanillaParent, vanillaParentControlled, vanillaAttachmentMode, false);
        }
    }
    public record NetAvatarAnimation(String name, long startedAtMillis, double lengthSeconds, boolean looping, double speed, double weight, int priority, int fadeInTicks, int fadeOutTicks, List<String> mask, boolean additive, long stoppingAtMillis) {}
    public record NetAvatarSnapshot(String playerId, String avatarId, String modelId, boolean replaceVanilla, boolean onlineSync, NetModelDefinition model, List<NetAvatarPart> parts, Map<String, Boolean> vanillaVisibility, Map<String, Object> syncedVars, String currentAnimation, long animationStartedAtMillis, List<NetAvatarAnimation> animationLayers, Map<String, Double> animationParameters, String nameplateText, boolean nameplateVisible, String nameplateBadge, int nameplateColorArgb, boolean nameplateBold, boolean nameplateItalic) {
        public NetAvatarSnapshot {
            nameplateBadge = nameplateBadge == null ? "" : nameplateBadge.substring(0, Math.min(24, nameplateBadge.length()));
            nameplateColorArgb = 0xFF000000 | (nameplateColorArgb & 0x00FFFFFF);
        }
        public NetAvatarSnapshot(String playerId, String avatarId, String modelId, boolean replaceVanilla, boolean onlineSync, NetModelDefinition model, List<NetAvatarPart> parts, Map<String, Boolean> vanillaVisibility, Map<String, Object> syncedVars, String currentAnimation, long animationStartedAtMillis, List<NetAvatarAnimation> animationLayers, Map<String, Double> animationParameters, String nameplateText, boolean nameplateVisible) {
            this(playerId, avatarId, modelId, replaceVanilla, onlineSync, model, parts, vanillaVisibility, syncedVars, currentAnimation, animationStartedAtMillis, animationLayers, animationParameters, nameplateText, nameplateVisible, "", 0xFFFFFFFF, false, false);
        }
        public NetAvatarSnapshot(String playerId, String avatarId, String modelId, boolean replaceVanilla, boolean onlineSync, NetModelDefinition model, List<NetAvatarPart> parts, Map<String, Boolean> vanillaVisibility, Map<String, Object> syncedVars, String currentAnimation, long animationStartedAtMillis, List<NetAvatarAnimation> animationLayers, String nameplateText, boolean nameplateVisible) {
            this(playerId, avatarId, modelId, replaceVanilla, onlineSync, model, parts, vanillaVisibility, syncedVars, currentAnimation, animationStartedAtMillis, animationLayers, Map.of(), nameplateText, nameplateVisible, "", 0xFFFFFFFF, false, false);
        }
    }
    public record NetPlayerPresence(String playerId, boolean avatarAvailable) {}
    public record NetModelDefinition(String modelId, String sourceModId, String displayName, int formatVersion, int textureWidth, int textureHeight, String primaryTextureRelativePath, List<NetTextureDefinition> textures, List<NetBoneDefinition> bones, List<NetCubeDefinition> cubes, List<NetMeshDefinition> meshes, List<NetAnimationDefinition> animations) {
        public NetModelDefinition(String modelId, String sourceModId, String displayName, int formatVersion, int textureWidth, int textureHeight, String primaryTextureRelativePath, List<NetTextureDefinition> textures, List<NetBoneDefinition> bones, List<NetCubeDefinition> cubes, List<NetAnimationDefinition> animations) {
            this(modelId, sourceModId, displayName, formatVersion, textureWidth, textureHeight, primaryTextureRelativePath, textures, bones, cubes, List.of(), animations);
        }
        public NetModelDefinition { meshes = meshes == null ? List.of() : List.copyOf(meshes); }
        public BbModelDefinition toRuntime() { return new BbModelDefinition(modelId, sourceModId, displayName, Path.of("__synced__.bbmodel"), formatVersion, textureWidth, textureHeight, primaryTextureRelativePath, textures.stream().map(NetTextureDefinition::toRuntime).toList(), bones.stream().map(NetBoneDefinition::toRuntime).toList(), cubes.stream().map(NetCubeDefinition::toRuntime).toList(), meshes.stream().map(NetMeshDefinition::toRuntime).toList(), animations.stream().map(NetAnimationDefinition::toRuntime).toList()); }
    }
    public record NetTextureDefinition(String id, String name, String relativePath, int width, int height, String contentHash, String contentBase64) { public BbTextureDefinition toRuntime() { return new BbTextureDefinition(id, name, relativePath, width, height); } }
    public record NetBoneDefinition(String uuid, String name, String parentName, String parentUuid, String parentType, String role, List<String> tags, String physicsPreset, int cubeCount, float pivotX, float pivotY, float pivotZ, float rotationX, float rotationY, float rotationZ, Boolean visible, List<String> childBoneUuids) {
        public NetBoneDefinition {
            physicsPreset = physicsPreset == null ? "none" : physicsPreset;
        }
        public BbBoneDefinition toRuntime() { return new BbBoneDefinition(uuid, name, parentName, parentUuid, parentType, role, tags, physicsPreset, cubeCount, pivotX, pivotY, pivotZ, rotationX, rotationY, rotationZ, visible == null || visible, childBoneUuids); }
    }
    public record NetCubeDefinition(String name, String parentBoneUuid, float fromX, float fromY, float fromZ, float toX, float toY, float toZ, float originX, float originY, float originZ, float rotationX, float rotationY, float rotationZ, float inflate, Map<String, NetFaceUvDefinition> faces, int textureIndex, boolean mirror, Boolean visible) { public BbCubeDefinition toRuntime() { return new BbCubeDefinition(name, parentBoneUuid, fromX, fromY, fromZ, toX, toY, toZ, originX, originY, originZ, rotationX, rotationY, rotationZ, inflate, faces.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toRuntime())), textureIndex, mirror, visible == null || visible); } }
    public record NetFaceUvDefinition(float u1, float v1, float u2, float v2, int rotation, int textureIndex, boolean enabled) { public BbFaceUvDefinition toRuntime() { return new BbFaceUvDefinition(u1, v1, u2, v2, rotation, textureIndex, enabled); } }
    public record NetMeshDefinition(String uuid, String name, String parentBoneUuid, float originX, float originY, float originZ, float rotationX, float rotationY, float rotationZ, Map<String, NetMeshVertexDefinition> vertices, List<NetMeshFaceDefinition> faces, Boolean visible) {
        public static NetMeshDefinition from(BbMeshDefinition mesh) {
            return new NetMeshDefinition(mesh.uuid(), mesh.name(), mesh.parentBoneUuid(), mesh.originX(), mesh.originY(), mesh.originZ(), mesh.rotationX(), mesh.rotationY(), mesh.rotationZ(),
                mesh.vertices().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> NetMeshVertexDefinition.from(entry.getValue()), (left, right) -> left, LinkedHashMap::new)),
                mesh.faces().stream().map(NetMeshFaceDefinition::from).toList(), mesh.visible());
        }
        public BbMeshDefinition toRuntime() {
            return new BbMeshDefinition(uuid, name, parentBoneUuid, originX, originY, originZ, rotationX, rotationY, rotationZ,
                vertices.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toRuntime(), (left, right) -> left, LinkedHashMap::new)),
                faces.stream().map(NetMeshFaceDefinition::toRuntime).toList(), visible == null || visible);
        }
    }
    public record NetMeshVertexDefinition(String id, float x, float y, float z) {
        public static NetMeshVertexDefinition from(BbMeshVertexDefinition vertex) { return new NetMeshVertexDefinition(vertex.id(), vertex.x(), vertex.y(), vertex.z()); }
        public BbMeshVertexDefinition toRuntime() { return new BbMeshVertexDefinition(id, x, y, z); }
    }
    public record NetMeshFaceDefinition(String id, List<String> vertexIds, Map<String, NetMeshUvDefinition> uvByVertex, int textureIndex, Boolean enabled) {
        public static NetMeshFaceDefinition from(BbMeshFaceDefinition face) {
            return new NetMeshFaceDefinition(face.id(), face.vertexIds(), face.uvByVertex().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> NetMeshUvDefinition.from(entry.getValue()), (left, right) -> left, LinkedHashMap::new)), face.textureIndex(), face.enabled());
        }
        public BbMeshFaceDefinition toRuntime() {
            return new BbMeshFaceDefinition(id, vertexIds, uvByVertex.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toRuntime(), (left, right) -> left, LinkedHashMap::new)), textureIndex, enabled == null || enabled);
        }
    }
    public record NetMeshUvDefinition(float u, float v) {
        public static NetMeshUvDefinition from(BbMeshUvDefinition uv) { return new NetMeshUvDefinition(uv.u(), uv.v()); }
        public BbMeshUvDefinition toRuntime() { return new BbMeshUvDefinition(u, v); }
    }
    public record NetAnimationDefinition(String name, double lengthSeconds, boolean looping, int animatorCount, Map<String, NetBoneAnimation> boneAnimations, List<String> affectedBones) { public BbAnimationDefinition toRuntime() { return new BbAnimationDefinition(name, lengthSeconds, looping, animatorCount, boneAnimations.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toRuntime())), affectedBones); } }
    public record NetBoneAnimation(String boneUuid, List<NetKeyframe> rotation, List<NetKeyframe> position, List<NetKeyframe> scale, boolean rotationGlobal, boolean quaternionInterpolation) {
        public NetBoneAnimation(String boneUuid, List<NetKeyframe> rotation, List<NetKeyframe> position, List<NetKeyframe> scale) { this(boneUuid, rotation, position, scale, false, false); }
        public BbBoneAnimation toRuntime() { return new BbBoneAnimation(boneUuid, rotation.stream().map(NetKeyframe::toRuntime).toList(), position.stream().map(NetKeyframe::toRuntime).toList(), scale.stream().map(NetKeyframe::toRuntime).toList(), rotationGlobal, quaternionInterpolation); }
    }
    public record NetKeyframe(float time, BbKeyframePoint pre, BbKeyframePoint post, String easing, BbBezierData bezier) {
        public NetKeyframe(float time, float x, float y, float z, String easing) { this(time, BbKeyframePoint.numeric(x, y, z), BbKeyframePoint.numeric(x, y, z), easing, BbBezierData.NONE); }
        public BbKeyframe toRuntime() { return new BbKeyframe(time, pre, post, easing, bezier); }
    }
    public record NetAvatarVars(String playerId, String avatarId, Map<String, Object> values) {}

    // =========================================================================
    // CustomPacketPayload Implementations
    // =========================================================================

    /**
     * General-purpose JSON envelope payload supporting optional transparent GZIP/deflate
     * compression when payload size exceeds {@link PacketCompressor#COMPRESSION_THRESHOLD}.
     */
    public record JsonPayload(CustomPacketPayload.Type<JsonPayload> payloadId, String json) implements CustomPacketPayload {
        public static StreamCodec<RegistryFriendlyByteBuf, JsonPayload> codec(CustomPacketPayload.Type<JsonPayload> payloadId) {
            return new StreamCodec<RegistryFriendlyByteBuf, JsonPayload>() {
                @Override
                public JsonPayload decode(RegistryFriendlyByteBuf buf) {
                    byte mode = buf.readByte();
                    if (mode == 1) {
                        int length = buf.readVarInt();
                        if (length < 0 || length > MAX_AVATAR_JSON_CHARS) throw new IllegalArgumentException("Payload compressed length out of bounds: " + length);
                        byte[] bytes = new byte[length];
                        buf.readBytes(bytes);
                        String json = PacketCompressor.decompress(bytes);
                        return new JsonPayload(payloadId, json != null ? json : "{}");
                    } else if (mode == 0) {
                        int length = buf.readVarInt();
                        if (length < 0 || length > MAX_AVATAR_JSON_CHARS) throw new IllegalArgumentException("Payload length out of bounds: " + length);
                        byte[] bytes = new byte[length];
                        buf.readBytes(bytes);
                        return new JsonPayload(payloadId, new String(bytes, StandardCharsets.UTF_8));
                    } else {
                        throw new IllegalArgumentException("Unknown payload encoding mode: " + mode);
                    }
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, JsonPayload payload) {
                    String json = payload.json() == null ? "{}" : payload.json();
                    PacketCompressor.CompressedPayload compressed = PacketCompressor.compressIfBeneficial(json, PacketCompressor.COMPRESSION_THRESHOLD);
                    if (compressed != null && compressed.compressed()) {
                        buf.writeByte(1);
                        buf.writeVarInt(compressed.data().length);
                        buf.writeBytes(compressed.data());
                    } else {
                        buf.writeByte(0);
                        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
                        buf.writeVarInt(raw.length);
                        buf.writeBytes(raw);
                    }
                }
            };
        }
        @Override public CustomPacketPayload.Type<JsonPayload> type() { return payloadId; }
    }
    public record ProtocolHelloPayload(int protocolVersion, String modVersion) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, ProtocolHelloPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ProtocolHelloPayload::protocolVersion,
            ByteBufCodecs.STRING_UTF8, ProtocolHelloPayload::modVersion,
            ProtocolHelloPayload::new
        ).cast();
        @Override public CustomPacketPayload.Type<ProtocolHelloPayload> type() { return PROTOCOL_HELLO_PAYLOAD; }
    }
    public record AvatarSyncRequestPayload(String playerId) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, AvatarSyncRequestPayload> CODEC =
            ByteBufCodecs.STRING_UTF8.map(AvatarSyncRequestPayload::new, AvatarSyncRequestPayload::playerId).cast();
        @Override public CustomPacketPayload.Type<AvatarSyncRequestPayload> type() { return AVATAR_SYNC_REQUEST_PAYLOAD; }
    }
    public record AvatarVarSetPayload(String avatarId, Map<String, String> values) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, AvatarVarSetPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, AvatarVarSetPayload::avatarId, ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), AvatarVarSetPayload::values, AvatarVarSetPayload::new).cast();
        @Override public CustomPacketPayload.Type<AvatarVarSetPayload> type() { return AVATAR_VAR_SET_PAYLOAD; }
    }
    public record SkillKeyPayload(String skill, int slot) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, SkillKeyPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SkillKeyPayload::skill, ByteBufCodecs.INT, SkillKeyPayload::slot, SkillKeyPayload::new).cast();
        @Override public CustomPacketPayload.Type<SkillKeyPayload> type() { return SKILL_KEY_PAYLOAD; }
    }
    public record AvatarChannelPayload(String senderId, String channel, String payloadJson) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, AvatarChannelPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AvatarChannelPayload::senderId,
            ByteBufCodecs.STRING_UTF8, AvatarChannelPayload::channel,
            ByteBufCodecs.STRING_UTF8, AvatarChannelPayload::payloadJson,
            AvatarChannelPayload::new
        ).cast();
        @Override public CustomPacketPayload.Type<AvatarChannelPayload> type() { return AVATAR_CHANNEL_PAYLOAD; }
    }
}

package seashyne.shynecore.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.animation.AnimationPlayback;
import seashyne.shynecore.animation.AnimationRuntime;
import seashyne.shynecore.attachment.AttachedModelState;
import seashyne.shynecore.attachment.AttachmentRuntime;
import seashyne.shynecore.avatar.AvatarValueCodec;
import seashyne.shynecore.equipment.EquipmentLoadout;
import seashyne.shynecore.equipment.EquipmentRuntime;
import seashyne.shynecore.equipment.WeaponDefinition;
import seashyne.shynecore.model.*;
import seashyne.shynecore.power.PowerState;
import seashyne.shynecore.power.PowerStateMachine;
import seashyne.shynecore.profile.PlayerProfile;
import seashyne.shynecore.profile.PlayerProfileRuntime;
import seashyne.shynecore.skill.SkillDefinition;
import seashyne.shynecore.skill.SkillExecutor;
import seashyne.shynecore.skill.SkillRegistry;
import seashyne.shynecore.skill.SkillSlot;

import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

public class ShyneNetwork implements BbModelRegistry.Listener, AnimationRuntime.Listener, AttachmentRuntime.Listener,
    PowerStateMachine.Listener, SkillRegistry.Listener, PlayerProfileRuntime.Listener, EquipmentRuntime.Listener {

    public static final int PROTOCOL_VERSION = 6;
    public static final String CAP_SERVER_AUTHORITATIVE_GAMEPLAY = "gameplay.server_authoritative";
    public static final String CAP_CONTENT_REGISTRY_SYNC = "content.registry_sync";
    public static final String CAP_AVATAR_PEER_SNAPSHOT = "avatar.peer_snapshot_v2";
    public static final String CAP_PLAYER_TAB_STATUS = "player.tab_status_v1";
    public static final List<String> SERVER_CAPABILITIES = List.of(
        CAP_SERVER_AUTHORITATIVE_GAMEPLAY,
        CAP_CONTENT_REGISTRY_SYNC,
        CAP_AVATAR_PEER_SNAPSHOT,
        CAP_PLAYER_TAB_STATUS
    );
    public static final Identifier PROTOCOL_HELLO = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "protocol_hello");
    public static final Identifier PROTOCOL_STATUS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "protocol_status");
    public static final Identifier SYNC_MODELS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_models");
    public static final Identifier PLAY_ANIMATION = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "play_animation");
    public static final Identifier STOP_ANIMATION = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "stop_animation");
    public static final Identifier SYNC_ACTIVE = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_active");
    public static final Identifier SYNC_ATTACHMENTS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_attachments");
    public static final Identifier SYNC_POWER = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_power");
    public static final Identifier SYNC_SKILLS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_skills");
    public static final Identifier SYNC_PROFILES = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_profiles");
    public static final Identifier SYNC_WEAPONS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_weapons");
    public static final Identifier SYNC_LOADOUTS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_loadouts");
    public static final Identifier SKILL_KEY = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "skill_key");
    public static final Identifier SYNC_AVATAR_VARS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_avatar_vars");
    public static final Identifier AVATAR_VAR_SET = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "avatar_var_set");
    public static final Identifier AVATAR_SNAPSHOT = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "avatar_snapshot");
    public static final Identifier SYNC_AVATAR_SNAPSHOTS = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_avatar_snapshots");
    public static final Identifier SYNC_PLAYER_PRESENCE = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "sync_player_presence");

    public static final CustomPacketPayload.Type<ProtocolHelloPayload> PROTOCOL_HELLO_PAYLOAD = new CustomPacketPayload.Type<>(PROTOCOL_HELLO);
    public static final CustomPacketPayload.Type<JsonPayload> PROTOCOL_STATUS_PAYLOAD = new CustomPacketPayload.Type<>(PROTOCOL_STATUS);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_MODELS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_MODELS);
    public static final CustomPacketPayload.Type<JsonPayload> PLAY_ANIMATION_PAYLOAD = new CustomPacketPayload.Type<>(PLAY_ANIMATION);
    public static final CustomPacketPayload.Type<JsonPayload> STOP_ANIMATION_PAYLOAD = new CustomPacketPayload.Type<>(STOP_ANIMATION);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_ACTIVE_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_ACTIVE);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_ATTACHMENTS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_ATTACHMENTS);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_POWER_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_POWER);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_SKILLS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_SKILLS);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_PROFILES_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_PROFILES);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_WEAPONS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_WEAPONS);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_LOADOUTS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_LOADOUTS);
    public static final CustomPacketPayload.Type<SkillKeyPayload> SKILL_KEY_PAYLOAD = new CustomPacketPayload.Type<>(SKILL_KEY);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_AVATAR_VARS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_AVATAR_VARS);
    public static final CustomPacketPayload.Type<AvatarVarSetPayload> AVATAR_VAR_SET_PAYLOAD = new CustomPacketPayload.Type<>(AVATAR_VAR_SET);
    public static final CustomPacketPayload.Type<JsonPayload> AVATAR_SNAPSHOT_PAYLOAD = new CustomPacketPayload.Type<>(AVATAR_SNAPSHOT);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_AVATAR_SNAPSHOTS_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_AVATAR_SNAPSHOTS);
    public static final CustomPacketPayload.Type<JsonPayload> SYNC_PLAYER_PRESENCE_PAYLOAD = new CustomPacketPayload.Type<>(SYNC_PLAYER_PRESENCE);

    public static final Gson GSON = new GsonBuilder().serializeNulls().create();
    public static final int MAX_TEXTURE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_AVATAR_TEXTURE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_AVATAR_JSON_CHARS = 2 * 1024 * 1024;
    private static final int MAX_AVATAR_PARTS = 1024;
    private static final int MAX_SYNCED_VARS = 64;
    private static final int MAX_MODEL_CUBES = 16_384;
    private static final int MAX_MODEL_BONES = 4_096;
    private static final int MAX_MODEL_ANIMATIONS = 512;
    private static final int MAX_MODEL_TEXTURES = 256;
    private static final long AVATAR_SNAPSHOT_INTERVAL_MS = 250L;
    private static final int HANDSHAKE_TIMEOUT_TICKS = 100;
    private static final Map<Identifier, CustomPacketPayload.Type<JsonPayload>> JSON_PAYLOAD_IDS = createJsonPayloadIds();
    private static boolean payloadsRegistered;

    private MinecraftServer server;
    private final BbModelRegistry registry;
    private final AnimationRuntime animationRuntime;
    private final AttachmentRuntime attachmentRuntime;
    private final PowerStateMachine powerStateMachine;
    private final SkillExecutor skillExecutor;
    private final SkillRegistry skillRegistry;
    private final PlayerProfileRuntime profileRuntime;
    private final EquipmentRuntime equipmentRuntime;
    private final Map<UUID, NetAvatarSnapshot> latestAvatarSnapshots = new HashMap<>();
    private final Map<UUID, Long> lastAvatarSnapshotAt = new HashMap<>();
    private final Map<UUID, Long> lastAvatarVarAt = new HashMap<>();
    private final Set<UUID> compatibleClients = new HashSet<>();
    private final Map<UUID, Integer> pendingHandshakes = new HashMap<>();
    private String lastBroadcastPresenceJson;

    public ShyneNetwork(BbModelRegistry registry, AnimationRuntime animationRuntime, AttachmentRuntime attachmentRuntime,
                        PowerStateMachine powerStateMachine, SkillExecutor skillExecutor, SkillRegistry skillRegistry,
                        PlayerProfileRuntime profileRuntime, EquipmentRuntime equipmentRuntime) {
        this.registry = registry;
        this.animationRuntime = animationRuntime;
        this.attachmentRuntime = attachmentRuntime;
        this.powerStateMachine = powerStateMachine;
        this.skillExecutor = skillExecutor;
        this.skillRegistry = skillRegistry;
        this.profileRuntime = profileRuntime;
        this.equipmentRuntime = equipmentRuntime;
        this.registry.addListener(this);
        this.animationRuntime.addListener(this);
        this.attachmentRuntime.addListener(this);
        this.powerStateMachine.addListener(this);
        this.skillRegistry.addListener(this);
        this.profileRuntime.addListener(this);
        this.equipmentRuntime.addListener(this);
    }

    public static void registerPayloads() {
        if (payloadsRegistered) return;
        for (CustomPacketPayload.Type<JsonPayload> payloadId : JSON_PAYLOAD_IDS.values()) PayloadTypeRegistry.clientboundPlay().register(payloadId, JsonPayload.codec(payloadId));
        PayloadTypeRegistry.serverboundPlay().register(PROTOCOL_HELLO_PAYLOAD, ProtocolHelloPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SKILL_KEY_PAYLOAD, SkillKeyPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AVATAR_VAR_SET_PAYLOAD, AvatarVarSetPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AVATAR_SNAPSHOT_PAYLOAD, JsonPayload.codec(AVATAR_SNAPSHOT_PAYLOAD));
        payloadsRegistered = true;
    }

    public void bindServer(MinecraftServer server) {
        this.server = server;
        this.lastBroadcastPresenceJson = null;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            if (s == this.server) {
                profileRuntime.ensurePlayer(handler.player);
                skillExecutor.ensurePlayer(handler.player);
                pendingHandshakes.put(handler.player.getUUID(), s.getTickCount() + HANDSHAKE_TIMEOUT_TICKS);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            if (s == this.server) {
                latestAvatarSnapshots.remove(handler.player.getUUID());
                lastAvatarSnapshotAt.remove(handler.player.getUUID());
                lastAvatarVarAt.remove(handler.player.getUUID());
                compatibleClients.remove(handler.player.getUUID());
                pendingHandshakes.remove(handler.player.getUUID());
                broadcastPlayerPresence();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s != this.server || pendingHandshakes.isEmpty()) return;
            Iterator<Map.Entry<UUID, Integer>> iterator = pendingHandshakes.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Integer> pending = iterator.next();
                if (s.getTickCount() < pending.getValue()) continue;
                ServerPlayer player = s.getPlayerList().getPlayer(pending.getKey());
                iterator.remove();
                if (player != null) player.connection.disconnect(Component.literal("Shyne Creator " + ShyneCore.VERSION + " is required to join this server."));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(PROTOCOL_HELLO_PAYLOAD, (payload, context) -> context.server().execute(() -> completeHandshake(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SKILL_KEY_PAYLOAD, (payload, context) -> context.server().execute(() -> {
            if (!compatibleClients.contains(context.player().getUUID())) return;
            SkillSlot mappedSlot = resolveSkillSlot(payload.skill(), payload.slot());
            String equippedSkill = profileRuntime.equippedSkill(context.player().getUUID(), mappedSlot);
            // The packet describes input intent only. The server-owned profile decides what can run.
            if (equippedSkill == null || equippedSkill.isBlank()) return;
            skillExecutor.execute(context.player(), equippedSkill, mappedSlot, payload.skill(), payload.slot());
        }));
        ServerPlayNetworking.registerGlobalReceiver(AVATAR_VAR_SET_PAYLOAD, (payload, context) -> context.server().execute(() -> {
            if (!compatibleClients.contains(context.player().getUUID())) return;
            UUID playerId = context.player().getUUID();
            long now = System.currentTimeMillis();
            if (now - lastAvatarVarAt.getOrDefault(playerId, 0L) < 50L) return;
            if (!isSafeId(payload.avatarId()) || payload.values() == null || payload.values().size() > MAX_SYNCED_VARS) return;
            if (payload.values().entrySet().stream().anyMatch(e -> !isSafeId(e.getKey()) || e.getValue() == null || e.getValue().length() > 4096)) return;
            NetAvatarSnapshot activeSnapshot = latestAvatarSnapshots.get(playerId);
            if (activeSnapshot == null || !payload.avatarId().equals(activeSnapshot.avatarId())) return;
            lastAvatarVarAt.put(playerId, now);
            NetAvatarVars vars = new NetAvatarVars(context.player().getStringUUID(), payload.avatarId(), AvatarValueCodec.decodeMap(payload.values()));
            broadcast(SYNC_AVATAR_VARS, GSON.toJson(new AvatarVarSyncPayload(List.of(vars))));
        }));
        ServerPlayNetworking.registerGlobalReceiver(AVATAR_SNAPSHOT_PAYLOAD, (payload, context) -> context.server().execute(() -> {
            if (!compatibleClients.contains(context.player().getUUID())) return;
            if (payload.json() == null || payload.json().length() > MAX_AVATAR_JSON_CHARS) return;
            UUID playerId = context.player().getUUID();
            long now = System.currentTimeMillis();
            if (now - lastAvatarSnapshotAt.getOrDefault(playerId, 0L) < AVATAR_SNAPSHOT_INTERVAL_MS) return;
            AvatarSnapshotSyncPayload snapshotPayload;
            try {
                snapshotPayload = GSON.fromJson(payload.json(), AvatarSnapshotSyncPayload.class);
            } catch (RuntimeException | StackOverflowError malformed) {
                return;
            }
            if (snapshotPayload == null || snapshotPayload.avatars() == null || snapshotPayload.avatars().size() != 1) return;
            NetAvatarSnapshot incoming = snapshotPayload.avatars().get(0);
            if (incoming != null && !incoming.onlineSync() && (incoming.avatarId() == null || incoming.avatarId().isBlank())) {
                lastAvatarSnapshotAt.put(playerId, now);
                latestAvatarSnapshots.remove(playerId);
                NetAvatarSnapshot cleared = new NetAvatarSnapshot(context.player().getStringUUID(), "", "", false, false, null, List.of(), Map.of(), Map.of(), "", 0L, List.of(), "", true);
                broadcast(SYNC_AVATAR_SNAPSHOTS, GSON.toJson(new AvatarSnapshotSyncPayload(List.of(cleared))));
                broadcastPlayerPresence();
                return;
            }
            NetAvatarSnapshot normalized = normalizeAvatarSnapshot(context.player(), incoming);
            if (normalized == null) return;
            lastAvatarSnapshotAt.put(playerId, now);
            latestAvatarSnapshots.put(playerId, normalized);
            NetAvatarSnapshot outgoing = snapshotPayload.avatars().get(0).model() == null
                ? new NetAvatarSnapshot(normalized.playerId(), normalized.avatarId(), normalized.modelId(), normalized.replaceVanilla(), true, null,
                    normalized.parts(), normalized.vanillaVisibility(), normalized.syncedVars(), normalized.currentAnimation(), normalized.animationStartedAtMillis(), normalized.animationLayers(), normalized.nameplateText(), normalized.nameplateVisible())
                : normalized;
            broadcast(SYNC_AVATAR_SNAPSHOTS, GSON.toJson(new AvatarSnapshotSyncPayload(List.of(outgoing))));
            broadcastPlayerPresence();
        }));
    }

    @Override public void onRegistryChanged(Collection<BbModelDefinition> allModels) { if (server != null) broadcast(SYNC_MODELS, GSON.toJson(new ModelSyncPayload(toNetModels(allModels)))); }
    @Override public void onPlay(AnimationPlayback playback) { if (server != null) broadcast(PLAY_ANIMATION, GSON.toJson(NetPlayback.from(playback))); }
    @Override public void onStop(UUID entityId) { if (server != null) broadcast(STOP_ANIMATION, GSON.toJson(new StopPayload(entityId.toString()))); }
    @Override public void onAttachmentSync(Collection<AttachedModelState> attachments) { if (server != null) broadcast(SYNC_ATTACHMENTS, GSON.toJson(new AttachmentSyncPayload(attachments.stream().map(NetAttachment::from).toList()))); }
    @Override public void onPowerStatesSync(Collection<PowerState> states) { if (server != null) broadcast(SYNC_POWER, GSON.toJson(new PowerSyncPayload(states.stream().map(NetPowerState::from).toList()))); }
    @Override public void onSkillRegistrySync(Collection<SkillDefinition> skills) { if (server != null) broadcast(SYNC_SKILLS, GSON.toJson(new SkillSyncPayload(skills.stream().map(NetSkillDefinition::from).toList()))); }
    @Override public void onProfileSync(Collection<PlayerProfile> profiles) { if (server != null) broadcast(SYNC_PROFILES, GSON.toJson(new ProfileSyncPayload(profiles.stream().map(NetPlayerProfile::from).toList()))); }
    @Override public void onWeaponRegistrySync(Collection<WeaponDefinition> weapons) { if (server != null) broadcast(SYNC_WEAPONS, GSON.toJson(new WeaponSyncPayload(weapons.stream().map(NetWeaponDefinition::from).toList()))); }
    @Override public void onLoadoutSync(Collection<EquipmentLoadout> loadouts) { if (server != null) broadcast(SYNC_LOADOUTS, GSON.toJson(new LoadoutSyncPayload(loadouts.stream().map(NetEquipmentLoadout::from).toList()))); }

    public void sendModelSync(ServerPlayer player) { send(player, SYNC_MODELS, GSON.toJson(new ModelSyncPayload(toNetModels(registry.all())))); }
    public void sendActiveSync(ServerPlayer player) { send(player, SYNC_ACTIVE, GSON.toJson(new ActiveSyncPayload(animationRuntime.allActive().stream().map(NetPlayback::from).toList()))); }
    public void sendAttachmentSync(ServerPlayer player) { send(player, SYNC_ATTACHMENTS, GSON.toJson(new AttachmentSyncPayload(attachmentRuntime.allAttached().stream().map(NetAttachment::from).toList()))); }
    public void sendPowerSync(ServerPlayer player) { send(player, SYNC_POWER, GSON.toJson(new PowerSyncPayload(powerStateMachine.all().stream().map(NetPowerState::from).toList()))); }
    public void sendSkillSync(ServerPlayer player) { send(player, SYNC_SKILLS, GSON.toJson(new SkillSyncPayload(skillRegistry.all().stream().map(NetSkillDefinition::from).toList()))); }
    public void sendProfileSync(ServerPlayer player) { send(player, SYNC_PROFILES, GSON.toJson(new ProfileSyncPayload(profileRuntime.all().stream().map(NetPlayerProfile::from).toList()))); }
    public void sendWeaponSync(ServerPlayer player) { send(player, SYNC_WEAPONS, GSON.toJson(new WeaponSyncPayload(equipmentRuntime.allWeapons().stream().map(NetWeaponDefinition::from).toList()))); }
    public void sendLoadoutSync(ServerPlayer player) { send(player, SYNC_LOADOUTS, GSON.toJson(new LoadoutSyncPayload(equipmentRuntime.allLoadouts().stream().map(NetEquipmentLoadout::from).toList()))); }
    public void sendAvatarVarSync(ServerPlayer player) { send(player, SYNC_AVATAR_VARS, GSON.toJson(new AvatarVarSyncPayload(List.of()))); }
    public void sendAvatarSnapshotSync(ServerPlayer player) { send(player, SYNC_AVATAR_SNAPSHOTS, GSON.toJson(new AvatarSnapshotSyncPayload(List.copyOf(latestAvatarSnapshots.values())))); }
    public void sendPlayerPresenceSync(ServerPlayer player) { send(player, SYNC_PLAYER_PRESENCE, GSON.toJson(playerPresenceSnapshot())); }

    private void broadcastPlayerPresence() {
        if (server == null) return;
        String json = GSON.toJson(playerPresenceSnapshot());
        if (json.equals(lastBroadcastPresenceJson)) return;
        lastBroadcastPresenceJson = json;
        broadcast(SYNC_PLAYER_PRESENCE, json);
    }

    private PlayerPresenceSyncPayload playerPresenceSnapshot() {
        List<NetPlayerPresence> players = compatibleClients.stream()
            .sorted()
            .map(playerId -> new NetPlayerPresence(playerId.toString(), latestAvatarSnapshots.containsKey(playerId)))
            .toList();
        return new PlayerPresenceSyncPayload(players);
    }

    private List<NetModelDefinition> toNetModels(Collection<BbModelDefinition> models) {
        return models.stream().map(m -> new NetModelDefinition(
            m.modelId(), m.sourceModId(), m.displayName(), m.formatVersion(), m.textureWidth(), m.textureHeight(), m.primaryTextureRelativePath(),
            m.textures().stream().map(t -> toNetTexture(m, t)).toList(),
            m.bones().stream().map(b -> new NetBoneDefinition(b.uuid(), b.name(), b.parentName(), b.parentUuid(), b.cubeCount(), b.pivotX(), b.pivotY(), b.pivotZ(), b.rotationX(), b.rotationY(), b.rotationZ(), b.childBoneUuids())).toList(),
            m.cubes().stream().map(c -> new NetCubeDefinition(c.name(), c.parentBoneUuid(), c.fromX(), c.fromY(), c.fromZ(), c.toX(), c.toY(), c.toZ(), c.originX(), c.originY(), c.originZ(), c.rotationX(), c.rotationY(), c.rotationZ(), c.inflate(),
                c.faces().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new NetFaceUvDefinition(e.getValue().u1(), e.getValue().v1(), e.getValue().u2(), e.getValue().v2(), e.getValue().rotation(), e.getValue().textureIndex(), e.getValue().enabled()))),
                c.textureIndex(), c.mirror())).toList(),
            m.animations().stream().map(a -> new NetAnimationDefinition(a.name(), a.lengthSeconds(), a.looping(), a.animatorCount(),
                a.boneAnimations().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    BbBoneAnimation v = e.getValue();
                    return new NetBoneAnimation(v.boneUuid(), toNetKeys(v.rotation()), toNetKeys(v.position()), toNetKeys(v.scale()));
                })), a.affectedBones())).toList()
        )).toList();
    }

    private List<NetKeyframe> toNetKeys(List<BbKeyframe> keys) { return keys.stream().map(k -> new NetKeyframe(k.time(), k.x(), k.y(), k.z(), k.easing())).toList(); }

    public static NetTextureDefinition toNetTexture(BbModelDefinition model, BbTextureDefinition texture) {
        byte[] bytes = readTextureBytes(model, texture);
        String hash = bytes == null ? "" : HexFormat.of().formatHex(sha256(bytes));
        String content = bytes == null ? "" : Base64.getEncoder().encodeToString(bytes);
        return new NetTextureDefinition(texture.id(), texture.name(), texture.relativePath(), texture.width(), texture.height(), hash, content);
    }

    private static byte[] readTextureBytes(BbModelDefinition model, BbTextureDefinition texture) {
        if (model == null || model.sourceFile() == null || texture == null || texture.relativePath() == null) return null;
        Path modelRoot = model.sourceFile().toAbsolutePath().normalize().getParent();
        if (modelRoot == null) return null;
        Path allowedRoot = modelRoot.getParent() == null ? modelRoot : modelRoot.getParent();
        Path candidate = modelRoot.resolve(texture.relativePath().replace('/', java.io.File.separatorChar)).normalize();
        Path fallback = modelRoot.resolve("textures").resolve(Path.of(texture.relativePath()).getFileName()).normalize();
        try {
            Path selected = Files.isRegularFile(candidate) && candidate.startsWith(allowedRoot) ? candidate : fallback;
            if (!selected.startsWith(allowedRoot) || !Files.isRegularFile(selected)) return null;
            long size = Files.size(selected);
            if (size <= 0 || size > MAX_TEXTURE_BYTES) return null;
            byte[] bytes = Files.readAllBytes(selected);
            return isPng(bytes) ? bytes : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private NetAvatarSnapshot normalizeAvatarSnapshot(ServerPlayer player, NetAvatarSnapshot incoming) {
        if (incoming == null || !incoming.onlineSync() || !isSafeId(incoming.avatarId())) return null;
        if (incoming.parts() != null && incoming.parts().size() > MAX_AVATAR_PARTS) return null;
        if (incoming.parts() != null && incoming.parts().stream().anyMatch(p -> !isSafePart(p))) return null;
        if (incoming.syncedVars() != null && incoming.syncedVars().size() > MAX_SYNCED_VARS) return null;
        if (incoming.currentAnimation() != null && incoming.currentAnimation().length() > 128) return null;
        if (incoming.animationLayers() != null && (incoming.animationLayers().size() > 64 || incoming.animationLayers().stream().anyMatch(layer -> !isSafeAnimationLayer(layer)))) return null;
        if (incoming.nameplateText() != null && incoming.nameplateText().length() > 128) return null;

        NetAvatarSnapshot previous = latestAvatarSnapshots.get(player.getUUID());
        String modelId = "remote:" + player.getStringUUID() + ":" + incoming.avatarId();
        NetModelDefinition model = incoming.model();
        if (model == null) {
            if (previous == null || !previous.avatarId().equals(incoming.avatarId())) return null;
            model = previous.model();
        } else {
            if (!isSafeModel(model)) return null;
            model = new NetModelDefinition(
                modelId, incoming.avatarId(), model.displayName(), model.formatVersion(), model.textureWidth(), model.textureHeight(),
                model.primaryTextureRelativePath(), model.textures(), model.bones(), model.cubes(), model.animations()
            );
        }
        if (model == null) return null;

        return new NetAvatarSnapshot(
            player.getStringUUID(), incoming.avatarId(), modelId, incoming.replaceVanilla(), true, model,
            incoming.parts() == null ? List.of() : List.copyOf(incoming.parts()),
            incoming.vanillaVisibility() == null ? Map.of() : Map.copyOf(incoming.vanillaVisibility()),
            incoming.syncedVars() == null ? Map.of() : Map.copyOf(incoming.syncedVars()),
            incoming.currentAnimation() == null ? "" : incoming.currentAnimation(), incoming.animationStartedAtMillis(),
            incoming.animationLayers() == null ? List.of() : List.copyOf(incoming.animationLayers()),
            incoming.nameplateText() == null ? "" : incoming.nameplateText(), incoming.nameplateVisible()
        );
    }

    private static boolean isSafeModel(NetModelDefinition model) {
        if (model.bones() == null || model.cubes() == null || model.animations() == null || model.textures() == null) return false;
        if (model.bones().size() > MAX_MODEL_BONES || model.cubes().size() > MAX_MODEL_CUBES || model.animations().size() > MAX_MODEL_ANIMATIONS || model.textures().size() > MAX_MODEL_TEXTURES) return false;
        if (model.textureWidth() <= 0 || model.textureWidth() > 8192 || model.textureHeight() <= 0 || model.textureHeight() > 8192) return false;
        long totalTextureBytes = 0;
        for (NetTextureDefinition texture : model.textures()) {
            if (texture == null || texture.contentHash() == null || texture.contentBase64() == null || texture.contentBase64().length() > (MAX_TEXTURE_BYTES * 4 / 3) + 8) return false;
            if (texture.contentHash().length() != 64 || texture.contentBase64().isBlank()) return false;
            try {
                byte[] bytes = Base64.getDecoder().decode(texture.contentBase64());
                if (bytes.length > MAX_TEXTURE_BYTES || !isPng(bytes)) return false;
                totalTextureBytes += bytes.length;
                if (totalTextureBytes > MAX_AVATAR_TEXTURE_BYTES) return false;
                if (!texture.contentHash().isBlank() && !texture.contentHash().equalsIgnoreCase(HexFormat.of().formatHex(sha256(bytes)))) return false;
            } catch (IllegalArgumentException error) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafeId(String value) {
        return value != null && value.length() >= 1 && value.length() <= 64 && value.matches("[A-Za-z0-9_.:-]+");
    }

    private static boolean isSafePart(NetAvatarPart part) {
        if (part == null || part.path() == null || part.path().length() > 256 || !part.path().matches("[A-Za-z0-9_.:-]+")) return false;
        return finiteBounded(part.posX()) && finiteBounded(part.posY()) && finiteBounded(part.posZ())
            && finiteBounded(part.rotX()) && finiteBounded(part.rotY()) && finiteBounded(part.rotZ())
            && finiteBounded(part.scaleX()) && finiteBounded(part.scaleY()) && finiteBounded(part.scaleZ());
    }

    private static boolean isSafeAnimationLayer(NetAvatarAnimation layer) {
        return layer != null && layer.name() != null && layer.name().length() <= 128
            && Double.isFinite(layer.speed()) && layer.speed() >= 0.01 && layer.speed() <= 8
            && Double.isFinite(layer.weight()) && layer.weight() >= 0 && layer.weight() <= 1
            && layer.priority() >= -1000 && layer.priority() <= 1000
            && layer.fadeInTicks() >= 0 && layer.fadeInTicks() <= 1200
            && layer.fadeOutTicks() >= 0 && layer.fadeOutTicks() <= 1200
            && (layer.mask() == null || (layer.mask().size() <= 256 && layer.mask().stream().allMatch(value -> value != null && value.length() <= 256)));
    }

    private static boolean finiteBounded(float value) {
        return Float.isFinite(value) && Math.abs(value) <= 10_000f;
    }

    private static boolean isPng(byte[] bytes) {
        return bytes != null && bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
            && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void completeHandshake(ServerPlayer player, ProtocolHelloPayload hello) {
        boolean protocolMatches = hello.protocolVersion() == PROTOCOL_VERSION;
        boolean versionMatches = ShyneCore.VERSION.equals(hello.modVersion());
        if (!protocolMatches || !versionMatches) {
            String reason = "Incompatible Shyne Creator: server=" + ShyneCore.VERSION + " (protocol " + PROTOCOL_VERSION
                + "), client=" + hello.modVersion() + " (protocol " + hello.protocolVersion() + ")";
            sendProtocolStatus(player, false, reason);
            pendingHandshakes.remove(player.getUUID());
            player.connection.disconnect(Component.literal(reason));
            return;
        }

        pendingHandshakes.remove(player.getUUID());
        compatibleClients.add(player.getUUID());
        sendProtocolStatus(player, true, "Shyne protocol ready");
        sendInitialSync(player);
        broadcastPlayerPresence();
        ShyneCore.LOGGER.info("[ShyneNetwork] Protocol ready for {}: mod={} protocol={}", player.getGameProfile().name(), hello.modVersion(), hello.protocolVersion());
    }

    private void sendInitialSync(ServerPlayer player) {
        sendModelSync(player);
        sendActiveSync(player);
        sendAttachmentSync(player);
        sendPowerSync(player);
        sendSkillSync(player);
        sendProfileSync(player);
        sendWeaponSync(player);
        sendLoadoutSync(player);
        sendAvatarVarSync(player);
        sendAvatarSnapshotSync(player);
    }

    private void sendProtocolStatus(ServerPlayer player, boolean accepted, String message) {
        send(player, PROTOCOL_STATUS, GSON.toJson(new ProtocolStatus(accepted, PROTOCOL_VERSION, ShyneCore.VERSION, message, SERVER_CAPABILITIES)));
    }

    private void broadcast(Identifier channel, String json) { for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player, channel, json); }
    private void send(ServerPlayer player, Identifier channel, String json) {
        CustomPacketPayload.Type<JsonPayload> payloadId = JSON_PAYLOAD_IDS.get(channel);
        if (payloadId == null) throw new IllegalArgumentException("Unregistered Shyne payload channel: " + channel);
        if (!channel.equals(PROTOCOL_STATUS) && !compatibleClients.contains(player.getUUID())) return;
        if (ServerPlayNetworking.canSend(player, payloadId)) ServerPlayNetworking.send(player, new JsonPayload(payloadId, json));
    }

    private static Map<Identifier, CustomPacketPayload.Type<JsonPayload>> createJsonPayloadIds() {
        Map<Identifier, CustomPacketPayload.Type<JsonPayload>> ids = new LinkedHashMap<>();
        ids.put(PROTOCOL_STATUS, PROTOCOL_STATUS_PAYLOAD);
        ids.put(SYNC_MODELS, SYNC_MODELS_PAYLOAD);
        ids.put(PLAY_ANIMATION, PLAY_ANIMATION_PAYLOAD);
        ids.put(STOP_ANIMATION, STOP_ANIMATION_PAYLOAD);
        ids.put(SYNC_ACTIVE, SYNC_ACTIVE_PAYLOAD);
        ids.put(SYNC_ATTACHMENTS, SYNC_ATTACHMENTS_PAYLOAD);
        ids.put(SYNC_POWER, SYNC_POWER_PAYLOAD);
        ids.put(SYNC_SKILLS, SYNC_SKILLS_PAYLOAD);
        ids.put(SYNC_PROFILES, SYNC_PROFILES_PAYLOAD);
        ids.put(SYNC_WEAPONS, SYNC_WEAPONS_PAYLOAD);
        ids.put(SYNC_LOADOUTS, SYNC_LOADOUTS_PAYLOAD);
        ids.put(SYNC_AVATAR_VARS, SYNC_AVATAR_VARS_PAYLOAD);
        ids.put(AVATAR_SNAPSHOT, AVATAR_SNAPSHOT_PAYLOAD);
        ids.put(SYNC_AVATAR_SNAPSHOTS, SYNC_AVATAR_SNAPSHOTS_PAYLOAD);
        ids.put(SYNC_PLAYER_PRESENCE, SYNC_PLAYER_PRESENCE_PAYLOAD);
        return Map.copyOf(ids);
    }

    private SkillSlot resolveSkillSlot(String skill, int slot) {
        if (slot == 1) return SkillSlot.PRIMARY;
        if (slot == 2) return SkillSlot.SECONDARY;
        if (slot == 3) return SkillSlot.UTILITY;
        if (slot == 4) return SkillSlot.ULTIMATE;
        String normalized = skill == null ? "" : skill.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "light", "primary" -> SkillSlot.PRIMARY;
            case "heavy", "secondary" -> SkillSlot.SECONDARY;
            case "utility" -> SkillSlot.UTILITY;
            case "finisher", "ultimate" -> SkillSlot.ULTIMATE;
            default -> SkillSlot.fromString(skill);
        };
    }

    public record ModelSyncPayload(List<NetModelDefinition> models) {}
    public record ActiveSyncPayload(List<NetPlayback> playbacks) {}
    public record AttachmentSyncPayload(List<NetAttachment> attachments) {}
    public record PowerSyncPayload(List<NetPowerState> states) {}
    public record SkillSyncPayload(List<NetSkillDefinition> skills) {}
    public record ProfileSyncPayload(List<NetPlayerProfile> profiles) {}
    public record WeaponSyncPayload(List<NetWeaponDefinition> weapons) {}
    public record LoadoutSyncPayload(List<NetEquipmentLoadout> loadouts) {}
    public record AvatarVarSyncPayload(List<NetAvatarVars> vars) {}
    public record AvatarSnapshotSyncPayload(List<NetAvatarSnapshot> avatars) {}
    public record PlayerPresenceSyncPayload(List<NetPlayerPresence> players) {}
    public record ProtocolStatus(boolean accepted, int protocolVersion, String modVersion, String message, List<String> capabilities) {}
    public record StopPayload(String entityId) {}

    public record NetPlayback(String entityId, String entityName, String modelId, String animationName, long startedAtMillis, double lengthSeconds, boolean looping) { public static NetPlayback from(AnimationPlayback playback) { return new NetPlayback(playback.entityId().toString(), playback.entityName(), playback.modelId(), playback.animationName(), playback.startedAtMillis(), playback.lengthSeconds(), playback.looping()); } }
    public record NetAttachment(String entityId, String entityName, String modelId, float offsetX, float offsetY, float offsetZ, float scale, String anchorBone, boolean visible) { public static NetAttachment from(AttachedModelState state) { return new NetAttachment(state.entityId().toString(), state.entityName(), state.modelId(), state.offsetX(), state.offsetY(), state.offsetZ(), state.scale(), state.anchorBone(), state.visible()); } public AttachedModelState toRuntime() { return new AttachedModelState(UUID.fromString(entityId), entityName, modelId, offsetX, offsetY, offsetZ, scale, anchorBone, visible); } }
    public record NetPowerState(String entityId, String comboId, int stage, String branch, long startedAtMillis, long updatedAtMillis, long resetAtMillis, long cooldownEndsAtMillis, double mana, double maxMana, String currentAnimation, boolean locked) { public static NetPowerState from(PowerState state) { return new NetPowerState(state.entityId().toString(), state.comboId(), state.stage(), state.branch(), state.startedAtMillis(), state.updatedAtMillis(), state.resetAtMillis(), state.cooldownEndsAtMillis(), state.mana(), state.maxMana(), state.currentAnimation(), state.locked()); } public PowerState toRuntime() { return new PowerState(UUID.fromString(entityId), comboId, stage, branch, startedAtMillis, updatedAtMillis, resetAtMillis, cooldownEndsAtMillis, mana, maxMana, currentAnimation, locked); } }
    public record NetSkillDefinition(String skillId, String displayName, String castType, String defaultSlot, double manaCost, int cooldownTicks, String modelId, String animation, List<String> tags) { public static NetSkillDefinition from(SkillDefinition def) { return new NetSkillDefinition(def.skillId(), def.displayName(), def.castType().name(), def.defaultSlot().name(), def.manaCost(), def.cooldownTicks(), def.modelId(), def.animation(), def.tags()); } }
    public record NetPlayerProfile(String playerId, String playerName, int level, long experience, int statPoints, int skillPoints, String playerClass, List<String> unlockedSkills, Map<String, String> equippedSkills, Map<String, Integer> attributes, String teamId, long updatedAtMillis) { public static NetPlayerProfile from(PlayerProfile profile) { return new NetPlayerProfile(profile.playerId().toString(), profile.playerName(), profile.level(), profile.experience(), profile.statPoints(), profile.skillPoints(), profile.playerClass(), profile.unlockedSkills(), profile.equippedSkills(), profile.attributes(), profile.teamId(), profile.updatedAtMillis()); } }
    public record NetWeaponDefinition(String weaponId, String displayName, String itemId, String modelId, String classTag, List<String> grantedSkills, Map<String, Double> statModifiers) { public static NetWeaponDefinition from(WeaponDefinition definition) { return new NetWeaponDefinition(definition.weaponId(), definition.displayName(), definition.itemId(), definition.modelId(), definition.classTag(), definition.grantedSkills(), definition.statModifiers()); } }
    public record NetEquipmentLoadout(String entityId, String mainHandWeaponId, String offHandWeaponId, Map<String, String> slots, long updatedAtMillis) { public static NetEquipmentLoadout from(EquipmentLoadout loadout) { return new NetEquipmentLoadout(loadout.entityId().toString(), loadout.mainHandWeaponId(), loadout.offHandWeaponId(), loadout.slots(), loadout.updatedAtMillis()); } }
    public record NetAvatarPart(String path, boolean visible, float posX, float posY, float posZ, float rotX, float rotY, float rotZ, float scaleX, float scaleY, float scaleZ, boolean positionControlled, boolean rotationControlled, boolean scaleControlled, int colorArgb, boolean emissive) {}
    public record NetAvatarAnimation(String name, long startedAtMillis, double lengthSeconds, boolean looping, double speed, double weight, int priority, int fadeInTicks, int fadeOutTicks, List<String> mask, boolean additive, long stoppingAtMillis) {}
    public record NetAvatarSnapshot(String playerId, String avatarId, String modelId, boolean replaceVanilla, boolean onlineSync, NetModelDefinition model, List<NetAvatarPart> parts, Map<String, Boolean> vanillaVisibility, Map<String, Object> syncedVars, String currentAnimation, long animationStartedAtMillis, List<NetAvatarAnimation> animationLayers, String nameplateText, boolean nameplateVisible) {}
    public record NetPlayerPresence(String playerId, boolean avatarAvailable) {}
    public record NetModelDefinition(String modelId, String sourceModId, String displayName, int formatVersion, int textureWidth, int textureHeight, String primaryTextureRelativePath, List<NetTextureDefinition> textures, List<NetBoneDefinition> bones, List<NetCubeDefinition> cubes, List<NetAnimationDefinition> animations) { public BbModelDefinition toRuntime() { return new BbModelDefinition(modelId, sourceModId, displayName, Path.of("__synced__.bbmodel"), formatVersion, textureWidth, textureHeight, primaryTextureRelativePath, textures.stream().map(NetTextureDefinition::toRuntime).toList(), bones.stream().map(NetBoneDefinition::toRuntime).toList(), cubes.stream().map(NetCubeDefinition::toRuntime).toList(), animations.stream().map(NetAnimationDefinition::toRuntime).toList()); } }
    public record NetTextureDefinition(String id, String name, String relativePath, int width, int height, String contentHash, String contentBase64) { public BbTextureDefinition toRuntime() { return new BbTextureDefinition(id, name, relativePath, width, height); } }
    public record NetBoneDefinition(String uuid, String name, String parentName, String parentUuid, int cubeCount, float pivotX, float pivotY, float pivotZ, float rotationX, float rotationY, float rotationZ, List<String> childBoneUuids) { public BbBoneDefinition toRuntime() { return new BbBoneDefinition(uuid, name, parentName, parentUuid, cubeCount, pivotX, pivotY, pivotZ, rotationX, rotationY, rotationZ, childBoneUuids); } }
    public record NetCubeDefinition(String name, String parentBoneUuid, float fromX, float fromY, float fromZ, float toX, float toY, float toZ, float originX, float originY, float originZ, float rotationX, float rotationY, float rotationZ, float inflate, Map<String, NetFaceUvDefinition> faces, int textureIndex, boolean mirror) { public BbCubeDefinition toRuntime() { return new BbCubeDefinition(name, parentBoneUuid, fromX, fromY, fromZ, toX, toY, toZ, originX, originY, originZ, rotationX, rotationY, rotationZ, inflate, faces.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toRuntime())), textureIndex, mirror); } }
    public record NetFaceUvDefinition(float u1, float v1, float u2, float v2, int rotation, int textureIndex, boolean enabled) { public BbFaceUvDefinition toRuntime() { return new BbFaceUvDefinition(u1, v1, u2, v2, rotation, textureIndex, enabled); } }
    public record NetAnimationDefinition(String name, double lengthSeconds, boolean looping, int animatorCount, Map<String, NetBoneAnimation> boneAnimations, List<String> affectedBones) { public BbAnimationDefinition toRuntime() { return new BbAnimationDefinition(name, lengthSeconds, looping, animatorCount, boneAnimations.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toRuntime())), affectedBones); } }
    public record NetBoneAnimation(String boneUuid, List<NetKeyframe> rotation, List<NetKeyframe> position, List<NetKeyframe> scale) { public BbBoneAnimation toRuntime() { return new BbBoneAnimation(boneUuid, rotation.stream().map(NetKeyframe::toRuntime).toList(), position.stream().map(NetKeyframe::toRuntime).toList(), scale.stream().map(NetKeyframe::toRuntime).toList()); } }
    public record NetKeyframe(float time, float x, float y, float z, String easing) { public BbKeyframe toRuntime() { return new BbKeyframe(time, x, y, z, easing); } }
    public record NetAvatarVars(String playerId, String avatarId, Map<String, Object> values) {}

    public record JsonPayload(CustomPacketPayload.Type<JsonPayload> payloadId, String json) implements CustomPacketPayload {
        public static StreamCodec<RegistryFriendlyByteBuf, JsonPayload> codec(CustomPacketPayload.Type<JsonPayload> payloadId) { return ByteBufCodecs.stringUtf8(MAX_AVATAR_JSON_CHARS).map(json -> new JsonPayload(payloadId, json), JsonPayload::json).cast(); }
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
    public record AvatarVarSetPayload(String avatarId, Map<String, String> values) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, AvatarVarSetPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, AvatarVarSetPayload::avatarId, ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), AvatarVarSetPayload::values, AvatarVarSetPayload::new).cast();
        @Override public CustomPacketPayload.Type<AvatarVarSetPayload> type() { return AVATAR_VAR_SET_PAYLOAD; }
    }
    public record SkillKeyPayload(String skill, int slot) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, SkillKeyPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SkillKeyPayload::skill, ByteBufCodecs.INT, SkillKeyPayload::slot, SkillKeyPayload::new).cast();
        @Override public CustomPacketPayload.Type<SkillKeyPayload> type() { return SKILL_KEY_PAYLOAD; }
    }
}

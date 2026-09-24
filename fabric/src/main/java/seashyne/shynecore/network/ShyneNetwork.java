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
import seashyne.shynecore.avatar.AvatarAnimationClock;
import seashyne.shynecore.avatar.AvatarValueCodec;
import seashyne.shynecore.avatar.AvatarValueValidator;
import seashyne.shynecore.avatar.PngTextureValidator;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import static seashyne.shynecore.network.ShyneNetworkValidator.*;

public class ShyneNetwork implements BbModelRegistry.Listener, AnimationRuntime.Listener, AttachmentRuntime.Listener,
    PowerStateMachine.Listener, SkillRegistry.Listener, PlayerProfileRuntime.Listener, EquipmentRuntime.Listener {

    public static final int PROTOCOL_VERSION = 14;
    public static final String CAP_SERVER_AUTHORITATIVE_GAMEPLAY = "gameplay.server_authoritative";
    public static final String CAP_CONTENT_REGISTRY_SYNC = "content.registry_sync";
    public static final String CAP_AVATAR_PEER_SNAPSHOT = "avatar.peer_snapshot_v2";
    public static final String CAP_PLAYER_TAB_STATUS = "player.tab_status_v1";
    public static final String CAP_AVATAR_SNAPSHOT_REQUEST = "avatar.snapshot_request_v1";
    public static final String CAP_AVATAR_RECIPIENT_SUBSCRIPTIONS = "avatar.recipient_subscriptions_v1";
    public static final String CAP_AVATAR_BONE_PHYSICS = "avatar.bone_physics_v1";
    public static final List<String> SERVER_CAPABILITIES = List.of(
        CAP_SERVER_AUTHORITATIVE_GAMEPLAY,
        CAP_CONTENT_REGISTRY_SYNC,
        CAP_AVATAR_PEER_SNAPSHOT,
        CAP_PLAYER_TAB_STATUS,
        CAP_AVATAR_SNAPSHOT_REQUEST,
        CAP_AVATAR_RECIPIENT_SUBSCRIPTIONS,
        CAP_AVATAR_BONE_PHYSICS
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
    public static final Identifier AVATAR_SYNC_REQUEST = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "avatar_sync_request");

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
    public static final CustomPacketPayload.Type<AvatarSyncRequestPayload> AVATAR_SYNC_REQUEST_PAYLOAD = new CustomPacketPayload.Type<>(AVATAR_SYNC_REQUEST);

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
    // Native physics publishes every two client ticks (about 100 ms). Leave a
    // little scheduling headroom so a valid 10 Hz stream is not discarded.
    private static final long AVATAR_SNAPSHOT_INTERVAL_NANOS = 75_000_000L;
    private static final long AVATAR_FULL_SNAPSHOT_INTERVAL_NANOS = 1_000_000_000L;
    private static final long AVATAR_VAR_INTERVAL_NANOS = 50_000_000L;
    private static final long AVATAR_AUX_SYNC_INTERVAL_NANOS = 2_000_000_000L;
    private static final int MAX_AVATAR_SUBSCRIPTION_TARGETS = 256;
    private static final long AVATAR_INGRESS_CAPACITY_BYTES = 3L * MAX_AVATAR_JSON_CHARS;
    private static final long AVATAR_INGRESS_REFILL_BYTES_PER_SECOND = 2L * 1024 * 1024;
    private static final long AVATAR_FULL_CAPACITY_BYTES = MAX_AVATAR_JSON_CHARS;
    private static final long AVATAR_FULL_REFILL_BYTES_PER_SECOND = 512L * 1024;
    private static final long AVATAR_DELTA_CAPACITY_BYTES = 512L * 1024;
    private static final long AVATAR_DELTA_REFILL_BYTES_PER_SECOND = 1024L * 1024;
    private static final long AVATAR_REQUEST_CAPACITY_BYTES = 4L * MAX_AVATAR_JSON_CHARS;
    private static final long AVATAR_REQUEST_REFILL_BYTES_PER_SECOND = 1024L * 1024;
    private static final long AVATAR_OUTBOUND_CAPACITY_BYTES = 4L * MAX_AVATAR_JSON_CHARS;
    private static final long AVATAR_OUTBOUND_REFILL_BYTES_PER_SECOND = 2L * 1024 * 1024;
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
    private final Map<UUID, Long> lastAvatarSnapshotAtNanos = new HashMap<>();
    private final Map<UUID, Long> lastAvatarFullSnapshotAtNanos = new HashMap<>();
    private final Map<UUID, Long> lastAvatarVarAtNanos = new HashMap<>();
    private final Map<UUID, Long> lastAvatarAuxSyncAtNanos = new HashMap<>();
    private final Map<UUID, ByteRateLimiter> avatarIngressBytes = new HashMap<>();
    private final Map<UUID, ByteRateLimiter> avatarFullUploadBytes = new HashMap<>();
    private final Map<UUID, ByteRateLimiter> avatarDeltaUploadBytes = new HashMap<>();
    private final Map<UUID, ByteRateLimiter> avatarRequestBytes = new HashMap<>();
    private final Map<UUID, ByteRateLimiter> avatarRequestResponseBytes = new HashMap<>();
    private final Map<UUID, ByteRateLimiter> avatarOutboundBytes = new HashMap<>();
    private final Map<UUID, AvatarSubscriptions> avatarSubscriptions = new HashMap<>();
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
        PayloadTypeRegistry.serverboundPlay().register(AVATAR_SYNC_REQUEST_PAYLOAD, AvatarSyncRequestPayload.CODEC);
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
                NetAvatarSnapshot removedAvatar = latestAvatarSnapshots.remove(handler.player.getUUID());
                forgetClient(handler.player.getUUID());
                if (removedAvatar != null) broadcastAvatarClear(handler.player.getStringUUID());
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
            long nowNanos = System.nanoTime();
            Long previousNanos = lastAvatarVarAtNanos.get(playerId);
            if (previousNanos != null && nowNanos - previousNanos < AVATAR_VAR_INTERVAL_NANOS) return;
            lastAvatarVarAtNanos.put(playerId, nowNanos);
            if (!isSafeId(payload.avatarId()) || payload.values() == null || payload.values().size() > MAX_SYNCED_VARS) return;
            if (payload.values().entrySet().stream().anyMatch(e -> !isSafeId(e.getKey()) || e.getValue() == null || e.getValue().length() > 4096)) return;
            NetAvatarSnapshot activeSnapshot = latestAvatarSnapshots.get(playerId);
            if (activeSnapshot == null || !payload.avatarId().equals(activeSnapshot.avatarId())) return;
            Map<String, Object> decoded = AvatarValueCodec.decodeMap(payload.values());
            if (!isSafeSyncedVars(decoded)) return;
            Map<String, Object> safeValues = Map.copyOf(decoded);
            latestAvatarSnapshots.put(playerId, withSyncedVars(activeSnapshot, safeValues));
            NetAvatarVars vars = new NetAvatarVars(context.player().getStringUUID(), payload.avatarId(), safeValues);
            broadcastAvatarVars(playerId, GSON.toJson(new AvatarVarSyncPayload(List.of(vars))));
        }));
        ServerPlayNetworking.registerGlobalReceiver(AVATAR_SNAPSHOT_PAYLOAD, (payload, context) -> context.server().execute(() -> {
            if (!compatibleClients.contains(context.player().getUUID())) return;
            if (payload.json() == null) return;
            UUID playerId = context.player().getUUID();
            long nowNanos = System.nanoTime();
            int payloadBytes = utf8Length(payload.json());
            if (payloadBytes > MAX_AVATAR_JSON_CHARS || !limiter(avatarIngressBytes, playerId,
                AVATAR_INGRESS_CAPACITY_BYTES, AVATAR_INGRESS_REFILL_BYTES_PER_SECOND, nowNanos).tryConsume(payloadBytes, nowNanos)) return;
            Long previousNanos = lastAvatarSnapshotAtNanos.get(playerId);
            if (previousNanos != null && nowNanos - previousNanos < AVATAR_SNAPSHOT_INTERVAL_NANOS) return;
            lastAvatarSnapshotAtNanos.put(playerId, nowNanos);
            AvatarSnapshotSyncPayload snapshotPayload;
            try {
                snapshotPayload = GSON.fromJson(payload.json(), AvatarSnapshotSyncPayload.class);
            } catch (RuntimeException | StackOverflowError malformed) {
                return;
            }
            if (snapshotPayload == null || snapshotPayload.avatars() == null || snapshotPayload.avatars().size() != 1) return;
            if (snapshotPayload.revision() < 0L) return;
            NetAvatarSnapshot incoming = snapshotPayload.avatars().get(0);
            boolean fullSnapshot = incoming != null && incoming.model() != null;
            if (fullSnapshot) {
                Long previousFull = lastAvatarFullSnapshotAtNanos.get(playerId);
                if (previousFull != null && nowNanos - previousFull < AVATAR_FULL_SNAPSHOT_INTERVAL_NANOS) return;
                if (!limiter(avatarFullUploadBytes, playerId, AVATAR_FULL_CAPACITY_BYTES,
                    AVATAR_FULL_REFILL_BYTES_PER_SECOND, nowNanos).tryConsume(payloadBytes, nowNanos)) return;
                lastAvatarFullSnapshotAtNanos.put(playerId, nowNanos);
            } else if (!limiter(avatarDeltaUploadBytes, playerId, AVATAR_DELTA_CAPACITY_BYTES,
                AVATAR_DELTA_REFILL_BYTES_PER_SECOND, nowNanos).tryConsume(payloadBytes, nowNanos)) {
                return;
            }
            if (incoming != null && !incoming.onlineSync() && (incoming.avatarId() == null || incoming.avatarId().isBlank())) {
                latestAvatarSnapshots.remove(playerId);
                NetAvatarSnapshot cleared = new NetAvatarSnapshot(context.player().getStringUUID(), "", "", false, false, null, List.of(), Map.of(), Map.of(), "", 0L, List.of(), "", true);
                broadcastAvatarSnapshot(playerId, cleared, snapshotPayload.revision());
                broadcastPlayerPresence();
                return;
            }
            NetAvatarSnapshot normalized;
            try {
                normalized = normalizeAvatarSnapshot(context.player(), incoming);
            } catch (RuntimeException | StackOverflowError malformed) {
                return;
            }
            if (normalized == null) return;
            latestAvatarSnapshots.put(playerId, normalized);
            NetAvatarSnapshot outgoing = snapshotPayload.avatars().get(0).model() == null
                ? new NetAvatarSnapshot(normalized.playerId(), normalized.avatarId(), normalized.modelId(), normalized.replaceVanilla(), true, null,
                    normalized.parts(), normalized.vanillaVisibility(), normalized.syncedVars(), normalized.currentAnimation(), normalized.animationStartedAtMillis(), normalized.animationLayers(), normalized.animationParameters(), normalized.nameplateText(), normalized.nameplateVisible())
                : normalized;
            broadcastAvatarSnapshot(playerId, outgoing, snapshotPayload.revision());
            broadcastPlayerPresence();
        }));
        ServerPlayNetworking.registerGlobalReceiver(AVATAR_SYNC_REQUEST_PAYLOAD, (payload, context) ->
            context.server().execute(() -> handleAvatarSyncRequest(context.player(), payload))
        );
    }

    private void handleAvatarSyncRequest(ServerPlayer player, AvatarSyncRequestPayload payload) {
        if (!compatibleClients.contains(player.getUUID())) return;
        long nowNanos = System.nanoTime();
        String requested = payload.playerId() == null ? "" : payload.playerId().trim();
        int requestBytes = utf8Length(requested);
        if (requestBytes > MAX_AVATAR_SUBSCRIPTION_COMMAND_BYTES
            || !limiter(avatarRequestBytes, player.getUUID(), MAX_AVATAR_SUBSCRIPTION_COMMAND_BYTES * 2L,
                MAX_AVATAR_SUBSCRIPTION_COMMAND_BYTES, nowNanos).tryConsume(Math.max(1, requestBytes), nowNanos)) return;

        AvatarSubscriptions subscriptions = avatarSubscriptions.computeIfAbsent(player.getUUID(), ignored -> new AvatarSubscriptions());
        LinkedHashSet<UUID> targets = new LinkedHashSet<>();
        boolean newlySubscribed = false;
        if (requested.isBlank()) {
            subscriptions.subscribeAll();
            targets.addAll(latestAvatarSnapshots.keySet());
            newlySubscribed = true;
        } else {
            String[] commands = requested.split(",");
            if (commands.length > MAX_AVATAR_SUBSCRIPTION_TARGETS) return;
            for (String rawCommand : commands) {
                String command = rawCommand.trim();
                if (command.isEmpty()) continue;
                if (command.equals("!")) {
                    subscriptions.reset();
                    continue;
                }
                if (command.equals("*")) {
                    subscriptions.subscribeAll();
                    targets.addAll(latestAvatarSnapshots.keySet());
                    newlySubscribed = true;
                    continue;
                }
                char action = command.charAt(0);
                String encodedId = action == '+' || action == '-' ? command.substring(1) : command;
                UUID targetId;
                try {
                    targetId = UUID.fromString(encodedId);
                } catch (IllegalArgumentException malformed) {
                    continue;
                }
                if (action == '-') {
                    subscriptions.unsubscribe(targetId);
                } else {
                    newlySubscribed |= !subscriptions.isSubscribed(targetId);
                    subscriptions.subscribe(targetId);
                    targets.add(targetId);
                }
            }
        }

        if (newlySubscribed) {
            Long lastAux = lastAvatarAuxSyncAtNanos.get(player.getUUID());
            if (lastAux == null || nowNanos - lastAux >= AVATAR_AUX_SYNC_INTERVAL_NANOS) {
                lastAvatarAuxSyncAtNanos.put(player.getUUID(), nowNanos);
                sendAttachmentSync(player);
                sendActiveSync(player);
            }
        }
        long nowMillis = System.currentTimeMillis();
        for (UUID targetId : targets) {
            NetAvatarSnapshot snapshot = latestAvatarSnapshots.get(targetId);
            if (snapshot != null && subscriptions.isSubscribed(targetId)) {
                sendAvatarSnapshotPacket(player, encodeAnimationTimes(snapshot, nowMillis), 0L, true);
            }
        }
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
    public void sendAvatarSnapshotSync(ServerPlayer player) {
        long nowMillis = System.currentTimeMillis();
        AvatarSubscriptions subscriptions = avatarSubscriptions.computeIfAbsent(player.getUUID(), ignored -> new AvatarSubscriptions());
        for (Map.Entry<UUID, NetAvatarSnapshot> entry : latestAvatarSnapshots.entrySet()) {
            if (subscriptions.isSubscribed(entry.getKey())) {
                sendAvatarSnapshotPacket(player, encodeAnimationTimes(entry.getValue(), nowMillis), 0L, false);
            }
        }
    }

    private void broadcastAvatarClear(String playerId) {
        UUID ownerId;
        try {
            ownerId = UUID.fromString(playerId);
        } catch (IllegalArgumentException malformed) {
            return;
        }
        NetAvatarSnapshot cleared = new NetAvatarSnapshot(playerId, "", "", false, false, null, List.of(), Map.of(), Map.of(), "", 0L, List.of(), "", true);
        broadcastAvatarSnapshot(ownerId, cleared, 0L);
    }
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
            m.bones().stream().map(b -> new NetBoneDefinition(b.uuid(), b.name(), b.parentName(), b.parentUuid(), b.parentType(), b.role(), b.tags(), b.physicsPreset(), b.cubeCount(), b.pivotX(), b.pivotY(), b.pivotZ(), b.rotationX(), b.rotationY(), b.rotationZ(), b.visible(), b.childBoneUuids())).toList(),
            m.cubes().stream().map(c -> new NetCubeDefinition(c.name(), c.parentBoneUuid(), c.fromX(), c.fromY(), c.fromZ(), c.toX(), c.toY(), c.toZ(), c.originX(), c.originY(), c.originZ(), c.rotationX(), c.rotationY(), c.rotationZ(), c.inflate(),
                c.faces().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new NetFaceUvDefinition(e.getValue().u1(), e.getValue().v1(), e.getValue().u2(), e.getValue().v2(), e.getValue().rotation(), e.getValue().textureIndex(), e.getValue().enabled()))),
                c.textureIndex(), c.mirror(), c.visible())).toList(),
            m.meshes().stream().map(NetMeshDefinition::from).toList(),
            m.animations().stream().map(a -> new NetAnimationDefinition(a.name(), a.lengthSeconds(), a.looping(), a.animatorCount(),
                a.boneAnimations().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    BbBoneAnimation v = e.getValue();
                    return new NetBoneAnimation(v.boneUuid(), toNetKeys(v.rotation()), toNetKeys(v.position()), toNetKeys(v.scale()), v.rotationGlobal(), v.quaternionInterpolation());
                })), a.affectedBones())).toList()
        )).toList();
    }

    private List<NetKeyframe> toNetKeys(List<BbKeyframe> keys) { return keys.stream().map(k -> new NetKeyframe(k.time(), k.pre(), k.post(), k.easing(), k.bezier())).toList(); }

    public static NetTextureDefinition toNetTexture(BbModelDefinition model, BbTextureDefinition texture) {
        byte[] bytes = readTextureBytes(model, texture);
        String hash = bytes == null ? "" : HexFormat.of().formatHex(sha256(bytes));
        String content = bytes == null ? "" : Base64.getEncoder().encodeToString(bytes);
        return new NetTextureDefinition(texture.id(), texture.name(), texture.relativePath(), texture.width(), texture.height(), hash, content);
    }

    private NetAvatarSnapshot normalizeAvatarSnapshot(ServerPlayer player, NetAvatarSnapshot incoming) {
        if (incoming == null || !incoming.onlineSync() || !isSafeId(incoming.avatarId())) return null;
        if (incoming.parts() != null && incoming.parts().size() > MAX_AVATAR_PARTS) return null;
        if (incoming.parts() != null && incoming.parts().stream().anyMatch(p -> !isSafePart(p))) return null;
        if (!isSafeSyncedVars(incoming.syncedVars()) || !isSafeVanillaVisibility(incoming.vanillaVisibility())) return null;
        if (incoming.currentAnimation() != null && incoming.currentAnimation().length() > 128) return null;
        if (incoming.currentAnimation() != null && !incoming.currentAnimation().isBlank()
            && !AvatarAnimationClock.isSafeAge(incoming.animationStartedAtMillis())) return null;
        if (incoming.animationLayers() != null && (incoming.animationLayers().size() > 64 || incoming.animationLayers().stream().anyMatch(layer -> !isSafeAnimationLayer(layer)))) return null;
        if (!isSafeAnimationParameters(incoming.animationParameters())) return null;
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
                model.primaryTextureRelativePath(), model.textures(), model.bones(), model.cubes(), model.meshes(), model.animations()
            );
        }
        if (model == null) return null;

        long nowMillis = System.currentTimeMillis();
        String currentAnimation = incoming.currentAnimation() == null ? "" : incoming.currentAnimation();
        List<NetAvatarAnimation> animationLayers = incoming.animationLayers() == null ? List.of() : incoming.animationLayers().stream()
            .map(layer -> decodeAnimationTimes(layer, nowMillis))
            .toList();
        return new NetAvatarSnapshot(
            player.getStringUUID(), incoming.avatarId(), modelId, incoming.replaceVanilla(), true, model,
            incoming.parts() == null ? List.of() : List.copyOf(incoming.parts()),
            incoming.vanillaVisibility() == null ? Map.of() : Map.copyOf(incoming.vanillaVisibility()),
            incoming.syncedVars() == null ? Map.of() : Map.copyOf(incoming.syncedVars()),
            currentAnimation, currentAnimation.isBlank() ? 0L : AvatarAnimationClock.decodeAge(nowMillis, incoming.animationStartedAtMillis()),
            animationLayers,
            incoming.animationParameters() == null ? Map.of() : Map.copyOf(incoming.animationParameters()),
            incoming.nameplateText() == null ? "" : incoming.nameplateText(), incoming.nameplateVisible()
        );
    }

    private static NetAvatarSnapshot encodeAnimationTimes(NetAvatarSnapshot snapshot, long nowMillis) {
        String currentAnimation = snapshot.currentAnimation() == null ? "" : snapshot.currentAnimation();
        List<NetAvatarAnimation> layers = snapshot.animationLayers() == null ? List.of() : snapshot.animationLayers().stream()
            .map(layer -> new NetAvatarAnimation(
                layer.name(), AvatarAnimationClock.encodeAge(nowMillis, layer.startedAtMillis()), layer.lengthSeconds(), layer.looping(),
                layer.speed(), layer.weight(), layer.priority(), layer.fadeInTicks(), layer.fadeOutTicks(), layer.mask(), layer.additive(),
                AvatarAnimationClock.encodeOptionalAge(nowMillis, layer.stoppingAtMillis())
            ))
            .toList();
        return new NetAvatarSnapshot(
            snapshot.playerId(), snapshot.avatarId(), snapshot.modelId(), snapshot.replaceVanilla(), snapshot.onlineSync(), snapshot.model(),
            snapshot.parts(), snapshot.vanillaVisibility(), snapshot.syncedVars(), currentAnimation,
            currentAnimation.isBlank() ? 0L : AvatarAnimationClock.encodeAge(nowMillis, snapshot.animationStartedAtMillis()),
            layers, snapshot.animationParameters(), snapshot.nameplateText(), snapshot.nameplateVisible()
        );
    }

    private static NetAvatarAnimation decodeAnimationTimes(NetAvatarAnimation layer, long nowMillis) {
        return new NetAvatarAnimation(
            layer.name(), AvatarAnimationClock.decodeAge(nowMillis, layer.startedAtMillis()), layer.lengthSeconds(), layer.looping(),
            layer.speed(), layer.weight(), layer.priority(), layer.fadeInTicks(), layer.fadeOutTicks(), layer.mask(), layer.additive(),
            AvatarAnimationClock.decodeOptionalAge(nowMillis, layer.stoppingAtMillis())
        );
    }

    private static NetAvatarSnapshot withSyncedVars(NetAvatarSnapshot snapshot, Map<String, Object> values) {
        return new NetAvatarSnapshot(snapshot.playerId(), snapshot.avatarId(), snapshot.modelId(), snapshot.replaceVanilla(), snapshot.onlineSync(), snapshot.model(),
            snapshot.parts(), snapshot.vanillaVisibility(), values, snapshot.currentAnimation(), snapshot.animationStartedAtMillis(), snapshot.animationLayers(),
            snapshot.animationParameters(), snapshot.nameplateText(), snapshot.nameplateVisible());
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

        if (compatibleClients.contains(player.getUUID())) {
            sendProtocolStatus(player, true, "Shyne protocol already ready");
            return;
        }

        pendingHandshakes.remove(player.getUUID());
        compatibleClients.add(player.getUUID());
        avatarSubscriptions.put(player.getUUID(), new AvatarSubscriptions());
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

    private void broadcastAvatarVars(UUID ownerId, String json) {
        if (server == null) return;
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (recipient.getUUID().equals(ownerId)) continue;
            AvatarSubscriptions subscriptions = avatarSubscriptions.computeIfAbsent(recipient.getUUID(), ignored -> new AvatarSubscriptions());
            if (subscriptions.isSubscribed(ownerId) && consumeOutbound(recipient.getUUID(), utf8Length(json), System.nanoTime())) {
                send(recipient, SYNC_AVATAR_VARS, json);
            }
        }
    }

    private void broadcastAvatarSnapshot(UUID ownerId, NetAvatarSnapshot snapshot, long revision) {
        if (server == null || snapshot == null) return;
        NetAvatarSnapshot encoded = encodeAnimationTimes(snapshot, System.currentTimeMillis());
        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerId);
        boolean isDeltaPose = snapshot.model() == null && !snapshot.avatarId().isBlank();
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            boolean ownerAck = recipient.getUUID().equals(ownerId);
            AvatarSubscriptions subscriptions = avatarSubscriptions.computeIfAbsent(recipient.getUUID(), ignored -> new AvatarSubscriptions());
            if (!ownerAck && !subscriptions.isSubscribed(ownerId)) continue;
            if (!ownerAck && isDeltaPose && ownerPlayer != null) {
                if (recipient.level() != ownerPlayer.level()) continue;
                if (recipient.distanceToSqr(ownerPlayer) > MAX_AVATAR_TRACKING_DISTANCE_SQR) continue;
            }
            NetAvatarSnapshot outgoing = ownerAck && encoded.model() != null ? withoutModel(encoded) : encoded;
            sendAvatarSnapshotPacket(recipient, outgoing, revision, false);
        }
    }

    private boolean sendAvatarSnapshotPacket(ServerPlayer recipient, NetAvatarSnapshot snapshot, long revision, boolean requested) {
        List<String> packets = avatarSnapshotPackets(List.of(snapshot), revision);
        if (packets.isEmpty()) return false;
        String json = packets.get(0);
        int bytes = utf8Length(json);
        long nowNanos = System.nanoTime();
        if (requested && !limiter(avatarRequestResponseBytes, recipient.getUUID(), AVATAR_REQUEST_CAPACITY_BYTES,
            AVATAR_REQUEST_REFILL_BYTES_PER_SECOND, nowNanos).tryConsume(bytes, nowNanos)) return false;
        boolean ownerAck = recipient.getStringUUID().equals(snapshot.playerId());
        if (!ownerAck && !consumeOutbound(recipient.getUUID(), bytes, nowNanos)) return false;
        send(recipient, SYNC_AVATAR_SNAPSHOTS, json);
        return true;
    }

    private boolean consumeOutbound(UUID recipientId, int bytes, long nowNanos) {
        return limiter(avatarOutboundBytes, recipientId, AVATAR_OUTBOUND_CAPACITY_BYTES,
            AVATAR_OUTBOUND_REFILL_BYTES_PER_SECOND, nowNanos).tryConsume(bytes, nowNanos);
    }

    static List<String> avatarSnapshotPackets(Collection<NetAvatarSnapshot> snapshots, long revision) {
        if (snapshots == null || snapshots.isEmpty()) return List.of();
        List<String> packets = new ArrayList<>();
        for (NetAvatarSnapshot snapshot : snapshots) {
            if (snapshot == null) continue;
            String json = GSON.toJson(new AvatarSnapshotSyncPayload(List.of(snapshot), revision));
            if (utf8Length(json) <= MAX_AVATAR_JSON_CHARS) packets.add(json);
        }
        return List.copyOf(packets);
    }

    private static NetAvatarSnapshot withoutModel(NetAvatarSnapshot snapshot) {
        return new NetAvatarSnapshot(snapshot.playerId(), snapshot.avatarId(), snapshot.modelId(), snapshot.replaceVanilla(), snapshot.onlineSync(), null,
            snapshot.parts(), snapshot.vanillaVisibility(), snapshot.syncedVars(), snapshot.currentAnimation(), snapshot.animationStartedAtMillis(),
            snapshot.animationLayers(), snapshot.animationParameters(), snapshot.nameplateText(), snapshot.nameplateVisible());
    }

    private static int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static ByteRateLimiter limiter(Map<UUID, ByteRateLimiter> limiters, UUID playerId,
                                           long capacityBytes, long refillBytesPerSecond, long nowNanos) {
        return limiters.computeIfAbsent(playerId, ignored -> new ByteRateLimiter(capacityBytes, refillBytesPerSecond, nowNanos));
    }

    private void forgetClient(UUID playerId) {
        lastAvatarSnapshotAtNanos.remove(playerId);
        lastAvatarFullSnapshotAtNanos.remove(playerId);
        lastAvatarVarAtNanos.remove(playerId);
        lastAvatarAuxSyncAtNanos.remove(playerId);
        avatarIngressBytes.remove(playerId);
        avatarFullUploadBytes.remove(playerId);
        avatarDeltaUploadBytes.remove(playerId);
        avatarRequestBytes.remove(playerId);
        avatarRequestResponseBytes.remove(playerId);
        avatarOutboundBytes.remove(playerId);
        avatarSubscriptions.remove(playerId);
        for (AvatarSubscriptions subscriptions : avatarSubscriptions.values()) subscriptions.forget(playerId);
        compatibleClients.remove(playerId);
        pendingHandshakes.remove(playerId);
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

    public static class ByteRateLimiter extends seashyne.shynecore.network.ByteRateLimiter {
        public ByteRateLimiter(long capacityBytes, long refillBytesPerSecond, long nowNanos) {
            super(capacityBytes, refillBytesPerSecond, nowNanos);
        }
    }

    public static class AvatarSubscriptions extends seashyne.shynecore.network.AvatarSubscriptions {}

    public record ModelSyncPayload(List<NetModelDefinition> models) {}
    public record ActiveSyncPayload(List<NetPlayback> playbacks) {}
    public record AttachmentSyncPayload(List<NetAttachment> attachments) {}
    public record PowerSyncPayload(List<NetPowerState> states) {}
    public record SkillSyncPayload(List<NetSkillDefinition> skills) {}
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

    /** startedAtMillis is an elapsed age on the wire, never an absolute clock. */
    public record NetPlayback(String entityId, String entityName, String modelId, String animationName, long startedAtMillis, double lengthSeconds, boolean looping) {
        public static NetPlayback from(AnimationPlayback playback) {
            return new NetPlayback(playback.entityId().toString(), playback.entityName(), playback.modelId(), playback.animationName(),
                AvatarAnimationClock.encodeAge(System.currentTimeMillis(), playback.startedAtMillis()), playback.lengthSeconds(), playback.looping());
        }
    }
    public record NetAttachment(String entityId, String entityName, String modelId, float offsetX, float offsetY, float offsetZ, float scale, String anchorBone, boolean visible) { public static NetAttachment from(AttachedModelState state) { return new NetAttachment(state.entityId().toString(), state.entityName(), state.modelId(), state.offsetX(), state.offsetY(), state.offsetZ(), state.scale(), state.anchorBone(), state.visible()); } public AttachedModelState toRuntime() { return new AttachedModelState(UUID.fromString(entityId), entityName, modelId, offsetX, offsetY, offsetZ, scale, anchorBone, visible); } }
    public record NetPowerState(String entityId, String comboId, int stage, String branch, long startedAtMillis, long updatedAtMillis, long resetAtMillis, long cooldownEndsAtMillis, double mana, double maxMana, String currentAnimation, boolean locked) { public static NetPowerState from(PowerState state) { return new NetPowerState(state.entityId().toString(), state.comboId(), state.stage(), state.branch(), state.startedAtMillis(), state.updatedAtMillis(), state.resetAtMillis(), state.cooldownEndsAtMillis(), state.mana(), state.maxMana(), state.currentAnimation(), state.locked()); } public PowerState toRuntime() { return new PowerState(UUID.fromString(entityId), comboId, stage, branch, startedAtMillis, updatedAtMillis, resetAtMillis, cooldownEndsAtMillis, mana, maxMana, currentAnimation, locked); } }
    public record NetSkillDefinition(String skillId, String displayName, String castType, String defaultSlot, double manaCost, int cooldownTicks, String modelId, String animation, List<String> tags) { public static NetSkillDefinition from(SkillDefinition def) { return new NetSkillDefinition(def.skillId(), def.displayName(), def.castType().name(), def.defaultSlot().name(), def.manaCost(), def.cooldownTicks(), def.modelId(), def.animation(), def.tags()); } }
    public record NetPlayerProfile(String playerId, String playerName, int level, long experience, int statPoints, int skillPoints, String playerClass, List<String> unlockedSkills, Map<String, String> equippedSkills, Map<String, Integer> attributes, String teamId, long updatedAtMillis) { public static NetPlayerProfile from(PlayerProfile profile) { return new NetPlayerProfile(profile.playerId().toString(), profile.playerName(), profile.level(), profile.experience(), profile.statPoints(), profile.skillPoints(), profile.playerClass(), profile.unlockedSkills(), profile.equippedSkills(), profile.attributes(), profile.teamId(), profile.updatedAtMillis()); } }
    public record NetWeaponDefinition(String weaponId, String displayName, String itemId, String modelId, String classTag, List<String> grantedSkills, Map<String, Double> statModifiers) { public static NetWeaponDefinition from(WeaponDefinition definition) { return new NetWeaponDefinition(definition.weaponId(), definition.displayName(), definition.itemId(), definition.modelId(), definition.classTag(), definition.grantedSkills(), definition.statModifiers()); } }
    public record NetEquipmentLoadout(String entityId, String mainHandWeaponId, String offHandWeaponId, Map<String, String> slots, long updatedAtMillis) { public static NetEquipmentLoadout from(EquipmentLoadout loadout) { return new NetEquipmentLoadout(loadout.entityId().toString(), loadout.mainHandWeaponId(), loadout.offHandWeaponId(), loadout.slots(), loadout.updatedAtMillis()); } }
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
        String vanillaParent, boolean vanillaParentControlled, String vanillaAttachmentMode
    ) {}
    public record NetAvatarAnimation(String name, long startedAtMillis, double lengthSeconds, boolean looping, double speed, double weight, int priority, int fadeInTicks, int fadeOutTicks, List<String> mask, boolean additive, long stoppingAtMillis) {}
    public record NetAvatarSnapshot(String playerId, String avatarId, String modelId, boolean replaceVanilla, boolean onlineSync, NetModelDefinition model, List<NetAvatarPart> parts, Map<String, Boolean> vanillaVisibility, Map<String, Object> syncedVars, String currentAnimation, long animationStartedAtMillis, List<NetAvatarAnimation> animationLayers, Map<String, Double> animationParameters, String nameplateText, boolean nameplateVisible) {
        public NetAvatarSnapshot(String playerId, String avatarId, String modelId, boolean replaceVanilla, boolean onlineSync, NetModelDefinition model, List<NetAvatarPart> parts, Map<String, Boolean> vanillaVisibility, Map<String, Object> syncedVars, String currentAnimation, long animationStartedAtMillis, List<NetAvatarAnimation> animationLayers, String nameplateText, boolean nameplateVisible) {
            this(playerId, avatarId, modelId, replaceVanilla, onlineSync, model, parts, vanillaVisibility, syncedVars, currentAnimation, animationStartedAtMillis, animationLayers, Map.of(), nameplateText, nameplateVisible);
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
    public record NetKeyframe(float time, seashyne.shynecore.model.BbKeyframePoint pre, seashyne.shynecore.model.BbKeyframePoint post, String easing, seashyne.shynecore.model.BbBezierData bezier) {
        public NetKeyframe(float time, float x, float y, float z, String easing) { this(time, seashyne.shynecore.model.BbKeyframePoint.numeric(x, y, z), seashyne.shynecore.model.BbKeyframePoint.numeric(x, y, z), easing, seashyne.shynecore.model.BbBezierData.NONE); }
        public BbKeyframe toRuntime() { return new BbKeyframe(time, pre, post, easing, bezier); }
    }
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
}

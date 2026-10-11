package seashyne.shynecore.network.server;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.animation.AnimationPlayback;
import seashyne.shynecore.animation.AnimationRuntime;
import seashyne.shynecore.attachment.AttachedModelState;
import seashyne.shynecore.attachment.AttachmentRuntime;
import seashyne.shynecore.avatar.AvatarAnimationClock;
import seashyne.shynecore.avatar.AvatarValueCodec;
import seashyne.shynecore.equipment.EquipmentLoadout;
import seashyne.shynecore.equipment.EquipmentRuntime;
import seashyne.shynecore.equipment.WeaponDefinition;
import seashyne.shynecore.item.ShyneItemDefinition;
import seashyne.shynecore.item.ShyneItemRuntime;
import seashyne.shynecore.model.*;
import seashyne.shynecore.network.ShyneNetworkCommon.*;
import seashyne.shynecore.network.ShynePacketSender;
import seashyne.shynecore.power.PowerState;
import seashyne.shynecore.power.PowerStateMachine;
import seashyne.shynecore.profile.PlayerProfile;
import seashyne.shynecore.profile.PlayerProfileRuntime;
import seashyne.shynecore.skill.SkillDefinition;
import seashyne.shynecore.skill.SkillExecutor;
import seashyne.shynecore.skill.SkillIconAsset;
import seashyne.shynecore.skill.SkillRegistry;
import seashyne.shynecore.skill.SkillSlot;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import seashyne.shynecore.network.ShyneNetworkCommon;
import static seashyne.shynecore.network.ShyneNetworkCommon.*;
import static seashyne.shynecore.network.ShyneNetworkValidator.isSafeAnimationLayer;
import static seashyne.shynecore.network.ShyneNetworkValidator.isSafeAnimationParameters;
import static seashyne.shynecore.network.ShyneNetworkValidator.isSafeId;
import static seashyne.shynecore.network.ShyneNetworkValidator.isSafeModel;
import static seashyne.shynecore.network.ShyneNetworkValidator.isSafePart;
import static seashyne.shynecore.network.ShyneNetworkValidator.isSafeSyncedVars;
import static seashyne.shynecore.network.ShyneNetworkValidator.isSafeVanillaVisibility;
import static seashyne.shynecore.network.ShyneNetworkValidator.readTextureBytes;
import static seashyne.shynecore.network.ShyneNetworkValidator.sha256;

/**
 * Core server networking coordinator for Shyne Creator.
 *
 * <p>Handles client handshakes, avatar snapshot synchronization, rate limiting,
 * gameplay input execution, and model/item/skill registry broadcast events.
 * Fully loader-agnostic: packet transmission is delegated via {@link ShynePacketSender}.</p>
 */
public class ShyneServerNetworking implements BbModelRegistry.Listener, AnimationRuntime.Listener, AttachmentRuntime.Listener,
    PowerStateMachine.Listener, SkillRegistry.Listener, PlayerProfileRuntime.Listener, EquipmentRuntime.Listener, ShyneItemRuntime.Listener {

    // =========================================================================
    // Rate Limiting & Bandwidth Protection Constants
    // =========================================================================

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

    private MinecraftServer server;
    private final BbModelRegistry registry;
    private final AnimationRuntime animationRuntime;
    private final AttachmentRuntime attachmentRuntime;
    private final PowerStateMachine powerStateMachine;
    private final SkillExecutor skillExecutor;
    private final SkillRegistry skillRegistry;
    private final PlayerProfileRuntime profileRuntime;
    private final EquipmentRuntime equipmentRuntime;
    private final ShyneItemRuntime itemRuntime;
    private final ShynePacketSender packetSender;

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
    private final Map<UUID, ByteRateLimiter> avatarChannelBytes = new HashMap<>();
    private final Map<UUID, AvatarSubscriptions> avatarSubscriptions = new HashMap<>();
    private final Set<UUID> compatibleClients = new HashSet<>();
    private final Map<UUID, Integer> pendingHandshakes = new HashMap<>();
    private String lastBroadcastPresenceJson;

    public ShyneServerNetworking(
        BbModelRegistry registry, AnimationRuntime animationRuntime, AttachmentRuntime attachmentRuntime,
        PowerStateMachine powerStateMachine, SkillExecutor skillExecutor, SkillRegistry skillRegistry,
        PlayerProfileRuntime profileRuntime, EquipmentRuntime equipmentRuntime, ShyneItemRuntime itemRuntime,
        ShynePacketSender packetSender
    ) {
        this.registry = registry;
        this.animationRuntime = animationRuntime;
        this.attachmentRuntime = attachmentRuntime;
        this.powerStateMachine = powerStateMachine;
        this.skillExecutor = skillExecutor;
        this.skillRegistry = skillRegistry;
        this.profileRuntime = profileRuntime;
        this.equipmentRuntime = equipmentRuntime;
        this.itemRuntime = itemRuntime;
        this.packetSender = packetSender;

        this.registry.addListener(this);
        this.animationRuntime.addListener(this);
        this.attachmentRuntime.addListener(this);
        this.powerStateMachine.addListener(this);
        this.skillRegistry.addListener(this);
        this.profileRuntime.addListener(this);
        this.equipmentRuntime.addListener(this);
        this.itemRuntime.addListener(this);
    }

    // =========================================================================
    // Server Lifecycle & Connection Events
    // =========================================================================

    public void bindServer(MinecraftServer server) {
        this.server = server;
        this.lastBroadcastPresenceJson = null;
        seashyne.shynecore.power.PowerServerConfig.get().load(java.nio.file.Path.of("config"));
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (this.server == null) return;
        profileRuntime.ensurePlayer(player);
        skillExecutor.ensurePlayer(player);
        pendingHandshakes.put(player.getUUID(), server.getTickCount() + HANDSHAKE_TIMEOUT_TICKS);
    }

    public void onPlayerLeave(ServerPlayer player) {
        if (this.server == null) return;
        NetAvatarSnapshot removedAvatar = latestAvatarSnapshots.remove(player.getUUID());
        forgetClient(player.getUUID());
        if (removedAvatar != null) broadcastAvatarClear(player.getStringUUID());
        broadcastPlayerPresence();
    }

    public void onServerTick(MinecraftServer s) {
        if (s != this.server || pendingHandshakes.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> iterator = pendingHandshakes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> pending = iterator.next();
            if (s.getTickCount() < pending.getValue()) continue;
            ServerPlayer player = s.getPlayerList().getPlayer(pending.getKey());
            iterator.remove();
            if (player != null) player.connection.disconnect(Component.literal("Shyne Creator " + ShyneCore.VERSION + " is required to join this server."));
        }
    }

    // =========================================================================
    // Handshake & Protocol Negotiation
    // =========================================================================

    public void completeHandshake(ServerPlayer player, ProtocolHelloPayload hello) {
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

    // =========================================================================
    // Gameplay Input & Ability Handling
    // =========================================================================

    public void handleSkillKey(ServerPlayer player, SkillKeyPayload payload) {
        if (!compatibleClients.contains(player.getUUID())) return;

        if (payload.slot() == DYNAMIC_ACTION_DECK_SLOT) {
            String skillId = payload.skill() == null ? "" : payload.skill().trim();
            SkillDefinition definition = skillRegistry.get(skillId);
            if (definition == null || !profileRuntime.canUseSkill(player, skillId)) return;
            SkillSlot presentationSlot = definition.defaultSlot() == null ? SkillSlot.PRIMARY : definition.defaultSlot();
            skillExecutor.execute(player, skillId, presentationSlot, "dynamic_deck", DYNAMIC_ACTION_DECK_SLOT);
            return;
        }

        SkillSlot mappedSlot = resolveSkillSlot(payload.skill(), payload.slot());
        String equippedSkill = profileRuntime.equippedSkill(player.getUUID(), mappedSlot);
        if (equippedSkill == null || equippedSkill.isBlank()) return;
        skillExecutor.execute(player, equippedSkill, mappedSlot, payload.skill(), payload.slot());
    }

    public void handleEquipSkill(ServerPlayer player, String json) {
        if (!compatibleClients.contains(player.getUUID())) return;
        try {
            EquipRequest request = GSON.fromJson(json, EquipRequest.class);
            if (request != null && request.slot() != null) {
                SkillSlot slot = SkillSlot.fromString(request.slot());
                String skillId = request.skillId();
                if (skillId != null && !skillId.isBlank() && seashyne.shynecore.power.PowerServerConfig.get().isBanned(skillId)) {
                    return;
                }
                profileRuntime.equipSkill(player, slot, skillId);
                try {
                    profileRuntime.save();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Avatar Synchronization & Subscriptions
    // =========================================================================

    public void handleAvatarVarSet(ServerPlayer player, AvatarVarSetPayload payload) {
        if (!compatibleClients.contains(player.getUUID())) return;
        UUID playerId = player.getUUID();
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
        NetAvatarVars vars = new NetAvatarVars(player.getStringUUID(), payload.avatarId(), safeValues);
        broadcastAvatarVars(playerId, GSON.toJson(new AvatarVarSyncPayload(List.of(vars))));
    }

    public void handleAvatarSyncRequest(ServerPlayer player, AvatarSyncRequestPayload payload) {
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

    public void handleAvatarSnapshot(ServerPlayer player, String json) {
        if (!compatibleClients.contains(player.getUUID())) return;
        if (json == null) return;
        UUID playerId = player.getUUID();
        long nowNanos = System.nanoTime();
        int payloadBytes = utf8Length(json);
        if (payloadBytes > MAX_AVATAR_JSON_CHARS || !limiter(avatarIngressBytes, playerId,
            AVATAR_INGRESS_CAPACITY_BYTES, AVATAR_INGRESS_REFILL_BYTES_PER_SECOND, nowNanos).tryConsume(payloadBytes, nowNanos)) return;
        Long previousNanos = lastAvatarSnapshotAtNanos.get(playerId);
        if (previousNanos != null && nowNanos - previousNanos < AVATAR_SNAPSHOT_INTERVAL_NANOS) return;
        lastAvatarSnapshotAtNanos.put(playerId, nowNanos);
        AvatarSnapshotSyncPayload snapshotPayload;
        try {
            snapshotPayload = GSON.fromJson(json, AvatarSnapshotSyncPayload.class);
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
            NetAvatarSnapshot cleared = new NetAvatarSnapshot(player.getStringUUID(), "", "", false, false, null, List.of(), Map.of(), Map.of(), "", 0L, List.of(), "", true);
            broadcastAvatarSnapshot(playerId, cleared, snapshotPayload.revision());
            broadcastPlayerPresence();
            return;
        }
        NetAvatarSnapshot normalized;
        try {
            normalized = normalizeAvatarSnapshot(player, incoming);
        } catch (RuntimeException | StackOverflowError malformed) {
            return;
        }
        if (normalized == null) return;
        latestAvatarSnapshots.put(playerId, normalized);
        NetAvatarSnapshot outgoing = incoming.model() == null
            ? new NetAvatarSnapshot(normalized.playerId(), normalized.avatarId(), normalized.modelId(), normalized.replaceVanilla(), true, null,
                    normalized.parts(), normalized.vanillaVisibility(), normalized.syncedVars(), normalized.currentAnimation(), normalized.animationStartedAtMillis(), normalized.animationLayers(), normalized.animationParameters(), normalized.nameplateText(), normalized.nameplateVisible(), normalized.nameplateBadge(), normalized.nameplateColorArgb(), normalized.nameplateBold(), normalized.nameplateItalic())
            : normalized;
        broadcastAvatarSnapshot(playerId, outgoing, snapshotPayload.revision());
        broadcastPlayerPresence();
    }

    public void handleAvatarChannel(ServerPlayer sender, AvatarChannelPayload payload) {
        if (!compatibleClients.contains(sender.getUUID())) return;
        if (payload == null || payload.channel() == null || payload.payloadJson() == null) return;
        String channel = payload.channel().trim().toLowerCase(Locale.ROOT);
        if (!seashyne.shynecore.avatar.AvatarChannelConstants.isValidChannel(channel)) return;
        int payloadBytes = utf8Length(payload.payloadJson());
        if (payloadBytes > seashyne.shynecore.avatar.AvatarChannelConstants.MAX_PAYLOAD_CHARS) return;
        long nowNanos = System.nanoTime();
        if (!limiter(avatarChannelBytes, sender.getUUID(),
            seashyne.shynecore.avatar.AvatarChannelConstants.MAX_PAYLOAD_CHARS * 4L,
            seashyne.shynecore.avatar.AvatarChannelConstants.MAX_PAYLOAD_CHARS * 2L,
            nowNanos).tryConsume(payloadBytes, nowNanos)) return;

        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (recipient.getUUID().equals(sender.getUUID())) continue;
            if (!compatibleClients.contains(recipient.getUUID())) continue;
            if (recipient.level() != sender.level()) continue;
            if (recipient.distanceToSqr(sender) > MAX_AVATAR_TRACKING_DISTANCE_SQR) continue;
            sendAvatarChannel(recipient, new AvatarChannelPayload(sender.getStringUUID(), channel, payload.payloadJson()));
        }
    }

    public void sendAvatarChannel(ServerPlayer player, AvatarChannelPayload payload) {
        packetSender.send(player, payload);
    }

    // =========================================================================
    // Registry Sync Listeners (Broadcasting Content Updates)
    // =========================================================================

    @Override public void onRegistryChanged(Collection<BbModelDefinition> allModels) { if (server != null) broadcast(SYNC_MODELS, GSON.toJson(new ModelSyncPayload(toNetModels(allModels)))); }
    @Override public void onPlay(AnimationPlayback playback) { if (server != null) broadcast(PLAY_ANIMATION, GSON.toJson(NetPlayback.from(playback))); }
    @Override public void onStop(UUID entityId) { if (server != null) broadcast(STOP_ANIMATION, GSON.toJson(new StopPayload(entityId.toString()))); }
    @Override public void onAttachmentSync(Collection<AttachedModelState> attachments) { if (server != null) broadcast(SYNC_ATTACHMENTS, GSON.toJson(new AttachmentSyncPayload(attachments.stream().map(NetAttachment::from).toList()))); }
    @Override public void onPowerStatesSync(Collection<PowerState> states) { if (server != null) broadcast(SYNC_POWER, GSON.toJson(new PowerSyncPayload(states.stream().map(NetPowerState::from).toList()))); }
    @Override public void onSkillRegistrySync(Collection<SkillDefinition> skills) { if (server != null) broadcast(SYNC_SKILLS, GSON.toJson(new SkillSyncPayload(syncedSkills()))); }
    @Override public void onItemRegistrySync(Collection<ShyneItemDefinition> items) { if (server != null) broadcast(SYNC_ITEMS, GSON.toJson(new ItemSyncPayload(syncedItems()))); }
    @Override public void onProfileSync(Collection<PlayerProfile> profiles) { if (server != null) broadcast(SYNC_PROFILES, GSON.toJson(new ProfileSyncPayload(profiles.stream().map(NetPlayerProfile::from).toList()))); }
    @Override public void onWeaponRegistrySync(Collection<WeaponDefinition> weapons) { if (server != null) broadcast(SYNC_WEAPONS, GSON.toJson(new WeaponSyncPayload(weapons.stream().map(NetWeaponDefinition::from).toList()))); }
    @Override public void onLoadoutSync(Collection<EquipmentLoadout> loadouts) { if (server != null) broadcast(SYNC_LOADOUTS, GSON.toJson(new LoadoutSyncPayload(loadouts.stream().map(NetEquipmentLoadout::from).toList()))); }

    // =========================================================================
    // Targeted Player Synchronization Handlers
    // =========================================================================

    public void sendModelSync(ServerPlayer player) { send(player, SYNC_MODELS, GSON.toJson(new ModelSyncPayload(toNetModels(registry.all())))); }
    public void sendActiveSync(ServerPlayer player) { send(player, SYNC_ACTIVE, GSON.toJson(new ActiveSyncPayload(animationRuntime.allActive().stream().map(NetPlayback::from).toList()))); }
    public void sendAttachmentSync(ServerPlayer player) { send(player, SYNC_ATTACHMENTS, GSON.toJson(new AttachmentSyncPayload(attachmentRuntime.allAttached().stream().map(NetAttachment::from).toList()))); }
    public void sendPowerSync(ServerPlayer player) { send(player, SYNC_POWER, GSON.toJson(new PowerSyncPayload(powerStateMachine.all().stream().map(NetPowerState::from).toList()))); }
    public void sendSkillSync(ServerPlayer player) { send(player, SYNC_SKILLS, GSON.toJson(new SkillSyncPayload(syncedSkills()))); }
    public void sendItemSync(ServerPlayer player) { send(player, SYNC_ITEMS, GSON.toJson(new ItemSyncPayload(syncedItems()))); }
    public void sendProfileSync(ServerPlayer player) { send(player, SYNC_PROFILES, GSON.toJson(new ProfileSyncPayload(profileRuntime.all().stream().map(NetPlayerProfile::from).toList()))); }
    public void sendWeaponSync(ServerPlayer player) { send(player, SYNC_WEAPONS, GSON.toJson(new WeaponSyncPayload(equipmentRuntime.allWeapons().stream().map(NetWeaponDefinition::from).toList()))); }
    public void sendLoadoutSync(ServerPlayer player) { send(player, SYNC_LOADOUTS, GSON.toJson(new LoadoutSyncPayload(equipmentRuntime.allLoadouts().stream().map(NetEquipmentLoadout::from).toList()))); }
    public void sendAvatarVarSync(ServerPlayer player) { send(player, SYNC_AVATAR_VARS, GSON.toJson(new AvatarVarSyncPayload(List.of()))); }
    public void sendPowerConfigSync(ServerPlayer player) { send(player, SYNC_POWER_CONFIG, seashyne.shynecore.power.PowerServerConfig.get().toJson()); }
    public void sendPlayerPresenceSync(ServerPlayer player) { send(player, SYNC_PLAYER_PRESENCE, GSON.toJson(playerPresenceSnapshot())); }

    public void sendAvatarSnapshotSync(ServerPlayer player) {
        long nowMillis = System.currentTimeMillis();
        AvatarSubscriptions subscriptions = avatarSubscriptions.computeIfAbsent(player.getUUID(), ignored -> new AvatarSubscriptions());
        for (Map.Entry<UUID, NetAvatarSnapshot> entry : latestAvatarSnapshots.entrySet()) {
            if (subscriptions.isSubscribed(entry.getKey())) {
                sendAvatarSnapshotPacket(player, encodeAnimationTimes(entry.getValue(), nowMillis), 0L, false);
            }
        }
    }

    private void sendInitialSync(ServerPlayer player) {
        sendModelSync(player);
        sendActiveSync(player);
        sendAttachmentSync(player);
        sendPowerSync(player);
        sendPowerConfigSync(player);
        sendSkillSync(player);
        sendItemSync(player);
        sendProfileSync(player);
        sendWeaponSync(player);
        sendLoadoutSync(player);
        sendAvatarVarSync(player);
        sendAvatarSnapshotSync(player);
    }

    private List<NetSkillDefinition> syncedSkills() {
        List<SkillDefinition> definitions = skillRegistry.all().stream()
            .sorted(Comparator.comparing(SkillDefinition::skillId))
            .toList();
        List<NetSkillDefinition> synced = new ArrayList<>(definitions.size());
        int remainingIconBytes = SkillIconAsset.MAX_SYNC_BYTES;
        for (SkillDefinition definition : definitions) {
            SkillIconAsset icon = skillRegistry.iconAsset(definition.skillId());
            if (icon != null && icon.byteSize() <= remainingIconBytes) {
                remainingIconBytes -= icon.byteSize();
            } else {
                icon = null;
            }
            synced.add(NetSkillDefinition.from(definition, icon));
        }
        return List.copyOf(synced);
    }

    private List<NetItemDefinition> syncedItems() {
        List<ShyneItemDefinition> definitions = itemRuntime.all().stream()
            .sorted(Comparator.comparing(ShyneItemDefinition::itemId))
            .toList();
        List<NetItemDefinition> synced = new ArrayList<>(definitions.size());
        int remainingIconBytes = SkillIconAsset.MAX_SYNC_BYTES;
        for (ShyneItemDefinition definition : definitions) {
            SkillIconAsset icon = itemRuntime.iconAsset(definition.itemId());
            if (icon != null && icon.byteSize() <= remainingIconBytes) {
                remainingIconBytes -= icon.byteSize();
            } else {
                icon = null;
            }
            synced.add(NetItemDefinition.from(definition, icon));
        }
        return List.copyOf(synced);
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

    private List<NetKeyframe> toNetKeys(List<BbKeyframe> keys) {
        return keys.stream().map(k -> new NetKeyframe(k.time(), k.pre(), k.post(), k.easing(), k.bezier())).toList();
    }

    public static NetTextureDefinition toNetTexture(BbModelDefinition model, BbTextureDefinition texture) {
        return ShyneNetworkCommon.toNetTexture(model, texture);
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
        if (incoming.nameplateBadge() != null && incoming.nameplateBadge().length() > 24) return null;

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
            incoming.nameplateText() == null ? "" : incoming.nameplateText(), incoming.nameplateVisible(), incoming.nameplateBadge(), incoming.nameplateColorArgb(), incoming.nameplateBold(), incoming.nameplateItalic()
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
            layers, snapshot.animationParameters(), snapshot.nameplateText(), snapshot.nameplateVisible(), snapshot.nameplateBadge(), snapshot.nameplateColorArgb(), snapshot.nameplateBold(), snapshot.nameplateItalic()
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
            snapshot.animationParameters(), snapshot.nameplateText(), snapshot.nameplateVisible(), snapshot.nameplateBadge(), snapshot.nameplateColorArgb(), snapshot.nameplateBold(), snapshot.nameplateItalic());
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

    public static List<String> avatarSnapshotPackets(Collection<NetAvatarSnapshot> snapshots, long revision) {
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
            snapshot.animationLayers(), snapshot.animationParameters(), snapshot.nameplateText(), snapshot.nameplateVisible(), snapshot.nameplateBadge(), snapshot.nameplateColorArgb(), snapshot.nameplateBold(), snapshot.nameplateItalic());
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
        avatarChannelBytes.remove(playerId);
        avatarSubscriptions.remove(playerId);
        for (AvatarSubscriptions subscriptions : avatarSubscriptions.values()) subscriptions.forget(playerId);
        compatibleClients.remove(playerId);
        pendingHandshakes.remove(playerId);
    }

    private void broadcast(Identifier channel, String json) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player, channel, json);
    }

    private void send(ServerPlayer player, Identifier channel, String json) {
        CustomPacketPayload.Type<JsonPayload> payloadId = JSON_PAYLOAD_IDS.get(channel);
        if (payloadId == null) throw new IllegalArgumentException("Unregistered Shyne payload channel: " + channel);
        if (!channel.equals(PROTOCOL_STATUS) && !compatibleClients.contains(player.getUUID())) return;
        packetSender.send(player, new JsonPayload(payloadId, json));
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
}

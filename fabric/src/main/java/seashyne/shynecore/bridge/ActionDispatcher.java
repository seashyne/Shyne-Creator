package seashyne.shynecore.bridge;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.animation.AnimationRuntime;
import seashyne.shynecore.attachment.AttachmentRuntime;
import seashyne.shynecore.model.BbModelRegistry;
import seashyne.shynecore.power.PowerStateMachine;
import seashyne.shynecore.profile.PlayerProfileRuntime;
import seashyne.shynecore.skill.SkillRegistry;
import seashyne.shynecore.skill.SkillExecutor;
import seashyne.shynecore.skill.SkillSlot;
import seashyne.shynecore.equipment.EquipmentRuntime;
import seashyne.shynecore.loader.ShyneModLoader;
import seashyne.shynecore.item.ShyneItemRuntime;
import seashyne.shynecore.projectile.ProjectileRuntime;
import seashyne.shynecore.projectile.SpellProjectileState;
import seashyne.shynecore.summon.SummonRuntime;
import seashyne.shynecore.summon.SummonedEntityState;
import seashyne.shynecore.targeting.TargetingRuntime;
import seashyne.shynecore.team.TeamRuntime;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ActionDispatcher {
    private final ConcurrentLinkedQueue<JsonObject> actionQueue = new ConcurrentLinkedQueue<>();
    private final List<ScheduledHook> scheduled = Collections.synchronizedList(new ArrayList<>());
    private final BbModelRegistry bbModelRegistry;
    private final AnimationRuntime animationRuntime;
    private final AttachmentRuntime attachmentRuntime;
    private final SummonRuntime summonRuntime;
    private final TargetingRuntime targetingRuntime;
    private final ProjectileRuntime projectileRuntime;
    private final PowerStateMachine powerStateMachine;
    private final TeamRuntime teamRuntime;
    private final SkillRegistry skillRegistry;
    private final SkillExecutor skillExecutor;
    private final PlayerProfileRuntime profileRuntime;
    private final EquipmentRuntime equipmentRuntime;
    private final ShyneItemRuntime itemRuntime;
    private final ShyneModLoader modLoader;

    public ActionDispatcher(ActionBus actionBus, BbModelRegistry bbModelRegistry, AnimationRuntime animationRuntime,
                            AttachmentRuntime attachmentRuntime, SummonRuntime summonRuntime, TargetingRuntime targetingRuntime,
                            ProjectileRuntime projectileRuntime, PowerStateMachine powerStateMachine, TeamRuntime teamRuntime,
                            SkillRegistry skillRegistry, SkillExecutor skillExecutor, PlayerProfileRuntime profileRuntime,
                            EquipmentRuntime equipmentRuntime, ShyneItemRuntime itemRuntime, ShyneModLoader modLoader) {
        this.bbModelRegistry = bbModelRegistry;
        this.animationRuntime = animationRuntime;
        this.attachmentRuntime = attachmentRuntime;
        this.summonRuntime = summonRuntime;
        this.targetingRuntime = targetingRuntime;
        this.projectileRuntime = projectileRuntime;
        this.powerStateMachine = powerStateMachine;
        this.teamRuntime = teamRuntime;
        this.skillRegistry = skillRegistry;
        this.skillExecutor = skillExecutor;
        this.profileRuntime = profileRuntime;
        this.equipmentRuntime = equipmentRuntime;
        this.itemRuntime = itemRuntime;
        this.modLoader = modLoader;
        actionBus.addHandler(this::enqueue);
    }

    public void bindServer(MinecraftServer server) {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void enqueue(JsonObject msg) {
        actionQueue.add(msg);
    }

    private void tick(MinecraftServer server) {
        JsonObject msg;
        while ((msg = actionQueue.poll()) != null) {
            try {
                executeMessage(msg, server);
            } catch (Exception e) {
                ShyneCore.LOGGER.error("[Dispatch] {}", e.getMessage(), e);
            }
        }
        animationRuntime.tick();
        powerStateMachine.tick();
        summonRuntime.tick(server.getAllLevels());
        targetingRuntime.tick(server.getAllLevels());
        projectileRuntime.tick(server.getAllLevels());
        pruneMissingEntities(server);
        List<ScheduledHook> fired = new ArrayList<>();
        synchronized (scheduled) {
            for (ScheduledHook sh : scheduled) {
                sh.ticksLeft--;
                if (sh.ticksLeft <= 0) fired.add(sh);
            }
            scheduled.removeAll(fired);
        }
        for (ScheduledHook sh : fired) {
            modLoader.fireHookIn(sh.modId, sh.hook, Map.of());
        }
    }

    private void pruneMissingEntities(MinecraftServer server) {
        Set<UUID> live = new HashSet<>();
        for (ServerLevel world : server.getAllLevels()) {
            for (Entity entity : world.getAllEntities()) {
                live.add(entity.getUUID());
            }
        }
        animationRuntime.pruneMissing(live);
        attachmentRuntime.pruneMissing(live);
        projectileRuntime.pruneMissing(live);
        targetingRuntime.pruneMissing(live);
        powerStateMachine.pruneMissing(live);
        teamRuntime.pruneMissing(live);
        for (SummonedEntityState state : new ArrayList<>(summonRuntime.all())) {
            if (!live.contains(state.entityId())) summonRuntime.remove(state.entityId());
        }
    }

    private void executeMessage(JsonObject msg, MinecraftServer server) {
        String type = str(msg, BridgeProtocol.F_TYPE, "");
        if (BridgeProtocol.RES_SCHEDULE.equals(type)) {
            scheduled.add(new ScheduledHook(str(msg, BridgeProtocol.F_MOD_ID, ""), str(msg, BridgeProtocol.F_HOOK, "on_tick"), msg.has(BridgeProtocol.F_DELAY) ? msg.get(BridgeProtocol.F_DELAY).getAsInt() : 20));
            return;
        }
        if (!BridgeProtocol.RES_ACTION.equals(type)) return;

        String action = str(msg, BridgeProtocol.F_ACTION, "");
        JsonObject args = msg.has(BridgeProtocol.F_ARGS) ? msg.getAsJsonObject(BridgeProtocol.F_ARGS) : new JsonObject();
        switch (action) {
            case BridgeProtocol.ACT_SEND_MESSAGE -> actSendMessage(args, server);
            case BridgeProtocol.ACT_RUN_COMMAND -> actRunCommand(args, server);
            case BridgeProtocol.ACT_GIVE_ITEM -> actGiveItem(args, server);
            case BridgeProtocol.ACT_GIVE_SHYNE_ITEM -> actGiveShyneItem(args, server);
            case BridgeProtocol.ACT_SET_BLOCK -> actSetBlock(args, server);
            case BridgeProtocol.ACT_PLAY_SOUND -> actPlaySound(args, server);
            case BridgeProtocol.ACT_LOG -> actLog(args, msg);
            case BridgeProtocol.ACT_LOAD_BBMODEL -> actLoadBbModel(args, msg);
            case BridgeProtocol.ACT_ATTACH_MODEL -> actAttachModel(args, server);
            case BridgeProtocol.ACT_DETACH_MODEL -> actDetachModel(args, server);
            case BridgeProtocol.ACT_PLAY_ANIMATION -> actPlayAnimation(resolveEntity(server, str(args, "player", "")), args);
            case BridgeProtocol.ACT_PLAY_ANIMATION_ENTITY -> actPlayAnimation(resolveEntity(server, str(args, "entity", "")), args);
            case BridgeProtocol.ACT_STOP_ANIMATION -> actStopAnimation(resolveEntity(server, str(args, "player", "")));
            case BridgeProtocol.ACT_STOP_ANIMATION_ENTITY -> actStopAnimation(resolveEntity(server, str(args, "entity", "")));
            case BridgeProtocol.ACT_SUMMON_ENTITY -> actSummonEntity(args, server);
            case BridgeProtocol.ACT_DESPAWN_ENTITY -> actDespawnEntity(args, server);
            case BridgeProtocol.ACT_DESPAWN_OWNER_SUMMONS -> actDespawnOwnerSummons(args, server);
            case BridgeProtocol.ACT_LAUNCH_PROJECTILE -> actLaunchProjectile(args, server);
            case BridgeProtocol.ACT_ADVANCE_COMBO -> actAdvanceCombo(args, server);
            case BridgeProtocol.ACT_RESET_COMBO -> actResetCombo(args, server);
            case BridgeProtocol.ACT_SET_TEAM -> actSetTeam(args, server);
            case BridgeProtocol.ACT_CONFIGURE_TEAM -> actConfigureTeam(args);
            case BridgeProtocol.ACT_SET_MANA -> actSetMana(args, server);
            case BridgeProtocol.ACT_GRANT_XP -> actGrantXp(args, server);
            case BridgeProtocol.ACT_UNLOCK_SKILL -> actUnlockSkill(args, server);
            case BridgeProtocol.ACT_EQUIP_SKILL -> actEquipSkill(args, server);
            case BridgeProtocol.ACT_EQUIP_WEAPON -> actEquipWeapon(args, server);
            case BridgeProtocol.ACT_CAST_SKILL -> actCastSkill(args, server);
            default -> ShyneCore.LOGGER.warn("[Dispatch] Unknown action: '{}'", action);
        }
    }

    private void actSendMessage(JsonObject args, MinecraftServer s) {
        String text = str(args, "text", "");
        String player = str(args, "player", "");
        Component c = Component.literal(text);
        if (player.isEmpty()) s.getPlayerList().broadcastSystemMessage(c, false);
        else {
            ServerPlayer p = s.getPlayerList().getPlayer(player);
            if (p != null) p.sendSystemMessage(c);
        }
    }

    private void actRunCommand(JsonObject args, MinecraftServer s) {
        String cmd = str(args, "command", "");
        if (!cmd.isEmpty()) s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), cmd);
    }

    private void actGiveItem(JsonObject args, MinecraftServer s) {
        String player = str(args, "player", "");
        String item = str(args, "item", "stone");
        int count = args.has("count") ? args.get("count").getAsInt() : 1;
        if (!player.isEmpty()) s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), String.format("give %s %s %d", player, item, count));
    }

    private void actGiveShyneItem(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        if (entity instanceof ServerPlayer player) {
            itemRuntime.give(player, str(args, "item", str(args, "item_id", "")),
                Math.max(1, args.has("count") ? args.get("count").getAsInt() : 1));
        }
    }

    private void actSetBlock(JsonObject args, MinecraftServer s) {
        int x = args.has("x") ? args.get("x").getAsInt() : 0;
        int y = args.has("y") ? args.get("y").getAsInt() : 64;
        int z = args.has("z") ? args.get("z").getAsInt() : 0;
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), String.format("setblock %d %d %d %s", x, y, z, str(args, "block", "stone")));
    }

    private void actPlaySound(JsonObject args, MinecraftServer s) {
        String player = str(args, "player", "");
        String sound = str(args, "sound", "minecraft:entity.player.levelup");
        String source = str(args, "source", "master");
        if (!player.isEmpty()) s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), String.format("playsound %s %s %s", sound, source, player));
    }

    private void actLog(JsonObject args, JsonObject msg) {
        String level = str(args, "level", "info");
        String text = str(args, "text", "");
        String line = "[Lua:" + str(msg, BridgeProtocol.F_MOD_ID, "?") + "] " + text;
        switch (level) {
            case "warn" -> ShyneCore.LOGGER.warn(line);
            case "error" -> ShyneCore.LOGGER.error(line);
            default -> ShyneCore.LOGGER.info(line);
        }
    }

    private void actLoadBbModel(JsonObject args, JsonObject msg) {
        String modId = str(msg, BridgeProtocol.F_MOD_ID, "scripted");
        String pathStr = str(args, "path", "");
        if (pathStr.isBlank()) return;
        bbModelRegistry.register(Path.of(pathStr), modId).ifPresent(model -> ShyneCore.LOGGER.info("[Dispatch] Registered bbmodel {} from {}", model.modelId(), pathStr));
    }

    private void actAttachModel(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        String modelId = str(args, "model", str(args, "model_id", ""));
        if (entity == null || modelId.isBlank()) return;
        attachmentRuntime.attach(entity, modelId, f(args, "offset_x", 0f), f(args, "offset_y", 0f), f(args, "offset_z", 0f), f(args, "scale", 1f), str(args, "anchor_bone", ""));
    }

    private void actDetachModel(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        if (entity != null) attachmentRuntime.detach(entity);
    }

    private void actPlayAnimation(Entity entity, JsonObject args) {
        if (entity == null) return;
        String modelId = str(args, "model", str(args, "model_id", ""));
        String animation = str(args, "animation", "");
        if (!modelId.isBlank() && !animation.isBlank()) animationRuntime.play(entity, modelId, animation);
    }

    private void actStopAnimation(Entity entity) {
        if (entity != null) animationRuntime.stop(entity);
    }

    private void actSummonEntity(JsonObject args, MinecraftServer server) {
        Entity owner = resolveEntity(server, str(args, "owner", str(args, "player", str(args, "entity", ""))));
        if (owner == null) return;
        if (!(owner.level() instanceof ServerLevel world)) return;
        Identifier typeId = Identifier.tryParse(str(args, "entity_type", "minecraft:armor_stand"));
        if (typeId == null) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId).map(reference -> reference.value()).orElse(null);
        if (type == null) return;
        Entity entity = type.create(world, EntitySpawnReason.COMMAND);
        if (entity == null) return;
        double x = owner.getX() + f(args, "offset_x", 0f);
        double y = owner.getY() + f(args, "offset_y", owner.getBbHeight() * 0.6f);
        double z = owner.getZ() + f(args, "offset_z", 0f);
        entity.snapTo(x, y, z, owner.getYRot(), owner.getXRot());
        world.addFreshEntity(entity);

        String modelId = str(args, "model", str(args, "model_id", ""));
        float scale = f(args, "scale", 1.0f);
        if (!modelId.isBlank()) attachmentRuntime.attach(entity, modelId, 0f, 0f, 0f, scale, str(args, "anchor_bone", ""));
        String idleAnimation = str(args, "animation", str(args, "idle_animation", ""));
        if (!idleAnimation.isBlank() && !modelId.isBlank()) animationRuntime.play(entity, modelId, idleAnimation);
        int durationTicks = args.has("duration_ticks") ? args.get("duration_ticks").getAsInt() : 100;
        long now = System.currentTimeMillis();
        summonRuntime.track(new SummonedEntityState(
            entity.getUUID(), owner.getUUID(), owner.getName().getString(), typeId.toString(), str(args, "tag", "summon"), modelId,
            now, durationTicks <= 0 ? 0 : now + durationTicks * 50L,
            !args.has("discard_on_expire") || args.get("discard_on_expire").getAsBoolean(),
            !args.has("detach_model_on_expire") || args.get("detach_model_on_expire").getAsBoolean(),
            idleAnimation, f(args, "offset_x", 0f), f(args, "offset_y", 0f), f(args, "offset_z", 0f), scale,
            str(args, "target_mode", "nearest"), d(args, "target_range", 16.0), d(args, "hitbox_radius", 1.25), d(args, "orbit_radius", 1.75), d(args, "orbit_speed", 0.18)
        ));
    }

    private void actDespawnEntity(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", ""));
        if (entity == null) return;
        animationRuntime.stop(entity);
        attachmentRuntime.detach(entity);
        summonRuntime.remove(entity.getUUID());
        projectileRuntime.remove(entity.getUUID());
        powerStateMachine.reset(entity.getUUID());
        entity.discard();
    }

    private void actDespawnOwnerSummons(JsonObject args, MinecraftServer server) {
        Entity owner = resolveEntity(server, str(args, "owner", str(args, "player", str(args, "entity", ""))));
        if (owner == null) return;
        if (owner.level() instanceof ServerLevel world) summonRuntime.despawnByOwner(owner, world);
    }

    private void actLaunchProjectile(JsonObject args, MinecraftServer server) {
        Entity owner = resolveEntity(server, str(args, "owner", str(args, "player", str(args, "entity", ""))));
        if (owner == null) return;
        if (!(owner.level() instanceof ServerLevel world)) return;
        Identifier typeId = Identifier.tryParse(str(args, "entity_type", "minecraft:armor_stand"));
        if (typeId == null) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId).map(reference -> reference.value()).orElse(null);
        if (type == null) return;
        Entity entity = type.create(world, EntitySpawnReason.COMMAND);
        if (entity == null) return;
        double speed = d(args, "speed", 1.0);
        entity.snapTo(owner.getX(), owner.getEyeY() - 0.2, owner.getZ(), owner.getYRot(), owner.getXRot());
        entity.setDeltaMovement(owner.getLookAngle().scale(speed));
        world.addFreshEntity(entity);

        String modelId = str(args, "model", str(args, "model_id", ""));
        if (!modelId.isBlank()) attachmentRuntime.attach(entity, modelId, 0f, 0f, 0f, f(args, "scale", 1f), "");
        String flyAnimation = str(args, "animation", str(args, "fly_animation", ""));
        if (!flyAnimation.isBlank() && !modelId.isBlank()) animationRuntime.play(entity, modelId, flyAnimation);
        long now = System.currentTimeMillis();
        int durationTicks = args.has("duration_ticks") ? args.get("duration_ticks").getAsInt() : 40;
        projectileRuntime.track(new SpellProjectileState(
            entity.getUUID(), owner.getUUID(), str(args, "tag", "projectile"), modelId, flyAnimation, str(args, "impact_animation", ""), speed,
            d(args, "hitbox_radius", 1.0), d(args, "damage", 4.0), now, durationTicks <= 0 ? 0 : now + durationTicks * 50L,
            !args.has("despawn_on_impact") || args.get("despawn_on_impact").getAsBoolean(),
            args.has("homing") && args.get("homing").getAsBoolean(),
            d(args, "homing_strength", 0.25),
            args.has("pierce_count") ? args.get("pierce_count").getAsInt() : 0,
            d(args, "explode_radius", 0.0),
            new HashSet<>()
        ));
    }

    private void actAdvanceCombo(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        if (entity == null) return;
        powerStateMachine.advance(entity, str(args, "combo", "default"), str(args, "branch", "light"),
            args.has("max_stage") ? args.get("max_stage").getAsInt() : 3,
            args.has("reset_ticks") ? args.get("reset_ticks").getAsInt() : 30,
            str(args, "model", str(args, "model_id", "")), str(args, "animation_prefix", "combo_"));
    }

    private void actSetTeam(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        if (entity != null) teamRuntime.setTeam(entity, str(args, "team", ""));
    }

    private void actConfigureTeam(JsonObject args) {
        teamRuntime.configureTeam(str(args, "team", "default"),
            args.has("friendly_fire") && args.get("friendly_fire").getAsBoolean(),
            !args.has("target_mobs") || args.get("target_mobs").getAsBoolean(),
            !args.has("target_players") || args.get("target_players").getAsBoolean());
    }

    private void actSetMana(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        if (entity != null) powerStateMachine.setMana(entity.getUUID(), d(args, "mana", 100.0), d(args, "max_mana", 100.0));
    }

    private void actResetCombo(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        if (entity != null) powerStateMachine.reset(entity.getUUID());
    }

    private void actGrantXp(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        if (entity instanceof ServerPlayer player) profileRuntime.addExperience(player, (long) d(args, "amount", 0));
    }

    private void actUnlockSkill(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        if (entity instanceof ServerPlayer player) profileRuntime.unlockSkill(player, str(args, "skill", str(args, "skill_id", "")));
    }

    private void actEquipSkill(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        if (entity instanceof ServerPlayer player) profileRuntime.equipSkill(player, SkillSlot.fromString(str(args, "slot", "primary")), str(args, "skill", str(args, "skill_id", "")));
    }

    private void actEquipWeapon(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        if (entity instanceof ServerPlayer player) equipmentRuntime.equipWeapon(player, str(args, "slot", "mainhand"), str(args, "weapon", str(args, "weapon_id", "")));
    }

    private void actCastSkill(JsonObject args, MinecraftServer server) {
        Entity entity = resolveEntity(server, str(args, "entity", str(args, "player", "")));
        if (!(entity instanceof ServerPlayer player)) return;
        String skillId = str(args, "skill", str(args, "skill_id", ""));
        var definition = skillRegistry.get(skillId);
        if (definition == null) return;
        skillExecutor.execute(player, skillId, definition.defaultSlot(), "script", 0);
    }

    private static String str(JsonObject obj, String key, String def) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    private static float f(JsonObject obj, String key, float def) {
        try { return obj != null && obj.has(key) ? obj.get(key).getAsFloat() : def; } catch (Exception ignored) { return def; }
    }

    private static double d(JsonObject obj, String key, double def) {
        try { return obj != null && obj.has(key) ? obj.get(key).getAsDouble() : def; } catch (Exception ignored) { return def; }
    }

    private Entity resolveEntity(MinecraftServer server, String token) {
        if (token == null || token.isBlank()) return null;
        ServerPlayer player = server.getPlayerList().getPlayer(token);
        if (player != null) return player;
        try {
            UUID uuid = UUID.fromString(token);
            for (ServerLevel world : server.getAllLevels()) {
                Entity entity = world.getEntityInAnyDimension(uuid);
                if (entity != null) return entity;
            }
        } catch (IllegalArgumentException ignored) {}
        for (ServerLevel world : server.getAllLevels()) {
            for (Entity entity : world.getAllEntities()) {
                if (entity.getName().getString().equalsIgnoreCase(token)) return entity;
            }
        }
        return null;
    }

    private static class ScheduledHook {
        final String modId;
        final String hook;
        int ticksLeft;

        ScheduledHook(String modId, String hook, int ticksLeft) {
            this.modId = modId;
            this.hook = hook;
            this.ticksLeft = ticksLeft;
        }
    }
}

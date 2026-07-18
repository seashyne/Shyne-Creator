package seashyne.shynecore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import seashyne.shynecore.animation.AnimationRuntime;
import seashyne.shynecore.attachment.AttachmentRuntime;
import seashyne.shynecore.diagnostics.ContentDiagnostics;
import seashyne.shynecore.diagnostics.ContentIssue;
import seashyne.shynecore.equipment.EquipmentLoadout;
import seashyne.shynecore.equipment.EquipmentRuntime;
import seashyne.shynecore.equipment.WeaponDefinition;
import seashyne.shynecore.loader.ShyneModLoader;
import seashyne.shynecore.item.ShyneItemRuntime;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbModelRegistry;
import seashyne.shynecore.power.PowerState;
import seashyne.shynecore.power.PowerStateMachine;
import seashyne.shynecore.profile.PlayerProfile;
import seashyne.shynecore.profile.PlayerProfileRuntime;
import seashyne.shynecore.projectile.ProjectileRuntime;
import seashyne.shynecore.projectile.SpellProjectileState;
import seashyne.shynecore.skill.SkillDefinition;
import seashyne.shynecore.skill.SkillExecutor;
import seashyne.shynecore.skill.SkillRegistry;
import seashyne.shynecore.skill.SkillSlot;
import seashyne.shynecore.summon.SummonRuntime;
import seashyne.shynecore.team.TeamRuntime;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ShyneCommand {
    private static final float MIN_PLAYER_SCALE = 0.25f;
    private static final float MAX_PLAYER_SCALE = 4.0f;

    private ShyneCommand() {}

    public static void register(
        ShyneModLoader modLoader,
        BbModelRegistry bbModelRegistry,
        AnimationRuntime animationRuntime,
        AttachmentRuntime attachmentRuntime,
        SummonRuntime summonRuntime,
        ProjectileRuntime projectileRuntime,
        PowerStateMachine powerStateMachine,
        TeamRuntime teamRuntime,
        SkillRegistry skillRegistry,
        SkillExecutor skillExecutor,
        PlayerProfileRuntime profileRuntime,
        EquipmentRuntime equipmentRuntime,
        ShyneItemRuntime itemRuntime,
        ContentDiagnostics diagnostics
    ) {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> {
            var dispatcher = event.getDispatcher();
            dispatcher.register(literal("shyne")
            .executes(ctx -> showHelp(ctx.getSource()))
            .then(literal("status").requires(ShyneCommand::isAdmin).executes(ctx -> {
                feedback(ctx.getSource(), "mods=" + modLoader.getLoadedCount()
                    + " models=" + bbModelRegistry.all().size()
                    + " skills=" + skillRegistry.all().size()
                    + " weapons=" + equipmentRuntime.allWeapons().size()
                    + " items=" + itemRuntime.all().size()
                    + " summons=" + summonRuntime.all().size()
                    + " projectiles=" + projectileRuntime.all().size()
                    + " powers=" + powerStateMachine.all().size()
                    + " runtime=lua"
                    + " " + diagnostics.summary());
                return Command.SINGLE_SUCCESS;
            }))
            .then(literal("mods").requires(ShyneCommand::isAdmin).executes(ctx -> listMods(ctx.getSource(), modLoader)))
            .then(literal("models").requires(ShyneCommand::isAdmin).executes(ctx -> listModels(ctx.getSource(), bbModelRegistry)))
            .then(literal("skills").requires(ShyneCommand::isAdmin).executes(ctx -> listSkills(ctx.getSource(), skillRegistry)))
            .then(literal("weapons").requires(ShyneCommand::isAdmin).executes(ctx -> listWeapons(ctx.getSource(), equipmentRuntime)))
            .then(literal("items").requires(ShyneCommand::isAdmin).executes(ctx -> listItems(ctx.getSource(), itemRuntime)))
            .then(literal("summons").requires(ShyneCommand::isAdmin).executes(ctx -> listSummons(ctx.getSource(), summonRuntime)))
            .then(literal("projectiles").requires(ShyneCommand::isAdmin).executes(ctx -> listProjectiles(ctx.getSource(), projectileRuntime)))
            .then(literal("profiles").requires(ShyneCommand::isAdmin).executes(ctx -> listProfiles(ctx.getSource(), profileRuntime)))
            .then(literal("loadouts").requires(ShyneCommand::isAdmin).executes(ctx -> listLoadouts(ctx.getSource(), equipmentRuntime)))
            .then(literal("profile").requires(ShyneCommand::isAdmin).then(argument("player", StringArgumentType.word()).executes(ctx -> showProfile(ctx.getSource(), profileRuntime, equipmentRuntime, StringArgumentType.getString(ctx, "player")))))
            .then(literal("errors").requires(ShyneCommand::isAdmin).executes(ctx -> listDiagnostics(ctx.getSource(), diagnostics, 8))
                .then(argument("limit", IntegerArgumentType.integer(1, 50)).executes(ctx -> listDiagnostics(ctx.getSource(), diagnostics, IntegerArgumentType.getInteger(ctx, "limit")))))
            .then(literal("report").requires(ShyneCommand::isAdmin).executes(ctx -> {
                feedback(ctx.getSource(), "summary=" + diagnostics.summary());
                feedback(ctx.getSource(), "json=" + diagnostics.jsonReportPath());
                feedback(ctx.getSource(), "text=" + diagnostics.textReportPath());
                return Command.SINGLE_SUCCESS;
            }))
            .then(literal("reload").requires(ShyneCommand::isAdmin).executes(ctx -> reloadContent(ctx.getSource(), modLoader, skillRegistry, equipmentRuntime, itemRuntime, profileRuntime, diagnostics)))
            .then(literal("reloand").requires(ShyneCommand::isAdmin).executes(ctx -> reloadContent(ctx.getSource(), modLoader, skillRegistry, equipmentRuntime, itemRuntime, profileRuntime, diagnostics)))
            .then(literal("setteam").requires(ShyneCommand::isAdmin).then(argument("entity", StringArgumentType.word()).then(argument("team", StringArgumentType.word()).executes(ctx -> {
                Entity entity = resolveEntity(ctx.getSource(), StringArgumentType.getString(ctx, "entity"));
                if (entity == null) return fail(ctx.getSource(), "Entity not found.");
                String team = StringArgumentType.getString(ctx, "team");
                teamRuntime.setTeam(entity, team);
                feedback(ctx.getSource(), "Set team of " + entity.getName().getString() + " to " + team);
                return Command.SINGLE_SUCCESS;
            }))))
            .then(literal("setmana").requires(ShyneCommand::isAdmin).then(argument("entity", StringArgumentType.word()).then(argument("mana", DoubleArgumentType.doubleArg(0.0)).executes(ctx -> {
                Entity entity = resolveEntity(ctx.getSource(), StringArgumentType.getString(ctx, "entity"));
                if (entity == null) return fail(ctx.getSource(), "Entity not found.");
                double mana = DoubleArgumentType.getDouble(ctx, "mana");
                powerStateMachine.setMana(entity.getUUID(), mana, 100.0);
                feedback(ctx.getSource(), "Mana of " + entity.getName().getString() + " set to " + (int) mana);
                return Command.SINGLE_SUCCESS;
            }))))
            .then(literal("grantxp").requires(ShyneCommand::isAdmin).then(argument("player", StringArgumentType.word()).then(argument("amount", IntegerArgumentType.integer(0)).executes(ctx -> {
                ServerPlayer player = resolvePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                if (player == null) return fail(ctx.getSource(), "Player not found.");
                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                profileRuntime.addExperience(player, amount);
                feedback(ctx.getSource(), "Granted " + amount + " xp to " + player.getName().getString());
                return Command.SINGLE_SUCCESS;
            }))))
            .then(literal("unlockskill").requires(ShyneCommand::isAdmin).then(argument("player", StringArgumentType.word()).then(argument("skill_id", StringArgumentType.word()).executes(ctx -> {
                ServerPlayer player = resolvePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                if (player == null) return fail(ctx.getSource(), "Player not found.");
                String skillId = StringArgumentType.getString(ctx, "skill_id");
                return profileRuntime.unlockSkill(player, skillId)
                    ? success(ctx.getSource(), "Unlocked " + skillId + " for " + player.getName().getString())
                    : fail(ctx.getSource(), "Could not unlock " + skillId + ". Check skill points and prerequisites.");
            }))))
            .then(literal("equipskill").requires(ShyneCommand::isAdmin).then(argument("player", StringArgumentType.word()).then(argument("slot", StringArgumentType.word()).then(argument("skill_id", StringArgumentType.word()).executes(ctx -> {
                ServerPlayer player = resolvePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                if (player == null) return fail(ctx.getSource(), "Player not found.");
                Optional<SkillSlot> slot = SkillSlot.tryParse(StringArgumentType.getString(ctx, "slot"));
                if (slot.isEmpty()) return fail(ctx.getSource(), "Invalid slot. Use primary, secondary, utility, ultimate, passive_1, or passive_2.");
                String skillId = StringArgumentType.getString(ctx, "skill_id");
                return profileRuntime.equipSkill(player, slot.get(), skillId)
                    ? success(ctx.getSource(), "Equipped " + skillId + " into " + slot.get().name().toLowerCase(Locale.ROOT))
                    : fail(ctx.getSource(), "Could not equip " + skillId + ". Unlock the skill first.");
            })))))
            .then(literal("equipweapon").requires(ShyneCommand::isAdmin).then(argument("player", StringArgumentType.word()).then(argument("slot", StringArgumentType.word()).then(argument("weapon_id", StringArgumentType.word()).executes(ctx -> {
                ServerPlayer player = resolvePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                if (player == null) return fail(ctx.getSource(), "Player not found.");
                String weaponId = StringArgumentType.getString(ctx, "weapon_id");
                return equipmentRuntime.equipWeapon(player, StringArgumentType.getString(ctx, "slot"), weaponId)
                    ? success(ctx.getSource(), "Equipped " + weaponId + " for " + player.getName().getString())
                    : fail(ctx.getSource(), "Could not equip weapon. Check the weapon_id and player target.");
            })))))
            .then(literal("giveitem").requires(ShyneCommand::isAdmin).then(argument("player", StringArgumentType.word()).then(argument("item_id", StringArgumentType.word())
                .executes(ctx -> giveItem(ctx.getSource(), itemRuntime, StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "item_id"), 1))
                .then(argument("count", IntegerArgumentType.integer(1, 2304)).executes(ctx -> giveItem(ctx.getSource(), itemRuntime,
                    StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "item_id"), IntegerArgumentType.getInteger(ctx, "count")))))))
            .then(literal("castskill").requires(ShyneCommand::isAdmin).then(argument("player", StringArgumentType.word()).then(argument("skill_id", StringArgumentType.word()).executes(ctx -> {
                ServerPlayer player = resolvePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                if (player == null) return fail(ctx.getSource(), "Player not found.");
                String skillId = StringArgumentType.getString(ctx, "skill_id");
                SkillDefinition definition = skillRegistry.get(skillId);
                if (definition == null) return fail(ctx.getSource(), "Unknown skill_id: " + skillId);
                SkillExecutor.Status status = skillExecutor.execute(player, skillId, definition.defaultSlot(), "command", 0);
                return status == SkillExecutor.Status.CAST
                    ? success(ctx.getSource(), "Cast " + skillId + " for " + player.getName().getString())
                    : fail(ctx.getSource(), "Could not cast " + skillId + ": " + status.name().toLowerCase(Locale.ROOT));
            }))))
            .then(literal("attachmodel").requires(ShyneCommand::isAdmin).then(argument("entity", StringArgumentType.word()).then(argument("model_id", StringArgumentType.word()).then(argument("scale", FloatArgumentType.floatArg(0.01f, 20f)).executes(ctx ->
                attachModel(ctx.getSource(), attachmentRuntime, bbModelRegistry, StringArgumentType.getString(ctx, "entity"), StringArgumentType.getString(ctx, "model_id"), FloatArgumentType.getFloat(ctx, "scale"))
            )))))
            .then(literal("detachmodel").requires(ShyneCommand::isAdmin).then(argument("entity", StringArgumentType.word()).executes(ctx ->
                detachModel(ctx.getSource(), attachmentRuntime, StringArgumentType.getString(ctx, "entity"))
            )))
            .then(literal("playanim").requires(ShyneCommand::isAdmin).then(argument("entity", StringArgumentType.word()).then(argument("model_id", StringArgumentType.word()).then(argument("animation", StringArgumentType.word()).executes(ctx ->
                playAnimation(ctx.getSource(), animationRuntime, bbModelRegistry, StringArgumentType.getString(ctx, "entity"), StringArgumentType.getString(ctx, "model_id"), StringArgumentType.getString(ctx, "animation"))
            )))))
            .then(literal("stopanim").requires(ShyneCommand::isAdmin).then(argument("entity", StringArgumentType.word()).executes(ctx ->
                stopAnimation(ctx.getSource(), animationRuntime, StringArgumentType.getString(ctx, "entity"))
            )))
            .then(literal("resetcombo").requires(ShyneCommand::isAdmin).then(argument("entity", StringArgumentType.word()).executes(ctx -> {
                Entity entity = resolveEntity(ctx.getSource(), StringArgumentType.getString(ctx, "entity"));
                if (entity == null) return fail(ctx.getSource(), "Entity not found.");
                powerStateMachine.reset(entity.getUUID());
                feedback(ctx.getSource(), "Reset combo state for " + entity.getName().getString());
                return Command.SINGLE_SUCCESS;
            })))
            .then(sizeCommand())
            .then(literal("help").executes(ctx -> showHelp(ctx.getSource())))
            );
            dispatcher.register(literal("sjyne")
                .executes(ctx -> showHelp(ctx.getSource()))
                .then(sizeCommand())
                .then(literal("help").executes(ctx -> showHelp(ctx.getSource()))));
        });
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return Commands.LEVEL_ADMINS.check(source.permissions());
    }

    private static int showHelp(CommandSourceStack source) {
        feedback(source, Component.translatable("command.shyne.help.title"));
        feedback(source, Component.translatable("command.shyne.help.usage"));
        feedback(source, Component.translatable("command.shyne.help.avatar"));
        feedback(source, Component.translatable("command.shyne.help.size"));
        if (isAdmin(source)) {
            feedback(source, Component.translatable("command.shyne.help.admin.list"));
            feedback(source, Component.translatable("command.shyne.help.admin.actions"));
        } else {
            feedback(source, Component.translatable("command.shyne.help.player"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sizeCommand() {
        return literal("size")
            .executes(ctx -> showPlayerScale(ctx.getSource(), ctx.getSource().getPlayerOrException()))
            .then(literal("reset").executes(ctx -> setPlayerScale(ctx.getSource(), ctx.getSource().getPlayerOrException(), 1.0f, true)))
            .then(argument("scale", FloatArgumentType.floatArg(MIN_PLAYER_SCALE, MAX_PLAYER_SCALE)).executes(ctx ->
                setPlayerScale(ctx.getSource(), ctx.getSource().getPlayerOrException(), FloatArgumentType.getFloat(ctx, "scale"), false)
            ))
            .then(argument("player", EntityArgument.player()).requires(ShyneCommand::isAdmin)
                .then(literal("reset").executes(ctx ->
                    setPlayerScale(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), 1.0f, true)
                ))
                .then(argument("scale", FloatArgumentType.floatArg(MIN_PLAYER_SCALE, MAX_PLAYER_SCALE)).executes(ctx ->
                    setPlayerScale(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), FloatArgumentType.getFloat(ctx, "scale"), false)
                )));
    }

    private static int showPlayerScale(CommandSourceStack source, ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.SCALE);
        if (attribute == null) return fail(source, Component.translatable("command.shyne.size.unavailable"));
        feedback(source, Component.translatable(
            "command.shyne.size.current",
            player.getDisplayName(),
            formatScale(attribute.getBaseValue()),
            formatScale(player.getScale())
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int setPlayerScale(CommandSourceStack source, ServerPlayer player, float scale, boolean reset) {
        AttributeInstance attribute = player.getAttribute(Attributes.SCALE);
        if (attribute == null) return fail(source, Component.translatable("command.shyne.size.unavailable"));
        attribute.setBaseValue(scale);
        player.refreshDimensions();

        Component result = Component.translatable(
            reset ? "command.shyne.size.reset" : "command.shyne.size.changed",
            player.getDisplayName(),
            formatScale(player.getScale())
        );
        feedback(source, result);
        if (source.getEntity() != player) {
            player.sendSystemMessage(Component.translatable(
                reset ? "command.shyne.size.target_reset" : "command.shyne.size.target_changed",
                formatScale(player.getScale())
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String formatScale(double scale) {
        return String.format(Locale.ROOT, "%.2f", scale);
    }

    private static int reloadContent(CommandSourceStack source, ShyneModLoader modLoader, SkillRegistry skillRegistry,
                                     EquipmentRuntime equipmentRuntime, ShyneItemRuntime itemRuntime,
                                     PlayerProfileRuntime profileRuntime, ContentDiagnostics diagnostics) {
        modLoader.discoverAndLoad();
        skillRegistry.discover(modLoader.getShyneModsDir());
        equipmentRuntime.discover(modLoader.getShyneModsDir());
        itemRuntime.discover(modLoader.getShyneModsDir());
        int profileFixes = profileRuntime.validateProfiles();
        modLoader.fireHook("on_reload", Map.of("mods", modLoader.getLoadedCount()));
        modLoader.fireHook("on_server_start", Map.of("version", source.getServer().getServerVersion()));
        feedback(source, "Reloaded Lua content: mods=" + modLoader.getLoadedCount() + " skills=" + skillRegistry.all().size() + " weapons=" + equipmentRuntime.allWeapons().size() + " items=" + itemRuntime.all().size() + " profile_fixes=" + profileFixes);
        feedback(source, diagnostics.summary() + " | report=" + diagnostics.textReportPath());
        return Command.SINGLE_SUCCESS;
    }

    private static int listMods(CommandSourceStack source, ShyneModLoader modLoader) {
        if (modLoader.getLoadedMods().isEmpty()) return fail(source, "No Lua content packs are currently loaded.");
        StringBuilder sb = new StringBuilder("[ShyneCreator] Loaded mods:");
        for (ShyneModLoader.ModInfo mod : modLoader.getLoadedMods()) {
            sb.append("\n- ").append(mod.id).append(" v").append(mod.version).append(" by ").append(mod.author).append(" @ ").append(mod.path);
        }
        feedback(source, sb.toString());
        return Command.SINGLE_SUCCESS;
    }

    private static int listModels(CommandSourceStack source, BbModelRegistry bbModelRegistry) {
        return listSimple(source, "Loaded models", bbModelRegistry.all(), model -> model.modelId() + " [" + model.sourceModId() + "]");
    }

    private static int listSkills(CommandSourceStack source, SkillRegistry skillRegistry) {
        return listSimple(source, "Registered skills", skillRegistry.all(), skill -> skill.skillId() + " (" + skill.defaultSlot().name().toLowerCase(Locale.ROOT) + ", " + skill.castType().name().toLowerCase(Locale.ROOT) + ")");
    }

    private static int listWeapons(CommandSourceStack source, EquipmentRuntime equipmentRuntime) {
        return listSimple(source, "Registered weapons", equipmentRuntime.allWeapons(), weapon -> weapon.weaponId() + " -> " + weapon.itemId() + " [" + weapon.classTag() + "]");
    }

    private static int listItems(CommandSourceStack source, ShyneItemRuntime itemRuntime) {
        return listSimple(source, "Registered Shyne items", itemRuntime.all(), item -> item.itemId()
            + (item.useSkill().isBlank() ? "" : " -> " + item.useSkill()) + " [" + item.rarity().getSerializedName() + "]");
    }

    private static int giveItem(CommandSourceStack source, ShyneItemRuntime itemRuntime, String playerToken, String itemId, int count) {
        ServerPlayer player = resolvePlayer(source, playerToken);
        if (player == null) return fail(source, "Player not found.");
        return itemRuntime.give(player, itemId, count)
            ? success(source, "Gave " + count + "x " + itemId + " to " + player.getName().getString())
            : fail(source, "Unknown Shyne item_id: " + itemId);
    }

    private static int listSummons(CommandSourceStack source, SummonRuntime summonRuntime) {
        return listSimple(source, "Active summons", summonRuntime.all(), summon -> summon.entityId() + " owner=" + summon.ownerName() + " tag=" + summon.summonTag());
    }

    private static int listProjectiles(CommandSourceStack source, ProjectileRuntime projectileRuntime) {
        return listSimple(source, "Active projectiles", projectileRuntime.all(), projectile -> projectile.projectileEntityId() + " tag=" + projectile.projectileTag() + " damage=" + projectile.damage());
    }

    private static int listProfiles(CommandSourceStack source, PlayerProfileRuntime profileRuntime) {
        return listSimple(source, "Player profiles", profileRuntime.all(), profile -> profile.playerName() + " lvl=" + profile.level() + " xp=" + profile.experience() + " unlocked=" + profile.unlockedSkills().size());
    }

    private static int listLoadouts(CommandSourceStack source, EquipmentRuntime equipmentRuntime) {
        return listSimple(source, "Equipment loadouts", equipmentRuntime.allLoadouts(), loadout -> loadout.entityId() + " main=" + loadout.mainHandWeaponId() + " off=" + loadout.offHandWeaponId());
    }

    private static int showProfile(CommandSourceStack source, PlayerProfileRuntime profileRuntime, EquipmentRuntime equipmentRuntime, String token) {
        ServerPlayer player = resolvePlayer(source, token);
        if (player == null) return fail(source, "Player not found.");
        PlayerProfile profile = profileRuntime.get(player.getUUID()).orElseGet(() -> profileRuntime.getOrCreate(player));
        EquipmentLoadout loadout = equipmentRuntime.getLoadout(player.getUUID()).orElse(null);
        StringBuilder sb = new StringBuilder("[ShyneCreator] Profile of ").append(profile.playerName());
        sb.append("\n- level=").append(profile.level()).append(" xp=").append(profile.experience()).append(" skill_points=").append(profile.skillPoints()).append(" stat_points=").append(profile.statPoints());
        sb.append("\n- class=").append(profile.playerClass()).append(" team=").append(profile.teamId().isBlank() ? "-" : profile.teamId());
        sb.append("\n- unlocked=").append(profile.unlockedSkills().isEmpty() ? "-" : String.join(", ", profile.unlockedSkills()));
        sb.append("\n- equipped=").append(profile.equippedSkills().isEmpty() ? "-" : profile.equippedSkills());
        if (loadout != null) sb.append("\n- loadout=").append(loadout.slots().isEmpty() ? "-" : loadout.slots());
        feedback(source, sb.toString());
        return Command.SINGLE_SUCCESS;
    }

    private static int listDiagnostics(CommandSourceStack source, ContentDiagnostics diagnostics, int limit) {
        if (diagnostics.snapshot().isEmpty()) return success(source, "No diagnostics issues are currently recorded.");
        StringBuilder sb = new StringBuilder("[ShyneCreator] Recent diagnostics:");
        for (ContentIssue issue : diagnostics.recent(limit)) sb.append("\n- ").append(issue.formatForHumans());
        feedback(source, sb.toString());
        return Command.SINGLE_SUCCESS;
    }

    private static <T> int listSimple(CommandSourceStack source, String title, Collection<T> entries, java.util.function.Function<T, String> formatter) {
        if (entries.isEmpty()) return fail(source, title + ": none");
        StringBuilder sb = new StringBuilder("[ShyneCreator] ").append(title).append(":");
        for (T entry : entries) sb.append("\n- ").append(formatter.apply(entry));
        feedback(source, sb.toString());
        return Command.SINGLE_SUCCESS;
    }

    private static int attachModel(CommandSourceStack source, AttachmentRuntime attachmentRuntime, BbModelRegistry modelRegistry, String entityToken, String modelId, float scale) {
        Entity entity = resolveEntity(source, entityToken);
        if (entity == null) return fail(source, "Entity not found.");
        if (modelRegistry.get(modelId) == null) return fail(source, "Unknown model_id: " + modelId);
        return attachmentRuntime.attach(entity, modelId, 0f, 0f, 0f, scale, "") ? success(source, "Attached model " + modelId + " to " + entity.getName().getString()) : fail(source, "Could not attach model.");
    }

    private static int detachModel(CommandSourceStack source, AttachmentRuntime attachmentRuntime, String entityToken) {
        Entity entity = resolveEntity(source, entityToken);
        if (entity == null) return fail(source, "Entity not found.");
        attachmentRuntime.detach(entity);
        return success(source, "Detached model from " + entity.getName().getString());
    }

    private static int playAnimation(CommandSourceStack source, AnimationRuntime animationRuntime, BbModelRegistry modelRegistry, String entityToken, String modelId, String animationName) {
        Entity entity = resolveEntity(source, entityToken);
        if (entity == null) return fail(source, "Entity not found.");
        if (modelRegistry.get(modelId) == null) return fail(source, "Unknown model_id: " + modelId);
        return animationRuntime.play(entity, modelId, animationName) ? success(source, "Playing " + animationName + " on " + entity.getName().getString()) : fail(source, "Could not play animation.");
    }

    private static int stopAnimation(CommandSourceStack source, AnimationRuntime animationRuntime, String entityToken) {
        Entity entity = resolveEntity(source, entityToken);
        if (entity == null) return fail(source, "Entity not found.");
        animationRuntime.stop(entity);
        return success(source, "Stopped animation on " + entity.getName().getString());
    }

    private static int success(CommandSourceStack source, String message) {
        feedback(source, message);
        return Command.SINGLE_SUCCESS;
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("[ShyneCreator] " + message));
        return 0;
    }

    private static int fail(CommandSourceStack source, Component message) {
        source.sendFailure(Component.literal("[ShyneCreator] ").append(message));
        return 0;
    }

    private static void feedback(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("[ShyneCreator] " + message), false);
    }

    private static void feedback(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> Component.literal("[ShyneCreator] ").append(message), false);
    }

    private static ServerPlayer resolvePlayer(CommandSourceStack source, String token) {
        Entity entity = resolveEntity(source, token);
        return entity instanceof ServerPlayer player ? player : null;
    }

    private static Entity resolveEntity(CommandSourceStack source, String token) {
        var player = source.getServer().getPlayerList().getPlayer(token);
        if (player != null) return player;
        try {
            UUID uuid = UUID.fromString(token);
            for (var world : source.getServer().getAllLevels()) {
                Entity entity = world.getEntityInAnyDimension(uuid);
                if (entity != null) return entity;
            }
        } catch (IllegalArgumentException ignored) {}
        for (var world : source.getServer().getAllLevels()) {
            for (Entity entity : world.getAllEntities()) {
                if (entity.getName().getString().equalsIgnoreCase(token)) return entity;
            }
        }
        return null;
    }
}

package seashyne.shynecore.client.avatar.bridge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.List;

import static seashyne.shynecore.client.avatar.bridge.AvatarBridgeHelper.vec3;

/**
 * Handles read queries for player state, world queries, blocks, entities, raycasting, and physics probes.
 */
public final class AvatarWorldBridge {
    private AvatarWorldBridge() {}

    public static void register(Globals globals) {
        globals.set("_shyne_read", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Minecraft client = Minecraft.getInstance();
                var player = client.player;
                String key = args.arg(1).optjstring("");
                if ("client.singleplayer".equals(key)) return LuaValue.valueOf(client.hasSingleplayerServer());
                if ("world.loaded".equals(key)) return LuaValue.valueOf(client.level != null);
                if ("settings.powers_enabled".equals(key)) return LuaValue.valueOf(seashyne.shynecore.client.config.ShyneClientSettings.avatarPowersEnabled);
                if ("settings.weapons_enabled".equals(key)) return LuaValue.valueOf(seashyne.shynecore.client.config.ShyneClientSettings.signatureWeaponsEnabled);
                if ("settings.hud_enabled".equals(key)) return LuaValue.valueOf(seashyne.shynecore.client.config.ShyneClientSettings.combatHudEnabled);
                if (player == null) return LuaValue.NIL;
                return switch (key) {
                    case "player.loaded" -> LuaValue.TRUE;
                    case "player.pos" -> vec3(player.getX(), player.getY(), player.getZ());
                    case "player.velocity" -> vec3(player.getDeltaMovement().x, player.getDeltaMovement().y, player.getDeltaMovement().z);
                    case "player.rot" -> vec3(player.getXRot(), player.getYRot(), 0);
                    case "player.look" -> vec3(player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z);
                    case "player.body_yaw" -> LuaValue.valueOf(player.yBodyRot);
                    case "player.in_water" -> LuaValue.valueOf(player.isInWater());
                    case "player.underwater" -> LuaValue.valueOf(player.isUnderWater());
                    case "player.in_lava" -> LuaValue.valueOf(player.isInLava());
                    case "player.wet" -> LuaValue.valueOf(player.isInWaterOrRain());
                    case "player.on_ground" -> LuaValue.valueOf(player.onGround());
                    case "player.crouching" -> LuaValue.valueOf(player.isCrouching());
                    case "player.swimming" -> LuaValue.valueOf(player.isSwimming());
                    case "player.fall_flying" -> LuaValue.valueOf(player.isFallFlying());
                    case "player.sleeping" -> LuaValue.valueOf(player.isSleeping());
                    case "player.left_handed" -> LuaValue.valueOf(player.getMainArm() == HumanoidArm.LEFT);
                    case "player.using_item" -> LuaValue.valueOf(player.isUsingItem());
                    case "player.active_item_time" -> LuaValue.valueOf(player.getTicksUsingItem());
                    case "player.pose" -> LuaValue.valueOf(player.getPose().name());
                    case "player.vehicle" -> vehicleInfo(player.getVehicle());
                    case "player.target" -> targetInfo(player, args.arg(2).optdouble(6));
                    case "player.effects" -> activeEffects(player);
                    case "player.swing" -> LuaValue.valueOf(1.0f - player.getAttackStrengthScale(0));
                    case "player.name" -> LuaValue.valueOf(player.getName().getString());
                    case "player.uuid" -> LuaValue.valueOf(player.getStringUUID());
                    case "player.health" -> LuaValue.valueOf(player.getHealth());
                    case "player.max_health" -> LuaValue.valueOf(player.getMaxHealth());
                    case "player.sprinting" -> LuaValue.valueOf(player.isSprinting());
                    case "player.main_hand" -> itemStack(player.getMainHandItem());
                    case "player.off_hand" -> itemStack(player.getOffhandItem());
                    case "player.armor_head" -> itemStack(player.getItemBySlot(EquipmentSlot.HEAD));
                    case "player.armor_chest" -> itemStack(player.getItemBySlot(EquipmentSlot.CHEST));
                    case "player.armor_legs" -> itemStack(player.getItemBySlot(EquipmentSlot.LEGS));
                    case "player.armor_feet" -> itemStack(player.getItemBySlot(EquipmentSlot.FEET));
                    case "player.active_hand" -> LuaValue.valueOf(player.isUsingItem() ? player.getUsedItemHand().name() : "NONE");
                    case "world.time" -> LuaValue.valueOf(player.level().getGameTime());
                    case "world.day_time" -> LuaValue.valueOf(player.level().getGameTime() % 24_000L);
                    case "world.raining" -> LuaValue.valueOf(player.level().isRaining());
                    case "player.item" -> itemBySlot(player, args.arg(2));
                    case "world.light" -> LuaValue.valueOf(player.level().getMaxLocalRawBrightness(BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()))));
                    case "world.block" -> LuaValue.valueOf(BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()))).getBlock()).toString());
                    case "world.block_info" -> AvatarProbeHelper.blockInfo(player, args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()));
                    case "world.probe" -> AvatarProbeHelper.physicsProbe(player, args);
                    case "world.raycast_block" -> AvatarProbeHelper.raycastBlock(player, args);
                    case "world.raycast_entity" -> AvatarProbeHelper.raycastEntity(player, args);
                    case "world.block_light" -> LuaValue.valueOf(player.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()))));
                    case "world.sky_light" -> LuaValue.valueOf(player.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()))));
                    case "world.redstone" -> LuaValue.valueOf(player.level().getBestNeighborSignal(BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()))));
                    case "world.thundering" -> LuaValue.valueOf(player.level().isThundering());
                    case "world.biome" -> LuaValue.valueOf(player.level().getBiome(BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ())))
                        .unwrapKey().map(entryKey -> entryKey.identifier().toString()).orElse(""));
                    case "client.paused" -> LuaValue.valueOf(client.isPaused());
                    case "client.first_person" -> LuaValue.valueOf(client.options.getCameraType().isFirstPerson());
                    case "client.camera_is_player" -> LuaValue.valueOf(client.getCameraEntity() == player);
                    case "client.camera_backwards" -> LuaValue.valueOf(client.options.getCameraType().isMirrored());
                    case "client.chat_open" -> LuaValue.valueOf(client.gui != null && client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen);
                    case "client.fps" -> LuaValue.valueOf(client.getFps());
                    case "client.mouse_x" -> LuaValue.valueOf(client.mouseHandler.xpos());
                    case "client.mouse_y" -> LuaValue.valueOf(client.mouseHandler.ypos());
                    case "client.window_w" -> LuaValue.valueOf(client.getWindow().getGuiScaledWidth());
                    case "client.window_h" -> LuaValue.valueOf(client.getWindow().getGuiScaledHeight());
                    case "client.camera_pos" -> {
                        var cam = client.gameRenderer.mainCamera();
                        yield vec3(cam.position().x, cam.position().y, cam.position().z);
                    }
                    case "client.camera_rot" -> {
                        var cam = client.gameRenderer.mainCamera();
                        yield vec3(cam.xRot(), cam.yRot(), 0);
                    }
                    case "client.fov" -> LuaValue.valueOf(client.options.fov().get());
                    case "player.voice_level" -> LuaValue.valueOf(seashyne.shynecore.voice.ShyneMicrophoneState.getSpeakerSnapshot(player.getUUID()).level());
                    case "player.speaking" -> LuaValue.valueOf(seashyne.shynecore.voice.ShyneMicrophoneState.getSpeakerSnapshot(player.getUUID()).speaking());
                    case "player.alive" -> LuaValue.valueOf(player.isAlive());
                    case "world.players" -> nearbyPlayers(player, args.arg(2).optdouble(32.0), args.arg(3).optboolean(false));
                    case "world.entities" -> nearbyEntities(player, args.arg(2).optdouble(16.0));
                    default -> LuaValue.NIL;
                };
            }
        });

        globals.set("_shyne_player_set_velocity", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (!seashyne.shynecore.client.config.ShyneClientSettings.avatarPowersEnabled) {
                    return LuaValue.FALSE;
                }
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) return LuaValue.FALSE;
                double vx = args.arg(1).optdouble(client.player.getDeltaMovement().x);
                double vy = args.arg(2).optdouble(client.player.getDeltaMovement().y);
                double vz = args.arg(3).optdouble(client.player.getDeltaMovement().z);
                client.player.setDeltaMovement(vx, vy, vz);
                return LuaValue.TRUE;
            }
        });
    }

    public static LuaTable itemStack(ItemStack stack) {
        LuaTable value = new LuaTable();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        value.set("id", LuaValue.valueOf(itemId));
        value.set("name", LuaValue.valueOf(stack.getHoverName().getString()));
        value.set("material", LuaValue.valueOf(armorMaterial(itemId)));
        value.set("count", LuaValue.valueOf(stack.getCount()));
        value.set("empty", LuaValue.valueOf(stack.isEmpty()));
        value.set("damage", LuaValue.valueOf(stack.isDamageableItem() ? stack.getDamageValue() : 0));
        value.set("max_damage", LuaValue.valueOf(stack.isDamageableItem() ? stack.getMaxDamage() : 0));
        value.set("glint", LuaValue.valueOf(stack.hasFoil()));
        var trim = stack.get(DataComponents.TRIM);
        if (trim != null) {
            value.set("trim_material", LuaValue.valueOf(trim.material().unwrapKey().map(key -> key.identifier().toString()).orElse("")));
            value.set("trim_pattern", LuaValue.valueOf(trim.pattern().unwrapKey().map(key -> key.identifier().toString()).orElse("")));
        } else {
            value.set("trim_material", LuaValue.NIL);
            value.set("trim_pattern", LuaValue.NIL);
        }
        return value;
    }

    public static String armorMaterial(String itemId) {
        for (String material : List.of("leather", "chainmail", "iron", "golden", "diamond", "netherite", "turtle", "copper")) {
            if (itemId.contains(material)) return "minecraft:" + material;
        }
        return "";
    }

    public static LuaTable activeEffects(Player player) {
        LuaTable result = new LuaTable();
        int index = 1;
        for (var effect : player.getActiveEffects()) {
            LuaTable value = new LuaTable();
            value.set("id", LuaValue.valueOf(effect.getEffect().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse(effect.getEffect().value().getDescriptionId())));
            value.set("ambient", LuaValue.valueOf(effect.isAmbient()));
            value.set("visible", LuaValue.valueOf(effect.isVisible()));
            value.set("amplifier", LuaValue.valueOf(effect.getAmplifier()));
            value.set("duration", LuaValue.valueOf(effect.getDuration()));
            result.set(index++, value);
        }
        return result;
    }

    public static LuaValue vehicleInfo(Entity vehicle) {
        if (vehicle == null) return LuaValue.NIL;
        LuaTable value = new LuaTable();
        value.set("type", LuaValue.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString()));
        value.set("name", LuaValue.valueOf(vehicle.getName().getString()));
        value.set("position", vec3(vehicle.getX(), vehicle.getY(), vehicle.getZ()));
        value.set("velocity", vec3(vehicle.getDeltaMovement().x, vehicle.getDeltaMovement().y, vehicle.getDeltaMovement().z));
        value.set("rotation", vec3(vehicle.getXRot(), vehicle.getYRot(), 0));
        value.set("on_ground", LuaValue.valueOf(vehicle.onGround()));
        value.set("passenger_count", LuaValue.valueOf(vehicle.getPassengers().size()));
        value.set("uuid", LuaValue.valueOf(vehicle.getStringUUID()));
        return value;
    }

    public static LuaValue targetInfo(Player player, double range) {
        double boundedRange = Math.max(1.0, Math.min(128.0, range));
        var hit = player.pick(boundedRange, 0f, false);
        var start = player.getEyePosition();
        var end = start.add(player.getLookAngle().scale(boundedRange));
        double nearestDistance = hit.getType() == HitResult.Type.MISS ? boundedRange : start.distanceTo(hit.getLocation());
        Entity nearestEntity = null;
        var search = player.getBoundingBox().expandTowards(player.getLookAngle().scale(boundedRange)).inflate(1.0);
        for (var entity : player.level().getEntities(player, search, entity -> entity.isPickable() && !entity.isSpectator())) {
            var intersection = entity.getBoundingBox().inflate(0.3).clip(start, end);
            if (intersection.isEmpty()) continue;
            double distance = start.distanceTo(intersection.get());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestEntity = entity;
            }
        }
        LuaTable value = new LuaTable();
        value.set("distance", LuaValue.valueOf(nearestDistance));
        if (nearestEntity != null) {
            value.set("type", LuaValue.valueOf("ENTITY"));
            value.set("position", vec3(nearestEntity.getX(), nearestEntity.getY(), nearestEntity.getZ()));
            value.set("entity_id", LuaValue.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(nearestEntity.getType()).toString()));
            value.set("uuid", LuaValue.valueOf(nearestEntity.getStringUUID()));
            value.set("name", LuaValue.valueOf(nearestEntity.getName().getString()));
            return value;
        }
        value.set("type", LuaValue.valueOf(hit.getType().name()));
        value.set("position", vec3(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z));
        if (hit instanceof BlockHitResult blockHit) {
            var blockPos = blockHit.getBlockPos();
            var block = player.level().getBlockState(blockPos).getBlock();
            value.set("block", LuaValue.valueOf(BuiltInRegistries.BLOCK.getKey(block).toString()));
            value.set("block_position", vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            value.set("face", LuaValue.valueOf(blockHit.getDirection().getName()));
        }
        return value;
    }

    public static LuaTable itemBySlot(Player player, LuaValue slotArg) {
        if (player == null) return itemStack(net.minecraft.world.item.ItemStack.EMPTY);
        if (slotArg.isnumber()) {
            int slot = slotArg.toint();
            var stack = switch (slot) {
                case 1 -> player.getMainHandItem();
                case 2 -> player.getOffhandItem();
                case 3 -> player.getItemBySlot(EquipmentSlot.FEET);
                case 4 -> player.getItemBySlot(EquipmentSlot.LEGS);
                case 5 -> player.getItemBySlot(EquipmentSlot.CHEST);
                case 6 -> player.getItemBySlot(EquipmentSlot.HEAD);
                default -> net.minecraft.world.item.ItemStack.EMPTY;
            };
            return itemStack(stack);
        }
        String slotStr = slotArg.optjstring("mainhand").toLowerCase(java.util.Locale.ROOT);
        var stack = switch (slotStr) {
            case "mainhand", "main_hand", "hand" -> player.getMainHandItem();
            case "offhand", "off_hand" -> player.getOffhandItem();
            case "head", "helmet" -> player.getItemBySlot(EquipmentSlot.HEAD);
            case "chest", "chestplate" -> player.getItemBySlot(EquipmentSlot.CHEST);
            case "legs", "leggings" -> player.getItemBySlot(EquipmentSlot.LEGS);
            case "feet", "boots" -> player.getItemBySlot(EquipmentSlot.FEET);
            default -> net.minecraft.world.item.ItemStack.EMPTY;
        };
        return itemStack(stack);
    }

    public static LuaTable nearbyPlayers(Player viewer, double radius, boolean includeSelf) {
        double boundedRadius = Math.max(0.5, Math.min(256.0, radius));
        double rSq = boundedRadius * boundedRadius;
        LuaTable list = new LuaTable();
        int index = 1;
        for (Player p : viewer.level().players()) {
            if (!includeSelf && p.getUUID().equals(viewer.getUUID())) continue;
            double distSq = viewer.distanceToSqr(p);
            if (distSq <= rSq) {
                list.set(index++, playerToLua(viewer, p, Math.sqrt(distSq)));
            }
        }
        return list;
    }

    public static LuaTable nearbyEntities(Player viewer, double radius) {
        double boundedRadius = Math.max(0.5, Math.min(128.0, radius));
        AABB box = viewer.getBoundingBox().inflate(boundedRadius);
        List<Entity> entities = viewer.level().getEntities(viewer, box, e -> e != null && e.isAlive() && !e.isSpectator());
        LuaTable list = new LuaTable();
        int index = 1;
        for (Entity e : entities) {
            double dist = viewer.distanceTo(e);
            if (dist <= boundedRadius) {
                list.set(index++, entityToLua(e, dist));
            }
        }
        return list;
    }

    private static LuaTable playerToLua(Player viewer, Player target, double distance) {
        LuaTable table = new LuaTable();
        table.set("uuid", LuaValue.valueOf(target.getStringUUID()));
        table.set("name", LuaValue.valueOf(target.getName().getString()));
        table.set("pos", vec3(target.getX(), target.getY(), target.getZ()));
        table.set("distance", LuaValue.valueOf(distance));
        table.set("health", LuaValue.valueOf(target.getHealth()));
        table.set("max_health", LuaValue.valueOf(target.getMaxHealth()));
        table.set("crouching", LuaValue.valueOf(target.isCrouching()));
        table.set("sprinting", LuaValue.valueOf(target.isSprinting()));
        table.set("gliding", LuaValue.valueOf(target.isFallFlying()));
        table.set("in_water", LuaValue.valueOf(target.isInWater()));
        table.set("on_ground", LuaValue.valueOf(target.onGround()));
        table.set("is_self", LuaValue.valueOf(target.getUUID().equals(viewer.getUUID())));
        return table;
    }

    private static LuaTable entityToLua(Entity e, double distance) {
        LuaTable table = new LuaTable();
        table.set("uuid", LuaValue.valueOf(e.getStringUUID()));
        table.set("name", LuaValue.valueOf(e.getName().getString()));
        table.set("type", LuaValue.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()));
        table.set("pos", vec3(e.getX(), e.getY(), e.getZ()));
        table.set("distance", LuaValue.valueOf(distance));
        if (e instanceof LivingEntity living) {
            table.set("health", LuaValue.valueOf(living.getHealth()));
            table.set("max_health", LuaValue.valueOf(living.getMaxHealth()));
            table.set("is_living", LuaValue.TRUE);
        } else {
            table.set("health", LuaValue.ZERO);
            table.set("max_health", LuaValue.ZERO);
            table.set("is_living", LuaValue.FALSE);
        }
        table.set("is_player", LuaValue.valueOf(e instanceof Player));
        table.set("is_monster", LuaValue.valueOf(e instanceof Monster));
        table.set("on_ground", LuaValue.valueOf(e.onGround()));
        return table;
    }

    private record BlockProbeHit(BlockHitResult hit, double distance) {}
}

package seashyne.shynecore.client.avatar.bridge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
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
                    case "world.light" -> LuaValue.valueOf(player.level().getMaxLocalRawBrightness(BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()))));
                    case "world.block" -> LuaValue.valueOf(BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()))).getBlock()).toString());
                    case "world.block_info" -> blockInfo(player, args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()));
                    case "world.probe" -> physicsProbe(player, args);
                    case "world.biome" -> LuaValue.valueOf(player.level().getBiome(BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ())))
                        .unwrapKey().map(entryKey -> entryKey.identifier().toString()).orElse(""));
                    case "client.paused" -> LuaValue.valueOf(client.isPaused());
                    case "client.first_person" -> LuaValue.valueOf(client.options.getCameraType().isFirstPerson());
                    case "client.chat_open" -> LuaValue.valueOf(client.gui != null && client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen);
                    case "player.voice_level" -> LuaValue.valueOf(seashyne.shynecore.voice.ShyneMicrophoneState.getSpeakerSnapshot(player.getUUID()).level());
                    case "player.speaking" -> LuaValue.valueOf(seashyne.shynecore.voice.ShyneMicrophoneState.getSpeakerSnapshot(player.getUUID()).speaking());
                    default -> LuaValue.NIL;
                };
            }
        });
    }

    public static LuaTable itemStack(ItemStack stack) {
        LuaTable value = new LuaTable();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        value.set("id", LuaValue.valueOf(itemId));
        value.set("material", LuaValue.valueOf(armorMaterial(itemId)));
        value.set("count", LuaValue.valueOf(stack.getCount()));
        value.set("empty", LuaValue.valueOf(stack.isEmpty()));
        value.set("damage", LuaValue.valueOf(stack.isDamageableItem() ? stack.getDamageValue() : 0));
        value.set("max_damage", LuaValue.valueOf(stack.isDamageableItem() ? stack.getMaxDamage() : 0));
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

    public static LuaTable blockInfo(Player player, double x, double y, double z) {
        var position = BlockPos.containing(x, y, z);
        var state = player.level().getBlockState(position);
        LuaTable result = new LuaTable();
        result.set("id", LuaValue.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
        result.set("solid", LuaValue.valueOf(!state.getCollisionShape(player.level(), position).isEmpty()));
        result.set("fluid", LuaValue.valueOf(!state.getFluidState().isEmpty()));
        result.set("position", vec3(position.getX(), position.getY(), position.getZ()));
        return result;
    }

    public static LuaTable physicsProbe(Player player, Varargs args) {
        var start = new Vec3(
            args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ())
        );
        var rawDirection = new Vec3(args.arg(5).optdouble(0), args.arg(6).optdouble(0), args.arg(7).optdouble(0));
        var direction = rawDirection.lengthSqr() < 0.000001 ? player.getLookAngle() : rawDirection.normalize();
        double distance = Math.max(0.01, Math.min(16.0, args.arg(8).optdouble(1.0)));
        double radius = Math.max(0.0, Math.min(2.0, args.arg(9).optdouble(0.0)));
        var end = start.add(direction.scale(distance));
        var sweptBlock = sweptBlockHit(player, start, end, radius);
        var blockHit = sweptBlock.hit();
        double nearestDistance = sweptBlock.distance();
        Entity nearestEntity = null;
        var search = new AABB(start, end).inflate(radius);
        for (var entity : player.level().getEntities(player, search, entity -> entity.isPickable() && !entity.isSpectator())) {
            var intersection = entity.getBoundingBox().inflate(radius).clip(start, end);
            if (intersection.isEmpty()) continue;
            double hitDistance = start.distanceTo(intersection.get());
            if (hitDistance < nearestDistance) { nearestDistance = hitDistance; nearestEntity = entity; }
        }
        LuaTable result = new LuaTable();
        result.set("distance", LuaValue.valueOf(nearestDistance));
        if (nearestEntity != null) {
            result.set("hit", LuaValue.TRUE); result.set("type", LuaValue.valueOf("ENTITY"));
            result.set("position", vec3(nearestEntity.getX(), nearestEntity.getY(), nearestEntity.getZ()));
            result.set("entity_id", LuaValue.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(nearestEntity.getType()).toString()));
            result.set("normal", vec3(-direction.x, -direction.y, -direction.z));
            return result;
        }
        result.set("hit", LuaValue.valueOf(blockHit.getType() != HitResult.Type.MISS));
        result.set("type", LuaValue.valueOf(blockHit.getType().name()));
        result.set("position", vec3(blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z));
        if (blockHit instanceof BlockHitResult hit) {
            var face = hit.getDirection();
            result.set("block", LuaValue.valueOf(BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(hit.getBlockPos()).getBlock()).toString()));
            result.set("normal", vec3(face.getStepX(), face.getStepY(), face.getStepZ()));
        } else result.set("normal", vec3(0, 0, 0));
        return result;
    }

    private static BlockProbeHit sweptBlockHit(Player player, Vec3 start, Vec3 end, double radius) {
        var center = clipBlock(player, start, end);
        double closestDistance = center.getType() == HitResult.Type.MISS
            ? start.distanceTo(end) : start.distanceTo(center.getLocation());
        if (radius <= 0.0001) return new BlockProbeHit(center, closestDistance);

        var delta = end.subtract(start);
        if (delta.lengthSqr() < 0.000001) return new BlockProbeHit(center, closestDistance);
        var direction = delta.normalize();
        var reference = Math.abs(direction.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        var right = direction.cross(reference).normalize().scale(radius);
        var up = right.cross(direction).normalize().scale(radius);
        var diagonalA = right.add(up).normalize().scale(radius);
        var diagonalB = right.subtract(up).normalize().scale(radius);
        Vec3[] offsets = {
            right, right.scale(-1), up, up.scale(-1),
            diagonalA, diagonalA.scale(-1), diagonalB, diagonalB.scale(-1)
        };
        for (var offset : offsets) {
            var sampleStart = start.add(offset);
            var sample = clipBlock(player, sampleStart, end.add(offset));
            if (sample.getType() == HitResult.Type.MISS) continue;
            double sampledDistance = sampleStart.distanceTo(sample.getLocation());
            if (center.getType() == HitResult.Type.MISS || sampledDistance < closestDistance) {
                center = sample;
                closestDistance = sampledDistance;
            }
        }
        return new BlockProbeHit(center, closestDistance);
    }

    private static BlockHitResult clipBlock(Player player, Vec3 start, Vec3 end) {
        return player.level().clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ));
    }

    private record BlockProbeHit(BlockHitResult hit, double distance) {}
}

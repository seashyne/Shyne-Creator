package seashyne.shynecore.client.avatar.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import static seashyne.shynecore.client.avatar.bridge.AvatarBridgeHelper.vec3;

/**
 * World raycasting, swept block physics probing, and block state inspection.
 * Coordinates with Minecraft world collision and entity bounding boxes.
 */
public final class AvatarProbeHelper {

    private AvatarProbeHelper() {}

    public static LuaTable blockInfo(Player player, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = player.level().getBlockState(pos);
        LuaTable result = new LuaTable();
        result.set("id", LuaValue.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
        result.set("solid", LuaValue.valueOf(!state.getCollisionShape(player.level(), pos).isEmpty()));
        result.set("fluid", LuaValue.valueOf(!state.getFluidState().isEmpty()));
        result.set("position", vec3(pos.getX(), pos.getY(), pos.getZ()));
        result.set("light", LuaValue.valueOf(player.level().getMaxLocalRawBrightness(pos)));
        result.set("block_light", LuaValue.valueOf(player.level().getBrightness(LightLayer.BLOCK, pos)));
        result.set("sky_light", LuaValue.valueOf(player.level().getBrightness(LightLayer.SKY, pos)));
        result.set("redstone", LuaValue.valueOf(player.level().getBestNeighborSignal(pos)));

        LuaTable properties = new LuaTable();
        for (Property<?> prop : state.getProperties()) {
            Comparable<?> val = state.getValue(prop);
            properties.set(prop.getName(), LuaValue.valueOf(val.toString()));
        }
        result.set("properties", properties);
        return result;
    }

    public static LuaTable raycastBlock(Player player, Varargs args) {
        Vec3 start = new Vec3(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getEyeY()), args.arg(4).optdouble(player.getZ()));
        Vec3 end = new Vec3(args.arg(5).optdouble(start.x), args.arg(6).optdouble(start.y), args.arg(7).optdouble(start.z));
        String shapeStr = args.arg(8).optjstring("COLLIDER").toUpperCase(java.util.Locale.ROOT);
        String fluidStr = args.arg(9).optjstring("NONE").toUpperCase(java.util.Locale.ROOT);

        ClipContext.Block blockShape = switch (shapeStr) {
            case "OUTLINE" -> ClipContext.Block.OUTLINE;
            case "VISUAL" -> ClipContext.Block.VISUAL;
            default -> ClipContext.Block.COLLIDER;
        };
        ClipContext.Fluid fluidHandling = switch (fluidStr) {
            case "ANY" -> ClipContext.Fluid.ANY;
            case "SOURCE_ONLY" -> ClipContext.Fluid.SOURCE_ONLY;
            default -> ClipContext.Fluid.NONE;
        };

        BlockHitResult hit = player.level().clip(new ClipContext(start, end, blockShape, fluidHandling, player));
        LuaTable result = new LuaTable();
        boolean isHit = hit.getType() != HitResult.Type.MISS;
        result.set("hit", LuaValue.valueOf(isHit));
        result.set("type", LuaValue.valueOf(hit.getType().name()));
        result.set("position", vec3(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z));
        result.set("distance", LuaValue.valueOf(start.distanceTo(hit.getLocation())));

        if (isHit) {
            BlockPos bp = hit.getBlockPos();
            result.set("block", LuaValue.valueOf(BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(bp).getBlock()).toString()));
            result.set("block_position", vec3(bp.getX(), bp.getY(), bp.getZ()));
            Direction dir = hit.getDirection();
            result.set("face", LuaValue.valueOf(dir.getName()));
            result.set("normal", vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ()));
        } else {
            result.set("normal", vec3(0, 0, 0));
        }
        return result;
    }

    public static LuaTable raycastEntity(Player player, Varargs args) {
        Vec3 start = new Vec3(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getEyeY()), args.arg(4).optdouble(player.getZ()));
        Vec3 end = new Vec3(args.arg(5).optdouble(start.x), args.arg(6).optdouble(start.y), args.arg(7).optdouble(start.z));
        double radius = Math.max(0.0, Math.min(4.0, args.arg(8).optdouble(0.3)));

        double totalDist = start.distanceTo(end);
        double nearestDistance = totalDist;
        Entity nearestEntity = null;
        Vec3 hitPos = end;

        AABB search = new AABB(start, end).inflate(radius + 1.0);
        for (Entity e : player.level().getEntities(player, search, entity -> entity.isPickable() && !entity.isSpectator())) {
            var intersection = e.getBoundingBox().inflate(radius).clip(start, end);
            if (intersection.isPresent()) {
                double d = start.distanceTo(intersection.get());
                if (d < nearestDistance) {
                    nearestDistance = d;
                    nearestEntity = e;
                    hitPos = intersection.get();
                }
            }
        }

        LuaTable result = new LuaTable();
        if (nearestEntity != null) {
            result.set("hit", LuaValue.TRUE);
            result.set("type", LuaValue.valueOf("ENTITY"));
            result.set("position", vec3(hitPos.x, hitPos.y, hitPos.z));
            result.set("distance", LuaValue.valueOf(nearestDistance));
            result.set("entity_id", LuaValue.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(nearestEntity.getType()).toString()));
            result.set("uuid", LuaValue.valueOf(nearestEntity.getStringUUID()));
            result.set("name", LuaValue.valueOf(nearestEntity.getName().getString()));
            Vec3 dir = end.subtract(start).normalize();
            result.set("normal", vec3(-dir.x, -dir.y, -dir.z));
        } else {
            result.set("hit", LuaValue.FALSE);
            result.set("type", LuaValue.valueOf("MISS"));
            result.set("position", vec3(end.x, end.y, end.z));
            result.set("distance", LuaValue.valueOf(totalDist));
            result.set("normal", vec3(0, 0, 0));
        }
        return result;
    }

    public static LuaTable physicsProbe(Player player, Varargs args) {
        Vec3 start = new Vec3(
            args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ())
        );
        Vec3 rawDirection = new Vec3(args.arg(5).optdouble(0), args.arg(6).optdouble(0), args.arg(7).optdouble(0));
        Vec3 direction = rawDirection.lengthSqr() < 0.000001 ? player.getLookAngle() : rawDirection.normalize();
        double distance = Math.max(0.01, Math.min(16.0, args.arg(8).optdouble(1.0)));
        double radius = Math.max(0.0, Math.min(2.0, args.arg(9).optdouble(0.0)));
        Vec3 end = start.add(direction.scale(distance));
        BlockProbeHit sweptBlock = sweptBlockHit(player, start, end, radius);
        BlockHitResult blockHit = sweptBlock.hit();
        double nearestDistance = sweptBlock.distance();
        Entity nearestEntity = null;
        AABB search = new AABB(start, end).inflate(radius);
        for (Entity entity : player.level().getEntities(player, search, entity -> entity.isPickable() && !entity.isSpectator())) {
            var intersection = entity.getBoundingBox().inflate(radius).clip(start, end);
            if (intersection.isEmpty()) continue;
            double hitDistance = start.distanceTo(intersection.get());
            if (hitDistance < nearestDistance) { nearestDistance = hitDistance; nearestEntity = entity; }
        }
        LuaTable result = new LuaTable();
        result.set("distance", LuaValue.valueOf(nearestDistance));
        if (nearestEntity != null) {
            result.set("hit", LuaValue.TRUE);
            result.set("type", LuaValue.valueOf("ENTITY"));
            result.set("position", vec3(nearestEntity.getX(), nearestEntity.getY(), nearestEntity.getZ()));
            result.set("entity_id", LuaValue.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(nearestEntity.getType()).toString()));
            result.set("normal", vec3(-direction.x, -direction.y, -direction.z));
            return result;
        }
        result.set("hit", LuaValue.valueOf(blockHit.getType() != HitResult.Type.MISS));
        result.set("type", LuaValue.valueOf(blockHit.getType().name()));
        result.set("position", vec3(blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z));
        if (blockHit.getType() != HitResult.Type.MISS) {
            Direction face = blockHit.getDirection();
            result.set("block", LuaValue.valueOf(BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(blockHit.getBlockPos()).getBlock()).toString()));
            result.set("normal", vec3(face.getStepX(), face.getStepY(), face.getStepZ()));
        } else {
            result.set("normal", vec3(0, 0, 0));
        }
        return result;
    }

    private static BlockProbeHit sweptBlockHit(Player player, Vec3 start, Vec3 end, double radius) {
        BlockHitResult center = clipBlock(player, start, end);
        double closestDistance = center.getType() == HitResult.Type.MISS
            ? start.distanceTo(end) : start.distanceTo(center.getLocation());
        if (radius <= 0.0001) return new BlockProbeHit(center, closestDistance);

        Vec3 delta = end.subtract(start);
        if (delta.lengthSqr() < 0.000001) return new BlockProbeHit(center, closestDistance);
        Vec3 direction = delta.normalize();
        Vec3 reference = Math.abs(direction.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = direction.cross(reference).normalize().scale(radius);
        Vec3 up = right.cross(direction).normalize().scale(radius);
        Vec3 diagonalA = right.add(up).normalize().scale(radius);
        Vec3 diagonalB = right.subtract(up).normalize().scale(radius);
        Vec3[] offsets = {
            right, right.scale(-1), up, up.scale(-1),
            diagonalA, diagonalA.scale(-1), diagonalB, diagonalB.scale(-1)
        };
        for (Vec3 offset : offsets) {
            Vec3 sampleStart = start.add(offset);
            BlockHitResult sample = clipBlock(player, sampleStart, end.add(offset));
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

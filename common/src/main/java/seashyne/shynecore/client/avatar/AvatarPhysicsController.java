package seashyne.shynecore.client.avatar;

import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Native secondary-motion controller for {@code shyne_physics} Blockbench groups.
 *
 * <p>The controller writes to its own additive rotation layer. Authored animation,
 * direct Lua rotation and Lua additive rotation therefore remain independent.</p>
 */
public final class AvatarPhysicsController {
    public static final String ROTATION_LAYER = "shyne.physics";

    private final List<Node> nodes;
    private boolean primed;
    private double lastVelocityX;
    private double lastVelocityY;
    private double lastVelocityZ;
    private double lastBodyYaw;
    private double lastPitch;
    private boolean lastOnGround;

    public AvatarPhysicsController(BbModelDefinition model) {
        this.nodes = buildNodes(model);
    }

    public boolean active() { return !nodes.isEmpty(); }
    public int nodeCount() { return nodes.size(); }
    public List<String> controlledPaths() { return nodes.stream().map(node -> node.path).toList(); }

    /** Re-primes motion sensors after respawn or a world/player instance change. */
    public void reset() {
        primed = false;
        lastVelocityX = lastVelocityY = lastVelocityZ = 0.0;
        lastBodyYaw = lastPitch = 0.0;
        lastOnGround = false;
        for (Node node : nodes) {
            node.velocityX = 0.0;
            node.velocityY = 0.0;
            node.velocityZ = 0.0;
        }
    }

    /** Advances every preset spring by one client tick and returns whether pose state changed. */
    public boolean tick(Signals signals, AvatarState state) {
        if (nodes.isEmpty() || state == null || signals == null) return false;

        double yawRadians = Math.toRadians(signals.bodyYawDegrees);
        double sinYaw = Math.sin(yawRadians);
        double cosYaw = Math.cos(yawRadians);
        double forwardVelocity = -signals.velocityX * sinYaw + signals.velocityZ * cosYaw;
        double rightVelocity = signals.velocityX * cosYaw + signals.velocityZ * sinYaw;

        double previousForward = -lastVelocityX * sinYaw + lastVelocityZ * cosYaw;
        double previousRight = lastVelocityX * cosYaw + lastVelocityZ * sinYaw;
        double forwardAcceleration = primed ? forwardVelocity - previousForward : 0.0;
        double rightAcceleration = primed ? rightVelocity - previousRight : 0.0;
        double verticalAcceleration = primed ? signals.velocityY - lastVelocityY : 0.0;
        double yawDelta = primed ? wrapDegrees(signals.bodyYawDegrees - lastBodyYaw) : 0.0;
        double pitchDelta = primed ? signals.pitchDegrees - lastPitch : 0.0;
        double landing = primed && !lastOnGround && signals.onGround
            ? clamp(Math.max(0.0, verticalAcceleration) * 140.0, 0.0, 14.0) : 0.0;
        double horizontalSpeed = Math.hypot(signals.velocityX, signals.velocityZ);

        boolean changed = false;
        for (Node node : nodes) {
            Parameters parameters = node.parameters;
            double wind = Math.sin(signals.worldTime * parameters.windFrequency + node.phase)
                * parameters.windStrength * (0.35 + Math.min(1.0, horizontalSpeed * 4.0));
            if (signals.inFluid) wind *= 0.35;
            Motion motion = target(node.preset, forwardAcceleration, rightAcceleration,
                verticalAcceleration, yawDelta, pitchDelta, landing, wind);

            double depthGain = 1.0 + Math.min(5, node.depth) * 0.12;
            double targetX = motion.x * depthGain;
            double targetY = motion.y * depthGain;
            double targetZ = motion.z * depthGain;
            if (node.parentIndex >= 0) {
                Node parent = nodes.get(node.parentIndex);
                targetX += parent.angleX * parameters.inherit;
                targetY += parent.angleY * parameters.inherit;
                targetZ += parent.angleZ * parameters.inherit;
            }

            double stiffness = signals.inFluid ? parameters.stiffness * 0.62 : parameters.stiffness;
            double damping = signals.inFluid ? Math.min(0.94, parameters.damping + 0.08) : parameters.damping;
            node.velocityX = (node.velocityX + (targetX - node.angleX) * stiffness) * damping;
            node.velocityY = (node.velocityY + (targetY - node.angleY) * stiffness) * damping;
            node.velocityZ = (node.velocityZ + (targetZ - node.angleZ) * stiffness) * damping;
            node.angleX += node.velocityX;
            node.angleY += node.velocityY;
            node.angleZ += node.velocityZ;
            constrainCone(node, parameters.maxAngle);

            changed |= state.getPart(node.path).setAdditiveRotationLayer(
                ROTATION_LAYER, (float) node.angleX, (float) node.angleY, (float) node.angleZ
            );
        }

        lastVelocityX = signals.velocityX;
        lastVelocityY = signals.velocityY;
        lastVelocityZ = signals.velocityZ;
        lastBodyYaw = signals.bodyYawDegrees;
        lastPitch = signals.pitchDegrees;
        lastOnGround = signals.onGround;
        primed = true;
        if (changed) state.markPoseDirty();
        return changed;
    }

    public boolean clear(AvatarState state) {
        if (state == null) return false;
        boolean changed = false;
        for (Node node : nodes) changed |= state.getPart(node.path).clearAdditiveRotationLayer(ROTATION_LAYER);
        if (changed) state.markPoseDirty();
        return changed;
    }

    private static List<Node> buildNodes(BbModelDefinition model) {
        if (model == null || model.bones() == null || model.bones().isEmpty()) return List.of();
        Map<String, BbBoneDefinition> byUuid = new HashMap<>();
        for (BbBoneDefinition bone : model.bones()) byUuid.put(bone.uuid(), bone);

        List<Node> result = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
        for (BbBoneDefinition root : model.bones()) {
            if ("none".equals(root.physicsPreset())) continue;
            collect(model, root, root.physicsPreset(), -1, 0, byUuid, assigned, result);
        }
        return List.copyOf(result);
    }

    private static void collect(
        BbModelDefinition model,
        BbBoneDefinition bone,
        String preset,
        int parentIndex,
        int depth,
        Map<String, BbBoneDefinition> byUuid,
        Set<String> assigned,
        List<Node> result
    ) {
        if (bone == null || !assigned.add(bone.uuid())) return;
        int nodeIndex = result.size();
        result.add(new Node(model.bonePath(bone.uuid()), preset, parentIndex, depth,
            Parameters.forPreset(preset), stablePhase(bone.uuid())));
        for (String childUuid : bone.childBoneUuids()) {
            BbBoneDefinition child = byUuid.get(childUuid);
            if (child == null) continue;
            // A nested explicit preset starts an independent controller branch.
            if (!"none".equals(child.physicsPreset())) continue;
            collect(model, child, preset, nodeIndex, depth + 1, byUuid, assigned, result);
        }
    }

    private static Motion target(
        String preset,
        double forwardAcceleration,
        double rightAcceleration,
        double verticalAcceleration,
        double yawDelta,
        double pitchDelta,
        double landing,
        double wind
    ) {
        return switch (preset) {
            case "bunny_ears" -> new Motion(
                -forwardAcceleration * 150.0 + verticalAcceleration * 80.0 + pitchDelta * 0.32 + landing + wind,
                -yawDelta * 0.08,
                -rightAcceleration * 115.0 - yawDelta * 0.16 + wind * 0.12
            );
            case "tail" -> new Motion(
                forwardAcceleration * 90.0 - verticalAcceleration * 75.0 - landing * 0.45 + wind * 0.35,
                -rightAcceleration * 150.0 - yawDelta * 0.68 + wind,
                -rightAcceleration * 35.0
            );
            case "hair" -> new Motion(
                -forwardAcceleration * 125.0 + verticalAcceleration * 75.0 + pitchDelta * 0.20 + landing * 0.65 + wind,
                -yawDelta * 0.10,
                -rightAcceleration * 90.0 - yawDelta * 0.20 + wind * 0.18
            );
            case "cloth" -> new Motion(
                -forwardAcceleration * 155.0 + verticalAcceleration * 95.0 + landing * 0.80 + wind,
                -yawDelta * 0.14,
                -rightAcceleration * 130.0 - yawDelta * 0.30 + wind * 0.25
            );
            case "wings" -> new Motion(
                -forwardAcceleration * 60.0 + verticalAcceleration * 55.0 + landing * 0.35,
                -yawDelta * 0.25,
                -rightAcceleration * 90.0 - yawDelta * 0.36 + wind * 0.50
            );
            default -> Motion.ZERO;
        };
    }

    private static void constrainCone(Node node, double maxAngle) {
        double length = Math.sqrt(node.angleX * node.angleX + node.angleY * node.angleY + node.angleZ * node.angleZ);
        if (length <= maxAngle || length <= 0.000001) return;
        double scale = maxAngle / length;
        node.angleX *= scale;
        node.angleY *= scale;
        node.angleZ *= scale;
        // Dropping outward velocity avoids a spring buzzing against the cone.
        node.velocityX *= 0.55;
        node.velocityY *= 0.55;
        node.velocityZ *= 0.55;
    }

    private static double stablePhase(String value) {
        return (value == null ? 0 : value.hashCode() & 0xFFFF) / 65535.0 * Math.PI * 2.0;
    }

    private static double wrapDegrees(double value) {
        double wrapped = value % 360.0;
        if (wrapped > 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Signals(
        double velocityX,
        double velocityY,
        double velocityZ,
        double bodyYawDegrees,
        double pitchDegrees,
        boolean onGround,
        boolean inFluid,
        long worldTime
    ) {
        public Signals {
            velocityX = finite(velocityX);
            velocityY = finite(velocityY);
            velocityZ = finite(velocityZ);
            bodyYawDegrees = finite(bodyYawDegrees);
            pitchDegrees = finite(pitchDegrees);
        }

        private static double finite(double value) { return Double.isFinite(value) ? value : 0.0; }
    }

    private static final class Node {
        private final String path;
        private final String preset;
        private final int parentIndex;
        private final int depth;
        private final Parameters parameters;
        private final double phase;
        private double angleX;
        private double angleY;
        private double angleZ;
        private double velocityX;
        private double velocityY;
        private double velocityZ;

        private Node(String path, String preset, int parentIndex, int depth, Parameters parameters, double phase) {
            this.path = path;
            this.preset = preset;
            this.parentIndex = parentIndex;
            this.depth = depth;
            this.parameters = parameters;
            this.phase = phase;
        }
    }

    private record Parameters(double stiffness, double damping, double inherit, double maxAngle,
                              double windStrength, double windFrequency) {
        private static Parameters forPreset(String preset) {
            return switch (preset) {
                case "bunny_ears" -> new Parameters(0.23, 0.70, 0.42, 28.0, 0.90, 0.115);
                case "tail" -> new Parameters(0.12, 0.82, 0.62, 44.0, 1.60, 0.080);
                case "hair" -> new Parameters(0.18, 0.76, 0.52, 26.0, 1.10, 0.105);
                case "cloth" -> new Parameters(0.11, 0.84, 0.64, 38.0, 1.35, 0.075);
                case "wings" -> new Parameters(0.24, 0.70, 0.38, 30.0, 0.80, 0.090);
                default -> new Parameters(0.18, 0.76, 0.50, 25.0, 0.0, 0.1);
            };
        }
    }

    private record Motion(double x, double y, double z) {
        private static final Motion ZERO = new Motion(0.0, 0.0, 0.0);
    }
}

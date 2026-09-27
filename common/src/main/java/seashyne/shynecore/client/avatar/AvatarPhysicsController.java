package seashyne.shynecore.client.avatar;

import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native secondary-motion controller for {@code shyne_physics} Blockbench groups
 * and dynamic bone physics enabled via Lua API.
 *
 * <p>The controller writes to its own additive rotation layer. Authored animation,
 * direct Lua rotation and Lua additive rotation therefore remain independent.</p>
 */
public final class AvatarPhysicsController {
    public static final String ROTATION_LAYER = "shyne.physics";

    private final List<Node> staticNodes;
    private final Map<String, DynamicNode> dynamicNodes = new ConcurrentHashMap<>();
    private boolean primed;
    private double lastVelocityX;
    private double lastVelocityY;
    private double lastVelocityZ;
    private double lastBodyYaw;
    private double lastPitch;
    private boolean lastOnGround;

    public AvatarPhysicsController(BbModelDefinition model) {
        this.staticNodes = buildNodes(model);
    }

    public boolean active() { return !staticNodes.isEmpty() || !dynamicNodes.isEmpty(); }
    public int nodeCount() { return staticNodes.size() + dynamicNodes.size(); }

    public List<String> controlledPaths() {
        List<String> paths = new ArrayList<>(staticNodes.stream().map(node -> node.path).toList());
        paths.addAll(dynamicNodes.keySet());
        return paths;
    }

    /** Enables or updates dynamic physics for a specific bone. */
    public void setBonePhysics(String path, PhysicsConfig config) {
        if (path == null || path.isBlank()) return;
        PhysicsConfig effective = config != null ? config : PhysicsConfig.DEFAULT;
        DynamicNode existing = dynamicNodes.get(path);
        if (existing != null) {
            existing.config = effective;
        } else {
            dynamicNodes.put(path, new DynamicNode(path, effective, stablePhase(path)));
        }
    }

    /** Disables dynamic physics for a specific bone and clears its rotation layer. */
    public void removeBonePhysics(String path, AvatarState state) {
        if (path == null) return;
        DynamicNode removed = dynamicNodes.remove(path);
        if (removed != null && state != null) {
            state.getPart(path).clearAdditiveRotationLayer(ROTATION_LAYER);
            state.markPoseDirty();
        }
    }

    public PhysicsConfig getBonePhysics(String path) {
        if (path == null) return null;
        DynamicNode node = dynamicNodes.get(path);
        return node != null ? node.config : null;
    }

    public void clearDynamicNodes(AvatarState state) {
        if (state != null) {
            for (String path : dynamicNodes.keySet()) {
                state.getPart(path).clearAdditiveRotationLayer(ROTATION_LAYER);
            }
            state.markPoseDirty();
        }
        dynamicNodes.clear();
    }

    /** Re-primes motion sensors after respawn or a world/player instance change. */
    public void reset() {
        primed = false;
        lastVelocityX = lastVelocityY = lastVelocityZ = 0.0;
        lastBodyYaw = lastPitch = 0.0;
        lastOnGround = false;
        for (Node node : staticNodes) {
            node.velocityX = 0.0;
            node.velocityY = 0.0;
            node.velocityZ = 0.0;
        }
        for (DynamicNode node : dynamicNodes.values()) {
            node.velocityX = 0.0;
            node.velocityY = 0.0;
            node.velocityZ = 0.0;
        }
    }

    /** Advances every preset spring by one client tick and returns whether pose state changed. */
    public boolean tick(Signals signals, AvatarState state) {
        if ((staticNodes.isEmpty() && dynamicNodes.isEmpty()) || state == null || signals == null) return false;

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

        // 1. Static preset nodes from Blockbench
        for (Node node : staticNodes) {
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
                Node parent = staticNodes.get(node.parentIndex);
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

            constrainCone(node.angleX, node.angleY, node.angleZ, node.velocityX, node.velocityY, node.velocityZ,
                parameters.maxAngle, (ax, ay, az, vx, vy, vz) -> {
                    node.angleX = ax; node.angleY = ay; node.angleZ = az;
                    node.velocityX = vx; node.velocityY = vy; node.velocityZ = vz;
                });

            changed |= state.getPart(node.path).setAdditiveRotationLayer(
                ROTATION_LAYER, (float) node.angleX, (float) node.angleY, (float) node.angleZ
            );
        }

        // 2. Dynamic nodes enabled via Lua API (setPhysics)
        for (DynamicNode node : dynamicNodes.values()) {
            PhysicsConfig config = node.config;
            double wind = Math.sin(signals.worldTime * 0.09 + node.phase)
                * config.wind * (0.35 + Math.min(1.0, horizontalSpeed * 4.0));
            if (signals.inFluid) wind *= 0.35;
            Motion motion = target(config.preset, forwardAcceleration, rightAcceleration,
                verticalAcceleration, yawDelta, pitchDelta, landing, wind);

            double targetX = motion.x + config.gravity * 100.0;
            double targetY = motion.y;
            double targetZ = motion.z;

            // Inherit from parent bone if parent also has physics
            String parentPath = getParentPath(node.path);
            if (parentPath != null) {
                DynamicNode parent = dynamicNodes.get(parentPath);
                if (parent != null) {
                    targetX += parent.angleX * config.inherit;
                    targetY += parent.angleY * config.inherit;
                    targetZ += parent.angleZ * config.inherit;
                }
            }

            double stiffness = signals.inFluid ? config.spring * 0.62 : config.spring;
            double damping = signals.inFluid ? Math.min(0.94, config.damping + 0.08) : config.damping;
            node.velocityX = (node.velocityX + (targetX - node.angleX) * stiffness) * damping;
            node.velocityY = (node.velocityY + (targetY - node.angleY) * stiffness) * damping;
            node.velocityZ = (node.velocityZ + (targetZ - node.angleZ) * stiffness) * damping;
            node.angleX += node.velocityX;
            node.angleY += node.velocityY;
            node.angleZ += node.velocityZ;

            constrainCone(node.angleX, node.angleY, node.angleZ, node.velocityX, node.velocityY, node.velocityZ,
                config.maxAngle, (ax, ay, az, vx, vy, vz) -> {
                    node.angleX = ax; node.angleY = ay; node.angleZ = az;
                    node.velocityX = vx; node.velocityY = vy; node.velocityZ = vz;
                });

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
        for (Node node : staticNodes) changed |= state.getPart(node.path).clearAdditiveRotationLayer(ROTATION_LAYER);
        for (DynamicNode node : dynamicNodes.values()) changed |= state.getPart(node.path).clearAdditiveRotationLayer(ROTATION_LAYER);
        if (changed) state.markPoseDirty();
        return changed;
    }

    private static String getParentPath(String path) {
        if (path == null) return null;
        int dot = path.lastIndexOf('.');
        return dot > 0 ? path.substring(0, dot) : null;
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
        return switch (preset != null ? preset : "") {
            case "bunny_ears", "ears" -> new Motion(
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
            case "cloth", "cape", "skirt" -> new Motion(
                -forwardAcceleration * 155.0 + verticalAcceleration * 95.0 + landing * 0.80 + wind,
                -yawDelta * 0.14,
                -rightAcceleration * 130.0 - yawDelta * 0.30 + wind * 0.25
            );
            case "wings" -> new Motion(
                -forwardAcceleration * 60.0 + verticalAcceleration * 55.0 + landing * 0.35,
                -yawDelta * 0.25,
                -rightAcceleration * 90.0 - yawDelta * 0.36 + wind * 0.50
            );
            default -> new Motion(
                forwardAcceleration * 90.0 - verticalAcceleration * 75.0 - landing * 0.45 + wind * 0.35,
                -rightAcceleration * 130.0 - yawDelta * 0.45 + wind * 0.5,
                -rightAcceleration * 35.0
            );
        };
    }

    @FunctionalInterface
    private interface ConeOutput {
        void accept(double ax, double ay, double az, double vx, double vy, double vz);
    }

    private static void constrainCone(double ax, double ay, double az, double vx, double vy, double vz, double maxAngle, ConeOutput out) {
        double length = Math.sqrt(ax * ax + ay * ay + az * az);
        if (length <= maxAngle || length <= 0.000001) {
            out.accept(ax, ay, az, vx, vy, vz);
            return;
        }
        double scale = maxAngle / length;
        out.accept(ax * scale, ay * scale, az * scale, vx * 0.55, vy * 0.55, vz * 0.55);
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

    public record PhysicsConfig(
        double spring,
        double damping,
        double gravity,
        double maxAngle,
        double wind,
        double inherit,
        String preset
    ) {
        public static final PhysicsConfig DEFAULT = new PhysicsConfig(0.18, 0.76, 0.0, 35.0, 0.5, 0.5, "custom");
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

    private static final class DynamicNode {
        private final String path;
        private PhysicsConfig config;
        private final double phase;
        private double angleX;
        private double angleY;
        private double angleZ;
        private double velocityX;
        private double velocityY;
        private double velocityZ;

        private DynamicNode(String path, PhysicsConfig config, double phase) {
            this.path = path;
            this.config = config;
            this.phase = phase;
        }
    }

    private record Parameters(double stiffness, double damping, double inherit, double maxAngle,
                              double windStrength, double windFrequency) {
        private static Parameters forPreset(String preset) {
            return switch (preset) {
                case "bunny_ears", "ears" -> new Parameters(0.23, 0.70, 0.42, 28.0, 0.90, 0.115);
                case "tail" -> new Parameters(0.12, 0.82, 0.62, 44.0, 1.60, 0.080);
                case "hair" -> new Parameters(0.18, 0.76, 0.52, 26.0, 1.10, 0.105);
                case "cloth", "cape", "skirt" -> new Parameters(0.11, 0.84, 0.64, 38.0, 1.35, 0.075);
                case "wings" -> new Parameters(0.24, 0.70, 0.38, 30.0, 0.80, 0.090);
                default -> new Parameters(0.18, 0.76, 0.50, 25.0, 0.0, 0.1);
            };
        }
    }

    private record Motion(double x, double y, double z) {
        private static final Motion ZERO = new Motion(0.0, 0.0, 0.0);
    }
}

package seashyne.shynecore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.profiler.AvatarProfiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stores and draws render tasks declared by active Avatar Lua runtimes. */
public final class AvatarRenderTaskRegistry {
    public static final int MAX_TASKS_PER_AVATAR = 256;
    public static final int MAX_RENDERED_TASKS_PER_FRAME = 128;
    public static final int MAX_LINE_POINTS_PER_FRAME = 4096;
    public static final int MAX_TEXT_GLYPHS_PER_FRAME = 4096;
    public static final double MAX_WORLD_LINE_LENGTH = 1024.0;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Map<String, Entry> TASKS = new LinkedHashMap<>();
    private static final Set<String> WARNED_TASKS = ConcurrentHashMap.newKeySet();
    private static volatile int lastRendered;
    private static volatile int lastCulled;
    private static volatile int lastHudRendered;
    private static volatile int lastHudCulled;
    private static volatile int lastWorldRendered;
    private static volatile int lastWorldCulled;
    private static volatile int lastScreenWidth;
    private static volatile int lastScreenHeight;

    private AvatarRenderTaskRegistry() {}

    public static synchronized boolean upsert(Object owner, String avatarId, String id, TaskSpec spec) {
        if (owner == null || spec == null) return false;
        String safeAvatar = safe(avatarId);
        String safeId = safe(id);
        if (safeAvatar.isBlank() || safeId.isBlank()) return false;
        String stableId = safeAvatar + "." + safeId;
        Entry entry = TASKS.computeIfAbsent(stableId, ignored -> new Entry(stableId, safeAvatar, safeId));
        entry.specs.put(owner, sanitize(spec));
        return true;
    }

    public static synchronized boolean remove(Object owner, String avatarId, String id) {
        String stableId = safe(avatarId) + "." + safe(id);
        Entry entry = TASKS.get(stableId);
        if (entry == null) return false;
        entry.specs.remove(owner);
        if (entry.specs.isEmpty()) {
            TASKS.remove(stableId);
            WARNED_TASKS.remove(stableId);
        }
        return true;
    }

    public static synchronized void clearOwner(Object owner) {
        TASKS.values().removeIf(entry -> {
            entry.specs.remove(owner);
            boolean empty = entry.specs.isEmpty();
            if (empty) WARNED_TASKS.remove(entry.stableId);
            return empty;
        });
    }

    public static synchronized List<Snapshot> snapshots() {
        List<Snapshot> result = new ArrayList<>();
        for (Entry entry : TASKS.values()) {
            TaskSpec spec = entry.current();
            if (spec != null) result.add(new Snapshot(entry.stableId, entry.avatarId, entry.id, spec));
        }
        // Java's stable sort keeps creation order for tasks on the same layer.
        result.sort(Comparator.comparingInt(value -> value.spec.zIndex));
        return List.copyOf(result);
    }

    public static synchronized long estimatedBytes() {
        long result = 0;
        for (Snapshot task : snapshots()) {
            result += 192L + task.stableId.length() * 2L;
            result += (task.spec.content == null ? 0 : task.spec.content.length() * 2L);
            result += (task.spec.resource == null ? 0 : task.spec.resource.length() * 2L);
        }
        return result;
    }

    public static int lastRendered() { return lastRendered; }
    public static int lastCulled() { return lastCulled; }
    public static int lastHudRendered() { return lastHudRendered; }
    public static int lastWorldRendered() { return lastWorldRendered; }
    public static int lastScreenWidth() { return lastScreenWidth; }
    public static int lastScreenHeight() { return lastScreenHeight; }

    public static void extractHud(GuiGraphicsExtractor graphics) {
        long started = System.nanoTime();
        try {
            lastScreenWidth = graphics.guiWidth();
            lastScreenHeight = graphics.guiHeight();
            Minecraft client = Minecraft.getInstance();
            int rendered = 0;
            int culled = 0;
            int linePointsRemaining = MAX_LINE_POINTS_PER_FRAME;
            int glyphsRemaining = MAX_TEXT_GLYPHS_PER_FRAME;
            for (Snapshot snapshot : snapshots()) {
                try {
                TaskSpec task = snapshot.spec;
                if (task.world) continue;
                if (!task.visible) continue;
                if (rendered >= MAX_RENDERED_TASKS_PER_FRAME) {
                    culled++;
                    continue;
                }
                if ("text".equals(task.type) && task.content.length() > glyphsRemaining) {
                    culled++;
                    continue;
                }
                ScreenPoint first = new ScreenPoint(task.x, task.y, true);
                if (!first.visible) {
                    culled++;
                    continue;
                }
                int x = (int) Math.round(first.x);
                int y = (int) Math.round(first.y);
                int color = RenderTaskMath.applyOpacity(task.color, task.opacity);
                switch (task.type) {
                    case "text" -> {
                        graphics.text(client.font, Component.literal(task.content), x, y, color, task.shadow);
                        glyphsRemaining -= task.content.length();
                    }
                    case "item" -> item(graphics, task.resource, x, y, false);
                    case "block" -> item(graphics, task.resource, x, y, true);
                    case "sprite" -> sprite(graphics, task, x, y);
                    case "rect" -> rect(graphics, x, y, task.width, task.height, color);
                    case "outline" -> outline(graphics, x, y, task.width, task.height, task.scale, color);
                    case "line" -> {
                        ScreenPoint second = new ScreenPoint(task.x2, task.y2, true);
                        if (second.visible && linePointsRemaining > 0) {
                            int used = line(graphics, x, y, (int) Math.round(second.x), (int) Math.round(second.y), color, task.width, linePointsRemaining);
                            linePointsRemaining -= used;
                        } else {
                            culled++;
                            continue;
                        }
                    }
                }
                rendered++;
                } catch (RuntimeException error) {
                    culled++;
                    warnInvalidTask(snapshot, "HUD", error);
                }
            }
            lastHudRendered = rendered;
            lastHudCulled = culled;
            updateFrameStats();
        } finally {
            AvatarProfiler.record(AvatarProfiler.Category.TASK_RENDER, System.nanoTime() - started);
        }
    }

    /** Submits true depth-tested world geometry into Minecraft's feature renderer. */
    public static void submitWorld(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (poseStack == null || collector == null || camera == null || camera.pos == null) return;
        long started = System.nanoTime();
        int rendered = 0;
        int culled = 0;
        try {
            Minecraft client = Minecraft.getInstance();
            int glyphsRemaining = MAX_TEXT_GLYPHS_PER_FRAME;
            for (Snapshot snapshot : snapshots()) {
                try {
                TaskSpec task = snapshot.spec;
                if (!task.world || !task.visible) continue;
                if (rendered >= MAX_RENDERED_TASKS_PER_FRAME) {
                    culled++;
                    continue;
                }
                if ("text".equals(task.type) && task.content.length() > glyphsRemaining) {
                    culled++;
                    continue;
                }
                ResolvedWorldTask resolved = resolveWorldTask(task);
                if (resolved == null || !withinWorldDistance(task, resolved, camera.pos)
                    || !withinFrustum(task, resolved, camera)) {
                    culled++;
                    continue;
                }

                boolean submitted = false;
                poseStack.pushPose();
                try {
                    applyWorldPose(poseStack, task, resolved, camera);
                    int color = RenderTaskMath.applyOpacity(task.color, task.opacity);
                    int light = worldLight(client, task, resolved.origin);
                    // snapshots() already provides stable z-index ordering. Keep
                    // every Shyne task in the same Minecraft order bucket so item
                    // and custom geometry follow identical local ordering rules.
                    OrderedSubmitNodeCollector ordered = collector;
                    submitted = switch (task.type) {
                        case "text" -> worldText(client, poseStack, ordered, camera, task, color, light);
                        case "item" -> worldItem(client, poseStack, collector, camera, task, false, light);
                        case "block" -> worldItem(client, poseStack, collector, camera, task, true, light);
                        case "sprite" -> worldSprite(poseStack, ordered, camera, task, color, light);
                        case "rect" -> worldRect(poseStack, ordered, camera, task, color);
                        case "outline" -> worldOutline(poseStack, ordered, camera, task, color);
                        case "line" -> worldLine(poseStack, ordered, task, resolved, color);
                        default -> false;
                    };
                } catch (RuntimeException error) {
                    if (WARNED_TASKS.add(snapshot.stableId)) {
                        ShyneCore.LOGGER.warn("[AvatarRenderTask] Skipped invalid world task {}: {}", snapshot.stableId, error.getMessage());
                    }
                } finally {
                    poseStack.popPose();
                }
                if (submitted) {
                    rendered++;
                    if ("text".equals(task.type)) glyphsRemaining -= task.content.length();
                } else culled++;
                } catch (RuntimeException error) {
                    culled++;
                    warnInvalidTask(snapshot, "world", error);
                }
            }
        } finally {
            lastWorldRendered = rendered;
            lastWorldCulled = culled;
            updateFrameStats();
            AvatarProfiler.record(AvatarProfiler.Category.TASK_RENDER, System.nanoTime() - started);
        }
    }

    private static boolean worldText(Minecraft client, PoseStack poseStack, OrderedSubmitNodeCollector collector,
                                     CameraRenderState camera, TaskSpec task, int color, int light) {
        Component text = Component.literal(task.content);
        applyBillboard(poseStack, camera, task);
        float scale = (float) (0.025 * task.scale);
        poseStack.scale(scale, -scale, scale);
        float x = -client.font.width(text) / 2f;
        collector.submitText(poseStack, x, 0, text.getVisualOrderText(), task.shadow,
            Font.DisplayMode.NORMAL, light, color, 0, 0);
        return true;
    }

    private static boolean worldItem(Minecraft client, PoseStack poseStack, SubmitNodeCollector collector,
                                     CameraRenderState camera, TaskSpec task, boolean block, int light) {
        ItemStack stack = itemStack(task.resource, block);
        if (stack.isEmpty()) return false;
        ItemStackRenderState renderState = new ItemStackRenderState();
        if (client.player != null) {
            client.getItemModelResolver().updateForLiving(renderState, stack, ItemDisplayContext.FIXED, client.player);
        } else {
            client.getItemModelResolver().updateForNonLiving(renderState, stack, ItemDisplayContext.FIXED, null);
        }
        if (renderState.isEmpty()) return false;
        applyBillboard(poseStack, camera, task);
        float scale = (float) task.scale;
        poseStack.scale(scale, scale, scale);
        renderState.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, task.resource.hashCode());
        return true;
    }

    private static boolean worldSprite(PoseStack poseStack, OrderedSubmitNodeCollector collector,
                                       CameraRenderState camera, TaskSpec task, int color, int light) {
        Identifier texture = Identifier.tryParse(task.resource);
        if (texture == null) return false;
        applyBillboard(poseStack, camera, task);
        float halfWidth = (float) (task.width * task.scale / 32.0);
        float halfHeight = (float) (task.height * task.scale / 32.0);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture), (pose, vertices) -> {
            texturedVertex(vertices, pose, -halfWidth, -halfHeight, 0, 0, 1, color, light);
            texturedVertex(vertices, pose, halfWidth, -halfHeight, 0, 1, 1, color, light);
            texturedVertex(vertices, pose, halfWidth, halfHeight, 0, 1, 0, color, light);
            texturedVertex(vertices, pose, -halfWidth, halfHeight, 0, 0, 0, color, light);
        });
        return true;
    }

    private static boolean worldRect(PoseStack poseStack, OrderedSubmitNodeCollector collector,
                                     CameraRenderState camera, TaskSpec task, int color) {
        applyBillboard(poseStack, camera, task);
        float halfWidth = (float) (task.width * task.scale / 32.0);
        float halfHeight = (float) (task.height * task.scale / 32.0);
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vertices) -> {
            colorVertex(vertices, pose, -halfWidth, -halfHeight, 0, color);
            colorVertex(vertices, pose, halfWidth, -halfHeight, 0, color);
            colorVertex(vertices, pose, halfWidth, halfHeight, 0, color);
            colorVertex(vertices, pose, -halfWidth, halfHeight, 0, color);
        });
        return true;
    }

    private static boolean worldOutline(PoseStack poseStack, OrderedSubmitNodeCollector collector,
                                        CameraRenderState camera, TaskSpec task, int color) {
        applyBillboard(poseStack, camera, task);
        float halfWidth = (float) (task.width / 32.0);
        float halfHeight = (float) (task.height / 32.0);
        float lineWidth = (float) task.scale;
        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertices) -> {
            lineSegment(vertices, pose, -halfWidth, -halfHeight, 0, halfWidth, -halfHeight, 0, color, lineWidth);
            lineSegment(vertices, pose, halfWidth, -halfHeight, 0, halfWidth, halfHeight, 0, color, lineWidth);
            lineSegment(vertices, pose, halfWidth, halfHeight, 0, -halfWidth, halfHeight, 0, color, lineWidth);
            lineSegment(vertices, pose, -halfWidth, halfHeight, 0, -halfWidth, -halfHeight, 0, color, lineWidth);
        });
        return true;
    }

    private static boolean worldLine(PoseStack poseStack, OrderedSubmitNodeCollector collector, TaskSpec task,
                                     ResolvedWorldTask resolved, int color) {
        float x = (float) (resolved.destination.x - resolved.origin.x);
        float y = (float) (resolved.destination.y - resolved.origin.y);
        float z = (float) (resolved.destination.z - resolved.origin.z);
        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertices) ->
            lineSegment(vertices, pose, 0, 0, 0, x, y, z, color, (float) task.width));
        return true;
    }

    private static void texturedVertex(com.mojang.blaze3d.vertex.VertexConsumer vertices, PoseStack.Pose pose,
                                       float x, float y, float z, float u, float v, int color, int light) {
        vertices.addVertex(pose, x, y, z).setColor(color).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
    }

    private static void colorVertex(com.mojang.blaze3d.vertex.VertexConsumer vertices, PoseStack.Pose pose,
                                    float x, float y, float z, int color) {
        vertices.addVertex(pose, x, y, z).setColor(color);
    }

    private static void lineSegment(com.mojang.blaze3d.vertex.VertexConsumer vertices, PoseStack.Pose pose,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    int color, float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = length > 0.0001f ? dx / length : 0f;
        float ny = length > 0.0001f ? dy / length : 1f;
        float nz = length > 0.0001f ? dz / length : 0f;
        vertices.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
        vertices.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    private static void updateFrameStats() {
        lastRendered = lastHudRendered + lastWorldRendered;
        lastCulled = lastHudCulled + lastWorldCulled;
    }

    private static void item(GuiGraphicsExtractor graphics, String id, int x, int y, boolean block) {
        ItemStack stack = itemStack(id, block);
        if (!stack.isEmpty()) graphics.item(stack, x, y);
    }

    private static ItemStack itemStack(String id, boolean block) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) return ItemStack.EMPTY;
        var item = block
            ? BuiltInRegistries.BLOCK.get(key).map(holder -> holder.value().asItem()).orElse(null)
            : BuiltInRegistries.ITEM.get(key).map(holder -> holder.value()).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static void sprite(GuiGraphicsExtractor graphics, TaskSpec task, int x, int y) {
        Identifier texture = Identifier.tryParse(task.resource);
        if (texture == null) return;
        int width = Math.max(1, (int) Math.round(task.width));
        int height = Math.max(1, (int) Math.round(task.height));
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width, height, width, height);
    }

    private static void rect(GuiGraphicsExtractor graphics, int x, int y, double width, double height, int color) {
        int right = x + Math.max(1, (int) Math.round(width));
        int bottom = y + Math.max(1, (int) Math.round(height));
        graphics.fill(x, y, right, bottom, color);
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, double width, double height, double thickness, int color) {
        int right = x + Math.max(1, (int) Math.round(width));
        int bottom = y + Math.max(1, (int) Math.round(height));
        int edge = Math.max(1, Math.min(16, (int) Math.round(thickness)));
        graphics.fill(x, y, right, Math.min(bottom, y + edge), color);
        graphics.fill(x, Math.max(y, bottom - edge), right, bottom, color);
        graphics.fill(x, y, Math.min(right, x + edge), bottom, color);
        graphics.fill(Math.max(x, right - edge), y, right, bottom, color);
    }

    private static int line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, double width, int pointBudget) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int steps = Math.min(1024, Math.max(dx, dy));
        int radius = Math.max(0, Math.min(4, (int) Math.round(width) / 2));
        if (steps == 0) {
            graphics.fill(x0 - radius, y0 - radius, x0 + radius + 1, y0 + radius + 1, color);
            return 1;
        }
        if (pointBudget <= 1) {
            graphics.fill(x0 - radius, y0 - radius, x0 + radius + 1, y0 + radius + 1, color);
            return 1;
        }
        int points = Math.min(steps, pointBudget - 1);
        for (int i = 0; i <= points; i++) {
            int x = x0 + (x1 - x0) * i / points;
            int y = y0 + (y1 - y0) * i / points;
            graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
        }
        return points + 1;
    }

    private static ResolvedWorldTask resolveWorldTask(TaskSpec task) {
        Matrix4f attachmentMatrix = null;
        Vec3 origin = new Vec3(task.x, task.y, task.z);
        Vec3 destination = new Vec3(task.x2, task.y2, task.z2);
        if (task.attachmentEntityId != null && !task.attachmentModelId.isBlank() && !task.attachmentPath.isBlank()) {
            var captured = AvatarBoneTransformRegistry.findWorld(
                task.attachmentEntityId, task.attachmentModelId, task.attachmentPath
            );
            if (captured == null || !captured.visible()) return null;
            attachmentMatrix = new Matrix4f().set(captured.matrix());
            Vector3f local = attachmentMatrix.transformPosition(new Vector3f(
                (float) task.localOffsetX, (float) task.localOffsetY, (float) task.localOffsetZ
            ));
            origin = new Vec3(local.x + task.x, local.y + task.y, local.z + task.z);
            if (task.localDestination) {
                Vector3f localTo = attachmentMatrix.transformPosition(new Vector3f(
                    (float) task.localToX, (float) task.localToY, (float) task.localToZ
                ));
                destination = new Vec3(localTo.x + task.x, localTo.y + task.y, localTo.z + task.z);
            }
        }
        if ("line".equals(task.type)) destination = capWorldLine(origin, destination);
        return new ResolvedWorldTask(origin, destination, attachmentMatrix);
    }

    static Vec3 capWorldLine(Vec3 origin, Vec3 destination) {
        double[] capped = RenderTaskMath.capLine(
            origin.x, origin.y, origin.z, destination.x, destination.y, destination.z, MAX_WORLD_LINE_LENGTH
        );
        return new Vec3(capped[0], capped[1], capped[2]);
    }

    private static void applyWorldPose(PoseStack poseStack, TaskSpec task, ResolvedWorldTask resolved,
                                       CameraRenderState camera) {
        if (resolved.attachmentMatrix != null && !task.billboard && !"line".equals(task.type)) {
            Matrix4f matrix = new Matrix4f(resolved.attachmentMatrix)
                .translate((float) task.localOffsetX, (float) task.localOffsetY, (float) task.localOffsetZ);
            matrix.m30(matrix.m30() + (float) (task.x - camera.pos.x));
            matrix.m31(matrix.m31() + (float) (task.y - camera.pos.y));
            matrix.m32(matrix.m32() + (float) (task.z - camera.pos.z));
            poseStack.mulPose(matrix);
            // Bone matrices use Blockbench pixels and an inverted model Y axis.
            // Cancel only that root conversion while retaining bone scale/rotation.
            poseStack.scale(16f, -16f, 16f);
            return;
        }
        poseStack.translate(resolved.origin.x - camera.pos.x, resolved.origin.y - camera.pos.y, resolved.origin.z - camera.pos.z);
    }

    private static void applyBillboard(PoseStack poseStack, CameraRenderState camera, TaskSpec task) {
        if (task.billboard && camera.orientation != null) poseStack.rotate(camera.orientation);
    }

    private static int worldLight(Minecraft client, TaskSpec task, Vec3 origin) {
        if (task.fullbright || client.level == null) return FULL_BRIGHT;
        return LightCoordsUtil.getLightCoords(client.level, BlockPos.containing(origin));
    }

    private static boolean withinWorldDistance(TaskSpec task, ResolvedWorldTask resolved, Vec3 camera) {
        double distanceSquared = "line".equals(task.type)
            ? pointSegmentDistanceSquared(camera, resolved.origin, resolved.destination)
            : camera.distanceToSqr(resolved.origin);
        return Double.isFinite(distanceSquared) && distanceSquared <= task.maxDistance * task.maxDistance;
    }

    private static double pointSegmentDistanceSquared(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared <= 0.0000001) return point.distanceToSqr(start);
        double t = Math.max(0, Math.min(1, point.subtract(start).dot(segment) / lengthSquared));
        return point.distanceToSqr(start.add(segment.scale(t)));
    }

    private static boolean withinFrustum(TaskSpec task, ResolvedWorldTask resolved, CameraRenderState camera) {
        if (camera.cullFrustum == null) return true;
        Vec3 end = "line".equals(task.type) ? resolved.destination : resolved.origin;
        double radius = taskRadius(task);
        if (resolved.attachmentMatrix != null && !task.billboard && !"line".equals(task.type)) {
            Matrix4f matrix = resolved.attachmentMatrix;
            double xScale = Math.sqrt(matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01() + matrix.m02() * matrix.m02());
            double yScale = Math.sqrt(matrix.m10() * matrix.m10() + matrix.m11() * matrix.m11() + matrix.m12() * matrix.m12());
            double zScale = Math.sqrt(matrix.m20() * matrix.m20() + matrix.m21() * matrix.m21() + matrix.m22() * matrix.m22());
            // applyWorldPose cancels the Blockbench 1/16 root conversion.
            radius *= Math.max(xScale, Math.max(yScale, zScale)) * 16.0;
        }
        radius = Math.max(0.05, Math.min(4096.0, radius));
        AABB bounds = new AABB(
            Math.min(resolved.origin.x, end.x) - radius,
            Math.min(resolved.origin.y, end.y) - radius,
            Math.min(resolved.origin.z, end.z) - radius,
            Math.max(resolved.origin.x, end.x) + radius,
            Math.max(resolved.origin.y, end.y) + radius,
            Math.max(resolved.origin.z, end.z) + radius
        );
        return camera.cullFrustum.isVisible(bounds);
    }

    private static double taskRadius(TaskSpec task) {
        return switch (task.type) {
            case "line" -> Math.max(0.02, task.width / 16.0);
            case "text" -> {
                Minecraft client = Minecraft.getInstance();
                double width = client.font.width(Component.literal(task.content)) * 0.025 * task.scale;
                double height = client.font.lineHeight * 0.025 * task.scale;
                yield Math.hypot(width * 0.5, height * 0.5);
            }
            case "item", "block" -> Math.max(0.5, task.scale);
            case "outline" -> Math.hypot(task.width, task.height) / 32.0;
            default -> Math.hypot(task.width, task.height) * task.scale / 32.0;
        };
    }

    private static void warnInvalidTask(Snapshot snapshot, String pass, RuntimeException error) {
        if (WARNED_TASKS.add(snapshot.stableId)) {
            ShyneCore.LOGGER.warn("[AvatarRenderTask] Skipped invalid {} task {}: {}", pass, snapshot.stableId, error.getMessage());
        }
    }

    private static TaskSpec sanitize(TaskSpec value) {
        return new TaskSpec(normalType(value.type), value.world,
            truncate(value.content, 1024), truncate(value.resource, 256),
            coordinate(value.x, value.world), coordinate(value.y, value.world), coordinate(value.z, value.world),
            coordinate(value.x2, value.world), coordinate(value.y2, value.world), coordinate(value.z2, value.world),
            clamp(value.width, 0.1, 512), clamp(value.height, 0.1, 512), clamp(value.scale, 0.05, 16),
            value.color, value.shadow, value.visible, clamp(value.maxDistance, 8, 1024),
            Math.max(-1024, Math.min(1024, value.zIndex)), clamp(value.opacity, 0, 1),
            value.attachmentEntityId, truncate(value.attachmentModelId, 256), truncate(value.attachmentPath, 512),
            clamp(value.localOffsetX, -4096, 4096), clamp(value.localOffsetY, -4096, 4096), clamp(value.localOffsetZ, -4096, 4096),
            clamp(value.localToX, -4096, 4096), clamp(value.localToY, -4096, 4096), clamp(value.localToZ, -4096, 4096),
            value.localDestination, value.billboard, value.fullbright);
    }

    private static String normalType(String value) {
        String type = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return switch (type) {
            case "text", "item", "block", "sprite", "line", "rect", "outline" -> type;
            default -> "text";
        };
    }

    private static String safe(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"); }
    private static String truncate(String value, int max) { return value == null ? "" : value.substring(0, Math.min(max, value.length())); }
    private static double finite(double value) { return Double.isFinite(value) ? value : 0; }
    private static double coordinate(double value, boolean world) {
        return world ? clamp(value, -30_000_000.0, 30_000_000.0) : clamp(value, -1_000_000.0, 1_000_000.0);
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, finite(value))); }

    public record TaskSpec(String type, boolean world, String content, String resource,
                           double x, double y, double z, double x2, double y2, double z2,
                           double width, double height, double scale, int color,
                           boolean shadow, boolean visible, double maxDistance,
                           int zIndex, double opacity,
                           UUID attachmentEntityId, String attachmentModelId, String attachmentPath,
                           double localOffsetX, double localOffsetY, double localOffsetZ,
                           double localToX, double localToY, double localToZ,
                           boolean localDestination, boolean billboard, boolean fullbright) {
        /** Compatibility constructor for Java callers that do not bind a bone. */
        public TaskSpec(String type, boolean world, String content, String resource,
                        double x, double y, double z, double x2, double y2, double z2,
                        double width, double height, double scale, int color,
                        boolean shadow, boolean visible, double maxDistance,
                        int zIndex, double opacity) {
            this(type, world, content, resource, x, y, z, x2, y2, z2,
                width, height, scale, color, shadow, visible, maxDistance, zIndex, opacity,
                null, "", "", 0, 0, 0, 0, 0, 0, false, true, false);
        }
    }
    public record Snapshot(String stableId, String avatarId, String id, TaskSpec spec) {}
    private record ScreenPoint(double x, double y, boolean visible) {}
    private record ResolvedWorldTask(Vec3 origin, Vec3 destination, Matrix4f attachmentMatrix) {}

    private static final class Entry {
        private final String stableId;
        private final String avatarId;
        private final String id;
        private final RenderTaskOwners<TaskSpec> specs = new RenderTaskOwners<>();
        private Entry(String stableId, String avatarId, String id) { this.stableId = stableId; this.avatarId = avatarId; this.id = id; }
        private TaskSpec current() {
            return specs.current();
        }
    }
}

/** Pure render math stays loadable in unit tests without a running Minecraft client. */
final class RenderTaskMath {
    private RenderTaskMath() {}

    static int applyOpacity(int color, double opacity) {
        double safeOpacity = Double.isFinite(opacity) ? Math.max(0, Math.min(1, opacity)) : 0;
        int alpha = (color >>> 24) & 0xFF;
        int adjusted = (int) Math.round(alpha * safeOpacity);
        return (color & 0x00FFFFFF) | (adjusted << 24);
    }

    static double[] capLine(double x1, double y1, double z1, double x2, double y2, double z2, double limit) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(lengthSquared)) return new double[] {x1, y1, z1};
        double safeLimit = Double.isFinite(limit) ? Math.max(0, limit) : 0;
        if (lengthSquared <= safeLimit * safeLimit || lengthSquared <= 0.0000001) return new double[] {x2, y2, z2};
        double scale = safeLimit / Math.sqrt(lengthSquared);
        return new double[] {x1 + dx * scale, y1 + dy * scale, z1 + dz * scale};
    }
}

/** Pure-Java ownership stack used to restore old tasks after a staged runtime rolls back. */
final class RenderTaskOwners<T> {
    private final List<Owned<T>> values = new ArrayList<>();
    void put(Object owner, T value) {
        remove(owner);
        values.add(new Owned<>(owner, value));
    }
    boolean remove(Object owner) { return values.removeIf(value -> value.owner == owner); }
    boolean isEmpty() { return values.isEmpty(); }
    T current() { return values.isEmpty() ? null : values.get(values.size() - 1).value; }
    private record Owned<T>(Object owner, T value) {}
}

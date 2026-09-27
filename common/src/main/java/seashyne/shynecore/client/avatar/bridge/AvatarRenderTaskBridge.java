package seashyne.shynecore.client.avatar.bridge;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.input.DynamicAvatarInputRegistry;
import seashyne.shynecore.client.profiler.AvatarProfiler;
import seashyne.shynecore.client.render.AvatarRenderTaskRegistry;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Handles custom 2D/3D render tasks, screens, stats, and avatar profiling.
 */
public final class AvatarRenderTaskBridge {
    private final AvatarState state;
    private final Object renderTaskOwner = new Object();
    private final Set<String> renderTaskIds = new HashSet<>();

    public AvatarRenderTaskBridge(AvatarState state) {
        this.state = state;
    }

    public void register(Globals globals) {
        globals.set("_shyne_render_task", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                boolean world = args.arg(3).optboolean(false);
                AvatarPermission renderPermission = world ? AvatarPermission.WORLD_RENDER : AvatarPermission.HUD_RENDER;
                if (!state.permissionAllowed(renderPermission)) return LuaValue.FALSE;
                String id = DynamicAvatarInputRegistry.sanitize(args.arg(1).optjstring(""));
                if (id.isBlank()) return LuaValue.FALSE;
                if (!renderTaskIds.contains(id) && renderTaskIds.size() >= AvatarRenderTaskRegistry.MAX_TASKS_PER_AVATAR) return LuaValue.FALSE;
                String attachmentPath = state.resolvePath(args.arg(21).optjstring(""));
                if (attachmentPath == null) attachmentPath = "";
                var spec = new AvatarRenderTaskRegistry.TaskSpec(
                    args.arg(2).optjstring("text"), world,
                    args.arg(4).optjstring(""), args.arg(5).optjstring(""),
                    args.arg(6).optdouble(0), args.arg(7).optdouble(0), args.arg(8).optdouble(0),
                    args.arg(9).optdouble(0), args.arg(10).optdouble(0), args.arg(11).optdouble(0),
                    args.arg(12).optdouble(1), args.arg(13).optdouble(16), args.arg(14).optdouble(1),
                    (int) args.arg(15).optlong(0xFFFFFFFFL), args.arg(16).optboolean(false), args.arg(17).optboolean(true),
                    args.arg(18).optdouble(128), args.arg(19).optint(0), args.arg(20).optdouble(1),
                    attachmentPath.isBlank() ? null : state.boundEntityId(), state.modelId(), attachmentPath,
                    args.arg(22).optdouble(0), args.arg(23).optdouble(0), args.arg(24).optdouble(0),
                    args.arg(25).optdouble(0), args.arg(26).optdouble(0), args.arg(27).optdouble(0),
                    args.arg(28).optboolean(false), args.arg(29).optboolean(true), args.arg(30).optboolean(false)
                );
                boolean added = AvatarRenderTaskRegistry.upsert(renderTaskOwner, state.avatarId(), id, spec);
                if (added) renderTaskIds.add(id);
                return LuaValue.valueOf(added);
            }
        });

        globals.set("_shyne_render_remove", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String id = DynamicAvatarInputRegistry.sanitize(arg.optjstring(""));
                renderTaskIds.remove(id);
                return LuaValue.valueOf(AvatarRenderTaskRegistry.remove(renderTaskOwner, state.avatarId(), id));
            }
        });

        globals.set("_shyne_render_clear", new ZeroArgFunction() {
            @Override public LuaValue call() {
                AvatarRenderTaskRegistry.clearOwner(renderTaskOwner);
                renderTaskIds.clear();
                return LuaValue.NIL;
            }
        });

        globals.set("_shyne_render_screen", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable result = new LuaTable();
                result.set("width", LuaValue.valueOf(AvatarRenderTaskRegistry.lastScreenWidth()));
                result.set("height", LuaValue.valueOf(AvatarRenderTaskRegistry.lastScreenHeight()));
                result.set("ready", LuaValue.valueOf(AvatarRenderTaskRegistry.lastScreenWidth() > 0));
                return result;
            }
        });

        globals.set("_shyne_render_stats", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable result = new LuaTable();
                result.set("tasks", LuaValue.valueOf(renderTaskIds.size()));
                result.set("rendered", LuaValue.valueOf(AvatarRenderTaskRegistry.lastRendered()));
                result.set("culled", LuaValue.valueOf(AvatarRenderTaskRegistry.lastCulled()));
                result.set("task_limit", LuaValue.valueOf(AvatarRenderTaskRegistry.MAX_TASKS_PER_AVATAR));
                result.set("frame_limit", LuaValue.valueOf(AvatarRenderTaskRegistry.MAX_RENDERED_TASKS_PER_FRAME));
                result.set("line_point_limit", LuaValue.valueOf(AvatarRenderTaskRegistry.MAX_LINE_POINTS_PER_FRAME));
                result.set("glyph_limit", LuaValue.valueOf(AvatarRenderTaskRegistry.MAX_TEXT_GLYPHS_PER_FRAME));
                return result;
            }
        });

        globals.set("_shyne_profiler_snapshot", new ZeroArgFunction() {
            @Override public LuaValue call() {
                var profile = AvatarProfiler.snapshot(AvatarRenderTaskRegistry.snapshots().size(), AvatarRenderTaskRegistry.estimatedBytes());
                LuaTable result = new LuaTable();
                result.set("fps", LuaValue.valueOf(profile.fps()));
                result.set("frame_ms", LuaValue.valueOf(profile.frameMs()));
                result.set("avatar_frame_ms", LuaValue.valueOf(profile.avatarFrameMs()));
                result.set("estimated_fps_loss", LuaValue.valueOf(profile.estimatedFpsLoss()));
                result.set("heap_bytes", LuaValue.valueOf(profile.heapBytes()));
                result.set("avatar_bytes", LuaValue.valueOf(profile.avatarBytes()));
                result.set("task_count", LuaValue.valueOf(profile.taskCount()));
                result.set("rendered_tasks", LuaValue.valueOf(AvatarRenderTaskRegistry.lastRendered()));
                result.set("culled_tasks", LuaValue.valueOf(AvatarRenderTaskRegistry.lastCulled()));
                LuaTable metrics = new LuaTable();
                profile.metrics().forEach((category, metric) -> {
                    LuaTable value = new LuaTable();
                    value.set("average_ms", LuaValue.valueOf(metric.averageMs()));
                    value.set("maximum_ms", LuaValue.valueOf(metric.maximumMs()));
                    value.set("last_ms", LuaValue.valueOf(metric.lastMs()));
                    metrics.set(category.name().toLowerCase(Locale.ROOT), value);
                });
                result.set("metrics", metrics);
                return result;
            }
        });
    }

    public void dispose() {
        AvatarRenderTaskRegistry.clearOwner(renderTaskOwner);
        renderTaskIds.clear();
    }

    public int taskCount() {
        return renderTaskIds.size();
    }

    public Set<String> renderTaskIds() {
        return renderTaskIds;
    }
}

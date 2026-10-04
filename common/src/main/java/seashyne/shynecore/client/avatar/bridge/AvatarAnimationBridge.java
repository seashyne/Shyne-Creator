package seashyne.shynecore.client.avatar.bridge;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import seashyne.shynecore.animation.AnimationPlayback;
import seashyne.shynecore.client.avatar.AvatarAnimationLayer;
import seashyne.shynecore.client.avatar.AvatarEmoteDefinition;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static seashyne.shynecore.client.avatar.bridge.AvatarBridgeHelper.stringList;

/**
 * Handles avatar animation playback, parameters, layers, emotes, and animation graphs.
 */
public final class AvatarAnimationBridge {
    private final AvatarState state;
    private final BbModelDefinition model;

    public AvatarAnimationBridge(AvatarState state, BbModelDefinition model) {
        this.state = state;
        this.model = model;
    }

    public void register(Globals globals) {
        globals.set("_avatar_anim_play", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                playAnimation(arg.tojstring(), 1.0, 1.0, 0, null, 0, 0, List.of(), false, 0);
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_anim_play_ex", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Boolean loop = args.arg(5).isnil() ? null : args.arg(5).toboolean();
                playAnimation(
                    args.arg(1).optjstring(""), args.arg(2).optdouble(1), args.arg(3).optdouble(1), args.arg(4).optint(0), loop,
                    args.arg(6).optint(0), args.arg(7).optint(0), stringList(args.arg(8)), args.arg(9).optboolean(false), args.arg(10).optint(0)
                );
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_anim_parameter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String operation = args.arg(1).optjstring("");
                String name = args.arg(2).optjstring("");
                return switch (operation) {
                    case "set" -> {
                        state.setAnimationParameter(name, args.arg(3).optdouble(0));
                        yield LuaValue.valueOf(state.animationParameter(name, 0));
                    }
                    case "clear" -> {
                        state.clearAnimationParameter(name);
                        yield LuaValue.NIL;
                    }
                    case "get" -> LuaValue.valueOf(state.animationParameter(name, 0));
                    default -> LuaValue.NIL;
                };
            }
        });

        globals.set("_avatar_anim_stop", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                stopAnimation(arg.optjstring(""));
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_anim_stop_all", new ZeroArgFunction() {
            @Override public LuaValue call() {
                state.animationLayers().clear();
                state.markAnimationLayersDirty();
                state.markSnapshotDirty();
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_anim_playing_list", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable table = new LuaTable();
                long now = System.currentTimeMillis();
                int idx = 1;
                for (var entry : state.animationLayers().entrySet()) {
                    if (!entry.getValue().finished(now)) {
                        table.set(idx++, LuaValue.valueOf(entry.getKey()));
                    }
                }
                return table;
            }
        });

        globals.set("_avatar_anim_playing", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(isAnimationPlaying(arg.optjstring("")));
            }
        });

        globals.set("_avatar_anim_exists", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(model != null && model.hasAnimation(arg.optjstring("")));
            }
        });

        globals.set("_avatar_anim_info", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String name = arg.optjstring("");
                var layer = state.animationLayers().get(name.toLowerCase(Locale.ROOT));
                LuaTable value = new LuaTable();
                if (layer == null) return value;
                long now = System.currentTimeMillis();
                value.set("time", LuaValue.valueOf(layer.currentTime(now)));
                value.set("length", LuaValue.valueOf(layer.lengthSeconds()));
                value.set("looping", LuaValue.valueOf(layer.looping()));
                value.set("weight", LuaValue.valueOf(layer.effectiveWeight(now)));
                value.set("priority", LuaValue.valueOf(layer.priority()));
                value.set("playing", LuaValue.valueOf(!layer.finished(now)));
                value.set("paused", LuaValue.valueOf(layer.paused()));
                return value;
            }
        });

        globals.set("_avatar_anim_time", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String name = args.arg(1).optjstring("");
                String key = name.toLowerCase(Locale.ROOT);
                var layer = state.animationLayers().get(key);
                if (layer == null) return LuaValue.ZERO;
                long now = System.currentTimeMillis();
                if (args.arg(2).isnil()) {
                    return LuaValue.valueOf(layer.currentTime(now));
                }
                double target = args.arg(2).todouble();
                state.animationLayers().put(key, layer.withTime(target, now));
                state.markAnimationLayersDirty();
                state.markSnapshotDirty();
                return LuaValue.valueOf(target);
            }
        });

        globals.set("_avatar_anim_pause", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String name = args.arg(1).optjstring("");
                String key = name.toLowerCase(Locale.ROOT);
                var layer = state.animationLayers().get(key);
                if (layer != null) {
                    boolean pause = args.arg(2).optboolean(true);
                    long now = System.currentTimeMillis();
                    state.animationLayers().put(key, layer.withPaused(pause, now));
                    state.markAnimationLayersDirty();
                    state.markSnapshotDirty();
                }
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_anim_length", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                var animation = model == null ? null : model.findAnimation(arg.optjstring(""));
                return LuaValue.valueOf(animation == null ? 0 : animation.lengthSeconds());
            }
        });

        globals.set("_avatar_emote_register", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String id = args.arg(1).optjstring("");
                String animation = args.arg(2).optjstring("");
                String title = args.arg(3).optjstring(id);
                String description = args.arg(4).optjstring("");
                String page = args.arg(5).optjstring("emotes");
                boolean loop = args.arg(6).optboolean(false);
                boolean localOnly = args.arg(7).optboolean(false);
                boolean closeOnUse = args.arg(8).optboolean(true);
                state.registerEmote(new AvatarEmoteDefinition(id, animation, title, description, page, loop, localOnly, closeOnUse));
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_emote_play", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(playEmote(arg.optjstring("")));
            }
        });

        globals.set("_avatar_graph_bind", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                state.animationGraph().bind(args.arg(1).optjstring(""), args.arg(2).optjstring(""));
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_graph_trigger", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(triggerAnimationGraph(arg.optjstring("")));
            }
        });
    }

    public void playAnimation(String animationName, double speed, double weight, int priority, Boolean loopOverride,
                              int fadeInTicks, int fadeOutTicks, List<String> mask, boolean additive, int transitionTicks) {
        if (animationName == null || animationName.isBlank() || state.boundEntityId() == null) return;
        var definition = model != null ? model.findAnimation(animationName) : null;
        double length = definition == null || definition.lengthSeconds() <= 0 ? 2.0 : definition.lengthSeconds();
        boolean looping = loopOverride != null ? loopOverride : definition == null || definition.looping();
        int transition = Math.max(0, Math.min(1200, transitionTicks));
        long now = System.currentTimeMillis();
        if (transition > 0 && !additive) {
            state.animationLayers().replaceAll((key, layer) ->
                !layer.additive() && layer.priority() == priority && !key.equals(animationName.toLowerCase(Locale.ROOT))
                    ? layer.requestStop(now, transition) : layer);
        }
        state.animationLayers().put(animationName.toLowerCase(Locale.ROOT), new AvatarAnimationLayer(
            animationName, now, length, looping,
            Math.max(0.01, Math.min(8.0, speed)), Math.max(0.0, Math.min(1.0, weight)),
            Math.max(-1000, Math.min(1000, priority)),
            Math.max(transition, Math.max(0, Math.min(1200, fadeInTicks))),
            Math.max(transition, Math.max(0, Math.min(1200, fadeOutTicks))),
            mask == null ? List.of() : List.copyOf(mask), additive, 0L
        ));
        state.markAnimationLayersDirty();
        state.markSnapshotDirty();
        state.refreshCurrentAnimationFromLayers(now);
        publishPlayback(state, now);
    }

    public void stopAnimation(String animationName) {
        if (animationName == null) return;
        String key = animationName.toLowerCase(Locale.ROOT);
        AvatarAnimationLayer layer = state.animationLayers().get(key);
        long now = System.currentTimeMillis();
        if (layer != null && layer.fadeOutTicks() > 0) state.animationLayers().put(key, layer.requestStop(now));
        else state.animationLayers().remove(key);
        state.markAnimationLayersDirty();
        state.markSnapshotDirty();
        state.refreshCurrentAnimationFromLayers(now);
        publishPlayback(state, now);
    }

    public boolean isAnimationPlaying(String animationName) {
        return animationName != null && state.animationLayers().containsKey(animationName.toLowerCase(Locale.ROOT));
    }

    public boolean playEmote(String emoteId) {
        AvatarEmoteDefinition emote = state.findEmote(emoteId);
        if (emote == null) return false;
        playAnimation(emote.animation(), 1.0, 1.0, 0, emote.loop(), 0, 0, List.of(), false, 0);
        return true;
    }

    public boolean triggerAnimationGraph(String trigger) {
        if (trigger == null || trigger.isBlank()) return false;
        String emoteId = state.animationGraph().resolve(trigger);
        return emoteId != null && !emoteId.isBlank() && playEmote(emoteId);
    }

    public static void publishPlayback(AvatarState state, long now) {
        if (state == null) return;
        UUID entityId = state.boundEntityId();
        if (entityId == null) return;
        AvatarAnimationLayer representative = state.representativeAnimationLayer(now);
        if (representative == null) {
            ClientAnimationState.removeLocalPlayback(entityId);
            return;
        }
        ClientAnimationState.putLocalPlayback(new AnimationPlayback(
            entityId, "local-player", state.modelId(), representative.name(), representative.startedAtMillis(),
            representative.lengthSeconds(), true
        ));
    }
}

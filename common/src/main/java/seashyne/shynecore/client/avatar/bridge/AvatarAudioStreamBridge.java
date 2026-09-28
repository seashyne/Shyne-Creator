package seashyne.shynecore.client.avatar.bridge;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import seashyne.shynecore.admin.ShyneServerPolicy;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.avatar.sound.AvatarAudioStream;
import seashyne.shynecore.client.avatar.sound.AvatarAudioStreamManager;

/**
 * Bridges Lua scripts with the native asynchronous audio streaming subsystem.
 */
public final class AvatarAudioStreamBridge {
    private AvatarAudioStreamBridge() {}

    public static void register(Globals globals, AvatarState state) {
        globals.set("_shyne_audio_stream_create", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                // 1. Permission check
                if (!state.permissionAllowed(AvatarPermission.AUDIO_STREAM)) return LuaValue.ZERO;
                // 2. Server policy check
                if (!ShyneServerPolicy.get().isExternalAudioStreamsAllowed()) return LuaValue.ZERO;

                String url = args.arg(1).optjstring("");
                float volume = (float) args.arg(2).optdouble(1.0);
                float pitch = (float) args.arg(3).optdouble(1.0);
                boolean loop = args.arg(4).optboolean(false);
                Double posX = args.arg(5).isnil() ? null : args.arg(5).todouble();
                Double posY = args.arg(6).isnil() ? null : args.arg(6).todouble();
                Double posZ = args.arg(7).isnil() ? null : args.arg(7).todouble();
                boolean autoPlay = args.arg(8).optboolean(true);

                int streamId = AvatarAudioStreamManager.createStream(url, state.avatarId(), volume, pitch, loop, posX, posY, posZ, autoPlay);
                return LuaValue.valueOf(streamId);
            }
        });

        globals.set("_shyne_audio_stream_play", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(arg.toint());
                if (stream != null) stream.play();
                return LuaValue.NIL;
            }
        });

        globals.set("_shyne_audio_stream_pause", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(arg.toint());
                if (stream != null) stream.pause();
                return LuaValue.NIL;
            }
        });

        globals.set("_shyne_audio_stream_stop", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(args.arg(1).toint());
                float fade = (float) args.arg(2).optdouble(0.0);
                if (stream != null) stream.stop(fade);
                return LuaValue.NIL;
            }
        });

        globals.set("_shyne_audio_stream_set_volume", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(args.arg(1).toint());
                if (stream != null) stream.setVolume((float) args.arg(2).optdouble(1.0));
                return LuaValue.NIL;
            }
        });

        globals.set("_shyne_audio_stream_set_pitch", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(args.arg(1).toint());
                if (stream != null) stream.setPitch((float) args.arg(2).optdouble(1.0));
                return LuaValue.NIL;
            }
        });

        globals.set("_shyne_audio_stream_set_pos", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(args.arg(1).toint());
                if (stream != null) {
                    Double x = args.arg(2).isnil() ? null : args.arg(2).todouble();
                    Double y = args.arg(3).isnil() ? null : args.arg(3).todouble();
                    Double z = args.arg(4).isnil() ? null : args.arg(4).todouble();
                    stream.setPos(x, y, z);
                }
                return LuaValue.NIL;
            }
        });

        globals.set("_shyne_audio_stream_get_level", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(arg.toint());
                return LuaValue.valueOf(stream != null ? stream.currentLevel() : 0.0);
            }
        });

        globals.set("_shyne_audio_stream_get_peak", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(arg.toint());
                return LuaValue.valueOf(stream != null ? stream.peakLevel() : 0.0);
            }
        });

        globals.set("_shyne_audio_stream_is_beat", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(arg.toint());
                return LuaValue.valueOf(stream != null && stream.isBeat());
            }
        });

        globals.set("_shyne_audio_stream_is_playing", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(arg.toint());
                return LuaValue.valueOf(stream != null && stream.isPlaying());
            }
        });

        globals.set("_shyne_audio_stream_is_buffering", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(arg.toint());
                return LuaValue.valueOf(stream != null && stream.isBuffering());
            }
        });

        globals.set("_shyne_audio_stream_is_paused", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(arg.toint());
                return LuaValue.valueOf(stream != null && stream.isPaused());
            }
        });

        globals.set("_shyne_audio_stream_is_stopped", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                AvatarAudioStream stream = AvatarAudioStreamManager.get(arg.toint());
                return LuaValue.valueOf(stream == null || stream.isStopped());
            }
        });
    }
}

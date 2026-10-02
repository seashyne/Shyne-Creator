package seashyne.shynecore.client.avatar.bridge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.input.DynamicAvatarInputRegistry;
import seashyne.shynecore.script.LuaSandbox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles avatar key and mouse input bindings, repeat triggers, and input polling.
 */
public final class AvatarInputBridge {
    public record InputBinding(
        DynamicAvatarInputRegistry.Handle handle,
        LuaValue onPress,
        LuaValue onRelease,
        LuaValue onHold,
        boolean repeat,
        int repeatDelay,
        int repeatInterval,
        boolean enabled,
        boolean gui,
        boolean wasDown,
        int heldTicks
    ) {}

    private final AvatarState state;
    private final Object inputOwner = new Object();
    private final Map<String, InputBinding> inputBindings = new LinkedHashMap<>();

    public AvatarInputBridge(AvatarState state) {
        this.state = state;
    }

    public void register(Globals globals) {
        globals.set("_shyne_input_bind", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (inputBindings.size() >= DynamicAvatarInputRegistry.MAX_BINDINGS_PER_AVATAR) {
                    ShyneCore.LOGGER.warn("[AvatarInput] {} reached the {} binding limit", state.avatarId(), DynamicAvatarInputRegistry.MAX_BINDINGS_PER_AVATAR);
                    return LuaValue.FALSE;
                }
                String id = DynamicAvatarInputRegistry.sanitize(args.arg(1).optjstring(""));
                if (id.isBlank()) return LuaValue.FALSE;
                try {
                    InputBinding previous = inputBindings.remove(id);
                    if (previous != null) previous.handle().close();
                    InputConstants.Type inputType = DynamicAvatarInputRegistry.inputType(args.arg(4).optjstring("keyboard"));
                    InputConstants.Key defaultKey = inputKey(args.arg(3), inputType);
                    var handle = DynamicAvatarInputRegistry.register(
                        inputOwner, state.avatarId(), id, args.arg(2).optjstring(id),
                        defaultKey.getType(), defaultKey.getValue(), args.arg(5).optint(0)
                    );
                    inputBindings.put(id, new InputBinding(handle, args.arg(6), args.arg(7), args.arg(8),
                        args.arg(9).optboolean(false), Math.max(1, args.arg(10).optint(10)),
                        Math.max(1, args.arg(11).optint(2)), true, args.arg(12).optboolean(false), handle.isDown(args.arg(12).optboolean(false)), 0));
                    return LuaValue.valueOf(id);
                } catch (Exception error) {
                    ShyneCore.LOGGER.warn("[AvatarInput] Could not bind {}.{}: {}", state.avatarId(), id, error.getMessage());
                    return LuaValue.FALSE;
                }
            }
        });

        globals.set("_shyne_input_unbind", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.remove(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                if (binding == null) return LuaValue.FALSE;
                binding.handle().close();
                return LuaValue.TRUE;
            }
        });

        globals.set("_shyne_input_is_down", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                return LuaValue.valueOf(binding != null && binding.enabled() && binding.handle().isDown(binding.gui()));
            }
        });

        globals.set("_shyne_input_get_key", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                if (binding == null) return LuaValue.NIL;
                return luaString(DynamicAvatarInputRegistry.keyName(binding.handle().stableId()));
            }
        });

        globals.set("_shyne_input_get_key_name", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                return binding == null ? LuaValue.NIL : luaString(DynamicAvatarInputRegistry.keyDisplayName(binding.handle().stableId()));
            }
        });

        globals.set("_shyne_input_get_default_key", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                return binding == null ? LuaValue.NIL : luaString(DynamicAvatarInputRegistry.defaultKeyName(binding.handle().stableId()));
            }
        });

        globals.set("_shyne_input_get_id", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                return LuaValue.valueOf(binding == null ? InputConstants.UNKNOWN.getValue()
                    : DynamicAvatarInputRegistry.keyCode(binding.handle().stableId()));
            }
        });

        globals.set("_shyne_input_set_key", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(args.arg(1).optjstring("")));
                if (binding == null) return LuaValue.FALSE;
                try {
                    return LuaValue.valueOf(DynamicAvatarInputRegistry.setKey(binding.handle().stableId(),
                        InputConstants.getKey(args.arg(2).checkjstring())));
                } catch (Exception error) {
                    return LuaValue.FALSE;
                }
            }
        });

        globals.set("_shyne_input_conflicts", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                LuaTable result = new LuaTable();
                if (binding == null) return result;
                List<String> conflicts = binding.handle().conflicts();
                for (int i = 0; i < conflicts.size(); i++) result.set(i + 1, LuaValue.valueOf(conflicts.get(i)));
                return result;
            }
        });

        globals.set("_shyne_input_reset", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                return LuaValue.valueOf(binding != null && DynamicAvatarInputRegistry.reset(binding.handle().stableId()));
            }
        });

        globals.set("_shyne_input_is_default", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                return LuaValue.valueOf(binding != null && java.util.Objects.equals(
                    DynamicAvatarInputRegistry.keyName(binding.handle().stableId()),
                    DynamicAvatarInputRegistry.defaultKeyName(binding.handle().stableId())));
            }
        });

        globals.set("_shyne_input_set_enabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String id = DynamicAvatarInputRegistry.sanitize(args.arg(1).optjstring(""));
                InputBinding binding = inputBindings.get(id);
                if (binding == null) return LuaValue.FALSE;
                inputBindings.put(id, updated(binding, args.arg(2).optboolean(true), binding.gui()));
                return LuaValue.TRUE;
            }
        });

        globals.set("_shyne_input_is_enabled", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                return LuaValue.valueOf(binding != null && binding.enabled());
            }
        });

        globals.set("_shyne_input_set_gui", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String id = DynamicAvatarInputRegistry.sanitize(args.arg(1).optjstring(""));
                InputBinding binding = inputBindings.get(id);
                if (binding == null) return LuaValue.FALSE;
                inputBindings.put(id, updated(binding, binding.enabled(), args.arg(2).optboolean(false)));
                return LuaValue.TRUE;
            }
        });

        globals.set("_shyne_input_is_gui", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                return LuaValue.valueOf(binding != null && binding.gui());
            }
        });

        globals.set("_shyne_input_vanilla_key", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                KeyMapping mapping = vanillaKey(arg.optjstring(""));
                return mapping == null ? LuaValue.NIL : LuaValue.valueOf(mapping.saveString());
            }
        });

        globals.set("_shyne_input_vanilla_name", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                KeyMapping mapping = vanillaKey(arg.optjstring(""));
                return mapping == null ? LuaValue.NIL : LuaValue.valueOf(mapping.getTranslatedKeyMessage().getString());
            }
        });
    }

    public void pollInputBindings(LuaSandbox.Budget instructionBudget, int eventInstructionLimit) {
        for (String id : List.copyOf(inputBindings.keySet())) {
            InputBinding binding = inputBindings.get(id);
            if (binding == null) continue;
            boolean down = binding.enabled() && binding.handle().isDown(binding.gui());
            int heldTicks = down ? binding.heldTicks() + 1 : 0;
            if (down != binding.wasDown()) {
                LuaValue callback = down ? binding.onPress() : binding.onRelease();
                if (callback != null && callback.isfunction()) {
                    try {
                        instructionBudget.reset(eventInstructionLimit);
                        callback.call(LuaValue.valueOf(id));
                    } catch (Exception error) {
                        ShyneCore.LOGGER.error("[AvatarLua] input {} failed: {}", id, error.getMessage(), error);
                    }
                }
            }
            if (inputBindings.get(id) != binding) continue;
            if (down && binding.onHold() != null && binding.onHold().isfunction()) {
                invokeInputCallback(id, binding.onHold(), instructionBudget, eventInstructionLimit);
            }
            if (inputBindings.get(id) != binding) continue;
            if (down && binding.repeat() && heldTicks >= binding.repeatDelay()
                && (heldTicks - binding.repeatDelay()) % binding.repeatInterval() == 0) {
                invokeInputCallback(id, binding.onPress(), instructionBudget, eventInstructionLimit);
            }
            if (inputBindings.get(id) != binding) continue;
            inputBindings.put(id, new InputBinding(binding.handle(), binding.onPress(), binding.onRelease(), binding.onHold(),
                binding.repeat(), binding.repeatDelay(), binding.repeatInterval(), binding.enabled(), binding.gui(), down, heldTicks));
        }
    }

    private void invokeInputCallback(String id, LuaValue callback, LuaSandbox.Budget instructionBudget, int eventInstructionLimit) {
        if (callback == null || !callback.isfunction()) return;
        try {
            instructionBudget.reset(eventInstructionLimit);
            callback.call(LuaValue.valueOf(id));
        } catch (Exception error) {
            ShyneCore.LOGGER.error("[AvatarLua] input {} failed: {}", id, error.getMessage(), error);
        }
    }

    private static InputConstants.Key inputKey(LuaValue value, InputConstants.Type fallbackType) {
        if (value.isstring()) return InputConstants.getKey(value.checkjstring());
        return fallbackType.getOrCreate(value.optint(InputConstants.UNKNOWN.getValue()));
    }

    private static InputBinding updated(InputBinding binding, boolean enabled, boolean gui) {
        return new InputBinding(binding.handle(), binding.onPress(), binding.onRelease(), binding.onHold(),
            binding.repeat(), binding.repeatDelay(), binding.repeatInterval(), enabled, gui, binding.wasDown(), binding.heldTicks());
    }

    private static LuaValue luaString(String value) {
        return value == null ? LuaValue.NIL : LuaValue.valueOf(value);
    }

    private static KeyMapping vanillaKey(String id) {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null || id == null || id.isBlank()) return null;
        for (KeyMapping mapping : client.options.keyMappings) {
            if (id.equals(mapping.getName())) return mapping;
        }
        return null;
    }

    public void dispose() {
        for (InputBinding binding : inputBindings.values()) {
            binding.handle().close();
        }
        inputBindings.clear();
    }

    public int bindingCount() {
        return inputBindings.size();
    }

    public int conflictCount() {
        return inputBindings.values().stream().mapToInt(value -> value.handle().conflicts().size()).sum();
    }

    public Map<String, InputBinding> inputBindings() {
        return inputBindings;
    }
}

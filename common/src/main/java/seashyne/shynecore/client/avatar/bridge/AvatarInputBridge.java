package seashyne.shynecore.client.avatar.bridge;

import com.mojang.blaze3d.platform.InputConstants;
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
                    var handle = DynamicAvatarInputRegistry.register(
                        inputOwner, state.avatarId(), id, args.arg(2).optjstring(id),
                        DynamicAvatarInputRegistry.inputType(args.arg(4).optjstring("keyboard")),
                        args.arg(3).optint(InputConstants.UNKNOWN.getValue()), args.arg(5).optint(0)
                    );
                    inputBindings.put(id, new InputBinding(handle, args.arg(6), args.arg(7), args.arg(8),
                        args.arg(9).optboolean(false), Math.max(1, args.arg(10).optint(10)),
                        Math.max(1, args.arg(11).optint(2)), handle.isDown(), 0));
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
                return LuaValue.valueOf(binding != null && binding.handle().isDown());
            }
        });

        globals.set("_shyne_input_get_key", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                if (binding == null) return LuaValue.NIL;
                return DynamicAvatarInputRegistry.snapshots().stream()
                    .filter(value -> value.stableId().equals(binding.handle().stableId())).findFirst()
                    .<LuaValue>map(value -> LuaValue.valueOf(value.keyName())).orElse(LuaValue.NIL);
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
    }

    public void pollInputBindings(LuaSandbox.Budget instructionBudget, int eventInstructionLimit) {
        for (String id : List.copyOf(inputBindings.keySet())) {
            InputBinding binding = inputBindings.get(id);
            if (binding == null) continue;
            boolean down = binding.handle().isDown();
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
                binding.repeat(), binding.repeatDelay(), binding.repeatInterval(), down, heldTicks));
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

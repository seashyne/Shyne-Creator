package seashyne.shynecore.script;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts sandbox values without allowing cyclic or sparse tables to exhaust memory. */
public final class LuaValueCodec {
    public static final int MAX_DEPTH = 8;
    public static final int MAX_TABLE_ENTRIES = 256;
    public static final int MAX_TOTAL_NODES = 1_024;
    public static final int MAX_STRING_CHARS = 4_096;

    private LuaValueCodec() {}

    public static Object toJava(LuaValue value) {
        return toJava(value == null ? LuaValue.NIL : value, 0, new Budget(), new IdentityHashMap<>());
    }

    public static LuaValue toLua(Object value) {
        return toLua(value, 0, new Budget(), new IdentityHashMap<>());
    }

    private static Object toJava(LuaValue value, int depth, Budget budget, IdentityHashMap<LuaValue, Boolean> visiting) {
        budget.visit(depth);
        if (value.isnil()) return null;
        if (value.isboolean()) return value.toboolean();
        if (value.isnumber()) {
            double number = value.todouble();
            if (!Double.isFinite(number)) throw new LuaError("avatar value must be a finite number");
            return number;
        }
        if (value.isstring()) return boundedString(value.tojstring(), "avatar string");
        if (!value.istable()) throw new LuaError("avatar values support only nil, boolean, number, string, and table");

        LuaTable table = value.checktable();
        if (visiting.put(table, Boolean.TRUE) != null) throw new LuaError("cyclic avatar table is not allowed");
        try {
            Map<Integer, Object> numeric = new LinkedHashMap<>();
            Map<String, Object> named = new LinkedHashMap<>();
            LuaValue cursor = LuaValue.NIL;
            int entries = 0;
            while (true) {
                Varargs next = table.next(cursor);
                LuaValue key = next.arg1();
                if (key.isnil()) break;
                cursor = key;
                if (++entries > MAX_TABLE_ENTRIES) throw new LuaError("avatar table has more than " + MAX_TABLE_ENTRIES + " entries");
                Object converted = toJava(next.arg(2), depth + 1, budget, visiting);
                if (key.isinttype()) {
                    int index = key.toint();
                    if (index < 1 || index > MAX_TABLE_ENTRIES) throw new LuaError("avatar array index must be between 1 and " + MAX_TABLE_ENTRIES);
                    numeric.put(index, converted);
                } else {
                    String name = boundedString(key.tojstring(), "avatar table key");
                    if (name.isBlank()) throw new LuaError("avatar table key cannot be empty");
                    named.put(name, converted);
                }
            }

            if (named.isEmpty() && !numeric.isEmpty() && numeric.size() == numeric.keySet().stream().mapToInt(Integer::intValue).max().orElse(0)) {
                List<Object> array = new ArrayList<>(numeric.size());
                for (int index = 1; index <= numeric.size(); index++) array.add(numeric.get(index));
                return array;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<Integer, Object> entry : numeric.entrySet()) map.put(Integer.toString(entry.getKey()), entry.getValue());
            map.putAll(named);
            return map;
        } finally {
            visiting.remove(table);
        }
    }

    private static LuaValue toLua(Object value, int depth, Budget budget, IdentityHashMap<Object, Boolean> visiting) {
        budget.visit(depth);
        if (value == null) return LuaValue.NIL;
        if (value instanceof Boolean booleanValue) return LuaValue.valueOf(booleanValue);
        if (value instanceof Number numberValue) {
            double number = numberValue.doubleValue();
            if (!Double.isFinite(number)) throw new LuaError("avatar value must be a finite number");
            return LuaValue.valueOf(number);
        }
        if (value instanceof String stringValue) return LuaValue.valueOf(boundedString(stringValue, "avatar string"));
        if (value instanceof Map<?, ?> map) {
            enterJavaContainer(value, map.size(), visiting);
            try {
                LuaTable table = new LuaTable();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = boundedString(String.valueOf(entry.getKey()), "avatar table key");
                    table.set(key, toLua(entry.getValue(), depth + 1, budget, visiting));
                }
                return table;
            } finally {
                visiting.remove(value);
            }
        }
        if (value instanceof List<?> list) {
            enterJavaContainer(value, list.size(), visiting);
            try {
                LuaTable table = new LuaTable();
                for (int index = 0; index < list.size(); index++) table.set(index + 1, toLua(list.get(index), depth + 1, budget, visiting));
                return table;
            } finally {
                visiting.remove(value);
            }
        }
        throw new LuaError("unsupported avatar value type: " + value.getClass().getSimpleName());
    }

    private static void enterJavaContainer(Object value, int size, IdentityHashMap<Object, Boolean> visiting) {
        if (size > MAX_TABLE_ENTRIES) throw new LuaError("avatar table has more than " + MAX_TABLE_ENTRIES + " entries");
        if (visiting.put(value, Boolean.TRUE) != null) throw new LuaError("cyclic avatar value is not allowed");
    }

    private static String boundedString(String value, String label) {
        String safe = value == null ? "" : value;
        if (safe.length() > MAX_STRING_CHARS) throw new LuaError(label + " is longer than " + MAX_STRING_CHARS + " characters");
        return safe;
    }

    private static final class Budget {
        private int nodes;
        private void visit(int depth) {
            if (depth > MAX_DEPTH) throw new LuaError("avatar value nesting is deeper than " + MAX_DEPTH);
            if (++nodes > MAX_TOTAL_NODES) throw new LuaError("avatar value is too complex");
        }
    }
}

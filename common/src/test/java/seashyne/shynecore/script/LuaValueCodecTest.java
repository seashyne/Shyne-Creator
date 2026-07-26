package seashyne.shynecore.script;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LuaValueCodecTest {
    @Test
    void nilMeansRemoveAndDenseArraysRemainLists() {
        assertNull(LuaValueCodec.toJava(LuaValue.NIL));
        LuaTable table = new LuaTable();
        table.set(1, LuaValue.valueOf("a"));
        table.set(2, LuaValue.valueOf("b"));
        assertEquals(List.of("a", "b"), LuaValueCodec.toJava(table));
        assertEquals("value", LuaValueCodec.toLua(Map.of("key", "value")).get("key").tojstring());
    }

    @Test
    void rejectsCyclesAndSparseHugeIndices() {
        LuaTable cyclic = new LuaTable();
        cyclic.set("self", cyclic);
        assertThrows(LuaError.class, () -> LuaValueCodec.toJava(cyclic));

        LuaTable sparse = new LuaTable();
        sparse.set(1_000_000_000, LuaValue.valueOf("boom"));
        assertThrows(LuaError.class, () -> LuaValueCodec.toJava(sparse));
    }

    @Test
    void rejectsNonFiniteNumbers() {
        assertThrows(LuaError.class, () -> LuaValueCodec.toJava(LuaValue.valueOf(Double.NaN)));
    }
}

package seashyne.shynecore.client.avatar.bridge;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;

public final class AvatarBridgeHelper {
    private AvatarBridgeHelper() {}

    public static LuaTable vec3(double x, double y, double z) {
        LuaTable value = new LuaTable();
        value.set("x", LuaValue.valueOf(x));
        value.set("y", LuaValue.valueOf(y));
        value.set("z", LuaValue.valueOf(z));
        value.set(1, LuaValue.valueOf(x));
        value.set(2, LuaValue.valueOf(y));
        value.set(3, LuaValue.valueOf(z));
        return value;
    }

    public static LuaTable matrix(float[] values) {
        LuaTable result = new LuaTable();
        float[] safe = values == null || values.length != 16
            ? new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}
            : values;
        for (int index = 0; index < 16; index++) result.set(index + 1, LuaValue.valueOf(safe[index]));
        return result;
    }

    public static List<String> stringList(LuaValue value) {
        if (!value.istable()) return List.of();
        List<String> result = new ArrayList<>();
        LuaTable table = value.checktable();
        for (int i = 1; i <= table.length() && result.size() < 256; i++) {
            String item = table.get(i).optjstring("").trim();
            if (!item.isBlank()) result.add(item);
        }
        return List.copyOf(result);
    }
}

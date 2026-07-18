package seashyne.shynecore.bridge;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** In-process transport from sandboxed Lua scripts to the server action dispatcher. */
public final class ActionBus {
    private final List<Consumer<JsonObject>> handlers = new CopyOnWriteArrayList<>();

    public void addHandler(Consumer<JsonObject> handler) {
        handlers.add(handler);
    }

    public void emit(JsonObject message) {
        for (Consumer<JsonObject> handler : handlers) handler.accept(message);
    }
}

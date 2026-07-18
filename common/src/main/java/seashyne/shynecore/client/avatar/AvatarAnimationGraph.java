package seashyne.shynecore.client.avatar;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AvatarAnimationGraph {
    private final Map<String, String> triggers = new LinkedHashMap<>();

    public void bind(String trigger, String emoteId) {
        if (trigger == null || trigger.isBlank() || emoteId == null || emoteId.isBlank()) return;
        triggers.put(trigger, emoteId);
    }

    public String resolve(String trigger) {
        if (trigger == null || trigger.isBlank()) return "";
        return triggers.getOrDefault(trigger, "");
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(triggers);
    }
}

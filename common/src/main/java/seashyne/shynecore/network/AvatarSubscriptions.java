package seashyne.shynecore.network;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Recipient-local allow list. It starts empty so saved privacy rules apply
 * before any avatar payload is sent.
 */
public class AvatarSubscriptions {
    private boolean subscribeAll;
    private final Set<UUID> subscribed = new HashSet<>();
    private final Set<UUID> excluded = new HashSet<>();

    public boolean isSubscribed(UUID playerId) {
        if (playerId == null) return false;
        return subscribeAll ? !excluded.contains(playerId) : subscribed.contains(playerId);
    }

    public void subscribe(UUID playerId) {
        if (playerId == null) return;
        if (subscribeAll) excluded.remove(playerId); else subscribed.add(playerId);
    }

    public void unsubscribe(UUID playerId) {
        if (playerId == null) return;
        if (subscribeAll) excluded.add(playerId); else subscribed.remove(playerId);
    }

    public void subscribeAll() {
        subscribeAll = true;
        subscribed.clear();
        excluded.clear();
    }

    public void reset() {
        subscribeAll = false;
        subscribed.clear();
        excluded.clear();
    }

    public void forget(UUID playerId) {
        subscribed.remove(playerId);
        excluded.remove(playerId);
    }
}

package seashyne.shynecore.client.avatar;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class AvatarSyncPolicy {
    private boolean allowRemoteSnapshot = true;
    private boolean allowRemoteVars = true;
    private final Set<String> syncedVarAllowlist = new LinkedHashSet<>();
    private final Set<String> localOnlyParts = new LinkedHashSet<>();
    private final Set<String> localOnlyVanillaParts = new LinkedHashSet<>();

    public boolean allowRemoteSnapshot() { return allowRemoteSnapshot; }
    public void setAllowRemoteSnapshot(boolean allowRemoteSnapshot) { this.allowRemoteSnapshot = allowRemoteSnapshot; }
    public boolean allowRemoteVars() { return allowRemoteVars; }
    public void setAllowRemoteVars(boolean allowRemoteVars) { this.allowRemoteVars = allowRemoteVars; }
    public Set<String> syncedVarAllowlist() { return syncedVarAllowlist; }
    public Set<String> localOnlyParts() { return localOnlyParts; }
    public Set<String> localOnlyVanillaParts() { return localOnlyVanillaParts; }

    public void allowSyncedVar(String key) {
        if (key != null && !key.isBlank()) syncedVarAllowlist.add(key);
    }

    public void setLocalOnlyPart(String path, boolean localOnly) {
        if (path == null || path.isBlank()) return;
        if (localOnly) localOnlyParts.add(path);
        else localOnlyParts.remove(path);
    }

    public void setLocalOnlyVanillaPart(String part, boolean localOnly) {
        if (part == null || part.isBlank()) return;
        if (localOnly) localOnlyVanillaParts.add(part);
        else localOnlyVanillaParts.remove(part);
    }

    public Map<String, Object> filterSyncedVars(Map<String, Object> source) {
        if (!allowRemoteVars || source == null || source.isEmpty()) return Map.of();
        if (syncedVarAllowlist.isEmpty()) return Map.copyOf(source);
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (syncedVarAllowlist.contains(entry.getKey())) filtered.put(entry.getKey(), entry.getValue());
        }
        return filtered;
    }

    public Map<String, AvatarPartState> filterParts(Map<String, AvatarPartState> source) {
        if (!allowRemoteSnapshot || source == null || source.isEmpty()) return Map.of();
        Map<String, AvatarPartState> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, AvatarPartState> entry : source.entrySet()) {
            if (!localOnlyParts.contains(entry.getKey())) filtered.put(entry.getKey(), entry.getValue().copy());
        }
        return filtered;
    }

    public Map<String, Boolean> filterVanillaVisibility(Map<String, Boolean> source) {
        if (!allowRemoteSnapshot || source == null || source.isEmpty()) return Map.of();
        Map<String, Boolean> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : source.entrySet()) {
            if (!localOnlyVanillaParts.contains(entry.getKey())) filtered.put(entry.getKey(), entry.getValue());
        }
        return filtered;
    }
}

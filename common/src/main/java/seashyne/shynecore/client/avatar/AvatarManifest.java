package seashyne.shynecore.client.avatar;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record AvatarManifest(
    int apiVersion,
    String id,
    String name,
    String version,
    String main,
    String model,
    boolean replaceVanilla,
    boolean onlineSync,
    String description,
    boolean firstPersonMasking,
    boolean localCamera,
    String textureSyncMode,
    String syncedSchema,
    List<String> textures,
    Set<AvatarPermission> permissions,
    String api,
    boolean automaticApi,
    Map<String, String> apiRequirements
) {}

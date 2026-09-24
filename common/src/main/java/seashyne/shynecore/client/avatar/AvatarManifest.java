package seashyne.shynecore.client.avatar;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record AvatarManifest(
    String standard,
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
    Map<String, String> apiRequirements,
    String profile,
    AvatarBehavior behavior,
    String importSource
) {
    public AvatarManifest(
        String standard,
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
        Map<String, String> apiRequirements,
        String profile,
        AvatarBehavior behavior
    ) {
        this(standard, id, name, version, main, model, replaceVanilla, onlineSync, description, firstPersonMasking, localCamera, textureSyncMode, syncedSchema, textures, permissions, api, automaticApi, apiRequirements, profile, behavior, "");
    }

    public boolean hasScript() { return main != null && !main.isBlank(); }
    public AvatarProfile parsedProfile() { return AvatarProfile.parse(profile); }
    public boolean isFiguraImport() { return "figura".equalsIgnoreCase(importSource); }
}

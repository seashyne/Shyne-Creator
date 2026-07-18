package seashyne.shynecore.client.avatar;

import java.util.Map;
import java.util.UUID;

public record RemoteAvatarState(
    UUID playerId,
    String avatarId,
    String modelId,
    boolean replaceVanilla,
    Map<String, AvatarPartState> parts,
    Map<String, Boolean> vanillaVisibility,
    Map<String, Object> syncedVars,
    String currentAnimation,
    long animationStartedAtMillis
) {}

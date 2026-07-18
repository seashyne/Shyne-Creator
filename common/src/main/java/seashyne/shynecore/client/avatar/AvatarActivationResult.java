package seashyne.shynecore.client.avatar;

public record AvatarActivationResult(boolean success, String avatarId, String message) {
    public static AvatarActivationResult success(String avatarId, String message) {
        return new AvatarActivationResult(true, avatarId == null ? "" : avatarId, message == null ? "" : message);
    }

    public static AvatarActivationResult failure(String avatarId, String message) {
        return new AvatarActivationResult(false, avatarId == null ? "" : avatarId, message == null ? "Unknown error" : message);
    }
}

package seashyne.shynecore.client.avatar;

/**
 * Result of an avatar activation or reload attempt, including line-precise error info
 * when a script or configuration fails.
 */
public record AvatarActivationResult(
    boolean success,
    String avatarId,
    String message,
    String errorFile,
    int errorLine,
    String cleanMessage
) {
    public AvatarActivationResult(boolean success, String avatarId, String message) {
        this(success, avatarId, message, "", -1, message);
    }

    public static AvatarActivationResult success(String avatarId, String message) {
        return new AvatarActivationResult(true, avatarId == null ? "" : avatarId, message == null ? "" : message, "", -1, "");
    }

    public static AvatarActivationResult failure(String avatarId, String message) {
        return new AvatarActivationResult(false, avatarId == null ? "" : avatarId, message == null ? "Unknown error" : message, "", -1, message == null ? "Unknown error" : message);
    }

    public static AvatarActivationResult failure(String avatarId, String message, String errorFile, int errorLine, String cleanMessage) {
        return new AvatarActivationResult(false, avatarId == null ? "" : avatarId, message == null ? "Unknown error" : message, errorFile == null ? "" : errorFile, errorLine, cleanMessage == null ? "" : cleanMessage);
    }

    /**
     * Formats the error into a creator-friendly string indicating file and line if available.
     * Example: "avatar.lua:14 - attempt to index nil global 'mymodel'"
     */
    public String formattedError() {
        if (success) return message;
        if (errorFile != null && !errorFile.isBlank() && errorLine > 0) {
            String detail = (cleanMessage != null && !cleanMessage.isBlank()) ? cleanMessage : message;
            return errorFile + ":" + errorLine + " - " + detail;
        }
        return message == null ? "Unknown error" : message;
    }
}

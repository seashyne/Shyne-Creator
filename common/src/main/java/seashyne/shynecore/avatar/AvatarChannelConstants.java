package seashyne.shynecore.avatar;

import java.util.regex.Pattern;

/**
 * Shared validation and limits for avatar communication channels.
 * Safe for use on both server and client runtimes.
 */
public final class AvatarChannelConstants {
    public static final int MAX_PAYLOAD_CHARS = 8192;
    public static final int MAX_PACKETS_PER_SEC = 20;
    private static final Pattern VALID_CHANNEL = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_.-]+$");

    private AvatarChannelConstants() {}

    public static boolean isValidChannel(String channel) {
        if (channel == null || channel.isBlank() || channel.length() > 64) return false;
        return VALID_CHANNEL.matcher(channel).matches();
    }
}

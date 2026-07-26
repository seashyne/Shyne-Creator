package seashyne.shynecore.client.avatar;

import java.nio.file.Path;

public record AvatarOutfit(
    String id,
    String name,
    Path path,
    int width,
    int height,
    Mode mode,
    boolean valid,
    String problem
) {
    public enum Mode {
        REPLACE,
        OVERLAY
    }
}

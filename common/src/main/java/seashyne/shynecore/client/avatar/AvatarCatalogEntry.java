package seashyne.shynecore.client.avatar;

import java.nio.file.Path;

public record AvatarCatalogEntry(
    String id,
    String name,
    String version,
    String description,
    Path root,
    AvatarManifest manifest,
    boolean valid,
    String problem,
    AvatarValidationReport validation
) {}

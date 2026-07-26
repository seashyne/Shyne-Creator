package seashyne.shynecore.client.ui;

import java.net.URI;
import java.util.List;

public final class ShyneCreatorInfo {
    public static final String CREATOR_NAME = "seashyne";

    private static final List<Link> LINKS = List.of(
        new Link(
            "docs",
            "screen.shyne_core.creator.docs",
            "screen.shyne_core.creator.docs.tooltip",
            "seashyne.github.io",
            URI.create("https://seashyne.github.io/Shyne-Creator/")
        ),
        new Link(
            "source",
            "screen.shyne_core.creator.source",
            "screen.shyne_core.creator.source.tooltip",
            "github.com/seashyne",
            URI.create("https://github.com/seashyne/Shyne-Creator")
        ),
        new Link(
            "downloads",
            "screen.shyne_core.creator.downloads",
            "screen.shyne_core.creator.downloads.tooltip",
            "curseforge.com",
            URI.create("https://www.curseforge.com/minecraft/mc-mods/shyne-creator")
        ),
        new Link(
            "support",
            "screen.shyne_core.creator.support",
            "screen.shyne_core.creator.support.tooltip",
            "github.com/issues",
            URI.create("https://github.com/seashyne/Shyne-Creator/issues")
        )
    );

    private ShyneCreatorInfo() {}

    public static List<Link> links() {
        return LINKS;
    }

    public record Link(
        String id,
        String labelKey,
        String tooltipKey,
        String displayUrl,
        URI uri
    ) {}
}

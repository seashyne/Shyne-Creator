package seashyne.shynecore.client.ui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShyneCreatorInfoTest {
    @Test
    void exposesUniqueOfficialHttpsLinks() {
        assertEquals("seashyne", ShyneCreatorInfo.CREATOR_NAME);
        assertEquals(Set.of("docs", "source", "downloads", "support"),
            ShyneCreatorInfo.links().stream().map(ShyneCreatorInfo.Link::id).collect(java.util.stream.Collectors.toSet()));

        Set<String> urls = new HashSet<>();
        for (ShyneCreatorInfo.Link link : ShyneCreatorInfo.links()) {
            assertEquals("https", link.uri().getScheme());
            assertNotNull(link.uri().getHost());
            assertFalse(link.uri().getHost().isBlank());
            assertFalse(link.displayUrl().isBlank());
            assertTrue(urls.add(link.uri().toString()), "duplicate creator URL: " + link.uri());
        }
    }
}

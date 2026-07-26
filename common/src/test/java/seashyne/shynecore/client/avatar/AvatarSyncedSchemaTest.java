package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarSyncedSchemaTest {
    @TempDir Path temp;

    @Test
    void validatesDeclaredKeysTypesRangesAndNestedArrays() throws Exception {
        Files.writeString(temp.resolve("synced.schema.json"), """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "mood": { "type": "string", "enum": ["calm", "happy"] },
                "energy": { "type": "integer", "minimum": 0, "maximum": 100 },
                "tags": { "type": "array", "maxItems": 2, "items": { "type": "string", "maxLength": 8 } }
              }
            }
            """);

        AvatarSyncedSchema schema = AvatarSyncedSchema.load(temp, "synced.schema.json");
        assertTrue(schema.accepts("mood", "happy"));
        assertTrue(schema.accepts("energy", 42L));
        assertTrue(schema.accepts("tags", java.util.List.of("ears", "tail")));
        assertFalse(schema.accepts("mood", "angry"));
        assertFalse(schema.accepts("energy", 42.5));
        assertFalse(schema.accepts("energy", 101L));
        assertFalse(schema.accepts("unknown", true));
        assertFalse(schema.accepts(Map.of("mood", "calm", "unknown", true)));
    }

    @Test
    void strictSchemaConfiguresSnapshotAllowlist() throws Exception {
        Files.writeString(temp.resolve("strict.json"), """
            { "type":"object", "additionalProperties":false,
              "properties": { "blink": { "type":"boolean" } } }
            """);
        AvatarSyncedSchema schema = AvatarSyncedSchema.load(temp, "strict.json");
        AvatarSyncPolicy policy = new AvatarSyncPolicy();
        policy.configureSyncedSchema(schema.declaredKeys(), schema.allowsAdditional());

        assertEquals(Map.of("blink", true), policy.filterSyncedVars(Map.of("blink", true, "secret", "local")));
    }

    @Test
    void rejectsTraversalAndRemoteReferences() throws Exception {
        assertThrows(java.io.IOException.class, () -> AvatarSyncedSchema.load(temp, "../outside.json"));
        Files.writeString(temp.resolve("ref.json"), "{\"type\":\"object\",\"$ref\":\"https://example.invalid/schema\"}");
        assertThrows(java.io.IOException.class, () -> AvatarSyncedSchema.load(temp, "ref.json"));
    }
}

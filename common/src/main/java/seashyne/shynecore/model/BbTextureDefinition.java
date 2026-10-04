package seashyne.shynecore.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable definition of a texture referenced by a Blockbench model.
 *
 * <p>Supports dual-mode storage: physical relative path on disk and/or
 * direct in-memory decoded PNG bytes.</p>
 */
public record BbTextureDefinition(
    String id,
    String name,
    String relativePath,
    int width,
    int height,
    byte[] embeddedBytes
) {
    public BbTextureDefinition(String id, String name, String relativePath, int width, int height) {
        this(id, name, relativePath, width, height, null);
    }

    public boolean hasEmbeddedBytes() {
        return embeddedBytes != null && embeddedBytes.length > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BbTextureDefinition that)) return false;
        return width == that.width &&
            height == that.height &&
            Objects.equals(id, that.id) &&
            Objects.equals(name, that.name) &&
            Objects.equals(relativePath, that.relativePath) &&
            Arrays.equals(embeddedBytes, that.embeddedBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, name, relativePath, width, height);
        result = 31 * result + Arrays.hashCode(embeddedBytes);
        return result;
    }
}

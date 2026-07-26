package seashyne.shynecore.client.avatar;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AvatarPartState {
    private boolean visible = true;
    private float posX;
    private float posY;
    private float posZ;
    private float rotX;
    private float rotY;
    private float rotZ;
    private float scaleX = 1f;
    private float scaleY = 1f;
    private float scaleZ = 1f;
    private boolean visibilityControlled;
    private boolean positionControlled;
    private boolean rotationControlled;
    private boolean scaleControlled;
    // Kept apart from rotX/Y/Z: this layer is applied after model animation.
    private float additiveRotX;
    private float additiveRotY;
    private float additiveRotZ;
    private boolean additiveRotationControlled;
    // Runtime systems own named layers so native physics, constraints and Lua
    // can compose without overwriting one another's rotation channel.
    private final Map<String, RotationLayer> additiveRotationLayers = new ConcurrentHashMap<>();
    // A runtime override wins over imported Blockbench parent_type until cleared.
    private String vanillaParent = "";
    private boolean vanillaParentControlled;
    private int colorArgb = 0xFFFFFFFF;
    private boolean emissive;
    private boolean renderControlled;

    public boolean visible() { return visible; }
    public boolean setVisible(boolean visible) {
        boolean changed = !visibilityControlled || this.visible != visible;
        visibilityControlled = true;
        this.visible = visible;
        return changed;
    }
    public float posX() { return posX; }
    public float posY() { return posY; }
    public float posZ() { return posZ; }
    public boolean setPosition(float x, float y, float z) {
        x = finite(x); y = finite(y); z = finite(z);
        boolean changed = !positionControlled || !same(this.posX, x) || !same(this.posY, y) || !same(this.posZ, z);
        positionControlled = true;
        this.posX = x; this.posY = y; this.posZ = z;
        return changed;
    }
    public float rotX() { return rotX; }
    public float rotY() { return rotY; }
    public float rotZ() { return rotZ; }
    public boolean setRotation(float x, float y, float z) {
        x = finite(x); y = finite(y); z = finite(z);
        boolean changed = !rotationControlled || !same(this.rotX, x) || !same(this.rotY, y) || !same(this.rotZ, z);
        rotationControlled = true;
        this.rotX = x; this.rotY = y; this.rotZ = z;
        return changed;
    }
    public float scaleX() { return scaleX; }
    public float scaleY() { return scaleY; }
    public float scaleZ() { return scaleZ; }
    public boolean setScale(float x, float y, float z) {
        x = finite(x); y = finite(y); z = finite(z);
        boolean changed = !scaleControlled || !same(this.scaleX, x) || !same(this.scaleY, y) || !same(this.scaleZ, z);
        scaleControlled = true;
        this.scaleX = x; this.scaleY = y; this.scaleZ = z;
        return changed;
    }

    public boolean visibilityControlled() { return visibilityControlled; }
    public boolean positionControlled() { return positionControlled; }
    public boolean rotationControlled() { return rotationControlled; }
    public boolean scaleControlled() { return scaleControlled; }
    /** Rotation layered on top of Blockbench animation and direct {@code rot()} values. */
    public float additiveRotX() { return additiveRotX + layerSum(Axis.X); }
    public float additiveRotY() { return additiveRotY + layerSum(Axis.Y); }
    public float additiveRotZ() { return additiveRotZ + layerSum(Axis.Z); }
    public boolean additiveRotationControlled() { return additiveRotationControlled || !additiveRotationLayers.isEmpty(); }
    public boolean setAdditiveRotation(float x, float y, float z) {
        x = finite(x); y = finite(y); z = finite(z);
        boolean changed = !additiveRotationControlled || !same(additiveRotX, x) || !same(additiveRotY, y) || !same(additiveRotZ, z);
        additiveRotationControlled = true;
        additiveRotX = x; additiveRotY = y; additiveRotZ = z;
        return changed;
    }
    public boolean setAdditiveRotationLayer(String layer, float x, float y, float z) {
        String key = normalizeLayer(layer);
        RotationLayer next = new RotationLayer(finite(x), finite(y), finite(z));
        RotationLayer previous = additiveRotationLayers.put(key, next);
        return previous == null || !previous.same(next);
    }
    public boolean clearAdditiveRotationLayer(String layer) {
        return additiveRotationLayers.remove(normalizeLayer(layer)) != null;
    }
    public Map<String, float[]> additiveRotationLayers() {
        java.util.LinkedHashMap<String, float[]> result = new java.util.LinkedHashMap<>();
        additiveRotationLayers.forEach((key, value) -> result.put(key, new float[] {value.x, value.y, value.z}));
        return Map.copyOf(result);
    }
    public String vanillaParent() { return vanillaParent; }
    public boolean vanillaParentControlled() { return vanillaParentControlled; }
    public boolean setVanillaParent(String value) {
        return setVanillaParent(value, "full");
    }
    public boolean setVanillaParent(String value, String mode) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT).replace("_", "");
        String normalizedMode = normalizeAttachmentMode(mode);
        boolean changed = !vanillaParentControlled || !vanillaParent.equals(normalized) || !vanillaAttachmentMode.equals(normalizedMode);
        vanillaParentControlled = true;
        vanillaParent = normalized;
        vanillaAttachmentMode = normalizedMode;
        return changed;
    }
    public String vanillaAttachmentMode() { return vanillaAttachmentMode; }
    public boolean clearVanillaParent() {
        boolean changed = vanillaParentControlled || !vanillaParent.isEmpty() || !vanillaAttachmentMode.equals("full");
        vanillaParentControlled = false;
        vanillaParent = "";
        vanillaAttachmentMode = "full";
        return changed;
    }
    public int colorArgb() { return colorArgb; }
    public boolean emissive() { return emissive; }
    public boolean renderControlled() { return renderControlled; }
    public boolean setColor(float red, float green, float blue) {
        int next = (colorArgb & 0xFF000000) | (channel(red) << 16) | (channel(green) << 8) | channel(blue);
        boolean changed = !renderControlled || colorArgb != next; renderControlled = true; colorArgb = next; return changed;
    }
    public boolean setOpacity(float alpha) {
        int next = (channel(alpha) << 24) | (colorArgb & 0x00FFFFFF);
        boolean changed = !renderControlled || colorArgb != next; renderControlled = true; colorArgb = next; return changed;
    }
    public boolean setEmissive(boolean value) {
        boolean changed = !renderControlled || emissive != value; renderControlled = true; emissive = value; return changed;
    }
    public void setRenderState(int colorArgb, boolean emissive) { this.colorArgb = colorArgb; this.emissive = emissive; this.renderControlled = true; }

    public boolean sameValues(AvatarPartState other) {
        return other != null && visible == other.visible
            && visibilityControlled == other.visibilityControlled
            && positionControlled == other.positionControlled
            && rotationControlled == other.rotationControlled
            && scaleControlled == other.scaleControlled
            && additiveRotationControlled == other.additiveRotationControlled
            && additiveRotationLayers.equals(other.additiveRotationLayers)
            && vanillaParentControlled == other.vanillaParentControlled && vanillaParent.equals(other.vanillaParent) && vanillaAttachmentMode.equals(other.vanillaAttachmentMode)
            && colorArgb == other.colorArgb && emissive == other.emissive && renderControlled == other.renderControlled
            && same(posX, other.posX) && same(posY, other.posY) && same(posZ, other.posZ)
            && same(rotX, other.rotX) && same(rotY, other.rotY) && same(rotZ, other.rotZ)
            && same(scaleX, other.scaleX) && same(scaleY, other.scaleY) && same(scaleZ, other.scaleZ)
            && same(additiveRotX, other.additiveRotX) && same(additiveRotY, other.additiveRotY) && same(additiveRotZ, other.additiveRotZ);
    }

    private static boolean same(float left, float right) { return Math.abs(left - right) <= 0.0001f; }
    private static int channel(float value) { return Math.max(0, Math.min(255, Math.round(value * 255f))); }

    public AvatarPartState copy() {
        AvatarPartState copy = new AvatarPartState();
        copy.visible = this.visible;
        copy.posX = this.posX;
        copy.posY = this.posY;
        copy.posZ = this.posZ;
        copy.rotX = this.rotX;
        copy.rotY = this.rotY;
        copy.rotZ = this.rotZ;
        copy.scaleX = this.scaleX;
        copy.scaleY = this.scaleY;
        copy.scaleZ = this.scaleZ;
        copy.visibilityControlled = this.visibilityControlled;
        copy.positionControlled = this.positionControlled;
        copy.rotationControlled = this.rotationControlled;
        copy.scaleControlled = this.scaleControlled;
        copy.additiveRotX = this.additiveRotX;
        copy.additiveRotY = this.additiveRotY;
        copy.additiveRotZ = this.additiveRotZ;
        copy.additiveRotationControlled = this.additiveRotationControlled;
        copy.additiveRotationLayers.putAll(this.additiveRotationLayers);
        copy.vanillaParent = this.vanillaParent;
        copy.vanillaParentControlled = this.vanillaParentControlled;
        copy.vanillaAttachmentMode = this.vanillaAttachmentMode;
        copy.colorArgb = this.colorArgb;
        copy.emissive = this.emissive;
        copy.renderControlled = this.renderControlled;
        return copy;
    }

    /**
     * Produces a render-only pose between two network snapshots. Discrete state
     * (visibility, material and parent binding) comes from {@code next}; only
     * controlled transform channels are smoothed.
     */
    public static AvatarPartState interpolate(AvatarPartState previous, AvatarPartState next, float alpha) {
        if (next == null) return previous == null ? null : previous.copy();
        if (previous == null || alpha >= 1f) return next.copy();
        float t = Math.max(0f, alpha);
        AvatarPartState result = next.copy();
        if (next.positionControlled) {
            result.posX = lerp(previous.posX, next.posX, t);
            result.posY = lerp(previous.posY, next.posY, t);
            result.posZ = lerp(previous.posZ, next.posZ, t);
        }
        if (next.rotationControlled) {
            result.rotX = lerpAngle(previous.rotX, next.rotX, t);
            result.rotY = lerpAngle(previous.rotY, next.rotY, t);
            result.rotZ = lerpAngle(previous.rotZ, next.rotZ, t);
        }
        if (next.scaleControlled) {
            result.scaleX = lerp(previous.scaleX, next.scaleX, t);
            result.scaleY = lerp(previous.scaleY, next.scaleY, t);
            result.scaleZ = lerp(previous.scaleZ, next.scaleZ, t);
        }
        if (next.additiveRotationControlled) {
            result.additiveRotX = lerpAngle(previous.additiveRotX(), next.additiveRotX(), t);
            result.additiveRotY = lerpAngle(previous.additiveRotY(), next.additiveRotY(), t);
            result.additiveRotZ = lerpAngle(previous.additiveRotZ(), next.additiveRotZ(), t);
            result.additiveRotationLayers.clear();
        }
        return result;
    }

    private String vanillaAttachmentMode = "full";
    private float layerSum(Axis axis) {
        float result = 0f;
        for (RotationLayer layer : additiveRotationLayers.values()) {
            result += switch (axis) { case X -> layer.x; case Y -> layer.y; case Z -> layer.z; };
        }
        return result;
    }
    private static String normalizeLayer(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("rotation layer name cannot be blank");
        return value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
    private static float finite(float value) { return Float.isFinite(value) ? value : 0f; }
    private static float lerp(float from, float to, float alpha) { return from + (to - from) * alpha; }
    private static float lerpAngle(float from, float to, float alpha) {
        float delta = (to - from) % 360f;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return from + delta * alpha;
    }
    private enum Axis { X, Y, Z }
    private record RotationLayer(float x, float y, float z) {
        private boolean same(RotationLayer other) {
            return other != null && AvatarPartState.same(x, other.x) && AvatarPartState.same(y, other.y) && AvatarPartState.same(z, other.z);
        }
    }
    private static String normalizeAttachmentMode(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "")) {
            case "position", "pos", "translation" -> "position";
            case "rotation", "rot", "orientation" -> "rotation";
            default -> "full";
        };
    }
}

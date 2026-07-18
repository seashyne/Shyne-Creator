package seashyne.shynecore.client.avatar;

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
        boolean changed = !positionControlled || !same(this.posX, x) || !same(this.posY, y) || !same(this.posZ, z);
        positionControlled = true;
        this.posX = x; this.posY = y; this.posZ = z;
        return changed;
    }
    public float rotX() { return rotX; }
    public float rotY() { return rotY; }
    public float rotZ() { return rotZ; }
    public boolean setRotation(float x, float y, float z) {
        boolean changed = !rotationControlled || !same(this.rotX, x) || !same(this.rotY, y) || !same(this.rotZ, z);
        rotationControlled = true;
        this.rotX = x; this.rotY = y; this.rotZ = z;
        return changed;
    }
    public float scaleX() { return scaleX; }
    public float scaleY() { return scaleY; }
    public float scaleZ() { return scaleZ; }
    public boolean setScale(float x, float y, float z) {
        boolean changed = !scaleControlled || !same(this.scaleX, x) || !same(this.scaleY, y) || !same(this.scaleZ, z);
        scaleControlled = true;
        this.scaleX = x; this.scaleY = y; this.scaleZ = z;
        return changed;
    }

    public boolean visibilityControlled() { return visibilityControlled; }
    public boolean positionControlled() { return positionControlled; }
    public boolean rotationControlled() { return rotationControlled; }
    public boolean scaleControlled() { return scaleControlled; }
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
            && colorArgb == other.colorArgb && emissive == other.emissive && renderControlled == other.renderControlled
            && same(posX, other.posX) && same(posY, other.posY) && same(posZ, other.posZ)
            && same(rotX, other.rotX) && same(rotY, other.rotY) && same(rotZ, other.rotZ)
            && same(scaleX, other.scaleX) && same(scaleY, other.scaleY) && same(scaleZ, other.scaleZ);
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
        copy.colorArgb = this.colorArgb;
        copy.emissive = this.emissive;
        copy.renderControlled = this.renderControlled;
        return copy;
    }
}

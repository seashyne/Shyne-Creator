package seashyne.shynecore.model;

/** Cubic Bezier handle offsets for the X, Y and Z axes of a keyframe. */
public record BbBezierData(
    float leftTimeX, float leftTimeY, float leftTimeZ,
    float leftValueX, float leftValueY, float leftValueZ,
    float rightTimeX, float rightTimeY, float rightTimeZ,
    float rightValueX, float rightValueY, float rightValueZ
) {
    public static final BbBezierData NONE = new BbBezierData(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public float leftTime(int axis) { return axis == 0 ? leftTimeX : axis == 1 ? leftTimeY : leftTimeZ; }
    public float leftValue(int axis) { return axis == 0 ? leftValueX : axis == 1 ? leftValueY : leftValueZ; }
    public float rightTime(int axis) { return axis == 0 ? rightTimeX : axis == 1 ? rightTimeY : rightTimeZ; }
    public float rightValue(int axis) { return axis == 0 ? rightValueX : axis == 1 ? rightValueY : rightValueZ; }
}

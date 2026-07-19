package seashyne.shynecore.model;

public record BbKeyframe(
    float time,
    BbKeyframePoint pre,
    BbKeyframePoint post,
    String easing,
    BbBezierData bezier
) {
    public BbKeyframe {
        pre = pre == null ? BbKeyframePoint.numeric(0, 0, 0) : pre;
        post = post == null ? pre : post;
        easing = easing == null || easing.isBlank() ? "linear" : easing;
        bezier = bezier == null ? BbBezierData.NONE : bezier;
    }

    /** Keeps source compatibility for numeric keyframes and network payloads. */
    public BbKeyframe(float time, float x, float y, float z, String easing) {
        this(time, BbKeyframePoint.numeric(x, y, z), BbKeyframePoint.numeric(x, y, z), easing, BbBezierData.NONE);
    }

    public float x() { return pre.numericAxis(0, 0); }
    public float y() { return pre.numericAxis(1, 0); }
    public float z() { return pre.numericAxis(2, 0); }
}

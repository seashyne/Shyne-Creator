package seashyne.shynecore.client.avatar;

public record AvatarNameplateStyle(String badge, int colorArgb, boolean bold, boolean italic) {
    public static final AvatarNameplateStyle DEFAULT = new AvatarNameplateStyle("", 0xFFFFFFFF, false, false);

    public AvatarNameplateStyle {
        badge = badge == null ? "" : badge.substring(0, Math.min(24, badge.length()));
        colorArgb = 0xFF000000 | (colorArgb & 0x00FFFFFF);
    }
}

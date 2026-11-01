package seashyne.shynecore.client.avatar;

import java.util.function.IntConsumer;

public final class AvatarAction {
    private final String id;
    private final String title;
    private final String description;
    private final String page;
    private final String icon;
    private final boolean localOnly;
    private final boolean closeOnUse;
    private final Runnable callback;
    private final Runnable secondaryCallback;
    private final IntConsumer scrollCallback;

    private final boolean isToggle;
    private boolean toggled;
    private final Integer color;
    private final Integer hoverColor;
    private final Integer toggleColor;
    private final int slotIndex;
    private final String texture;
    private final int texU;
    private final int texV;
    private final int texW;
    private final int texH;
    private final float texScale;

    public AvatarAction(String title, String page, Runnable callback) {
        this(title, title, "", page, "spark", false, true, callback, null);
    }

    public AvatarAction(String id, String title, String description, String page, boolean localOnly, boolean closeOnUse, Runnable callback) {
        this(id, title, description, page, "", localOnly, closeOnUse, callback, null);
    }

    public AvatarAction(String id, String title, String description, String page, String icon, boolean localOnly, boolean closeOnUse, Runnable callback) {
        this(id, title, description, page, icon, localOnly, closeOnUse, callback, null);
    }

    public AvatarAction(String id, String title, String description, String page, String icon, boolean localOnly, boolean closeOnUse, Runnable callback, Runnable secondaryCallback) {
        this(id, title, description, page, icon, localOnly, closeOnUse, false, false, null, null, callback, secondaryCallback);
    }

    public AvatarAction(String id, String title, String description, String page, String icon,
                        boolean localOnly, boolean closeOnUse, boolean isToggle, boolean toggled,
                        Integer color, Integer hoverColor,
                        Runnable callback, Runnable secondaryCallback) {
        this(
            id, title, description, page, icon,
            localOnly, closeOnUse, isToggle, toggled,
            color, hoverColor, null,
            -1, "", 0, 0, 0, 0, 1.0f,
            callback, secondaryCallback, null
        );
    }

    public AvatarAction(String id, String title, String description, String page, String icon,
                        boolean localOnly, boolean closeOnUse, boolean isToggle, boolean toggled,
                        Integer color, Integer hoverColor, Integer toggleColor,
                        int slotIndex, String texture, int texU, int texV, int texW, int texH, float texScale,
                        Runnable callback, Runnable secondaryCallback, IntConsumer scrollCallback) {
        String safeTitle = title == null ? "" : title;
        this.id = id == null || id.isBlank() ? (safeTitle.isBlank() ? "action" : safeTitle) : id;
        this.title = safeTitle;
        this.description = description == null ? "" : description;
        this.page = page == null || page.isBlank() ? "main" : page;
        this.icon = icon == null ? "" : icon.trim();
        this.localOnly = localOnly;
        this.closeOnUse = closeOnUse;
        this.isToggle = isToggle;
        this.toggled = toggled;
        this.color = color;
        this.hoverColor = hoverColor;
        this.toggleColor = toggleColor;
        this.slotIndex = slotIndex;
        this.texture = texture == null ? "" : texture.trim();
        this.texU = texU;
        this.texV = texV;
        this.texW = texW;
        this.texH = texH;
        this.texScale = Float.isFinite(texScale) && texScale > 0f ? texScale : 1.0f;
        this.callback = callback == null ? () -> {} : callback;
        this.secondaryCallback = secondaryCallback;
        this.scrollCallback = scrollCallback;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String displayTitle() { return title.isBlank() ? id : title; }
    public String description() { return description; }
    public String page() { return page; }
    public String icon() { return icon; }
    public boolean localOnly() { return localOnly; }
    public boolean closeOnUse() { return closeOnUse; }
    public boolean isToggle() { return isToggle; }
    public boolean isToggled() { return toggled; }
    public void setToggled(boolean toggled) { this.toggled = toggled; }
    public Integer color() { return color; }
    public Integer hoverColor() { return hoverColor; }
    public Integer toggleColor() { return toggleColor; }
    public int slotIndex() { return slotIndex; }
    public String texture() { return texture; }
    public boolean hasTexture() { return !texture.isEmpty(); }
    public int texU() { return texU; }
    public int texV() { return texV; }
    public int texW() { return texW; }
    public int texH() { return texH; }
    public float texScale() { return texScale; }
    public Runnable callback() { return callback; }
    public Runnable secondaryCallback() { return secondaryCallback; }
    public boolean hasSecondaryCallback() { return secondaryCallback != null; }
    public IntConsumer scrollCallback() { return scrollCallback; }
    public boolean hasScrollCallback() { return scrollCallback != null; }
    public void onScroll(int direction) {
        if (scrollCallback != null) {
            scrollCallback.accept(direction);
        }
    }
}

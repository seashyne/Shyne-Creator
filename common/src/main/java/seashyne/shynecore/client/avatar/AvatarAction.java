package seashyne.shynecore.client.avatar;

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

    private final boolean isToggle;
    private boolean toggled;
    private final Integer color;
    private final Integer hoverColor;

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
        this.id = id == null || id.isBlank() ? title : id;
        this.title = title == null || title.isBlank() ? "Action" : title;
        this.description = description == null ? "" : description;
        this.page = page == null || page.isBlank() ? "main" : page;
        this.icon = icon == null ? "" : icon.trim();
        this.localOnly = localOnly;
        this.closeOnUse = closeOnUse;
        this.isToggle = isToggle;
        this.toggled = toggled;
        this.color = color;
        this.hoverColor = hoverColor;
        this.callback = callback == null ? () -> {} : callback;
        this.secondaryCallback = secondaryCallback;
    }

    public String id() { return id; }
    public String title() { return title; }
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
    public Runnable callback() { return callback; }
    public Runnable secondaryCallback() { return secondaryCallback; }
    public boolean hasSecondaryCallback() { return secondaryCallback != null; }
}

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

    public AvatarAction(String title, String page, Runnable callback) {
        this(title, title, "", page, "spark", false, true, callback);
    }

    public AvatarAction(String id, String title, String description, String page, boolean localOnly, boolean closeOnUse, Runnable callback) {
        this(id, title, description, page, "", localOnly, closeOnUse, callback);
    }

    public AvatarAction(String id, String title, String description, String page, String icon, boolean localOnly, boolean closeOnUse, Runnable callback) {
        this.id = id == null || id.isBlank() ? title : id;
        this.title = title == null || title.isBlank() ? "Action" : title;
        this.description = description == null ? "" : description;
        this.page = page == null || page.isBlank() ? "main" : page;
        this.icon = icon == null ? "" : icon.trim();
        this.localOnly = localOnly;
        this.closeOnUse = closeOnUse;
        this.callback = callback == null ? () -> {} : callback;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String description() { return description; }
    public String page() { return page; }
    public String icon() { return icon; }
    public boolean localOnly() { return localOnly; }
    public boolean closeOnUse() { return closeOnUse; }
    public Runnable callback() { return callback; }
}

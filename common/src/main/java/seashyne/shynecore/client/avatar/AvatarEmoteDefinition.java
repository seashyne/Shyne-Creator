package seashyne.shynecore.client.avatar;

public record AvatarEmoteDefinition(
    String id,
    String animation,
    String title,
    String description,
    String page,
    boolean loop,
    boolean localOnly,
    boolean closeOnUse
) {}

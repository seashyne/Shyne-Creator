package seashyne.shynecore.client.avatar.linter;

/**
 * Represents a single issue detected by the avatar linter during script or project analysis.
 */
public record AvatarLintIssue(
    Severity severity,
    String file,
    int line,
    String code,
    String message,
    String recommendation
) {
    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public String formatted() {
        String loc = (file != null && !file.isBlank())
            ? (line > 0 ? file + ":" + line : file)
            : "project";
        return "[" + severity + "] (" + code + ") " + loc + " - " + message +
            (recommendation != null && !recommendation.isBlank() ? " -> Suggestion: " + recommendation : "");
    }
}

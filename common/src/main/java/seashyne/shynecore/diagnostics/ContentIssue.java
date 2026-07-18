package seashyne.shynecore.diagnostics;

public record ContentIssue(
    String sourceType,
    String sourceId,
    String path,
    DiagnosticSeverity severity,
    String message,
    String hint,
    long timestamp
) {
    public String formatForHumans() {
        StringBuilder line = new StringBuilder()
            .append("[").append(severity).append("] ")
            .append(sourceType);
        if (sourceId != null && !sourceId.isBlank()) line.append(" (").append(sourceId).append(")");
        line.append(": ").append(message);
        if (path != null && !path.isBlank()) line.append(" @ ").append(path);
        if (hint != null && !hint.isBlank()) line.append(" | hint: ").append(hint);
        return line.toString();
    }
}

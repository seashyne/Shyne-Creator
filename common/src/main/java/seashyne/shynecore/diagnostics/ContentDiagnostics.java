package seashyne.shynecore.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import seashyne.shynecore.ShyneCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ContentDiagnostics {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path reportDir;
    private final Path jsonReportPath;
    private final Path textReportPath;
    private final List<ContentIssue> issues = new CopyOnWriteArrayList<>();

    public ContentDiagnostics(Path reportDir) {
        this.reportDir = reportDir;
        this.jsonReportPath = reportDir.resolve("content-report.json");
        this.textReportPath = reportDir.resolve("content-report.txt");
    }

    public void clearSource(String sourceType) {
        issues.removeIf(issue -> issue.sourceType().equalsIgnoreCase(sourceType));
        writeReports();
    }

    public void clearAll() {
        issues.clear();
        writeReports();
    }

    public void info(String sourceType, String sourceId, Path path, String message, String hint) {
        addIssue(sourceType, sourceId, path, DiagnosticSeverity.INFO, message, hint);
    }

    public void warn(String sourceType, String sourceId, Path path, String message, String hint) {
        addIssue(sourceType, sourceId, path, DiagnosticSeverity.WARN, message, hint);
    }

    public void error(String sourceType, String sourceId, Path path, String message, String hint) {
        addIssue(sourceType, sourceId, path, DiagnosticSeverity.ERROR, message, hint);
    }

    public List<ContentIssue> snapshot() {
        return issues.stream()
            .sorted(Comparator.comparingLong(ContentIssue::timestamp).reversed())
            .toList();
    }

    public List<ContentIssue> recent(int limit) {
        List<ContentIssue> ordered = snapshot();
        return ordered.subList(0, Math.min(limit, ordered.size()));
    }

    public long count(DiagnosticSeverity severity) {
        return issues.stream().filter(issue -> issue.severity() == severity).count();
    }

    public String summary() {
        return String.format(Locale.ROOT, "issues=%d errors=%d warns=%d info=%d",
            issues.size(), count(DiagnosticSeverity.ERROR), count(DiagnosticSeverity.WARN), count(DiagnosticSeverity.INFO));
    }

    public Path jsonReportPath() {
        return jsonReportPath;
    }

    public Path textReportPath() {
        return textReportPath;
    }

    private void addIssue(String sourceType, String sourceId, Path path, DiagnosticSeverity severity, String message, String hint) {
        issues.add(new ContentIssue(
            sourceType == null ? "unknown" : sourceType,
            sourceId == null ? "" : sourceId,
            path == null ? "" : path.toString(),
            severity,
            message == null ? "" : message,
            hint == null ? "" : hint,
            System.currentTimeMillis()
        ));
        writeReports();
    }

    private synchronized void writeReports() {
        try {
            Files.createDirectories(reportDir);
            List<ContentIssue> ordered = new ArrayList<>(snapshot());
            Files.writeString(jsonReportPath, GSON.toJson(ordered));

            StringBuilder human = new StringBuilder();
            human.append("Shyne Creator Content Report").append(System.lineSeparator());
            human.append("Generated: ").append(Instant.now()).append(System.lineSeparator());
            human.append(summary()).append(System.lineSeparator()).append(System.lineSeparator());
            for (ContentIssue issue : ordered) {
                human.append(issue.formatForHumans()).append(System.lineSeparator());
            }
            Files.writeString(textReportPath, human.toString());
        } catch (IOException e) {
            ShyneCore.LOGGER.warn("[Diagnostics] Could not write report files: {}", e.getMessage());
        }
    }
}

package seashyne.shynecore.client.avatar;

import java.nio.file.Path;
import java.util.List;

public record AvatarValidationReport(
    Path root,
    AvatarManifest manifest,
    List<Issue> issues,
    Stats stats
) {
    public AvatarValidationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
        stats = stats == null ? Stats.EMPTY : stats;
    }

    public boolean valid() { return errorCount() == 0; }
    public long errorCount() { return issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count(); }
    public long warningCount() { return issues.stream().filter(issue -> issue.severity() == Severity.WARNING).count(); }
    public String firstProblem() {
        return issues.stream().filter(issue -> issue.severity() == Severity.ERROR).map(Issue::message).findFirst()
            .orElseGet(() -> issues.stream().filter(issue -> issue.severity() == Severity.WARNING).map(Issue::message).findFirst().orElse(""));
    }
    public String summary() {
        if (!valid()) return errorCount() + " error(s), " + warningCount() + " warning(s)";
        if (warningCount() > 0) return "Valid with " + warningCount() + " warning(s)";
        return "Valid";
    }

    public enum Severity { ERROR, WARNING }
    public record Issue(Severity severity, String code, String message, String file) {}
    public record Stats(int files, long totalBytes, int luaFiles, int bones, int cubes, int animations, int textures) {
        public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0, 0);
    }
}

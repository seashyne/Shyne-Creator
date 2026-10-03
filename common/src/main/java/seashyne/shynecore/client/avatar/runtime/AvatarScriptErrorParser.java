package seashyne.shynecore.client.avatar.runtime;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Lua exceptions (from compilation or runtime execution) into structured,
 * line-precise error information for creator hot-reload and diagnostics.
 */
public final class AvatarScriptErrorParser {

    private static final Pattern LUA_ERROR_PATTERN = Pattern.compile(
        "(?:^\\[string\\s*\"([^\"]+)\"\\]|([a-zA-Z0-9_./\\\\-]+\\.lua|\\w+)):(\\d+):?\\s*(.*)",
        Pattern.DOTALL
    );

    private static final Pattern GENERIC_LINE_PATTERN = Pattern.compile(
        "\\b(?:line|at line)\\s*(\\d+)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public record ScriptErrorInfo(String file, int line, String cleanMessage, String formatted) {
        public ScriptErrorInfo {
            file = (file == null || file.isBlank()) ? "avatar.lua" : file;
            cleanMessage = (cleanMessage == null || cleanMessage.isBlank()) ? "Unknown error" : cleanMessage;
            if (formatted == null || formatted.isBlank()) {
                formatted = line > 0 ? file + ":" + line + " - " + cleanMessage : file + " - " + cleanMessage;
            }
        }
    }

    private AvatarScriptErrorParser() {}

    /**
     * Parses a Throwable error from Lua compilation or execution.
     *
     * @param error the caught exception
     * @param defaultFileName the fallback filename (e.g. main.lua)
     * @return structured ScriptErrorInfo
     */
    public static ScriptErrorInfo parse(Throwable error, String defaultFileName) {
        if (error == null) {
            return new ScriptErrorInfo(defaultFileName, -1, "Unknown error", null);
        }

        String raw = error.getMessage();
        if (raw == null || raw.isBlank()) {
            raw = error.toString();
        }

        // Clean out leading carriage returns and extract the first logical line/chunk
        String firstLine = raw.trim();
        int nlIndex = firstLine.indexOf('\n');
        if (nlIndex > 0) {
            firstLine = firstLine.substring(0, nlIndex).trim();
        }

        Matcher matcher = LUA_ERROR_PATTERN.matcher(firstLine);
        if (matcher.find()) {
            String matchedFile = matcher.group(1);
            if (matchedFile == null || matchedFile.isBlank()) {
                matchedFile = matcher.group(2);
            }
            String fileName = sanitizeFileName(matchedFile, defaultFileName);

            int line = -1;
            try {
                line = Integer.parseInt(matcher.group(3));
            } catch (NumberFormatException ignored) {}

            String msg = matcher.group(4);
            if (msg == null || msg.isBlank()) {
                msg = extractDescription(raw);
            } else {
                msg = sanitizeDescription(msg);
            }

            String formatted = line > 0 ? fileName + ":" + line + " - " + msg : fileName + " - " + msg;
            return new ScriptErrorInfo(fileName, line, msg, formatted);
        }

        // Fallback: check for generic line pattern
        Matcher lineMatcher = GENERIC_LINE_PATTERN.matcher(raw);
        int line = -1;
        if (lineMatcher.find()) {
            try {
                line = Integer.parseInt(lineMatcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        String clean = sanitizeDescription(firstLine);
        String fileName = sanitizeFileName(defaultFileName, "avatar.lua");
        String formatted = line > 0 ? fileName + ":" + line + " - " + clean : clean;
        return new ScriptErrorInfo(fileName, line, clean, formatted);
    }

    private static String sanitizeFileName(String pathStr, String defaultFileName) {
        if (pathStr == null || pathStr.isBlank()) {
            return (defaultFileName == null || defaultFileName.isBlank()) ? "avatar.lua" : defaultFileName;
        }
        try {
            return Path.of(pathStr).getFileName().toString();
        } catch (Exception e) {
            int lastSlash = Math.max(pathStr.lastIndexOf('/'), pathStr.lastIndexOf('\\'));
            return lastSlash >= 0 ? pathStr.substring(lastSlash + 1) : pathStr;
        }
    }

    private static String sanitizeDescription(String desc) {
        if (desc == null) return "Script error";
        String s = desc.trim();
        int traceback = s.toLowerCase().indexOf("stack traceback");
        if (traceback >= 0) {
            s = s.substring(0, traceback).trim();
        }
        int nl = s.indexOf('\n');
        if (nl >= 0) {
            s = s.substring(0, nl).trim();
        }
        // Strip trailing colon or dot if left over
        if (s.endsWith(":")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s.isBlank() ? "Script error" : s;
    }

    private static String extractDescription(String raw) {
        String clean = sanitizeDescription(raw);
        int colon = clean.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < clean.length()) {
            return clean.substring(colon + 1).trim();
        }
        return clean;
    }
}

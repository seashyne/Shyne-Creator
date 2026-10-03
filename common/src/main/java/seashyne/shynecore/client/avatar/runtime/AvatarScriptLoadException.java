package seashyne.shynecore.client.avatar.runtime;

import java.io.IOException;

/**
 * Thrown when an avatar Lua script fails compilation or execution during loading.
 * Preserves the exact file name and line number where the fault occurred.
 */
public final class AvatarScriptLoadException extends IOException {
    private final String file;
    private final int line;
    private final String cleanMessage;
    private final String formatted;

    public AvatarScriptLoadException(String file, int line, String cleanMessage, String formatted) {
        super(formatted);
        this.file = file == null || file.isBlank() ? "avatar.lua" : file;
        this.line = line;
        this.cleanMessage = cleanMessage == null ? "Script execution failed" : cleanMessage;
        this.formatted = formatted == null ? (this.file + ":" + this.line + " - " + this.cleanMessage) : formatted;
    }

    public String file() { return file; }
    public int line() { return line; }
    public String cleanMessage() { return cleanMessage; }
    public String formatted() { return formatted; }
}

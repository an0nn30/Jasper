package dev.jasper.app.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Plain text only; locations are 1-based, or both zero when unavailable. */
public record ConfigDiagnostic(Severity severity, Path file, int line, int column, String key, String message) {
    public enum Severity { WARNING, ERROR }

    public ConfigDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(message, "message");
        if (line < 0 || column < 0 || (line == 0) != (column == 0)) {
            throw new IllegalArgumentException("Diagnostic position must be positive or unavailable.");
        }
    }

    public String formatted() {
        String location = file + (line == 0 ? "" : ":" + line + ":" + column);
        return location + ": " + severity.name().toLowerCase(Locale.ROOT)
            + (key.isEmpty() ? "" : " [" + key + "]") + ": " + message;
    }
}

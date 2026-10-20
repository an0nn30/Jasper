package dev.jasper.ui.theme;

/** A theme file Jasper cannot use. The message starts with the origin and, when known, the 1-based line. */
public final class ThemeFileException extends IllegalArgumentException {
    private final String origin;
    private final int line;
    private final String detail;

    /** Creates a diagnostic with its source origin and optional 1-based line. */
    public ThemeFileException(String origin, int line, String message) {
        super(origin + (line > 0 ? ":" + line : "") + ": " + message);
        this.origin = origin;
        this.line = line;
        this.detail = message;
    }

    /** The file name or resource the problem is in. */
    public String origin() { return origin; }

    /** The 1-based line, or 0 when unknown. */
    public int line() { return line; }

    /** The problem without the origin and line, for callers that show those separately. */
    public String detail() { return detail; }
}

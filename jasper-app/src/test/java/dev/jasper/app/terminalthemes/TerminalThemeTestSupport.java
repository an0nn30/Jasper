package dev.jasper.app.terminalthemes;

/** Owner-package bridge for the moved pure parser tests. */
public final class TerminalThemeTestSupport {
    public static final String NESTED = "x = " + "[".repeat(25_000) + "]".repeat(25_000) + "\n";
    private TerminalThemeTestSupport() {}
    public static java.util.List<String> bundledFiles() { return TerminalThemeCatalog.bundledFiles(); }
    public static String bundledText(String file) { return TerminalThemeCatalog.bundledText(file); }
}

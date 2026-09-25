package dev.jasper.app.appearance;

/** Package-local test access, excluded from production artifacts. */
public final class ThemeTestSupport {
    public static boolean install(Theme theme) { return ThemeManager.install(theme); }
}

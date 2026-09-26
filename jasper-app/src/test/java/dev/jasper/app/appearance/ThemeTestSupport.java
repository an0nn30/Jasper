package dev.jasper.app.appearance;

/** Package-local test access, excluded from production artifacts. */
public final class ThemeTestSupport {
    public static boolean install(Theme theme) { return ThemeManager.install(theme); }

    /** A built-in theme by name, or Jasper Dark: what start-up picks for a name no theme has. */
    public static Theme named(String name) { return ThemeCatalog.builtIn().latest().find(name).orElse(Theme.DARK); }
}

package dev.jasper.ui.theme;

/** A view's explicit appearance owner. */
public enum ThemeRoute {
    CHROME, WORKSPACE;
    public GeneratedTheme select(ThemePair pair) { return this == CHROME ? pair.chrome() : pair.workspace(); }
}

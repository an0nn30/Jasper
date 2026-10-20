package dev.jasper.ui.theme;

/** A view's explicit appearance owner. */
public enum ThemeRoute {
    CHROME, WORKSPACE,
    /** The window header: the chrome's {@link TitleBarTheme}, as the header's own controls paint. */
    TITLE_BAR;

    public GeneratedTheme select(ThemePair pair) {
        return switch (this) {
            case CHROME -> pair.chrome();
            case WORKSPACE -> pair.workspace();
            case TITLE_BAR -> TitleBarTheme.of(pair.chrome());
        };
    }
}

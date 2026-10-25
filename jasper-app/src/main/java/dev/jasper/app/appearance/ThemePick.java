package dev.jasper.app.appearance;

import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.TerminalScheme;
import java.util.Objects;

/** A saved theme identity: an authored theme or the desktop's runtime interface colors. */
public sealed interface ThemePick permits ThemePick.Authored, ThemePick.Desktop {
    String id();
    GeneratedTheme generated(boolean dark, GeneratedTheme desktop);
    TerminalScheme matching(boolean dark);

    /** An installed authored theme, including its own content colors. */
    record Authored(JasperTheme theme) implements ThemePick {
        public Authored { Objects.requireNonNull(theme, "theme"); }
        @Override public String id() { return theme.id(); }
        @Override public GeneratedTheme generated(boolean dark, GeneratedTheme desktop) { return theme.generated(dark); }
        @Override public TerminalScheme matching(boolean dark) { return theme.matching(dark); }
    }

    /** Runtime interface colors with Slate Cobalt's existing matching terminal schemes. */
    record Desktop() implements ThemePick {
        @Override public String id() { return "desktop-colors"; }
        @Override public GeneratedTheme generated(boolean dark, GeneratedTheme desktop) { return Objects.requireNonNull(desktop, "desktop"); }
        @Override public TerminalScheme matching(boolean dark) { return JasperTheme.bundled().matching(dark); }
    }
}

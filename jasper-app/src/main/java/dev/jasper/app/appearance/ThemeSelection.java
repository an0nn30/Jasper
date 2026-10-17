package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.TerminalScheme;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * The themes shown with the Dark and Light appearances and the terminal scheme, all from {@code library}. Every pick
 * is one the library can honour: {@link #of} replaces any other by its default and reports it. Immutable; a selection
 * from a refreshed library is a different selection, even when its picks are the same.
 */
public record ThemeSelection(ThemeLibrary library, JasperTheme dark, JasperTheme light, TerminalSchemeChoice scheme) {
    public ThemeSelection {
        Objects.requireNonNull(library, "library"); Objects.requireNonNull(dark, "dark");
        Objects.requireNonNull(light, "light"); Objects.requireNonNull(scheme, "scheme");
        if (!dark.has(true)) throw new IllegalArgumentException(dark.name() + " has no Dark variant");
        if (!light.has(false)) throw new IllegalArgumentException(light.name() + " has no Light variant");
        if (scheme.scheme().isPresent() && library.scheme(scheme.scheme().get()).isEmpty())
            throw new IllegalArgumentException("Not an installed scheme: " + scheme.key());
    }

    /** Slate Cobalt for both appearances, from the built-in library; a scheme the built-in themes lack becomes match. */
    public static ThemeSelection slateCobalt(TerminalSchemeChoice scheme) {
        return of(ThemeLibrary.bundledOnly(), JasperTheme.BUNDLED_ID, JasperTheme.BUNDLED_ID, scheme, (key, message) -> { });
    }

    /**
     * The picks {@code dark}, {@code light} and {@code scheme} from {@code library}. A pick the library cannot honour (a
     * theme it does not have, a theme without that variant, a scheme that is not installed) is reported to
     * {@code problems} as its setting's key and a message, and replaced by its default.
     */
    public static ThemeSelection of(ThemeLibrary library, String dark, String light, TerminalSchemeChoice scheme,
                                    BiConsumer<String, String> problems) {
        var darkTheme = pick(library, dark, true, problems);
        var lightTheme = pick(library, light, false, problems);
        var choice = scheme;
        if (scheme.scheme().isPresent() && library.scheme(scheme.scheme().get()).isEmpty()) {
            problems.accept("terminal.scheme", "\"" + scheme.key() + "\" is not an installed scheme. Use \"match\" or one of "
                + library.schemes().stream().map(found -> "\"" + found.id() + "\"").collect(Collectors.joining(", ")) + "; using the default.");
            choice = TerminalSchemeChoice.MATCH;
        }
        return new ThemeSelection(library, darkTheme, lightTheme, choice);
    }

    private static JasperTheme pick(ThemeLibrary library, String id, boolean dark, BiConsumer<String, String> problems) {
        var theme = library.theme(id);
        if (theme.isPresent() && theme.get().has(dark)) return theme.get();
        // Spec 7.5: the pick of a theme whose plugin is off is kept, and shows the default without a warning.
        if (theme.isEmpty() && library.dormant().contains(id)) return JasperTheme.bundled();
        String reason = theme.isPresent() ? "\"" + id + "\" has no " + (dark ? "Dark" : "Light") + " variant."
            : "\"" + id + "\" is not an installed theme.";
        problems.accept(dark ? "ui.theme.dark" : "ui.theme.light", reason + " Use one of "
            + library.themes(dark).stream().map(found -> "\"" + found.id() + "\"").collect(Collectors.joining(", ")) + "; using the default.");
        return JasperTheme.bundled();
    }

    /** The theme shown with the Dark or Light appearance. */
    public JasperTheme theme(boolean dark) { return dark ? this.dark : light; }

    /** The terminal scheme shown with the Dark or Light appearance: the chosen one, or with match the shown theme's own. */
    public TerminalScheme terminal(boolean dark) {
        return scheme.scheme().flatMap(library::scheme).orElseGet(() -> theme(dark).matching(dark));
    }

    /** This selection with another scheme; one the library does not have becomes match. */
    public ThemeSelection withScheme(TerminalSchemeChoice choice) {
        boolean installed = choice.scheme().map(id -> library.scheme(id).isPresent()).orElse(true);
        return new ThemeSelection(library, dark, light, installed ? choice : TerminalSchemeChoice.MATCH);
    }
}

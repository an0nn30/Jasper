package dev.jasper.app.settingsui;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The themes the Appearance page offers, supplied by the application: every installed theme's name, and the import
 * behind the theme gear's Import theme… for one open dialog. EDT only.
 *
 * @param names the installed theme names, in dropdown order; read again on each refresh
 * @param importer the import for a page's dialog; the page closes it when it is disposed
 */
public record ThemeChoices(Supplier<List<String>> names, Function<PageContext, Import> importer) {
    public ThemeChoices {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(importer, "importer");
    }

    /** {@code names} with an Import theme… that does nothing, for pages built without an application. */
    public static ThemeChoices of(Supplier<List<String>> names) {
        return new ThemeChoices(names, context -> new Import(() -> { }, () -> { }));
    }

    /**
     * One page's import.
     *
     * @param start asks for a theme file and imports it over the dialog
     * @param close ends an import still running
     */
    public record Import(Runnable start, Runnable close) {
        public Import {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(close, "close");
        }
    }
}

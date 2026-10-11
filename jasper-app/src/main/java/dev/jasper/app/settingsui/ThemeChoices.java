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
public record ThemeChoices(Supplier<List<String>> names, Function<PageContext, Import> importer,
                           Supplier<List<Source>> sources, Supplier<java.util.Optional<Boolean>> systemDark,
                           Function<Runnable, dev.jasper.app.lifecycle.Subscription> onChanged) {
    public ThemeChoices {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(importer, "importer");
        Objects.requireNonNull(sources); Objects.requireNonNull(systemDark); Objects.requireNonNull(onChanged);
    }

    /** Names-only provider for embedders without source previews or system appearance capability. */
    public ThemeChoices(Supplier<List<String>> names, Function<PageContext, Import> importer) {
        this(names, importer, List::of, java.util.Optional::empty, ignored -> new dev.jasper.app.lifecycle.Subscription(() -> {}));
    }

    /** An actual resolved authored source, with colors and workspace defaults supplied by its owner. */
    public record Source(String name, dev.jasper.app.config.ThemeSource origin, boolean dark,
                         dev.jasper.ui.model.Option.Palette colors, float opacity, boolean blur) {
        public Source { Objects.requireNonNull(name); Objects.requireNonNull(origin); Objects.requireNonNull(colors); }
    }

    /** Resolves manual origin without conflating an installed name collision with a bundled source. */
    public java.util.Optional<Source> find(String name, dev.jasper.app.config.ThemeSource origin) {
        return sources.get().stream().filter(source -> source.name().strip().equalsIgnoreCase(name.strip())
            && (origin == dev.jasper.app.config.ThemeSource.CATALOG || source.origin() == origin)).findFirst();
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

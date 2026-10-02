package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A theme Jasper can install: its name, darkness and terminal palette, and the resolved Jasper theme document
 * ({@code extends} merged in) it is generated from.
 */
public record Theme(String id, String name, boolean dark, Palette palette, Source source) {
    /** Where a theme comes from; every source carries the resolved document. */
    public sealed interface Source permits Bundled, UserFile {
        /** The resolved theme document. */
        ThemeDocument document();
    }

    /** A theme Jasper ships, from {@code file} under {@code dev/jasper/app/themes/}: a Jasper theme or a converted IntelliJ one. */
    public record Bundled(String file, ThemeDocument document) implements Source {
        public Bundled { Objects.requireNonNull(file); Objects.requireNonNull(document); }
    }

    /** A Jasper theme file in the {@code themes} folder beside {@code config.toml}. */
    public record UserFile(Path file, ThemeDocument document) implements Source {
        public UserFile { Objects.requireNonNull(file); Objects.requireNonNull(document); }
    }

    /** Jasper's own light theme. */
    public static final Theme JASPER_LIGHT = BuiltInThemes.toml("jasper-light", "jasper-light.toml");
    /** Jasper's own dark theme, the default. */
    public static final Theme DARK = BuiltInThemes.toml("jasper-dark", "jasper-dark.toml");
    /** Classic IntelliJ Light, converted from intellij-community. */
    public static final Theme INTELLIJ_LIGHT = BuiltInThemes.toml("intellij-light", "intellij-light.toml");
    /** Darcula, converted from intellij-community. */
    public static final Theme DARCULA = BuiltInThemes.toml("darcula", "darcula.toml");

    public Theme {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(palette);
        Objects.requireNonNull(source);
    }

    /** The theme {@code source}'s document generates, under {@code id}. */
    public static Theme of(String id, Source source) {
        GeneratedTheme generated = ThemeGenerator.generate(source.document());
        return new Theme(id, generated.name(), generated.dark(), generated.terminal(), source);
    }
}

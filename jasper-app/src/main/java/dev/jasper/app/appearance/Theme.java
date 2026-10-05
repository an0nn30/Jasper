package dev.jasper.app.appearance;
import dev.jasper.ui.theme.ThemeGenerator;
import dev.jasper.ui.theme.ThemeDocument;
import dev.jasper.ui.theme.GeneratedTheme;

import dev.jasper.terminal.config.Palette;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

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

    /**
     * A theme Jasper ships, from {@code file} under {@code dev/jasper/ui/themeimport/native/}: a Jasper theme or a converted IntelliJ
     * one. Its document is read on first use, once: nearly all of a converted theme's bytes are [ui] and [icons] tables,
     * which only installing or previewing it needs. Bundled themes are equal by file, so comparing catalogs reads nothing.
     */
    public static final class Bundled implements Source {
        private final String file;
        private Supplier<ThemeDocument> read;
        private ThemeDocument document;

        Bundled(String file, Supplier<ThemeDocument> read) {
            this.file = Objects.requireNonNull(file);
            this.read = Objects.requireNonNull(read);
        }

        public String file() { return file; }

        @Override public synchronized ThemeDocument document() {
            if (document == null) {
                document = Objects.requireNonNull(read.get());
                read = null;
            }
            return document;
        }

        @Override public boolean equals(Object other) { return other instanceof Bundled bundled && file.equals(bundled.file); }
        @Override public int hashCode() { return file.hashCode(); }
        @Override public String toString() { return "Bundled[" + file + "]"; }
    }

    /** A Jasper theme file in the {@code themes} folder beside {@code config.toml}. */
    public record UserFile(Path file, ThemeDocument document) implements Source {
        public UserFile { Objects.requireNonNull(file); Objects.requireNonNull(document); }
    }

    /** Jasper's own light theme. */
    public static final Theme JASPER_LIGHT = BuiltInThemes.toml("jasper-light", "jasper-light.toml");
    /** Jasper's own dark theme, the default. */
    public static final Theme DARK = BuiltInThemes.toml("jasper-dark", "jasper-dark.toml");

    public Theme {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(palette);
        Objects.requireNonNull(source);
    }

    /** The theme {@code source}'s document generates, under {@code id}. */
    public static Theme of(String id, Source source) {
        GeneratedTheme generated = ThemeGenerator.generate(source.document());
        return new Theme(id, generated.name(), generated.dark(), TerminalPaletteAdapter.toTerminal(generated.terminal()), source);
    }
}

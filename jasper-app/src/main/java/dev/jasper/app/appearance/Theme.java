package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A theme Jasper can install: an IntelliJ-format theme and the terminal palette used when the
 * terminal matches the UI (its editor scheme's console colours, or a Jasper palette).
 */
public record Theme(String id, String name, boolean dark, Palette palette, Source source) {
    /** Where a theme's {@code .theme.json} comes from. */
    public sealed interface Source permits Resource, Archive {}

    /** A theme file under {@code dev/jasper/app/themes/}. */
    public record Resource(String file) implements Source {
        public Resource { Objects.requireNonNull(file); }
    }

    /** A theme an installed plugin provides; its JSON is read when the catalog reads {@code archive}. */
    public record Archive(Path archive, String entry, String json) implements Source {
        public Archive { Objects.requireNonNull(archive); Objects.requireNonNull(entry); Objects.requireNonNull(json); }
    }

    /** Classic IntelliJ Light, vendored from intellij-community. */
    public static final Theme LIGHT = new Theme("intellij-light", "IntelliJ Light", false, Palette.jasperLight(),
        new Resource("intellij/Light.theme.json"));
    /** Jasper's own dark theme. */
    public static final Theme DARK = new Theme("jasper-dark", "Jasper Dark", true, graphitePalette(),
        new Resource("jasper-dark.theme.json"));

    private static Palette graphitePalette() {
        var ansi = Palette.jasperDark();
        return new Palette(new java.awt.Color(0xd5dee5), new java.awt.Color(0x191c20),
            new java.awt.Color(0xd5dee5), new java.awt.Color(0x35434e), ansi.ansi());
    }

    public Theme {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(palette);
        Objects.requireNonNull(source);
    }
}

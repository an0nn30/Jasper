package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
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
    public static final Theme DARK = new Theme("jasper-dark", "Jasper Dark", true, Palette.jasperDark(),
        new Resource("jasper-dark.theme.json"));

    public Theme {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(palette);
        Objects.requireNonNull(source);
    }

    static Theme of(Appearance appearance) { return appearance == Appearance.LIGHT ? LIGHT : DARK; }

    public Appearance appearance() { return dark ? Appearance.DARK : Appearance.LIGHT; }
}

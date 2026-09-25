package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.terminal.config.Palette;
import java.util.Objects;

/**
 * A theme Jasper can install: an IntelliJ-format theme file under {@code dev/jasper/app/themes/} and
 * the Jasper terminal palette used when the terminal matches the UI.
 */
public record Theme(String id, String name, String file, boolean dark, Palette palette) {
    /** Classic IntelliJ Light, vendored from intellij-community. */
    public static final Theme LIGHT = new Theme("intellij-light", "IntelliJ Light", "intellij/Light.theme.json", false, Palette.jasperLight());
    /** Jasper's own dark theme. */
    public static final Theme DARK = new Theme("jasper-dark", "Jasper Dark", "jasper-dark.theme.json", true, Palette.jasperDark());

    public Theme {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(file);
        Objects.requireNonNull(palette);
    }

    static Theme of(Appearance appearance) { return appearance == Appearance.LIGHT ? LIGHT : DARK; }

    public Appearance appearance() { return dark ? Appearance.DARK : Appearance.LIGHT; }
}

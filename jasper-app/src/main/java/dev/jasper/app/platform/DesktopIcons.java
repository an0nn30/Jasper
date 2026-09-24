package dev.jasper.app.platform;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

/** GTK-style artwork from the desktop icon theme; bundled artwork stands in for any name the theme lacks. */
final class DesktopIcons {
    static final String SOURCE_KEY = "Jasper.desktopIcons";
    static final int COMPACT = 16;
    static final int TOOLBAR = 24;
    private DesktopIcons() {}

    static Icon app(String name, int size) {
        if (!GnomeIcons.NAMES.contains(name)) throw new IllegalArgumentException("Unknown application icon: " + name);
        return themed(FreedesktopNames.of(name), size).<Icon>map(icon -> icon).orElseGet(() -> GnomeIcons.icon(name, size));
    }

    static Icon skin(String name) { return new DesktopSkinIcon(name); }

    /** Each candidate, then its symbolic form, recoloured to the chrome foreground. */
    static Optional<ImageIcon> themed(List<String> names, int size) {
        var candidates = new ArrayList<>(names);
        for (String name : names) candidates.add(name + "-symbolic");
        Color symbolic = UIManager.getColor("Jasper.chromeForeground");
        return source().image(candidates, size, symbolic == null ? Color.BLACK : symbolic).map(ImageIcon::new);
    }

    /** One resolver per installed look and feel, created on first use from the live desktop. */
    static FreedesktopIcons source() {
        if (UIManager.get(SOURCE_KEY) instanceof FreedesktopIcons icons) return icons;
        var created = FreedesktopIcons.fromEnvironment(IconThemeName.find(), System.getenv(), Path.of(System.getProperty("user.home")));
        UIManager.getLookAndFeelDefaults().put(SOURCE_KEY, created);
        return created;
    }
}

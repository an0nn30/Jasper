package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.util.Map;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Bundled IntelliJ, Tabler, GNOME 2 and Tango icons; no network access is needed to render chrome.
 * Modern SVGs render as authored: FlatLaf's global colour filter remaps IntelliJ palette colours
 * for the running theme at paint time.
 */
public final class AppIcons {
    private static final String INTELLIJ = "dev/jasper/app/icons/intellij/";
    /** Modern artwork for the application's own chrome names. */
    private static final Map<String, String> MODERN = Map.ofEntries(
        Map.entry("square-plus", "add"), Map.entry("app-window", "moveToWindow"),
        Map.entry("columns-2", "splitVertically"), Map.entry("maximize", "expandComponent"),
        Map.entry("search", "find"), Map.entry("settings", "gearPlain"), Map.entry("refresh", "refresh"),
        Map.entry("command", "execute"), Map.entry("history", "history"), Map.entry("bookmark", "bookmark"),
        Map.entry("close", "close"), Map.entry("exit", "exit"));
    private AppIcons() {}

    public static Icon icon(String name) {
        if (!GnomeIcons.NAMES.contains(name))
            throw new IllegalArgumentException("Unknown application icon: " + name);
        if (SwingAppearance.retro()) return GnomeIcons.icon(name);
        return chrome(MODERN.get(name));
    }

    /** Application toolbar artwork: large classic icons in retro, regular modern icons otherwise. */
    public static Icon toolbarIcon(String name) {
        return SwingAppearance.retro() ? GnomeIcons.icon(name, 28) : icon(name);
    }

    /** Modern chrome artwork by IntelliJ file name, for example {@code "closeHovered"}. */
    public static Icon chrome(String intellijName) {
        return svg(AppIcons.class.getClassLoader(), INTELLIJ + intellijName + ".svg");
    }

    /** A plugin's 16-pixel SVG drawn as authored; IntelliJ light-palette colours follow the theme. */
    public static Icon plugin(ClassLoader loader, String svgResourcePath) {
        Objects.requireNonNull(loader, "loader");
        return svg(loader, svgResourcePath);
    }

    /** Host-owned semantic artwork, independent of any plugin's resource loader. */
    public static Icon named(String name) {
        String resource = NamedIcons.resource(name);
        if (SwingAppearance.retro()) return new SkinIcon(name);
        return NamedIcons.tinted(name) ? tinted(resource) : svg(AppIcons.class.getClassLoader(), resource);
    }

    /** Selects plugin SVG or explicit OldGNOME2 artwork for the running style. */
    public static Icon skin(ClassLoader loader, String modernSvgResourcePath, String retroName) {
        Objects.requireNonNull(loader, "loader");
        if (retroName == null || !OldGnomeCatalog.NAMES.contains(retroName))
            throw new IllegalArgumentException("Unknown OldGNOME2 icon: " + retroName);
        if (modernSvgResourcePath == null || loader.getResource(modernSvgResourcePath) == null)
            throw new IllegalArgumentException("No such icon resource: " + modernSvgResourcePath);
        return SwingAppearance.retro() ? new SkinIcon(retroName) : plugin(loader, modernSvgResourcePath);
    }

    /** Sizes only managed retro icons; shared compact icons and external artwork remain untouched. */
    public static Icon forToolbar(Icon icon) {
        return icon instanceof SkinIcon managed ? managed.toolbar() : icon;
    }

    private static FlatSVGIcon svg(ClassLoader loader, String path) {
        if (path == null || loader.getResource(path) == null)
            throw new IllegalArgumentException("No such icon resource: " + path);
        return new FlatSVGIcon(path, 16, 16, loader);
    }

    /** Monochrome Tabler fallback for names IntelliJ has no artwork for; it follows the chrome foreground. */
    private static Icon tinted(String path) {
        FlatSVGIcon icon = svg(AppIcons.class.getClassLoader(), path);
        return icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> themed("Jasper.chromeForeground", source)));
    }

    private static Color themed(String key, Color source) {
        Color target = UIManager.getColor(key);
        if (target == null) return source;
        return new Color(target.getRed(), target.getGreen(), target.getBlue(), source.getAlpha());
    }
}

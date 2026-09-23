package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import javax.swing.UIManager;

/** Bundled Tabler, GNOME 2 and Tango icons; no network access is needed to render chrome. */
public final class AppIcons {
    private AppIcons() {}
    public static javax.swing.Icon icon(String name) {
        if (!GnomeIcons.NAMES.contains(name))
            throw new IllegalArgumentException("Unknown application icon: " + name);
        if (SwingAppearance.retro()) return GnomeIcons.icon(name);
        String resource = name.equals("close") ? "dev/jasper/app/icons/title/x.svg" : "dev/jasper/app/icons/" + name + ".svg";
        FlatSVGIcon icon = new FlatSVGIcon(resource, 16, 16);
        return icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> themed("Jasper.chromeForeground", source)));
    }

    /** Application toolbar artwork: large classic icons in retro, regular modern icons otherwise. */
    public static javax.swing.Icon toolbarIcon(String name) {
        return SwingAppearance.retro() ? GnomeIcons.icon(name, 28) : icon(name);
    }

    /**
     * A chrome-sized icon from an SVG that another class loader holds, recolored like the bundled icons.
     * The color is read from the look and feel at paint time, so the icon follows theme changes.
     */
    public static javax.swing.Icon themed(ClassLoader loader, String svgResourcePath) {
        java.util.Objects.requireNonNull(loader, "loader");
        if (svgResourcePath == null || loader.getResource(svgResourcePath) == null)
            throw new IllegalArgumentException("No such icon resource: " + svgResourcePath);
        FlatSVGIcon icon = new FlatSVGIcon(svgResourcePath, 16, 16, loader);
        return icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> themed("Jasper.chromeForeground", source)));
    }

    /** Host-owned semantic artwork, independent of any plugin's resource loader. */
    public static javax.swing.Icon named(String name) {
        String resource = NamedIcons.resource(name);
        return SwingAppearance.retro() ? new SkinIcon(name) : themed(AppIcons.class.getClassLoader(), resource);
    }

    /** Selects plugin SVG or explicit OldGNOME2 artwork for the running style. */
    public static javax.swing.Icon skin(ClassLoader loader, String modernSvgResourcePath, String retroName) {
        java.util.Objects.requireNonNull(loader, "loader");
        if (retroName == null || !OldGnomeCatalog.NAMES.contains(retroName))
            throw new IllegalArgumentException("Unknown OldGNOME2 icon: " + retroName);
        if (modernSvgResourcePath == null || loader.getResource(modernSvgResourcePath) == null)
            throw new IllegalArgumentException("No such icon resource: " + modernSvgResourcePath);
        return SwingAppearance.retro() ? new SkinIcon(retroName) : themed(loader, modernSvgResourcePath);
    }

    /** Sizes only managed retro icons; shared compact icons and external artwork remain untouched. */
    public static javax.swing.Icon forToolbar(javax.swing.Icon icon) {
        return icon instanceof SkinIcon managed ? managed.toolbar() : icon;
    }

    private static Color themed(String key, Color source) {
        Color target = UIManager.getColor(key);
        if (target == null) return source;
        return new Color(target.getRed(), target.getGreen(), target.getBlue(), source.getAlpha());
    }
}

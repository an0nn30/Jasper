package dev.jasper.app.platform;

import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.metrics.DesignMetrics;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLaf.DisabledIconProvider;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Central, offline icon manager. Bundled New UI SVGs retain their authored colours and
 * select upstream dark siblings for the component's zone at paint time.
 */
public final class AppIcons {
    private static final String BASE = "dev/jasper/app/icons/intellij/";
    private static final Set<String> CATALOG = readCatalog();
    private static final Map<String, Icon> SURFACE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> CHROME = Map.ofEntries(
        Map.entry("square-plus", "ADD"), Map.entry("app-window", "NEW_WINDOW"),
        Map.entry("columns-2", "SPLIT"), Map.entry("maximize", "ZOOM"),
        Map.entry("search", "SEARCH"), Map.entry("settings", "SETTINGS"), Map.entry("refresh", "REFRESH"),
        Map.entry("command", "EXECUTE"), Map.entry("history", "HISTORY"), Map.entry("bookmark", "BOOKMARK"),
        Map.entry("close", "CLOSE"), Map.entry("exit", "EXIT"));
    private static final dev.jasper.ui.icons.IconRegistry NAMED = new dev.jasper.ui.icons.IconRegistry();
    private static final Icon DROPDOWN = new SemanticIcon("chevron.down", 8, false);
    private AppIcons() { }

    /** Compact dropdown affordance for existing toolbar spacing. */
    public static Icon dropdown() { return DROPDOWN; }

    /** A 16-pixel application chrome icon by Jasper name. */
    public static Icon icon(String name) {
        String meaning = CHROME.get(name);
        if (meaning == null) throw new IllegalArgumentException("Unknown application icon: " + name);
        return named(meaning);
    }

    /** Host-owned semantic artwork, independent of any plugin's resource loader. */
    public static Icon named(String name) {
        return CACHE.computeIfAbsent("named:" + name, ignored -> new SemanticIcon(NamedIcons.kitName(name),
            DesignMetrics.DEFAULT.value(DesignMetrics.Role.ICON_RAIL), false));
    }

    /** A module glyph for an app-native affordance outside the SDK named-icon vocabulary. */
    public static Icon semantic(String name) {
        if (!NAMED.names().contains(name)) throw new IllegalArgumentException("Unknown module icon: " + name);
        return CACHE.computeIfAbsent("semantic:" + name, ignored -> new SemanticIcon(name,
            DesignMetrics.DEFAULT.value(DesignMetrics.Role.ICON_RAIL), false));
    }

    /** The authored light SVG behind a semantic name, for a surface that renders it itself (the macOS menu bar glyph). */
    static java.net.URL artwork(String name) {
        return Objects.requireNonNull(AppIcons.class.getClassLoader().getResource(BASE + NamedIcons.id(name) + ".svg"), name);
    }

    /** Select authored host artwork for an independently coloured surface; custom icons stay intact. */
    public static Icon forBackground(Icon icon, Color background) {
        if (!(icon instanceof ZoneIcon zone)) return icon;
        boolean dark = (.2126 * background.getRed() + .7152 * background.getGreen()
            + .0722 * background.getBlue()) < 128;
        String key = zone.id() + ":" + dark + ":" + zone.size() + ":" + zone.disabled();
        return SURFACE_CACHE.computeIfAbsent(key, ignored -> dark ? zone.dark() : zone.light());
    }

    /** All supported catalog IDs, including explicit size choices but excluding automatic dark siblings. */
    public static Set<String> bundledIds() { return CATALOG; }

    /** A catalog choice. Only IDs in the generated index are accepted, never arbitrary resource paths. */
    public static Icon bundled(String id) {
        if (id == null || !CATALOG.contains(id)) throw new IllegalArgumentException("Unknown bundled icon: " + id);
        return CACHE.computeIfAbsent(id, key -> dynamic(key, 16, false));
    }

    /** A plugin's 16-pixel SVG drawn as authored, using only its own resource loader. */
    public static Icon plugin(ClassLoader loader, String svgResourcePath) {
        Objects.requireNonNull(loader, "loader");
        if (svgResourcePath == null || loader.getResource(svgResourcePath) == null)
            throw new IllegalArgumentException("No such icon resource: " + svgResourcePath);
        return new FlatSVGIcon(svgResourcePath, 16, 16, loader);
    }

    private static Icon dynamic(String id, int size, boolean disabled) {
        var loader = AppIcons.class.getClassLoader();
        var lightUrl = Objects.requireNonNull(loader.getResource(BASE + id + ".svg"));
        var darkUrl = loader.getResource(BASE + id + "_dark.svg");
        FlatSVGIcon light = new FlatSVGIcon(lightUrl);
        FlatSVGIcon dark = new FlatSVGIcon(darkUrl == null ? lightUrl : darkUrl);
        return new ZoneIcon(id, size, disabled,
            new FittedIcon(disabled ? light.getDisabledIcon() : light, size),
            new FittedIcon(disabled ? dark.getDisabledIcon() : dark, size));
    }

    private record ZoneIcon(String id, int size, boolean disabled, Icon light, Icon dark)
            implements Icon, DisabledIconProvider {
        @Override public Icon getDisabledIcon() { return dynamic(id, size, true); }
        @Override public int getIconWidth() { return UIScale.scale(size); }
        @Override public int getIconHeight() { return UIScale.scale(size); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            (ThemeZone.dark(component, FlatLaf.isLafDark()) ? dark : light).paintIcon(component, graphics, x, y);
        }
    }

    /** Paint-time module tint follows live chrome/workspace routing; only immutable glyph paths are cached. */
    private record SemanticIcon(String name, int size, boolean disabled) implements Icon, DisabledIconProvider {
        @Override public Icon getDisabledIcon() { return new SemanticIcon(name, size, true); }
        @Override public int getIconWidth() { return UIScale.scale(size); }
        @Override public int getIconHeight() { return UIScale.scale(size); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            var g = (Graphics2D) graphics.create();
            try {
                if (disabled) g.setComposite(java.awt.AlphaComposite.SrcOver.derive(.42f));
                NAMED.icon(name, ThemeZone.theme(component).semantic(), SemanticColor.TEXT_PRIMARY,
                    getIconWidth()).paintIcon(component, g, x, y);
            } finally { g.dispose(); }
        }
    }

    private static Set<String> readCatalog() {
        try (var input = AppIcons.class.getClassLoader().getResourceAsStream(BASE + "catalog.txt")) {
            if (input == null) throw new IllegalStateException("Missing bundled icon catalog");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().collect(Collectors.toUnmodifiableSet());
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }

    /** Preserve non-square catalog artwork within the same square box as ordinary controls. */
    private record FittedIcon(Icon source, int size) implements Icon, DisabledIconProvider {
        @Override public Icon getDisabledIcon() {
            return new FittedIcon(((DisabledIconProvider) source).getDisabledIcon(), size);
        }
        @Override public int getIconWidth() { return UIScale.scale(size); }
        @Override public int getIconHeight() { return UIScale.scale(size); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                double scale = (double) getIconWidth() / Math.max(source.getIconWidth(), source.getIconHeight());
                copy.translate(x + (getIconWidth() - scale * source.getIconWidth()) / 2,
                    y + (getIconHeight() - scale * source.getIconHeight()) / 2);
                copy.scale(scale, scale);
                source.paintIcon(component, copy, 0, 0);
            } finally { copy.dispose(); }
        }
    }
}

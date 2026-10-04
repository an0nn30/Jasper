package dev.jasper.app.platform;

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
    private static final Icon KEY = new KeyIcon(false);
    private static final Icon DROPDOWN = dynamic(NamedIcons.id("DROPDOWN"), 8, false);
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
        String id = NamedIcons.id(name);
        return id.equals("standard/KEY") ? KEY : bundled(id);
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

    /** Only the semantic key is tinted; catalog and plugin artwork keep their authored colours. */
    private static final class KeyIcon implements Icon, DisabledIconProvider {
        private final boolean disabled;
        private final FlatSVGIcon source;
        KeyIcon(boolean disabled) {
            this.disabled = disabled;
            source = new FlatSVGIcon("dev/jasper/app/icons/standard/KEY.svg", 16, 16);
        }
        @Override public Icon getDisabledIcon() { return new KeyIcon(true); }
        @Override public int getIconWidth() { return source.getIconWidth(); }
        @Override public int getIconHeight() { return source.getIconHeight(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Color color = ThemeZone.foreground(component, UIManager.getColor("Jasper.chromeForeground"));
            source.setColorFilter(new FlatSVGIcon.ColorFilter(authored -> color == null ? authored
                : new Color(color.getRed(), color.getGreen(), color.getBlue(), authored.getAlpha())));
            (disabled ? source.getDisabledIcon() : source).paintIcon(component, graphics, x, y);
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

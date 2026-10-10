package dev.jasper.app.platform;

import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.metrics.DesignMetrics;

import com.formdev.flatlaf.FlatLaf.DisabledIconProvider;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;

/** App-native named module glyphs, resolved against the receiving component at paint time. */
public final class AppIcons {
    private static final String BASE = "dev/jasper/app/icons/intellij/";
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

    /**
     * The module glyph name behind a host icon, for kit descriptors: present for {@link #icon}, {@link #named} and
     * {@link #semantic} icons, empty for identity artwork.
     */
    public static java.util.Optional<String> kitName(Icon icon) {
        if (icon instanceof SemanticIcon named) return java.util.Optional.of(named.name());
        if (icon instanceof SurfaceIcon surface) return java.util.Optional.of(surface.icon().name());
        return java.util.Optional.empty();
    }

    /** Native macOS tray template artwork; not a standard-control icon catalog. */
    static java.net.URL trayArtwork() {
        return Objects.requireNonNull(AppIcons.class.getClassLoader().getResource(BASE + "terminal/toolwindow/terminal.svg"));
    }

    /**
     * Artwork for an independently coloured surface: named module glyphs follow the surface text; identity icons stay intact.
     */
    public static Icon forBackground(Icon icon, Color background) { return forSurface(icon, background, null); }

    /**
     * As {@link #forBackground}, with the surface's own foreground (for example the terminal palette's text on a
     * folder tab) as the named icon's tint; the module holds it to the non-text contrast floor on that surface.
     */
    public static Icon forSurface(Icon icon, Color background, Color foregroundOrNull) {
        Objects.requireNonNull(background, "background");
        if (icon instanceof SemanticIcon named) return new SurfaceIcon(named, background, foregroundOrNull);
        if (icon instanceof SurfaceIcon surface) return new SurfaceIcon(surface.icon(), background, foregroundOrNull);
        return icon;
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

    /** A named icon tinted for one explicit surface; value equality keeps repeated refreshes event-free. */
    private record SurfaceIcon(SemanticIcon icon, Color surface, Color foreground) implements Icon, DisabledIconProvider {
        @Override public Icon getDisabledIcon() {
            return new SurfaceIcon((SemanticIcon) icon.getDisabledIcon(), surface, foreground);
        }
        @Override public int getIconWidth() { return icon.getIconWidth(); }
        @Override public int getIconHeight() { return icon.getIconHeight(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            var g = (Graphics2D) graphics.create();
            try {
                if (icon.disabled()) g.setComposite(java.awt.AlphaComposite.SrcOver.derive(.42f));
                Color base = foreground != null ? foreground : ThemeZone.color(component, SemanticColor.TEXT_PRIMARY);
                NAMED.icon(icon.name(), surface, base, getIconWidth()).paintIcon(component, g, x, y);
            } finally { g.dispose(); }
        }
    }

}

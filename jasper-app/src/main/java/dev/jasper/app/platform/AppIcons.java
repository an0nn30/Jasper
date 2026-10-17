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
        return CACHE.computeIfAbsent("named:" + name, ignored -> new SemanticIcon(NamedIcons.kitName(name), railSize(), false));
    }

    /** A module glyph for an app-native affordance outside the SDK named-icon vocabulary. */
    public static Icon semantic(String name) {
        if (!NAMED.names().contains(name)) throw new IllegalArgumentException("Unknown module icon: " + name);
        return CACHE.computeIfAbsent("semantic:" + name, ignored -> new SemanticIcon(name, railSize(), false));
    }

    /** The installed kit's rail glyph size; a cached icon keeps it, since the kit is fixed before any window. */
    private static int railSize() {
        return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.ICON_RAIL);
    }

    /**
     * The module glyph name behind a host icon, for kit descriptors: present for {@link #icon}, {@link #named} and
     * {@link #semantic} icons, empty for identity artwork.
     */
    public static java.util.Optional<String> kitName(Icon icon) {
        if (icon instanceof SemanticIcon named) return java.util.Optional.of(named.name());
        if (icon instanceof SurfaceIcon surface) return java.util.Optional.of(surface.icon().name());
        if (icon instanceof StripGlyph strip) return strip.name();
        return java.util.Optional.empty();
    }

    /**
     * A header strip's action glyph (spec 10.7): {@code icon}'s module glyph (a {@link #semantic}, {@link #named} or
     * {@link PresentationIcon} glyph) drawn in a {@code logicalSize} box, in the ink {@code ink} gives for the receiving
     * component; artwork without a module glyph is returned unchanged.
     */
    public static Icon stripGlyph(Icon icon, int logicalSize, java.util.function.Function<Component, Color> ink) {
        Objects.requireNonNull(ink, "ink");
        Icon source = icon instanceof StripGlyph strip ? strip.source() : icon;
        return source instanceof PresentationIcon || kitName(source).isPresent() ? new StripGlyph(source, logicalSize, ink) : source;
    }

    /** A module glyph drawn at a header strip's size in its action ink; the ink is read at paint time. */
    private record StripGlyph(Icon source, int size, java.util.function.Function<Component, Color> ink) implements Icon {
        java.util.Optional<String> name() {
            return source instanceof PresentationIcon presentation ? java.util.Optional.of(presentation.name()) : kitName(source);
        }
        @Override public int getIconWidth() { return UIScale.scale(size); }
        @Override public int getIconHeight() { return getIconWidth(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Color color = ink.apply(component);
            var palette = ThemeZone.theme(component).semantic();
            Icon glyph = source instanceof PresentationIcon presentation ? presentation.glyph(palette, color, getIconWidth())
                : NAMED.panel(name().orElseThrow(), palette, color, getIconWidth());
            glyph.paintIcon(component, graphics, x, y);
        }
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

    /** Client property marking a native chrome receiver: icons painted inside it use the chrome's original artwork. */
    private static final String CHROME_RECEIVER = "Jasper.chromeArtwork";

    /**
     * Marks {@code component} as one of the workspace chrome's receiving contexts (title bar header, tool window
     * stripes, status bar): named glyphs painted inside it use {@link dev.jasper.ui.icons.IconRegistry#chromeIcon}'s
     * original desktop artwork. Icon values, sizes and identities are unchanged; everything outside keeps ordinary artwork.
     */
    public static void chromeReceiver(javax.swing.JComponent component) { component.putClientProperty(CHROME_RECEIVER, Boolean.TRUE); }

    static boolean inChrome(Component component) {
        for (Component c = component; c != null; c = c.getParent())
            if (c instanceof javax.swing.JComponent j && j.getClientProperty(CHROME_RECEIVER) == Boolean.TRUE) return true;
        return false;
    }

    private record PreparationKey(String name, int size) { }
    private record NativeSnapshot(int size, dev.jasper.ui.icons.IconRegistry.PreparedIcon artwork,
                                  dev.jasper.ui.icons.IconRegistry.PreparedIcon chrome) { }
    /** One process-lifetime preparation owner, shared with builtin PresentationIcons. No receiving theme is cached. */
    static final class NativePreparation {
        final String name; final int baseSize;
        volatile NativeSnapshot snapshot;
        NativePreparation(String name,int size) { this.name=name;this.baseSize=size;prepare(); }
        void prepare() { int size=UIScale.scale(baseSize);snapshot=new NativeSnapshot(size,NAMED.themedIcon(name,size),NAMED.chromeIcon(name,size)); }
        int size(){return snapshot.size();}
        void paint(Component c,Graphics g,int x,int y,SemanticColor tint) {
            var current=snapshot;(inChrome(c)?current.chrome():current.artwork()).icon(ThemeZone.theme(c),tint).paintIcon(c,g,x,y);
        }
    }
    private static final Map<PreparationKey,NativePreparation> PREPARED = new java.util.HashMap<>();
    static {
        UIScale.addPropertyChangeListener(event -> {
            if("userScaleFactor".equals(event.getPropertyName())) synchronized(PREPARED) { PREPARED.values().forEach(NativePreparation::prepare); }
        });
    }
    private static final Icon DROPDOWN = new SemanticIcon("chevron.down", 8, false);
    static NativePreparation prepare(String name,int size) {
        if(size!=8 && size!=16)throw new IllegalArgumentException("Unsupported native icon base size: "+size);
        synchronized(PREPARED) { return PREPARED.computeIfAbsent(new PreparationKey(name,size),key -> new NativePreparation(name,size)); }
    }
    /** Value identity excludes the shared process-lifetime scale preparation. */
    private static final class SemanticIcon implements Icon, DisabledIconProvider {
        private final String name; private final int size; private final boolean disabled;
        private final NativePreparation preparation;
        SemanticIcon(String name,int size,boolean disabled){this.name=name;this.size=size;this.disabled=disabled;this.preparation=prepare(name,size);}
        String name(){return name;} boolean disabled(){return disabled;}
        @Override public boolean equals(Object other){return other instanceof SemanticIcon icon && name.equals(icon.name) && size==icon.size && disabled==icon.disabled;}
        @Override public int hashCode(){return Objects.hash(name,size,disabled);}
        @Override public Icon getDisabledIcon() { return new SemanticIcon(name,size,true); }
        @Override public int getIconWidth() { return preparation.size(); }
        @Override public int getIconHeight() { return preparation.size(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            var g=(Graphics2D)graphics.create();
            try {
                if(disabled)g.setComposite(java.awt.AlphaComposite.SrcOver.derive(.42f));
                // A highlighted native menu row is the accent: its glyph turns white with its label.
                boolean highlighted = component instanceof javax.swing.JMenu menu ? menu.isSelected()
                    : component instanceof javax.swing.JMenuItem item && item.getModel().isArmed();
                preparation.paint(component,g,x,y,highlighted ? SemanticColor.TEXT_ONACCENT : SemanticColor.TEXT_PRIMARY);
            } finally {g.dispose();}
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

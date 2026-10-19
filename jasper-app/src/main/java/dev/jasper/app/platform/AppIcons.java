package dev.jasper.app.platform;

import dev.jasper.ui.icons.ChromeArtwork;
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

    /**
     * The icon a labelled tool window stripe's button draws for a panel's or rail action's {@code icon} (spec 10.7), a
     * {@code logicalSize} square whose art and ink are read at paint time from the receiving component. A header
     * strip's glyph is first unwrapped to its source. Under {@link ChromeArtwork#CLASSIC} a named icon draws IntelliJ's
     * classic tool-window file where {@link dev.jasper.ui.icons.IconRegistry#stripeIcon} has one, authored and untinted;
     * a plugin's path, and any other name, draws its monochrome glyph in the ink {@code ink} gives for the receiving
     * component. Under {@link ChromeArtwork#NEW_UI} it draws what the icon rails draw: a named icon's chrome artwork, or
     * a plugin's ordinary glyph. Any other icon is drawn as given, centred in the square and scaled down uniformly when
     * larger. Empty for no icon: a null icon, or one whose width or height is not positive.
     */
    public static java.util.Optional<Icon> stripeIcon(Icon icon, ChromeArtwork artwork, int logicalSize,
                                                      java.util.function.Function<Component, Color> ink) {
        Objects.requireNonNull(artwork, "artwork");
        Objects.requireNonNull(ink, "ink");
        Icon source = icon instanceof StripGlyph strip ? strip.source() : icon;
        if (source == null || source.getIconWidth() <= 0 || source.getIconHeight() <= 0) return java.util.Optional.empty();
        if (source instanceof PresentationIcon presentation && presentation.pathData() != null)
            return java.util.Optional.of(new PluginStripeIcon(presentation, artwork, logicalSize, ink));
        java.util.Optional<String> name = source instanceof PresentationIcon presentation
            ? java.util.Optional.of(presentation.name()) : kitName(source);
        if (name.isPresent()) {
            var tint = source instanceof PresentationIcon ? SemanticColor.TEXT_SECONDARY : SemanticColor.TEXT_PRIMARY;
            return java.util.Optional.of(new NamedStripeIcon(name.get(), tint, artwork, logicalSize, ink));
        }
        return java.util.Optional.of(new GivenStripeIcon(source, logicalSize));
    }

    /** One stripe preparation: the chrome artwork, the icon name and the device size it was prepared at. */
    private record StripeKey(ChromeArtwork artwork, String name, int size) { }
    /**
     * Prepared stripe art, built on first paint: under CLASSIC the tool-window file (empty for a name without one),
     * under NEW_UI the chrome icon. Only a CLASSIC stripe's paint reads the stripe table.
     */
    private static final Map<StripeKey, java.util.Optional<dev.jasper.ui.icons.IconRegistry.PreparedIcon>> STRIPES =
        new ConcurrentHashMap<>();

    /** A built-in or semantic icon name on a labelled stripe; {@code tint} is its rail tint under New UI. */
    private record NamedStripeIcon(String name, SemanticColor tint, ChromeArtwork artwork, int size,
                                   java.util.function.Function<Component, Color> ink) implements Icon {
        @Override public int getIconWidth() { return UIScale.scale(size); }
        @Override public int getIconHeight() { return getIconWidth(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            int device = getIconWidth();
            var theme = ThemeZone.theme(component);
            var prepared = STRIPES.computeIfAbsent(new StripeKey(artwork, name, device), key -> switch (key.artwork()) {
                case CLASSIC -> NAMED.stripeIcon(key.name(), key.size());
                case NEW_UI -> java.util.Optional.of(NAMED.chromeIcon(key.name(), key.size()));
            });
            Icon art = prepared.isPresent()
                ? prepared.get().icon(theme, artwork == ChromeArtwork.CLASSIC ? SemanticColor.TEXT_PRIMARY : tint)
                : NAMED.panel(name, theme.semantic(), ink.apply(component), device);
            art.paintIcon(component, graphics, x, y);
        }
    }

    /** A plugin's own path on a labelled stripe: its glyph in the label ink, or its ordinary glyph under New UI. */
    private record PluginStripeIcon(PresentationIcon source, ChromeArtwork artwork, int size,
                                    java.util.function.Function<Component, Color> ink) implements Icon {
        @Override public int getIconWidth() { return UIScale.scale(size); }
        @Override public int getIconHeight() { return getIconWidth(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            int device = getIconWidth();
            var theme = ThemeZone.theme(component);
            Icon art = artwork == ChromeArtwork.CLASSIC ? source.glyph(theme.semantic(), ink.apply(component), device)
                : source.ordinary(theme, device);
            art.paintIcon(component, graphics, x, y);
        }
    }

    /** Any other icon on a labelled stripe, drawn as given: centred in the square, scaled down uniformly when larger. */
    private record GivenStripeIcon(Icon source, int size) implements Icon {
        @Override public int getIconWidth() { return UIScale.scale(size); }
        @Override public int getIconHeight() { return getIconWidth(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            int box = getIconWidth();
            int width = source.getIconWidth(), height = source.getIconHeight();
            double scale = Math.min(1.0, (double) box / Math.max(width, height));
            int left = (box - (int) Math.round(width * scale)) / 2, top = (box - (int) Math.round(height * scale)) / 2;
            var g = (Graphics2D) graphics.create();
            try {
                g.clipRect(x, y, box, box);
                g.translate(x + left, y + top);
                if (scale < 1.0) g.scale(scale, scale);
                source.paintIcon(component, g, 0, 0);
            } finally { g.dispose(); }
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

    /**
     * Client property marking a native chrome receiver with its {@link ChromeArtwork}: icons painted inside it use that
     * chrome artwork.
     */
    private static final String CHROME_RECEIVER = "Jasper.chromeArtwork";

    /**
     * Marks {@code component} as one of the workspace chrome's receiving contexts (title bar header, tool window
     * stripes, status bar) for the New UI originals: the same as {@code chromeReceiver(component, ChromeArtwork.NEW_UI)}.
     */
    public static void chromeReceiver(javax.swing.JComponent component) { chromeReceiver(component, ChromeArtwork.NEW_UI); }

    /**
     * Marks {@code component} as one of the workspace chrome's receiving contexts, drawing {@code artwork}: named glyphs
     * painted inside it use {@link dev.jasper.ui.icons.IconRegistry#chromeIcon(String, int, ChromeArtwork)}'s desktop
     * artwork for that set. Icon values, sizes and identities are unchanged; everything outside keeps ordinary artwork.
     * A later mark replaces an earlier one.
     */
    public static void chromeReceiver(javax.swing.JComponent component, ChromeArtwork artwork) {
        component.putClientProperty(CHROME_RECEIVER, Objects.requireNonNull(artwork, "artwork"));
    }

    /**
     * The chrome artwork of the nearest receiver holding {@code component} (itself included), or empty outside every
     * receiver.
     */
    public static java.util.Optional<ChromeArtwork> chromeArtwork(Component component) {
        for (Component c = component; c != null; c = c.getParent())
            if (c instanceof javax.swing.JComponent j && j.getClientProperty(CHROME_RECEIVER) instanceof ChromeArtwork artwork)
                return java.util.Optional.of(artwork);
        return java.util.Optional.empty();
    }

    private record PreparationKey(String name, int size) { }
    private record NativeSnapshot(int size, dev.jasper.ui.icons.IconRegistry.PreparedIcon artwork,
                                  dev.jasper.ui.icons.IconRegistry.PreparedIcon chrome) { }
    /** A classic chrome icon prepared for one snapshot's size. */
    private record ClassicSnapshot(int size, dev.jasper.ui.icons.IconRegistry.PreparedIcon icon) { }
    /**
     * One process-lifetime preparation owner, shared with builtin PresentationIcons. No receiving theme is cached. The
     * classic chrome icon is built only the first time it paints inside a {@link ChromeArtwork#CLASSIC} receiver, so a
     * default-look start never prepares it.
     */
    static final class NativePreparation {
        final String name; final int baseSize;
        volatile NativeSnapshot snapshot;
        private volatile ClassicSnapshot classic;
        NativePreparation(String name,int size) { this.name=name;this.baseSize=size;prepare(); }
        void prepare() {
            int size=UIScale.scale(baseSize);
            snapshot=new NativeSnapshot(size,NAMED.themedIcon(name,size),NAMED.chromeIcon(name,size));
            if(classic!=null && classic.size()!=size)classic=null;
        }
        int size(){return snapshot.size();}
        void paint(Component c,Graphics g,int x,int y,SemanticColor tint) {
            var current=snapshot;
            var prepared=switch(chromeArtwork(c).orElse(null)) {
                case null -> current.artwork();
                case NEW_UI -> current.chrome();
                case CLASSIC -> classic(current.size());
            };
            prepared.icon(ThemeZone.theme(c),tint).paintIcon(c,g,x,y);
        }
        private dev.jasper.ui.icons.IconRegistry.PreparedIcon classic(int size) {
            var built=classic;
            if(built==null || built.size()!=size)classic=built=new ClassicSnapshot(size,NAMED.chromeIcon(name,size,ChromeArtwork.CLASSIC));
            return built.icon();
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

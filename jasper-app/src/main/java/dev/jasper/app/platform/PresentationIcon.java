package dev.jasper.app.platform;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.model.Nodes;
import java.awt.Component;
import java.awt.Graphics;
import java.util.Objects;
import javax.swing.Icon;

/** Immutable app-only semantic identity and constrained artwork. Native paint follows its actual mount's theme;
 * receiving kit views copy the value into their own scoped registries. No plugin callbacks or resource loaders survive.
 */
public final class PresentationIcon implements Icon {
    private final String name;
    private final String pathData;
    /** The installed kit's rail glyph size in logical pixels, read once: the kit is fixed before any window. */
    private final int rail;
    private final AppIcons.NativePreparation nativeArtwork;
    // This value's registry has no external owner or subscription; it is reclaimed with the presentation value.
    private final IconRegistry artwork = new IconRegistry();
    public PresentationIcon(String name, String pathData) {
        this.name = Objects.requireNonNull(name);
        this.pathData = pathData;
        if (pathData != null) artwork.register(name, pathData);
        else if (!artwork.names().contains(name)) throw new IllegalArgumentException("Unknown icon: " + name);
        rail = artwork.size(dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics(), Nodes.IconSize.RAIL);
        nativeArtwork=pathData==null?AppIcons.prepare(name,rail):null;
    }
    public String name() { return name; }
    /** This artwork in {@code ink} at {@code size} device pixels, as a header strip draws it (spec 10.7). */
    Icon glyph(dev.jasper.ui.theme.SemanticPalette palette, java.awt.Color ink, int size) { return artwork.panel(name, palette, ink, size); }
    /** This artwork as its own paint draws it, at {@code size} device pixels (a labelled stripe under New UI artwork). */
    Icon ordinary(dev.jasper.ui.theme.GeneratedTheme theme, int size) { return artwork.icon(name, theme, Nodes.Hue.NONE, size); }
    public String pathData() { return pathData; }
    /** This artwork in {@code role}'s ink at this icon's size, as a dimmed status item paints it. */
    public Icon in(dev.jasper.ui.theme.SemanticColor role) {
        Objects.requireNonNull(role, "role");
        return new Icon() {
            @Override public int getIconWidth() { return PresentationIcon.this.getIconWidth(); }
            @Override public int getIconHeight() { return PresentationIcon.this.getIconHeight(); }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                if(nativeArtwork!=null) nativeArtwork.paint(c,g,x,y,role);
                else artwork.icon(name, ThemeZone.theme(c), role, getIconWidth()).paintIcon(c,g,x,y);
            }
        };
    }
    @Override public int getIconWidth() { return nativeArtwork==null?UIScale.scale(rail):nativeArtwork.size(); }
    @Override public int getIconHeight() { return nativeArtwork==null?UIScale.scale(rail):nativeArtwork.size(); }
    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
        if(nativeArtwork!=null) nativeArtwork.paint(c,g,x,y,dev.jasper.ui.theme.SemanticColor.TEXT_SECONDARY);
        else artwork.icon(name, ThemeZone.theme(c), Nodes.Hue.NONE, getIconWidth()).paintIcon(c,g,x,y);
    }
}

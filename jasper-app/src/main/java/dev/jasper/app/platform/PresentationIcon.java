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
    // This value's registry has no external owner or subscription; it is reclaimed with the presentation value.
    private final IconRegistry artwork = new IconRegistry();
    public PresentationIcon(String name, String pathData) {
        this.name = Objects.requireNonNull(name);
        this.pathData = pathData;
        if (pathData != null) artwork.register(name, pathData);
        else if (!artwork.names().contains(name)) throw new IllegalArgumentException("Unknown icon: " + name);
    }
    public String name() { return name; }
    public String pathData() { return pathData; }
    @Override public int getIconWidth() { return UIScale.scale(artwork.size(Nodes.IconSize.RAIL)); }
    @Override public int getIconHeight() { return UIScale.scale(artwork.size(Nodes.IconSize.RAIL)); }
    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
        artwork.icon(name, ThemeZone.theme(c).semantic(), Nodes.Hue.NONE, getIconWidth()).paintIcon(c,g,x,y);
    }
}

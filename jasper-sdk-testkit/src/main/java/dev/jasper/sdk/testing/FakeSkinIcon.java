package dev.jasper.sdk.testing;

import dev.jasper.sdk.ui.OldGnomeIcon;
import java.awt.Component;
import java.awt.Graphics;
import java.util.Objects;
import javax.swing.Icon;

/**
 * Inspectable, non-rendering icon selection from the fake host.
 * @param modernSvgResourcePath requested plugin SVG resource
 * @param retroIcon requested bundled artwork
 * @param retro whether the fake host selected retro artwork
 * @since 0.7.2
 */
public record FakeSkinIcon(String modernSvgResourcePath, OldGnomeIcon retroIcon, boolean retro) implements Icon {
    /** Creates a validated icon selection. */
    public FakeSkinIcon {
        Objects.requireNonNull(modernSvgResourcePath, "modernSvgResourcePath");
        Objects.requireNonNull(retroIcon, "retroIcon");
    }
    @Override public int getIconWidth() { return 16; }
    @Override public int getIconHeight() { return 16; }
    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) { }
}

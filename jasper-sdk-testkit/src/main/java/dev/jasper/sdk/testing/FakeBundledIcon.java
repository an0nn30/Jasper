package dev.jasper.sdk.testing;

import dev.jasper.sdk.ui.BundledIcon;
import java.awt.Component;
import java.awt.Graphics;
import java.util.Objects;
import javax.swing.Icon;

/**
 * Inspectable, non-rendering selection from the host-owned catalog.
 * @param name requested bundled icon
 * @since 0.8.2
 */
public record FakeBundledIcon(BundledIcon name) implements Icon {
    /** Creates a validated selection. */
    public FakeBundledIcon { Objects.requireNonNull(name, "name"); }
    @Override public int getIconWidth() { return 16; }
    @Override public int getIconHeight() { return 16; }
    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) { }
}

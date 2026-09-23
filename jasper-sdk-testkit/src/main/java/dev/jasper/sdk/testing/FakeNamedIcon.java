package dev.jasper.sdk.testing;

import dev.jasper.sdk.ui.IconName;
import java.awt.Component;
import java.awt.Graphics;
import java.util.Objects;
import javax.swing.Icon;

/**
 * Inspectable, non-rendering selection from the host-owned catalog.
 * @param name requested semantic icon
 * @param retro whether the host selected retro artwork
 * @since 0.7.3
 */
public record FakeNamedIcon(IconName name, boolean retro) implements Icon {
    /** Creates a validated selection. */
    public FakeNamedIcon { Objects.requireNonNull(name, "name"); }
    @Override public int getIconWidth() { return 16; }
    @Override public int getIconHeight() { return 16; }
    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) { }
}

package dev.jasper.app.workspace;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.util.Arrays;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.UIResource;

/**
 * Paints a panel region in the theme's tool window colour, as IntelliJ paints its tool windows. Plain
 * containers inside the region take it while their background is still the look and feel's; controls
 * keep their own colours and a background a plugin set is left alone. Containers added later take it
 * too. A theme change resets it with the other defaults, so the owner reapplies it. EDT only.
 */
final class ToolWindowSurface {
    private static final ContainerListener ADDED = new ContainerListener() {
        @Override public void componentAdded(ContainerEvent event) { paint(event.getChild(), colour()); }
        @Override public void componentRemoved(ContainerEvent event) { }
    };

    private ToolWindowSurface() {}

    static void apply(Component root) { paint(root, colour()); }

    private static ColorUIResource colour() {
        Color colour = UIManager.getColor("ToolWindow.background");
        return new ColorUIResource(colour != null ? colour : UIManager.getColor("Panel.background"));
    }

    private static void paint(Component component, ColorUIResource colour) {
        if (surface(component) && (component.getBackground() == null || component.getBackground() instanceof UIResource))
            component.setBackground(colour);
        if (component instanceof Container container) {
            if (!Arrays.asList(container.getContainerListeners()).contains(ADDED)) container.addContainerListener(ADDED);
            for (Component child : container.getComponents()) paint(child, colour);
        }
    }

    private static boolean surface(Component component) {
        return component instanceof JPanel || component instanceof JViewport || component instanceof JScrollPane
            || component instanceof JToolBar;
    }
}

package dev.jasper.app.workspace;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.util.Arrays;
import javax.swing.CellRendererPane;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.UIResource;

/**
 * Paints a panel region in the theme's tool window colour, as IntelliJ paints its tool windows. Plain
 * containers inside the region take it while their background is still the look and feel's; controls
 * keep their own colours and a background a plugin set is left alone. Containers added later take it
 * too. Lists, tables, trees and combo boxes are left whole: their children are cell renderers and
 * editors, which a renderer pane re-adds on every paint and which carry the owner's row and selection
 * colours. A theme change resets the surface with the other defaults, so the owner reapplies it. EDT only.
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
        if (cells(component)) return;
        if (surface(component) && (component.getBackground() == null || component.getBackground() instanceof UIResource))
            component.setBackground(colour);
        if (component instanceof Container container) {
            if (!Arrays.asList(container.getContainerListeners()).contains(ADDED)) container.addContainerListener(ADDED);
            for (Component child : container.getComponents()) paint(child, colour);
        }
    }

    /** A renderer pane, or a component whose children are its own renderers and editors. */
    private static boolean cells(Component component) {
        return component instanceof CellRendererPane || component instanceof JList || component instanceof JTable
            || component instanceof JTree || component instanceof JComboBox;
    }

    private static boolean surface(Component component) {
        return component instanceof JPanel || component instanceof JViewport || component instanceof JScrollPane
            || component instanceof JToolBar;
    }
}

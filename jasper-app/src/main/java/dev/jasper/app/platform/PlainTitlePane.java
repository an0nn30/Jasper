package dev.jasper.app.platform;

import com.formdev.flatlaf.ui.FlatTitlePane;
import java.awt.Component;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import javax.swing.JLabel;
import javax.swing.JRootPane;

/**
 * Shows a window's title as plain text in FlatLaf's title pane (Windows and Linux, {@link TitleBarStyle#CUSTOM}). The
 * pane's title label never sets {@code html.disable}, so a title starting with {@code <html>} (a tab's, which a program
 * sets, or a plugin's dialog title) would render as HTML. FlatLaf builds a new pane each time the root pane's UI is
 * installed, a theme change included.
 */
public final class PlainTitlePane {
    private static final String INSTALLED = "jasper.plainTitlePane";

    private PlainTitlePane() {}

    /**
     * Makes the title pane {@code root} has, and every one FlatLaf adds later, show its title as plain text; once per
     * root, before its window shows. A root without FlatLaf's decorations is left as it is.
     *
     * @param root the window's root pane
     */
    public static void install(JRootPane root) {
        if (root.getClientProperty(INSTALLED) != null) return;
        root.putClientProperty(INSTALLED, Boolean.TRUE);
        var layered = root.getLayeredPane();
        layered.addContainerListener(new ContainerAdapter() {
            @Override public void componentAdded(ContainerEvent event) { plain(event.getChild()); }
        });
        for (Component child : layered.getComponents()) plain(child);
    }

    private static void plain(Component child) {
        if (!(child instanceof FlatTitlePane pane)) return;
        for (Component part : pane.getComponents()) {
            if (!(part instanceof JLabel label) || Boolean.TRUE.equals(label.getClientProperty("html.disable"))) continue;
            label.putClientProperty("html.disable", Boolean.TRUE);
            // A pane added to a shown window already holds the title: setting it again rebuilds the label's view.
            String title = label.getText();
            label.setText(null);
            label.setText(title);
        }
    }
}

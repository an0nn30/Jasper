package dev.jasper.app.plugins;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.UIManager;

/**
 * A running plugin's page body: the message about a rejected settings file above the page's content, both at the top.
 * It follows the dialog's width but not its height, so the content keeps its preferred height. EDT only.
 */
final class PluginPageBody extends JPanel implements Scrollable {
    PluginPageBody(JLabel message, JComponent content) {
        super(new BorderLayout());
        setOpaque(false);
        var top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(message, BorderLayout.NORTH);
        top.add(content, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
    }

    /** The message a plugin's page shows while the plugin's settings file has errors. */
    static JLabel message() {
        var message = new JLabel("This plugin's settings file has errors; fix them before changing its settings here.");
        message.putClientProperty("html.disable", true);
        Color red = UIManager.getColor("Jasper.configErrorForeground");
        message.setForeground(red != null ? red : Color.RED);
        message.setBorder(BorderFactory.createEmptyBorder(12, 20, 0, 20));
        return message;
    }

    /** Shows {@code message} exactly while the plugin's settings file is rejected. */
    static void showMessage(JLabel message, DeclaredSettings declared) { message.setVisible(declared.rejected()); }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return visible.height; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}

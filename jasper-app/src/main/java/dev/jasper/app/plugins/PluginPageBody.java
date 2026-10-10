package dev.jasper.app.plugins;

import java.awt.BorderLayout;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * A running plugin's page body: the message about a rejected settings file above the page's content, both at the top.
 * It follows the dialog's width but not its height, so the content keeps its preferred height. EDT only.
 */
final class PluginPageBody extends JPanel implements Scrollable {
    PluginPageBody(KitView message, JComponent content) {
        super(new BorderLayout());
        setOpaque(false);
        var top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(message, BorderLayout.NORTH);
        top.add(content, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
    }

    /** The message a plugin's page shows while the plugin's settings file has errors. */
    static KitView message(KitContext kit) {
        return text(kit, "This plugin's settings file has errors; fix them before changing its settings here.", true);
    }

    /** A native host message owns its kit view separately from the plugin's view. */
    static KitView text(KitContext kit, String text, boolean error) {
        return new KitView(kit, new ViewSpec(new Nodes.Stack("plugin-page-message", ControlState.READY, Nodes.LayoutRole.DETAIL,
            List.of(new Nodes.Text("plugin-page-text", ControlState.READY, text, Nodes.TextRole.BODY,
                error ? Optional.of(Nodes.Severity.DANGER) : Optional.empty(), OptionalInt.empty())))), event -> {});
    }

    /** Shows the host-owned message exactly while the settings file is rejected. */
    static void showMessage(KitView message, DeclaredSettings declared) { if (message != null) message.setVisible(declared.rejected()); }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return Math.max(1, getFontMetrics(getFont()).getHeight()); }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return visible.height; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}

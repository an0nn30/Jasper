package dev.jasper.app.settingsui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * A page body that fills the dialog's page area instead of growing it, for pages such as Keymap and Plugins that scroll
 * their own lists. EDT only.
 */
public final class FillingPage extends JPanel implements Scrollable {
    public FillingPage(JComponent content) {
        super(new BorderLayout());
        setOpaque(false);
        add(content, BorderLayout.CENTER);
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
    }
    /** A filled page still respects the receiving controls' minimum; the existing outer viewport owns overflow. */
    @Override public boolean getScrollableTracksViewportWidth() {
        return !(getParent() instanceof JViewport viewport) || viewport.getWidth() >= getMinimumSize().width;
    }
    @Override public Dimension getPreferredSize() {
        var size=super.getPreferredSize();
        if(getParent() instanceof JViewport viewport && viewport.getWidth()<getMinimumSize().width)
            size.width=getMinimumSize().width;
        return size;
    }
    @Override public boolean getScrollableTracksViewportHeight() { return true; }
}

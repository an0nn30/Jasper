package dev.jasper.app.settingsui;

import dev.jasper.ui.swing.KitView;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * Holds a page's kit view in the dialog's page scroller. The page follows the dialog's width while it fits; narrower
 * than its controls need, it scrolls sideways rather than squashing them. Layout only: the kit view owns all styling.
 */
final class PageBody extends JPanel implements Scrollable {
    final KitView view;

    PageBody(KitView view) {
        super(new BorderLayout());
        this.view = view;
        setOpaque(false);
        add(view, BorderLayout.CENTER);
    }

    /** The shared Settings layout wraps controls at the available width. */
    @Override public Dimension getMinimumSize() { return view.getMinimumSize(); }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    /** A line of the page's text per step, so scrolling follows the user's font size. */
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(1, getFontMetrics(getFont()).getHeight());
    }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
    }
    @Override public boolean getScrollableTracksViewportWidth() {
        return !(getParent() instanceof JViewport viewport) || viewport.getWidth() >= getMinimumSize().width;
    }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}

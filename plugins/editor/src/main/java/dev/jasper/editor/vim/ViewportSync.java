package dev.jasper.editor.vim;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;

/**
 * Keeps the text area's scroll pane and {@link View} in step (spec 5.1): the view's rows and columns follow the
 * viewport's size in lines and characters, after each command the viewport shows the view's first line (and, without
 * 'wrap', its first column), and when the user scrolls the viewport (the wheel, the scroll bar) the view follows and
 * the cursor stays inside it, as Vim keeps it inside the window.
 */
final class ViewportSync {
    private final VimEditor editor;
    private final VimTextArea area;
    private JViewport viewport;
    private boolean moving;
    private final java.beans.PropertyChangeListener typographyChanged = event -> resize(true);
    private final ChangeListener scrolled = event -> userScrolled();
    private final ComponentListener resized = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { resize(); }
    };
    /** The text area may be put into a scroll pane after Vim turns on. */
    private final java.awt.event.HierarchyListener moved = event -> viewport();

    ViewportSync(VimEditor editor, VimTextArea area) {
        this.editor = editor;
        this.area = area;
        area.addHierarchyListener(moved);
        area.addPropertyChangeListener("editorTypography", typographyChanged);
        viewport();
    }

    /** @return the viewport the text area is in, listening to it once found; null when it is not in one */
    private JViewport viewport() {
        JViewport found = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, area);
        if (found != viewport) {
            detach();
            viewport = found;
            editor.view().physical(viewport != null);
            if (viewport != null) {
                viewport.addChangeListener(scrolled);
                viewport.addComponentListener(resized);
                resize();
            }
        }
        return viewport;
    }

    /** Vim turned off: nothing is listened to any more. */
    void close() {
        area.removeHierarchyListener(moved);
        area.removePropertyChangeListener("editorTypography", typographyChanged);
        editor.view().physical(false);
        detach();
    }

    private void detach() {
        if (viewport == null) return;
        viewport.removeChangeListener(scrolled);
        viewport.removeComponentListener(resized);
        viewport = null;
    }

    /**
     * The view's size from the viewport: whole lines and whole characters of the Editor's font (the text area's own
     * left and right insets hold no text). When the size changes, the view scrolls to keep the cursor shown, and the
     * text area's room below the last line follows the new height.
     */
    void resize() { resize(false); }

    private void resize(boolean force) {
        JViewport port = viewport;
        if (port == null) return;
        Rectangle visible = port.getViewRect();
        java.awt.Insets insets = area.getInsets();
        int lineHeight = Math.max(1, area.getLineHeight());
        int charWidth = Math.max(1, area.getFontMetrics(area.getFont()).charWidth('m'));
        int textWidth = visible.width - insets.left - insets.right;
        if (visible.height <= 0 || textWidth <= 0) return;
        View view = editor.view();
        int rows = Math.max(1, visible.height / lineHeight), columns = Math.max(1, textWidth / charWidth);
        area.revalidate();
        if (!force && rows == view.height() && columns == view.width()) return;
        view.resize(rows, columns);
        editor.viewResized();
    }

    /** After a command: the viewport shows the view's first line and column. */
    void show() {
        JViewport port = viewport();
        if (port == null) return;
        View view = editor.view();
        try {
            int y = area.yForLine(view.topline - 1);
            if (y < 0) return;
            int charWidth = Math.max(1, area.getFontMetrics(area.getFont()).charWidth('m'));
            int x = editor.options().flag("wrap") ? 0 : view.leftcol * charWidth;
            Point target = new Point(x, y);
            if (!target.equals(port.getViewPosition())) {
                moving = true;
                try {
                    // The text area's room below the last line (VimTextArea's preferred size) must be laid out before
                    // a first line near the end can be shown, or the scroll pane would pull the view back.
                    if (area.getHeight() < area.getPreferredSize().height) port.doLayout();
                    port.setViewPosition(target);
                } finally { moving = false; }
            }
        } catch (BadLocationException ignored) { }
    }

    /** The user scrolled: the view's first line follows, and the cursor moves inside the window. */
    private void userScrolled() {
        if (moving || viewport == null) return;
        int top = topline(viewport.getViewPosition().y);
        if (top < 0 || top == editor.view().topline) return;
        editor.viewScrolled(top);
    }

    /**
     * Another view's edit puts the viewport back on the line it showed first (pane flexibility spec 6): it shows
     * {@code position}, and the cursor does not follow, as it follows a user's scroll; the editor places it after.
     *
     * @param position the viewport position to show
     * @return the window's first line there, as Vim numbers lines; -1 when the text area is in no viewport
     */
    int keep(Point position) {
        JViewport port = viewport();
        if (port == null) return -1;
        moving = true;
        try { port.setViewPosition(position); } finally { moving = false; }
        return topline(position.y);
    }

    /** @return the first line shown whole from {@code y} down, as Vim numbers lines; -1 when there is none */
    private int topline(int y) {
        int offset = area.viewToModel2D(new Point(0, y));
        int line;
        try { line = area.getLineOfOffset(Math.max(0, offset)); }
        catch (BadLocationException bad) { return -1; }
        int top = line + 1;
        // The first line only partly shown does not count, as Vim shows whole lines.
        try {
            if (area.yForLine(line) < y && top < editor.buffer().count()) top++;
        } catch (BadLocationException ignored) { }
        return top;
    }
}

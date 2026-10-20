package dev.jasper.app.windows;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Objects;
import javax.swing.JComponent;

/**
 * Keeps a dialog the host packed itself fitted to its content: when the mounted kit view's preferred size changes (a
 * form revealing or hiding rows, a message line appearing), the dialog re-packs on the next event-loop turn, growing or
 * shrinking, clamped to its screen's usable bounds and moved only as far as needed to stay on that screen. This is what
 * plugin dialogs did with {@code getWindowAncestor(this).pack()} before their content became kit views, which cannot
 * reach their window. Once the user resizes the dialog (any size the rule did not set), the rule stops for good. Dialogs
 * with remembered bounds never get one. EDT only.
 */
public final class DialogFit {
    /** What the rule needs from a native dialog. */
    public interface Target {
        /** The dialog's current size. */
        Dimension size();
        /** The size {@code pack()} would give it now (frame insets around the content's preferred size). */
        Dimension packedSize();
        /** The usable area of the dialog's screen (screen bounds less task bars and menu bars). */
        Rectangle usableBounds();
        /** The dialog's top-left corner. */
        Point location();
        /** Moves and resizes the dialog. */
        void setBounds(Rectangle bounds);
    }

    private final JComponent content;
    private final Target target;
    private Dimension expected, preferred;
    private boolean active, userResized, scheduled, closed;

    /** {@code content} is the surface's holder, whose preferred size is the mounted view's. */
    public DialogFit(JComponent content, Target target) {
        this.content = Objects.requireNonNull(content); this.target = Objects.requireNonNull(target);
    }

    /** The host has just packed the dialog (at show): its size is the rule's own, and later content changes follow it. */
    public void packed() {
        if (closed) return;
        active = true;
        expected = target.size();
        // Valid, so the content's next invalidation reaches the holder and is noticed.
        content.validate();
        preferred = content.getPreferredSize();
    }

    /** The native dialog reports its size; one the rule did not set means the user resized it, and the rule stops. */
    public void resized(Dimension size) {
        if (active && !closed && expected != null && !expected.equals(size)) userResized = true;
    }

    /** Whether the user has resized the dialog, so the rule no longer applies. */
    public boolean userResized() { return userResized; }

    /** The content's layout was invalidated; a changed preferred size re-packs on the next event-loop turn. */
    public void contentChanged() {
        if (!active || closed || userResized || scheduled) return;
        scheduled = true;
        EventQueue.invokeLater(this::fit);
    }

    /** The dialog closed: nothing follows any more. */
    public void close() { closed = true; }

    private void fit() {
        scheduled = false;
        if (closed || userResized) return;
        content.validate();
        Dimension now = content.getPreferredSize();
        if (now.equals(preferred)) return;
        preferred = now;
        Dimension packed = target.packedSize();
        Rectangle usable = target.usableBounds();
        int width = Math.min(packed.width, usable.width), height = Math.min(packed.height, usable.height);
        Point at = target.location();
        int x = Math.max(usable.x, Math.min(at.x, usable.x + usable.width - width));
        int y = Math.max(usable.y, Math.min(at.y, usable.y + usable.height - height));
        expected = new Dimension(width, height);
        target.setBounds(new Rectangle(x, y, width, height));
    }
}

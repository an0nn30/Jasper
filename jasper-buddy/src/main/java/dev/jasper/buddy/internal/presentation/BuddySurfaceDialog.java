package dev.jasper.buddy.internal.presentation;

import java.awt.Graphics;
import javax.swing.JDialog;

/** Focusable ownerless fallback with the same translucent composition as owned surfaces. */
final class BuddySurfaceDialog extends JDialog {
    private final BuddyFrameBuffer buffer = new BuddyFrameBuffer();
    BuddySurfaceDialog() {
        super((java.awt.Window) null);
        setUndecorated(true);
        getRootPane().setDoubleBuffered(false);
    }
    @Override public void paint(Graphics graphics) {
        buffer.paint(graphics, getWidth(), getHeight(), super::paint);
    }
    @Override public void dispose() { buffer.dispose(); super.dispose(); }
}

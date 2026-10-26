package dev.jasper.buddy.internal.presentation;

import java.awt.Graphics;
import javax.swing.JWindow;

/** Composes a translucent window before presenting its pixels to the native surface. */
final class BuddySurfaceWindow extends JWindow {
    private final BuddyFrameBuffer buffer = new BuddyFrameBuffer();

    BuddySurfaceWindow() { this(null); }

    BuddySurfaceWindow(java.awt.Window owner) {
        super(owner);
        // X11's Swing buffer is opaque; compose Window.paint's background clear offscreen too.
        getRootPane().setDoubleBuffered(false);
    }

    @Override public void paint(Graphics graphics) {
        buffer.paint(graphics, getWidth(), getHeight(), super::paint);
    }

    @Override public void dispose() {
        buffer.dispose();
        super.dispose();
    }
}

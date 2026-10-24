package dev.jasper.buddy.internal.presentation;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/** EDT-owned device-resolution storage for one complete translucent window frame. */
final class BuddyFrameBuffer {
    private BufferedImage image;

    void paint(Graphics graphics, int width, int height, Consumer<Graphics2D> painter) {
        if (width <= 0 || height <= 0) return;
        Graphics2D target = (Graphics2D) graphics;
        double scaleX = target.getTransform().getScaleX();
        double scaleY = target.getTransform().getScaleY();
        int pixelWidth = (int) Math.ceil(width * scaleX);
        int pixelHeight = (int) Math.ceil(height * scaleY);
        if (image == null || image.getWidth() != pixelWidth || image.getHeight() != pixelHeight) {
            dispose();
            image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB_PRE);
        }
        Graphics2D frame = image.createGraphics();
        try {
            frame.setComposite(AlphaComposite.Clear);
            frame.fillRect(0, 0, pixelWidth, pixelHeight);
            frame.setComposite(AlphaComposite.SrcOver);
            frame.scale(scaleX, scaleY);
            frame.clipRect(0, 0, width, height);
            painter.accept(frame);
        } finally { frame.dispose(); }

        Graphics2D output = (Graphics2D) target.create();
        try {
            output.clipRect(0, 0, width, height);
            output.setComposite(AlphaComposite.Src);
            output.drawImage(image, AffineTransform.getScaleInstance(1 / scaleX, 1 / scaleY), null);
        } finally { output.dispose(); }
    }

    void dispose() {
        if (image == null) return;
        image.flush();
        image = null;
    }
}

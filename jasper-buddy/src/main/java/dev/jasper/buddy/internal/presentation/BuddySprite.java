package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.animation.BuddyFrame;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

/** The shaded pixel-art strip: two source pixels per logical pixel, with stable companion geometry. */
final class BuddySprite {
    static final int FRAME_WIDTH = 42;
    static final int FRAME_HEIGHT = 48;
    static final int ART_SCALE = 2;
    static final int ART_WIDTH = FRAME_WIDTH * ART_SCALE;
    static final int ART_HEIGHT = FRAME_HEIGHT * ART_SCALE;
    static final String RESOURCE = "jasper-buddy.png";

    private final Map<BuddyFrame, BufferedImage> frames = new EnumMap<>(BuddyFrame.class);

    BuddySprite(BufferedImage sheet) {
        Objects.requireNonNull(sheet, "sheet");
        int expectedWidth = ART_WIDTH * BuddyFrame.values().length;
        if (sheet.getWidth() != expectedWidth || sheet.getHeight() != ART_HEIGHT) {
            throw new IllegalStateException("Buddy sprite must be " + expectedWidth + "x" + ART_HEIGHT
                + " but is " + sheet.getWidth() + "x" + sheet.getHeight());
        }
        for (BuddyFrame frame : BuddyFrame.values()) {
            BufferedImage copy = new BufferedImage(ART_WIDTH, ART_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(sheet, 0, 0, ART_WIDTH, ART_HEIGHT,
                frame.column() * ART_WIDTH, 0, (frame.column() + 1) * ART_WIDTH, ART_HEIGHT, null);
            g.dispose();
            frames.put(frame, copy);
        }
    }

    static BuddySprite load() {
        try (InputStream input = BuddySprite.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing buddy sprite resource " + RESOURCE);
            BufferedImage sheet = ImageIO.read(input);
            if (sheet == null) throw new IllegalStateException("Unreadable buddy sprite resource " + RESOURCE);
            return new BuddySprite(sheet);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read buddy sprite resource " + RESOURCE, failure);
        }
    }

    BufferedImage frame(BuddyFrame frame) { return frames.get(frame); }

    static Dimension size(double scale) {
        return new Dimension((int) Math.round(FRAME_WIDTH * scale), (int) Math.round(FRAME_HEIGHT * scale));
    }

    /** Draws pixel artwork with hard edges into the existing logical frame size. */
    void paint(Graphics2D g, BuddyFrame frame, double scale, int x, int y) {
        Graphics2D copy = (Graphics2D) g.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            Dimension size = size(scale);
            copy.drawImage(frames.get(frame), x, y, size.width, size.height, null);
        } finally { copy.dispose(); }
    }
}

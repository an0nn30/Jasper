package dev.jasper.app;

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

/** The committed pixel-art strip: validates, slices and paints frames without smoothing. */
final class BuddySprite {
    static final int FRAME_WIDTH = 42;
    static final int FRAME_HEIGHT = 48;
    static final String RESOURCE = "/dev/jasper/app/buddy/jasper-buddy.png";

    private final Map<BuddyFrame, BufferedImage> frames = new EnumMap<>(BuddyFrame.class);

    BuddySprite(BufferedImage sheet) {
        Objects.requireNonNull(sheet, "sheet");
        int expectedWidth = FRAME_WIDTH * BuddyFrame.values().length;
        if (sheet.getWidth() != expectedWidth || sheet.getHeight() != FRAME_HEIGHT) {
            throw new IllegalStateException("Buddy sprite must be " + expectedWidth + "x" + FRAME_HEIGHT
                + " but is " + sheet.getWidth() + "x" + sheet.getHeight());
        }
        for (BuddyFrame frame : BuddyFrame.values()) {
            BufferedImage copy = new BufferedImage(FRAME_WIDTH, FRAME_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(sheet, 0, 0, FRAME_WIDTH, FRAME_HEIGHT,
                frame.column() * FRAME_WIDTH, 0, (frame.column() + 1) * FRAME_WIDTH, FRAME_HEIGHT, null);
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

    static Dimension size(int scale) { return new Dimension(FRAME_WIDTH * scale, FRAME_HEIGHT * scale); }

    /** Draws one frame at an integer scale; every art pixel becomes a solid scale-by-scale block. */
    void paint(Graphics2D g, BuddyFrame frame, int scale, int x, int y) {
        Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frames.get(frame), x, y, FRAME_WIDTH * scale, FRAME_HEIGHT * scale, null);
        if (previous != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
    }
}

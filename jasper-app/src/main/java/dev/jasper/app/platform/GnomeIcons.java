package dev.jasper.app.platform;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.Icon;

/** Self-contained, full-color raster artwork. Source and license travel with the resources. */
final class GnomeIcons {
    static final Set<String> NAMES = Set.of("square-plus", "app-window", "columns-2", "maximize",
        "search", "settings", "refresh", "command", "history", "bookmark", "close");
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();
    private GnomeIcons() {}
    static Icon icon(String name) {
        if (!NAMES.contains(name)) throw new IllegalArgumentException("Unknown application icon: " + name);
        return CACHE.computeIfAbsent(name, key -> new Raster(read(key, 16), read(key, 24)));
    }
    private static BufferedImage read(String name, int size) {
        String path = "/dev/jasper/app/icons/gnome2/" + size + "/" + name + ".png";
        try (var input = GnomeIcons.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing bundled icon: " + path);
            var image = ImageIO.read(input);
            if (image == null || image.getWidth() != size || image.getHeight() != size)
                throw new IllegalStateException("Invalid bundled icon: " + path);
            return image;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private record Raster(BufferedImage small, BufferedImage large) implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            var g = (Graphics2D) graphics.create();
            try {
                var transform = g.getTransform();
                double scale = Math.max(Math.hypot(transform.getScaleX(), transform.getShearY()),
                    Math.hypot(transform.getShearX(), transform.getScaleY()));
                var image = scale <= 1 ? small : large;
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, x, y, 16, 16, component);
            } finally { g.dispose(); }
        }
    }
}

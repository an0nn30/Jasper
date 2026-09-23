package dev.jasper.app.platform;

import java.awt.image.BufferedImage;
import java.awt.image.BaseMultiResolutionImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** Self-contained, full-color raster artwork. Source and license travel with the resources. */
final class GnomeIcons {
    static final Set<String> NAMES = Set.of("square-plus", "app-window", "columns-2", "maximize",
        "search", "settings", "refresh", "command", "history", "bookmark", "close");
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();
    private GnomeIcons() {}
    static Icon icon(String name) { return icon(name, 16); }
    static Icon icon(String name, int size) {
        if (!NAMES.contains(name)) throw new IllegalArgumentException("Unknown application icon: " + name);
        if (size != 16 && size != 24) throw new IllegalArgumentException("Unsupported icon size: " + size);
        // ImageIcon lets Metal generate its native disabled variant. Keep resolution selection in the JDK.
        return CACHE.computeIfAbsent(name + "/" + size, key -> new ImageIcon(size == 16
            ? new BaseMultiResolutionImage(read(name, 16), read(name, 24)) : read(name, 24)));
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
}

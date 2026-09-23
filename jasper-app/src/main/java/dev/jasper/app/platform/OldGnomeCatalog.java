package dev.jasper.app.platform;

import java.awt.RenderingHints;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/** Explicit OldGNOME2 SDK catalog; never substitutes other icon families. */
final class OldGnomeCatalog {
    private static final Map<String, List<Integer>> SOURCES = Map.ofEntries(
        Map.entry("LOCK", java.util.List.of(16, 24, 32, 48)),
        Map.entry("UNLOCK", java.util.List.of(16, 24, 32, 48)),
        Map.entry("KEY", java.util.List.of(16, 24, 32, 48)),
        Map.entry("FOLDER", java.util.List.of(16, 24, 48)),
        Map.entry("SAVE", java.util.List.of(16, 24)),
        Map.entry("SEARCH", java.util.List.of(16, 24)),
        Map.entry("HISTORY", java.util.List.of(16, 24)),
        Map.entry("BOOKMARK", java.util.List.of(16, 24, 32, 48)),
        Map.entry("ADD", java.util.List.of(16, 24)),
        Map.entry("REMOVE", java.util.List.of(16, 24)),
        Map.entry("DELETE", java.util.List.of(16, 24)),
        Map.entry("COPY", java.util.List.of(16, 24)),
        Map.entry("PASTE", java.util.List.of(16, 24)),
        Map.entry("REFRESH", java.util.List.of(16, 24)),
        Map.entry("SETTINGS", java.util.List.of(16, 24)),
        Map.entry("EXECUTE", java.util.List.of(16, 24)),
        Map.entry("CONNECT", java.util.List.of(16, 24)),
        Map.entry("DISCONNECT", java.util.List.of(16, 24)),
        Map.entry("NETWORK", java.util.List.of(16, 24, 32, 48)),
        Map.entry("INFO", java.util.List.of(16, 24)),
        Map.entry("HELP", java.util.List.of(16, 24)),
        Map.entry("CLOSE", java.util.List.of(16, 24)));
    static final Set<String> NAMES = SOURCES.keySet();
    private static final Map<String, ImageIcon> CACHE = new ConcurrentHashMap<>();
    private OldGnomeCatalog() { }

    static ImageIcon icon(String name, int size) {
        if (name == null || !NAMES.contains(name)) throw new IllegalArgumentException("Unknown OldGNOME2 icon: " + name);
        if (size != 16 && size != 28) throw new IllegalArgumentException("Unsupported icon size: " + size);
        return CACHE.computeIfAbsent(name + "/" + size, key -> new ImageIcon(
            new BaseMultiResolutionImage(image(name, size), image(name, size * 2))));
    }

    private static BufferedImage image(String name, int target) {
        var sizes = SOURCES.get(name);
        int size = sizes.stream().filter(value -> value >= target).findFirst().orElse(sizes.getLast());
        String resource = "/dev/jasper/app/icons/oldgnome-sdk/" + size + "/" + name + ".png";
        try (var input = OldGnomeCatalog.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing bundled icon: " + resource);
            var source = ImageIO.read(input);
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0
                    || Math.max(source.getWidth(), source.getHeight()) != size)
                throw new IllegalStateException("Invalid bundled icon: " + resource);
            if (target == source.getWidth() && target == source.getHeight()) return source;
            var result = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
            var graphics = result.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                double scale = (double) target / size;
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                graphics.drawImage(source, (target - width) / 2, (target - height) / 2, width, height, null);
            } finally { graphics.dispose(); }
            return result;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}

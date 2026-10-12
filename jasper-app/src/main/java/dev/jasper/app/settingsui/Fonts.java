package dev.jasper.app.settingsui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Installed and bundled font families, read once; the monospaced subset draws 'i' and 'W' at the same width. Reading
 * them takes a few hundred milliseconds, so {@link #prepare()} reads them off the EDT before Settings needs them.
 */
final class Fonts {
    private static List<String> all;
    private static List<String> monospaced;
    private static boolean preparing;

    private Fonts() { }

    /** Reads the families once on a low-priority daemon thread; a caller that needs them meanwhile waits for it. */
    static synchronized void prepare() {
        if (preparing || all != null) return;
        preparing = true;
        Thread.ofPlatform().daemon().priority(Thread.MIN_PRIORITY).name("jasper-font-families").start(() -> families(false));
    }

    static synchronized List<String> families(boolean monospacedOnly) {
        if (all == null) {
            dev.jasper.app.platform.BundledFonts.register();
            all = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            var graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {
                var fixed = new ArrayList<String>();
                for (String family : all) {
                    var metrics = graphics.getFontMetrics(new Font(family, Font.PLAIN, 12));
                    if (metrics.charWidth('i') == metrics.charWidth('W')) fixed.add(family);
                }
                monospaced = List.copyOf(fixed);
            } finally { graphics.dispose(); }
        }
        return monospacedOnly ? monospaced : all;
    }
}

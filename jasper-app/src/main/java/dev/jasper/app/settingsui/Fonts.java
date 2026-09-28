package dev.jasper.app.settingsui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Installed font families, read once; the monospaced subset draws 'i' and 'W' at the same width. */
final class Fonts {
    private static List<String> all;
    private static List<String> monospaced;

    private Fonts() { }

    static synchronized List<String> families(boolean monospacedOnly) {
        if (all == null) {
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

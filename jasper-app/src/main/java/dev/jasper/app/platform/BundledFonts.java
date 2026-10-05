package dev.jasper.app.platform;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.List;

/** Application fonts registered in this JVM only; no system installation or user files are changed. */
public final class BundledFonts {
    private static boolean registered;

    private BundledFonts() { }

    /** Loads the bundled terminal family once, before font lookup, measurement or font-picker enumeration. */
    public static synchronized void register() {
        if (registered) return;
        var environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String style : List.of("Regular", "Bold", "Italic", "BoldItalic")) {
            String resource = "/dev/jasper/app/fonts/cousine/CousineNerdFontMono-" + style + ".ttf";
            try (var stream = BundledFonts.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Missing bundled font: " + resource);
                // An installed face with the same name may already exist; a false return is harmless.
                environment.registerFont(Font.createFont(Font.TRUETYPE_FONT, stream));
            } catch (IOException | FontFormatException failure) {
                throw new IllegalStateException("Could not load bundled terminal font: " + resource, failure);
            }
        }
        registered = true;
    }
}

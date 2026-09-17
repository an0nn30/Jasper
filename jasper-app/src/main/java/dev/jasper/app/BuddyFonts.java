package dev.jasper.app;

import java.awt.Font;
import java.util.Locale;
import javax.swing.UIManager;

/**
 * The OS system font for the buddy's surfaces. Asking the look and feel is not the same as asking the
 * system: FlatLaf sets {@code Label.font} to Helvetica Neue, which is a real face but the pre-2015
 * macOS system font, so a bubble that trusts it looks a decade old rather than obviously broken.
 */
final class BuddyFonts {
    /**
     * The only name under which macOS exposes its system font to Java. "SF Pro" and "SF Pro Text"
     * resolve to Dialog instead — silently, which is how a wrong choice goes unnoticed.
     */
    static final String MAC_SYSTEM_FONT = ".AppleSystemUIFont";

    private BuddyFonts() { }

    /** The system font, falling back to the look and feel's label font and then to a generic sans. */
    static Font system(int style, float size) {
        Font system = macSystemFont();
        if (system != null) return system.deriveFont(style, size);
        Font label = UIManager.getFont("Label.font");
        return label != null ? label.deriveFont(style, size) : new Font(Font.SANS_SERIF, style, (int) size);
    }

    /**
     * Null unless the request actually resolved. Java never fails a font lookup — it answers with
     * Dialog — so the resolved family is the only honest evidence that the name exists.
     */
    private static Font macSystemFont() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")) return null;
        Font candidate = new Font(MAC_SYSTEM_FONT, Font.PLAIN, 13);
        return candidate.getFamily().equalsIgnoreCase(Font.DIALOG) ? null : candidate;
    }
}

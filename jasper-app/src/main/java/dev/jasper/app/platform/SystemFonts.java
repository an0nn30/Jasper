package dev.jasper.app.platform;

import java.awt.Font;
import java.util.Locale;
import javax.swing.UIManager;

/**
 * The OS system font for title bars and buddy surfaces. Asking the look and feel is not the same as asking the
 * system: FlatLaf sets {@code Label.font} to Helvetica Neue, which is a real face but the pre-2015
 * macOS system font, so a bubble that trusts it looks a decade old rather than obviously broken.
 */
public final class SystemFonts {
    /**
     * The only name under which macOS exposes its system font to Java. "SF Pro" and "SF Pro Text"
     * resolve to Dialog instead — silently, which is how a wrong choice goes unnoticed.
     */
    static final String MAC_SYSTEM_FONT = ".AppleSystemUIFont";

    private SystemFonts() { }

    /** The system font, falling back to the look and feel's label font and then to a generic sans. */
    public static Font system(int style, float size) {
        Font system = macSystemFont();
        if (system != null) return system.deriveFont(style, size);
        Font label = UIManager.getFont("Label.font");
        return label != null ? label.deriveFont(style, size) : new Font(Font.SANS_SERIF, style, (int) size);
    }

    /** App chrome follows UI typography while retaining the native title font by default. */
    public static Font ui(int style, float logicalSize) {
        Font label = UIManager.getFont("Label.font");
        float size = label.getSize2D() + com.formdev.flatlaf.util.UIScale.scale(logicalSize - 12f);
        return Boolean.TRUE.equals(UIManager.get("Jasper.uiFontFamilyOverride"))
            ? label.deriveFont(style, size) : system(style, size);
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

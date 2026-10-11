package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.Typography;
import java.awt.Font;
import javax.swing.UIManager;

/** Shared bridge from host font provenance to logical roles and effective Swing point sizes. */
final class SwingTypography {
    private SwingTypography() {}
    static boolean fontProperty(String name) {
        return "defaultFont".equals(name)||"Label.font".equals(name)||"Jasper.uiFontFamilyOverride".equals(name);
    }
    private static Font[] overrides() {
        Font[] result = {null, null};
        // Enumerate only developer defaults: the LAF's inherited fonts are already scaled.
        UIManager.getDefaults().forEach((key, value) -> {
            if ("defaultFont".equals(key) && value instanceof Font font) result[0] = font;
            if ("Label.font".equals(key) && value instanceof Font font) result[1] = font;
        });
        return result;
    }
    static Typography typography() {
        Font[] fonts = overrides();
        if (fonts[0] == null) return new Typography();
        float size = (fonts[1] == null ? fonts[0] : fonts[1]).getSize2D();
        return Boolean.FALSE.equals(UIManager.get("Jasper.uiFontFamilyOverride"))
            ? new Typography(size) : new Typography(fonts[0].getFamily(), size);
    }
    static Font effective(Font role) {
        // A host override is an exact effective form-font size, including fractions. UIScale may
        // itself derive from that size; applying it again would turn configured 20pt into 30pt.
        // Unset catalog fonts receive user scaling once. Geometry and Java2D device scaling stay
        // independent, including when an explicit flatlaf.uiScale accompanies a font override.
        return overrides()[0] != null ? role : role.deriveFont(UIScale.scale(role.getSize2D()));
    }
}

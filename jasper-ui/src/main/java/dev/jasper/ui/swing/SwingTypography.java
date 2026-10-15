package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.Typography;
import java.awt.Font;
import java.util.Optional;
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
    static Typography typography(Kit kit) {
        return Typography.of(kit.type(), kit.faces(), user());
    }
    /**
     * The user's font settings as the host applied them: none without a developer {@code defaultFont}; otherwise its size
     * ({@code Label.font}'s when that is set too) and, unless {@code Jasper.uiFontFamilyOverride} is false, its family.
     */
    static Typography.UserFont user() {
        Font[] fonts = overrides();
        if (fonts[0] == null) return Typography.UserFont.NONE;
        Optional<Float> size = Optional.of((fonts[1] == null ? fonts[0] : fonts[1]).getSize2D());
        return Boolean.FALSE.equals(UIManager.get("Jasper.uiFontFamilyOverride"))
            ? new Typography.UserFont(Optional.empty(), size) : new Typography.UserFont(Optional.of(fonts[0].getFamily()), size);
    }
    static Font effective(Font role) {
        // A host override is an exact effective form-font size, including fractions. UIScale may
        // itself derive from that size; applying it again would turn configured 20pt into 30pt.
        // Unset catalog fonts receive user scaling once. Geometry and Java2D device scaling stay
        // independent, including when an explicit flatlaf.uiScale accompanies a font override.
        return overrides()[0] != null ? role : role.deriveFont(UIScale.scale(role.getSize2D()));
    }
}

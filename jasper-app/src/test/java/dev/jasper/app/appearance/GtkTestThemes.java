package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import java.awt.Color;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.metal.MetalLookAndFeel;

/** Package-local test access, excluded from production artifacts: a GTK-style controller on any host. */
public final class GtkTestThemes {
    private GtkTestThemes() {}

    /** Adwaita-like samples. */
    public static final Map<String, Color> LIGHT = Map.of(
        GtkDefaults.PANEL_BACKGROUND, new Color(0xf6f5f4), GtkDefaults.LABEL_FOREGROUND, new Color(0x2e3436),
        GtkDefaults.LIST_SELECTION_BACKGROUND, new Color(0x3584e4), GtkDefaults.LIST_SELECTION_FOREGROUND, Color.WHITE,
        GtkDefaults.TEXT_BACKGROUND, Color.WHITE, GtkDefaults.TEXT_FOREGROUND, Color.BLACK,
        GtkDefaults.TEXT_CARET, Color.BLACK, GtkDefaults.TEXT_SELECTION_BACKGROUND, new Color(0x3584e4));

    /** Adwaita-dark-like samples. */
    public static final Map<String, Color> DARK = Map.of(
        GtkDefaults.PANEL_BACKGROUND, new Color(0x353535), GtkDefaults.LABEL_FOREGROUND, new Color(0xeeeeec),
        GtkDefaults.LIST_SELECTION_BACKGROUND, new Color(0x15539e), GtkDefaults.LIST_SELECTION_FOREGROUND, Color.WHITE,
        GtkDefaults.TEXT_BACKGROUND, new Color(0x2d2d2d), GtkDefaults.TEXT_FOREGROUND, Color.WHITE,
        GtkDefaults.TEXT_CARET, Color.WHITE, GtkDefaults.TEXT_SELECTION_BACKGROUND, new Color(0x15539e));

    /** Metal delegates decorated exactly as GTK would be, so native-chrome and icon paths run headless. */
    public static ThemeController themes(Map<String, Color> colors) {
        return new ThemeController(ThemeStyle.GTK, Appearance.DARK, theme -> {
            if (theme != BuiltinTheme.GTK) return ThemeController.install(theme);
            try { UIManager.setLookAndFeel(new MetalLookAndFeel()); }
            catch (UnsupportedLookAndFeelException failure) { throw new IllegalStateException(failure); }
            GtkDefaults.decorate(UIManager.getLookAndFeelDefaults(), colors::get);
            return true;
        });
    }

    /** A GTK request on a host where installing GTK fails. */
    public static ThemeController unavailable(Appearance saved) {
        return new ThemeController(ThemeStyle.GTK, saved, theme -> {
            if (theme == BuiltinTheme.GTK) throw new IllegalStateException("GTK is not available on this desktop");
            return ThemeController.install(theme);
        });
    }
}

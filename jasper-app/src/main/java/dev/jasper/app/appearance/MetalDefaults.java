package dev.jasper.app.appearance;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Set;
import javax.swing.plaf.FontUIResource;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

/** Stock Metal delegates with regular system typography and aliases for Jasper-owned paint. */
final class MetalDefaults {
    private MetalDefaults() {}
    static boolean install() {
        MetalLookAndFeel.setCurrentTheme(new OceanTheme());
        try { UIManager.setLookAndFeel(new MetalLookAndFeel()); }
        catch (UnsupportedLookAndFeelException failure) { throw new IllegalStateException(failure); }
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        installFonts(defaults);
        defaults.put("Jasper.retro", true);
        alias(defaults, "Panel.background", "Jasper.titleBackground", "Jasper.paletteBackground");
        alias(defaults, "Label.foreground", "Jasper.titleForeground", "Jasper.chromeForeground",
            "Jasper.tabSelectedForeground", "Jasper.paletteForeground");
        alias(defaults, "Label.disabledForeground", "Jasper.titleInactiveForeground",
            "Jasper.mutedForeground", "Jasper.paletteMutedForeground");
        alias(defaults, "controlShadow", "Jasper.titleSeparator", "Jasper.splitDivider",
            "Component.borderColor", "Jasper.paletteBorder");
        alias(defaults, "TabbedPane.selected", "Jasper.tabSelectedBackground");
        alias(defaults, "List.selectionForeground", "Jasper.paletteAccent");
        alias(defaults, "controlDkShadow", "Component.focusedBorderColor");
        alias(defaults, "List.selectionBackground", "Jasper.paletteSelectionBackground");
        alias(defaults, "List.selectionForeground", "Jasper.paletteSelectionForeground");
        defaults.put("Jasper.runningForeground", new Color(0x166534));
        defaults.put("Actions.Red", new Color(0xb91c1c));
        defaults.put("Actions.Yellow", new Color(0x854d0e));
        defaults.put("Actions.Green", new Color(0x166534));
        defaults.put("Jasper.configSuccessForeground", new Color(0x166534));
        defaults.put("Jasper.configWarningForeground", new Color(0x854d0e));
        defaults.put("Jasper.configErrorForeground", new Color(0xb91c1c));
        return true;
    }
    private static void installFonts(UIDefaults defaults) {
        Set<String> available = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        String family = List.of("Helvetica Neue", "Segoe UI", "Noto Sans", "DejaVu Sans").stream()
            .filter(available::contains).findFirst().orElse(Font.SANS_SERIF);
        // Keep Metal's sizing and delegates, but remove its default bold menu/label typography.
        // LAF-local defaults leave modern mode and the terminal's configured font independent.
        for (Object key : defaults.keySet().toArray()) {
            if (defaults.get(key) instanceof Font original) {
                defaults.put(key, new FontUIResource(new Font(family, Font.PLAIN, original.getSize())
                    .deriveFont(original.getSize2D())));
            }
        }
    }

    private static void alias(UIDefaults defaults, String source, String... targets) {
        Object value = defaults.get(source);
        if (value == null) throw new IllegalStateException("Missing Metal default: " + source);
        for (String target : targets) defaults.put(target, value);
    }
}

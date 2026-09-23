package dev.jasper.app.appearance;

import java.awt.Color;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

/** Stock Metal delegates with aliases for Jasper-owned paint, never branded component defaults. */
final class MetalDefaults {
    private MetalDefaults() {}
    static boolean install() {
        MetalLookAndFeel.setCurrentTheme(new OceanTheme());
        try { UIManager.setLookAndFeel(new MetalLookAndFeel()); }
        catch (UnsupportedLookAndFeelException failure) { throw new IllegalStateException(failure); }
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
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
    private static void alias(UIDefaults defaults, String source, String... targets) {
        Object value = defaults.get(source);
        if (value == null) throw new IllegalStateException("Missing Metal default: " + source);
        for (String target : targets) defaults.put(target, value);
    }
}

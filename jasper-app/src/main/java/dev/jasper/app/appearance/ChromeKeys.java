package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;

/**
 * Fills the {@code Jasper.*} chrome keys a theme leaves unset from its IntelliJ keys (the theme engine
 * specification, section 3). A key the theme sets is never replaced. Derived text and stroke colours are
 * moved toward the theme's foreground until they meet Jasper's contrast floor.
 */
final class ChromeKeys {
    /** Every key this class can fill. */
    static final List<String> KEYS = List.of("Jasper.titleBackground", "Jasper.titleForeground",
        "Jasper.titleInactiveForeground", "Jasper.titleSeparator", "Jasper.chromeForeground", "Jasper.mutedForeground",
        "Jasper.tabSelectedBackground", "Jasper.tabSelectedForeground", "Jasper.tabHoverBackground", "Jasper.tabUnderline",
        "Jasper.tabUnderlineInactive", "Jasper.splitDivider", "Jasper.findErrorBackground", "Jasper.runningForeground",
        "Jasper.configSuccessForeground", "Jasper.configWarningForeground", "Jasper.configErrorForeground");

    private ChromeKeys() {}

    /** Derives the missing keys into {@code defaults} and returns what it added. */
    static Map<String, Color> derive(UIDefaults defaults, Color terminalBackground) {
        var added = new LinkedHashMap<String, Color>();
        Color foreground = first(defaults, "Label.foreground");
        put(defaults, added, "Jasper.titleBackground", first(defaults, "MainToolbar.background", "TitlePane.background"));
        Color title = defaults.getColor("Jasper.titleBackground");
        put(defaults, added, "Jasper.titleForeground", first(defaults, "TitlePane.foreground"));
        put(defaults, added, "Jasper.titleInactiveForeground",
            readable(first(defaults, "TitlePane.inactiveForeground"), title, 3, foreground));
        put(defaults, added, "Jasper.titleSeparator", first(defaults, "MainToolbar.borderColor", "Borders.color", "Separator.foreground"));
        put(defaults, added, "Jasper.chromeForeground", first(defaults, "MainToolbar.foreground", "Label.foreground"));
        put(defaults, added, "Jasper.mutedForeground",
            readable(first(defaults, "Label.infoForeground", "Label.disabledForeground"), title, 3, foreground));
        put(defaults, added, "Jasper.tabSelectedBackground",
            first(defaults, "EditorTabs.underlinedTabBackground", "EditorTabs.selectedBackground", "Panel.background"));
        put(defaults, added, "Jasper.tabSelectedForeground", first(defaults, "EditorTabs.underlinedTabForeground", "Label.foreground"));
        put(defaults, added, "Jasper.tabHoverBackground", first(defaults, "EditorTabs.hoverBackground", "TabbedPane.hoverColor"));
        put(defaults, added, "Jasper.tabUnderline", first(defaults, "EditorTabs.underlineColor", "TabbedPane.underlineColor"));
        put(defaults, added, "Jasper.tabUnderlineInactive",
            first(defaults, "EditorTabs.inactiveUnderlineColor", "TabbedPane.inactiveUnderlineColor"));
        put(defaults, added, "Jasper.splitDivider",
            readable(first(defaults, "Borders.color", "Separator.foreground"), terminalBackground, 3, foreground));
        Color error = defaults.getColor("SearchField.errorBackground");
        put(defaults, added, "Jasper.findErrorBackground",
            error != null ? error : mix(first(defaults, "Actions.Red"), first(defaults, "TextField.background"), .12));
        Color status = first(defaults, "StatusBar.background", "Panel.background");
        put(defaults, added, "Jasper.runningForeground", readable(first(defaults, "Actions.Green"), status, 3, foreground));
        put(defaults, added, "Jasper.configSuccessForeground", readable(first(defaults, "Actions.Green"), status, 4.5, foreground));
        put(defaults, added, "Jasper.configWarningForeground", readable(first(defaults, "Actions.Yellow"), status, 4.5, foreground));
        put(defaults, added, "Jasper.configErrorForeground", readable(first(defaults, "Actions.Red"), status, 4.5, foreground));
        return added;
    }

    private static void put(UIDefaults defaults, Map<String, Color> added, String key, Color value) {
        if (defaults.getColor(key) != null) return;
        var color = new ColorUIResource(value);
        defaults.put(key, color);
        added.put(key, color);
    }

    /** The first key present; the last key in every call is one FlatLaf always defines. */
    private static Color first(UIDefaults defaults, String... keys) {
        for (String key : keys) {
            Color color = defaults.getColor(key);
            if (color != null) return color;
        }
        throw new IllegalStateException("None of " + List.of(keys) + " is defined by the theme");
    }

    /** {@code color}, moved toward {@code toward} in 5% steps until its contrast with {@code background} reaches {@code minimum}. */
    static Color readable(Color color, Color background, double minimum, Color toward) {
        for (int step = 0; step <= 20; step++) {
            Color candidate = mix(toward, color, step / 20.0);
            if (contrast(candidate, background) >= minimum) return candidate;
        }
        return toward;
    }

    /** {@code amount} of {@code top} over {@code bottom}, opaque. */
    static Color mix(Color top, Color bottom, double amount) {
        return new Color((int) Math.round(top.getRed() * amount + bottom.getRed() * (1 - amount)),
            (int) Math.round(top.getGreen() * amount + bottom.getGreen() * (1 - amount)),
            (int) Math.round(top.getBlue() * amount + bottom.getBlue() * (1 - amount)));
    }

    /** The WCAG contrast ratio of two opaque colours. */
    static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }

    private static double luminance(Color color) {
        double[] rgb = {color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0};
        for (int i = 0; i < rgb.length; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }
}

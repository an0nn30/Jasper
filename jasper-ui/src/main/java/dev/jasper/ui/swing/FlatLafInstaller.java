package dev.jasper.ui.swing;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.IntelliJTheme;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.metrics.DesignMetrics;
import java.awt.EventQueue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

/** Real FlatLaf installation. Authored colors are retained; geometry is owned by the kit. EDT only. */
public final class FlatLafInstaller {
    private FlatLafInstaller() {}
    /** Installed immutable snapshot; stored with the LAF so appearance rollback restores it atomically. */
    public static GeneratedTheme installedTheme() {
        Object value = UIManager.getLookAndFeelDefaults().get("Jasper.installedTheme");
        if (value instanceof GeneratedTheme theme) return theme;
        throw new IllegalStateException("No Jasper theme installed");
    }
    private static final class SnapshotLaf extends IntelliJTheme.ThemeLaf {
        private final GeneratedTheme snapshot;
        SnapshotLaf(IntelliJTheme source, GeneratedTheme snapshot) { super(source); this.snapshot = snapshot; }
        @Override public javax.swing.UIDefaults getDefaults() {
            var defaults = super.getDefaults();
            publish(defaults, snapshot);
            return defaults;
        }
    }
    public static void install(GeneratedTheme theme) {
        if (!EventQueue.isDispatchThread()) throw new IllegalStateException("Appearance requires EDT");
        String json = FlatLafThemeData.json(theme.name(), theme.dark(), theme.author(), theme.uiKeys());
        FlatLaf laf;
        try { laf = new SnapshotLaf(new IntelliJTheme(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))), theme); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        var fixed = new LinkedHashMap<String, String>();
        var metrics = DesignMetrics.DEFAULT;
        String control = Integer.toString(metrics.value(DesignMetrics.Role.CONTROL_RADIUS) * 2);
        fixed.put("Component.arc", control);
        fixed.put("TextComponent.arc", control);
        fixed.put("Button.arc", control);
        fixed.put("Component.focusWidth", Integer.toString(metrics.value(DesignMetrics.Role.FOCUS_RING)));
        fixed.put("Button.minimumHeight", Integer.toString(metrics.value(DesignMetrics.Role.BUTTON_HEIGHT)));
        fixed.put("Component.minimumHeight", Integer.toString(metrics.value(DesignMetrics.Role.FIELD_HEIGHT)));
        // Buttons as on main: a 14 px label margin and a 72 px minimum width (a window footer's action).
        int label = metrics.value(DesignMetrics.Role.BUTTON_LABEL_MARGIN), side = metrics.value(DesignMetrics.Role.BUTTON_HORIZONTAL_PADDING);
        fixed.put("Button.margin", insets(label, side, label, side));
        fixed.put("Button.minimumWidth", Integer.toString(metrics.value(DesignMetrics.Role.WINDOW_ACTION_MIN_WIDTH)));
        // Selection is a plain rectangle, edge to edge.
        for (var key : java.util.List.of("List", "Tree", "Table", "ComboBox", "MenuItem")) {
            fixed.put(key + ".selectionArc", "0");
            fixed.put(key + ".selectionInsets", "0,0,0,0");
        }
        // Native menu rows match the kit's: a 22 px row from a 16 px line, 8 px sides, 4 px popup padding, radius 6.
        int row = metrics.value(DesignMetrics.Role.MENU_ITEM_VERTICAL_PADDING), inset = metrics.value(DesignMetrics.Role.MENU_ITEM_PADDING);
        for (var key : java.util.List.of("MenuItem", "Menu", "CheckBoxMenuItem", "RadioButtonMenuItem")) fixed.put(key + ".margin", insets(row, inset, row, inset));
        int padding = metrics.value(DesignMetrics.Role.MENU_PADDING), line = metrics.value(DesignMetrics.Role.HAIRLINE);
        fixed.put("PopupMenu.borderInsets", insets(padding, line, padding, line));
        String radius = Integer.toString(metrics.value(DesignMetrics.Role.CARD_RADIUS));
        fixed.put("Popup.borderCornerRadius", radius);
        fixed.put("PopupMenu.borderCornerRadius", radius);
        laf.setExtraDefaults(fixed);
        try { UIManager.setLookAndFeel(laf); }
        catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new IllegalStateException("Cannot install generated appearance", failure); }
    }

    private static String insets(int top, int left, int bottom, int right) { return top + "," + left + "," + bottom + "," + right; }

    // A LAF reinstall (including rollback) obtains fresh defaults; publish the same immutable snapshot every time.
    private static void publish(javax.swing.UIDefaults defaults, GeneratedTheme theme) {
        defaults.put("Jasper.installedTheme", theme);
        FlatLafThemeData.explicitColors(theme.uiKeys()).forEach((key, color) -> defaults.put(key, new ColorUIResource(color)));
        for (var role : SemanticColor.values()) defaults.put("Jasper.semantic." + role.key(), new ColorUIResource(theme.semantic().color(role)));
        // Popovers and menus: FlatLaf's painted popup shadow (macOS keeps its native one), black at theme.md's strength.
        defaults.put("Popup.dropShadowColor", new ColorUIResource(java.awt.Color.BLACK));
        defaults.put("Popup.dropShadowOpacity", DesignMetrics.DEFAULT.value(theme.dark()
            ? DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK : DesignMetrics.Role.POPOVER_SHADOW_PERCENT_LIGHT) / 100f);
        var palette = theme.semantic();
        // FlatLaf primitives also inherit the canonical roles; kit paints use the palette directly.
        Map<String, SemanticColor> roles = Map.ofEntries(
            Map.entry("Panel.background", SemanticColor.SURFACE_PANEL), Map.entry("Label.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("TextField.background", SemanticColor.SURFACE_INPUT), Map.entry("TextField.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("PasswordField.background", SemanticColor.SURFACE_INPUT), Map.entry("PasswordField.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("Component.borderColor", SemanticColor.BORDER_INPUT), Map.entry("Component.focusColor", SemanticColor.BORDER_FOCUS),
            Map.entry("Button.background", SemanticColor.BUTTON_SECONDARY_BG), Map.entry("Button.foreground", SemanticColor.TEXT_STRONG_SECONDARY),
            Map.entry("List.selectionBackground", SemanticColor.SELECTION_BG), Map.entry("List.selectionForeground", SemanticColor.TEXT_ONACCENT));
        roles.forEach((key, role) -> defaults.put(key, new ColorUIResource(palette.color(role))));
    }
}

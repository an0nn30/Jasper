package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.*;

/**
 * Kit-owned styling for identity-sensitive native Swing adapters. Does not rebuild documents,
 * models, input maps or callbacks. App composition migrates these adapters to descriptor views separately.
 */
public final class NativeControlStyles {
    private NativeControlStyles() {}
    /** User typography with the display scale applied exactly once. */
    public static Font font(Typography.Role role) {
        Font[] override = {null, null};
        UIManager.getDefaults().forEach((key, value) -> {
            if ("defaultFont".equals(key) && value instanceof Font font) override[0] = font;
            if ("Label.font".equals(key) && value instanceof Font font) override[1] = font;
        });
        Font base = override[1] == null ? override[0] : override[1];
        var typography = base == null ? new Typography()
            : Boolean.FALSE.equals(UIManager.get("Jasper.uiFontFamilyOverride"))
                ? new Typography(base.getSize2D()) : new Typography(base.getFamily(), base.getSize2D());
        Font result = typography.font(role);
        return result.deriveFont(UIScale.scale(result.getSize2D()));
    }
    /** Scaled spacing from the fixed module catalog. */
    public static int metric(DesignMetrics.Role role) { return UIScale.scale(DesignMetrics.DEFAULT.value(role)); }
    /** Borderless native command; host retains focusability and Action ownership. */
    public static void unframed(AbstractButton button) {
        button.setBorder(BorderFactory.createEmptyBorder()); button.setContentAreaFilled(false); button.setOpaque(false);
    }
    /** Role padding for native adapters; callers supply semantic sides, not literal geometry. */
    public static javax.swing.border.Border padding(DesignMetrics.Role vertical, DesignMetrics.Role horizontal) {
        return BorderFactory.createEmptyBorder(metric(vertical), metric(horizontal), metric(vertical), metric(horizontal));
    }
    /**
     * Compact ghost toolbar action: module role colors only. The host's existing arc and toolbar margins are kept;
     * action/model and focus policy remain host-owned.
     */
    public static void button(AbstractButton button, Supplier<GeneratedTheme> theme) { ghost(button, theme, null); }
    /** Ghost toolbar action with a named, preserved native arc (for example the tool-window rail's 10). */
    public static void button(AbstractButton button, Supplier<GeneratedTheme> theme, DesignMetrics.Role arc) {
        ghost(button, theme, java.util.Objects.requireNonNull(arc));
    }
    private static void ghost(AbstractButton button, Supplier<GeneratedTheme> theme, DesignMetrics.Role arc) {
        bind(button, () -> {
            var palette = theme.get().semantic();
            button.setFont(font(Typography.Role.LABEL));
            button.setForeground(palette.color(SemanticColor.TEXT_SECONDARY));
            button.setOpaque(false);
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            var style = new java.util.LinkedHashMap<String, Object>();
            if (arc != null) style.put("arc", DesignMetrics.DEFAULT.value(arc));
            style.put("toolbar.hoverBackground", ColorMath.opacity(palette.color(SemanticColor.HOVER_OVERLAY), .08));
            style.put("toolbar.pressedBackground", palette.color(SemanticColor.SURFACE_CONTROL_HOVER));
            style.put("toolbar.selectedBackground", palette.color(SemanticColor.SEGMENT_SELECTED_BG));
            button.putClientProperty("FlatLaf.style", Map.copyOf(style));
        });
    }
    /**
     * Standard framed command (the kit's secondary kind): module role colors; the host keeps the look and feel's
     * button arc, margin, font and focus behavior. Native framed commands stay framed until Plan 3 Task 10.
     */
    public static void framedButton(AbstractButton button, Supplier<GeneratedTheme> theme) {
        bind(button, () -> {
            var palette = theme.get().semantic();
            button.putClientProperty("JButton.buttonType", null);
            button.setContentAreaFilled(true); button.setBorderPainted(true);
            button.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));
            button.putClientProperty("FlatLaf.style", Map.of(
                "background", palette.color(SemanticColor.BUTTON_SECONDARY_BG),
                "hoverBackground", palette.color(SemanticColor.BUTTON_SECONDARY_BG_HOVER),
                "borderColor", palette.color(SemanticColor.BORDER_CONTROL),
                "focusedBorderColor", palette.color(SemanticColor.BORDER_FOCUS)));
        });
    }
    /** Native informational label; text and accessibility remain host-owned. */
    public static void label(JLabel label, Typography.Role role, SemanticColor color, Supplier<GeneratedTheme> theme) {
        bind(label, () -> { label.setFont(font(role)); label.setForeground(theme.get().semantic().color(color)); });
    }
    /** Read-only native diagnostics text retaining its document and selection. */
    public static void textArea(JTextArea text, Supplier<GeneratedTheme> theme) {
        bind(text, () -> {
            var palette = theme.get().semantic();
            text.setFont(font(Typography.Role.BODY));
            text.setBackground(palette.color(SemanticColor.SURFACE_PANEL));
            text.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));
            text.setCaretColor(palette.color(SemanticColor.TEXT_PRIMARY));
            text.setSelectionColor(palette.color(SemanticColor.SELECTION_BG));
            text.setSelectedTextColor(palette.color(SemanticColor.TEXT_ONACCENT));
        });
    }
    /** Native menu presentation (colors and typography); keyboard routing, actions and look-and-feel margins stay with the host. */
    public static void menu(JMenuItem item, Supplier<GeneratedTheme> theme) {
        bind(item, () -> {
            var palette = theme.get().semantic();
            item.setFont(font(Typography.Role.LABEL));
            item.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));
            item.setBackground(palette.color(SemanticColor.SURFACE_MENU));
            item.putClientProperty("FlatLaf.style", Map.of("selectionBackground", palette.color(SemanticColor.SELECTION_BG),
                "selectionForeground", palette.color(SemanticColor.TEXT_ONACCENT)));
        });
    }
    /** Styles an existing popup hierarchy without replacing items, models or listeners. */
    public static void menuTree(JPopupMenu popup, Supplier<GeneratedTheme> theme) {
        bind(popup, () -> popup.setBackground(theme.get().semantic().color(SemanticColor.SURFACE_MENU)));
        for (Component child : popup.getComponents()) {
            if (child instanceof JMenuItem item) menu(item, theme);
            if (child instanceof JMenu menu) menuTree(menu.getPopupMenu(), theme);
        }
    }
    /**
     * Borderless in-row field (the find bar's query): module colors and typography with the existing zero border
     * and focus widths, so the row keeps its native height and has no focus ring until Plan 3 Task 10.
     */
    public static void borderlessField(JTextField field, Supplier<GeneratedTheme> theme) {
        bind(field, () -> {
            fieldColors(field, theme.get());
            field.putClientProperty("FlatLaf.style", "borderWidth: 0; focusWidth: 0; innerFocusWidth: 0");
        });
    }
    private static void fieldColors(JTextField field, GeneratedTheme theme) {
        var palette = theme.semantic();
        field.setFont(font(Typography.Role.LABEL));
        field.setBackground("error".equals(field.getClientProperty("JComponent.outline"))
            ? theme.tokens().get(Token.ERROR_BACKGROUND) : palette.color(SemanticColor.SURFACE_INPUT));
        field.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));
        field.setCaretColor(palette.color(SemanticColor.TEXT_PRIMARY));
        field.setSelectionColor(palette.color(SemanticColor.SELECTION_BG));
        field.setSelectedTextColor(palette.color(SemanticColor.TEXT_ONACCENT));
    }
    /** Reapply native adapter roles after either a LAF refresh or a workspace-only appearance update. */
    public static void refresh(JComponent component) {
        if (component.getClientProperty("Jasper.kit.nativeStyle") instanceof Runnable refresh) refresh.run();
    }
    private static void bind(JComponent component, Runnable refresh) {
        if (component.getClientProperty("Jasper.kit.nativeStyle") == null)
            component.addPropertyChangeListener("UI", event -> refresh(component));
        component.putClientProperty("Jasper.kit.nativeStyle", refresh);
        refresh.run();
    }
}

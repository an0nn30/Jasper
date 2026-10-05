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
        var typography = base == null ? new Typography() : new Typography(base.getFamily(), base.getSize2D());
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
    /** Compact ghost action; existing action/model and focus policy remain host-owned. */
    public static void button(AbstractButton button, Supplier<GeneratedTheme> theme) {
        bind(button, () -> {
            var palette = theme.get().semantic();
            button.setFont(font(Typography.Role.LABEL));
            button.setForeground(palette.color(SemanticColor.TEXT_SECONDARY));
            button.setOpaque(false);
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            button.putClientProperty("FlatLaf.style", Map.of(
                "arc", DesignMetrics.DEFAULT.value(DesignMetrics.Role.CONTROL_RADIUS) * 2,
                "toolbar.hoverBackground", ColorMath.opacity(palette.color(SemanticColor.HOVER_OVERLAY), .08),
                "toolbar.pressedBackground", palette.color(SemanticColor.SURFACE_CONTROL_HOVER),
                "toolbar.selectedBackground", palette.color(SemanticColor.SEGMENT_SELECTED_BG)));
            button.setMargin(new Insets(0, 0, 0, 0));
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
    /** Native menu presentation; keyboard routing and actions remain owned by the host. */
    public static void menu(JMenuItem item, Supplier<GeneratedTheme> theme) {
        bind(item, () -> {
            var palette = theme.get().semantic();
            item.setFont(font(Typography.Role.LABEL));
            item.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));
            item.setBackground(palette.color(SemanticColor.SURFACE_MENU));
            item.setMargin(new Insets(metric(DesignMetrics.Role.MENU_PADDING), metric(DesignMetrics.Role.MENU_ITEM_PADDING),
                metric(DesignMetrics.Role.MENU_PADDING), metric(DesignMetrics.Role.MENU_ITEM_PADDING)));
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
    /** Standard text field, retaining its document/caret/input behavior. */
    public static void field(JTextField field, Supplier<GeneratedTheme> theme) {
        bind(field, () -> {
            var palette = theme.get().semantic();
            field.setFont(font(Typography.Role.LABEL));
            field.setBackground("error".equals(field.getClientProperty("JComponent.outline"))
                ? theme.get().tokens().get(Token.ERROR_BACKGROUND) : palette.color(SemanticColor.SURFACE_INPUT));
            field.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));
            field.setCaretColor(palette.color(SemanticColor.TEXT_PRIMARY));
            field.setSelectionColor(palette.color(SemanticColor.SELECTION_BG));
            field.setSelectedTextColor(palette.color(SemanticColor.TEXT_ONACCENT));
            field.putClientProperty("FlatLaf.style", Map.of("arc", DesignMetrics.DEFAULT.value(DesignMetrics.Role.CONTROL_RADIUS) * 2,
                "borderColor", palette.color(SemanticColor.BORDER_INPUT), "focusedBorderColor", palette.color(SemanticColor.BORDER_FOCUS)));
        });
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

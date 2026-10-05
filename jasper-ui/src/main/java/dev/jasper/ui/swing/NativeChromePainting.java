package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import javax.swing.*;
import com.formdev.flatlaf.util.UIScale;

/** Kit-owned painters for temporary native chrome control adapters; native input/layout remain host-owned. */
public final class NativeChromePainting {
    private NativeChromePainting() {}
    public static void action(AbstractButton button, Graphics graphics, GeneratedTheme theme, boolean labels, Icon arrow) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            var model = button.getModel();
            var palette = theme.semantic();
            if (button.isEnabled() && (model.isRollover() || model.isPressed() || model.isSelected())) {
                g.setColor(model.isPressed() || model.isSelected() ? palette.color(SemanticColor.SEGMENT_SELECTED_BG)
                    : ColorMath.opacity(palette.color(SemanticColor.HOVER_OVERLAY), .08));
                int arc = NativeControlStyles.metric(DesignMetrics.Role.NATIVE_TOOLBAR_HOVER_ARC);
                g.fillRoundRect(0, 0, button.getWidth(), button.getHeight(), arc, arc);
            }
            if (!button.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(
                DesignMetrics.DEFAULT.value(DesignMetrics.Role.NATIVE_TOOLBAR_DISABLED_PERCENT) / 100f));
            Icon icon = button.getIcon();
            int pad = NativeControlStyles.metric(DesignMetrics.Role.NATIVE_ACTION_PADDING);
            int arrowSpace = NativeControlStyles.metric(DesignMetrics.Role.NATIVE_MENU_SPACE);
            int x = labels ? pad : arrow != null ? Math.max(pad, (button.getWidth() - arrowSpace - icon.getIconWidth()) / 2)
                : (button.getWidth() - icon.getIconWidth()) / 2;
            icon.paintIcon(button, g, x, (button.getHeight() - icon.getIconHeight()) / 2);
            x += icon.getIconWidth();
            if (labels) {
                x += pad; g.setFont(NativeControlStyles.font(Typography.Role.LABEL)); g.setColor(palette.color(SemanticColor.TEXT_PRIMARY));
                var metrics = g.getFontMetrics();
                g.drawString(button.getText(), x, (button.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
                x += metrics.stringWidth(button.getText());
            }
            if (arrow != null) { x += NativeControlStyles.metric(DesignMetrics.Role.NATIVE_MENU_ARROW_GAP); arrow.paintIcon(button, g, x, (button.getHeight() - arrow.getIconHeight()) / 2); }
        } finally { g.dispose(); }
    }

    public static void search(JComponent component, Graphics graphics, GeneratedTheme theme, boolean hover, boolean collapsed,
                              String context, String hint, Icon search, Icon contextIcon) {
        var g = (Graphics2D) graphics.create();
        try {
            var palette = theme.semantic();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int arc = NativeControlStyles.metric(DesignMetrics.Role.TITLE_SEARCH_ARC), width = component.getWidth(), height = component.getHeight();
            g.setColor(palette.color(hover ? SemanticColor.SURFACE_CONTROL_HOVER : SemanticColor.SURFACE_INPUT));
            g.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);
            g.setColor(palette.color(SemanticColor.BORDER_INPUT));
            g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            if (collapsed) { search.paintIcon(component, g, (width - search.getIconWidth()) / 2, (height - search.getIconHeight()) / 2); return; }
            int pad = NativeControlStyles.metric(DesignMetrics.Role.TITLE_SEARCH_PADDING), gap = NativeControlStyles.metric(DesignMetrics.Role.TITLE_SEARCH_GAP), x = pad;
            search.paintIcon(component, g, x, (height - search.getIconHeight()) / 2);
            x += search.getIconWidth() + gap;
            g.setFont(NativeControlStyles.font(Typography.Role.LABEL));
            var metrics = g.getFontMetrics();
            int hintWidth = hint.isEmpty() ? 0 : metrics.stringWidth(hint);
            int end = width - pad - (hintWidth == 0 ? 0 : hintWidth + gap);
            if (contextIcon != null && x + contextIcon.getIconWidth() <= end) {
                contextIcon.paintIcon(component, g, x, (height - contextIcon.getIconHeight()) / 2);
                x += contextIcon.getIconWidth() + gap;
            }
            int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            g.setColor(palette.color(SemanticColor.TEXT_PRIMARY)); g.drawString(clip(context, metrics, end - x), x, baseline);
            if (hintWidth > 0 && width - pad - hintWidth > x) {
                g.setColor(palette.color(SemanticColor.TEXT_PLACEHOLDER)); g.drawString(hint, width - pad - hintWidth, baseline);
            }
        } finally { g.dispose(); }
    }
    public static String clip(String text, FontMetrics metrics, int width) {
        if (width <= 0) return "";
        if (metrics.stringWidth(text) <= width) return text;
        for (int end = text.offsetByCodePoints(text.length(), -1); end > 0; end = text.offsetByCodePoints(end, -1)) {
            String candidate = text.substring(0, end) + "…";
            if (metrics.stringWidth(candidate) <= width) return candidate;
        }
        return "";
    }
}

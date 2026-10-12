package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import javax.swing.*;
import com.formdev.flatlaf.util.UIScale;

/** Kit-owned painters for temporary native chrome control adapters; native input/layout remain host-owned. */
public final class NativeChromePainting {
    private NativeChromePainting() {}
    private static final java.awt.font.FontRenderContext TEXT_CONTEXT = new java.awt.font.FontRenderContext(null,true,true);
    /** Applies the engine's text coverage to a caller-owned graphics clone. */
    public static void prepareText(Graphics2D graphics) { ThemePainting.antialias(graphics); }
    /** Literal advance measured with the same fractional context used by engine labels and native text. */
    public static int measureText(Font font, String text) { return (int)Math.ceil(font.getStringBounds(text,TEXT_CONTEXT).getWidth()); }

    public static void action(AbstractButton button, Graphics graphics, GeneratedTheme theme, boolean labels, Icon arrow) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            var model = button.getModel();
            var palette = theme.semantic();
            if (button.isEnabled() && (model.isRollover() || model.isPressed() || model.isSelected())) {
                g.setColor(model.isPressed() || model.isSelected() ? palette.color(SemanticColor.SEGMENT_SELECTED_BG)
                    : hoverOverlay(theme));
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
                x += pad; g.setFont(NativeControlStyles.font(Typography.Role.TOOLBAR_LABEL)); g.setColor(palette.color(SemanticColor.TEXT_PRIMARY));
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
            g.setFont(NativeControlStyles.font(Typography.Role.TITLE_CONTEXT));
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
                g.setColor(SurfaceContrast.on(palette, SemanticColor.TEXT_PLACEHOLDER,
                    hover ? SemanticColor.SURFACE_CONTROL_HOVER : SemanticColor.SURFACE_INPUT, SurfaceContrast.INDICATOR)); g.drawString(hint, width - pad - hintWidth, baseline);
            }
        } finally { g.dispose(); }
    }
    /** The native chrome's resting hover tint: the hover overlay at its named share. */
    public static Color hoverOverlay(GeneratedTheme theme) {
        return ColorMath.opacity(theme.semantic().color(SemanticColor.HOVER_OVERLAY),
            DesignMetrics.DEFAULT.value(DesignMetrics.Role.NATIVE_HOVER_OVERLAY_PERCENT) / 100.0);
    }

    /**
     * A toolbar action's preferred width: square at {@code side} for an icon-only action; otherwise padding, icon,
     * padding, the label in the toolbar role and trailing padding; a menu action adds the arrow's space.
     */
    public static int actionWidth(AbstractButton button, int side, boolean labels, boolean menu) {
        int pad = DesignMetrics.DEFAULT.value(DesignMetrics.Role.NATIVE_ACTION_PADDING);
        // The leading padding, the toolbar icon and the gap scale as one span, as the label's trailing padding does.
        int width = labels ? UIScale.scale(2 * pad + DesignMetrics.DEFAULT.value(DesignMetrics.Role.TOOLBAR_ACTION_ICON)) + UIScale.scale(pad)
            + button.getFontMetrics(NativeControlStyles.font(Typography.Role.TOOLBAR_LABEL)).stringWidth(button.getText()) : side;
        return menu ? width + NativeControlStyles.metric(DesignMetrics.Role.NATIVE_MENU_SPACE) : width;
    }

    /** A toolbar's vertical rule between action groups: its whole bounds in the subtle border role. */
    public static void rule(JComponent component, Graphics graphics, GeneratedTheme theme) {
        graphics.setColor(theme.semantic().color(SemanticColor.BORDER_SUBTLE));
        graphics.fillRect(0, 0, component.getWidth(), component.getHeight());
    }

    /** The hairline closing a native row (the find bar) along its lower edge. */
    public static void bottomHairline(JComponent component, Graphics graphics, GeneratedTheme theme) {
        int line = NativeControlStyles.metric(DesignMetrics.Role.HAIRLINE);
        graphics.setColor(theme.semantic().color(SemanticColor.BORDER_SUBTLE));
        graphics.fillRect(0, component.getHeight() - line, component.getWidth(), line);
    }

    /**
     * The native title row's edges: a header row (the workspace title bar) gets the hover overlay's highlight along its
     * top; a window's title row gets a subtle hairline along its bottom; a dialog's title band, which continues into
     * the dialog surface, gets neither.
     */
    public static void titleBarEdges(JComponent bar, Graphics graphics, GeneratedTheme theme, boolean header, boolean rule) {
        int line = NativeControlStyles.metric(DesignMetrics.Role.HAIRLINE);
        if (header) { graphics.setColor(hoverOverlay(theme)); graphics.fillRect(0, 0, bar.getWidth(), line); }
        if (!header && rule) {
            graphics.setColor(theme.semantic().color(SemanticColor.BORDER_SUBTLE));
            graphics.fillRect(0, bar.getHeight() - line, bar.getWidth(), line);
        }
    }

    /**
     * An equal-width workspace tab's surface, derived from the workspace surface it sits on: the selected tab lifts
     * toward white and the others sink toward black by named per-mille shares for dark and light workspaces.
     */
    public static Color tabSurface(Color base, boolean dark, boolean selected) {
        var role = selected ? dark ? DesignMetrics.Role.TAB_SELECTED_LIFT_DARK : DesignMetrics.Role.TAB_SELECTED_LIFT_LIGHT
            : dark ? DesignMetrics.Role.TAB_IDLE_SHADE_DARK : DesignMetrics.Role.TAB_IDLE_SHADE_LIGHT;
        double blend = DesignMetrics.DEFAULT.value(role) / 1000.0;
        int target = selected ? 255 : 0;
        return new Color((int) Math.round(base.getRed() * (1 - blend) + target * blend),
            (int) Math.round(base.getGreen() * (1 - blend) + target * blend),
            (int) Math.round(base.getBlue() * (1 - blend) + target * blend), base.getAlpha());
    }

    /**
     * The equal-width tab strip: the lifted surface behind everything, each slot's surface (only {@code selected}
     * lifted), the window-border dividers between slots and the rule along the bottom. Slot bounds are the host's
     * retained motion geometry; {@code selected} is -1 when no slot is selected.
     */
    public static void tabStrip(JComponent strip, Graphics graphics, GeneratedTheme workspace, Color base,
                                java.util.List<Rectangle> slots, int selected) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setColor(tabSurface(base, workspace.dark(), true)); g.fillRect(0, 0, strip.getWidth(), strip.getHeight());
            for (int i = 0; i < slots.size(); i++) { g.setColor(tabSurface(base, workspace.dark(), i == selected)); g.fill(slots.get(i)); }
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(workspace.semantic().color(SemanticColor.BORDER_WINDOW));
            int line = NativeControlStyles.metric(DesignMetrics.Role.TAB_DIVIDER);
            for (int i = 0; i < slots.size() - 1; i++) {
                Rectangle bounds = slots.get(i);
                if (bounds.width > 0) g.fillRect(bounds.x + bounds.width - line, 0, line, strip.getHeight());
            }
            g.fillRect(0, strip.getHeight() - line, strip.getWidth(), line);
        } finally { g.dispose(); }
    }

    /** The status bar's running dot at its leading gap: success while a shell runs, secondary text otherwise. */
    public static void statusDot(JComponent bar, Graphics graphics, GeneratedTheme theme, boolean running) {
        var g = (Graphics2D) graphics.create();
        try {
            var palette = theme.semantic();
            g.setColor(running ? SurfaceContrast.on(palette, SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_STATUS, SurfaceContrast.TEXT)
                : palette.color(SemanticColor.TEXT_SECONDARY));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = NativeControlStyles.metric(DesignMetrics.Role.STATUS_RUNNING_DOT);
            g.fillOval(NativeControlStyles.metric(DesignMetrics.Role.STATUS_GAP), (bar.getHeight() - size) / 2, size, size);
        } finally { g.dispose(); }
    }

    /**
     * An unread badge at an icon's top-right corner on the rail: a rail-coloured ring, then the accent (or danger) dot
     * held to the indicator floor on the rail surface.
     */
    public static void badge(Graphics graphics, int x, int y, int iconWidth, GeneratedTheme theme, boolean error) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int dot = NativeControlStyles.metric(DesignMetrics.Role.RAIL_BADGE_DOT), ring = NativeControlStyles.metric(DesignMetrics.Role.HAIRLINE);
            int left = x + iconWidth - dot;
            var palette = theme.semantic();
            Color rail = palette.color(SemanticColor.SURFACE_APP);
            if (rail != null) { g.setColor(rail); g.fillOval(left - ring, y - ring, dot + 2 * ring, dot + 2 * ring); }
            g.setColor(SurfaceContrast.on(palette, error ? SemanticColor.STATUS_DANGER : SemanticColor.ACCENT, SemanticColor.SURFACE_APP,
                SurfaceContrast.INDICATOR));
            g.fillOval(left, y, dot, dot);
        } finally { g.dispose(); }
    }

    /** A tool window header's connected dot: the success status held to the indicator floor on the sidebar surface. */
    public static void connectedDot(Graphics graphics, int x, int y, int size, GeneratedTheme theme) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SurfaceContrast.on(theme.semantic(), SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_SIDEBAR, SurfaceContrast.INDICATOR));
            g.fillOval(x, y, size, size);
        } finally { g.dispose(); }
    }

    /** The thin rule centred in a native split divider's wider drag area, in the divider token. */
    public static void splitDivider(Graphics graphics, int x, int y, int width, int height, boolean vertical, GeneratedTheme theme) {
        int thickness = NativeControlStyles.metric(DesignMetrics.Role.SPLIT_STROKE);
        Graphics g = graphics.create();
        try {
            g.setColor(theme.tokens().get(Token.DIVIDER));
            if (vertical) g.fillRect(x + (width - thickness) / 2, y, thickness, height);
            else g.fillRect(x, y + (height - thickness) / 2, width, thickness);
        } finally { g.dispose(); }
    }

    /** A dock drop guide colour: the accent at the named fill or border alpha. */
    public static Color dropGuide(GeneratedTheme theme, boolean border) {
        Color accent = theme.semantic().color(SemanticColor.ACCENT);
        return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
            DesignMetrics.DEFAULT.value(border ? DesignMetrics.Role.DOCK_DROP_BORDER_ALPHA : DesignMetrics.Role.DOCK_DROP_FILL_ALPHA));
    }

    /**
     * Dock drop guides: a square highlight over a whole zone and a rounded placeholder where the window lands. Each fill
     * sits inside its border so the two translucent colours never stack; the placeholder border is stroked half a pixel
     * in, so its inner edge and the fill share one corner diameter.
     */
    public static void dropGuides(Graphics graphics, Rectangle highlight, Rectangle placeholder, Color fill, Color border) {
        var g = (Graphics2D) graphics.create();
        try {
            if (highlight != null) {
                g.setColor(fill);
                g.fillRect(highlight.x + 1, highlight.y + 1, highlight.width - 2, highlight.height - 2);
                g.setColor(border);
                g.drawRect(highlight.x, highlight.y, highlight.width - 1, highlight.height - 1);
            }
            if (placeholder != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double arc = NativeControlStyles.metric(DesignMetrics.Role.DOCK_DROP_ARC);
                double inner = Math.max(0, arc - 1);
                g.setColor(fill);
                g.fill(new java.awt.geom.RoundRectangle2D.Double(placeholder.x + 1, placeholder.y + 1, placeholder.width - 2, placeholder.height - 2, inner, inner));
                g.setColor(border);
                g.draw(new java.awt.geom.RoundRectangle2D.Double(placeholder.x + 0.5, placeholder.y + 0.5, placeholder.width - 1, placeholder.height - 1, arc, arc));
            }
        } finally { g.dispose(); }
    }

    /**
     * The command palette's soft shadow around its card: stacked rings of the scrim colour at a named alpha (lighter in
     * light chrome, as {@code dark} says), out to the named spread.
     */
    public static void popupShadow(Graphics graphics, Rectangle card, GeneratedTheme theme, boolean dark) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color shadow = theme.semantic().color(SemanticColor.SURFACE_SCRIM);
            g.setColor(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), DesignMetrics.DEFAULT.value(
                dark ? DesignMetrics.Role.PALETTE_SHADOW_ALPHA_DARK : DesignMetrics.Role.PALETTE_SHADOW_ALPHA_LIGHT)));
            for (int i = DesignMetrics.DEFAULT.value(DesignMetrics.Role.PALETTE_SHADOW_SPREAD); i >= 1; i--) {
                int expansion = UIScale.scale(i);
                g.fillRect(card.x - expansion, card.y - expansion, card.width + expansion * 2, card.height + expansion * 2);
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

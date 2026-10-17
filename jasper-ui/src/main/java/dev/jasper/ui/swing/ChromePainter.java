package dev.jasper.ui.swing;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.Objects;
import javax.swing.*;
import com.formdev.flatlaf.util.UIScale;

/**
 * Kit-owned painters for temporary native chrome control adapters, over one kit's metrics and type; native input and
 * layout remain host-owned. Application chrome paints with {@link #installed()}; a test builds one for the kit it checks.
 */
public final class ChromePainter {
    /** The painter of the installed kit last asked for; replaced when the installed kit changes identity. */
    private static volatile ChromePainter current;
    private final Kit kit;
    private final NativeStyles styles;

    /** A painter over {@code kit}'s metrics and type. */
    public ChromePainter(Kit kit) {
        this.kit = Objects.requireNonNull(kit, "kit");
        this.styles = new NativeStyles(kit);
    }

    /** The painter of {@link FlatLafInstaller#installedKit()}: one instance until the installed kit changes. */
    public static ChromePainter installed() {
        Kit kit = FlatLafInstaller.installedKit();
        ChromePainter painter = current;
        if (painter == null || painter.kit != kit) current = painter = new ChromePainter(kit);
        return painter;
    }

    /** The kit whose metrics and type this painter reads. */
    public Kit kit() { return kit; }

    // metric() is scaled for the display; value() is the logical role (an alpha, a percentage or a span the caller scales).
    private int metric(DesignMetrics.Role role) { return styles.metric(role); }
    private int value(DesignMetrics.Role role) { return kit.metrics().value(role); }
    private Font font(Typography.Role role) { return styles.font(role); }

    /** Applies the engine's text coverage to a caller-owned graphics clone. */
    public void prepareText(Graphics2D graphics) { ThemePainting.antialias(graphics); }
    /** Literal advance measured with the same fractional context used by engine labels and native text. */
    public int measureText(Font font, String text) { return ThemePainting.measureText(font, text); }

    public void action(AbstractButton button, Graphics graphics, GeneratedTheme theme, boolean labels, Icon arrow) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            var model = button.getModel();
            var palette = theme.semantic();
            if (button.isEnabled() && (model.isRollover() || model.isPressed() || model.isSelected())) {
                g.setColor(model.isPressed() || model.isSelected() ? palette.color(SemanticColor.SURFACE_CONTROL_SELECTED)
                    : hoverOverlay(theme));
                int arc = metric(DesignMetrics.Role.NATIVE_TOOLBAR_HOVER_ARC);
                g.fillRoundRect(0, 0, button.getWidth(), button.getHeight(), arc, arc);
            }
            if (!button.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(
                value(DesignMetrics.Role.NATIVE_TOOLBAR_DISABLED_PERCENT) / 100f));
            Icon icon = button.getIcon();
            int pad = metric(DesignMetrics.Role.NATIVE_ACTION_PADDING);
            int arrowSpace = metric(DesignMetrics.Role.NATIVE_MENU_SPACE);
            int x = labels ? pad : arrow != null ? Math.max(pad, (button.getWidth() - arrowSpace - icon.getIconWidth()) / 2)
                : (button.getWidth() - icon.getIconWidth()) / 2;
            icon.paintIcon(button, g, x, (button.getHeight() - icon.getIconHeight()) / 2);
            x += icon.getIconWidth();
            if (labels) {
                x += pad; g.setFont(font(Typography.Role.TOOLBAR_LABEL)); g.setColor(palette.color(SemanticColor.TEXT_PRIMARY));
                var metrics = g.getFontMetrics();
                g.drawString(button.getText(), x, (button.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
                x += metrics.stringWidth(button.getText());
            }
            if (arrow != null) { x += metric(DesignMetrics.Role.NATIVE_MENU_ARROW_GAP); arrow.paintIcon(button, g, x, (button.getHeight() - arrow.getIconHeight()) / 2); }
        } finally { g.dispose(); }
    }

    public void search(JComponent component, Graphics graphics, GeneratedTheme theme, boolean hover, boolean collapsed,
                       String context, String hint, Icon search, Icon contextIcon) {
        var g = (Graphics2D) graphics.create();
        try {
            var palette = theme.semantic();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int arc = metric(DesignMetrics.Role.TITLE_SEARCH_ARC), width = component.getWidth(), height = component.getHeight();
            g.setColor(palette.color(SemanticColor.SURFACE_FIELD_RAISED));
            g.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);
            g.setColor(palette.color(hover ? SemanticColor.BORDER_FIELD_RAISED_HOVER : SemanticColor.BORDER_FIELD_RAISED));
            g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            if (collapsed) { search.paintIcon(component, g, (width - search.getIconWidth()) / 2, (height - search.getIconHeight()) / 2); return; }
            int pad = metric(DesignMetrics.Role.TITLE_SEARCH_PADDING), gap = metric(DesignMetrics.Role.TITLE_SEARCH_GAP), x = pad;
            search.paintIcon(component, g, x, (height - search.getIconHeight()) / 2);
            x += search.getIconWidth() + gap;
            g.setFont(font(Typography.Role.TITLE_CONTEXT));
            var metrics = g.getFontMetrics();
            int hintWidth = hint.isEmpty() ? 0 : metrics.stringWidth(hint);
            int end = width - pad - (hintWidth == 0 ? 0 : hintWidth + gap);
            if (contextIcon != null && x + contextIcon.getIconWidth() <= end) {
                contextIcon.paintIcon(component, g, x, (height - contextIcon.getIconHeight()) / 2);
                x += contextIcon.getIconWidth() + gap;
            }
            int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            g.setColor(palette.color(SemanticColor.TEXT_FIELD_RAISED)); g.drawString(clip(context, metrics, end - x), x, baseline);
            if (hintWidth > 0 && width - pad - hintWidth > x) {
                g.setColor(SurfaceContrast.on(palette, SemanticColor.TEXT_PLACEHOLDER,
                    SemanticColor.SURFACE_FIELD_RAISED, SurfaceContrast.INDICATOR)); g.drawString(hint, width - pad - hintWidth, baseline);
            }
        } finally { g.dispose(); }
    }
    /** The native chrome's resting hover tint: the hover overlay at its named share. */
    public Color hoverOverlay(GeneratedTheme theme) {
        return ColorMath.opacity(theme.semantic().color(SemanticColor.HOVER_OVERLAY),
            value(DesignMetrics.Role.NATIVE_HOVER_OVERLAY_PERCENT) / 100.0);
    }

    /**
     * A toolbar action's preferred width: square at {@code side} for an icon-only action; otherwise padding, icon,
     * padding, the label in the toolbar role and trailing padding; a menu action adds the arrow's space.
     */
    public int actionWidth(AbstractButton button, int side, boolean labels, boolean menu) {
        int pad = value(DesignMetrics.Role.NATIVE_ACTION_PADDING);
        // The leading padding, the toolbar icon and the gap scale as one span, as the label's trailing padding does.
        int width = labels ? UIScale.scale(2 * pad + value(DesignMetrics.Role.TOOLBAR_ACTION_ICON)) + UIScale.scale(pad)
            + button.getFontMetrics(font(Typography.Role.TOOLBAR_LABEL)).stringWidth(button.getText()) : side;
        return menu ? width + metric(DesignMetrics.Role.NATIVE_MENU_SPACE) : width;
    }

    /** A toolbar's vertical rule between action groups: its whole bounds in the subtle border role. */
    public void rule(JComponent component, Graphics graphics, GeneratedTheme theme) {
        graphics.setColor(theme.semantic().color(SemanticColor.BORDER_SUBTLE));
        graphics.fillRect(0, 0, component.getWidth(), component.getHeight());
    }

    /** The hairline closing a native row (the find bar) along its lower edge. */
    public void bottomHairline(JComponent component, Graphics graphics, GeneratedTheme theme) {
        int line = metric(DesignMetrics.Role.HAIRLINE);
        graphics.setColor(theme.semantic().color(SemanticColor.BORDER_SUBTLE));
        graphics.fillRect(0, component.getHeight() - line, component.getWidth(), line);
    }

    /**
     * The native title row's edges: a header row (the workspace title bar) gets the hover overlay's highlight along its
     * top; a window's title row gets a subtle hairline along its bottom; a dialog's title band, which continues into
     * the dialog surface, gets neither.
     */
    public void titleBarEdges(JComponent bar, Graphics graphics, GeneratedTheme theme, boolean header, boolean rule) {
        int line = metric(DesignMetrics.Role.HAIRLINE);
        if (header) { graphics.setColor(hoverOverlay(theme)); graphics.fillRect(0, 0, bar.getWidth(), line); }
        if (!header && rule) {
            graphics.setColor(theme.semantic().color(SemanticColor.BORDER_SUBTLE));
            graphics.fillRect(0, bar.getHeight() - line, bar.getWidth(), line);
        }
    }

    /**
     * An equal-width workspace tab's surface: the selected tab is the terminal's surface and the others the tab strip's,
     * kept clearly apart from it ({@link SurfaceContrast#TAB_LIGHTNESS}) so the selected tab shows whatever the theme gives
     * the two, both at {@code base}'s alpha (the window's opacity while it is translucent).
     */
    public Color tabSurface(GeneratedTheme workspace, Color base, boolean selected) {
        var palette = workspace.semantic();
        Color terminal = palette.color(SemanticColor.SURFACE_TERMINAL);
        Color role = selected ? terminal : SurfaceContrast.apart(palette.color(SemanticColor.SURFACE_TABSTRIP), terminal, SurfaceContrast.TAB_LIGHTNESS);
        return new Color(role.getRed(), role.getGreen(), role.getBlue(), base.getAlpha());
    }

    /**
     * The equal-width tab strip: the strip's surface behind everything, each slot's surface (only {@code selected} the
     * terminal's), the hover overlay over a hovered inactive slot, the selected slot's indicator along its top
     * ({@code tab.indicator}, or {@code tab.indicator.inactive} while the window is not {@code active}), the
     * window-border dividers between slots and the rule along the bottom. Slot bounds are the host's retained motion
     * geometry; {@code selected} and {@code hovered} are -1 when no slot is.
     */
    public void tabStrip(JComponent strip, Graphics graphics, GeneratedTheme workspace, Color base,
                         java.util.List<Rectangle> slots, int selected, int hovered, boolean active) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setColor(tabSurface(workspace, base, false)); g.fillRect(0, 0, strip.getWidth(), strip.getHeight());
            for (int i = 0; i < slots.size(); i++) { g.setColor(tabSurface(workspace, base, i == selected)); g.fill(slots.get(i)); }
            g.setComposite(AlphaComposite.SrcOver);
            if (hovered >= 0 && hovered < slots.size() && hovered != selected) { g.setColor(hoverOverlay(workspace)); g.fill(slots.get(hovered)); }
            if (selected >= 0 && selected < slots.size()) {
                Rectangle tab = slots.get(selected);
                g.setColor(workspace.semantic().color(active ? SemanticColor.TAB_INDICATOR : SemanticColor.TAB_INDICATOR_INACTIVE));
                g.fillRect(tab.x, tab.y, tab.width, metric(DesignMetrics.Role.TAB_INDICATOR));
            }
            g.setColor(workspace.semantic().color(SemanticColor.BORDER_WINDOW));
            int line = metric(DesignMetrics.Role.TAB_DIVIDER);
            for (int i = 0; i < slots.size() - 1; i++) {
                Rectangle bounds = slots.get(i);
                if (bounds.width > 0) g.fillRect(bounds.x + bounds.width - line, 0, line, strip.getHeight());
            }
            g.fillRect(0, strip.getHeight() - line, strip.getWidth(), line);
        } finally { g.dispose(); }
    }

    /** The status bar's running dot at its leading gap: success while a shell runs, the idle status otherwise. */
    public void statusDot(JComponent bar, Graphics graphics, GeneratedTheme theme, boolean running) {
        var g = (Graphics2D) graphics.create();
        try {
            var palette = theme.semantic();
            g.setColor(running ? SurfaceContrast.on(palette, SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_STATUS, SurfaceContrast.TEXT)
                : palette.color(SemanticColor.STATUS_IDLE));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = metric(DesignMetrics.Role.STATUS_RUNNING_DOT);
            g.fillOval(metric(DesignMetrics.Role.STATUS_GAP), (bar.getHeight() - size) / 2, size, size);
        } finally { g.dispose(); }
    }

    /**
     * An unread badge at an icon's top-right corner on the rail: a rail-coloured ring, then the accent (or danger) dot
     * held to the indicator floor on the rail surface.
     */
    public void badge(Graphics graphics, int x, int y, int iconWidth, GeneratedTheme theme, boolean error) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int dot = metric(DesignMetrics.Role.RAIL_BADGE_DOT), ring = metric(DesignMetrics.Role.HAIRLINE);
            int left = x + iconWidth - dot;
            var palette = theme.semantic();
            Color rail = palette.color(SemanticColor.SURFACE_APP);
            if (rail != null) { g.setColor(rail); g.fillOval(left - ring, y - ring, dot + 2 * ring, dot + 2 * ring); }
            g.setColor(SurfaceContrast.on(palette, error ? SemanticColor.STATUS_DANGER : SemanticColor.ACCENT, SemanticColor.SURFACE_APP,
                SurfaceContrast.INDICATOR));
            g.fillOval(left, y, dot, dot);
        } finally { g.dispose(); }
    }

    /** A tool window header's connected dot: the success status held to the indicator floor on the panel surface. */
    public void connectedDot(Graphics graphics, int x, int y, int size, GeneratedTheme theme) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SurfaceContrast.on(theme.semantic(), SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_PANEL, SurfaceContrast.INDICATOR));
            g.fillOval(x, y, size, size);
        } finally { g.dispose(); }
    }

    /** The thin rule centred in a native split divider's wider drag area, in the divider token. */
    public void splitDivider(Graphics graphics, int x, int y, int width, int height, boolean vertical, GeneratedTheme theme) {
        int thickness = metric(DesignMetrics.Role.SPLIT_STROKE);
        Graphics g = graphics.create();
        try {
            g.setColor(theme.semantic().color(SemanticColor.BORDER_WINDOW));
            if (vertical) g.fillRect(x + (width - thickness) / 2, y, thickness, height);
            else g.fillRect(x, y + (height - thickness) / 2, width, thickness);
        } finally { g.dispose(); }
    }

    /** A dock drop guide colour: the accent at the named fill or border alpha. */
    public Color dropGuide(GeneratedTheme theme, boolean border) {
        Color accent = theme.semantic().color(SemanticColor.ACCENT);
        return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
            value(border ? DesignMetrics.Role.DOCK_DROP_BORDER_ALPHA : DesignMetrics.Role.DOCK_DROP_FILL_ALPHA));
    }

    /**
     * Dock drop guides: a square highlight over a whole zone and a rounded placeholder where the window lands. Each fill
     * sits inside its border so the two translucent colours never stack; the placeholder border is stroked half a pixel
     * in, so its inner edge and the fill share one corner diameter.
     */
    public void dropGuides(Graphics graphics, Rectangle highlight, Rectangle placeholder, Color fill, Color border) {
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
                double arc = metric(DesignMetrics.Role.DOCK_DROP_ARC);
                double inner = Math.max(0, arc - 1);
                g.setColor(fill);
                g.fill(new java.awt.geom.RoundRectangle2D.Double(placeholder.x + 1, placeholder.y + 1, placeholder.width - 2, placeholder.height - 2, inner, inner));
                g.setColor(border);
                g.draw(new java.awt.geom.RoundRectangle2D.Double(placeholder.x + 0.5, placeholder.y + 0.5, placeholder.width - 1, placeholder.height - 1, arc, arc));
            }
        } finally { g.dispose(); }
    }

    /**
     * The command palette's soft shadow around its card, theme.md's popover shadow: black, 12 px down, 32 px blur, 50% in
     * Dark and 18% in Light (as {@code dark} says).
     */
    public void popupShadow(Graphics graphics, Rectangle card, GeneratedTheme theme, boolean dark) {
        ThemePainting.softShadow((Graphics2D) graphics, new java.awt.geom.Rectangle2D.Double(card.x, card.y, card.width, card.height),
            metric(DesignMetrics.Role.PALETTE_CORNER_ARC) / 2.0, metric(DesignMetrics.Role.POPOVER_SHADOW_OFFSET),
            metric(DesignMetrics.Role.POPOVER_SHADOW_BLUR),
            value(dark ? DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK : DesignMetrics.Role.POPOVER_SHADOW_PERCENT_LIGHT) / 100.0);
    }

    /** The dock's 1 px rules on the window surface. */
    public static final PaintPair DOCK_RULE = new PaintPair(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_APP, SurfaceContrast.Guard.STRUCTURAL);
    /** A label on the stripe, on a hovered closed label's tile and on an open label's tile. */
    public static final PaintPair STRIPE_LABEL = new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_STRIPE, SurfaceContrast.Guard.TEXT),
        HOVERED_STRIPE_LABEL = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CONTROL_HOVER, SurfaceContrast.Guard.TEXT),
        OPEN_STRIPE_LABEL = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CONTROL_SELECTED, SurfaceContrast.Guard.TEXT);
    /** The accent and error count pills' text on their grounds. */
    public static final PaintPair ACCENT_PILL = new PaintPair(SemanticColor.ACCENT, DerivedColor.INFO_GROUND, SurfaceContrast.Guard.TEXT),
        ERROR_PILL = new PaintPair(SemanticColor.STATUS_DANGER, DerivedColor.DANGER_GROUND, SurfaceContrast.Guard.TEXT);
    /**
     * The focused header strip's surface, held 1 CIE L* past the other strips' toward the text, so a focused frame shows
     * whatever the theme authors for the two (spec 10.7): a theme that authors both alike (Catppuccin) still shows it, and
     * the Workstation mock's own pair, 1.5 apart, paints as authored.
     */
    public static final PaintPair FOCUSED_HEADER = new PaintPair(DerivedColor.SURFACE_HEADER_ACTIVE, DerivedColor.SURFACE_HEADER, SurfaceContrast.Guard.beyond(1));
    /**
     * A header strip's title, word detail, count, connected dot and action glyphs, on the inactive strip and on the focused
     * one; the focused pairs are held on {@link #FOCUSED_HEADER}'s painted surface.
     */
    public static final PaintPair HEADER_TITLE = new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_HEADER, SurfaceContrast.Guard.TEXT),
        FOCUSED_HEADER_TITLE = new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT),
        HEADER_DETAIL = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_HEADER, SurfaceContrast.Guard.TEXT),
        FOCUSED_HEADER_DETAIL = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT),
        HEADER_COUNT = new PaintPair(SemanticColor.TEXT_SECONDARY, DerivedColor.SURFACE_HEADER, SurfaceContrast.Guard.TEXT),
        FOCUSED_HEADER_COUNT = new PaintPair(SemanticColor.TEXT_SECONDARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT),
        HEADER_DOT = new PaintPair(SemanticColor.STATUS_SUCCESS, DerivedColor.SURFACE_HEADER, SurfaceContrast.Guard.INDICATOR),
        FOCUSED_HEADER_DOT = new PaintPair(SemanticColor.STATUS_SUCCESS, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.INDICATOR),
        HEADER_ACTION = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_HEADER, SurfaceContrast.Guard.INDICATOR),
        FOCUSED_HEADER_ACTION = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.INDICATOR);

    /**
     * The pairs the classic dock chrome paints (spec 10.7 and 7.3), each through its guard, in this order: the 1 px rules;
     * a label on the stripe, a hovered tile and an open tile; the count pills; the focused strip's surface; and the header
     * strips' title, detail, count, connected dot and action glyphs, each on the inactive, then the focused, strip.
     */
    public static final java.util.List<PaintPair> DOCK_PAIRS = java.util.List.of(DOCK_RULE, STRIPE_LABEL, HOVERED_STRIPE_LABEL,
        OPEN_STRIPE_LABEL, ACCENT_PILL, ERROR_PILL, FOCUSED_HEADER, HEADER_TITLE, FOCUSED_HEADER_TITLE, HEADER_DETAIL,
        FOCUSED_HEADER_DETAIL, HEADER_COUNT, FOCUSED_HEADER_COUNT, HEADER_DOT, FOCUSED_HEADER_DOT, HEADER_ACTION,
        FOCUSED_HEADER_ACTION);

    /** The dock's 1 px rule: between frames, along a stripe's inner edge, under a header strip. */
    public Color dockRule(GeneratedTheme theme) { return SurfaceContrast.painted(theme, DOCK_RULE); }

    /** A stripe's and the bottom strip's surface. */
    public Color stripeSurface(GeneratedTheme theme) { return DerivedColor.SURFACE_STRIPE.color(theme); }

    /** A label's tile: an open window's, a hovered closed one's, or null for none. */
    public Color stripeTile(GeneratedTheme theme, boolean open, boolean hover) {
        if (open) return theme.semantic().color(SemanticColor.SURFACE_CONTROL_SELECTED);
        return hover ? theme.semantic().color(SemanticColor.SURFACE_CONTROL_HOVER) : null;
    }

    /** A label's text on the stripe, on a hovered closed label's tile, or on an open label's tile. */
    public Color stripeLabel(GeneratedTheme theme, boolean open, boolean hover) {
        return SurfaceContrast.painted(theme, open ? OPEN_STRIPE_LABEL : hover ? HOVERED_STRIPE_LABEL : STRIPE_LABEL);
    }

    /** The bottom strip's divider after each label. */
    public Color stripDivider(GeneratedTheme theme) { return DerivedColor.BORDER_DIVIDER.color(theme); }

    /** The size of a count pill showing {@code text}: the pill's height, at least its width or the text and its padding. */
    public Dimension countPill(String text) {
        int width = Math.max(metric(DesignMetrics.Role.STRIPE_PILL_WIDTH),
            measureText(font(Typography.Role.CHIP), text) + 2 * metric(DesignMetrics.Role.STRIPE_PILL_PADDING));
        return new Dimension(width, metric(DesignMetrics.Role.STRIPE_PILL_HEIGHT));
    }

    /**
     * A count pill with its top-left corner at ({@code x}, {@code y}) in the graphics' current transform: the tone's
     * ground (info for the accent tone, danger for the error tone), fully rounded, the text centred in the tone's colour.
     */
    public void countPill(Graphics graphics, int x, int y, String text, GeneratedTheme theme, boolean error) {
        Dimension size = countPill(text);
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            g.setColor((error ? DerivedColor.DANGER_GROUND : DerivedColor.INFO_GROUND).color(theme));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(x, y, size.width, size.height, size.height, size.height));
            Font font = font(Typography.Role.CHIP);
            g.setFont(font);
            g.setColor(SurfaceContrast.painted(theme, error ? ERROR_PILL : ACCENT_PILL));
            FontMetrics metrics = g.getFontMetrics(font);
            g.drawString(text, x + (size.width - measureText(font, text)) / 2f,
                y + (size.height - metrics.getAscent() - metrics.getDescent()) / 2f + metrics.getAscent());
        } finally { g.dispose(); }
    }

    /** A header strip's surface: the focused window's ({@link #FOCUSED_HEADER}), or the others'. */
    public Color headerSurface(GeneratedTheme theme, boolean focused) {
        return focused ? SurfaceContrast.painted(theme, FOCUSED_HEADER) : DerivedColor.SURFACE_HEADER.color(theme);
    }

    /** {@code pair}, one of a header strip's, as painted on the strip's own surface. */
    private Color onHeader(GeneratedTheme theme, PaintPair pair, boolean focused) {
        return SurfaceContrast.painted(theme, pair, headerSurface(theme, focused));
    }

    /** A header strip's title. */
    public Color headerTitle(GeneratedTheme theme, boolean focused) { return onHeader(theme, focused ? FOCUSED_HEADER_TITLE : HEADER_TITLE, focused); }

    /** A header strip's detail: muted, a count (a parenthesised number) more so. */
    public Color headerDetail(GeneratedTheme theme, boolean focused, boolean count) {
        return onHeader(theme, count ? (focused ? FOCUSED_HEADER_COUNT : HEADER_COUNT) : (focused ? FOCUSED_HEADER_DETAIL : HEADER_DETAIL), focused);
    }

    /** A header strip's action glyphs: native, the host's own and contributed actions alike. */
    public Color headerAction(GeneratedTheme theme, boolean focused) { return onHeader(theme, focused ? FOCUSED_HEADER_ACTION : HEADER_ACTION, focused); }

    /** A header strip over {@code width} x {@code height}: its surface, with the 1 px rule along its bottom edge. */
    public void headerStrip(Graphics graphics, int width, int height, GeneratedTheme theme, boolean focused) {
        Graphics g = graphics.create();
        try {
            int rule = metric(DesignMetrics.Role.DOCK_RULE);
            g.setColor(headerSurface(theme, focused));
            g.fillRect(0, 0, width, Math.max(0, height - rule));
            g.setColor(dockRule(theme));
            g.fillRect(0, Math.max(0, height - rule), width, rule);
        } finally { g.dispose(); }
    }

    /** A header strip's connected dot: the success status held to the indicator floor on the strip's own surface. */
    public void headerDot(Graphics graphics, int x, int y, int size, GeneratedTheme theme, boolean focused) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(onHeader(theme, focused ? FOCUSED_HEADER_DOT : HEADER_DOT, focused));
            g.fillOval(x, y, size, size);
        } finally { g.dispose(); }
    }

    public String clip(String text, FontMetrics metrics, int width) {
        if (width <= 0) return "";
        if (metrics.stringWidth(text) <= width) return text;
        for (int end = text.offsetByCodePoints(text.length(), -1); end > 0; end = text.offsetByCodePoints(end, -1)) {
            String candidate = text.substring(0, end) + "…";
            if (metrics.stringWidth(candidate) <= width) return candidate;
        }
        return "";
    }
}

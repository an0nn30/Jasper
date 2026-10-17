package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The classic buttons (spec 6.4, DESIGN_LANGUAGE §6): a top-to-bottom gradient from {@code button.secondary.bg} to
 * {@code button.end} inside a {@code button.secondary.border} edge that darkens to {@code border.hover} on hover; the
 * primary filled with the accent inside {@code accent.edge}, its label medium, with no inset line or drop; a disabled
 * face flat; ghosts flat until hovered where a toolbar holds them. Dropdowns and picker faces follow, ending with a ▼.
 */
final class ClassicButtonFace implements ButtonFace {
    private static final float DISABLED = .42f;
    private final PaintPair primaryLabel, label, edge, primaryEdge, hoverEdge, arrow, primaryHoverLabel;

    /** A face whose buttons stand on {@code surface}, the kit's dialog surface. */
    ClassicButtonFace(SemanticColor surface) {
        primaryLabel = new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT, SurfaceContrast.Guard.TEXT);
        label = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.BUTTON_SECONDARY_BG, SurfaceContrast.Guard.TEXT);
        edge = new PaintPair(SemanticColor.BUTTON_SECONDARY_BORDER, surface, SurfaceContrast.Guard.CONTROL);
        primaryEdge = new PaintPair(DerivedColor.ACCENT_EDGE, surface, SurfaceContrast.Guard.CONTROL);
        hoverEdge = new PaintPair(DerivedColor.BORDER_HOVER, surface, SurfaceContrast.Guard.CONTROL);
        arrow = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.BUTTON_SECONDARY_BG, SurfaceContrast.Guard.INDICATOR);
        primaryHoverLabel = new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT_HOVER, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(primaryLabel, label, edge, primaryEdge, hoverEdge, arrow, primaryHoverLabel); }

    /**
     * Plan 4b Rulings 7: flat until hovered in toolbars, rows, panel headers and contextual slots, and as a composite control's
     * inner part; a standard button elsewhere. A menu button's parts and face stand where its surface does.
     */
    static boolean flat(Buttons.KitButton b) {
        var placed = b.placement();
        return b.inner || b.panel || b.panelRow || b.contextual || b.orderedCommand
            || SwingUtilities.getAncestorOfClass(InteractiveRows.Row.class, placed) != null
            || placed.getParent() instanceof Stacks.StackSurface stack && (stack.role == Nodes.LayoutRole.INLINE || stack.role == Nodes.LayoutRole.NAVIGATION);
    }

    private static boolean ghost(Buttons.KitButton b) { return b.kind == Nodes.ButtonKind.GHOST || b.kind == Nodes.ButtonKind.DESTRUCTIVE_GHOST; }

    /** The standard fill in {@code face}: {@code button.secondary.bg} at the top to {@code button.end} at the bottom, reversed while pressed. */
    private static void gradient(Graphics2D g, KitContext c, Shape face, boolean pressed) {
        Color top = ThemePainting.color(c, SemanticColor.BUTTON_SECONDARY_BG), bottom = ThemePainting.role(c, DerivedColor.BUTTON_END);
        var bounds = face.getBounds2D();
        g.setPaint(new GradientPaint(0, (float) bounds.getMinY(), pressed ? bottom : top, 0, (float) bounds.getMaxY(), pressed ? top : bottom));
        g.fill(face);
    }

    @Override public void paintFace(Buttons.KitButton b, Graphics2D g, boolean hover, boolean focused) {
        if (b.embedded || b.recorder() || b.circular || b.kind == Nodes.ButtonKind.LINK && !b.sideColumn) { b.paintRaised(g, hover, focused); return; }
        var c = b.context();
        boolean flat = ghost(b) && flat(b), selected = b.toggle && b.isSelected(), pressed = b.isEnabled() && b.getModel().isPressed();
        if (flat && !hover && !selected) { ThemePainting.rounded(g, b, c, null, null, focused, false); return; }
        ThemePainting.antialias(g);
        int inset = b.panelDetail ? ThemePainting.metric(c, DesignMetrics.Role.FOCUS_RING) : 0;
        double trim = b.panelDetail ? UIScale.scale((float) c.metrics().value(DesignMetrics.Role.PANEL_DETAIL_ACTION_BOTTOM_TRIM)) : 0;
        double radius = ThemePainting.metric(c, DesignMetrics.Role.CONTROL_RADIUS);
        var pair = b.placement().getParent() instanceof Stacks.StackSurface stack && stack.joined ? stack : null;
        Shape face = pair == null ? new RoundRectangle2D.Double(inset, inset, b.getWidth() - 2.0 * inset, b.getHeight() - trim - 2.0 * inset, 2 * radius, 2 * radius)
            : joined(pair, b.placement(), 0, b.getWidth(), b.getHeight(), radius);
        boolean primary = b.kind == Nodes.ButtonKind.PRIMARY;
        Color border;
        if (!b.isEnabled()) {
            g.setColor(ThemePainting.role(c, primary ? DerivedColor.SURFACE_HEADER : DerivedColor.SURFACE_TABLE_HEADER)); g.fill(face);
            // A joined pair keeps one outline: its disabled half takes the standard edge (the mocks' history pair).
            border = pair != null ? ThemePainting.painted(c, edge) : ThemePainting.role(c, DerivedColor.BORDER_DIVIDER);
        } else if (selected || flat) {
            g.setColor(ThemePainting.color(c, selected ? SemanticColor.SURFACE_CONTROL_SELECTED : SemanticColor.SURFACE_CONTROL_HOVER)); g.fill(face);
            border = ThemePainting.painted(c, hoverEdge);
        } else if (primary) {
            g.setColor(ThemePainting.color(c, hover || pressed ? SemanticColor.ACCENT_HOVER : SemanticColor.ACCENT)); g.fill(face);
            border = ThemePainting.painted(c, primaryEdge);
        } else {
            gradient(g, c, face, pressed);
            border = ThemePainting.painted(c, hover ? hoverEdge : edge);
        }
        // A borderless face (a strip's icon-only menu) keeps its fill without the outline, as by default.
        if (pair == null) { ThemePainting.rounded(g, b, c, null, b.borderless ? null : border, focused, false, radius, inset, trim); return; }
        ThemePainting.rounded(g, b, c, null, null, focused, false, radius, 0, 0);
        if (b.borderless) return;
        var line = (Graphics2D) g.create();
        try {
            line.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            line.setColor(border); line.setStroke(new BasicStroke(UIScale.scale(1f)));
            line.draw(joined(pair, b.placement(), .5, b.getWidth() - 1, b.getHeight() - 1, radius));
        } finally { line.dispose(); }
    }

    /**
     * A joined pair's half at {@code offset}, {@code width} by {@code height}: rounded on the pair's outer side only, so the
     * halves meet square at the border they share.
     */
    private static Shape joined(Stacks.StackSurface pair, Component half, double offset, double width, double height, double radius) {
        var shape = new Area(new RoundRectangle2D.Double(offset, offset, width, height, 2 * radius, 2 * radius));
        double square = Math.min(radius, width / 2);
        if (pair.getComponentCount() > 1 && pair.getComponent(0) != half) shape.add(new Area(new Rectangle2D.Double(offset, offset, square, height)));
        if (pair.getComponentCount() > 1 && pair.getComponent(pair.getComponentCount() - 1) != half)
            shape.add(new Area(new Rectangle2D.Double(offset + width - square, offset, square, height)));
        return shape;
    }

    @Override public Color text(Buttons.KitButton b, boolean hover) {
        var c = b.context();
        if (b.embedded || b.recorder() || b.circular || b.onSelection || b.kind == Nodes.ButtonKind.LINK && !b.sideColumn) return ThemePainting.color(c, b.textRole(hover));
        if (b.sideColumn && b.isEnabled() && b.kind != Nodes.ButtonKind.PRIMARY) return ThemePainting.painted(c, label);
        if (!b.isEnabled()) return ColorMath.opacity(ThemePainting.color(c, SemanticColor.TEXT_PRIMARY), DISABLED);
        if (ghost(b) && flat(b)) return ThemePainting.color(c, b.textRole(hover));
        // A hovered or pressed primary fills accent.hover (paintFace), so its label is guarded there.
        if (b.kind == Nodes.ButtonKind.PRIMARY) return ThemePainting.painted(c, hover || b.getModel().isPressed() ? primaryHoverLabel : primaryLabel);
        var role = b.textRole(hover);
        return role == SemanticColor.TEXT_STRONG_SECONDARY || ghost(b) ? ThemePainting.painted(c, label) : ThemePainting.color(c, role);
    }

    /**
     * The buttons this face leaves to the default treatment fade as one layer: those {@code paintFace} hands to the
     * raised face, and those on a selected row, whose labels {@code text} keeps in their role. The condition is
     * {@code text}'s, which is {@code paintFace}'s with the selected row added, so the three change together. Every
     * other button draws its own flat disabled face (plan 4b Rulings 6); a disabled SECONDARY or PRIMARY button on a selected
     * row draws that flat face and is faded with it as well.
     */
    @Override public boolean fades(Buttons.KitButton b) {
        return b.embedded || b.recorder() || b.circular || b.onSelection || b.kind == Nodes.ButtonKind.LINK && !b.sideColumn;
    }

    @Override public Typography.Role label(Nodes.ButtonKind kind, Typography.Role role) {
        return kind == Nodes.ButtonKind.PRIMARY && role == Typography.Role.LABEL ? Typography.Role.BODY_STRONG : role;
    }

    @Override public void paintCombo(Choices.ChoiceCombo combo, Graphics2D g, boolean hover, boolean focused) {
        var c = combo.context;
        ThemePainting.antialias(g);
        double radius = ThemePainting.metric(c, DesignMetrics.Role.CONTROL_RADIUS);
        var face = new RoundRectangle2D.Double(0, 0, combo.getWidth(), combo.getHeight(), 2 * radius, 2 * radius);
        if (combo.isEnabled()) gradient(g, c, face, false);
        else { g.setColor(ThemePainting.role(c, DerivedColor.SURFACE_TABLE_HEADER)); g.fill(face); }
        ThemePainting.rounded(g, combo, c, null, focused ? ThemePainting.color(c, SemanticColor.BORDER_FOCUS)
            : !combo.isEnabled() ? ThemePainting.role(c, DerivedColor.BORDER_DIVIDER) : ThemePainting.painted(c, hover ? hoverEdge : edge), focused, false);
    }

    /** The guarded ▼ in text.secondary; a bare cell dropdown on a selected row takes the row's supporting ink, as by default. */
    @Override public void paintArrow(Choices.ChoiceCombo combo, JComponent owner, Graphics2D g, int beyond, boolean settings) {
        var c = combo.context;
        down(owner, g, c, beyond, combo.bare() && combo.cellInk != null ? ThemePainting.color(c, combo.cellInk.supporting) : ThemePainting.painted(c, arrow));
    }

    /** The drawn ▼ in {@code colour}, centred {@code DROPDOWN_GLYPH_INSET} in from a trailing edge {@code beyond} past {@code owner}'s. */
    static void down(JComponent owner, Graphics2D g, KitContext c, int beyond, Color colour) {
        int glyph = ThemePainting.metric(c, DesignMetrics.Role.DROPDOWN_GLYPH);
        double right = owner.getWidth() + beyond - ThemePainting.metric(c, DesignMetrics.Role.DROPDOWN_GLYPH_INSET);
        ClassicGlyphs.down(g, right - glyph / 2.0, (owner.getHeight() - glyph * .8) / 2, glyph, colour);
    }

    @Override public void paintPickerArrow(Choices.PickerButton picker, Graphics2D g) {
        down(picker, g, picker.context, 0, ThemePainting.painted(picker.context, arrow));
    }

    @Override public void paintPicker(JComponent owner, Graphics graphics, KitContext c, Nodes.PickerFace face, boolean empty, boolean focus,
                                      boolean hover, boolean expanded) {
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            double radius = ThemePainting.metric(c, DesignMetrics.Role.CONTROL_RADIUS);
            var shape = new RoundRectangle2D.Double(0, 0, owner.getWidth(), owner.getHeight(), 2 * radius, 2 * radius);
            boolean enabled = owner.isEnabled();
            // Disabled, like a disabled dropdown and button (plan 4b Rulings 6): a flat fill, the lighter edge, then the line and arrow faded.
            if (enabled) gradient(g, c, shape, false);
            else { g.setColor(ThemePainting.role(c, DerivedColor.SURFACE_TABLE_HEADER)); g.fill(shape); }
            ThemePainting.rounded(g, owner, c, null, focus || expanded ? ThemePainting.color(c, SemanticColor.BORDER_FOCUS)
                : !enabled ? ThemePainting.role(c, DerivedColor.BORDER_DIVIDER) : ThemePainting.painted(c, hover ? hoverEdge : edge), focus || expanded, false);
            if (!enabled) g.setComposite(AlphaComposite.SrcOver.derive(DISABLED));
            int pad = ThemePainting.metric(c, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING), glyph = ThemePainting.metric(c, DesignMetrics.Role.DROPDOWN_GLYPH);
            int right = owner.getWidth() - ThemePainting.metric(c, DesignMetrics.Role.DROPDOWN_GLYPH_INSET) - glyph;
            ClassicGlyphs.down(g, right + glyph / 2.0, (owner.getHeight() - glyph * .8) / 2, glyph, ThemePainting.painted(c, arrow));
            PickerMenus.line(g, c, empty ? "Choose…" : face.primary(), empty ? "" : face.caption(), pad, 0, Math.max(0, right - 2 * pad),
                owner.getHeight(), false, empty, face.density() == Nodes.PickerDensity.COMPACT, false, face.technicalCaption());
        } finally { g.dispose(); }
    }

    @Override public boolean tiles() { return false; }
    /** The classic pair's halves share one border, as the Settings mocks' history pair does (51 wide for two 26 px halves). */
    @Override public int joinedOverlap() { return UIScale.scale(1); }
    @Override public Color menuDivider(KitContext context) { return ThemePainting.role(context, DerivedColor.BORDER_ROW); }
}

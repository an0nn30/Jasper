package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.swing.JLabel;

/**
 * The classic route (spec 5.2, the New Tunnel mock; plan 4c Rulings 12): one boxed strip on the input surface, equal
 * cells each led by a 3 px edge in its step's hue (the step's leading Icon is hidden), upper-case captions, and a drawn
 * arrow at the end of every cell but the last.
 */
final class ClassicRouteFace implements RouteFace {
    private static final List<Nodes.Hue> HUES = List.of(Nodes.Hue.BLUE, Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE, Nodes.Hue.NEUTRAL);
    private final PaintPair box, caption, arrow;
    private final List<PaintPair> edges;

    /** A face whose strip stands on {@code surface}, the kit's dialog surface. */
    ClassicRouteFace(SemanticColor surface) {
        box = new PaintPair(SemanticColor.BORDER_CONTROL, surface, SurfaceContrast.Guard.CONTROL);
        caption = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.TEXT);
        arrow = new PaintPair(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.INDICATOR);
        edges = HUES.stream().map(hue -> new PaintPair(role(hue), SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.INDICATOR)).toList();
    }

    @Override public List<PaintPair> pairs() {
        var all = new java.util.ArrayList<PaintPair>(List.of(box, caption, arrow));
        all.addAll(edges);
        return List.copyOf(all);
    }

    /** The edge pair of {@code hue}; NONE takes the neutral pair. */
    PaintPair edge(Nodes.Hue hue) { return edges.get(HUES.indexOf(hue == Nodes.Hue.NONE ? Nodes.Hue.NEUTRAL : hue)); }

    private static SemanticColor role(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
            case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL, NONE -> SemanticColor.HUE_NEUTRAL;
        };
    }

    /** A step's hue: its leading Icon's, else neutral. */
    static Nodes.Hue hue(Nodes.Node step) {
        Nodes.Node lead = step instanceof Nodes.Stack stack && !stack.children().isEmpty() ? stack.children().getFirst() : step;
        return lead instanceof Nodes.Icon icon ? icon.hue() : Nodes.Hue.NEUTRAL;
    }

    @Override public boolean tiles() { return false; }
    @Override public LayoutManager layout(Stacks.StackControl route) { return new EdgedLayout(route); }

    @Override public void strip(Stacks.StackControl route, List<ControlRegistry.Control> steps) {
        var hues = new java.util.IdentityHashMap<Component, Nodes.Hue>();
        for (var step : steps) hues.put(step.component, hue(step.node));
        ((Stacks.StackSurface) route.component).stepHues = hues;
    }

    /**
     * Upper-case captions (a step's CAPTION or DESCRIPTION line) in their guarded ink, kept through every later refresh by
     * the text control's hook.
     */
    @Override public void step(Stacks.StackControl step, List<ControlRegistry.Control> controls) {
        for (var control : controls)
            if (control.node instanceof Nodes.Text text && (text.role() == Nodes.TextRole.CAPTION || text.role() == Nodes.TextRole.DESCRIPTION)
                    && control instanceof Labels.TextControl label) {
                label.styled = shown -> {
                    shown.setText(text.text().toUpperCase(Locale.ROOT));
                    shown.setForeground(ThemePainting.painted(step.context(), caption));
                };
                label.styled.accept((JLabel) label.component);
            }
    }

    @Override public LayoutManager stepLayout(Stacks.StackControl step) { return new LinesLayout(); }

    /** A classic step: its texts stacked and centred; its leading Icon gets no room (its cell's hue edge stands for it). */
    static final class LinesLayout implements Composition.WidthLayout {
        static boolean icon(Component c) { return c instanceof JLabel label && label.getIcon() != null && (label.getText() == null || label.getText().isEmpty()); }
        List<Component> lines(Container parent) { return Arrays.stream(parent.getComponents()).filter(c -> c.isVisible() && !icon(c)).toList(); }
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public int height(Container parent, int width) { return lines(parent).stream().mapToInt(c -> c.getPreferredSize().height).sum(); }
        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = lines(parent).stream().mapToInt(c -> c.getPreferredSize().width).max().orElse(0);
            return new Dimension(width, height(parent, width));
        }
        @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, height(parent, 0)); }
        @Override public void layoutContainer(Container parent) {
            for (var child : parent.getComponents()) if (icon(child)) child.setBounds(0, 0, 0, 0);
            int y = Math.max(0, (parent.getHeight() - height(parent, parent.getWidth())) / 2);
            for (var line : lines(parent)) {
                int h = line.getPreferredSize().height;
                line.setBounds(0, y, parent.getWidth(), h);
                y += h;
            }
        }
    }

    @Override public void paintStrip(Stacks.StackSurface strip, Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            var c = strip.context;
            ThemePainting.antialias(g);
            if (!strip.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
            int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE), top = ThemePainting.metric(c, DesignMetrics.Role.ROUTE_TOP);
            int width = strip.getWidth(), height = strip.getHeight() - top;
            g.setColor(ThemePainting.color(c, SemanticColor.SURFACE_INPUT));
            g.fillRect(0, top, width, height);
            g.setColor(ThemePainting.painted(c, box));
            g.fillRect(0, top, width, line);
            g.fillRect(0, top + height - line, width, line);
            g.fillRect(0, top, line, height);
            g.fillRect(width - line, top, line, height);
            int edge = ThemePainting.metric(c, DesignMetrics.Role.ROUTE_EDGE);
            for (int i = 0; i < strip.cells.length; i++) {
                var hue = i < strip.hues.size() ? strip.hues.get(i) : Nodes.Hue.NEUTRAL;
                g.setColor(ThemePainting.painted(c, edge(hue)));
                g.fillRect(strip.cells[i], top + line, edge, Math.max(0, height - 2 * line));
            }
            int size = ThemePainting.metric(c, DesignMetrics.Role.ROUTE_ARROW);
            var ink = ThemePainting.painted(c, arrow);
            for (int x : strip.arrows) arrow(g, x, top + height / 2.0, size, ink);
        } finally { g.dispose(); }
    }

    /** A rightward arrow {@code size} wide from {@code x}, centred on {@code cy}: a shaft and a filled head. */
    static void arrow(Graphics2D g, double x, double cy, double size, Color color) {
        double head = size * .45;
        var shaft = (Graphics2D) g.create();
        try {
            shaft.setColor(color);
            shaft.setStroke(new BasicStroke((float) Math.max(1, size / 12)));
            shaft.draw(new Line2D.Double(x, cy, x + size - head * .6, cy));
        } finally { shaft.dispose(); }
        ClassicGlyphs.right(g, x + size - head * .8, cy, head, color);
    }

    /**
     * Equal cells across the strip inside its edge: each step after its cell's hue edge and padding, truncated before
     * its cell's arrow (or the strip's end padding for the last), centred on the strip's height.
     */
    static final class EdgedLayout implements Composition.WidthLayout {
        final Stacks.StackControl owner;

        EdgedLayout(Stacks.StackControl owner) { this.owner = owner; }

        int metric(DesignMetrics.Role role) { return ThemePainting.metric(owner.context(), role); }

        List<Component> steps(Container parent) { return Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList(); }

        /** A cell's room besides its step: the edge, the padding before the step, and the arrow (or end padding) after it. */
        int chrome() {
            return metric(DesignMetrics.Role.ROUTE_EDGE) + metric(DesignMetrics.Role.ROUTE_PADDING) + metric(DesignMetrics.Role.ROUTE_ARROW_GAP)
                + metric(DesignMetrics.Role.ROUTE_ARROW) + metric(DesignMetrics.Role.ROUTE_ARROW_INSET);
        }

        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }

        @Override public int height(Container parent, int width) {
            int tallest = steps(parent).stream().mapToInt(c -> c.getPreferredSize().height).max().orElse(0);
            return metric(DesignMetrics.Role.ROUTE_TOP) + Math.max(metric(DesignMetrics.Role.ROUTE_HEIGHT),
                tallest + 2 * (metric(DesignMetrics.Role.HAIRLINE) + metric(DesignMetrics.Role.ROUTE_VERTICAL_PADDING)));
        }

        int width(List<Component> steps, boolean minimum) {
            int widest = steps.stream().mapToInt(c -> (minimum ? c.getMinimumSize() : c.getPreferredSize()).width).max().orElse(0);
            return 2 * metric(DesignMetrics.Role.HAIRLINE) + steps.size() * (widest + chrome());
        }

        @Override public Dimension preferredLayoutSize(Container parent) { var steps = steps(parent); int w = width(steps, false); return new Dimension(w, height(parent, w)); }
        @Override public Dimension minimumLayoutSize(Container parent) { var steps = steps(parent); int w = width(steps, true); return new Dimension(w, height(parent, w)); }

        @Override public void layoutContainer(Container parent) {
            var steps = steps(parent);
            var surface = (Stacks.StackSurface) parent;
            int count = steps.size(), line = metric(DesignMetrics.Role.HAIRLINE), top = metric(DesignMetrics.Role.ROUTE_TOP);
            int height = parent.getHeight() - top, inner = Math.max(0, parent.getWidth() - 2 * line);
            surface.cells = new int[count];
            surface.arrows = new int[Math.max(0, count - 1)];
            // The visible steps' hues, cell for cell.
            surface.hues = steps.stream().map(step -> surface.stepHues.getOrDefault(step, Nodes.Hue.NEUTRAL)).toList();
            for (int i = 0; i < count; i++) {
                int start = line + (int) ((long) inner * i / count), end = line + (int) ((long) inner * (i + 1) / count);
                boolean last = i == count - 1;
                surface.cells[i] = start;
                if (!last) surface.arrows[i] = end - metric(DesignMetrics.Role.ROUTE_ARROW_INSET) - metric(DesignMetrics.Role.ROUTE_ARROW);
                int x = start + metric(DesignMetrics.Role.ROUTE_EDGE) + metric(DesignMetrics.Role.ROUTE_PADDING);
                int limit = last ? end - metric(DesignMetrics.Role.ROUTE_PADDING) : surface.arrows[i] - metric(DesignMetrics.Role.ROUTE_ARROW_GAP);
                var step = steps.get(i);
                int stepHeight = Math.min(height, step.getPreferredSize().height);
                step.setBounds(x, top + (height - stepHeight) / 2, Math.min(step.getPreferredSize().width, Math.max(0, limit - x)), stepHeight);
            }
        }
    }
}

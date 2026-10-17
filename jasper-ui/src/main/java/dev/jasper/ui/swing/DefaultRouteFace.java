package dev.jasper.ui.swing;

import dev.jasper.ui.theme.PaintPair;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.util.List;

/** The default route: equal columns with the arrow opening each later one, each step led by its hue tile. */
final class DefaultRouteFace implements RouteFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public boolean tiles() { return true; }
    @Override public LayoutManager layout(Stacks.StackControl route) { return new Stacks.RouteLayout(route); }
    @Override public void paintStrip(Stacks.StackSurface strip, Graphics graphics) { strip.paintRoute(graphics); }
    @Override public void strip(Stacks.StackControl route, List<ControlRegistry.Control> steps) { }
    @Override public void step(Stacks.StackControl step, List<ControlRegistry.Control> controls) { }
    @Override public LayoutManager stepLayout(Stacks.StackControl step) { return new Stacks.RouteStepLayout(step.context()); }
}

package dev.jasper.ui.swing;

import java.awt.Graphics;
import java.awt.LayoutManager;
import java.util.List;

/** The route point (spec 5.2): ROUTE steps as icon tiles with arrows, or edged cells with a hue edge and arrows. */
interface RouteFace extends Face {
    /** Whether each step's leading Icon is drawn as a hue tile. */
    boolean tiles();
    /** The ROUTE stack's layout. */
    LayoutManager layout(Stacks.StackControl route);
    /** Paints the strip behind its steps. */
    void paintStrip(Stacks.StackSurface strip, Graphics graphics);
    /** Prepares the strip after its steps are mounted. */
    void strip(Stacks.StackControl route, List<ControlRegistry.Control> steps);
    /** Prepares one step's children after they refresh. */
    void step(Stacks.StackControl step, List<ControlRegistry.Control> controls);
    /** A step's layout. */
    LayoutManager stepLayout(Stacks.StackControl step);
}

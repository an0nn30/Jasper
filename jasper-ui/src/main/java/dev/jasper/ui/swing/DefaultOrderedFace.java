package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.JLabel;

/** The default ordered list: the rounded box with row dividers, per-row commands and the add action under the rows. */
final class DefaultOrderedFace implements OrderedFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public boolean rowCommands() { return true; }
    @Override public Nodes.TextRole indexRole() { return Nodes.TextRole.CAPTION; }
    @Override public void styleIndex(JLabel index, KitContext context, int row) { }
    @Override public void paintCanvas(InteractiveRows.Canvas canvas, Graphics graphics) { canvas.paintOrdered(graphics); }
    @Override public void paintPane(ItemLists.ListControl list, Graphics2D g) { }
    @Override public void mount(ItemLists.ListControl list, ControlRegistry.Control add) { }
    @Override public void span(Stacks.StackControl stack, List<ControlRegistry.Control> controls) { }
}

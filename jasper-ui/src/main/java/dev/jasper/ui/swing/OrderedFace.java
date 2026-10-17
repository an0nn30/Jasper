package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.JLabel;

/** The ordered-list point (spec 5.2): per-row commands, or a side column of equal buttons acting on the selection. */
interface OrderedFace extends Face {
    /** Whether each row carries its own up, down and remove commands and the add action sits under the rows. */
    boolean rowCommands();
    /** The role of a row's index. */
    Nodes.TextRole indexRole();
    /** Styles the index of the unselected row at {@code row} after it refreshes; a selected row keeps its ink. */
    void styleIndex(JLabel index, KitContext context, int row);
    /** Paints the list's canvas behind its rows. */
    void paintCanvas(InteractiveRows.Canvas canvas, Graphics graphics);
    /** Paints the list's pane behind its viewport. */
    void paintPane(ItemLists.ListControl list, Graphics2D g);
    /** Mounts the list's add action, or null, when the rows carry no commands. */
    void mount(ItemLists.ListControl list, ControlRegistry.Control add);
    /** Lets a side column span an entry row that follows its list in a VERTICAL stack. */
    void span(Stacks.StackControl stack, List<ControlRegistry.Control> controls);
}

package dev.jasper.ui.swing;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;

/**
 * What the table and tree points share (spec 5.2, plan 4c Rulings 1): the header over the rows, the box around them and
 * the hover fill.
 */
interface HeaderFace extends Face {
    /** Styles column title {@code index} after the control builds it: its ink, and its inset after a column separator. */
    void styleTitle(JLabel title, KitContext context, int index);
    /** The header's padding: {@code side} at each end; {@code line} is the titles' height. */
    Insets headerInsets(KitContext context, int side, int line);
    /** Paints behind the titles: nothing, or the classic band with its separators and rule. */
    void paintHeader(JComponent header, Graphics2D g, KitContext context);
    /** {@code owner}'s scroll pane border, or null to keep the pane's own. */
    Border box(InteractiveRows.RowsControl owner);
    /** A hovered or keyboard-focused row's fill. */
    Color hover(KitContext context);
}

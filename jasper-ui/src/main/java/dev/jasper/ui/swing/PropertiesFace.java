package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * How Properties lay out and paint (plan 10a Rulings 4), read from the kit's table point: the default card of SDK 0.18,
 * or the classic Workstation grid and label-colon lines.
 */
interface PropertiesFace extends Face {
    /** The heading label, made once at the control's construction. */
    JLabel title();

    /** The rows' container, made once at the control's construction. */
    JComponent container(PropertyLists.PropertiesControl control);

    /** The text a row's label shows. */
    String labelText(Nodes.Properties node, String label);

    /** Fonts, colours, borders and the control's own layout, after each refresh. */
    void style(PropertyLists.PropertiesControl control);

    /** The layout of the rows' container. */
    Composition.WidthLayout grid(PropertyLists.PropertiesControl control);

    /** Paints the rows' container under its labels and values. */
    void paint(PropertyLists.PropertiesControl control, Graphics2D g, int width, int height);

    /**
     * Whether {@code node}, leading a panel's footer stack, stands flush with the footer's edges, its actions after it at
     * the trailing edge (the classic grid, as the Workstation mock draws the Sessions Properties).
     */
    boolean flush(Nodes.Properties node);
}

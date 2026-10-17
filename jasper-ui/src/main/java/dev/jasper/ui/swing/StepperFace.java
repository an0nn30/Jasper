package dev.jasper.ui.swing;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The stepper point (spec 5.2): a STEPPER (and a SLIDER) field as "− value +", or as a spinner. */
interface StepperFace extends Face {
    /** Places the decrease and increase buttons on the field's surface. */
    void arrange(JPanel surface, JButton decrease, JButton increase);
    /** The value's horizontal alignment in its cell. */
    int alignment();
    /** A step button's size. */
    Dimension stepSize(KitContext context, int direction);
    /** Paints a step button; {@code standard} paints the default look's. */
    void paintStep(JButton button, Graphics2D g, KitContext context, int direction, boolean focus, Runnable standard);
    /** Styles the value and its unit. */
    void styleValue(KitContext context, JComponent value, JLabel unit);
    /** The value cell's least width. */
    int valueWidth(KitContext context);
    /** The field surface's insets round the value and the buttons. */
    Insets surfaceInsets(KitContext context);
    /** Whether a SLIDER field is a slider; otherwise it is built as this stepper. */
    boolean slider();
}

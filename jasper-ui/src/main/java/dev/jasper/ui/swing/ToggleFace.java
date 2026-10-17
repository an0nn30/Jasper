package dev.jasper.ui.swing;

import java.awt.Dimension;
import java.awt.Graphics2D;

/** The toggle point (spec 5.2): a switch trailing its setting row, or a checkbox leading its label. */
interface ToggleFace extends Face {
    /** The control's size, its focus reservation included. */
    Dimension preferredSize(Switches.SwitchButton button);
    /** Paints the control in its own bounds. */
    void paint(Switches.SwitchButton button, Graphics2D g);
    /** Whether the control leads its title in a setting row; otherwise it sits at the row's end. */
    boolean leads();
    /** Whether a change of state moves the face (the knob's travel) while motion is on. */
    boolean moves();
    /** The control's height in a list row, without its focus reservation. */
    int rowHeight(KitContext context, boolean compact);
}

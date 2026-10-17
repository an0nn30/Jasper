package dev.jasper.ui.swing;

import java.awt.Graphics2D;

/** The tag point (spec 5.2): a chip as a borderless tint, or a tiny upper-case label in a 1 px box on the tint. */
interface TagFace extends Face {
    /** Styles a chip's label after the chip refreshes. */
    void style(Indicators.ChipControl chip, Indicators.ChipLabel label);
    /** Paints the chip behind its text. */
    void paint(Indicators.ChipLabel label, Graphics2D g);
}

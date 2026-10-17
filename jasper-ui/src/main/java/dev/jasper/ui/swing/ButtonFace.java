package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/** The buttons point (spec 5.2): buttons' faces, and the dropdown and picker faces that follow them. */
interface ButtonFace extends Face {
    /** Paints the face behind a button's glyph and label: its fill and border (and the default primary's inset line and drop). */
    void paintFace(Buttons.KitButton button, Graphics2D g, boolean hover, boolean focused);
    /** The button's label colour. */
    Color text(Buttons.KitButton button, boolean hover);
    /** Whether disabled {@code button} is its whole face faded as one layer; otherwise the face draws its own disabled state. */
    boolean fades(Buttons.KitButton button);
    /** The label's type role for a {@code kind} button whose label would take {@code role}. */
    Typography.Role label(Nodes.ButtonKind kind, Typography.Role role);
    /** Paints a dropdown's closed face behind its value. */
    void paintCombo(Choices.ChoiceCombo combo, Graphics2D g, boolean hover, boolean focused);
    /**
     * Paints {@code combo}'s arrow in {@code arrow}, its arrow button, whose trailing edge lies {@code beyond} inside the
     * combo's; a bare cell dropdown on a selected row (plan 3c) takes that row's ink.
     */
    void paintArrow(Choices.ChoiceCombo combo, JComponent arrow, Graphics2D g, int beyond, boolean settings);
    /** Paints a plain picker button's trailing arrow. */
    void paintPickerArrow(Choices.PickerButton picker, Graphics2D g);
    /** Paints a rich picker or dropdown face. */
    void paintPicker(JComponent owner, Graphics graphics, KitContext context, Nodes.PickerFace face, boolean empty, boolean focus,
                     boolean hover, boolean expanded);
    /** Whether picker menu rows draw their icon tiles. */
    boolean tiles();
    /** The rule above a picker menu's footer row. */
    Color menuDivider(KitContext context);
    /**
     * How far each later button of a {@code JOINED_ACTIONS} pair overlaps the one before it: none where the buttons stand
     * side by side inside the pair's outline, one border where the halves share it.
     */
    int joinedOverlap();
}

package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/** The buttons point (spec 5.2): buttons' faces, and the dropdown and picker faces that follow them. */
interface ButtonFace extends Face {
    /** Paints the face behind a button's glyph and label: its fill and border (and the default primary's inset line and drop). */
    void paintFace(Buttons.KitButton button, Graphics2D g, boolean hover, boolean focused);
    /** The button's label colour. */
    Color text(Buttons.KitButton button, boolean hover);
    /** The role of {@code button}'s glyph where neither a selected row, a severity nor a panel decides it. */
    SemanticColor iconRole(Buttons.KitButton button, boolean hover);
    /** Whether {@code button}'s named glyph is recoloured in its role even where its artwork is authored. */
    boolean recolours(Buttons.KitButton button);
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
    /** The closed size of a rich dropdown in a toolbar strip, or null to keep the standard face's. */
    Dimension toolbarCombo(Choices.ChoiceCombo combo);
    /** Whether picker menu rows draw their icon tiles. */
    boolean tiles();
    /** The rule above a picker menu's footer row. */
    Color menuDivider(KitContext context);
    /**
     * How far each later button of a {@code JOINED_ACTIONS} pair overlaps the one before it: none where the buttons stand
     * side by side inside the pair's outline, one border where the halves share it.
     */
    int joinedOverlap();
    /** The width a table cell's dropdown keeps after its value's text padding for its chevron. */
    int cellChevronLane(KitContext context);
    /**
     * Whether the buttons of a line led by a title over a further line (a detail heading without a tile) stand at the line's
     * top, on the title's line; otherwise they are centred on the title block.
     */
    boolean titleLine();
    /** A DOCUMENT bar's text button's side padding, scaled, or -1 to keep the standard padding. */
    int barPadding(KitContext context);
    /**
     * The lane, scaled, a DOCUMENT bar's menu button that only opens its menu keeps after its label for its arrow, or -1
     * for the standard chevron in {@code MENU_ARROW_WIDTH}.
     */
    int barMenuLane(KitContext context);
    /** Paints that arrow in {@code main}'s trailing lane, where {@link #barMenuLane} gives one. */
    void paintBarMenuArrow(Buttons.KitButton main, Graphics2D g);
    /** The colour of a DOCUMENT bar's caption standing after a button (that action's shortcut), or null to keep the caption's. */
    SemanticColor barShortcut();
}

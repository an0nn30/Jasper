package dev.jasper.ui.swing;

import java.awt.Color;
import java.util.List;
import javax.swing.border.Border;

/** The form-label point (spec 5.2): how FormRow labels, captions and columns read. */
interface LabelPolicy extends Face {
    /** A FormRow label's text for the descriptor's {@code label}. */
    String label(String label);
    /** A FormRow label's colour, {@code standard} being the default look's. */
    Color labelInk(KitContext context, Color standard);
    /** A SettingRow title's colour: as a label in the label column when {@code formLabel}, otherwise as a setting's title. */
    Color settingInk(KitContext context, boolean formLabel, Color standard);
    /** Styles a FormRow caption. */
    void styleCaption(Composition.WrappedText caption, KitContext context);
    /** Whether a caption is one line, shortened at its end. */
    boolean singleCaption();
    /** A card's shared label column for its labels' widths, or -1 to keep each row's fixed column. */
    int column(KitContext context, List<Integer> labelWidths);
    /** Whether a trailing PORT field gets its own "Port:" label. */
    boolean portLabel();
    /** The inset an unlabelled EXCLUSIVE choice in a card takes to reach the field column, or null for none. */
    Border choiceInset(KitContext context, int column);
}

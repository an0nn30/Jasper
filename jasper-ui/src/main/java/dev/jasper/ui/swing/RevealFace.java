package dev.jasper.ui.swing;

import javax.swing.JPanel;

/**
 * How a secret's reveal and copy draw (plan 10b Rulings 1), read from the kit's toggle point: the default glyph
 * buttons, or the classic "Show" checkbox and "Show" and "Copy" buttons.
 */
interface RevealFace extends Face {
    /** Places an INPUT or DISCREET_INPUT secret's reveal, once, after the field surface and the reveal exist. */
    void mountInput(SecretFields.SecretControl control);

    /** The row holding a DETAIL secret's reveal and copy, made once. */
    JPanel detailActions(SecretFields.SecretControl control);

    /** The secret learned whether it is in a prompt form. */
    void prompt(SecretFields.SecretControl control, boolean prompt);

    /** Shows or hides the reveal for the secret's kind, focus and content. */
    void visibility(SecretFields.SecretControl control);

    /** The reveal's and copy's texts, glyphs, fonts and borders, after each refresh and each reveal. */
    void style(SecretFields.SecretControl control);
}

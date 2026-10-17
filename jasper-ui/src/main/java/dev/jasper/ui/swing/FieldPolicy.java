package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import java.awt.Color;
import java.awt.Graphics2D;

/** The text-field policy built with the buttons point (spec 6.4): leading glyphs and the field's surface. */
interface FieldPolicy extends Face {
    /** Whether a {@code kind} field shows its leading glyph (a SEARCH or PATH icon, a PORT's colon). */
    boolean glyph(Nodes.FieldKind kind);
    /** Paints a field's surface and outline, read-only or not. */
    void paintField(TextFields.FieldSurface surface, Graphics2D g);
    /** The text colour of a read-only field painted on {@code surface}. */
    Color readOnlyText(TextFields.FieldSurface surface);
    /** The least width of a text field on a Settings page (a setting row's text value); 0 keeps its columns' width. */
    int settingsWidth(KitContext context);
}

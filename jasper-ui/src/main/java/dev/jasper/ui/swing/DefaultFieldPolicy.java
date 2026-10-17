package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/** The default fields: leading glyphs, one surface whether read-only or not. */
final class DefaultFieldPolicy implements FieldPolicy {
    @Override public boolean glyph(Nodes.FieldKind kind) { return true; }
    @Override public void paintField(TextFields.FieldSurface surface, Graphics2D g) { surface.paintDefault(g); }
    @Override public Color readOnlyText(TextFields.FieldSurface surface) { return ThemePainting.color(surface.context, SemanticColor.TEXT_PRIMARY); }
    @Override public List<PaintPair> pairs() { return List.of(); }
}

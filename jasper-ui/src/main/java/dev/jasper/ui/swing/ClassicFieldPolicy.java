package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/** The classic fields (spec 6.4, DESIGN_LANGUAGE §6): no leading glyph; a read-only field grey with muted text. */
final class ClassicFieldPolicy implements FieldPolicy {
    private final PaintPair readOnlyEdge, readOnlyLabel;

    /** A policy whose fields stand on {@code surface}, the kit's dialog surface. */
    ClassicFieldPolicy(SemanticColor surface) {
        readOnlyEdge = new PaintPair(DerivedColor.BORDER_DIVIDER, surface, SurfaceContrast.Guard.STRUCTURAL);
        readOnlyLabel = new PaintPair(SemanticColor.TEXT_SECONDARY, DerivedColor.SURFACE_TABLE_HEADER, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(readOnlyEdge, readOnlyLabel); }

    /** Rulings 10: no SEARCH or PATH glyph, and no PORT colon (its row gives it a "Port:" label). */
    @Override public boolean glyph(Nodes.FieldKind kind) { return false; }

    @Override public void paintField(TextFields.FieldSurface s, Graphics2D g) {
        if (s.transparent || !s.readOnly) { s.paintDefault(g); return; }
        var c = s.context;
        ThemePainting.rounded(g, s, c, ThemePainting.role(c, DerivedColor.SURFACE_TABLE_HEADER),
            s.focus.visible() ? ThemePainting.color(c, SemanticColor.BORDER_FOCUS) : ThemePainting.painted(c, readOnlyEdge),
            s.focus.visible(), s.error, s.radiusRole);
    }

    @Override public Color readOnlyText(KitContext context) { return ThemePainting.painted(context, readOnlyLabel); }
}

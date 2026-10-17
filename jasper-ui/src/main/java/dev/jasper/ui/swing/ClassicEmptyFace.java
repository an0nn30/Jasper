package dev.jasper.ui.swing;

import dev.jasper.ui.theme.*;
import java.util.List;

/**
 * The classic empty state (spec 5.2, DESIGN_LANGUAGE §8): a bold line and one sentence, guarded against the dialog
 * surface, and at most one button; no tile (plan 4c Rulings 10).
 */
final class ClassicEmptyFace implements EmptyFace {
    private final PaintPair title, description;

    /** A face whose text stands on {@code surface}, the kit's dialog surface. */
    ClassicEmptyFace(SemanticColor surface) {
        title = new PaintPair(SemanticColor.TEXT_PRIMARY, surface, SurfaceContrast.Guard.TEXT);
        description = new PaintPair(SemanticColor.TEXT_SECONDARY, surface, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(title, description); }
    @Override public boolean tile() { return false; }
    @Override public int actions(int declared) { return Math.min(1, declared); }

    @Override public void style(EmptyStates.EmptyControl empty) {
        empty.title.setForeground(ThemePainting.painted(empty.context(), title));
        empty.description.setForeground(ThemePainting.painted(empty.context(), description));
    }
}

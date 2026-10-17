package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.util.List;

/**
 * The classic empty state (spec 5.2, DESIGN_LANGUAGE §8): a bold line and one sentence, guarded against the dialog
 * surface, and at most one button: the visible PRIMARY action, or else the first visible one; no tile (plan 4c Rulings 10).
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
    @Override public List<ControlRegistry.Control> actions(List<ControlRegistry.Control> declared) {
        var visible = declared.stream().filter(control -> control.node.state().visible()).toList();
        return visible.stream().filter(control -> control.node instanceof Nodes.Button button && button.kind() == Nodes.ButtonKind.PRIMARY)
            .findFirst().or(() -> visible.stream().findFirst()).map(List::of).orElse(List.of());
    }

    @Override public void style(EmptyStates.EmptyControl empty) {
        empty.title.setForeground(ThemePainting.painted(empty.context(), title));
        empty.description.setForeground(ThemePainting.painted(empty.context(), description));
    }
}

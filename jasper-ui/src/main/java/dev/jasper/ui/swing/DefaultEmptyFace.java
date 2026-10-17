package dev.jasper.ui.swing;

import dev.jasper.ui.theme.PaintPair;
import java.util.List;

/** The default empty state: the tile over the title and description, then every action. */
final class DefaultEmptyFace implements EmptyFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public boolean tile() { return true; }
    @Override public List<ControlRegistry.Control> actions(List<ControlRegistry.Control> declared) { return declared; }
    @Override public void style(EmptyStates.EmptyControl empty) { }
}

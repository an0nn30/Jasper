package dev.jasper.ui.swing;

import dev.jasper.ui.theme.PaintPair;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.util.List;

/** The default prompt: the tile over the prompt title and description, centred. */
final class DefaultPromptFace implements PromptFace {
    @Override public LayoutManager headerLayout(Stacks.StackControl header) { return new Stacks.PromptLayout(header); }
    @Override public void paintTile(Stacks.Tile tile, Graphics2D g) { tile.paintDefault(g); }
    @Override public void children(Stacks.StackControl header, List<ControlRegistry.Control> controls) { }
    @Override public List<PaintPair> pairs() { return List.of(); }
}

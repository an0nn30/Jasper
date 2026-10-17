package dev.jasper.ui.swing;

import dev.jasper.ui.theme.PaintPair;
import java.awt.Graphics2D;
import java.util.List;

/** The default tag: the chip's tint at the tile share, its text as the chip control sets it. */
final class DefaultTagFace implements TagFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public void style(Indicators.ChipControl chip, Indicators.ChipLabel label) { }
    @Override public void paint(Indicators.ChipLabel label, Graphics2D g) { label.paintTile(g); }
}

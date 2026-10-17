package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.PaintPair;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;

/** The default toggle: the switch, trailing its row, its knob travelling while motion is on. */
final class DefaultToggleFace implements ToggleFace {
    @Override public Dimension preferredSize(Switches.SwitchButton button) { return button.switchSize(); }
    @Override public void paint(Switches.SwitchButton button, Graphics2D g) { button.paintSwitch(g); }
    @Override public boolean leads() { return false; }
    @Override public boolean moves() { return true; }
    @Override public int rowHeight(KitContext context, boolean compact) {
        return ThemePainting.metric(context, compact ? DesignMetrics.Role.COMPACT_SWITCH_HEIGHT : DesignMetrics.Role.SWITCH_HEIGHT);
    }
    /** The off switch is painted as authored; the default toggle repairs nothing (spec 7.3). */
    @Override public List<PaintPair> pairs() { return List.of(); }
}

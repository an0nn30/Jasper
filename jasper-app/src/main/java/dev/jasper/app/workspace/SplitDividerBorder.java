package dev.jasper.app.workspace;


import java.awt.Component;
import java.awt.Graphics;
import javax.swing.JSplitPane;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicSplitPaneDivider;

/** A thin separator inside FlatLaf's wider draggable divider. Loaded by theme defaults. */
public final class SplitDividerBorder extends AbstractBorder implements UIResource {
    @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
        if (!(component instanceof BasicSplitPaneDivider divider)) return;
        boolean vertical = divider.getBasicSplitPaneUI().getSplitPane().getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
        dev.jasper.ui.swing.ChromePainter.installed().splitDivider(graphics, x, y, width, height, vertical,
            dev.jasper.app.platform.ThemeZone.theme(component));
    }
}

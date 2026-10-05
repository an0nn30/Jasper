package dev.jasper.app.workspace;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeControlStyles;

import com.formdev.flatlaf.util.UIScale;
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
        int thickness = NativeControlStyles.metric(DesignMetrics.Role.SPLIT_STROKE);
        Graphics g = graphics.create();
        try {
            g.setColor(dev.jasper.app.platform.ThemeZone.theme(component).tokens().get(dev.jasper.ui.theme.Token.DIVIDER));
            if (vertical) g.fillRect(x + (width - thickness) / 2, y, thickness, height);
            else g.fillRect(x, y + (height - thickness) / 2, width, thickness);
        } finally {
            g.dispose();
        }
    }
}

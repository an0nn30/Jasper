package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.Graphics;

/** Today's status bar: the shell and its folder, then contributed items, and the grid size with the configuration, slash-separated. */
final class SlashSegments implements StatusCells {
    @Override public void install(WindowStatusBar bar) {
        bar.add(bar.left); bar.add(bar.leftItems); bar.add(bar.rightItems); bar.add(bar.right); bar.add(bar.contentItems);
    }

    @Override public void layout(WindowStatusBar bar) {
        int width = bar.getWidth(), height = bar.getHeight();
        int lead = bar.runItem() == null ? 0 : bar.runItem().getPreferredSize().width;
        if (bar.runItem() != null) bar.runItem().setBounds(0, 0, Math.min(width, lead), height);
        int edge = Math.min(NativeStyles.installed().metric(DesignMetrics.Role.STATUS_GAP), width / 2);
        int leftInset = Math.min(width, lead + NativeStyles.installed().metric(DesignMetrics.Role.STATUS_LEADING));
        int gap = NativeStyles.installed().metric(DesignMetrics.Role.STATUS_GAP);
        int available = Math.max(0, width - edge * 2);
        int rightWidth = Math.min(available, bar.right.getPreferredSize().width);
        bar.right.setBounds(Math.max(edge, width - edge - rightWidth), 0, rightWidth, height);
        int rightEdge = bar.right.getX();
        int documentWidth = Math.min(bar.contentItems.getPreferredSize().width, Math.max(0, (rightEdge - leftInset) * 2 / 3));
        bar.contentItems.setBounds(Math.max(leftInset, rightEdge - documentWidth), 0, documentWidth, height);
        if (documentWidth > 0) rightEdge -= documentWidth + gap;
        int remaining = Math.max(0, rightEdge - leftInset - edge);
        int rightItemsWidth = bar.rightItems.getComponentCount() == 0 ? 0 : Math.min(bar.rightItems.getPreferredSize().width, remaining / 3);
        int rightItemsSpace = rightItemsWidth == 0 ? 0 : rightItemsWidth + gap;
        // An empty row collapses at the origin: no child may ever extend past the bar, however narrow it is.
        if (rightItemsWidth == 0) bar.rightItems.setBounds(0, 0, 0, 0);
        else bar.rightItems.setBounds(rightEdge - rightItemsSpace, 0, rightItemsWidth, height);
        remaining -= rightItemsSpace;
        int leftItemsWidth = bar.leftItems.getComponentCount() == 0 ? 0 : Math.min(bar.leftItems.getPreferredSize().width, remaining / 4);
        int leftItemsSpace = leftItemsWidth == 0 ? 0 : leftItemsWidth + gap;
        int leftWidth = Math.max(0, Math.min(bar.left.getPreferredSize().width, remaining - leftItemsSpace));
        if (leftItemsWidth == 0) leftWidth = Math.max(0, remaining);
        bar.left.setBounds(leftInset, 0, leftWidth, height);
        if (leftItemsWidth == 0) bar.leftItems.setBounds(0, 0, 0, 0);
        else bar.leftItems.setBounds(Math.min(leftInset + leftWidth + gap, Math.max(0, width - leftItemsWidth)), 0, leftItemsWidth, height);
    }

    @Override public void paint(WindowStatusBar bar, Graphics graphics) {
        if (bar.showingContent()) return;
        // The run item, when there is one, leads the bar: the dot keeps its place after it.
        int lead = bar.runItem() == null ? 0 : bar.runItem().getWidth();
        if (lead == 0) { dev.jasper.ui.swing.ChromePainter.installed().statusDot(bar, graphics, ThemeZone.theme(null), bar.running()); return; }
        Graphics shifted = graphics.create();
        try {
            shifted.translate(lead, 0);
            dev.jasper.ui.swing.ChromePainter.installed().statusDot(bar, shifted, ThemeZone.theme(null), bar.running());
        } finally { shifted.dispose(); }
    }

    @Override public void refresh(WindowStatusBar bar) { }

    @Override public int itemGap() { return NativeStyles.installed().metric(DesignMetrics.Role.STATUS_GAP); }
}

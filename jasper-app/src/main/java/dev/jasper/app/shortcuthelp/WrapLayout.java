package dev.jasper.app.shortcuthelp;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A FlowLayout whose preferred height counts the rows it wraps into, so a narrow container grows instead of cutting
 * off its last components. Before its first layout the width comes from the nearest laid-out ancestor, less the
 * insets in between. EDT only.
 */
final class WrapLayout extends FlowLayout {
    WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

    @Override public Dimension preferredLayoutSize(Container target) { return size(target, true); }

    @Override public Dimension minimumLayoutSize(Container target) { return size(target, false); }

    private Dimension size(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int width = target.getWidth(), between = 0;
            for (Container at = target; width <= 0 && at.getParent() != null; ) {
                at = at.getParent();
                Insets insets = at.getInsets();
                between += insets.left + insets.right;
                width = at.getWidth() - between;
            }
            if (width <= 0) width = Integer.MAX_VALUE;
            Insets insets = target.getInsets();
            int hgap = getHgap(), vgap = getVgap();
            long room = (long) width - insets.left - insets.right - hgap * 2L;
            int rowWidth = 0, rowHeight = 0, widest = 0, height = 0;
            for (Component child : target.getComponents()) {
                if (!child.isVisible()) continue;
                Dimension d = preferred ? child.getPreferredSize() : child.getMinimumSize();
                if (rowWidth > 0 && (long) rowWidth + hgap + d.width > room) {
                    widest = Math.max(widest, rowWidth); height += rowHeight + vgap; rowWidth = 0; rowHeight = 0;
                }
                rowWidth += (rowWidth > 0 ? hgap : 0) + d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            widest = Math.max(widest, rowWidth); height += rowHeight;
            return new Dimension(widest + insets.left + insets.right + hgap * 2, height + insets.top + insets.bottom + vgap * 2);
        }
    }
}

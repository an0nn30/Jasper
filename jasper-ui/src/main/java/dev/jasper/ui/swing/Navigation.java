package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;

import java.awt.*;

import javax.swing.*;

/** Semantic navigation reserves action width and shrinks the path/content lane first. */
final class Navigation {
    private Navigation() {}

    static final class InlineLayout implements Composition.WidthLayout {
        final KitContext context;

        InlineLayout(KitContext context) {
            this.context = context;
        }

        int gap() {
            return ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
        }

        @Override
        public int height(Container parent, int width) {
            int h = 0;
            for (var c : parent.getComponents())
                if (c.isVisible()) h = Math.max(h, c.getPreferredSize().height);
            return h;
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int w = 0, n = 0;
            for (var c : parent.getComponents())
                if (c.isVisible()) {
                    w += c.getPreferredSize().width;
                    n++;
                }
            return new Dimension(w + Math.max(0, n - 1) * gap(), height(parent, w));
        }

        @Override
        public void layoutContainer(Container parent) {
            var children =
                    java.util.Arrays.stream(parent.getComponents())
                            .filter(Component::isVisible)
                            .toList();
            int width = Math.max(0, parent.getWidth() - Math.max(0, children.size() - 1) * gap()),
                    fixed = 0,
                    flexible = 0;
            for (var c : children) {
                if (flexible(c)) flexible += c.getPreferredSize().width;
                else fixed += c.getPreferredSize().width;
            }
            int budget = Math.max(0, width - fixed), x = 0, consumed = 0;
            boolean anchor = !children.isEmpty() && children.getLast() instanceof AbstractButton;
            for (var c : children) {
                int natural = c.getPreferredSize().width;
                int w = natural;
                if (flexible(c) && (flexible > budget || anchor) && flexible > 0) {
                    w = (int)((long)(consumed + natural) * budget / flexible - (long)consumed * budget / flexible);
                    consumed += natural;
                }
                w = Math.max(0, Math.min(w, parent.getWidth() - x));
                int h = Math.min(parent.getHeight(), c.getPreferredSize().height);
                c.setBounds(x, (parent.getHeight() - h) / 2, w, h);
                x += w + gap();
            }
        }

        boolean flexible(Component c) {
            return c instanceof JLabel label
                            && label.getIcon() == null
                            && label.getClientProperty("Jasper.chip") == null
                    || c instanceof JPanel;
        }
    }

    static String leadingEllipsis(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) return text;
        String ellipsis = "\u2026";
        if (metrics.stringWidth(ellipsis) > width) return "";
        int start = text.length();
        while (start > 0
                && metrics.stringWidth(
                                ellipsis + text.substring(text.offsetByCodePoints(start, -1)))
                        <= width) start = text.offsetByCodePoints(start, -1);
        return ellipsis + text.substring(start);
    }
}

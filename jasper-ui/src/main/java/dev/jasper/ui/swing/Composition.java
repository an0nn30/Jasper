package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

/** Shared content-driven layout and literal wrapping; numeric geometry stays in named roles. */
final class Composition {
    private Composition() {}

    static int height(Component c, int width) {
        if (c instanceof Labels.LiteralLabel label && label.wraps()) return label.wrappedHeight(width);
        if (c instanceof WrappedText text) return text.height(width);
        if (c instanceof JPanel panel && panel.getLayout() instanceof WidthLayout layout)
            return layout.height(panel, width);
        return c.getPreferredSize().height;
    }

    interface WidthLayout extends LayoutManager {
        int height(Container parent, int width);

        default void addLayoutComponent(String name, Component child) {}

        default void removeLayoutComponent(Component child) {}

        default Dimension minimumLayoutSize(Container parent) {
            return new Dimension(0, height(parent, Math.max(1, parent.getWidth())));
        }
    }

    static class Vertical implements WidthLayout {
        final int gap;

        Vertical(int gap) {
            this.gap = gap;
        }

        @Override
        public int height(Container parent, int width) {
            Insets i = parent.getInsets();
            int h = i.top + i.bottom, count = 0;
            for (Component c : parent.getComponents())
                if (c.isVisible()) {
                    h += Composition.height(c, Math.max(0, width - i.left - i.right));
                    count++;
                }
            return h + Math.max(0, count - 1) * gap;
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int w = 0;
            for (Component c : parent.getComponents())
                if (c.isVisible()) w = Math.max(w, c.getPreferredSize().width);
            Insets i = parent.getInsets();
            w += i.left + i.right;
            return new Dimension(w, height(parent, parent.getWidth() > 0 ? parent.getWidth() : w));
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets i = parent.getInsets();
            int y = i.top, w = Math.max(0, parent.getWidth() - i.left - i.right);
            for (Component c : parent.getComponents())
                if (c.isVisible()) {
                    int h = Composition.height(c, w);
                    c.setBounds(i.left, y, w, h);
                    y += h + gap;
                }
        }
    }

    static WrappedText text(String id) {
        var text = new WrappedText();
        KitOwnership.mark(text, id);
        return text;
    }

    static void style(
            JComponent component, KitContext context, Typography.Role role, SemanticColor color) {
        component.setFont(ThemePainting.font(context, role));
        component.setForeground(ThemePainting.color(context, color));
    }

    static void label(ControlRegistry.Control child, boolean embedded) {
        child.embeddedLabel(embedded);
    }

    static final class WrappedText extends JComponent {
        String text = "";
        boolean centered;

        WrappedText() {
            setOpaque(false);
        }

        void text(String value) {
            text = value;
            getAccessibleContext().setAccessibleName(value);
            revalidate();
            repaint();
        }

        @Override
        public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {};
            return accessibleContext;
        }

        List<String> lines(int width) {
            var lines = new ArrayList<String>();
            if (text.isEmpty()) return lines;
            var metrics = getFontMetrics(getFont());
            String line = "";
            for (String paragraph : text.split("\n", -1)) {
                for (String word : paragraph.split(" ", -1)) {
                    String next = line.isEmpty() ? word : line + " " + word;
                    if (!line.isEmpty() && metrics.stringWidth(next) > width) {
                        lines.add(line);
                        line = word;
                    } else line = next;
                    while (metrics.stringWidth(line) > width && line.length() > 1 && width > 0) {
                        int end = 1;
                        while (end < line.length()
                                && metrics.stringWidth(line.substring(0, end + 1)) <= width) end++;
                        lines.add(line.substring(0, end));
                        line = line.substring(end);
                    }
                }
                lines.add(line);
                line = "";
            }
            return lines;
        }

        int height(int width) {
            return lines(Math.max(1, width)).size() * getFontMetrics(getFont()).getHeight();
        }

        @Override
        public Dimension getPreferredSize() {
            var fm = getFontMetrics(getFont());
            int width = Math.min(300, fm.stringWidth(text));
            return new Dimension(width, height(getWidth() > 0 ? getWidth() : Math.max(1, width)));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                g.setFont(getFont());
                g.setColor(getForeground());
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                var fm = g.getFontMetrics();
                int y = fm.getAscent();
                for (String line : lines(getWidth())) {
                    g.drawString(
                            line,
                            centered ? Math.max(0, (getWidth() - fm.stringWidth(line)) / 2) : 0,
                            y);
                    y += fm.getHeight();
                }
            } finally {
                g.dispose();
            }
        }
    }
}

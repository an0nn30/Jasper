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
        if (c instanceof Buttons.KitButton button && button.recorder()) return button.heightAt(width);
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

        int gap(Component previous, Component next) { return gap; }

        @Override
        public int height(Container parent, int width) {
            Insets i = parent.getInsets();
            int h = i.top + i.bottom;
            Component previous = null;
            for (Component c : parent.getComponents())
                if (c.isVisible()) {
                    h += Composition.height(c, Math.max(0, width - i.left - i.right));
                    if (previous != null) h += gap(previous, c);
                    previous = c;
                }
            return h;
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
            Component previous = null;
            for (Component c : parent.getComponents())
                if (c.isVisible()) {
                    if (previous != null) y += gap(previous,c);
                    int h = Composition.height(c, w);
                    c.setBounds(i.left, y, w, h);
                    y += h;
                    previous = c;
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
        /** One line, shortened at its end with an ellipsis when it does not fit (a form row's caption). */
        boolean single;
        int lineHeight;
        /** Distribute a taller line box's extra leading evenly above and below each line. */
        boolean centeredLeading;
        int noteStart = -1;
        Color noteColor;

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

        /**
         * A line's advance in the fractional antialiased context {@link #paintComponent} draws with. The component's own
         * metrics can differ (Linux hints integer advances wider) and would break lines that fit as painted.
         */
        int advance(String line) {
            return NativeChromePainting.measureText(getFont(), line);
        }

        List<String> lines(int width) {
            var lines = new ArrayList<String>();
            if (text.isEmpty()) return lines;
            if (single) {
                String line = text.replace('\n', ' ');
                if (advance(line) > width) {
                    String ellipsis = "\u2026";
                    int end = line.length();
                    while (end > 0 && advance(line.substring(0, end) + ellipsis) > width) end = line.offsetByCodePoints(end, -1);
                    line = end == 0 ? "" : line.substring(0, end).stripTrailing() + ellipsis;
                }
                lines.add(line);
                return lines;
            }
            String line = "";
            for (String paragraph : text.split("\n", -1)) {
                for (String word : paragraph.split(" ", -1)) {
                    String next = line.isEmpty() ? word : line + " " + word;
                    if (!line.isEmpty() && advance(next) > width) {
                        lines.add(line);
                        line = word;
                    } else line = next;
                    while (advance(line) > width && line.length() > 1 && width > 0) {
                        int end = 1;
                        while (end < line.length()
                                && advance(line.substring(0, end + 1)) <= width) end++;
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
            return lines(Math.max(1, width)).size() * Math.max(lineHeight, getFontMetrics(getFont()).getHeight());
        }

        @Override
        public Dimension getPreferredSize() {
            int width = Math.min(300, advance(text));
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
                float y = fm.getAscent() + (centeredLeading ? Math.max(0, lineHeight - fm.getHeight()) / 2f : 0);
                int consumed = 0;
                for (String line : lines(getWidth())) {
                    int x = centered ? Math.max(0, (getWidth() - fm.stringWidth(line)) / 2) : 0;
                    int start = Math.max(0, text.indexOf(line, consumed));
                    int split = noteStart < 0 ? line.length() : Math.max(0, Math.min(line.length(), noteStart - start));
                    String leading = line.substring(0, split);
                    g.setColor(getForeground()); g.drawString(leading, x, y);
                    if (split < line.length()) { g.setColor(noteColor); g.drawString(line.substring(split), (float)(x + fm.stringWidth(leading)), y); }
                    consumed = start + line.length();
                    y += Math.max(lineHeight, fm.getHeight());
                }
            } finally {
                g.dispose();
            }
        }
    }
}

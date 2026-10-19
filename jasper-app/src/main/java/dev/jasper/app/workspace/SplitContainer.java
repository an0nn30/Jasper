package dev.jasper.app.workspace;

import dev.jasper.app.layout.Orientation;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * One split of a workspace layout (workspace tabs spec 10): its children side by side (HORIZONTAL) or stacked
 * (VERTICAL), sized by weight, with a draggable divider between each two. A drag moves the boundary between the
 * divider's two neighbours, each keeping at least {@link #MINIMUM_SHARE} of the split, lays them out as the pointer moves
 * and, on release, reports the split's path and its new weights. Positions follow the weights alone, so laying the window
 * out again (a theme change) never moves a divider. Below opacity 1 the split paints nothing behind its children; its
 * dividers stay solid. EDT only.
 */
final class SplitContainer extends JPanel {
    /** The least share of the split a drag leaves either neighbour of a divider: two children get 0.1 to 0.9. */
    static final double MINIMUM_SHARE = 0.1;
    private final Orientation orientation;
    private final List<Integer> path;
    private final List<JComponent> children;
    private final List<Divider> dividers = new ArrayList<>();
    private final BiConsumer<List<Integer>, List<Double>> resized;
    private double[] weights;

    /**
     * @param orientation HORIZONTAL lays the children out left to right
     * @param path the split's place in the layout, as {@code Layout.resize} takes it
     * @param weights one per child
     * @param children two or more
     * @param resized told the path and the weights when a divider drag ends having moved something
     */
    SplitContainer(Orientation orientation, List<Integer> path, List<Double> weights, List<? extends JComponent> children,
                   BiConsumer<List<Integer>, List<Double>> resized) {
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        this.path = List.copyOf(path);
        this.children = List.copyOf(children);
        this.resized = Objects.requireNonNull(resized, "resized");
        if (this.children.size() < 2 || weights.size() != this.children.size())
            throw new IllegalArgumentException("A split needs two or more children and a weight for each");
        this.weights = weights.stream().mapToDouble(Double::doubleValue).toArray();
        setLayout(new Arrangement());
        setName("split");
        for (int i = 0; i < this.children.size(); i++) {
            if (i > 0) {
                var divider = new Divider(i - 1);
                dividers.add(divider);
                add(divider);
            }
            add(this.children.get(i));
        }
    }

    Orientation orientation() { return orientation; }
    List<Integer> path() { return path; }
    List<Double> weights() { return Arrays.stream(weights).boxed().toList(); }
    List<JComponent> children() { return children; }
    /** The divider after child {@code index}. */
    JComponent divider(int index) { return dividers.get(index); }

    /** Takes the weights the layout holds now and lays the children out again; reports nothing. */
    void setWeights(List<Double> next) {
        if (next.size() != weights.length) throw new IllegalArgumentException("A weight for each child");
        double[] values = next.stream().mapToDouble(Double::doubleValue).toArray();
        if (Arrays.equals(values, weights)) return;
        weights = values;
        revalidate(); repaint();
    }

    /** Below opacity 1 the split leaves its children's translucent fill alone. */
    void setGlass(boolean glass) {
        setOpaque(!glass);
        repaint();
    }

    /** A divider's thickness: the native split divider's, scaled. */
    static int dividerSize() { return NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_SPLIT_DIVIDER); }

    private boolean horizontal() { return orientation == Orientation.HORIZONTAL; }
    private int lengthOf(Dimension size) { return horizontal() ? size.width : size.height; }
    private int breadthOf(Dimension size) { return horizontal() ? size.height : size.width; }
    /** The length the children share: the split's, less its dividers. */
    private int available() { return Math.max(0, (horizontal() ? getWidth() : getHeight()) - dividers.size() * dividerSize()); }

    /** Lays the children out by weight along the split, each divider after its child; the last child takes the rounding. */
    private final class Arrangement implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return measure(false); }
        @Override public Dimension minimumLayoutSize(Container parent) { return measure(true); }

        @Override public void layoutContainer(Container parent) {
            int available = available(), size = dividerSize(), breadth = horizontal() ? getHeight() : getWidth();
            double total = Arrays.stream(weights).sum(), sum = 0;
            int start = 0;
            for (int i = 0; i < children.size(); i++) {
                sum += weights[i];
                int end = i == children.size() - 1 ? available : (int) Math.round(available * sum / total);
                place(children.get(i), start + i * size, end - start, breadth);
                if (i < dividers.size()) place(dividers.get(i), end + i * size, size, breadth);
                start = end;
            }
        }

        private void place(Component component, int along, int length, int breadth) {
            if (horizontal()) component.setBounds(along, 0, Math.max(0, length), breadth);
            else component.setBounds(0, along, breadth, Math.max(0, length));
        }

        private Dimension measure(boolean minimum) {
            int length = dividers.size() * dividerSize(), breadth = 0;
            for (JComponent child : children) {
                Dimension size = minimum ? child.getMinimumSize() : child.getPreferredSize();
                length += lengthOf(size);
                breadth = Math.max(breadth, breadthOf(size));
            }
            return horizontal() ? new Dimension(length, breadth) : new Dimension(breadth, length);
        }
    }

    /** The rule after child {@code index}: dragging it trades length between that child and the next. */
    private final class Divider extends JComponent {
        private final int index;
        private Point origin;
        private double[] start;

        Divider(int index) {
            this.index = index;
            setName("splitDivider");
            setOpaque(true);
            setCursor(Cursor.getPredefinedCursor(horizontal() ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
            var drag = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event)) return;
                    origin = SwingUtilities.convertPoint(Divider.this, event.getPoint(), SplitContainer.this);
                    start = weights.clone();
                }
                @Override public void mouseDragged(MouseEvent event) { if (origin != null) follow(event); }
                @Override public void mouseReleased(MouseEvent event) {
                    if (origin == null) return;
                    follow(event);
                    boolean moved = !Arrays.equals(start, weights);
                    origin = null;
                    start = null;
                    if (moved) resized.accept(path, weights());
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }

        private void follow(MouseEvent event) {
            Point at = SwingUtilities.convertPoint(this, event.getPoint(), SplitContainer.this);
            int available = available();
            double total = Arrays.stream(start).sum(), pair = start[index] + start[index + 1], least = MINIMUM_SHARE * total;
            if (available <= 0 || pair < 2 * least) return;
            double moved = (horizontal() ? at.x - origin.x : at.y - origin.y) * total / available;
            double first = Math.clamp(start[index] + moved, least, pair - least);
            double[] next = start.clone();
            next[index] = first;
            next[index + 1] = pair - first;
            weights = next;
            SplitContainer.this.doLayout();
            SplitContainer.this.revalidate();
            SplitContainer.this.repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            graphics.setColor(SplitContainer.this.getBackground());
            graphics.fillRect(0, 0, getWidth(), getHeight());
            ChromePainter.installed().splitDivider(graphics, 0, 0, getWidth(), getHeight(), horizontal(), ThemeZone.theme(this));
        }
    }
}

package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Header/navigation and footer remain mounted while a flexible body changes or scrolls. */
final class Panels {
    private Panels() {}

    static final class PanelControl extends ControlRegistry.Control {
        final JScrollPane scroll = new JScrollPane();
        final Body body = new Body();
        final JPanel content = new JPanel(new BorderLayout());
        ControlRegistry.Control queryControl, footerControl;
        final JPanel navigation = new JPanel();
        final PanelFooters.Footer footer;
        PanelHeaders.HeaderControl header;

        PanelControl(KitView view, Nodes.Panel node) {
            super(view, node, new Surface(view.context()));
            footer = new PanelFooters.Footer(context(), node.id() + ".footer-surface");
            KitOwnership.mark(scroll, node.id() + ".body-scroll");
            scroll.setViewportView(body);
            scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            content.setOpaque(false);
            body.navigation = navigation;
            body.content = content;
            body.add(navigation);
            body.add(content);
            navigation.setOpaque(false);
            navigation.setLayout(new Composition.Vertical(0));
            body.setOpaque(false);
            component.setLayout(new Layout(this));
            refresh();
        }

        @Override
        void refresh() {
            state();
            footer.refresh();
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            navigation.setBorder(
                    new EmptyBorder(
                            0,
                            pad,
                            ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP),
                            pad));
            component.revalidate();
            component.repaint();
        }

        @Override
        Container childParent(Nodes.Node child) {
            var p = (Nodes.Panel) node;
            if (child == p.header()) return component;
            if (p.searchOrNavigation().filter(n -> n == child).isPresent()) return navigation;
            if (p.footer().filter(n -> n == child).isPresent()) return footer;
            return content;
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            var p = (Nodes.Panel) node;
            header = (PanelHeaders.HeaderControl) controls.getFirst();
            queryControl = null;
            footerControl = null;
            if (p.searchOrNavigation().isEmpty()) Stacks.mount(navigation, List.of());
            if (p.footer().isEmpty()) Stacks.mount(footer, List.of());
            for (var c : controls) {
                if (childParent(c.node) == navigation) {
                    queryControl = c;
                    Composition.label(c, true);
                    Stacks.mount(navigation, List.of(c.component));
                } else if (childParent(c.node) == footer) {
                    footerControl = c;
                    footer.mount(c.component);
                } else if (childParent(c.node) == content) {
                    body.control = c;
                    Stacks.mount(content, List.of(c.component));
                }
            }
            presentation();
            Stacks.mount(component, List.of(header.component, scroll, footer));
        }

        void presentation() {
            var p = (Nodes.Panel) node;
            if (queryControl != null && p.searchOrNavigation().isPresent())
                queryControl.component.setVisible(
                        queryControl.node.state().visible()
                                && !(p.header().count().isPresent()
                                        && p.header().count().getAsInt() == 0));
            navigation.setVisible(
                    p.searchOrNavigation().isPresent()
                            && queryControl != null
                            && queryControl.component.isVisible());
            footer.setVisible(p.footer().isPresent());
            if (footerControl instanceof SettingRows.RowControl row) {
                row.panelFooter = true;
                row.refresh();
            }
        }
    }

    static final class Body extends JPanel implements Scrollable {
        JPanel navigation, content;
        ControlRegistry.Control control;

        Body() {
            super(null);
        }

        int width() {
            return Math.max(
                    1,
                    getParent() instanceof JViewport viewport
                            ? viewport.getExtentSize().width
                            : getWidth());
        }

        int navigationHeight(int width) {
            return navigation != null && navigation.isVisible()
                    ? Composition.height(navigation, width)
                    : 0;
        }

        int contentHeight(int width) {
            if (control == null || !control.component.isVisible()) return 0;
            if (control instanceof InteractiveRows.RowsControl rows) {
                Insets insets = rows.scroll.getInsets();
                int height = rows.rowHeight + insets.top + insets.bottom;
                var header = rows.scroll.getColumnHeader();
                if (header != null && header.isVisible())
                    height += header.getPreferredSize().height;
                var border = rows.scroll.getViewportBorder();
                if (border != null) {
                    Insets viewport = border.getBorderInsets(rows.scroll);
                    height += viewport.top + viewport.bottom;
                }
                return height;
            }
            return Composition.height(control.component, width);
        }

        @Override
        public Dimension getPreferredSize() {
            int w = width();
            return new Dimension(
                    control == null ? 0 : control.component.getPreferredSize().width,
                    navigationHeight(w) + contentHeight(w));
        }

        @Override
        public void doLayout() {
            int nh = navigationHeight(getWidth());
            navigation.setBounds(0, 0, getWidth(), nh);
            content.setBounds(
                    0, nh, getWidth(), Math.max(contentHeight(getWidth()), getHeight() - nh));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return Math.max(1, getFontMetrics(getFont()).getHeight());
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return Math.max(1, r.height - getScrollableUnitIncrement(r, orientation, direction));
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getPreferredSize().height <= getParent().getHeight();
        }
    }

    static final class Layout implements Composition.WidthLayout {
        final PanelControl owner;

        Layout(PanelControl owner) {
            this.owner = owner;
        }

        @Override
        public int height(Container p, int width) {
            return owner.header == null
                    ? 0
                    : (owner.header.component.isVisible() ? Composition.height(owner.header.component, width) : 0)
                            + owner.body.navigationHeight(width)
                            + owner.body.contentHeight(width)
                            + (owner.footer.isVisible() ? owner.footer.height(width) : 0);
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            int w = 0;
            for (var c : p.getComponents())
                if (c.isVisible()) w = Math.max(w, c.getPreferredSize().width);
            return new Dimension(w, height(p, w));
        }

        @Override
        public void layoutContainer(Container p) {
            if (owner.header == null) return;
            int w = p.getWidth(), h = p.getHeight();
            int fh = owner.footer.isVisible() ? Math.min(h, owner.footer.height(w)) : 0;
            int hh = Math.min(Math.max(0, h - fh), (owner.header.component.isVisible() ? Composition.height(owner.header.component, w) : 0));
            owner.header.component.setBounds(0, 0, w, hh);
            owner.scroll.setBounds(0, hh, w, Math.max(0, h - fh - hh));
            owner.footer.setBounds(0, h - fh, w, fh);
        }
    }

    static final class Surface extends JPanel {
        final KitContext context;

        Surface(KitContext context) {
            this.context = context;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_PANEL));
                int r = ThemePainting.metric(context, DesignMetrics.Role.CARD_RADIUS);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), r * 2, r * 2);
            } finally {
                g.dispose();
            }
        }
    }
}

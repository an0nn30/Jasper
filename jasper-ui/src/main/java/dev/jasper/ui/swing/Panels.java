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
        final JScrollPane scroll = new JScrollPane(), footerScroll = new JScrollPane();
        final Body body = new Body();
        final JPanel content = new JPanel(new BorderLayout());
        ControlRegistry.Control queryControl, footerControl;
        final JPanel navigation = new JPanel();
        final PanelFooters.Footer footer;
        PanelHeaders.HeaderControl header;
        int column = -1;

        PanelControl(KitView view, Nodes.Panel node) {
            super(view, node, new Surface(view.context()));
            footer = new PanelFooters.Footer(context(), node.id() + ".footer-surface");
            footerScroll.setViewportView(footer);
            footerScroll.setBorder(new EmptyBorder(0,0,0,0));
            footerScroll.setOpaque(false);footerScroll.getViewport().setOpaque(false);
            footerScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            KitOwnership.mark(footerScroll,node.id()+".footer-scroll");
            var focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
            java.beans.PropertyChangeListener focusChanged=event -> {
                if(!closed && !view.isClosed() && event.getNewValue() instanceof JComponent focused && SwingUtilities.isDescendingFrom(focused,footer))
                    focused.scrollRectToVisible(new Rectangle(0,0,focused.getWidth(),focused.getHeight()));
            };
            focusManager.addPropertyChangeListener("focusOwner",focusChanged);
            own(() -> focusManager.removePropertyChangeListener("focusOwner",focusChanged));
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
            ((Surface)component).column = column;
            ((Surface)component).painted = view.panelSurface();
            var panel = (Nodes.Panel)node;
            boolean single = panel.body() instanceof Nodes.ItemList list && list.kind()==Nodes.ListKind.SINGLE;
            // Two-line panel rows (the body list, or the first child of a vertical body stack) share the panel row inset;
            // without a search or navigation row above them they start a compact gap below the header.
            Nodes.Node first = panel.body() instanceof Nodes.Stack stack && stack.role()==Nodes.LayoutRole.VERTICAL && !stack.children().isEmpty() ? stack.children().getFirst() : panel.body();
            boolean compact = column < 0 && first instanceof Nodes.ItemList list && list.kind()==Nodes.ListKind.DOUBLE;
            int side = column == 0 || column == 1 ? context().metrics().value(DesignMetrics.Role.COLUMN_LIST_SIDE) : single || compact ? context().metrics().value(DesignMetrics.Role.PANEL_ROW_SIDE) : 0;
            // A rail, and a list column without its search row, start their rows at the rail's top inset.
            int top = column == 0 || column == 1 && panel.searchOrNavigation().isEmpty() ? ThemePainting.metric(context(), DesignMetrics.Role.COLUMN_RAIL_TOP)
                : compact && panel.searchOrNavigation().isEmpty() ? ThemePainting.metric(context(), DesignMetrics.Role.PANEL_COMPACT_LIST_TOP) : 0;
            contentSide = side; contentTop = top;
            contentInsets();
            boolean search=((Nodes.Panel)node).searchOrNavigation().filter(n -> n instanceof Nodes.Field f && f.kind()==Nodes.FieldKind.SEARCH).isPresent();
            int pad = ThemePainting.metric(context(), search && column<0 ? DesignMetrics.Role.PANEL_SEARCH_SIDE : DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            navigation.setBorder(
                    new EmptyBorder(
                            column == 1 ? ThemePainting.metric(context(), DesignMetrics.Role.COLUMN_SEARCH_TOP) : 0,
                            column == 1 ? ThemePainting.metric(context(), DesignMetrics.Role.COLUMN_SEARCH_SIDE) : pad,
                            ThemePainting.metric(context(), column == 1 ? DesignMetrics.Role.COLUMN_SEARCH_BOTTOM : search ? DesignMetrics.Role.PANEL_SEARCH_BOTTOM : DesignMetrics.Role.ICON_GAP),
                            column == 1 ? ThemePainting.metric(context(), DesignMetrics.Role.COLUMN_SEARCH_SIDE) : pad));
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
                    footer.detail = c.node instanceof Nodes.Stack stack && stack.role() == Nodes.LayoutRole.DETAIL;
                    footer.refresh();
                    footer.mount(c.component);
                } else if (childParent(c.node) == content) {
                    body.control = c;
                    Stacks.mount(content, List.of(c.component));
                }
            }
            contentInsets();
            presentation();
            Stacks.mount(component, List.of(header.component, scroll, footerScroll));
        }

        /** The body's logical side inset and its top inset in pixels, from the last refresh. */
        int contentSide, contentTop;
        /**
         * A list body runs its selection edge to edge (spec 2): the panel's side inset becomes its rows' gutter. Any other
         * body keeps the inset on the content.
         */
        void contentInsets() {
            var p = (Nodes.Panel) node;
            boolean rows = p.body() instanceof Nodes.ItemList || p.body() instanceof Nodes.Table || p.body() instanceof Nodes.Tree;
            int side = contentSide;
            if (rows && body.control instanceof InteractiveRows.RowsControl list) { list.gutter(side); side = 0; }
            int scaled = com.formdev.flatlaf.util.UIScale.scale(side);
            content.setBorder(new EmptyBorder(contentTop, scaled, 0, scaled));
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
            footerScroll.setVisible(p.footer().isPresent());
            // The reconciler marks the footer's persistent rows and footer stack before each update (applyPanelBody).
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
            int footerMinimum=owner.footer.isVisible() ? owner.footer.interactiveHeight(owner.footer) : 0;
            int hh = Math.min(Math.max(0,h-footerMinimum), owner.header.component.isVisible() ? Composition.height(owner.header.component,w) : 0);
            int fh = owner.footer.isVisible() ? Math.min(Math.max(0,h-hh),owner.footer.height(w)) : 0;
            owner.header.component.setBounds(0, 0, w, hh);
            owner.scroll.setBounds(0, hh, w, Math.max(0, h - fh - hh));
            owner.footerScroll.setBounds(0, h - fh, w, fh);
        }
    }

    static final class Surface extends JPanel {
        final KitContext context;
        int column = -1;
        /** False when the view's host paints the dock surface behind a body panel (KitView.panelSurface). */
        boolean painted = true;

        Surface(KitContext context) {
            this.context = context;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                g.setColor(ThemePainting.color(context, column == 0 ? SemanticColor.SURFACE_SIDEBAR : SemanticColor.SURFACE_PANEL));
                if (column >= 0) {
                    g.fillRect(0,0,getWidth(),getHeight());
                    if (column < 2) {
                        g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
                        int stroke=ThemePainting.metric(context,DesignMetrics.Role.HAIRLINE);
                        g.fillRect(getWidth()-stroke,0,stroke,getHeight());
                    }
                    return;
                }
                if (!painted) return;
                int r = ThemePainting.metric(context, DesignMetrics.Role.CARD_RADIUS);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), r * 2, r * 2);
            } finally {
                g.dispose();
            }
        }
    }
}

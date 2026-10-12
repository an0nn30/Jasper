package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;

import java.awt.*;
import java.util.List;

import javax.swing.*;

/**
 * Full-body centered empty state retaining real interactive action controls. Inside a panel body (the reconciler sets
 * {@link EmptyControl#panel}) the state takes the panel geometry measured from the plugin-panel references: a borderless
 * compact tile, a narrower wrapped description in its own role and line height, and a slight optical lift.
 */
final class EmptyStates {
    private EmptyStates() {}

    static final class EmptyControl extends ControlRegistry.Control {
        final JPanel content = new JPanel(), actions = new JPanel();
        final EmptyTile tile;
        final Composition.WrappedText title, description;
        /** A panel body owns this state; set by the reconciler before every update. */
        boolean panel;

        EmptyControl(KitView view, Nodes.Empty node) {
            super(view, node, new JPanel(new GridBagLayout()));
            tile = new EmptyTile(this);
            KitOwnership.mark(tile, node.id() + ".tile");
            title = Composition.text(node.id() + ".title");
            description = Composition.text(node.id() + ".description");
            title.centered = true;
            description.centered = true;
            component.setOpaque(false);
            content.setOpaque(false);
            tile.setOpaque(false);
            actions.setOpaque(false);
            component.add(content);
            refresh();
        }

        int metric(DesignMetrics.Role role) { return ThemePainting.metric(context(), role); }

        int tileSize() { return metric(panel ? DesignMetrics.Role.PANEL_EMPTY_TILE : DesignMetrics.Role.EMPTY_TILE); }

        int textWidth() { return metric(panel ? DesignMetrics.Role.PANEL_EMPTY_TEXT_WIDTH : DesignMetrics.Role.EMPTY_TEXT_WIDTH); }

        /** The gap after the tile, the title and the description, in that order. */
        int gap(int index) {
            if (!panel) return metric(DesignMetrics.Role.ICON_GAP);
            return metric(switch (index) {
                case 0 -> DesignMetrics.Role.PANEL_EMPTY_TITLE_GAP;
                case 1 -> DesignMetrics.Role.PANEL_EMPTY_TEXT_GAP;
                default -> DesignMetrics.Role.PANEL_EMPTY_ACTION_GAP;
            });
        }

        @Override
        void refresh() {
            state();
            var empty = (Nodes.Empty) node;
            title.text(empty.title());
            description.text(empty.description());
            Composition.style(
                    title, context(), Typography.Role.PANEL_TITLE, SemanticColor.TEXT_PRIMARY);
            Composition.style(
                    description,
                    context(),
                    panel ? Typography.Role.PANEL_EMPTY_DESCRIPTION : Typography.Role.DESCRIPTION,
                    SemanticColor.TEXT_SECONDARY);
            description.lineHeight = panel ? Math.round(context().metrics().value(DesignMetrics.Role.PANEL_EMPTY_LINE_HEIGHT)
                    * ThemePainting.font(context(), Typography.Role.BODY).getSize2D() / 13f) : 0;
            content.setLayout(new ContentLayout(this));
            actions.setLayout(new Stacks.StackLayout(context(), Nodes.LayoutRole.ACTIONS));
            int size = tileSize();
            tile.setPreferredSize(new Dimension(size, size));
            content.setPreferredSize(null);
            component.setLayout(new Layout(this));
        }

        @Override
        Container childParent(Nodes.Node child) {
            return child == ((Nodes.Empty) node).icon() ? tile : actions;
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            if (controls.getFirst() instanceof Labels.IconControl icon) icon.glyph(panel ? context().metrics().value(DesignMetrics.Role.PANEL_EMPTY_GLYPH) : 0);
            // Panel empty-state buttons take the references' smaller label and height; a menu button keeps the control size.
            for (var action : controls.subList(1, controls.size())) {
                if (action instanceof Buttons.ButtonControl button && button.button.panelEmpty != panel) { button.button.panelEmpty = panel; button.refresh(); }
            }
            Stacks.mount(tile, List.of(controls.getFirst().component));
            Stacks.mount(
                    actions,
                    controls.subList(1, controls.size()).stream().map(c -> c.component).toList());
            Stacks.mount(content, List.of(tile, title, description, actions));
        }

        /** The stacked height of tile, title, description and actions at {@code width}. */
        int contentHeight(int width) {
            return tile.getPreferredSize().height + gap(0) + title.height(width) + gap(1)
                + description.height(width) + gap(2) + Composition.height(actions, width);
        }
    }

    /**
     * The state's tile: the outlined control-tone tile of window empty states, or in a panel the borderless compact
     * tile of the panel references.
     */
    static final class EmptyTile extends JPanel {
        final EmptyControl owner;

        EmptyTile(EmptyControl owner) {
            super(new GridBagLayout());
            this.owner = owner;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            int size = owner.tileSize();
            return new Dimension(size, size);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                var context = owner.context();
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CONTROL));
                int radius = ThemePainting.metric(context, owner.panel ? DesignMetrics.Role.PANEL_EMPTY_TILE_RADIUS : DesignMetrics.Role.HERO_TILE_RADIUS);
                if (owner.panel) {
                    g.fill(new java.awt.geom.RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), radius * 2, radius * 2));
                    return;
                }
                g.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_CONTROL));
                g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, radius * 2, radius * 2);
            } finally {
                g.dispose();
            }
        }
    }

    static final class ContentLayout implements Composition.WidthLayout {
        final EmptyControl owner;
        ContentLayout(EmptyControl owner) { this.owner=owner; }
        @Override public void addLayoutComponent(String n, Component c) {}
        @Override public void removeLayoutComponent(Component c) {}
        @Override public int height(Container parent, int width) { return owner.contentHeight(width); }
        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = parent.getWidth() > 0 ? parent.getWidth() : owner.textWidth();
            return new Dimension(width, height(parent, width));
        }
        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
        @Override public void layoutContainer(Container parent) {
            int width = parent.getWidth(), y = 0;
            int size = owner.tile.getPreferredSize().width;
            owner.tile.setBounds(Math.max(0, (width - size) / 2), y, size, size);
            y += size + owner.gap(0);
            int th = owner.title.height(width);
            owner.title.setBounds(0, y, width, th);
            y += th + owner.gap(1);
            int dh = owner.description.height(width);
            owner.description.setBounds(0, y, width, dh);
            y += dh + owner.gap(2);
            int aw = Math.min(width, owner.actions.getPreferredSize().width), ah = Composition.height(owner.actions, aw);
            owner.actions.setBounds(Math.max(0, (width - aw) / 2), y, aw, ah);
        }
    }

    static final class Layout implements Composition.WidthLayout {
        final EmptyControl owner;

        Layout(EmptyControl owner) {
            this.owner = owner;
        }

        @Override
        public void addLayoutComponent(String n, Component c) {}

        @Override
        public void removeLayoutComponent(Component c) {}

        int width(int available) { return Math.max(1, Math.min(available, owner.textWidth())); }

        @Override
        public int height(Container p, int width) {
            return owner.contentHeight(width(width));
        }
        @Override
        public Dimension preferredLayoutSize(Container p) {
            int width = p.getWidth() > 0 ? p.getWidth() : owner.content.getPreferredSize().width;
            return new Dimension(width, height(p, width));
        }

        @Override
        public Dimension minimumLayoutSize(Container p) {
            return preferredLayoutSize(p);
        }

        @Override
        public void layoutContainer(Container p) {
            int width = width(p.getWidth()), height = owner.contentHeight(width);
            // A panel state sits a little above the exact middle of the body, as the panel references do.
            int lift = owner.panel ? owner.metric(DesignMetrics.Role.PANEL_EMPTY_LIFT) : 0;
            int x = Math.max(0, (p.getWidth() - width) / 2),
                    y = Math.max(0, (p.getHeight() - height) / 2 - lift);
            owner.content.setBounds(x, y, width, height);
            owner.content.doLayout();
        }
    }
}

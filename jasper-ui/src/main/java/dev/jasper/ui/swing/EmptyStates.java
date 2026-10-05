package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;

import java.awt.*;
import java.util.List;

import javax.swing.*;

/** Full-body centered empty state retaining real interactive action controls. */
final class EmptyStates {
    private EmptyStates() {}

    static final class EmptyControl extends ControlRegistry.Control {
        final JPanel content = new JPanel(), tile, actions = new JPanel();
        final Composition.WrappedText title, description;

        EmptyControl(KitView view, Nodes.Empty node) {
            super(view, node, new JPanel(new GridBagLayout()));
            tile = new Stacks.Tile(view.context(), DesignMetrics.Role.EMPTY_TILE);
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
                    Typography.Role.DESCRIPTION,
                    SemanticColor.TEXT_SECONDARY);
            content.setLayout(
                    new ContentLayout(this));
            actions.setLayout(new Stacks.StackLayout(context(), Nodes.LayoutRole.ACTIONS));
            int size = ThemePainting.metric(context(), DesignMetrics.Role.EMPTY_TILE);
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
            Stacks.mount(tile, List.of(controls.getFirst().component));
            Stacks.mount(
                    actions,
                    controls.subList(1, controls.size()).stream().map(c -> c.component).toList());
            Stacks.mount(content, List.of(tile, title, description, actions));
        }
    }

    static final class ContentLayout extends Composition.Vertical {
        final EmptyControl owner;
        ContentLayout(EmptyControl owner) { super(ThemePainting.metric(owner.context(), DesignMetrics.Role.ICON_GAP)); this.owner=owner; }
        @Override public void layoutContainer(Container parent) {
            super.layoutContainer(parent);
            int size=owner.tile.getPreferredSize().width;
            owner.tile.setBounds(Math.max(0,(parent.getWidth()-size)/2),owner.tile.getY(),size,size);
            int width=Math.min(parent.getWidth(),owner.actions.getPreferredSize().width);
            owner.actions.setBounds(Math.max(0,(parent.getWidth()-width)/2),owner.actions.getY(),width,owner.actions.getHeight());
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

        @Override
        public int height(Container p, int width) {
            width = Math.max(1, Math.min(width, ThemePainting.metric(owner.context(), DesignMetrics.Role.EMPTY_TEXT_WIDTH)));
            return owner.tile.getPreferredSize().height + owner.title.height(width)
                + owner.description.height(width) + Composition.height(owner.actions, width)
                + 3 * ThemePainting.metric(owner.context(), DesignMetrics.Role.ICON_GAP);
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
            int
                    width =
                            Math.min(
                                    p.getWidth(),
                                    ThemePainting.metric(
                                            owner.context(), DesignMetrics.Role.EMPTY_TEXT_WIDTH)),
                    gap = ThemePainting.metric(owner.context(), DesignMetrics.Role.ICON_GAP);
            int height =
                    owner.tile.getPreferredSize().height
                            + owner.title.height(width)
                            + owner.description.height(width)
                            + owner.actions.getPreferredSize().height
                            + 3 * gap;
            int x = Math.max(0, (p.getWidth() - width) / 2),
                    y = Math.max(0, (p.getHeight() - height) / 2);
            owner.content.setBounds(x, y, width, height);
            owner.content.doLayout();
            int tw = owner.tile.getPreferredSize().width;
            owner.tile.setBounds((width - tw) / 2, 0, tw, tw);
            int aw = Math.min(width, owner.actions.getPreferredSize().width);
            owner.actions.setBounds(
                    (width - aw) / 2, owner.actions.getY(), aw, owner.actions.getHeight());
        }
    }
}

package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** One inline title/count row with separately owned host action slots. */
final class PanelHeaders {
    private PanelHeaders() {}

    static final class HeaderControl extends ControlRegistry.Control {
        final JLabel title = Labels.literal(), count = Labels.literal();
        final JPanel descriptor = new JPanel(), host = new JPanel();
        Map<String, Buttons.ButtonControl> hosts = new LinkedHashMap<>();
        Consumer<UiEvent> hostEvents = e -> {};
        Menus.MenuControl overflow;

        HeaderControl(KitView view, Nodes.Header node) {
            super(view, node, new JPanel());
            component.setOpaque(false);
            descriptor.setOpaque(false);
            host.setOpaque(false);
            KitOwnership.mark(title, node.id() + ".title");
            KitOwnership.mark(count, node.id() + ".count");
            title.putClientProperty("html.disable", true);
            count.putClientProperty("html.disable", true);
            own(
                    () -> {
                        hosts.values().forEach(ControlRegistry.Control::close);
                        hosts.clear();
                    });
            own(
                    () -> {
                        if (overflow != null) overflow.close();
                    });
            refresh();
        }

        void hostActions(List<Nodes.Button> actions, Consumer<UiEvent> events) {
            Objects.requireNonNull(events);
            actions = List.copyOf(actions);
            var ids = new HashSet<String>();
            for (var action : actions)
                if (!ids.add(action.id()))
                    throw new IllegalArgumentException("Duplicate host action id: " + action.id());
            var next = new LinkedHashMap<String, Buttons.ButtonControl>();
            var created = new ArrayList<Buttons.ButtonControl>();
            try {
                for (var action : actions) {
                    var old = hosts.get(action.id());
                    if (old != null
                            && ControlRegistry.kind(old.node).equals(ControlRegistry.kind(action)))
                        next.put(action.id(), old);
                    else {
                        var control =
                                new Buttons.ButtonControl(
                                        view,
                                        action,
                                        e -> {
                                            if (interactive()) hostEvents.accept(e);
                                        });
                        created.add(control);
                        next.put(action.id(), control);
                    }
                }
            } catch (Throwable failure) {
                created.forEach(ControlRegistry.Control::close);
                throw failure;
            }
            hostEvents = events;
            for (var action : actions) next.get(action.id()).update(action);
            for (var old : hosts.values()) if (next.get(old.node.id()) != old) old.close();
            hosts = next;
            Stacks.mount(host, hosts.values().stream().map(c -> c.component).toList());
            refresh();
        }

        @Override
        void refresh() {
            state();
            var h = (Nodes.Header) node;
            title.setText(h.title());
            count.setText(h.count().isPresent() ? Integer.toString(h.count().getAsInt()) : "");
            count.setVisible(h.count().isPresent() && h.count().getAsInt() > 0);
            Composition.style(
                    title, context(), Typography.Role.PANEL_TITLE, SemanticColor.TEXT_PRIMARY);
            Composition.style(
                    count, context(), Typography.Role.COUNT, SemanticColor.TEXT_SECONDARY);
            title.setEnabled(component.isEnabled());
            count.setEnabled(component.isEnabled());
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            component.setBorder(new EmptyBorder(0, pad, 0, pad));
            component.setLayout(new HeaderLayout(this));
            descriptor.setLayout(new Navigation.InlineLayout(context()));
            host.setLayout(new Navigation.InlineLayout(context()));
            for (var control : hosts.values()) {
                control.parentEnabled = interactive();
                control.button.panel = true;
                control.refresh();
            }
            if (h.overflow().isEmpty()) {
                if (overflow != null) {
                    overflow.close();
                    overflow = null;
                }
            } else {
                var menu =
                        new Nodes.MenuButton(
                                h.id() + ".overflow",
                                h.state(),
                                "",
                                "more",
                                Nodes.ButtonKind.GHOST,
                                "",
                                h.overflow(),
                                "More actions");
                if (overflow == null) overflow = new Menus.MenuControl(view, menu);
                else overflow.update(menu);
                overflow.parentEnabled = parentEnabled;
                overflow.refresh();
                overflow.main.panel = true;
                overflow.arrow.panel = true;
                ((Menus.Main) overflow.main).menuOnly = false;
                int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING);
                overflow.main.setBorder(new EmptyBorder(ring, ring, ring, ring));
            }
            var wanted = new ArrayList<JComponent>(List.of(title, count, descriptor));
            if (overflow != null) wanted.add(overflow.component);
            wanted.add(host);
            Stacks.mount(component, wanted);
            component.revalidate();
            component.repaint();
        }

        @Override
        Container childParent(Nodes.Node child) {
            return descriptor;
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            for (var control : controls) {
                if (control instanceof Buttons.ButtonControl button) button.button.panel = true;
                if (control instanceof Menus.MenuControl menu) {
                    menu.main.panel = true;
                    menu.arrow.panel = true;
                }
            }
            Stacks.mount(descriptor, controls.stream().map(c -> c.component).toList());
        }

        boolean dismiss() {
            return overflow != null && overflow.dismiss();
        }

        boolean popupOpen() {
            return overflow != null && overflow.popup.isVisible();
        }
    }

    static final class HeaderLayout implements Composition.WidthLayout {
        final HeaderControl owner;

        HeaderLayout(HeaderControl owner) {
            this.owner = owner;
        }

        int gap() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.ICON_GAP);
        }

        @Override
        public int height(Container p, int width) {
            int h = ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_HEADER);
            for (var c : p.getComponents())
                if (c.isVisible()) h = Math.max(h, c.getPreferredSize().height + 2 * gap());
            return h;
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            int w = p.getInsets().left + p.getInsets().right;
            for (var c : p.getComponents())
                if (c.isVisible()) w += c.getPreferredSize().width + gap();
            return new Dimension(w, height(p, w));
        }

        @Override
        public void layoutContainer(Container p) {
            Insets i = p.getInsets();
            int left = i.left, right = Math.max(left, p.getWidth() - i.right), g = gap();
            // Reserve trailing controls first. Only the title's text lane may compress.
            var trailing = new ArrayList<Component>();
            if (owner.host.getComponentCount() > 0) trailing.add(owner.host);
            if (owner.overflow != null) trailing.add(owner.overflow.component);
            if (owner.descriptor.getComponentCount() > 0) trailing.add(owner.descriptor);
            for (var c : trailing) {
                int w = Math.min(c.getPreferredSize().width, Math.max(0, right - left));
                right -= w;
                place(c, right, w, p.getHeight());
                right = Math.max(left, right - g);
            }
            int cw =
                    owner.count.isVisible()
                            ? Math.min(
                                    owner.count.getPreferredSize().width, Math.max(0, right - left))
                            : 0;
            int tw =
                    Math.min(
                            owner.title.getPreferredSize().width,
                            Math.max(0, right - left - cw - (cw > 0 ? g : 0)));
            place(owner.title, left, tw, p.getHeight());
            left += tw + g;
            if (cw > 0) place(owner.count, left, cw, p.getHeight());
        }

        void place(Component c, int x, int w, int height) {
            int h = Math.min(height, c.getPreferredSize().height);
            c.setBounds(x, height / 2 - h / 2, w, h);
        }
    }
}

package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;

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
        final JLabel title = Labels.literal(), count = Labels.literal(), detail = Labels.literal();
        final JPanel descriptor = new JPanel(), host = new JPanel();
        Map<String, Buttons.ButtonControl> hosts = new LinkedHashMap<>();
        Consumer<UiEvent> hostEvents = e -> {};
        Menus.MenuControl overflow;
        /** Host header metadata; its actions are matched to {@link #contributed} by position. */
        PanelHostHeader hostHeader = PanelHostHeader.EMPTY;
        final List<Buttons.ButtonControl> contributed = new ArrayList<>();

        HeaderControl(KitView view, Nodes.Header node) {
            super(view, node, new JPanel());
            component.setOpaque(false);
            descriptor.setOpaque(false);
            host.setOpaque(false);
            KitOwnership.mark(title, node.id() + ".title");
            KitOwnership.mark(count, node.id() + ".count");
            KitOwnership.mark(detail, node.id() + ".host-detail");
            title.putClientProperty("html.disable", true);
            count.putClientProperty("html.disable", true);
            detail.putClientProperty("html.disable", true);
            own(
                    () -> {
                        hosts.values().forEach(ControlRegistry.Control::close);
                        hosts.clear();
                    });
            own(
                    () -> {
                        contributed.forEach(ControlRegistry.Control::close);
                        contributed.clear();
                        hostHeader = PanelHostHeader.EMPTY;
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
            mountHost();
            refresh();
        }

        /**
         * Shows host metadata: the detail and dot after the count, and the contributed actions first in the host slot,
         * as a native tool window header orders contributed, options and hide. Controls are kept by position, so a
         * metadata refresh keeps focus and identity; each activation runs the current description's action once.
         */
        void hostHeader(PanelHostHeader header) {
            Objects.requireNonNull(header);
            var actions = header.actions();
            while (contributed.size() > actions.size()) contributed.removeLast().close();
            for (int i = 0; i < actions.size(); i++) {
                var action = actions.get(i);
                var button = new Nodes.Button(node.id() + ".host-header-action-" + i, ControlState.READY, "",
                        // The node's glyph name is never resolved: the control shows the host's artwork.
                        "more", Nodes.ButtonKind.GHOST, "host-header-action-" + i, action.label());
                if (i == contributed.size()) {
                    int index = i;
                    contributed.add(new Buttons.ButtonControl(view, button, e -> {
                        if (interactive() && index < hostHeader.actions().size()) hostHeader.actions().get(index).invoke().run();
                    }, action.icon()));
                } else {
                    var control = contributed.get(i);
                    control.artwork = action.icon();
                    control.update(button);
                }
                contributed.get(i).semanticArtwork = action.artwork();
                contributed.get(i).refresh();
            }
            hostHeader = header;
            mountHost();
            refresh();
        }

        private void mountHost() {
            var controls = new ArrayList<JComponent>();
            contributed.forEach(c -> controls.add(c.component));
            hosts.values().forEach(c -> controls.add(c.component));
            Stacks.mount(host, controls);
        }

        /** The connected dot: the success status held to the indicator floor on the panel surface it sits on. */
        private Icon dot() {
            return new Icon() {
                @Override public int getIconWidth() { return ThemePainting.metric(context(), DesignMetrics.Role.STATUS_DOT); }
                @Override public int getIconHeight() { return getIconWidth(); }
                @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
                    var g = (Graphics2D) graphics.create();
                    try {
                        ThemePainting.antialias(g);
                        g.setColor(SurfaceContrast.on(context().theme().semantic(), SemanticColor.STATUS_SUCCESS,
                                SemanticColor.SURFACE_PANEL, SurfaceContrast.INDICATOR));
                        g.fillOval(x, y, getIconWidth(), getIconHeight());
                    } finally {
                        g.dispose();
                    }
                }
            };
        }

        @Override
        void refresh() {
            state();
            var h = (Nodes.Header) node;
            title.setText(h.title());
            count.setText(h.count().isPresent() ? Integer.toString(h.count().getAsInt()) : "");
            count.setVisible(h.count().isPresent() && h.count().getAsInt() > 0);
            Composition.style(
                    title, context(), Typography.Role.PANEL_HEADER_TITLE, SemanticColor.TEXT_PRIMARY);
            Composition.style(
                    count, context(), Typography.Role.PANEL_HEADER_COUNT, SemanticColor.TEXT_TERTIARY);
            title.setEnabled(component.isEnabled());
            count.setEnabled(component.isEnabled());
            detail.setText(hostHeader.detail());
            detail.setToolTipText(hostHeader.detail().isEmpty() ? null : hostHeader.detail());
            detail.setIcon(hostHeader.active() ? dot() : null);
            detail.setIconTextGap(ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP));
            detail.setVisible(!hostHeader.detail().isEmpty() || hostHeader.active());
            Composition.style(
                    detail, context(), Typography.Role.PANEL_HEADER_COUNT, SemanticColor.TEXT_SECONDARY);
            detail.setEnabled(component.isEnabled());
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            component.setBorder(new EmptyBorder(0, pad, 0, ThemePainting.metric(context(),DesignMetrics.Role.PANEL_HEADER_TRAILING_PADDING)));
            component.setLayout(new HeaderLayout(this));
            descriptor.setLayout(new Navigation.InlineLayout(context()));
            host.setLayout(new Navigation.InlineLayout(context(),DesignMetrics.Role.PANEL_HEADER_ACTION_GAP));
            for (var control : contributed) {
                control.parentEnabled = interactive();
                control.button.panel = true;
                control.refresh();
            }
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
            var wanted = new ArrayList<JComponent>(List.of(title, count, detail, descriptor));
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

        private List<Component> trailing() {
            var controls = new ArrayList<Component>();
            if (owner.host.getComponentCount() > 0) controls.add(owner.host);
            if (owner.overflow != null) controls.add(owner.overflow.component);
            if (owner.descriptor.getComponentCount() > 0) controls.add(owner.descriptor);
            return controls;
        }

        @Override
        public int height(Container p, int width) {
            int height = ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_HEADER);
            int bottom = ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_HEADER_BOTTOM_GAP);
            int available = Math.max(0, width - p.getInsets().left - p.getInsets().right);
            // Use the same allocated widths as placement. A host action strip can wrap at enlarged
            // fonts even though its unconstrained preferred height still describes a single row.
            for (var control : trailing()) {
                int allocated = Math.min(control.getPreferredSize().width, available);
                height = Math.max(height, Composition.height(control, allocated) + bottom + 2 * ThemePainting.metric(owner.context(), DesignMetrics.Role.FOCUS_RING));
                available = Math.max(0, available - allocated - gap());
            }
            for (var label : List.of(owner.title, owner.count, owner.detail))
                if (label.isVisible()) height = Math.max(height, label.getPreferredSize().height + bottom);
            return height;
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
            int contentHeight = Math.max(0, p.getHeight() - ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_HEADER_BOTTOM_GAP));
            // Reserve trailing controls first. Only the title's text lane may compress.
            for (var c : trailing()) {
                int w = Math.min(c.getPreferredSize().width, Math.max(0, right - left));
                right -= w;
                place(c, right, w, contentHeight);
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
            place(owner.title, left, tw, contentHeight);
            left += tw + g;
            if (cw > 0) {
                place(owner.count, left, cw, contentHeight);
                left += cw + g;
            }
            // Host detail takes what the title and count leave, as the native header's detail does.
            if (owner.detail.isVisible())
                place(owner.detail, left, Math.min(owner.detail.getPreferredSize().width, Math.max(0, right - left)), contentHeight);
        }

        void place(Component c, int x, int w, int height) {
            int h = Math.min(height, Composition.height(c, w));
            c.setBounds(x, height / 2 - h / 2, w, h);
        }
    }
}

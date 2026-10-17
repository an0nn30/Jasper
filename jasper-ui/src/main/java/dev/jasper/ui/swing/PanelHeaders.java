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
        /** The header strip's painter over this view's kit, made once. */
        private ChromePainter painter;

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
            if (actions.equals(hostHeader.actions()) && actions.size() == contributed.size()) {
                // Only the detail, the dot or the frame changed (a focus move re-sends the header): keep the artwork.
                hostHeader = header;
                refresh();
                return;
            }
            var snapshots = new ArrayList<Buttons.HostArtwork>();
            var next = new ArrayList<Buttons.ButtonControl>();
            var nodes = new ArrayList<Nodes.Button>();
            var created = new ArrayList<Buttons.ButtonControl>();
            var previousNodes = contributed.stream().map(c -> c.node).toList();
            var previousArtwork = contributed.stream().map(c -> c.hostArtwork).toList();
            try {
                // Validate every action before changing even the first retained node, artwork or callback.
                for (var action : actions) snapshots.add(Buttons.HostArtwork.prepare(context(), action));
                for (int i = 0; i < actions.size(); i++) {
                    var action = actions.get(i);
                    var button = new Nodes.Button(node.id() + ".host-header-action-" + i, ControlState.READY, "",
                        "more", Nodes.ButtonKind.GHOST, "host-header-action-" + i, action.label());
                    nodes.add(button);
                    if (i < contributed.size()) next.add(contributed.get(i));
                    else {
                        int index = i;
                        var control = new Buttons.ButtonControl(view, button, e -> {
                            if (interactive() && index < hostHeader.actions().size()) hostHeader.actions().get(index).invoke().run();
                        });
                        created.add(control); next.add(control);
                    }
                }
                for (int i = 0; i < next.size(); i++) {
                    next.get(i).hostArtwork = snapshots.get(i);
                    next.get(i).update(nodes.get(i));
                }
            } catch (Throwable failure) {
                for (int i = 0; i < Math.min(next.size(), contributed.size()); i++) {
                    var control = contributed.get(i); control.hostArtwork = previousArtwork.get(i);
                    try { control.update(previousNodes.get(i)); } catch (Throwable rollback) { failure.addSuppressed(rollback); }
                }
                created.forEach(ControlRegistry.Control::close);
                snapshots.forEach(Buttons.HostArtwork::close);
                throw failure;
            }
            for (int i = 0; i < Math.min(next.size(), contributed.size()); i++)
                if (previousArtwork.get(i) != null) previousArtwork.get(i).close();
            for (int i = next.size(); i < contributed.size(); i++) contributed.get(i).close();
            contributed.clear(); contributed.addAll(next);
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

        /** Whether the host frames this header as a flush frame's strip (spec 10.7). */
        boolean flush() { return hostHeader.frame() != PanelHostHeader.Frame.STANDARD; }

        /** Whether that strip's frame holds the focus owner. */
        boolean focusedFrame() { return hostHeader.frame() == PanelHostHeader.Frame.FLUSH_FOCUSED; }

        /** The header strip's painter over this view's kit. */
        ChromePainter painter() {
            if (painter == null || painter.kit() != context().kit()) painter = new ChromePainter(context().kit());
            return painter;
        }

        /** The connected dot: the success status held to the indicator floor on the panel surface it sits on. */
        private Icon dot() {
            return new Icon() {
                @Override public int getIconWidth() { return ThemePainting.metric(context(), DesignMetrics.Role.STATUS_DOT); }
                @Override public int getIconHeight() { return getIconWidth(); }
                @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
                    if (flush()) {
                        // On a header strip the dot is held on the strip's own surface (ChromePainter.DOCK_PAIRS).
                        painter().headerDot(graphics, x, y, getIconWidth(), context().theme(), focusedFrame());
                        return;
                    }
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
            count.setText(h.count().isPresent() ? (flush() ? "(" + h.count().getAsInt() + ")" : Integer.toString(h.count().getAsInt())) : "");
            count.setVisible(h.count().isPresent() && h.count().getAsInt() > 0);
            Composition.style(
                    title, context(), Typography.Role.PANEL_HEADER_TITLE, SemanticColor.TEXT_PRIMARY);
            Composition.style(
                    count, context(), Typography.Role.PANEL_HEADER_COUNT, SemanticColor.TEXT_TERTIARY);
            if (flush()) {
                title.setForeground(painter().headerTitle(context().theme(), focusedFrame()));
                count.setForeground(painter().headerDetail(context().theme(), focusedFrame(), true));
            }
            title.setEnabled(component.isEnabled());
            count.setEnabled(component.isEnabled());
            detail.setText(hostHeader.detail());
            detail.setToolTipText(hostHeader.detail().isEmpty() ? null : hostHeader.detail());
            detail.setIcon(hostHeader.active() ? dot() : null);
            detail.setIconTextGap(ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP));
            detail.setVisible(!hostHeader.detail().isEmpty() || hostHeader.active());
            Composition.style(
                    detail, context(), Typography.Role.PANEL_HEADER_COUNT, SemanticColor.TEXT_SECONDARY);
            if (flush()) {
                // A strip's word detail ("Mac") is in the label face and muted; a digits-only detail is a count (spec 10.7).
                boolean digits = hostHeader.detail().matches("\\d+");
                if (digits) detail.setText("(" + hostHeader.detail() + ")");
                else Composition.style(detail, context(), Typography.Role.LABEL, SemanticColor.TEXT_SECONDARY);
                detail.setIconTextGap(ThemePainting.metric(context(), DesignMetrics.Role.HEADER_STRIP_DOT_GAP));
                detail.setForeground(painter().headerDetail(context().theme(), focusedFrame(), digits));
            }
            detail.setEnabled(component.isEnabled());
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            int trailing = ThemePainting.metric(context(), DesignMetrics.Role.PANEL_HEADER_TRAILING_PADDING);
            component.setBorder(flush() ? new StripBorder(pad, ThemePainting.metric(context(), DesignMetrics.Role.DOCK_RULE), trailing, this)
                    : new EmptyBorder(0, pad, 0, trailing));
            component.setLayout(new HeaderLayout(this));
            descriptor.setLayout(flush() ? new Navigation.InlineLayout(context(), DesignMetrics.Role.PANEL_HEADER_ACTION_GAP)
                    : new Navigation.InlineLayout(context()));
            host.setLayout(new Navigation.InlineLayout(context(),DesignMetrics.Role.PANEL_HEADER_ACTION_GAP));
            // A strip draws every action, its own and contributed alike, at one glyph size in one ink (spec 10.7).
            var stripInk = flush() ? painter().headerAction(context().theme(), focusedFrame()) : null;
            for (var control : contributed) {
                control.parentEnabled = interactive();
                control.button.panel = true;
                control.button.stripInk = stripInk;
                control.refresh();
            }
            for (var control : hosts.values()) {
                control.parentEnabled = interactive();
                control.button.panel = true;
                control.button.stripInk = stripInk;
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

    /**
     * A borrowed header's strip under a flush frame (spec 10.7): it fills the header in the strip's surface and draws the
     * 1 px rule along its bottom, under the header's labels and actions; its insets keep the header's paddings and the
     * rule.
     */
    private static final class StripBorder extends EmptyBorder {
        private final HeaderControl owner;

        StripBorder(int left, int rule, int right, HeaderControl owner) {
            super(0, left, rule, right);
            this.owner = owner;
        }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics copy = g.create(x, y, width, height);
            try { owner.painter().headerStrip(copy, width, height, owner.context().theme(), owner.focusedFrame()); }
            finally { copy.dispose(); }
        }

        @Override public boolean isBorderOpaque() { return true; }
    }

    static final class HeaderLayout implements Composition.WidthLayout {
        final HeaderControl owner;

        HeaderLayout(HeaderControl owner) {
            this.owner = owner;
        }

        int gap() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.ICON_GAP);
        }

        /** Between the trailing groups: the icon gap, or on a header strip the actions' 2 px. */
        int trailingGap() {
            return owner.flush() ? ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_HEADER_ACTION_GAP) : gap();
        }

        /** After the title and after the count: the icon gap, or on a header strip the title gap. */
        int textGap() {
            return owner.flush() ? ThemePainting.metric(owner.context(), DesignMetrics.Role.HEADER_STRIP_TITLE_GAP) : gap();
        }

        /** Before a count: the text gap, or on a header strip the mocks' title-to-count gap. */
        int countGap() {
            return owner.flush() ? ThemePainting.metric(owner.context(), DesignMetrics.Role.HEADER_STRIP_COUNT_GAP) : textGap();
        }

        /**
         * How far the title's {@code width} wide label moves what follows it: the width, or on a header strip the title's
         * set advance (the mocks' kerned fractional one, which a label's whole-pixel width overruns).
         */
        int titleAdvance(int width) {
            if (!owner.flush() || owner.title.getText() == null || owner.title.getFont() == null) return width;
            return Math.min(width, ThemePainting.setAdvance(owner.title.getFont(), owner.title.getText()));
        }

        /** What lies under the content: the bottom gap, or a header strip's rule. */
        int bottom() {
            return ThemePainting.metric(owner.context(), owner.flush() ? DesignMetrics.Role.DOCK_RULE : DesignMetrics.Role.PANEL_HEADER_BOTTOM_GAP);
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
            int bottom = bottom();
            // A header strip is PANEL_HEADER tall above its rule.
            int height = ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_HEADER) + (owner.flush() ? bottom : 0);
            int available = Math.max(0, width - p.getInsets().left - p.getInsets().right);
            // Use the same allocated widths as placement. A host action strip can wrap at enlarged
            // fonts even though its unconstrained preferred height still describes a single row.
            for (var control : trailing()) {
                int allocated = Math.min(control.getPreferredSize().width, available);
                height = Math.max(height, Composition.height(control, allocated) + bottom + 2 * ThemePainting.metric(owner.context(), DesignMetrics.Role.FOCUS_RING));
                available = Math.max(0, available - allocated - trailingGap());
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
            int left = i.left, right = Math.max(left, p.getWidth() - i.right), g = trailingGap(), t = textGap(), counting = countGap();
            int contentHeight = Math.max(0, p.getHeight() - bottom());
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
                            Math.max(0, right - left - cw - (cw > 0 ? counting : 0)));
            place(owner.title, left, tw, contentHeight);
            // A count, or a detail that is one ("(1)"), follows the title by the count gap; a dot or word by the text gap.
            boolean counted = cw > 0 || owner.flush() && owner.detail.getText() != null && owner.detail.getText().matches("\\(\\d+\\)");
            left += titleAdvance(tw) + (counted ? counting : t);
            if (cw > 0) {
                place(owner.count, left, cw, contentHeight);
                left += cw + t;
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

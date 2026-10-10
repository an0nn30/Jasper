package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Path2D;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Content-only forms; app owns native chrome, available height and window lifetime. */
final class Forms {
    private Forms() {}

    static final class FormControl extends ControlRegistry.Control {
        final Body body = new Body();
        final JPanel footer = new JPanel(),
                actions = new JPanel(),
                preview = new JPanel(new BorderLayout());
        final JScrollPane scroll = new JScrollPane(body);
        List<ControlRegistry.Control> controls = List.of();
        final JLabel title = Labels.literal();

        FormControl(KitView view, Nodes.Form node) {
            super(view, node, new Surface(view));
            component.setLayout(new BorderLayout());
            KitOwnership.mark(title, node.id() + ".title");
            body.add(title);
            body.setOpaque(false);
            footer.setOpaque(false);
            actions.setOpaque(false);
            preview.setOpaque(false);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
            KitOwnership.mark(scroll, node.id() + ".scroll");
            component.add(scroll, BorderLayout.CENTER);
            component.add(footer, BorderLayout.SOUTH);
            footer.add(preview);
            footer.add(actions);
            bind(
                    "ENTER",
                    "primary",
                    () ->
                            submit(
                                    KeyboardFocusManager.getCurrentKeyboardFocusManager()
                                            .getFocusOwner()));
            bind("ESCAPE", "cancel", this::cancel);
            component.setFocusCycleRoot(true);
            component.setFocusTraversalPolicy(
                    new LayoutFocusTraversalPolicy() {
                        @Override
                        public Component getDefaultComponent(Container root) {
                            return firstInput();
                        }
                    });
            refresh();
        }

        void cancel() {
            if (!view.dismissPopup()
                    && interactive()
                    && !((Nodes.Form) node).cancelAction().isBlank())
                emit(new UiEvent.Action(((Nodes.Form) node).cancelAction()));
        }

        void bind(String stroke, String key, Runnable run) {
            var input = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            input.put(KeyStroke.getKeyStroke(stroke), key);
            component
                    .getActionMap()
                    .put(
                            key,
                            new AbstractAction() {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    run.run();
                                }
                            });
            own(
                    () -> {
                        input.remove(KeyStroke.getKeyStroke(stroke));
                        component.getActionMap().remove(key);
                    });
        }

        Component firstInput() {
            for (var child : controls) {
                Component found = input(child.node);
                if (found != null) return found;
            }
            return component;
        }

        Component input(Nodes.Node node) {
            var owner = view.control(node.id());
            if (owner.interactive()
                    && (node instanceof Nodes.Field
                            || node instanceof Nodes.Secret
                            || node instanceof Nodes.Choice)) return owner.focusTarget();
            for (var child : ControlRegistry.children(node)) {
                Component found = input(child);
                if (found != null) return found;
            }
            return null;
        }

        void submit(Component focus) {
            if (!interactive() || view.popupOpen() || focus instanceof JTextArea || !valid(node))
                return;
            var form = (Nodes.Form) node;
            form.actions().stream()
                    .filter(a -> a.kind() == Nodes.ButtonKind.PRIMARY)
                    .findFirst()
                    .ifPresent(
                            a -> {
                                var owner = view.control(a.id());
                                if (owner.interactive()) owner.emit(new UiEvent.Action(a.action()));
                            });
        }

        boolean valid(Nodes.Node node) {
            if (!node.state().visible() || !node.state().enabled()) return true;
            if (node.state().busy() || !node.state().error().isEmpty()) return false;
            return ControlRegistry.children(node).stream().allMatch(this::valid);
        }

        @Override
        void refresh() {
            state();
            var form = (Nodes.Form) node;
            title.setText(form.title());
            title.setVisible(form.kind() == Nodes.FormKind.SHEET && form.hero().isEmpty());
            Composition.style(
                    title, context(), Typography.Role.HERO_TITLE, SemanticColor.TEXT_PRIMARY);
            ((Surface) component).sheet = form.kind() == Nodes.FormKind.SHEET;
            boolean hero = form.kind() == Nodes.FormKind.HERO || form.kind() == Nodes.FormKind.HERO_COMPACT;
            boolean compact = form.kind() == Nodes.FormKind.HERO_COMPACT;
            int side = ThemePainting.metric(context(), hero ? DesignMetrics.Role.HERO_SIDES : DesignMetrics.Role.FORM_SIDES);
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.FOOTER_PADDING);
            int focusClearance = hero ? ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING) : 0;
            body.setBorder(new EmptyBorder(hero ? ThemePainting.metric(context(), DesignMetrics.Role.HERO_TOP) : pad,
                    side, focusClearance, side));
            body.setLayout(hero ? new Composition.Vertical(ThemePainting.metric(context(), DesignMetrics.Role.HERO_SECTION_GAP)) {
                @Override int gap(Component previous, Component next) {
                    if (form.hero().isPresent() && previous == view.control(form.hero().get().id()).component)
                        return ThemePainting.metric(context(), compact ? DesignMetrics.Role.HERO_COMPACT_INTRO_GAP : DesignMetrics.Role.HERO_INTRO_GAP);
                    return previous instanceof Cards.CardSurface
                        ? ThemePainting.metric(context(), DesignMetrics.Role.HERO_FOOTNOTE_GAP) : super.gap(previous, next);
                }
            } : new Composition.Vertical(ThemePainting.metric(context(), form.kind() == Nodes.FormKind.SHEET
                    ? DesignMetrics.Role.SHEET_SECTION_GAP : DesignMetrics.Role.SECTION_GAP)));
            int footerTop = hero ? ThemePainting.metric(context(), compact
                    ? DesignMetrics.Role.HERO_COMPACT_FOOTER_TOP : DesignMetrics.Role.HERO_FOOTER_TOP) - focusClearance : pad;
            footer.setBorder(new EmptyBorder(footerTop, side,
                    hero ? ThemePainting.metric(context(), DesignMetrics.Role.HERO_FOOTER_BOTTOM) : pad, side));
            footer.setLayout(new FooterLayout(this));
            actions.setLayout(
                    hero
                            ? new GridLayout(
                                    1,
                                    0,
                                    ThemePainting.metric(context(), DesignMetrics.Role.BUTTON_GAP),
                                    0)
                            : new Stacks.StackLayout(context(), Nodes.LayoutRole.ACTIONS));
            scroll.getVerticalScrollBar()
                    .setUnitIncrement(ThemePainting.metric(context(), DesignMetrics.Role.ROW_GAP));
            scroll.getVerticalScrollBar()
                    .setBackground(ThemePainting.color(context(), SemanticColor.SURFACE_DIALOG));
            scroll.getVerticalScrollBar()
                    .setForeground(ThemePainting.color(context(), SemanticColor.BORDER_SUBTLE));
            component.revalidate();
            component.repaint();
        }

        @Override
        Container childParent(Nodes.Node child) {
            var f = (Nodes.Form) node;
            return f.actions().contains(child)
                    ? actions
                    : f.preview().isPresent() && f.preview().get() == child ? preview : body;
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            this.controls = controls;
            var f = (Nodes.Form) node;
            Stacks.mount(
                    body,
                    java.util.stream.Stream.concat(
                                    java.util.stream.Stream.of(title),
                                    controls.stream()
                                            .filter(c -> childParent(c.node) == body)
                                            .map(c -> c.component))
                            .toList());
            Stacks.mount(
                    actions,
                    controls.stream()
                            .filter(c -> childParent(c.node) == actions)
                            .map(c -> c.component)
                            .toList());
            Stacks.mount(
                    preview,
                    controls.stream()
                            .filter(c -> childParent(c.node) == preview)
                            .map(c -> c.component)
                            .toList());
            boolean valid = valid(node);
            for (var control : controls)
                if (control.node instanceof Nodes.Button action
                        && action.kind() == Nodes.ButtonKind.PRIMARY) {
                    control.parentEnabled &= valid;
                    control.refresh();
                }
            preview.setVisible(f.preview().isPresent());
        }
    }

    static void cancelFrom(KitView view, Component source) {
        if (view.dismissPopup()) return;
        for (Component current = source; current != null; current = current.getParent()) {
            if (current instanceof Surface) {
                ((FormControl) view.control(current.getName())).cancel();
                return;
            }
        }
    }

    static final class Body extends JPanel implements Scrollable {
        @Override public Dimension getPreferredSize() {
            if (getLayout() instanceof Composition.WidthLayout layout && getParent() instanceof JViewport viewport
                    && viewport.getParent() instanceof JScrollPane scroll && scroll.getWidth() > 0) {
                // Measure the current scroll allocation, not this body's stale width after an earlier scrollbar.
                // Otherwise a queued native repack can retain a scrollbar solely because its old width wraps text.
                Insets insets = scroll.getInsets();
                int width = Math.max(1, scroll.getWidth() - insets.left - insets.right);
                int availableHeight = scroll.getHeight() - insets.top - insets.bottom;
                int height = layout.height(this, width);
                if (height > availableHeight) {
                    width = Math.max(1, width - scroll.getVerticalScrollBar().getPreferredSize().width);
                    height = layout.height(this, width);
                }
                return new Dimension(width, height);
            }
            return super.getPreferredSize();
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int axis, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int axis, int direction) {
            return Math.max(16, r.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    static final class Surface extends JPanel {
        final KitView view;
        boolean sheet;

        Surface(KitView view) {
            this.view = view;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            var owner = (FormControl) view.control(getName());
            var f = (Nodes.Form) owner.node;
            int width =
                    ThemePainting.metric(
                            view.context(),
                            switch (f.kind()) {
                                case HERO -> DesignMetrics.Role.HERO_WIDTH;
                                case HERO_COMPACT -> DesignMetrics.Role.HERO_COMPACT_WIDTH;
                                case SHEET -> DesignMetrics.Role.SHEET_WIDTH;
                                default -> DesignMetrics.Role.FORM_WIDTH;
                            });
            int measuredWidth = getWidth() > 0 ? getWidth() : width;
            int height =
                    Composition.height(owner.body, measuredWidth)
                            + Composition.height(owner.footer, measuredWidth);
            int available = view.availableHeight();
            return new Dimension(
                    width,
                    available == Integer.MAX_VALUE
                            ? height
                            : Math.min(height, (int) Math.floor(available * .85)));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                int r = ThemePainting.metric(view.context(), DesignMetrics.Role.WINDOW_RADIUS);
                g.setColor(ThemePainting.color(view.context(), SemanticColor.SURFACE_DIALOG));
                if (!sheet) g.fillRoundRect(0, 0, getWidth(), getHeight(), r * 2, r * 2);
                else {
                    double radius = r;
                    var path = new Path2D.Double();
                    path.moveTo(0, 0);
                    path.lineTo(getWidth(), 0);
                    path.lineTo(getWidth(), getHeight() - radius);
                    path.quadTo(getWidth(), getHeight(), getWidth() - radius, getHeight());
                    path.lineTo(radius, getHeight());
                    path.quadTo(0, getHeight(), 0, getHeight() - radius);
                    path.closePath();
                    g.fill(path);
                }
            } finally {
                g.dispose();
            }
        }
    }

    static final class FooterLayout implements Composition.WidthLayout {
        final FormControl owner;

        FooterLayout(FormControl owner) {
            this.owner = owner;
        }

        int gap() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.COLUMN_GAP);
        }

        record Geometry(
                int contentWidth,
                int actionWidth,
                int actionHeight,
                int previewWidth,
                int previewHeight,
                boolean stacked) {}

        Geometry geometry(Container parent, int width) {
            Insets i = parent.getInsets();
            int contentWidth = Math.max(0, width - i.left - i.right);
            int actionWidth =
                    (((Nodes.Form) owner.node).kind() == Nodes.FormKind.HERO || ((Nodes.Form) owner.node).kind() == Nodes.FormKind.HERO_COMPACT)
                            ? contentWidth
                            : Math.min(contentWidth, owner.actions.getPreferredSize().width);
            int actionHeight = Composition.height(owner.actions, actionWidth);
            int sharedPreviewWidth = Math.max(0, contentWidth - actionWidth - gap());
            boolean stacked =
                    owner.preview.isVisible()
                            && owner.preview.getPreferredSize().width > sharedPreviewWidth;
            int previewWidth = stacked ? contentWidth : sharedPreviewWidth;
            int previewHeight =
                    owner.preview.isVisible() ? Composition.height(owner.preview, previewWidth) : 0;
            return new Geometry(
                    contentWidth, actionWidth, actionHeight, previewWidth, previewHeight, stacked);
        }

        @Override
        public int height(Container p, int width) {
            Insets i = p.getInsets();
            var geometry = geometry(p, width);
            return i.top
                    + i.bottom
                    + (geometry.stacked()
                            ? geometry.previewHeight() + gap() + geometry.actionHeight()
                            : Math.max(geometry.previewHeight(), geometry.actionHeight()));
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            Insets i = p.getInsets();
            int naturalWidth =
                    i.left
                            + i.right
                            + owner.actions.getPreferredSize().width
                            + (owner.preview.isVisible()
                                    ? owner.preview.getPreferredSize().width + gap()
                                    : 0);
            int width =
                    p.getParent() != null && p.getParent().getWidth() > 0
                            ? p.getParent().getWidth()
                            : p.getWidth() > 0
                                    ? p.getWidth()
                                    : ThemePainting.metric(
                                            owner.context(),
                                            switch (((Nodes.Form) owner.node).kind()) {
                                                case HERO -> DesignMetrics.Role.HERO_WIDTH;
                                                case HERO_COMPACT -> DesignMetrics.Role.HERO_COMPACT_WIDTH;
                                                case SHEET -> DesignMetrics.Role.SHEET_WIDTH;
                                                default -> DesignMetrics.Role.FORM_WIDTH;
                                            });
            return new Dimension(naturalWidth, height(p, width));
        }

        @Override
        public void layoutContainer(Container p) {
            Insets i = p.getInsets();
            var geometry = geometry(p, p.getWidth());
            int availableHeight = Math.max(0, p.getHeight() - i.top - i.bottom);
            int actionY =
                    geometry.stacked()
                            ? i.top + geometry.previewHeight() + gap()
                            : i.top + Math.max(0, (availableHeight - geometry.actionHeight()) / 2);
            owner.actions.setBounds(
                    p.getWidth() - i.right - geometry.actionWidth(),
                    actionY,
                    geometry.actionWidth(),
                    geometry.actionHeight());
            if (owner.preview.isVisible()) {
                int previewY =
                        geometry.stacked()
                                ? i.top
                                : i.top
                                        + Math.max(
                                                0,
                                                (availableHeight - geometry.previewHeight()) / 2);
                owner.preview.setBounds(
                        i.left, previewY, geometry.previewWidth(), geometry.previewHeight());
            }
        }
    }
}

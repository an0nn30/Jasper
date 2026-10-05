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
            int side =
                    ThemePainting.metric(
                            context(),
                            form.kind() == Nodes.FormKind.HERO
                                    ? DesignMetrics.Role.HERO_SIDES
                                    : DesignMetrics.Role.FORM_SIDES);
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.FOOTER_PADDING);
            body.setBorder(new EmptyBorder(pad, side, 0, side));
            body.setLayout(
                    new Composition.Vertical(
                            ThemePainting.metric(
                                    context(),
                                    form.kind() == Nodes.FormKind.SHEET
                                            ? DesignMetrics.Role.SHEET_SECTION_GAP
                                            : DesignMetrics.Role.SECTION_GAP)));
            footer.setBorder(new EmptyBorder(pad, side, pad, side));
            footer.setLayout(new FooterLayout(this));
            actions.setLayout(
                    form.kind() == Nodes.FormKind.HERO
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
                                case SHEET -> DesignMetrics.Role.SHEET_WIDTH;
                                default -> DesignMetrics.Role.FORM_WIDTH;
                            });
            int height =
                    Composition.height(owner.body, getWidth() > 0 ? getWidth() : width)
                            + owner.footer.getPreferredSize().height;
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
                if (!sheet) g.fillRoundRect(0, 0, getWidth(), getHeight(), r, r);
                else {
                    double half = r / 2.0;
                    var path = new Path2D.Double();
                    path.moveTo(0, 0);
                    path.lineTo(getWidth(), 0);
                    path.lineTo(getWidth(), getHeight() - half);
                    path.quadTo(getWidth(), getHeight(), getWidth() - half, getHeight());
                    path.lineTo(half, getHeight());
                    path.quadTo(0, getHeight(), 0, getHeight() - half);
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

        @Override
        public int height(Container p, int width) {
            Insets i = p.getInsets();
            int aw = owner.actions.getPreferredSize().width,
                    w = Math.max(0, width - i.left - i.right);
            int ph = owner.preview.isVisible() ? Composition.height(owner.preview, w) : 0,
                    ah = owner.actions.getPreferredSize().height;
            return i.top
                    + i.bottom
                    + (owner.preview.isVisible()
                                    && aw + owner.preview.getPreferredSize().width + gap() > w
                            ? ph + gap() + ah
                            : Math.max(ph, ah));
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            Insets i = p.getInsets();
            int w =
                    i.left
                            + i.right
                            + owner.actions.getPreferredSize().width
                            + (owner.preview.isVisible()
                                    ? owner.preview.getPreferredSize().width + gap()
                                    : 0);
            return new Dimension(
                    w,
                    height(
                            p,
                            p.getWidth() > 0
                                    ? p.getWidth()
                                    : ThemePainting.metric(
                                            owner.context(), DesignMetrics.Role.FORM_WIDTH)));
        }

        @Override
        public void layoutContainer(Container p) {
            Insets i = p.getInsets();
            int w = Math.max(0, p.getWidth() - i.left - i.right),
                    aw =
                            ((Nodes.Form) owner.node).kind() == Nodes.FormKind.HERO
                                    ? w
                                    : Math.min(w, owner.actions.getPreferredSize().width),
                    ah = owner.actions.getPreferredSize().height;
            int ay = p.getHeight() - i.bottom - ah;
            owner.actions.setBounds(p.getWidth() - i.right - aw, Math.max(i.top, ay), aw, ah);
            if (owner.preview.isVisible()) {
                int pw = Math.max(0, w - aw - gap());
                boolean stack = owner.preview.getPreferredSize().width > pw;
                owner.preview.setBounds(
                        i.left,
                        i.top,
                        stack ? w : pw,
                        stack ? Math.max(0, ay - i.top - gap()) : ah);
            }
        }
    }
}

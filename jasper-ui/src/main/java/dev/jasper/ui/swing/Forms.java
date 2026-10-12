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
        final Footer footer = new Footer();
        final JPanel actions = new JPanel(),
                preview = new JPanel(new BorderLayout());
        final JScrollPane scroll = new JScrollPane(body);
        final JScrollPane footerScroll = new JScrollPane(footer, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override public Dimension getPreferredSize() { return footer.getPreferredSize(); }
            @Override public Dimension getMinimumSize() { return footer.getMinimumSize(); }
        };
        final java.util.Map<Buttons.ButtonControl, java.awt.event.FocusListener> focusReveal = new java.util.IdentityHashMap<>();
        List<ControlRegistry.Control> controls = List.of();
        final JLabel title = Labels.literal();

        FormControl(KitView view, Nodes.Form node) {
            super(view, node, new Surface(view));
            component.setLayout(new BorderLayout() {
                @Override public void layoutContainer(Container parent) {
                    Insets insets = parent.getInsets();
                    int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
                    int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
                    int footerHeight = Math.min(height, Composition.height(footer, width));
                    if (height < footerHeight + ThemePainting.metric(context(),DesignMetrics.Role.FIELD_HEIGHT)) {
                        int actionHeight=java.util.Arrays.stream(actions.getComponents()).filter(Component::isVisible).mapToInt(c->c.getPreferredSize().height).max().orElse(0);
                        // Keep one full action reachable whenever possible; below that retain a bounded body viewport.
                        footerHeight=Math.min(footerHeight,Math.min(Math.max(0,height-1),Math.max(actionHeight,height/2)));
                    }
                    scroll.setBounds(insets.left, insets.top, width, height - footerHeight);
                    footerScroll.setBounds(insets.left, insets.top + height - footerHeight, width, footerHeight);
                }
            });
            own(() -> {
                focusReveal.forEach((button, listener) -> button.button.removeFocusListener(listener));
                focusReveal.clear();
            });
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
            footerScroll.setOpaque(false); footerScroll.getViewport().setOpaque(false);
            footerScroll.setBorder(new EmptyBorder(0, 0, 0, 0)); footerScroll.setViewportBorder(new EmptyBorder(0, 0, 0, 0));
            KitOwnership.mark(footerScroll, node.id() + ".footerScroll");
            component.add(footerScroll, BorderLayout.SOUTH);
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
            component.setFocusTraversalPolicy(
                    new LayoutFocusTraversalPolicy() {
                        @Override
                        public Component getDefaultComponent(Container root) {
                            return view.isClosed() ? null : firstInput();
                        }
                        @Override public Component getFirstComponent(Container root) {
                            return view.isClosed() ? null : super.getFirstComponent(root);
                        }
                        @Override public Component getLastComponent(Container root) {
                            return view.isClosed() ? null : super.getLastComponent(root);
                        }
                        @Override public Component getComponentAfter(Container root, Component child) {
                            return view.isClosed() ? null : super.getComponentAfter(root, child);
                        }
                        @Override public Component getComponentBefore(Container root, Component child) {
                            return view.isClosed() ? null : super.getComponentBefore(root, child);
                        }
                    });
            var focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
            java.beans.PropertyChangeListener focusChanged=event->{
                if(event.getNewValue() instanceof JComponent focused) revealFocusedBody(focused);
            };
            focusManager.addPropertyChangeListener("focusOwner",focusChanged);
            own(()->focusManager.removePropertyChangeListener("focusOwner",focusChanged));
            refresh();
        }

        void revealFocusedBody(JComponent focused) {
            if(!closed && !view.isClosed() && SwingUtilities.isDescendingFrom(focused,body))
                for (Container ancestor=focused.getParent(); ancestor!=null; ancestor=ancestor.getParent()) {
                    if (ancestor instanceof JViewport viewport && viewport.getView() instanceof JComponent content)
                        content.scrollRectToVisible(SwingUtilities.convertRectangle(focused,new Rectangle(0,0,focused.getWidth(),focused.getHeight()),content));
                    if (ancestor==scroll) break;
                }
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
            if (form.submission() == Nodes.FormSubmission.NONE) return;
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
            int stroke=ThemePainting.metric(context(),DesignMetrics.Role.HAIRLINE);
            component.setBorder(form.kind()==Nodes.FormKind.SHEET ? new EmptyBorder(0,stroke,stroke,stroke) : new EmptyBorder(0,0,0,0));
            boolean hero = form.kind() == Nodes.FormKind.HERO || form.kind() == Nodes.FormKind.HERO_COMPACT;
            boolean compact = form.kind() == Nodes.FormKind.HERO_COMPACT;
            boolean window = form.kind() == Nodes.FormKind.WINDOW;
            // A window body shares traversal with native title accessories. Dialog forms
            // retain their own cycle; a provider preserves our default input within a window.
            component.setFocusCycleRoot(!window);
            component.setFocusTraversalPolicyProvider(window);
            body.fill = window; footer.window = window; footer.context = context();
            int side = window ? ThemePainting.metric(context(), DesignMetrics.Role.WINDOW_FOOTER_SIDE) : ThemePainting.metric(context(), hero ? DesignMetrics.Role.HERO_SIDES : DesignMetrics.Role.FORM_SIDES);
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.FOOTER_PADDING);
            int focusClearance = hero ? ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING) : 0;
            body.setBorder(window ? new EmptyBorder(0, 0, 0, 0) : new EmptyBorder(hero ? ThemePainting.metric(context(), DesignMetrics.Role.HERO_TOP) : form.kind() == Nodes.FormKind.FULL ? ThemePainting.metric(context(), DesignMetrics.Role.FORM_TOP) : pad,
                    side, focusClearance, side));
            body.setLayout(window ? new Composition.Vertical(0) {
                @Override public void layoutContainer(Container parent) {
                    for (var child : parent.getComponents()) if (child.isVisible()) child.setBounds(0, 0, parent.getWidth(), parent.getHeight());
                }
            } : hero ? new Composition.Vertical(ThemePainting.metric(context(), DesignMetrics.Role.HERO_SECTION_GAP)) {
                @Override int gap(Component previous, Component next) {
                    if (form.hero().isPresent() && previous == view.control(form.hero().get().id()).component)
                        return ThemePainting.metric(context(), compact ? DesignMetrics.Role.HERO_COMPACT_INTRO_GAP : DesignMetrics.Role.HERO_INTRO_GAP);
                    return previous instanceof Cards.CardSurface
                        ? ThemePainting.metric(context(), DesignMetrics.Role.HERO_FOOTNOTE_GAP) : super.gap(previous, next);
                }
            } : new Composition.Vertical(ThemePainting.metric(context(), form.kind() == Nodes.FormKind.SHEET
                    ? DesignMetrics.Role.SHEET_SECTION_GAP : DesignMetrics.Role.SECTION_GAP)));
            int footerTop = window ? ThemePainting.metric(context(), DesignMetrics.Role.WINDOW_FOOTER_VERTICAL) : hero ? ThemePainting.metric(context(), compact
                    ? DesignMetrics.Role.HERO_COMPACT_FOOTER_TOP : DesignMetrics.Role.HERO_FOOTER_TOP) - focusClearance : form.kind() == Nodes.FormKind.FULL ? ThemePainting.metric(context(), DesignMetrics.Role.FULL_FOOTER_TOP) : pad;
            footer.setBorder(new EmptyBorder(footerTop, side,
                    window ? ThemePainting.metric(context(), DesignMetrics.Role.WINDOW_FOOTER_VERTICAL) : hero ? ThemePainting.metric(context(), DesignMetrics.Role.HERO_FOOTER_BOTTOM) : pad, side));
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
            footerScroll.setBorder(new EmptyBorder(0, 0, 0, 0));
            footerScroll.getVerticalScrollBar().setUnitIncrement(ThemePainting.metric(context(), DesignMetrics.Role.ROW_GAP));
            footerScroll.getVerticalScrollBar().setBackground(ThemePainting.color(context(), SemanticColor.SURFACE_DIALOG));
            footerScroll.getVerticalScrollBar().setForeground(ThemePainting.color(context(), SemanticColor.BORDER_SUBTLE));
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
            focusReveal.entrySet().removeIf(entry -> {
                if (controls.contains(entry.getKey()) && f.actions().contains(entry.getKey().node)) return false;
                entry.getKey().button.removeFocusListener(entry.getValue());
                return true;
            });
            for(var control:controls)if(control instanceof Buttons.ButtonControl button && f.actions().contains(control.node)) {
                button.button.fullFooter=f.kind()==Nodes.FormKind.FULL;
                button.button.windowFooter=f.kind()==Nodes.FormKind.WINDOW;
                if (!focusReveal.containsKey(button)) {
                    var reveal = new java.awt.event.FocusAdapter() {
                        @Override public void focusGained(java.awt.event.FocusEvent event) {
                            if (!closed && !view.isClosed()) button.button.scrollRectToVisible(new Rectangle(0,0,button.button.getWidth(),button.button.getHeight()));
                        }
                    };
                    button.button.addFocusListener(reveal);
                    focusReveal.put(button, reveal);
                }
            }
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

    /** Stable footer ancestry; only its viewport overflows on screens shorter than the fixed actions. */
    static final class Footer extends JPanel implements Scrollable {
        boolean window;
        KitContext context;
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (window) {
                // The window footer band shares the sidebar surface below one full-width hairline.
                graphics.setColor(ThemePainting.color(context, SemanticColor.SURFACE_SIDEBAR));
                graphics.fillRect(0, 0, getWidth(), getHeight());
                graphics.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
                graphics.fillRect(0, 0, getWidth(), ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE));
            }
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle bounds, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle bounds, int orientation, int direction) { return Math.max(16, bounds.height - 16); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return getParent() != null && getPreferredSize().height <= getParent().getHeight(); }
    }

    static final class Body extends JPanel implements Scrollable {
        boolean fill;
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
            return fill && getParent()!=null && getPreferredSize().height<=getParent().getHeight();
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
            if (view.isClosed()) return new Dimension();
            var owner = (FormControl) view.control(getName());
            var f = (Nodes.Form) owner.node;
            int width =
                    ThemePainting.metric(
                            view.context(),
                            switch (f.kind()) {
                                case WINDOW -> DesignMetrics.Role.FORM_WIDTH;
                                case HERO -> DesignMetrics.Role.HERO_WIDTH;
                                case HERO_COMPACT -> DesignMetrics.Role.HERO_COMPACT_WIDTH;
                                case SHEET -> DesignMetrics.Role.SHEET_WIDTH;
                                default -> DesignMetrics.Role.FORM_WIDTH;
                            });
            if (f.kind() == Nodes.FormKind.WINDOW) width = owner.body.getPreferredSize().width;
            Insets insets=getInsets();
            width += insets.left+insets.right;
            int measuredWidth = Math.max(0,(getWidth() > 0 ? getWidth() : width)-insets.left-insets.right);
            int height =
                    Composition.height(owner.body, measuredWidth)
                            + Composition.height(owner.footer, measuredWidth)+insets.top+insets.bottom;
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
                    double stroke=ThemePainting.metric(view.context(),DesignMetrics.Role.HAIRLINE), half=stroke/2, radius=r;
                    // The sheet hangs from the owner's title edge: its fill reaches the top, its outline is sides and bottom only.
                    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    var outline = new Path2D.Double();
                    outline.moveTo(half,0);
                    outline.lineTo(half,getHeight()-radius);
                    outline.quadTo(half,getHeight()-half,radius,getHeight()-half);
                    outline.lineTo(getWidth()-radius,getHeight()-half);
                    outline.quadTo(getWidth()-half,getHeight()-half,getWidth()-half,getHeight()-radius);
                    outline.lineTo(getWidth()-half,0);
                    var path = new Path2D.Double(outline);path.closePath();
                    g.fill(path);g.setColor(ThemePainting.color(view.context(),SemanticColor.BORDER_WINDOW));
                    g.setStroke(new BasicStroke((float)stroke));g.draw(outline);
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
            // A technical preview (an equivalent command) keeps its one line beside the actions and shortens at its end,
            // as a window footer does; any other preview (a message) takes its own line above them when it does not fit.
            var form = (Nodes.Form) owner.node;
            boolean technical = form.preview().filter(n -> n instanceof Nodes.Text text && (text.role() == Nodes.TextRole.MONO_CAPTION
                || text.role() == Nodes.TextRole.MONO_SMALL || text.role() == Nodes.TextRole.MONO)).isPresent();
            boolean stacked =
                    owner.preview.isVisible()
                            && (form.kind() == Nodes.FormKind.WINDOW || technical
                                ? sharedPreviewWidth < ThemePainting.metric(owner.context(), DesignMetrics.Role.FORM_LABEL_WIDTH)
                                : owner.preview.getPreferredSize().width > sharedPreviewWidth);
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

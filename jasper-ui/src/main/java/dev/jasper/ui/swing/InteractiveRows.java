package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

/** Viewport-sized live row shells. Data and logical focus outlive offscreen widgets. */
final class InteractiveRows {
    private InteractiveRows() {}

    abstract static class RowsControl extends ControlRegistry.Control {
        final JScrollPane scroll;
        final Canvas canvas;
        final LinkedHashMap<String, Row> mounted = new LinkedHashMap<>();
        final Set<String> selected = new LinkedHashSet<>();
        Set<String> declaredSelection;
        String focusItem, focusControl;
        int rowHeight;
        boolean syncing;
        JComponent footer;
        final Menus.Popup popup;
        final List<Runnable> popupCleanup = new ArrayList<>();

        RowsControl(KitView view, Nodes.Node node) {
            super(view, node, new JScrollPane());
            scroll = (JScrollPane) component;
            canvas = new Canvas(this);
            scroll.setViewportView(canvas);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            popup = new Menus.Popup(context());
            javax.swing.event.ChangeListener listener = e -> sync();
            scroll.getViewport().addChangeListener(listener);
            own(() -> scroll.getViewport().removeChangeListener(listener));
            own(
                    () -> {
                        popup.setVisible(false);
                        clearPopup();
                    });
            bind(canvas, "DOWN", () -> move(1));
            bind(canvas, "UP", () -> move(-1));
            bind(
                    canvas,
                    "ENTER",
                    () -> {
                        if (focusItem != null) activate(focusItem);
                    });
            bind(
                    canvas,
                    "SPACE",
                    () -> {
                        if (focusItem != null) select(focusItem, multiple());
                    });
            bind(
                    canvas,
                    "CONTEXT_MENU",
                    () -> {
                        if (focusItem != null) context(focusItem, canvas, 0, 0);
                    });
        }

        abstract int size();

        abstract String itemId(int index);

        abstract Set<String> specifiedSelection();

        abstract boolean multiple();

        abstract String activation();

        abstract List<MenuItem> contextActions();

        abstract void populate(Row row, int index);

        abstract void data();

        int lines() {
            return 1;
        }

        int indexOf(String id) {
            for (int i = 0; i < size(); i++) if (itemId(i).equals(id)) return i;
            return -1;
        }

        @Override
        void refresh() {
            state();
            data();
            if (!Objects.equals(declaredSelection, specifiedSelection())) {
                selected.clear();
                selected.addAll(specifiedSelection());
                declaredSelection = specifiedSelection();
            }
            selected.removeIf(id -> indexOf(id) < 0);
            if (focusItem != null && indexOf(focusItem) < 0) {
                focusItem = null;
                focusControl = null;
            }
            Font font = ThemePainting.font(context(), Typography.Role.BODY);
            int line = canvas.getFontMetrics(font).getHeight();
            rowHeight =
                    Math.max(
                            ThemePainting.height(
                                    context(),
                                    DesignMetrics.Role.BUTTON_HEIGHT,
                                    Typography.Role.LABEL),
                            line * lines()
                                    + ThemePainting.metric(context(), DesignMetrics.Role.ROW_GAP));
            canvas.setBackground(ThemePainting.color(context(), SemanticColor.SURFACE_APP));
            scroll.getVerticalScrollBar().setUnitIncrement(rowHeight);
            canvas.revalidate();
            sync();
            canvas.repaint();
        }

        int footerHeight() {
            return footer == null || !footer.isVisible() ? 0 : footer.getPreferredSize().height;
        }

        void sync() {
            if (closed || syncing || rowHeight <= 0) return;
            syncing = true;
            try {
                int extent = scroll.getViewport().getExtentSize().height;
                int y = scroll.getViewport().getViewPosition().y;
                int first = Math.max(0, Math.min(size(), y / rowHeight) - 2);
                int last =
                        Math.min(size(), (y + Math.max(1, extent) + rowHeight - 1) / rowHeight + 2);
                var wanted = new LinkedHashSet<String>();
                for (int i = first; i < last; i++) wanted.add(itemId(i));
                var spare = new ArrayDeque<Row>();
                for (var it = mounted.entrySet().iterator(); it.hasNext(); ) {
                    var entry = it.next();
                    if (!wanted.contains(entry.getKey())) {
                        entry.getValue().release();
                        spare.add(entry.getValue());
                        it.remove();
                    }
                }
                int width = scroll.getViewport().getExtentSize().width;
                for (int i = first; i < last; i++) {
                    String id = itemId(i);
                    Row row = mounted.get(id);
                    if (row == null) {
                        row = spare.isEmpty() ? new Row(this) : spare.remove();
                        row.id = id;
                        mounted.put(id, row);
                        canvas.add(row);
                    }
                    row.index = i;
                    populate(row, i);
                    row.finish();
                    row.setBounds(0, i * rowHeight, Math.max(0, width), rowHeight);
                    row.doLayout();
                }
                for (var row : spare) canvas.remove(row);
                if (footer != null)
                    footer.setBounds(0, size() * rowHeight, Math.max(0, width), footerHeight());
                canvas.repaint();
            } finally {
                syncing = false;
            }
        }

        boolean stillCurrent(String item) {
            return !closed && indexOf(item) >= 0 && interactive();
        }

        void select(String id, boolean toggle) {
            if (!interactive() || indexOf(id) < 0) return;
            focusItem = id;
            focusControl = null;
            if (toggle && multiple()) {
                if (!selected.remove(id)) selected.add(id);
            } else {
                selected.clear();
                selected.add(id);
            }
            sync();
            canvas.repaint();
            emit(new UiEvent.Selected(node.id(), selected));
        }

        void activate(String id) {
            String action = activation();
            select(id, false);
            if (!action.isBlank() && stillCurrent(id) && action.equals(activation()))
                emit(new UiEvent.Action(action));
        }

        void move(int delta) {
            if (size() == 0 || !interactive()) return;
            int i =
                    Math.max(
                            0,
                            Math.min(
                                    size() - 1,
                                    (focusItem == null ? -1 : indexOf(focusItem)) + delta));
            String id = itemId(i);
            select(id, false);
            ensure(i);
            canvas.requestFocusInWindow();
        }

        void ensure(int index) {
            int y = index * rowHeight,
                    top = scroll.getViewport().getViewPosition().y,
                    h = scroll.getViewport().getExtentSize().height;
            if (y < top || y + rowHeight > top + h)
                scroll.getViewport()
                        .setViewPosition(
                                new Point(0, Math.max(0, y - Math.max(0, h - rowHeight) / 2)));
            sync();
        }

        ControlRegistry.Control inline(String id) {
            return null;
        }

        void context(String id, JComponent invoker, int x, int y) {
            var actions = contextActions();
            if (!interactive()) return;
            if (!selected.contains(id)) select(id, false);
            if (!stillCurrent(id) || !actions.equals(contextActions())) return;
            clearPopup();
            for (var item : contextActions()) {
                if (item.separator()) {
                    popup.add(new Menus.Separator(context()));
                    continue;
                }
                var entry = new Menus.Item(context());
                entry.setText(item.label());
                entry.putClientProperty("html.disable", true);
                entry.setEnabled(item.enabled());
                Composition.style(
                        entry, context(), Typography.Role.BODY, SemanticColor.TEXT_PRIMARY);
                int inset = ThemePainting.metric(context(), DesignMetrics.Role.MENU_ITEM_PADDING);
                entry.setBorder(BorderFactory.createEmptyBorder(0, inset, 0, inset));
                entry.setIcon(
                        item.icon().isBlank()
                                ? null
                                : context()
                                        .icons()
                                        .tile(
                                                item.icon(),
                                                context().theme().semantic(),
                                                Nodes.Hue.NEUTRAL,
                                                ThemePainting.metric(
                                                        context(), DesignMetrics.Role.MENU_TILE),
                                                false));
                ActionListener listener =
                        e -> {
                            emit(new UiEvent.Selected(node.id(), selected));
                            if (stillCurrent(id) && contextActions().contains(item))
                                emit(new UiEvent.Action(item.action()));
                        };
                entry.addActionListener(listener);
                popupCleanup.add(() -> entry.removeActionListener(listener));
                popup.add(entry);
            }
            if (popup.getComponentCount() > 0 && invoker.isShowing()) popup.show(invoker, x, y);
        }

        void clearPopup() {
            popupCleanup.forEach(Runnable::run);
            popupCleanup.clear();
            popup.removeAll();
        }

        boolean popupOpen() {
            if (popup.isVisible()) return true;
            for (var row : mounted.values())
                for (var control : row.controls.values()) {
                    if (control instanceof Menus.MenuControl menu && menu.popup.isVisible())
                        return true;
                    if (control instanceof Choices.ChoiceControl choice
                            && choice.input instanceof JComboBox<?> combo
                            && combo.isPopupVisible()) return true;
                }
            return false;
        }

        boolean dismiss() {
            boolean visible = popup.isVisible();
            popup.setVisible(false);
            for (var row : mounted.values())
                for (var c : row.controls.values())
                    if (c instanceof Menus.MenuControl menu) visible |= menu.dismiss();
                    else if (c instanceof Choices.ChoiceControl choice
                            && choice.input instanceof JComboBox<?> combo
                            && combo.isPopupVisible()) {
                        combo.hidePopup();
                        visible = true;
                    }
            return visible;
        }

        @Override
        JComponent focusTarget() {
            return canvas;
        }

        @Override
        public void close() {
            if (closed) return;
            super.close();
            mounted.values().forEach(Row::release);
            mounted.clear();
            canvas.removeAll();
            focusItem = null;
            focusControl = null;
        }
    }

    static final class Canvas extends JPanel implements Scrollable {
        final RowsControl owner;

        Canvas(RowsControl owner) {
            super(null);
            this.owner = owner;
            setOpaque(false);
            setFocusable(true);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(360, owner.size() * owner.rowHeight + owner.footerHeight());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(
                    360, Math.min(8, owner.size()) * owner.rowHeight + owner.footerHeight());
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return owner.rowHeight;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return Math.max(owner.rowHeight, r.height - owner.rowHeight);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        @Override
        public void doLayout() {
            owner.sync();
        }
    }

    static final class Row extends JPanel {
        final RowsControl owner;
        final Map<String, ControlRegistry.Control> controls = new LinkedHashMap<>();
        final Set<String> used = new HashSet<>();
        final JPanel leading = part(), title = part(), subtitle = part(), trailing = part();
        final JCheckBox check = new JCheckBox();
        String id;
        int index, indent;
        boolean hover;
        final java.util.List<JComponent> cells = new ArrayList<>();

        Row(RowsControl owner) {
            super(null);
            this.owner = owner;
            setOpaque(false);
            add(leading);
            add(title);
            add(subtitle);
            add(trailing);
            add(check);
            check.setOpaque(false);
            check.addActionListener(e -> owner.select(id, true));
            MouseAdapter mouse =
                    new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            hover = true;
                            repaint();
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            hover = false;
                            repaint();
                        }

                        @Override
                        public void mousePressed(MouseEvent e) {
                            if (e.isPopupTrigger()) popup(e);
                        }

                        @Override
                        public void mouseReleased(MouseEvent e) {
                            if (e.isPopupTrigger()) popup(e);
                        }

                        void popup(MouseEvent e) {
                            owner.context(id, Row.this, e.getX(), e.getY());
                        }

                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (!SwingUtilities.isLeftMouseButton(e)) return;
                            if (e.getClickCount() == 2) owner.activate(id);
                            else owner.select(id, e.isControlDown() || e.isMetaDown());
                            owner.canvas.requestFocusInWindow();
                        }
                    };
            addMouseListener(mouse);
            leading.addMouseListener(mouse);
            title.addMouseListener(mouse);
            subtitle.addMouseListener(mouse);
        }

        static JPanel part() {
            var p = new JPanel();
            p.setOpaque(false);
            return p;
        }

        void begin() {
            used.clear();
            cells.clear();
            indent = 0;
        }

        ControlRegistry.Control control(Nodes.Node node) {
            return control(node, owner.interactive());
        }

        ControlRegistry.Control control(Nodes.Node node, boolean parentEnabled) {
            used.add(node.id());
            var c = controls.get(node.id());
            if (c != null && !ControlRegistry.kind(c.node).equals(ControlRegistry.kind(node))) {
                c.close();
                controls.remove(node.id());
                c = null;
            }
            if (c == null) {
                c = ControlRegistry.create(owner.view, node);
                controls.put(node.id(), c);
                var target = c.focusTarget();
                String controlId = node.id();
                FocusListener listener =
                        new FocusAdapter() {
                            @Override
                            public void focusGained(FocusEvent e) {
                                owner.focusItem = id;
                                owner.focusControl = controlId;
                            }
                        };
                target.addFocusListener(listener);
                c.own(() -> target.removeFocusListener(listener));
                // Passive row content forwards pointer selection; interactive controls keep their
                // own
                // input.
                if (node instanceof Nodes.Text
                        || node instanceof Nodes.Icon
                        || node instanceof Nodes.Chip
                        || node instanceof Nodes.StatusDot) {
                    MouseAdapter mouse =
                            new MouseAdapter() {
                                @Override
                                public void mouseClicked(MouseEvent e) {
                                    if (SwingUtilities.isLeftMouseButton(e)) {
                                        if (e.getClickCount() == 2) owner.activate(id);
                                        else owner.select(id, e.isControlDown() || e.isMetaDown());
                                        owner.canvas.requestFocusInWindow();
                                    }
                                }

                                @Override
                                public void mousePressed(MouseEvent e) {
                                    if (e.isPopupTrigger())
                                        owner.context(
                                                id,
                                                (JComponent) e.getComponent(),
                                                e.getX(),
                                                e.getY());
                                }

                                @Override
                                public void mouseReleased(MouseEvent e) {
                                    if (e.isPopupTrigger())
                                        owner.context(
                                                id,
                                                (JComponent) e.getComponent(),
                                                e.getX(),
                                                e.getY());
                                }
                            };
                    c.component.addMouseListener(mouse);
                    var component = c.component;
                    c.own(() -> component.removeMouseListener(mouse));
                }
            }
            c.parentEnabled = parentEnabled;
            c.update(node);
            boolean enabled = c.interactive();
            var children =
                    ControlRegistry.children(node).stream()
                            .map(child -> control(child, enabled))
                            .toList();
            c.children(children);
            if (c.component instanceof JLabel label && node instanceof Nodes.Text text)
                label.putClientProperty(
                        "Jasper.leadingEllipsis",
                        text.role() == Nodes.TextRole.MONO
                                || text.role() == Nodes.TextRole.MONO_SMALL);
            return c;
        }

        void slot(JPanel panel, List<Nodes.Node> nodes) {
            panel.setLayout(new Navigation.InlineLayout(owner.context()));
            Stacks.mount(panel, nodes.stream().map(n -> control(n).component).toList());
        }

        void finish() {
            for (var it = controls.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (!used.contains(e.getKey())) {
                    e.getValue().close();
                    it.remove();
                }
            }
            check.setVisible(owner.multiple());
            check.setEnabled(owner.interactive());
            check.setSelected(owner.selected.contains(id));
            check.getAccessibleContext().setAccessibleName("Select " + id);
            if (Objects.equals(id, owner.focusItem) && owner.focusControl != null) {
                var target = controls.get(owner.focusControl);
                Component focused =
                        KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (target != null && (focused == owner.canvas || focused == null))
                    target.focusTarget().requestFocusInWindow();
            }
        }

        void release() {
            for (var control : controls.values()) {
                if (control.focusTarget().isFocusOwner()) {
                    owner.focusItem = id;
                    owner.focusControl = control.node.id();
                    owner.canvas.requestFocusInWindow();
                }
                if (control instanceof Choices.ChoiceControl choice
                        && choice.input instanceof JComboBox<?> combo) combo.hidePopup();
                control.close();
            }
            controls.clear();
            used.clear();
            cells.clear();
            leading.removeAll();
            title.removeAll();
            subtitle.removeAll();
            trailing.removeAll();
            hover = false;
        }

        @Override
        public void doLayout() {
            int pad = ThemePainting.metric(owner.context(), DesignMetrics.Role.ICON_GAP),
                    x = pad + indent,
                    width = Math.max(0, getWidth() - pad),
                    h = getHeight();
            int checkWidth =
                    check.isVisible()
                            ? Math.min(check.getPreferredSize().width, Math.max(0, width - x))
                            : 0;
            check.setBounds(x, 0, checkWidth, h);
            x += checkWidth;
            int tw = Math.min(trailing.getPreferredSize().width, Math.max(0, width - x));
            trailing.setBounds(width - tw, 0, tw, h);
            width = Math.max(x, width - tw - (tw > 0 ? pad : 0));
            int lw = Math.min(leading.getPreferredSize().width, Math.max(0, width - x));
            leading.setBounds(x, 0, lw, h);
            x += lw + (lw > 0 ? pad : 0);
            int content = Math.max(0, width - x);
            if (!cells.isEmpty()) {
                title.setBounds(x, 0, content, h);
                subtitle.setBounds(0, 0, 0, 0);
            } else if (subtitle.getComponentCount() > 0) {
                title.setBounds(x, 0, content, h / 2);
                subtitle.setBounds(x, h / 2, content, h - h / 2);
            } else {
                title.setBounds(x, 0, content, h);
                subtitle.setBounds(0, 0, 0, 0);
            }
            leading.doLayout();
            title.doLayout();
            subtitle.doLayout();
            trailing.doLayout();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (owner.selected.contains(id)) {
                g.setColor(ThemePainting.color(owner.context(), SemanticColor.SELECTION_BG));
                g.fillRect(0, 0, getWidth(), getHeight());
            } else if (hover) {
                g.setColor(
                        ColorMath.opacity(
                                ThemePainting.color(owner.context(), SemanticColor.HOVER_OVERLAY),
                                .05));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            if (Objects.equals(id, owner.focusItem) && owner.canvas.isFocusOwner()) {
                g.setColor(ThemePainting.color(owner.context(), SemanticColor.BORDER_FOCUS));
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            }
        }
    }

    static void bind(JComponent component, String key, Runnable action) {
        component.getInputMap().put(KeyStroke.getKeyStroke(key), key);
        component
                .getActionMap()
                .put(
                        key,
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent event) {
                                action.run();
                            }
                        });
    }
}

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
        boolean declaredMultiple;
        String focusItem, focusControl;
        int rowHeight;
        boolean syncing;
        JComponent footer;
        final Menus.Popup popup;
        final List<Runnable> popupCleanup = new ArrayList<>();
        /** The Shift-range origin; the last row chosen without Shift. */
        String anchor;
        /** A row the host selected, scrolled into view once the viewport has a size. */
        String pendingReveal;
        String typed = "";
        long typedAt = Long.MIN_VALUE;
        /** Row tops when rows differ in height (item-list headings in RESULTS lists); null while every row is rowHeight tall. */
        int[] tops;

        RowsControl(KitView view, Nodes.Node node) {
            this(view, node, new JScrollPane());
        }

        RowsControl(KitView view, Nodes.Node node, JScrollPane pane) {
            super(view, node, pane);
            scroll = (JScrollPane) component;
            if (pane instanceof OverlayScrollPane overlay) own(overlay::stop);
            canvas = new Canvas(this);
            scroll.setViewportView(canvas);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            popup = new Menus.Popup(context());
            scroll.putClientProperty(KitOwnership.CONTEXT_MENU, popup);
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
            bind(canvas, "shift DOWN", () -> extend(1));
            bind(canvas, "shift UP", () -> extend(-1));
            java.awt.event.KeyListener typing = new KeyAdapter() {
                @Override public void keyTyped(KeyEvent event) {
                    int menu = InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
                    if ((event.getModifiersEx() & menu) == 0) typeAhead(event.getKeyChar(), event.getWhen());
                }
            };
            canvas.addKeyListener(typing);
            own(() -> canvas.removeKeyListener(typing));
            // Enter activates the focused row; with no activate action it is not the rows' key and passes on to the host,
            // as a single-line field's Enter does without a trailing action.
            canvas.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "ENTER");
            canvas.getActionMap().put("ENTER", new AbstractAction() {
                @Override public boolean isEnabled() { return !closed && !activation().isBlank(); }
                @Override public void actionPerformed(ActionEvent event) { if (focusItem != null) activate(focusItem); }
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

        /** The context menu for one item; the control's own menu unless the item declares its own. */
        List<MenuItem> contextActions(String id) {
            return contextActions();
        }

        /** The text type-ahead matches for one item: its first column or title. */
        String itemText(int index) {
            return "";
        }

        abstract void populate(Row row, int index);

        abstract void data();

        int lines() {
            return 1;
        }

        /** Whether the item can be selected, activated, hovered or given a menu; item-list headings cannot. */
        boolean selectable(int index) {
            return true;
        }

        /** Whether every row is {@link #rowHeight} tall; otherwise {@link #rowHeightAt} sizes each row. */
        boolean uniformRows() {
            return true;
        }

        int rowHeightAt(int index) {
            return rowHeight;
        }

        /** How many selectable rows the viewport shows before it scrolls; absent keeps the default of eight rows. */
        OptionalInt visibleRows() {
            return OptionalInt.empty();
        }

        /** Whether the rows follow an external field: they never take focus, and a left press activates a row. */
        boolean externallyDriven() {
            return false;
        }

        /** One-line result rows: a fixed icon column, the title and detail inline, and a trailing tag that drops first. */
        boolean resultsLayout() {
            return false;
        }

        void measureRows() {
            if (uniformRows()) { tops = null; return; }
            int n = size();
            tops = new int[n + 1];
            for (int i = 0; i < n; i++) tops[i + 1] = tops[i] + rowHeightAt(i);
        }

        int top(int index) {
            return tops == null ? index * rowHeight : tops[Math.max(0, Math.min(index, tops.length - 1))];
        }

        int heightAt(int index) {
            return tops == null ? rowHeight : tops[index + 1] - tops[index];
        }

        int rowsHeight() {
            return top(size());
        }

        /** The row under {@code y} (clamped to {@code 0..size}). */
        int indexAt(int y) {
            if (tops == null) return rowHeight <= 0 ? 0 : Math.max(0, y) / rowHeight;
            int low = 0, high = size();
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                if (tops[middle] <= y) low = middle; else high = middle - 1;
            }
            return low;
        }

        /** The viewport's preferred height: {@link #visibleRows} selectable rows and the headings among them. */
        int viewportRowsHeight() {
            var visible = visibleRows();
            if (visible.isEmpty()) return top(Math.min(8, size()));
            int height = 0;
            for (int i = 0, rows = 0; i < size() && rows < visible.getAsInt(); i++) {
                height += heightAt(i);
                if (selectable(i)) rows++;
            }
            return height;
        }

        /** The row a key step lands on: the next selectable row that way, or the first one when none has focus yet. */
        int step(int delta) {
            int current = focusItem == null ? -1 : indexOf(focusItem);
            return current < 0 ? nextSelectable(-1, 1) : nextSelectable(current, Integer.signum(delta));
        }

        /** The next selectable index from {@code from} in {@code direction}, or {@code from} when there is none. */
        int nextSelectable(int from, int direction) {
            for (int i = from + direction; i >= 0 && i < size(); i += direction) if (selectable(i)) return i;
            return from;
        }

        int indexOf(String id) {
            for (int i = 0; i < size(); i++) if (itemId(i).equals(id)) return i;
            return -1;
        }

        boolean containsItem(String id) {
            return indexOf(id) >= 0;
        }

        int minimumRowHeight() {
            return 0;
        }

        @Override
        void refresh() {
            state();
            data();
            if (!Objects.equals(declaredSelection, specifiedSelection())
                    || declaredMultiple != multiple()) {
                // A selection the host made (not the echo of the user's own) is scrolled into view.
                if (!selected.equals(specifiedSelection()) && !specifiedSelection().isEmpty())
                    pendingReveal = specifiedSelection().stream().min(Comparator.comparingInt(this::indexOf)).orElse(null);
                selected.clear();
                selected.addAll(specifiedSelection());
                declaredSelection = specifiedSelection();
                declaredMultiple = multiple();
            }
            selected.removeIf(id -> !containsItem(id));
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
            rowHeight = Math.max(rowHeight, minimumRowHeight());
            measureRows();
            canvas.setFocusable(!externallyDriven());
            canvas.setBackground(ThemePainting.color(context(), SemanticColor.SURFACE_APP));
            scroll.getVerticalScrollBar().setUnitIncrement(rowHeight);
            canvas.revalidate();
            sync();
            canvas.repaint();
            for (var child : popup.getComponents()) if (child instanceof Menus.Item entry
                    && entry.getClientProperty("Jasper.menu.item") instanceof MenuItem item) {
                Composition.style(entry, context(), Typography.Role.BODY, SemanticColor.TEXT_PRIMARY);
                entry.setIcon(item.icon().isBlank() ? null : context().icons().tile(item.icon(),
                        context().theme().semantic(), item.hue(), ThemePainting.metric(context(), DesignMetrics.Role.MENU_TILE), false));
            }
            popup.revalidate(); popup.repaint();
        }

        int footerHeight() {
            return footer == null || !footer.isVisible() ? 0 : footer.getPreferredSize().height;
        }

        void sync() {
            if (closed || syncing || rowHeight <= 0) return;
            if (pendingReveal != null && scroll.getViewport().getExtentSize().height > 0) {
                int index = indexOf(pendingReveal);
                pendingReveal = null;
                if (index >= 0) position(index);
            }
            syncing = true;
            try {
                int extent = scroll.getViewport().getExtentSize().height;
                int y = scroll.getViewport().getViewPosition().y;
                int first, last;
                if (tops == null) {
                    first = Math.max(0, Math.min(size(), y / rowHeight) - 2);
                    last = Math.min(size(), (y + Math.max(1, extent) + rowHeight - 1) / rowHeight + 2);
                } else {
                    first = Math.max(0, indexAt(y) - 2);
                    last = Math.min(size(), indexAt(y + Math.max(1, extent) - 1) + 1 + 2);
                }
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
                    row.setBounds(0, top(i), Math.max(0, width), heightAt(i));
                    row.doLayout();
                }
                for (var row : spare) canvas.remove(row);
                if (footer != null)
                    footer.setBounds(0, rowsHeight(), Math.max(0, width), footerHeight());
                canvas.repaint();
            } finally {
                syncing = false;
            }
        }

        boolean stillCurrent(String item) {
            return !closed && indexOf(item) >= 0 && interactive();
        }

        void select(String id, boolean toggle) {
            if (!interactive() || indexOf(id) < 0 || !selectable(indexOf(id))) return;
            focusItem = id;
            focusControl = null;
            anchor = id;
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

        /** Whether a plain left single click also reports {@link UiEvent.Clicked} (tables and trees). */
        boolean reportsClicks() {
            return false;
        }

        /**
         * A left single click: selects the row (Ctrl/Meta toggles it); only a plain click, with no modifier, also
         * reports {@link UiEvent.Clicked} for tables and trees.
         */
        void click(String id, boolean toggle, boolean plain) {
            select(id, toggle);
            if (plain && reportsClicks() && stillCurrent(id)) emit(new UiEvent.Clicked(node.id(), id));
        }

        /** Pointer input shared by a row and its passive content; a click whose press opened the context menu is ignored. */
        void pressed(Row row, MouseEvent e) {
            row.contextPress = e.isPopupTrigger();
            if (row.contextPress) context(row.id, (JComponent) e.getComponent(), e.getX(), e.getY());
            // A list that follows an external field runs a row on a single left press, keeping the field's focus.
            else if (externallyDriven() && SwingUtilities.isLeftMouseButton(e)) activate(row.id);
        }

        void released(Row row, MouseEvent e) {
            if (!e.isPopupTrigger()) return;
            row.contextPress = true;
            context(row.id, (JComponent) e.getComponent(), e.getX(), e.getY());
        }

        void clicked(Row row, MouseEvent e) {
            boolean context = row.contextPress;
            row.contextPress = false;
            if (externallyDriven()) return;
            // macOS Ctrl+click is the popup trigger on press yet arrives as a BUTTON1 click: never a selection.
            if (context || !SwingUtilities.isLeftMouseButton(e)) return;
            int modifiers = InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK
                    | InputEvent.ALT_GRAPH_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
            if (e.getClickCount() == 2) activate(row.id);
            else if (e.isShiftDown()) range(row.id);
            else click(row.id, e.isControlDown() || e.isMetaDown(), (e.getModifiersEx() & modifiers) == 0);
            canvas.requestFocusInWindow();
        }

        /** Shift-click: with multiple selection, every row from the anchor through {@code id}; otherwise a plain select. */
        void range(String id) {
            if (!multiple() || anchor == null || indexOf(anchor) < 0) { select(id, false); return; }
            if (!interactive() || indexOf(id) < 0) return;
            int from = indexOf(anchor), to = indexOf(id);
            selected.clear();
            for (int i = Math.min(from, to); i <= Math.max(from, to); i++) if (selectable(i)) selected.add(itemId(i));
            focusItem = id;
            focusControl = null;
            sync();
            canvas.repaint();
            emit(new UiEvent.Selected(node.id(), selected));
        }

        void extend(int delta) {
            if (!multiple()) { move(delta); return; }
            if (size() == 0 || !interactive()) return;
            int i = step(delta);
            if (i < 0) return;
            if (anchor == null) anchor = itemId(i);
            range(itemId(i));
            ensure(i);
            canvas.requestFocusInWindow();
        }

        /** Selects the first item whose text starts with what was typed within the type-ahead window. */
        void typeAhead(char c, long when) {
            if (Character.isISOControl(c) || size() == 0 || !interactive()) return;
            long window = context().metrics().value(DesignMetrics.Role.TYPE_AHEAD_MILLIS);
            typed = typedAt != Long.MIN_VALUE && when - typedAt <= window ? typed + c : String.valueOf(c);
            typedAt = when;
            String prefix = typed.toLowerCase(Locale.ROOT);
            for (int i = 0; i < size(); i++)
                if (selectable(i) && itemText(i).toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    select(itemId(i), false);
                    ensure(i);
                    return;
                }
        }

        void activate(String id) {
            if (indexOf(id) < 0 || !selectable(indexOf(id))) return;
            String action = activation();
            select(id, false);
            if (!action.isBlank() && stillCurrent(id) && action.equals(activation()))
                emit(new UiEvent.Action(action));
        }

        void move(int delta) {
            if (size() == 0 || !interactive()) return;
            int i = step(delta);
            if (i < 0) return;
            String id = itemId(i);
            select(id, false);
            ensure(i);
            canvas.requestFocusInWindow();
        }

        void ensure(int index) {
            position(index);
            sync();
        }

        void position(int index) {
            int y = top(index), height = heightAt(index),
                    top = scroll.getViewport().getViewPosition().y,
                    h = scroll.getViewport().getExtentSize().height;
            if (y < top || y + height > top + h)
                scroll.getViewport()
                        .setViewPosition(
                                new Point(0, Math.max(0, y - Math.max(0, h - height) / 2)));
        }

        ControlRegistry.Control inline(String id) {
            return null;
        }

        /** Whether the row declares its own menu (which names it), rather than sharing the control's. */
        boolean ownsContextActions(String id) {
            return false;
        }

        /**
         * A context press targets the row under the pointer and never changes the selection. A row's own menu is
         * offered on any row; the control's shared menu acts on the selection, so it is offered only on a selected row.
         */
        void context(String id, JComponent invoker, int x, int y) {
            if (!interactive()) return;
            clearPopup();
            if (!stillCurrent(id) || !selectable(indexOf(id)) || !selected.contains(id) && !ownsContextActions(id)) return;
            var actions = contextActions(id);
            for (var item : actions) {
                if (item.separator()) {
                    popup.add(new Menus.Separator(context()));
                    continue;
                }
                var entry = new Menus.Item(context());
                entry.setText(item.label());
                entry.putClientProperty("Jasper.menu.item", item);
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
                                                item.hue(),
                                                ThemePainting.metric(
                                                        context(), DesignMetrics.Role.MENU_TILE),
                                                false));
                ActionListener listener =
                        e -> {
                            // A row's menu acts on that row (its action names it); it never re-reports the
                            // selection, so choosing an item cannot also open or navigate to the row.
                            if (stillCurrent(id) && contextActions(id).contains(item))
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

        @Override protected void paintComponent(Graphics graphics) {
            if (owner.node instanceof Nodes.ItemList list && list.kind() == Nodes.ListKind.ORDERED) {
                var g = (Graphics2D)graphics.create();
                try {
                    ThemePainting.antialias(g);
                    int radius = ThemePainting.metric(owner.context(), DesignMetrics.Role.ORDERED_RADIUS);
                    g.setColor(ThemePainting.color(owner.context(), SemanticColor.SURFACE_INPUT));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
                    g.setColor(ThemePainting.color(owner.context(), SemanticColor.BORDER_INPUT));
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius * 2, radius * 2);
                    g.setColor(ThemePainting.color(owner.context(), SemanticColor.BORDER_SUBTLE));
                    for (int i = 1; i <= owner.size(); i++) g.drawLine(1, i * owner.rowHeight, getWidth() - 2, i * owner.rowHeight);
                } finally { g.dispose(); }
            }
        }
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(360, owner.rowsHeight() + owner.footerHeight());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(360, owner.viewportRowsHeight() + owner.footerHeight());
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
        /** The last press or release opened the context menu, so its click is not a selection. */
        boolean contextPress;
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
                            owner.pressed(Row.this, e);
                        }

                        @Override
                        public void mouseReleased(MouseEvent e) {
                            owner.released(Row.this, e);
                        }

                        @Override
                        public void mouseClicked(MouseEvent e) {
                            owner.clicked(Row.this, e);
                        }
                    };
            addMouseListener(mouse);
            leading.addMouseListener(mouse);
            title.addMouseListener(mouse);
            subtitle.addMouseListener(mouse);
        }

        @Override
        public JToolTip createToolTip() {
            var tip = super.createToolTip();
            tip.putClientProperty("html.disable", true);
            return tip;
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
                                    owner.clicked(Row.this, e);
                                }

                                @Override
                                public void mousePressed(MouseEvent e) {
                                    owner.pressed(Row.this, e);
                                }

                                @Override
                                public void mouseReleased(MouseEvent e) {
                                    owner.released(Row.this, e);
                                }
                            };
                    c.component.addMouseListener(mouse);
                    var component = c.component;
                    c.own(() -> component.removeMouseListener(mouse));
                }
            }
            c.parentEnabled = parentEnabled;
            c.embeddedLabel(true);
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
            check.setVisible(owner.multiple() && owner.selectable(index));
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
            setToolTipText(null);
            getAccessibleContext().setAccessibleDescription(null);
            leading.removeAll();
            title.removeAll();
            subtitle.removeAll();
            trailing.removeAll();
            hover = false;
            contextPress = false;
        }

        @Override
        public void doLayout() {
            if (!owner.selectable(index)) { headingLayout(); return; }
            if (owner.resultsLayout()) { resultLayout(); return; }
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
                int titleHeight = title.getPreferredSize().height,
                        subtitleHeight = subtitle.getPreferredSize().height;
                int extra = Math.max(0, h - titleHeight - subtitleHeight);
                int split = Math.min(h, titleHeight + extra / 2);
                title.setBounds(x, 0, content, split);
                subtitle.setBounds(x, split, content, h - split);
            } else {
                title.setBounds(x, 0, content, h);
                subtitle.setBounds(0, 0, 0, 0);
            }
            leading.doLayout();
            title.doLayout();
            subtitle.doLayout();
            trailing.doLayout();
        }

        /** A heading shows only its title, inset like the rows below it. */
        void headingLayout() {
            int pad = ThemePainting.metric(owner.context(), owner.resultsLayout() ? DesignMetrics.Role.RESULTS_ROW_PADDING : DesignMetrics.Role.ICON_GAP);
            check.setBounds(0, 0, 0, 0);
            leading.setBounds(0, 0, 0, 0);
            subtitle.setBounds(0, 0, 0, 0);
            trailing.setBounds(0, 0, 0, 0);
            title.setBounds(pad, 0, Math.max(0, getWidth() - 2 * pad), getHeight());
            title.doLayout();
        }

        /**
         * One line: the icon column, the title, the detail after it and the tag at the end. When space runs out the tag
         * goes first, then the detail and finally the title are cut short. The tag shows only when the title, the detail
         * (if any) and the tag all fit, so it never claims room the detail needs.
         */
        void resultLayout() {
            var context = owner.context();
            int side = ThemePainting.metric(context, DesignMetrics.Role.RESULTS_ROW_PADDING),
                    gap = ThemePainting.metric(context, DesignMetrics.Role.RESULTS_ROW_GAP),
                    icon = ThemePainting.metric(context, DesignMetrics.Role.ICON_RAIL), h = getHeight();
            check.setBounds(0, 0, 0, 0);
            leading.setBounds(side, 0, icon, h);
            int start = side + icon + gap, end = getWidth() - side;
            int titleWidth = title.getPreferredSize().width;
            int tagWidth = trailing.getComponentCount() == 0 ? 0 : trailing.getPreferredSize().width;
            int detailWidth = subtitle.getComponentCount() == 0 ? 0 : subtitle.getPreferredSize().width;
            int detailTerm = detailWidth > 0 ? gap + detailWidth : 0;
            boolean showTag = tagWidth > 0 && start + titleWidth + detailTerm + gap + tagWidth <= end;
            int textEnd = showTag ? end - tagWidth - gap : end;
            trailing.setBounds(showTag ? end - tagWidth : 0, 0, showTag ? tagWidth : 0, h);
            int shownTitle = Math.max(0, Math.min(titleWidth, textEnd - start));
            title.setBounds(start, 0, shownTitle, h);
            int detailStart = start + shownTitle + gap;
            subtitle.setBounds(detailStart, 0, Math.max(0, Math.min(detailWidth, textEnd - detailStart)), h);
            leading.doLayout();
            title.doLayout();
            subtitle.doLayout();
            trailing.doLayout();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!owner.selectable(index)) {
                // A heading after other rows opens its section with a hairline rule.
                if (index > 0) {
                    g.setColor(ThemePainting.color(owner.context(), SemanticColor.BORDER_SUBTLE));
                    g.fillRect(0, 0, getWidth(), ThemePainting.metric(owner.context(), DesignMetrics.Role.HAIRLINE));
                }
                return;
            }
            if (owner.resultsLayout()) {
                Color fill = owner.selected.contains(id) ? ThemePainting.color(owner.context(), SemanticColor.SELECTION_BG)
                        : hover ? ColorMath.opacity(ThemePainting.color(owner.context(), SemanticColor.HOVER_OVERLAY), .05) : null;
                if (fill != null) {
                    var rounded = (Graphics2D) g.create();
                    try {
                        ThemePainting.antialias(rounded);
                        int inset = ThemePainting.metric(owner.context(), DesignMetrics.Role.RESULTS_SELECTION_INSET),
                                arc = ThemePainting.metric(owner.context(), DesignMetrics.Role.RESULTS_SELECTION_ARC);
                        rounded.setColor(fill);
                        rounded.fillRoundRect(inset, 0, Math.max(0, getWidth() - 2 * inset), getHeight(), arc, arc);
                    } finally { rounded.dispose(); }
                }
                return;
            }
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

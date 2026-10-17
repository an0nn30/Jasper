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
        boolean declaredMultiple, declaredPassive;
        String focusItem, focusControl;
        int rowHeight;
        /** Logical inset of this list's rows from its edges; the owner moved its own side inset here (spec 2). */
        int gutter;
        void gutter(int logical) { if (gutter != logical) { gutter = logical; sync(); } }
        /** Whether row {@code index} shows the selection; an ordered list's shows only while it has focus. */
        boolean selectionShown(int index) {
            String id = itemId(index);
            if (!dataRow(index) || passive() || !selected.contains(id)) return false;
            if (this instanceof Trees.TreeControl tree && tree.detail != null && tree.detail.itemId.equals(id)) return false;
            return !(node instanceof Nodes.ItemList list && list.kind() == Nodes.ListKind.ORDERED) || canvas.isFocusOwner();
        }
        int column = -1;
        boolean panelSingle;
        /** A passive list among a DOCUMENT split's bars (command-line output): rows are text lines, not list rows. */
        boolean documentLines;
        /** A panel body's two-line rows (Tunnels, File Servers): compact height, inset rounded selection; set by the reconciler. */
        boolean panelCompact;
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
            FocusListener focusPaint=new FocusAdapter() {
                @Override public void focusGained(FocusEvent e){
                    // Tab or Shift+Tab onto the list means the list, not the row control a recycle would hand focus back to.
                    if (switch (e.getCause()) { case TRAVERSAL, TRAVERSAL_FORWARD, TRAVERSAL_BACKWARD -> true; default -> false; })
                        focusControl = null;
                    sync();canvas.repaint();
                }
                @Override public void focusLost(FocusEvent e){sync();canvas.repaint();}
            };
            canvas.addFocusListener(focusPaint);
            own(() -> canvas.removeFocusListener(focusPaint));
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
            // Tables only: a context press outside every row (below the rows, or anywhere in an empty table) opens the
            // control's shared menu, which acts on the selection; it never changes the selection, as the JTable's
            // component popup did.
            MouseAdapter outside = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) outsideContext((JComponent) e.getComponent(), e.getX(), e.getY()); }
                @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) outsideContext((JComponent) e.getComponent(), e.getX(), e.getY()); }
            };
            canvas.addMouseListener(outside);
            scroll.getViewport().addMouseListener(outside);
            own(() -> { canvas.removeMouseListener(outside); scroll.getViewport().removeMouseListener(outside); });
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
            // Enter activates the focused row (or runs a list's own Enter action); with neither it is not the rows' key and
            // passes on to the host, as a single-line field's Enter does without a trailing action.
            canvas.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "ENTER");
            canvas.getActionMap().put("ENTER", new AbstractAction() {
                // While the rows are not interactive Enter is not theirs and passes on; so it does without a focused row,
                // except in a table, which keeps Enter as the JTable it replaced did.
                @Override public boolean isEnabled() {
                    return !closed && (focusItem != null || keepsEnterWithoutFocus()) && rowInteractive() && !enterKey().isBlank();
                }
                @Override public void actionPerformed(ActionEvent event) { if (focusItem != null) enter(focusItem); }
            });
            // Delete and Backspace report a collection's delete action for the focused row; without one they pass on.
            for (String stroke : List.of("DELETE", "BACK_SPACE")) {
                canvas.getInputMap().put(KeyStroke.getKeyStroke(stroke), "delete");
            }
            canvas.getActionMap().put("delete", new AbstractAction() {
                @Override public boolean isEnabled() { return !closed && focusItem != null && rowInteractive() && !deletion().isBlank(); }
                @Override public void actionPerformed(ActionEvent event) { if (focusItem != null) delete(focusItem); }
            });
            bind(
                    canvas,
                    "SPACE",
                    () -> {
                        if (focusItem != null) space(focusItem);
                    });
            // The menu key and Shift+F10 both open the focused row's menu, as platform lists do.
            for (String stroke : List.of("CONTEXT_MENU", "shift F10"))
                bind(
                        canvas,
                        stroke,
                        () -> {
                            if (focusItem != null) context(focusItem, canvas, 0, 0);
                        });
        }

        abstract int size();

        /** Space on the focused row: it selects the row unless a collection gives the key another meaning. */
        void space(String id) {
            select(id, multiple());
        }

        abstract String itemId(int index);

        abstract Set<String> specifiedSelection();

        abstract boolean multiple();

        /** Selection capability is separate from enabled state so passive rows retain full-opacity inline controls. */
        boolean passive() {
            return node instanceof Nodes.ItemList list && list.selectionMode() == Nodes.SelectionMode.NONE
                || node instanceof Nodes.Table table && table.selectionMode() == Nodes.SelectionMode.NONE;
        }
        boolean rowInteractive() { return interactive() && !passive(); }

        abstract String activation();

        abstract List<MenuItem> contextActions();

        /** Whether Enter with no focused row stays with the rows (doing nothing) instead of passing on; tables only. */
        boolean keepsEnterWithoutFocus() {
            return false;
        }

        /** What Enter on the focused row reports; the activate action unless a collection gives Enter its own. */
        String enterKey() {
            return activation();
        }

        /** Enter on the focused row. */
        void enter(String id) {
            activate(id);
        }

        /** What Delete and Backspace on the focused row report; blank leaves the keys to the host. */
        String deletion() {
            return "";
        }

        /** Delete or Backspace on the focused row: an unselected row is selected first, then the delete action runs. */
        void delete(String id) {
            if (indexOf(id) < 0 || !selectable(indexOf(id)) || !interactive()) return;
            String action = deletion();
            if (!selected.contains(id)) select(id, false);
            if (!action.isBlank() && stillCurrent(id) && action.equals(deletion())) emit(new UiEvent.Action(action));
        }

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

        /** A data row counts toward visibleRows even when its selection mode is NONE; headings do not. */
        boolean dataRow(int index) { return true; }

        /** Whether a row can be selected, activated, hovered or given a menu. */
        boolean selectable(int index) {
            return !passive() && dataRow(index);
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

        /** A selected row's fill and the ink of what it holds: the accent fill in a results layout, the selection fill elsewhere. */
        Ink ink() {
            return resultsLayout() ? Ink.ACCENT : Ink.SELECTION;
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

        /**
         * The viewport's preferred height: {@link #visibleRows} selectable rows and the headings among them. A list that
         * {@link #keepsVisibleHeight keeps its height} reserves the missing rows at its row height, so it does not shrink
         * (and a host-packed dialog around it does not re-pack) when it has fewer rows.
         */
        int viewportRowsHeight() {
            var visible = visibleRows();
            if (visible.isEmpty()) return top(Math.min(8, size()));
            int height = 0, rows = 0;
            for (int i = 0; i < size() && rows < visible.getAsInt(); i++) {
                height += heightAt(i);
                if (dataRow(i)) rows++;
            }
            if (keepsVisibleHeight() && rows < visible.getAsInt()) height += (visible.getAsInt() - rows) * reservedRowHeight();
            return height;
        }

        /** Height reserved for each missing selectable row; variable-height lists may use their data-row metric. */
        int reservedRowHeight() {
            return rowHeight;
        }

        /** Whether the viewport keeps its {@link #visibleRows} height however few rows it has; none by default. */
        boolean keepsVisibleHeight() {
            return false;
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

        boolean identityRows() { return false; }

        int minimumRowHeight() {
            return 0;
        }

        @Override
        void refresh() {
            state();
            data();
            if (!Objects.equals(declaredSelection, specifiedSelection())
                    || declaredMultiple != multiple() || declaredPassive != passive()) {
                // A selection the host made (not the echo of the user's own) is scrolled into view, and the arrow keys
                // and Space continue from it, as from a selection the user made.
                if (!selected.equals(specifiedSelection()) && !specifiedSelection().isEmpty()) {
                    pendingReveal = specifiedSelection().stream().min(Comparator.comparingInt(this::indexOf)).orElse(null);
                    if (pendingReveal != null && !Objects.equals(focusItem, pendingReveal)) { focusItem = pendingReveal; focusControl = null; }
                }
                // Entering passive mode revokes selection-driven scrolling; later explicit reveals still work.
                if (passive()) pendingReveal = null;
                selected.clear();
                selected.addAll(specifiedSelection());
                declaredSelection = specifiedSelection();
                declaredMultiple = multiple();
                declaredPassive = passive();
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
                            // A row's lines take the one-line row padding (as menus and navigation do), not a form's row gap.
                            line * lines()
                                    + ThemePainting.metric(context(), DesignMetrics.Role.ROW_VERTICAL_PADDING));
            // Manager columns: one-line rows (the rail's, a list column's single-line rows) take the rail row, two-line list rows the item row.
            if (column == 0 || column == 1) rowHeight = Math.max(rowHeight,
                ThemePainting.metric(context(), column == 0 || lines() < 2 ? DesignMetrics.Role.COLUMN_RAIL_ROW : DesignMetrics.Role.COLUMN_ITEM_ROW));
            rowHeight = Math.max(rowHeight, minimumRowHeight());
            if (documentLines) rowHeight = Math.max(line, canvas.getFontMetrics(ThemePainting.font(context(), Typography.Role.MONO)).getHeight())
                + ThemePainting.metric(context(), DesignMetrics.Role.DOCUMENT_BAR_LINE_GAP);
            if (panelCompact) rowHeight = Math.max(ThemePainting.metric(context(), DesignMetrics.Role.PANEL_COMPACT_ROW_HEIGHT), compactRowHeight());
            measureRows();
            emptyLine();
            canvas.setFocusable(!externallyDriven() && !passive());
            if (passive()) { focusItem = null; focusControl = null; anchor = null; }
            canvas.setBackground(ThemePainting.color(context(), column == 0 ? SemanticColor.SURFACE_SIDEBAR : SemanticColor.SURFACE_APP));
            scroll.getVerticalScrollBar().setUnitIncrement(rowHeight);
            canvas.revalidate();
            sync();
            canvas.repaint();
            for (var child : popup.getComponents()) if (child instanceof Menus.Item entry
                    && entry.getClientProperty("Jasper.menu.item") instanceof MenuItem item) entry.describe(item);
            popup.restyle();
            popup.revalidate(); popup.repaint();
        }

        /** The content height a compact panel row needs at the current fonts (its slots stacked plus their vertical padding). */
        int compactRowHeight() { return 0; }

        int footerHeight() {
            return footer == null || !footer.isVisible() ? 0 : footer.getPreferredSize().height;
        }

        /** The muted line an empty collection shows in its body (ItemList.emptyText, Table.emptyText); null until first set. */
        JLabel emptyLine;

        /** The text an empty collection shows in its body; blank shows none. */
        String emptyText() {
            return "";
        }

        /** Whether the canvas fills the viewport's height, as an empty table's state does; never by default. */
        boolean fillsViewport() {
            return false;
        }

        /** The empty line's band: one row tall while it shows. */
        int emptyLineHeight() {
            return emptyLine != null && emptyLine.isVisible() ? rowHeight : 0;
        }

        /** Shows the empty line while the collection has no items and a non-blank empty text; created on first use. */
        void emptyLine() {
            String text = emptyText();
            if (emptyLine == null && text.isBlank()) return;
            if (emptyLine == null) {
                emptyLine = Labels.literal();
                emptyLine.setHorizontalAlignment(SwingConstants.CENTER);
                KitOwnership.mark(emptyLine, "@rows/" + node.id() + "/empty");
                canvas.add(emptyLine);
            }
            emptyLine.setText(text);
            emptyLine.setFont(ThemePainting.font(context(), Typography.Role.DESCRIPTION));
            emptyLine.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_TERTIARY));
            emptyLine.setVisible(size() == 0 && !text.isBlank());
        }

        int rowShellHeight(int index) {return heightAt(index);}
        void layoutAccessories(int width) { }

        void sync() {
            if (closed || syncing || rowHeight <= 0) return;
            if (pendingReveal != null && scroll.getViewport().getExtentSize().height > 0) {
                int index = indexOf(pendingReveal);
                pendingReveal = null;
                if (index >= 0) position(index);
            }
            syncing = true;
            try {
                measureRows();
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
                int width = scroll.getViewport().getExtentSize().width, inset = com.formdev.flatlaf.util.UIScale.scale(gutter);
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
                    row.inked = selectionShown(i);
                    populate(row, i);
                    row.finish();
                    row.setBounds(inset, top(i), Math.max(0, width - 2 * inset), rowShellHeight(i));
                    row.doLayout();
                }
                for (var row : spare) canvas.remove(row);
                if (emptyLine != null)
                    emptyLine.setBounds(inset, rowsHeight(), Math.max(0, width - 2 * inset), emptyLineHeight());
                if (footer != null)
                    footer.setBounds(inset, rowsHeight() + emptyLineHeight(), Math.max(0, width - 2 * inset), footerHeight());
                layoutAccessories(width);
                canvas.repaint();
            } finally {
                syncing = false;
            }
        }

        boolean stillCurrent(String item) {
            return !closed && indexOf(item) >= 0 && rowInteractive();
        }

        void select(String id, boolean toggle) {
            if (!rowInteractive() || indexOf(id) < 0 || !selectable(indexOf(id))) return;
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
            if (plain && reportsClicks() && stillCurrent(id) && selectable(indexOf(id))) emit(new UiEvent.Clicked(node.id(), id));
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
            if (!rowInteractive()) return;
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
            if (!rowInteractive() || indexOf(id) < 0) return;
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
            if (size() == 0 || !rowInteractive()) return;
            int i = step(delta);
            if (i < 0) return;
            if (anchor == null) anchor = itemId(i);
            range(itemId(i));
            ensure(i);
            canvas.requestFocusInWindow();
        }

        /** Selects the first item whose text starts with what was typed within the type-ahead window. */
        void typeAhead(char c, long when) {
            if (Character.isISOControl(c) || size() == 0 || !rowInteractive()) return;
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
            if (size() == 0 || !rowInteractive()) return;
            int i = step(delta);
            if (i < 0) return;
            String id = itemId(i);
            select(id, false);
            ensure(i);
            canvas.requestFocusInWindow();
        }

        /**
         * An external field's arrow keys (Field.listId): moves the keyboard row {@code steps} selectable rows, clamped to
         * the ends, selects it and keeps it in view, leaving the keyboard focus where it is.
         */
        void follow(int steps) {
            if (size() == 0 || !rowInteractive() || steps == 0) return;
            int current = focusItem == null ? -1 : indexOf(focusItem);
            int i = current < 0 ? nextSelectable(-1, 1) : current;
            for (int k = current < 0 ? 1 : 0; k < Math.abs(steps); k++) i = nextSelectable(i, Integer.signum(steps));
            if (i < 0 || !selectable(i)) return;
            select(itemId(i), false);
            ensure(i);
        }

        /** How many rows the viewport shows, for Page Up and Page Down; at least one. */
        int page() {
            int extent = scroll.getViewport().getExtentSize().height;
            return Math.max(1, rowHeight <= 0 ? 1 : extent / rowHeight);
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

        boolean revealInline(String id) { return false; }

        ControlRegistry.Control inline(String id) {
            return null;
        }

        /** Whether the row declares its own menu (which names it), rather than sharing the control's. */
        boolean ownsContextActions(String id) {
            return false;
        }

        /** Whether a context press outside every row opens the control's shared menu (tables only). */
        boolean sharedMenuOutsideRows() {
            return false;
        }

        /** A context press outside every row: the shared menu, acting on the selection, which it never changes. */
        void outsideContext(JComponent invoker, int x, int y) {
            if (!sharedMenuOutsideRows() || !interactive()) return;
            clearPopup();
            showMenu(contextActions(), item -> contextActions().contains(item), invoker, x, y);
        }

        /**
         * A context press targets the row under the pointer and never changes the selection. A row's own menu is
         * offered on any row; the control's shared menu acts on the selection, so it is offered only on a selected row.
         */
        void context(String id, JComponent invoker, int x, int y) {
            if (!interactive()) return;
            clearPopup();
            if (!stillCurrent(id) || !selectable(indexOf(id)) || !selected.contains(id) && !ownsContextActions(id)) return;
            // A row's menu acts on that row (its action names it); it never re-reports the selection, so choosing an
            // item cannot also open or navigate to the row.
            showMenu(contextActions(id), item -> stillCurrent(id) && contextActions(id).contains(item), invoker, x, y);
        }

        /** Fills and shows the popup; a chosen item reports only its action, and only while {@code current} still holds. */
        void showMenu(List<MenuItem> actions, java.util.function.Predicate<MenuItem> current, JComponent invoker, int x, int y) {
            for (var item : actions) {
                if (item.separator()) {
                    popup.add(new Menus.Separator(context()));
                    continue;
                }
                var entry = new Menus.Item(context());
                entry.describe(item);
                ActionListener listener =
                        e -> {
                            if (current.test(item)) emit(new UiEvent.Action(item.action()));
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
            if(owner instanceof Trees.TreeControl tree && tree.detail!=null) {
                int index=tree.indexOf(tree.detail.itemId);
                if(index>=0) {
                    var g=(Graphics2D)graphics.create();
                    try {
                        ThemePainting.antialias(g);
                        int inset=ThemePainting.metric(owner.context(),DesignMetrics.Role.KEYCAP_DETAIL_INSET),radius=ThemePainting.metric(owner.context(),DesignMetrics.Role.CONTROL_RADIUS)*2;
                        g.setColor(ThemePainting.color(owner.context(),SemanticColor.SURFACE_CONTROL));
                        int card=Math.max(0,tree.heightAt(index)-tree.detailAfter());
                        g.fillRoundRect(inset,tree.top(index),Math.max(0,getWidth()-2*inset),card,radius,radius);
                        g.setColor(ThemePainting.color(owner.context(),SemanticColor.BORDER_FOCUS));
                        g.drawRoundRect(inset,tree.top(index),Math.max(0,getWidth()-2*inset-1),Math.max(0,card-1),radius,radius);
                    }finally{g.dispose();}
                }
            }
            if (owner.node instanceof Nodes.ItemList list && list.kind() == Nodes.ListKind.ORDERED) {
                var g = (Graphics2D)graphics.create();
                try {
                    ThemePainting.rounded(g, this, owner.context(), ThemePainting.color(owner.context(), SemanticColor.SURFACE_INPUT),
                        ThemePainting.color(owner.context(), SemanticColor.BORDER_INPUT), false, false, DesignMetrics.Role.ORDERED_RADIUS);
                    g.setColor(ThemePainting.color(owner.context(), SemanticColor.BORDER_SUBTLE));
                    int stroke = ThemePainting.metric(owner.context(), DesignMetrics.Role.HAIRLINE);
                    for (int i = 1; i <= owner.size(); i++) g.fillRect(stroke, i * owner.rowHeight, Math.max(0, getWidth() - 2 * stroke), stroke);
                } finally { g.dispose(); }
            }
            // Selection and hover fill each row's whole band, edge to edge of the list, under the row's inset content.
            for (var row : owner.mounted.values()) row.paintFill(graphics, getWidth());
        }
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(360, owner.rowsHeight() + owner.emptyLineHeight() + owner.footerHeight());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(360, Math.max(owner.viewportRowsHeight(), owner.emptyLineHeight()) + owner.footerHeight());
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
            // An empty table's state takes the whole body below the headers; otherwise the rows keep their own height.
            return owner.fillsViewport() && getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
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
        String id;
        /** The last press or release opened the context menu, so its click is not a selection. */
        boolean contextPress;
        int index, indent;
        boolean hover;
        /** Whether this row shows the selection: its kit text and glyphs take the on-accent roles (set before populate). */
        boolean inked;

        /** This row's selection or hover fill across the list's full {@code width}, in the canvas's coordinates. */
        void paintFill(Graphics graphics, int width) {
            if (!owner.dataRow(index) || owner.passive()) return;
            if (owner instanceof Trees.TreeControl tree && tree.detail != null && tree.detail.itemId.equals(id)) return;
            boolean focusedOnly = Objects.equals(id, owner.focusItem) && owner.canvas.isFocusOwner() && !owner.selected.contains(id);
            Color fill = owner.selectionShown(index)
                ? ThemePainting.color(owner.context(), owner.ink().fill)
                : hover || focusedOnly ? ColorMath.opacity(ThemePainting.color(owner.context(), SemanticColor.HOVER_OVERLAY), .05) : null;
            if (fill == null) return;
            graphics.setColor(fill);
            graphics.fillRect(0, getY(), width, getHeight());
        }

        /** The fill lies on the canvas across the list's width, so a row's repaint redraws its whole band. */
        @Override public void repaint(long tm, int x, int y, int width, int height) {
            if (owner != null && getParent() == owner.canvas) owner.canvas.repaint(tm, 0, getY() + y, owner.canvas.getWidth(), height);
            else super.repaint(tm, x, y, width, height);
        }
        final java.util.List<JComponent> cells = new ArrayList<>();
        /**
         * Hover follows the pointer over the whole row. Text with a tooltip, icons and switches take their own mouse
         * events, so crossing into one is an exit from the slot beneath it; only an exit to a point outside the row ends
         * the hover. The row, its slots and every control mounted in it share this tracker.
         */
        final MouseAdapter hoverTracker = hoverTracker();
        /** The same tracking for mounted controls, as its own listener: the row's pointer handlers never sit on a control. */
        final MouseAdapter controlHover = hoverTracker();
        private MouseAdapter hoverTracker() {
            return new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover(true); }
                @Override public void mouseExited(MouseEvent e) {
                    hover(contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), Row.this)));
                }
            };
        }
        void hover(boolean value) { if (hover == value) return; hover = value; repaint(); }
        /** The row's pointer selection: the row, its slots and the content that forwards to it pass presses, releases and clicks to the owner. */
        final MouseAdapter pointer = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { owner.pressed(Row.this, e); }
            @Override public void mouseReleased(MouseEvent e) { owner.released(Row.this, e); }
            @Override public void mouseClicked(MouseEvent e) { owner.clicked(Row.this, e); }
        };

        /** Passes the pointer over {@code control}'s own component to the row, once however often the row is populated. */
        void forwardPointer(ControlRegistry.Control control) {
            var component = control.component;
            for (var listener : component.getMouseListeners()) if (listener == pointer) return;
            component.addMouseListener(pointer);
            control.own(() -> component.removeMouseListener(pointer));
        }

        Row(RowsControl owner) {
            super(null);
            this.owner = owner;
            setOpaque(false);
            add(leading);
            add(title);
            add(subtitle);
            add(trailing);
            addMouseListener(pointer);
            leading.addMouseListener(pointer);
            title.addMouseListener(pointer);
            subtitle.addMouseListener(pointer);
            // The trailing slot listens too, so its empty area shows the row tooltip and still reaches the row.
            trailing.addMouseListener(pointer);
            for (var part : List.of(this, leading, title, subtitle, trailing)) part.addMouseListener(hoverTracker);
        }

        @Override
        public JToolTip createToolTip() {
            var tip = super.createToolTip();
            tip.putClientProperty("html.disable", true);
            return tip;
        }

        /** A row slot; its tooltip (the row's, over the slot's empty area) is literal text. */
        static JPanel part() {
            var p = new JPanel() {
                @Override
                public JToolTip createToolTip() {
                    var tip = super.createToolTip();
                    tip.putClientProperty("html.disable", true);
                    return tip;
                }
            };
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
                                Row.this.repaint();
                            }
                            @Override public void focusLost(FocusEvent e){Row.this.repaint();}
                        };
                target.addFocusListener(listener);
                c.own(() -> target.removeFocusListener(listener));
                // Every mounted control keeps the row hovered while the pointer is over it, passive or interactive.
                var hovered = c.component;
                hovered.addMouseListener(controlHover);
                c.own(() -> hovered.removeMouseListener(controlHover));
                // Passive row content forwards pointer selection; interactive controls keep their
                // own
                // input.
                if (node instanceof Nodes.Text
                        || node instanceof Nodes.Icon
                        || node instanceof Nodes.Chip
                        || node instanceof Nodes.StatusDot) forwardPointer(c);
            }
            c.parentEnabled = parentEnabled;
            c.embeddedLabel(true);
            var ink = owner.ink();
            if (c instanceof Labels.TextControl text) { text.inked = inked; text.ink = ink; }
            else if (c instanceof Labels.IconControl icon) { icon.inked = inked; icon.ink = ink; }
            else if (c instanceof Buttons.ButtonControl button) { button.button.onSelection = inked; button.button.ink = ink; }
            else if (c instanceof Switches.ToggleControl toggle) { toggle.button.onSelection = inked; toggle.button.ink = ink; }
            else if (c instanceof Indicators.ChipControl chip) { chip.inked = inked; chip.ink = ink; }
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
                                || text.role() == Nodes.TextRole.MONO_SMALL || text.role() == Nodes.TextRole.MONO_CAPTION);
            return c;
        }

        /**
         * The row's accessible name: its title text (Text and Chip labels, joined), as a list cell's name was its text.
         * Assistive technology reads the name first and the tooltip as the description.
         */
        void name(List<Nodes.Node> titleNodes) {
            var parts = new ArrayList<String>();
            for (var n : titleNodes) {
                if (n instanceof Nodes.Text text && !text.text().isBlank()) parts.add(text.text());
                else if (n instanceof Nodes.Chip chip && !chip.label().isBlank()) parts.add(chip.label());
            }
            getAccessibleContext().setAccessibleName(parts.isEmpty() ? null : String.join(", ", parts));
        }

        /**
         * The row's literal tooltip and accessible description over the row and its passive content; interactive controls
         * keep their own tooltips, and an icon without a row tooltip shows its accessible name again.
         */
        void tooltip(String text) {
            String tooltip = text == null || text.isBlank() ? null : text;
            setToolTipText(tooltip);
            getAccessibleContext().setAccessibleDescription(tooltip);
            // The slots cover the row, so they show it too: anywhere on the row, as a list row's tooltip did.
            for (var part : List.of(leading, title, subtitle, trailing)) part.setToolTipText(tooltip);
            for (var c : controls.values()) {
                if (!(c.component instanceof JComponent component)) continue;
                switch (c.node) {
                    case Nodes.Text _, Nodes.Chip _, Nodes.StatusDot _ -> component.setToolTipText(tooltip);
                    case Nodes.Icon icon -> component.setToolTipText(tooltip == null ? icon.accessibleName() : tooltip);
                    default -> { }
                }
            }
        }

        boolean ordered() { return owner.node instanceof Nodes.ItemList list && list.kind() == Nodes.ListKind.ORDERED; }

        void slot(JPanel panel, List<Nodes.Node> nodes) {
            var gap = owner.panelSingle && panel == title ? DesignMetrics.Role.PANEL_ROW_TITLE_GAP
                : ordered() && panel == title ? DesignMetrics.Role.INLINE_GAP : ordered() && panel == trailing ? DesignMetrics.Role.ORDERED_ACTION_GAP : DesignMetrics.Role.ICON_GAP;
            // A compact panel row's name keeps its width before the detail beside it (via host, a problem) is cut short.
            panel.setLayout(owner.panelCompact && panel == title ? new Navigation.InlineLayout(owner.context(), gap) { @Override boolean leadFirst() { return true; } }
                : new Navigation.InlineLayout(owner.context(), gap));
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
            getAccessibleContext().setAccessibleName(null);
            getAccessibleContext().setAccessibleDescription(null);
            for (var part : List.of(leading, title, subtitle, trailing)) part.setToolTipText(null);
            leading.removeAll();
            title.removeAll();
            subtitle.removeAll();
            trailing.removeAll();
            hover = false;
            contextPress = false;
        }

        @Override
        public void doLayout() {
            if (!owner.dataRow(index)) { headingLayout(); return; }
            if (owner.resultsLayout()) { resultLayout(); return; }
            if (owner instanceof Trees.TreeControl tree && tree.layoutPresented(this)) return;
            boolean settingsNavigation=owner instanceof Trees.TreeControl tree && tree.settingsNavigation;
            if (owner.panelCompact && owner.column < 0 && !settingsNavigation && !owner.identityRows()) { compactLayout(); return; }
            boolean ordered = ordered();
            boolean keycapGroup=owner instanceof Trees.TreeControl keyed && !keyed.presentation.isEmpty();
            int pad = ThemePainting.metric(owner.context(), settingsNavigation ? DesignMetrics.Role.SETTINGS_NAV_ROW_SIDE : keycapGroup ? DesignMetrics.Role.KEYCAP_GROUP_SIDE : owner.identityRows() ? DesignMetrics.Role.IDENTITY_ROW_SIDE : owner.column >= 0 ? DesignMetrics.Role.COLUMN_LIST_SIDE : owner.panelSingle ? DesignMetrics.Role.PANEL_ROW_PADDING : DesignMetrics.Role.ICON_GAP),
                    // An ordered row's index column starts at the row inset; its commands keep the narrower trailing pad.
                    x = (ordered ? ThemePainting.metric(owner.context(), DesignMetrics.Role.ORDERED_ROW_SIDE) : pad) + indent,
                    // A keycap tree's section counts end where its shortcut keys end; an identity row's switch sits a
                    // little closer to the row's edge than its tile.
                    width = Math.max(0, getWidth() - (keycapGroup
                        ? ThemePainting.metric(owner.context(), DesignMetrics.Role.KEYCAP_CONTENT_TRAILING)
                        : owner.identityRows() ? ThemePainting.metric(owner.context(), DesignMetrics.Role.IDENTITY_ROW_TRAILING) : pad)),
                    h = getHeight();
            int tw = Math.min(trailing.getPreferredSize().width, Math.max(0, width - x));
            int actionInset=owner.panelSingle && owner.column < 0 ? ThemePainting.metric(owner.context(),DesignMetrics.Role.PANEL_ROW_ACTION_INSET) : 0;
            trailing.setBounds(Math.max(x,width - tw - actionInset), 0, tw, h);
            width = Math.max(x, width - tw - (tw > 0 ? pad : 0));
            int lw = Math.min(leading.getPreferredSize().width, Math.max(0, width - x));
            leading.setBounds(x, 0, lw, h);
            x += lw + (lw > 0 ? ordered ? ThemePainting.metric(owner.context(),DesignMetrics.Role.ORDERED_TEXT_GAP) : owner.column == 0 || owner.column == 1 ? ThemePainting.metric(owner.context(),DesignMetrics.Role.COLUMN_TEXT_GAP) : keycapGroup ? ThemePainting.metric(owner.context(),DesignMetrics.Role.KEYCAP_GROUP_TEXT_GAP) : owner.identityRows() ? ThemePainting.metric(owner.context(),DesignMetrics.Role.IDENTITY_ROW_TEXT_GAP) : owner.panelSingle ? ThemePainting.metric(owner.context(),DesignMetrics.Role.PANEL_ROW_TEXT_GAP) : pad : 0);
            int content = Math.max(0, width - x);
            if (!cells.isEmpty()) {
                title.setBounds(x, 0, content, h);
                subtitle.setBounds(0, 0, 0, 0);
            } else if (subtitle.getComponentCount() > 0) {
                int titleHeight = title.getPreferredSize().height,
                        subtitleHeight = subtitle.getPreferredSize().height;
                if (owner.column == 1 || owner.identityRows()) {
                    int top = Math.max(0, (h - titleHeight - subtitleHeight) / 2);
                    title.setBounds(x, top, content, titleHeight);
                    subtitle.setBounds(x, top + titleHeight, content, subtitleHeight);
                } else {
                int extra = Math.max(0, h - titleHeight - subtitleHeight);
                int split = Math.min(h, titleHeight + extra / 2);
                title.setBounds(x, 0, content, split);
                subtitle.setBounds(x, split, content, h - split);
                }
            } else {
                title.setBounds(x, 0, content, h);
                subtitle.setBounds(0, 0, 0, 0);
            }
            leading.doLayout();
            title.doLayout();
            subtitle.doLayout();
            trailing.doLayout();
        }

        /**
         * A compact panel row: the leading status dot or glyph, then the title over the subtitle as one block centered in
         * the row, and the trailing controls at the row's end. A glyph sits nearer the edge than a dot, as its artwork
         * carries its own side bearing.
         */
        void compactLayout() {
            var context = owner.context();
            boolean dot = leading.getComponentCount() > 0 && leading.getComponent(0) instanceof Indicators.Dot;
            int h = getHeight(), x = ThemePainting.metric(context, dot ? DesignMetrics.Role.PANEL_ROW_PADDING : DesignMetrics.Role.PANEL_COMPACT_ROW_PADDING) + indent;
            // The trailing inset is measured to a switch's painted track; its focus halo extends into the inset.
            boolean halo = trailing.getComponentCount() > 0 && trailing.getComponent(trailing.getComponentCount() - 1) instanceof Switches.SwitchButton;
            int end = getWidth() - ThemePainting.metric(context, DesignMetrics.Role.PANEL_COMPACT_ROW_TRAILING)
                + (halo ? ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING) : 0);
            int tw = Math.min(trailing.getPreferredSize().width, Math.max(0, end - x));
            trailing.setBounds(Math.max(x, end - tw), 0, tw, h);
            int gap = ThemePainting.metric(context, DesignMetrics.Role.PANEL_ROW_TEXT_GAP);
            int textEnd = Math.max(x, end - tw - (tw > 0 ? gap : 0));
            int lw = Math.min(leading.getPreferredSize().width, Math.max(0, textEnd - x));
            leading.setBounds(x, 0, lw, h);
            x += lw + (lw > 0 ? gap : 0);
            int content = Math.max(0, textEnd - x);
            // The two lines sit at the title font's ascent-plus-descent pitch, without its leading, as the reference rows do.
            int titleHeight = title.getPreferredSize().height, subtitleHeight = subtitle.getComponentCount() > 0 ? subtitle.getPreferredSize().height : 0;
            if (subtitleHeight > 0 && title.getComponentCount() > 0) {
                var metrics = title.getComponent(0).getFontMetrics(title.getComponent(0).getFont());
                titleHeight = Math.min(titleHeight, metrics.getAscent() + metrics.getDescent());
            }
            int top = Math.max(0, (h - titleHeight - subtitleHeight) / 2);
            title.setBounds(x, top, content, titleHeight);
            subtitle.setBounds(x, top + titleHeight, subtitleHeight > 0 ? content : 0, subtitleHeight);
            leading.doLayout();
            title.doLayout();
            subtitle.doLayout();
            trailing.doLayout();
        }

        /** A heading shows its title, inset like the rows below it, and a table heading's count at the row's end. */
        void headingLayout() {
            int pad = ThemePainting.metric(owner.context(), owner.resultsLayout() ? DesignMetrics.Role.RESULTS_ROW_PADDING : owner.identityRows() ? DesignMetrics.Role.IDENTITY_ROW_SIDE : DesignMetrics.Role.ICON_GAP);
            leading.setBounds(0, 0, 0, 0);
            subtitle.setBounds(0, 0, 0, 0);
            // An identity list's heading sits low in its band, close to the rows it introduces.
            int line = owner.identityRows() ? Math.min(getHeight(), title.getPreferredSize().height) : getHeight();
            int top = owner.identityRows() ? Math.max(0, getHeight() - line - ThemePainting.metric(owner.context(), DesignMetrics.Role.IDENTITY_HEADING_BOTTOM)) : 0;
            // Only a table heading has trailing content (its count); a list heading has a title only.
            int tw = trailing.getComponentCount() == 0 ? 0 : Math.min(trailing.getPreferredSize().width, Math.max(0, getWidth() - 2 * pad));
            if (tw == 0) trailing.setBounds(0, 0, 0, 0);
            else trailing.setBounds(getWidth() - pad - tw, top, tw, line);
            title.setBounds(pad, top, Math.max(0, getWidth() - 2 * pad - (tw > 0 ? tw + pad : 0)), line);
            title.doLayout();
            trailing.doLayout();
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
            if (!owner.dataRow(index)) {
                // A heading after other rows opens its section with a hairline rule: a row divider, or the menu's edge in the
                // palette's results (spec 7).
                if (index > 0) {
                    g.setColor(ThemePainting.color(owner.context(), owner.resultsLayout() ? SemanticColor.BORDER_MENU : SemanticColor.BORDER_SUBTLE));
                    g.fillRect(0, 0, getWidth(), ThemePainting.metric(owner.context(), DesignMetrics.Role.HAIRLINE));
                }
                return;
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

    /**
     * A selected fill and the ink of what lies on it: the accent fill takes the on-accent text roles, the selection fill
     * the on-selection ones. A theme may pair either fill with dark or light text, so content on a selected fill takes
     * its fill's roles.
     */
    enum Ink {
        ACCENT(SemanticColor.ACCENT, SemanticColor.TEXT_ONACCENT, SemanticColor.TEXT_ONACCENT_SECONDARY),
        SELECTION(SemanticColor.SELECTION_BG, SemanticColor.TEXT_ONSELECTION, SemanticColor.TEXT_ONSELECTION_SECONDARY);

        /** The fill. */
        final SemanticColor fill;
        /** Text and glyphs on the fill. */
        final SemanticColor text;
        /** Supporting text on the fill. */
        final SemanticColor supporting;

        Ink(SemanticColor fill, SemanticColor text, SemanticColor supporting) {
            this.fill = fill;
            this.text = text;
            this.supporting = supporting;
        }
    }
}

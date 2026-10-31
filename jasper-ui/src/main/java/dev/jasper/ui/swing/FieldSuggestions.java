package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.LookAndFeel;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * A field's suggestions ({@link Nodes.Field#suggestions()}): a list under the field's box and as wide as it, the first
 * suggestion selected, with {@link #LOADING} after the suggestions while {@link Nodes.Field#suggestionsLoading()} holds.
 * The list never takes the keyboard focus, so typing goes on in the field. While it shows suggestions, Up and Down move
 * the selection, Tab and Enter report {@link UiEvent.SuggestionChosen} for the selected one (a click on a suggestion
 * reports it as Enter does), and Tab is no focus traversal key; while it shows at all, Escape hides it and reports the
 * field's cancel action. The pointer on a row only highlights it, leaving the selection (plan 4b's P16). A choice, Escape, the focus leaving the field for good and a press outside the list hide it
 * until the person next edits the field; an update keeps the selection while its suggestion is still listed, and an edit
 * selects the first again. A key listener reads the keys before the field's own bindings, so while the list is hidden
 * the field's keys are exactly its own. The field makes it the first time it has suggestions. EDT only.
 */
final class FieldSuggestions implements AutoCloseable {
    /** The row after the suggestions while more load. */
    static final String LOADING = "Loading suggestions…";
    /** The most rows the list shows before it scrolls: as many as a dropdown's list shows. */
    static final int VISIBLE_ROWS = 8;
    private static final AWTKeyStroke TAB = AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, 0);
    private static final int MODIFIERS = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
        | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

    private final TextFields.FieldControl owner;
    private final DefaultListModel<Object> rows = new DefaultListModel<>();
    /** The rows: the field's suggestions, then {@link #LOADING} while more load. */
    final JList<Object> list = new JList<>(rows);
    private final JScrollPane scroller = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    final Popup popup = new Popup();
    /** The suggestion the person moved the selection to; null for the first. */
    private String selectedId;
    /** The suggestion row the pointer rests on, highlighted; -1 for none. */
    private int hovered = -1;
    /** Hidden until the person next edits the field: by a choice, Escape, the focus leaving for good or a press outside. */
    private boolean dismissed;
    /** Whether Tab is taken from the field's focus traversal keys, and the keys it had set before (null: inherited). */
    private boolean tabTaken;
    private Set<AWTKeyStroke> traversal;
    /** The look and feel the popup's delegates were made with: the popup is outside every window while hidden. */
    private LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
    /** Where the popup was last shown, in the field box's coordinates; null while it is hidden. */
    private Point placedAt;
    private EmptyBorder rowBorder;
    private int detailGap;
    private boolean closed;

    private final KeyListener keys = new KeyAdapter() {
        @Override public void keyPressed(KeyEvent event) {
            var field = field();
            if (event.isConsumed() || owner.composing || (event.getModifiersEx() & MODIFIERS) != 0 || !shows(field)) return;
            boolean offers = !field.suggestions().isEmpty();
            switch (event.getKeyCode()) {
                case KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> { if (!offers) return; move(field, -1); }
                case KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> { if (!offers) return; move(field, 1); }
                case KeyEvent.VK_TAB -> { if (!offers) return; choose(field, true); }
                case KeyEvent.VK_ENTER -> { if (!offers) return; choose(field, false); }
                case KeyEvent.VK_ESCAPE -> escape(field);
                default -> { return; }
            }
            event.consume();
        }
    };
    private final FocusListener focus = new FocusAdapter() {
        @Override public void focusGained(FocusEvent event) { sync(); }
        /**
         * A temporary loss keeps the list, which comes back with the focus; a window that goes behind another closes the
         * popup itself, as Swing closes any open popup then ({@code canceled}), until the next edit.
         */
        @Override public void focusLost(FocusEvent event) { if (!event.isTemporary()) dismissed = true; sync(); }
    };
    /** The pointer on the list's rows. */
    final MouseAdapter pointer = new MouseAdapter() {
        @Override public void mouseMoved(MouseEvent event) { point(event); }
        @Override public void mouseDragged(MouseEvent event) { point(event); }
        @Override public void mousePressed(MouseEvent event) { point(event); }
        @Override public void mouseExited(MouseEvent event) { hover(-1); }
        @Override public void mouseReleased(MouseEvent event) {
            var field = field();
            int row = row(event.getPoint());
            if (!SwingUtilities.isLeftMouseButton(event) || row < 0 || !shows(field)) return;
            selectedId = field.suggestions().get(row).id();
            choose(field, false);
        }
        /**
         * The pointer highlights the suggestion it rests on and leaves the selection, which the keyboard moves (P16); the
         * list's own press handling never selects another row.
         */
        private void point(MouseEvent event) {
            var field = field();
            hover(shows(field) ? row(event.getPoint()) : -1);
            selectList(field);
        }
    };
    /** A press outside the list closes it as it closes a menu: until the next edit. The popup is closing itself. */
    private final PopupMenuListener canceled = new PopupMenuListener() {
        @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) {}
        @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { placedAt = null; }
        @Override public void popupMenuCanceled(PopupMenuEvent event) { dismissed = true; syncState(field()); }
    };

    FieldSuggestions(TextFields.FieldControl owner) {
        this.owner = owner;
        list.setFocusable(false); list.setOpaque(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Row());
        // Rows take the list's width, so a long title is shortened with an ellipsis instead of widening the list.
        list.setFixedCellWidth(1);
        list.getAccessibleContext().setAccessibleName("Suggestions");
        KitOwnership.mark(list, owner.node.id() + ".suggestions");
        list.addMouseListener(pointer); list.addMouseMotionListener(pointer);
        scroller.setBorder(new EmptyBorder(0, 0, 0, 0)); scroller.setOpaque(false); scroller.getViewport().setOpaque(false);
        scroller.setFocusable(false); scroller.getVerticalScrollBar().setFocusable(false);
        popup.add(scroller, BorderLayout.CENTER);
        popup.addPopupMenuListener(canceled);
        owner.input.addKeyListener(keys); owner.input.addFocusListener(focus);
    }

    private Nodes.Field field() { return (Nodes.Field) owner.node; }

    /** Whether the list shows: the field takes input, has suggestions or loads them, and nothing has hidden the list. */
    private boolean shows(Nodes.Field field) {
        return !closed && !dismissed && owner.interactive() && (field.suggestionsLoading() || !field.suggestions().isEmpty());
    }

    /** The selected suggestion's row: the one the person moved to while it is listed, else the first; -1 without any. */
    private int selectedIndex(Nodes.Field field) {
        var suggestions = field.suggestions();
        if (suggestions.isEmpty()) return -1;
        for (int row = 0; row < suggestions.size(); row++) if (suggestions.get(row).id().equals(selectedId)) return row;
        return 0;
    }

    /** The suggestion row at {@code point} in the list, or -1: the loading row, or no row. */
    private int row(Point point) {
        int row = list.locationToIndex(point);
        if (row < 0 || row >= field().suggestions().size()) return -1;
        var bounds = list.getCellBounds(row, row);
        return bounds != null && bounds.contains(point) ? row : -1;
    }

    /** Highlights suggestion row {@code row}, or none for -1. */
    private void hover(int row) {
        if (row == hovered) return;
        hovered = row;
        list.repaint();
    }

    /** The field's descriptor changed: a selection whose suggestion is gone falls back to the first. */
    void refresh() {
        var field = field();
        if (selectedId != null && field.suggestions().stream().noneMatch(suggestion -> suggestion.id().equals(selectedId))) selectedId = null;
        restyle();
        sync();
    }

    /** The person edited the field: the list shows again, the first suggestion selected. */
    void edited() {
        dismissed = false;
        selectedId = null;
        sync();
    }

    private void move(Nodes.Field field, int delta) {
        var suggestions = field.suggestions();
        selectedId = suggestions.get(Math.max(0, Math.min(suggestions.size() - 1, selectedIndex(field) + delta))).id();
        selectList(field);
    }

    private void choose(Nodes.Field field, boolean byTab) {
        var chosen = field.suggestions().get(selectedIndex(field));
        dismissed = true;
        sync();
        owner.emit(new UiEvent.SuggestionChosen(field.id(), chosen.id(), byTab));
    }

    private void escape(Nodes.Field field) {
        dismissed = true;
        sync();
        if (!field.cancelAction().isBlank()) owner.emit(new UiEvent.Action(field.cancelAction()));
    }

    private void sync() {
        var field = field();
        syncState(field);
        if (shows(field) && owner.input.isShowing() && owner.input.isFocusOwner()) place(); else hide();
    }

    /** The rows, the selection, Tab and the published list follow the field; the popup follows separately. */
    private void syncState(Nodes.Field field) {
        boolean shows = shows(field);
        var wanted = new ArrayList<Object>(field.suggestions());
        if (field.suggestionsLoading()) wanted.add(LOADING);
        if (!Collections.list(rows.elements()).equals(wanted)) { rows.clear(); rows.addAll(wanted); hovered = -1; }
        selectList(field);
        takeTab(shows && !field.suggestions().isEmpty());
        var published = shows ? list : null;
        if (owner.input.getClientProperty(KitOwnership.SUGGESTIONS) != published) owner.input.putClientProperty(KitOwnership.SUGGESTIONS, published);
    }

    /** The list's own selection mirrors the selected suggestion, for assistive technology; the rows paint from the field. */
    private void selectList(Nodes.Field field) {
        int row = selectedIndex(field);
        if (row < 0) list.clearSelection();
        else if (list.getSelectedIndex() != row) { list.setSelectedIndex(row); list.ensureIndexIsVisible(row); }
        list.repaint();
    }

    /**
     * Tab chooses a suggestion while the list offers one, so it leaves the field's forward traversal keys (Ctrl+Tab stays,
     * and Shift+Tab still moves back); the keys come back as they were when the list goes.
     */
    private void takeTab(boolean take) {
        if (take == tabTaken) return;
        tabTaken = take;
        var input = owner.input;
        int forward = KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS;
        if (take) {
            traversal = input.areFocusTraversalKeysSet(forward) ? input.getFocusTraversalKeys(forward) : null;
            var keys = new HashSet<AWTKeyStroke>(input.getFocusTraversalKeys(forward));
            keys.remove(TAB);
            input.setFocusTraversalKeys(forward, keys);
        } else {
            input.setFocusTraversalKeys(forward, traversal);
            traversal = null;
        }
    }

    /** The list under the field's box, or above it when the window has no room below; while another menu is open, nowhere. */
    private void place() {
        if (!popup.isVisible() && MenuSelectionManager.defaultManager().getSelectedPath().length > 0) return;
        if (UIManager.getLookAndFeel() != lookAndFeel) { lookAndFeel = UIManager.getLookAndFeel(); SwingUtilities.updateComponentTreeUI(popup); restyle(); }
        list.setVisibleRowCount(Math.max(1, Math.min(rows.size(), VISIBLE_ROWS)));
        var surface = owner.surface;
        Container host = SwingUtilities.getRootPane(surface);
        if (host == null) host = owner.view;
        var size = popup.getPreferredSize();
        var anchor = SwingUtilities.convertRectangle(surface.getParent(), surface.getBounds(), host);
        var at = SwingUtilities.convertPoint(host, Menus.anchor(anchor, size, new Rectangle(0, 0, host.getWidth(), host.getHeight())), surface);
        if (popup.isVisible() && size.equals(popup.getSize()) && at.equals(placedAt)) return;
        if (popup.isVisible() && !size.equals(popup.getSize())) popup.pack();
        popup.show(surface, at.x, at.y);
        placedAt = at;
    }

    private void hide() {
        placedAt = null;
        if (!popup.isVisible()) return;
        var manager = MenuSelectionManager.defaultManager();
        if (Arrays.asList(manager.getSelectedPath()).contains(popup)) manager.clearSelectedPath();
        else popup.setVisible(false);
    }

    /** The menu's card, edge and row height, and the rows' insets, from the field's context. */
    private void restyle() {
        var context = owner.context();
        int padding = ThemePainting.metric(context, DesignMetrics.Role.MENU_PADDING), line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
        var edge = new Insets(padding, line, padding, line);
        if (!(popup.getBorder() instanceof EmptyBorder border && border.getBorderInsets().equals(edge))) popup.setBorder(new EmptyBorder(edge));
        int side = ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
        if (rowBorder == null || rowBorder.getBorderInsets().left != side) rowBorder = new EmptyBorder(0, side, 0, side);
        detailGap = ThemePainting.metric(context, DesignMetrics.Role.MENU_SHORTCUT_GAP);
        list.setFixedCellHeight(ThemePainting.height(context, DesignMetrics.Role.MENU_ITEM_HEIGHT, Typography.Role.BODY));
        list.repaint();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        hide();
        takeTab(false);
        owner.input.putClientProperty(KitOwnership.SUGGESTIONS, null);
        owner.input.removeKeyListener(keys); owner.input.removeFocusListener(focus);
        list.removeMouseListener(pointer); list.removeMouseMotionListener(pointer);
        popup.removePopupMenuListener(canceled);
        rows.clear();
    }

    /** The list's card: the menu surface and edge, as wide as the field's box (a menu's least width at narrowest). */
    final class Popup extends JPopupMenu {
        Popup() { setLayout(new BorderLayout()); setFocusable(false); setOpaque(false); }
        @Override public Dimension getPreferredSize() {
            var size = super.getPreferredSize();
            return new Dimension(Math.max(owner.surface.getWidth(), ThemePainting.metric(owner.context(), DesignMetrics.Role.MENU_MIN_WIDTH)), size.height);
        }
        @Override protected void paintComponent(Graphics graphics) { Menus.paintSurface(this, graphics, owner.context()); }
    }

    /**
     * A row: the suggestion's title, shortened with an ellipsis when it does not fit, then its detail, quieter; the
     * loading line alone, quieter still. The selected suggestion's row is filled as a dropdown's highlighted option is,
     * and the one under the pointer, unless selected, as a hovered list row is.
     */
    final class Row extends JPanel implements ListCellRenderer<Object> {
        final JLabel title = Labels.literal(), detail = Labels.literal();
        /** Whether the row being painted is the selected suggestion's, and whether it is the one under the pointer. */
        boolean selected, hovered;
        Row() { super(new BorderLayout()); setOpaque(false); add(title, BorderLayout.CENTER); add(detail, BorderLayout.EAST); }
        @Override public Component getListCellRendererComponent(JList<?> source, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            var context = owner.context();
            var suggestion = value instanceof Nodes.Suggestion shown ? shown : null;
            selected = suggestion != null && index == selectedIndex(field());
            hovered = suggestion != null && index == FieldSuggestions.this.hovered;
            String titleText = suggestion == null ? LOADING : suggestion.title(), detailText = suggestion == null ? "" : suggestion.detail();
            title.setText(titleText); detail.setText(detailText);
            detail.setVisible(!detailText.isBlank());
            title.setFont(ThemePainting.font(context, Typography.Role.BODY, titleText));
            detail.setFont(ThemePainting.font(context, Typography.Role.BODY, detailText));
            title.setForeground(ThemePainting.color(context, selected ? SemanticColor.TEXT_ONSELECTION : suggestion == null ? SemanticColor.TEXT_TERTIARY : SemanticColor.TEXT_PRIMARY));
            detail.setForeground(ThemePainting.color(context, selected ? SemanticColor.TEXT_ONSELECTION_SECONDARY : SemanticColor.TEXT_TERTIARY));
            if (rowBorder != null) setBorder(rowBorder);
            ((BorderLayout) getLayout()).setHgap(detailGap);
            getAccessibleContext().setAccessibleName(detailText.isBlank() ? titleText : titleText + ", " + detailText);
            return this;
        }
        @Override protected void paintComponent(Graphics graphics) {
            if (!selected && !hovered) return;
            var context = owner.context();
            graphics.setColor(selected ? ThemePainting.color(context, SemanticColor.SELECTION_BG)
                : ColorMath.opacity(ThemePainting.color(context, SemanticColor.HOVER_OVERLAY), .05));
            graphics.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}

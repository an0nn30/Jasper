package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;

/**
 * The SEARCHABLE choice: a dropdown that filters as you type, as IntelliJ's theme list does. Typing shows a search bar
 * at the top of the open list and narrows it to entries holding every typed word, ignoring case; pinned entries always
 * stay. Up/Down/Page/Home/End move the highlight, Enter picks it, Backspace edits the search and Escape closes; closing
 * clears the search. Focus stays on the dropdown, which never picks by first letter.
 * <p>With free entry the dropdown is editable: its text is the value, typing filters the options as suggestions and
 * picking one enters its label. One control serves both modes. EDT only.
 */
final class SearchChoices {
    private SearchChoices() {}

    static final class Combo extends Choices.ChoiceCombo {
        private Popup searchPopup;
        int pinned;
        String noMatches = "";
        /** Shown in the placeholder role while a free entry is empty; never a value. */
        String placeholder = "";
        boolean freeEntry;
        /** Receives free-entry text the user typed or picked; descriptor updates are quiet. */
        Consumer<String> entered = text -> {};
        /** Free entry's Enter: runs the choice's action, when it has one; answers whether it did. */
        java.util.function.BooleanSupplier submitted = () -> false;
        private boolean quiet;
        private boolean entering;
        private boolean disposed;
        private String declaredEntry;
        private long entryRevision;
        private final DocumentListener typing = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { typed(); }
            @Override public void removeUpdate(DocumentEvent event) { typed(); }
            @Override public void changedUpdate(DocumentEvent event) { typed(); }
        };
        private JTextComponent observed;
        private final PropertyChangeListener editorChange = event -> observeEditor();

        Combo(KitContext context) {
            super(context);
            // A non-UIResource editor belongs to this control, not to each installed delegate.
            // Keeping the component also keeps its document, caret, IME and focus identity.
            setEditor(new javax.swing.plaf.basic.BasicComboBoxEditor() {
                // An empty free entry shows the descriptor's placeholder; the kit paints it once, in the placeholder role.
                @Override protected JTextField createEditorComponent() {
                    var field = new JTextField("", 9) {
                        @Override protected void paintComponent(Graphics graphics) {
                            super.paintComponent(graphics);
                            if (placeholder.isEmpty() || getDocument().getLength() > 0 || Combo.this.context == null) return;
                            var g = (Graphics2D) graphics.create();
                            try {
                                ThemePainting.antialias(g); g.setFont(getFont());
                                g.setColor(ThemePainting.color(Combo.this.context, SemanticColor.TEXT_PLACEHOLDER));
                                Insets insets = getInsets(); var metrics = g.getFontMetrics();
                                g.drawString(placeholder, insets.left, insets.top + (getHeight() - insets.top - insets.bottom - metrics.getHeight()) / 2 + metrics.getAscent());
                            } finally { g.dispose(); }
                        }
                    };
                    field.setBorder(null);
                    return field;
                }
            });
            addPropertyChangeListener("editor", editorChange);
        }

        /**
         * Refresh an editable kit delegate in place: BasicComboBoxUI uninstall removes every child, which transfers
         * native focus away from its editor and can interrupt IME even when the same editor is installed again.
         * Module painting/popup styles are live; Swing's tree refresh still updates the attached editor's own UI.
         */
        @Override public void updateUI() {
            if (context == null) { super.updateUI(); return; }
            if (!freeEntry || !(getUI() instanceof Choices.ChoiceCombo.KitUi)) {
                unobserveEditor();
                setUI(kitUi());
                observeEditor();
            }
            if (getRenderer() instanceof Component renderer) SwingUtilities.updateComponentTreeUI(renderer);
            refreshPopup();
        }

        @Override ComboPopup createKitPopup(JComboBox<Object> comboBox) {
            searchPopup = new Popup(this);
            return searchPopup;
        }

        Popup searchPopup() { return searchPopup; }

        /** Never picks by key: typed characters are search text. */
        @Override public boolean selectWithKeyChar(char keyChar) { return false; }

        /** Free entry keeps its own text; Swing would replace it with the (absent) selected option. */
        @Override public void configureEditor(ComboBoxEditor editor, Object item) { if (!freeEntry) super.configureEditor(editor, item); }

        /** Free entry reports text, never an option object Swing would make of it. */
        @Override public void actionPerformed(java.awt.event.ActionEvent event) { if (!freeEntry) super.actionPerformed(event); }

        JTextField editorField() { return (JTextField) getEditor().getEditorComponent(); }

        void configure(Nodes.ChoiceSearch search) {
            pinned = search.pinned();
            noMatches = search.noMatches();
            if (!placeholder.equals(search.placeholder())) { placeholder = search.placeholder(); editorField().repaint(); }
            boolean free = search.entry().isPresent();
            if (free != freeEntry) { freeEntry = free; setEditable(free); observeEditor(); }
            if (!free) declaredEntry = null;
            search.entry().ifPresent(text -> {
                // A palette refresh must not replay the descriptor over unacknowledged user text.
                if (!java.util.Objects.equals(declaredEntry, text) || entering) {
                    declaredEntry = text;
                    showEntry(text);
                }
            });
        }

        /** The domain's text; while the user's own edit is being delivered Swing forbids a document write, so it follows. */
        private void showEntry(String text) {
            long revision = ++entryRevision;
            var field = editorField();
            if (field.getText().equals(text)) return;
            if (entering) {
                java.awt.EventQueue.invokeLater(() -> {
                    if (!disposed && freeEntry && entryRevision == revision && !editorField().getText().equals(text)) quietly(text);
                });
                return;
            }
            quietly(text);
        }

        private void quietly(String text) {
            quiet = true;
            try { editorField().setText(text); } finally { quiet = false; }
        }

        /** Enters a picked suggestion as one edit. */
        void enter(String text) {
            quietly(text);
            entering = true;
            try { entered.accept(text); } finally { entering = false; }
        }

        private void observeEditor() {
            if (disposed) return;
            var field = getEditor() == null ? null : getEditor().getEditorComponent() instanceof JTextComponent text ? text : null;
            if (field == observed) return;
            unobserveEditor();
            observed = field;
            if (observed != null) {
                observed.getDocument().addDocumentListener(typing);
                if (searchPopup != null) observed.addKeyListener(searchPopup.keys);
            }
        }

        private void unobserveEditor() {
            if (observed != null) {
                observed.getDocument().removeDocumentListener(typing);
                if (searchPopup != null) observed.removeKeyListener(searchPopup.keys);
                observed = null;
            }
        }

        private void typed() {
            if (quiet || !freeEntry) return;
            String text = editorField().getText();
            entering = true;
            try { entered.accept(text); } finally { entering = false; }
            if (searchPopup == null) return;
            searchPopup.typedEntry(text);
        }

        void styleEditor() {
            if (!freeEntry) return;
            var field = editorField();
            field.setOpaque(false);
            field.setFont(ThemePainting.font(context, Typography.Role.BODY));
            field.setForeground(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
            field.setCaretColor(field.getForeground());
            field.setSelectionColor(ThemePainting.color(context, SemanticColor.SELECTION_BG));
            field.setSelectedTextColor(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
            int pad = ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            field.setBorder(new EmptyBorder(0, pad, 0, 0));
        }

        @Override void refreshPopup() {
            styleEditor();
            if (searchPopup != null) searchPopup.restyle();
        }

        void dispose() {
            disposed = true;
            removePropertyChangeListener("editor", editorChange);
            unobserveEditor();
        }
    }

    /**
     * The open list: a search bar shown from the first typed character until the list closes, over the matching entries.
     * Its width is fixed when it opens, so searching never resizes it except to show the bar.
     */
    static final class Popup extends JPopupMenu implements ComboPopup {
        private final Combo combo;
        private final JTextField search = new JTextField();
        private final JLabel searchIcon = Labels.literal();
        private final JPanel bar = new JPanel(new BorderLayout());
        private final DefaultListModel<Object> rows = new DefaultListModel<>();
        private final JList<Object> list = new JList<>(rows);
        private final JScrollPane scroller = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        /** The combo's index of each shown row; -1 for the "no matches" line. */
        private final List<Integer> indexes = new ArrayList<>();
        private final ListDataListener changes = new ListDataListener() {
            @Override public void intervalAdded(ListDataEvent event) { refilter(); }
            @Override public void intervalRemoved(ListDataEvent event) { refilter(); }
            @Override public void contentsChanged(ListDataEvent event) { if (event.getIndex0() >= 0) refilter(); }
        };
        private final PropertyChangeListener modelChange = event -> {
            if (event.getOldValue() instanceof ListModel<?> old) old.removeListDataListener(changes);
            if (event.getNewValue() instanceof ListModel<?> now) now.addListDataListener(changes);
            refilter();
        };
        final KeyListener keys = new Keys();
        private final MouseMotionListener noDrag = new MouseMotionAdapter() { };
        private final MouseListener toggle = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || !combo.isEnabled()) return;
                if (!combo.isFocusOwner() && !combo.freeEntry) combo.requestFocus();
                combo.setPopupVisible(!isVisible());
            }
        };
        private int openWidth;
        private boolean filtering;
        /** Free entry: the text typed since the list opened filters it; empty shows every suggestion. */
        private String entryQuery = "";

        Popup(Combo combo) {
            this.combo = combo;
            super.setLayout(new BorderLayout());
            setFocusable(false);
            setOpaque(false);
            search.setFocusable(false);
            search.setOpaque(false);
            search.putClientProperty("html.disable", Boolean.TRUE);
            search.getAccessibleContext().setAccessibleName("Search");
            search.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { searched(); }
                @Override public void removeUpdate(DocumentEvent event) { searched(); }
                @Override public void changedUpdate(DocumentEvent event) { searched(); }
            });
            bar.setOpaque(false);
            bar.add(searchIcon, BorderLayout.WEST);
            bar.add(search, BorderLayout.CENTER);
            bar.setVisible(false);
            list.setFocusable(false);
            list.setOpaque(false);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer((source, value, row, selected, focused) -> cell(String.valueOf(value), row, selected));
            var pointer = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent event) {
                    int row = list.locationToIndex(event.getPoint());
                    if (row >= 0 && indexes.get(row) >= 0) list.setSelectedIndex(row);
                }
                @Override public void mouseReleased(MouseEvent event) {
                    int row = list.locationToIndex(event.getPoint());
                    if (SwingUtilities.isLeftMouseButton(event) && row >= 0 && indexes.get(row) >= 0) choose();
                }
            };
            list.addMouseListener(pointer);
            list.addMouseMotionListener(pointer);
            scroller.setBorder(new EmptyBorder(0, 0, 0, 0));
            scroller.setOpaque(false);
            scroller.getViewport().setOpaque(false);
            scroller.setFocusable(false);
            scroller.getVerticalScrollBar().setFocusable(false);
            add(bar, BorderLayout.NORTH);
            add(scroller, BorderLayout.CENTER);
            combo.getModel().addListDataListener(changes);
            combo.addPropertyChangeListener("model", modelChange);
            addPopupMenuListener(new PopupMenuListener() {
                @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) { }
                @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                    combo.firePopupMenuWillBecomeInvisible();
                    reset();
                }
                @Override public void popupMenuCanceled(PopupMenuEvent event) { combo.firePopupMenuCanceled(); }
            });
            restyle();
        }

        /** Kit colors, fonts and padding from the combo's context; replayed on every theme change. */
        void restyle() {
            var context = combo.context;
            int pad = ThemePainting.metric(context, DesignMetrics.Role.MENU_PADDING), line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            setBorder(new EmptyBorder(pad, line, pad, line));
            list.setFont(ThemePainting.font(context, Typography.Role.BODY));
            list.setFixedCellHeight(ThemePainting.height(context, DesignMetrics.Role.MENU_ITEM_HEIGHT, Typography.Role.BODY));
            list.setBackground(ThemePainting.color(context, SemanticColor.SURFACE_MENU));
            list.setForeground(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
            list.setSelectionBackground(ThemePainting.color(context, SemanticColor.SELECTION_BG));
            list.setSelectionForeground(ThemePainting.color(context, SemanticColor.TEXT_ONSELECTION));
            search.setFont(ThemePainting.font(context, Typography.Role.BODY));
            search.setForeground(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
            search.setCaretColor(search.getForeground());
            search.setBorder(new EmptyBorder(0, 0, 0, 0));
            searchIcon.setIcon(context.icons().icon("search", context.theme().semantic(), SemanticColor.TEXT_PLACEHOLDER,
                com.formdev.flatlaf.util.UIScale.scale(context.icons().size(context.metrics(), Nodes.IconSize.CONTROL))));
            searchIcon.setBorder(new EmptyBorder(0, 0, 0, ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP)));
            int field = ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            bar.setBorder(new EmptyBorder(pad, field, pad, field));
            revalidate(); repaint(); list.repaint();
        }

        @Override protected void paintComponent(Graphics graphics) { Menus.paintSurface(this, graphics, combo.context); }

        /** A row as the kit's dropdown paints its options; the "no matches" line is muted. */
        private Component cell(String text, int row, boolean selected) {
            boolean line = row >= 0 && row < indexes.size() && indexes.get(row) < 0;
            Optional<Nodes.Hue> hue = row >= 0 && row < indexes.size() && indexes.get(row) >= 0
                && indexes.get(row) < combo.getItemCount() ? combo.getItemAt(indexes.get(row)).hue() : Optional.empty();
            var label = Choices.optionLabel(combo.context, text, hue, selected && !line, line ? SemanticColor.TEXT_TERTIARY : null);
            return label;
        }

        /** Opens on the current choice with no search. */
        @SuppressWarnings("deprecation")
        @Override public void show() {
            combo.firePopupMenuWillBecomeVisible();
            reset();
            if (!combo.isShowing()) return;
            list.setVisibleRowCount(Math.max(1, Math.min(rows.size(), combo.getMaximumRowCount())));
            openWidth = 0;
            openWidth = getPreferredSize().width;
            show(combo, 0, combo.getHeight());
        }

        @SuppressWarnings("deprecation")
        @Override public void hide() {
            MenuSelectionManager manager = MenuSelectionManager.defaultManager();
            boolean selected = false;
            for (MenuElement element : manager.getSelectedPath()) selected |= element == this;
            if (selected) manager.clearSelectedPath();
            else if (super.isVisible()) setVisible(false);
            reset();
        }

        @Override public JList<Object> getList() { return list; }
        @Override public MouseListener getMouseListener() { return toggle; }
        @Override public MouseMotionListener getMouseMotionListener() { return noDrag; }
        @Override public KeyListener getKeyListener() { return keys; }
        @Override public void uninstallingUI() {
            combo.getModel().removeListDataListener(changes);
            combo.removePropertyChangeListener("model", modelChange);
        }

        @Override public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            size.width = Math.max(size.width, Math.max(openWidth, combo.getWidth()));
            return size;
        }

        /** The search text; with free entry, the text typed since the list opened. */
        String query() { return combo.freeEntry ? entryQuery : search.getText(); }
        JPanel searchBar() { return bar; }
        /** Every shown row's text, the "no matches" line included. */
        List<String> shown() {
            var labels = new ArrayList<String>();
            for (int row = 0; row < rows.size(); row++) labels.add((String) rows.get(row));
            return labels;
        }
        /** The highlighted entry's text, or null. */
        String highlighted() { return (String) list.getSelectedValue(); }

        private boolean searching() { return !query().isEmpty(); }

        /** No search, every entry, the current choice highlighted and no bar. */
        private void reset() {
            filtering = true;
            try { search.setText(""); entryQuery = ""; } finally { filtering = false; }
            bar.setVisible(false);
            filter(current());
        }

        /** The entry shown now: the selected option, or with free entry the option whose label is the text. */
        private int current() {
            if (!combo.freeEntry) return combo.getSelectedIndex();
            String text = combo.editorField().getText();
            for (int index = 0; index < combo.getItemCount(); index++) if (combo.getItemAt(index).label().equals(text)) return index;
            return -1;
        }

        private void searched() {
            if (filtering) return;
            boolean showing = bar.isVisible();
            bar.setVisible(true);
            refilter();
            if (!showing && super.isVisible()) pack();
        }

        /** Free entry typed: open the list on the suggestions holding the text. */
        void typedEntry(String text) {
            entryQuery = text;
            if (!super.isVisible() && combo.isShowing() && !text.isEmpty()) {
                combo.setPopupVisible(true);
                entryQuery = text;
            }
            refilter();
        }

        /**
         * Filters again after the search or the entries change, keeping the highlighted entry when it still matches. A
         * closed list with no search waits for {@link #show()}, so rebuilding the entries stays cheap.
         */
        private void refilter() {
            if (filtering || !super.isVisible() && !searching()) return;
            int row = list.getSelectedIndex();
            String kept = row < 0 ? null : (String) rows.get(row);
            int wanted = -1;
            for (int index = 0; index < combo.getItemCount() && kept != null && wanted < 0; index++)
                if (kept.equals(combo.getItemAt(index).label())) wanted = index;
            filter(wanted < 0 ? current() : wanted);
        }

        /**
         * Shows the entries matching the search, highlighting {@code wanted} when it matches and else the first match.
         * The rows change in one step, and not at all when they are the same: added one by one, the list measured every
         * row again for each row added, which made a long list such as the installed fonts slow to open.
         */
        private void filter(int wanted) {
            filtering = true;
            try {
                String[] words = query().strip().toLowerCase(Locale.ROOT).split("\\s+");
                var labels = new ArrayList<Object>();
                indexes.clear();
                int pinned = Math.min(combo.pinned, combo.getItemCount());
                int keep = -1, first = -1, matched = 0;
                for (int index = 0; index < combo.getItemCount(); index++) {
                    String label = combo.getItemAt(index).label();
                    boolean matches = matches(label, words);
                    if (!matches && index >= pinned) continue;
                    if (matches && index >= pinned) matched++;
                    if (matches && first < 0) first = labels.size();
                    if (matches && index == wanted) keep = labels.size();
                    labels.add(label);
                    indexes.add(index);
                }
                // With no match nothing is highlighted, so Enter after a typo picks nothing.
                int row = keep >= 0 ? keep : first;
                if (searching() && matched == 0) { labels.add(combo.noMatches); indexes.add(-1); }
                if (!Collections.list(rows.elements()).equals(labels)) { rows.clear(); rows.addAll(labels); }
                if (row < 0) list.clearSelection();
                else { list.setSelectedIndex(row); list.ensureIndexIsVisible(row); }
            } finally { filtering = false; }
        }

        private static boolean matches(String label, String[] words) {
            String text = label.toLowerCase(Locale.ROOT);
            for (String word : words) if (!word.isEmpty() && !text.contains(word)) return false;
            return true;
        }

        private void move(int delta) {
            int row = list.getSelectedIndex(), step = Integer.signum(delta);
            for (int remaining = Math.abs(delta), next = row + step; remaining > 0 && next >= 0 && next < rows.size(); next += step)
                if (indexes.get(next) >= 0) { row = next; remaining--; }
            if (row < 0) return;
            list.setSelectedIndex(row);
            list.ensureIndexIsVisible(row);
        }

        private void choose() {
            int row = list.getSelectedIndex();
            if (row >= 0 && indexes.get(row) >= 0) {
                if (combo.freeEntry) combo.enter(combo.getItemAt(indexes.get(row)).label());
                else combo.setSelectedIndex(indexes.get(row));
            }
            combo.setPopupVisible(false);
            reset();
        }

        private final class Keys extends KeyAdapter {
            @Override public void keyTyped(KeyEvent event) {
                if (combo.freeEntry) return; // free entry types into its own text
                char typed = event.getKeyChar();
                // AltGr arrives as Ctrl+Alt on Windows and Linux; its characters are search text.
                boolean shortcut = event.isMetaDown() || event.isControlDown() && !event.isAltDown();
                if (typed == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(typed) || shortcut || !combo.isEnabled()) return;
                if (typed == ' ' && !searching()) {
                    if (!isVisible()) combo.setPopupVisible(true);
                    event.consume();
                    return;
                }
                if (!isVisible() && !searching()) combo.setPopupVisible(true);
                search.setText(search.getText() + typed);
                event.consume();
            }

            @Override public void keyPressed(KeyEvent event) {
                if (event.isControlDown() || event.isMetaDown() || event.isAltDown()) return;
                if (combo.freeEntry && event.getKeyCode() == KeyEvent.VK_ENTER) {
                    // Enter enters the highlighted suggestion, if any, then runs the choice's action (such as Add).
                    boolean open = isVisible() || searching();
                    if (open) choose();
                    if (combo.submitted.getAsBoolean() || open) event.consume();
                    return;
                }
                if (!isVisible() && !searching()) return;
                switch (event.getKeyCode()) {
                    // Every key the combo's own bindings would answer with a pick behind the list is handled here.
                    case KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> move(1);
                    case KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> move(-1);
                    case KeyEvent.VK_PAGE_DOWN -> move(combo.getMaximumRowCount());
                    case KeyEvent.VK_PAGE_UP -> move(-combo.getMaximumRowCount());
                    case KeyEvent.VK_END -> { if (combo.freeEntry) return; move(rows.size()); }
                    case KeyEvent.VK_HOME -> { if (combo.freeEntry) return; list.clearSelection(); move(1); }
                    case KeyEvent.VK_ENTER -> choose();
                    case KeyEvent.VK_ESCAPE -> { combo.firePopupMenuCanceled(); combo.setPopupVisible(false); reset(); }
                    case KeyEvent.VK_BACK_SPACE -> {
                        if (combo.freeEntry || !searching()) return;
                        String text = search.getText();
                        search.setText(text.substring(0, text.length() - 1));
                    }
                    case KeyEvent.VK_SPACE -> { if (combo.freeEntry || !searching()) return; }
                    default -> { return; }
                }
                event.consume();
            }
        }
    }
}

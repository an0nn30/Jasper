package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.ui.FlatComboBoxUI;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
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
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;

/**
 * A dropdown that searches, as IntelliJ's theme list does: typing shows a search bar at the top of the open list and
 * narrows it to the entries holding every typed word, ignoring case; the first {@code pinned} entries always stay.
 * Typing on the closed dropdown opens it. Up and Down move the highlight, Enter picks it, Backspace edits the search
 * and Escape closes the list; closing clears the search. Keyboard focus stays on the dropdown, which routes these keys
 * to the search, so the dropdown never picks by first letter. Its own popup replaces the look and feel's. EDT only.
 */
final class SearchCombo extends JComboBox<String> {
    private final int pinned;
    private final String noMatches;
    /** Assigned while the look and feel installs, which the superclass constructor already does. */
    private Popup popup;

    SearchCombo(int pinned, String noMatches) {
        this.pinned = pinned;
        this.noMatches = noMatches;
    }

    /**
     * Never picks by key: typed characters are search text. Each look and feel installs its own key selection manager,
     * whose index would count the filtered rows, so overriding this outlasts them all.
     */
    @Override public boolean selectWithKeyChar(char keyChar) { return false; }

    @Override public void updateUI() {
        setUI(UIManager.getLookAndFeel() instanceof FlatLaf ? new FlatUi() : new BasicUi());
        if (getRenderer() instanceof Component renderer) SwingUtilities.updateComponentTreeUI(renderer);
    }

    Popup searchPopup() { return popup; }

    /** A new popup for the look and feel being installed; the UI classes' own {@code popup} field hides this one's. */
    private Popup installed() {
        popup = new Popup();
        return popup;
    }

    private final class FlatUi extends FlatComboBoxUI {
        @Override protected ComboPopup createPopup() { return installed(); }
    }

    private final class BasicUi extends BasicComboBoxUI {
        @Override protected ComboPopup createPopup() { return installed(); }
    }

    /**
     * The open list: a search bar, shown from the first typed character until the list closes, over the matching
     * entries. Its size is fixed when it opens, so searching never resizes it except to show the bar.
     */
    final class Popup extends JPopupMenu implements ComboPopup {
        private final SearchCombo combo = SearchCombo.this;
        private final JTextField search = new JTextField();
        private final JPanel bar = new JPanel(new BorderLayout());
        private final DefaultListModel<Object> rows = new DefaultListModel<>();
        private final JList<Object> list = new JList<>(rows);
        private final JScrollPane scroller = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        /** The dropdown's index of each shown row; -1 for the "no matches" line. */
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
        private final KeyListener keys = new Keys();
        private final MouseMotionListener noDrag = new MouseMotionAdapter() { };
        private final MouseListener toggle = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || !combo.isEnabled()) return;
                if (!combo.isFocusOwner()) combo.requestFocus();
                combo.setPopupVisible(!isVisible());
            }
        };
        private int openWidth;
        private boolean filtering;

        Popup() {
            super.setLayout(new BorderLayout());
            setFocusable(false);
            setBorder(UIManager.getBorder("PopupMenu.border"));
            search.setFocusable(false);
            search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, AppIcons.icon("search"));
            search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
            // The clear button would take the focus from the dropdown, which closes the list.
            for (Component part : search.getComponents()) part.setFocusable(false);
            search.getAccessibleContext().setAccessibleName("Search");
            search.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { searched(); }
                @Override public void removeUpdate(DocumentEvent event) { searched(); }
                @Override public void changedUpdate(DocumentEvent event) { searched(); }
            });
            bar.setOpaque(false);
            bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            bar.add(search);
            bar.setVisible(false);
            list.setFocusable(false);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setBackground(color("ComboBox.popupBackground", "List.background"));
            list.setForeground(color("ComboBox.foreground", "List.foreground"));
            list.setSelectionBackground(color("ComboBox.selectionBackground", "List.selectionBackground"));
            list.setSelectionForeground(color("ComboBox.selectionForeground", "List.selectionForeground"));
            list.setCellRenderer(this::render);
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
            scroller.setBorder(BorderFactory.createEmptyBorder());
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
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Component render(JList<?> source, Object value, int row, boolean selected, boolean focused) {
            ListCellRenderer renderer = combo.getRenderer();
            Component shown = renderer.getListCellRendererComponent(source, value, row, selected, focused);
            if (row >= 0 && row < indexes.size() && indexes.get(row) < 0) shown.setForeground(color("Label.disabledForeground", "Label.foreground"));
            return shown;
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

        String query() { return search.getText(); }
        JPanel searchBar() { return bar; }
        /** Every shown row's text, the "no matches" line included. */
        List<String> shown() {
            var labels = new ArrayList<String>();
            for (int row = 0; row < rows.size(); row++) labels.add((String) rows.get(row));
            return labels;
        }
        /** The highlighted entry's text, or null. */
        String highlighted() { return (String) list.getSelectedValue(); }

        private boolean searching() { return !search.getText().isEmpty(); }

        /** No search, every entry, the current choice highlighted and no bar. */
        private void reset() {
            filtering = true;
            try { search.setText(""); } finally { filtering = false; }
            bar.setVisible(false);
            filter(combo.getSelectedIndex());
        }

        private void searched() {
            if (filtering) return;
            boolean showing = bar.isVisible();
            bar.setVisible(true);
            refilter();
            if (!showing && super.isVisible()) pack();
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
                if (kept.equals(combo.getItemAt(index))) wanted = index;
            filter(wanted < 0 ? combo.getSelectedIndex() : wanted);
        }

        /** Shows the entries matching the search, highlighting {@code wanted} when it matches and else the first match. */
        private void filter(int wanted) {
            filtering = true;
            try {
                String[] words = search.getText().strip().toLowerCase(Locale.ROOT).split("\\s+");
                rows.clear();
                indexes.clear();
                int keep = -1, first = -1, matched = 0;
                for (int index = 0; index < combo.getItemCount(); index++) {
                    String label = combo.getItemAt(index);
                    boolean matches = matches(label, words);
                    if (!matches && index >= pinned) continue;
                    if (matches && index >= pinned) matched++;
                    if (matches && first < 0) first = rows.size();
                    if (matches && index == wanted) keep = rows.size();
                    rows.addElement(label);
                    indexes.add(index);
                }
                // With no match nothing is highlighted, so Enter after a typo picks nothing.
                int row = keep >= 0 ? keep : first;
                if (searching() && matched == 0) { rows.addElement(noMatches); indexes.add(-1); }
                if (row < 0) list.clearSelection(); else { list.setSelectedIndex(row); list.ensureIndexIsVisible(row); }
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
            if (row >= 0 && indexes.get(row) >= 0) combo.setSelectedIndex(indexes.get(row));
            combo.setPopupVisible(false);
        }

        private final class Keys extends KeyAdapter {
            @Override public void keyTyped(KeyEvent event) {
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
                if (!isVisible() && !searching() || event.isControlDown() || event.isMetaDown() || event.isAltDown()) return;
                switch (event.getKeyCode()) {
                    // Every key the combo's own bindings would answer with a pick behind the list is handled here.
                    case KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> move(1);
                    case KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> move(-1);
                    case KeyEvent.VK_PAGE_DOWN -> move(combo.getMaximumRowCount());
                    case KeyEvent.VK_PAGE_UP -> move(-combo.getMaximumRowCount());
                    case KeyEvent.VK_END -> move(rows.size());
                    case KeyEvent.VK_HOME -> { list.clearSelection(); move(1); }
                    case KeyEvent.VK_ENTER -> choose();
                    case KeyEvent.VK_ESCAPE -> { combo.firePopupMenuCanceled(); combo.setPopupVisible(false); }
                    case KeyEvent.VK_BACK_SPACE -> {
                        if (!searching()) return;
                        String text = search.getText();
                        search.setText(text.substring(0, text.length() - 1));
                    }
                    case KeyEvent.VK_SPACE -> { if (!searching()) return; }
                    default -> { return; }
                }
                event.consume();
            }
        }
    }

    private static Color color(String key, String fallback) {
        Color value = UIManager.getColor(key);
        return value != null ? value : UIManager.getColor(fallback);
    }
}

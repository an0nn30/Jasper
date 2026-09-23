package dev.jasper.app.shortcuthelp;

import dev.jasper.app.platform.WindowInput;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** Passive grouped table and window-scoped capture. Native dispatch exists only while mounted. */
final class ShortcutPanel extends JPanel implements AutoCloseable {
    final JTextField search = new JTextField();
    final JButton record = new JButton("Record shortcut");
    final JButton clear = new JButton("Clear");
    final JTable table;
    private final JLabel status = new JLabel();
    private final Rows model = new Rows();
    private List<ShortcutEntry> all = List.of();
    private List<ShortcutEntry> visible = List.of();
    private KeyStroke captured;
    private boolean recording;
    private boolean editingCapture;
    private boolean closed;
    private boolean installed;
    private boolean swallowTyped;
    private final Set<Integer> held = new HashSet<>();
    private final KeyEventDispatcher dispatcher = this::dispatch;
    private final java.beans.PropertyChangeListener focusListener = event -> {
        if (SwingUtilities.getWindowAncestor(this) != event.getNewValue()) focusLeftWindow();
    };
    private final WindowInput.ShortcutRecorder nativeRecorder = new WindowInput.ShortcutRecorder(this::capture);
    private JRootPane mountedRoot;

    ShortcutPanel() {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));
        var top = new JPanel(new BorderLayout(10, 8));
        var label = new JLabel("Find a shortcut"); label.setLabelFor(search);
        top.add(label, BorderLayout.NORTH);
        search.putClientProperty("JTextField.placeholderText", "Search actions, plugins, or Cmd+Shift+H");
        search.getAccessibleContext().setAccessibleName("Search keyboard shortcuts");
        top.add(search, BorderLayout.CENTER);
        var buttons = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
        buttons.add(record); buttons.add(clear); top.add(buttons, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new Cell());
        table.getAccessibleContext().setAccessibleName("Keyboard shortcuts grouped by section or plugin");
        int[] widths = {285, 165, 160, 290};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.addPropertyChangeListener("font", event -> sizeRows()); sizeRows();
        var scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        add(scroll, BorderLayout.CENTER);
        status.getAccessibleContext().setAccessibleName("Shortcut search status");
        add(status, BorderLayout.SOUTH);
        search.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() { if (!editingCapture) { captured = null; filter(); } }
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
        });
        record.addActionListener(event -> {
            recording = !recording;
            search.setEditable(!recording);
            record.setText(recording ? "Stop recording" : "Record shortcut");
            if (recording) { captured = null; search.setText(""); }
            filter(); search.requestFocusInWindow();
        });
        clear.addActionListener(event -> { captured = null; search.setText(""); filter(); search.requestFocusInWindow(); });
        filter();
    }

    private void sizeRows() {
        table.setRowHeight(table.getFontMetrics(table.getFont()).getHeight() + 12);
    }

    void setRows(List<ShortcutEntry> rows) { all = List.copyOf(rows); filter(); }

    private void filter() {
        visible = all.stream().filter(row -> ShortcutText.matches(row, search.getText(), captured)).toList();
        model.fireTableDataChanged();
        status.setText(recording ? "Press a shortcut to find it. Click Stop recording when finished."
            : visible.isEmpty() ? "No matching shortcuts. Try another name or key combination."
            : visible.size() + " shortcuts · Current bindings · Read-only reference");
    }

    boolean capture(KeyStroke stroke) {
        if (!recording || closed) return false;
        captured = stroke;
        editingCapture = true;
        try { search.setText(ShortcutText.format(stroke)); }
        finally { editingCapture = false; }
        filter(); return true;
    }

    boolean dispatch(KeyEvent event) {
        int type = event.getID(), code = event.getKeyCode();
        boolean tail = type == KeyEvent.KEY_RELEASED ? held.remove(code)
            : type == KeyEvent.KEY_TYPED ? swallowTyped && !held.isEmpty()
            : type == KeyEvent.KEY_PRESSED && held.contains(code);
        if (type == KeyEvent.KEY_PRESSED) swallowTyped = tail;
        if (tail) { event.consume(); return true; }
        if (closed || !recording || type != KeyEvent.KEY_PRESSED || !belongs(event.getComponent())) return false;
        held.add(code); swallowTyped = true;
        if (!modifier(code)) capture(KeyStroke.getKeyStrokeForEvent(event));
        event.consume(); return true;
    }

    /** Losing focus can hide key releases from AWT; never retain physical keys across that boundary. */
    void focusLeftWindow() {
        held.clear(); swallowTyped = false;
        recording = false; search.setEditable(true); record.setText("Record shortcut");
        filter();
    }

    private boolean belongs(Component source) {
        JRootPane root = SwingUtilities.getRootPane(this);
        return source == this || SwingUtilities.isDescendingFrom(source, this)
            || root != null && SwingUtilities.getRootPane(source) == root;
    }

    private static boolean modifier(int code) {
        return code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
            || code == KeyEvent.VK_META || code == KeyEvent.VK_ALT_GRAPH;
    }

    @Override public void addNotify() {
        super.addNotify();
        if (closed || installed) return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusedWindow", focusListener);
        installed = true;
        mountedRoot = SwingUtilities.getRootPane(this);
        if (mountedRoot != null) mountedRoot.putClientProperty(WindowInput.SHORTCUT_RECORDER, nativeRecorder);
    }

    @Override public void removeNotify() { uninstall(); super.removeNotify(); }

    private void uninstall() {
        if (installed) KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
        if (installed) KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusedWindow", focusListener);
        installed = false;
        if (mountedRoot != null && mountedRoot.getClientProperty(WindowInput.SHORTCUT_RECORDER) == nativeRecorder)
            mountedRoot.putClientProperty(WindowInput.SHORTCUT_RECORDER, null);
        mountedRoot = null; held.clear(); swallowTyped = false;
    }

    @Override public void close() { closed = true; recording = false; uninstall(); }

    private final class Rows extends AbstractTableModel {
        private static final String[] HEADINGS = {"Action", "Shortcut", "Section / Plugin", "Context"};
        @Override public int getRowCount() { return visible.size(); }
        @Override public int getColumnCount() { return HEADINGS.length; }
        @Override public String getColumnName(int column) { return HEADINGS[column]; }
        @Override public Object getValueAt(int row, int column) {
            var item = visible.get(row);
            return switch (column) {
                case 0 -> item.name(); case 1 -> ShortcutText.format(item.stroke());
                case 2 -> item.group(); default -> item.context();
            };
        }
    }

    private final class Cell extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable grid, Object value, boolean selected,
                                                                 boolean focused, int row, int column) {
            super.getTableCellRendererComponent(grid, value, selected, focused, row, column);
            boolean start = row == 0 || !visible.get(row).group().equals(visible.get(row - 1).group());
            Color line = UIManager.getColor("Component.borderColor");
            if (line == null) line = grid.getForeground();
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(start ? 1 : 0, 0, 0, 0, line),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
            setFont(grid.getFont().deriveFont(column == 2 ? Font.BOLD : Font.PLAIN));
            setToolTipText(String.valueOf(value));
            return this;
        }
    }
}

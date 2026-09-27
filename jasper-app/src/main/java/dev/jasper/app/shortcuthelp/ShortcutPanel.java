package dev.jasper.app.shortcuthelp;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import dev.jasper.app.platform.WindowInput;
import java.awt.BorderLayout;
import java.awt.Component;
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
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Expandable shortcut sections and window-scoped capture. Native dispatch exists only while mounted. */
final class ShortcutPanel extends JPanel implements AutoCloseable {
    final JTextField search = new JTextField();
    final JButton record = new JButton("Record shortcut");
    final JButton clear = new JButton("Clear");
    final ShortcutTree tree;
    final ShortcutEditPanel editor;
    private final JLabel status = new JLabel();
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

    ShortcutPanel(boolean macOs, Function<Map<String, Optional<String>>,
            CompletableFuture<Void>> writer) {
        super(new BorderLayout(0, 12));
        editor = new ShortcutEditPanel(macOs, writer, this::stopSearchRecording);
        setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));
        var top = new JPanel(new BorderLayout(10, 8)); top.setOpaque(false);
        var label = new JLabel("Find a shortcut"); label.setLabelFor(search);
        top.add(label, BorderLayout.NORTH);
        search.putClientProperty("JTextField.placeholderText", "Search actions, plugins, or Cmd+Shift+H");
        search.getAccessibleContext().setAccessibleName("Search keyboard shortcuts");
        top.add(search, BorderLayout.CENTER);
        var buttons = new JPanel(new java.awt.GridLayout(1, 2, 6, 0)); buttons.setOpaque(false);
        buttons.add(record); buttons.add(clear); top.add(buttons, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        tree = new ShortcutTree(this::editRow);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(tree);
        var scroll = new JScrollPane(tree) {
            @Override public void updateUI() {
                super.updateUI();
                setBorder(BorderFactory.createEmptyBorder());
                setBackground(UIManager.getColor("Panel.background"));
                getViewport().setBackground(getBackground());
            }
        };
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE,
            "track: $Panel.background; hoverTrackColor: $Panel.background; pressedTrackColor: $Panel.background");
        var sections = new JPanel(new BorderLayout(0, 6)); sections.setOpaque(false);
        var sectionTools = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 4, 0)); sectionTools.setOpaque(false);
        for (boolean open : new boolean[] {true, false}) {
            var button = new JButton(dev.jasper.app.platform.AppIcons.bundled(open ? "general/expandAll" : "general/collapseAll"));
            button.setToolTipText(open ? "Expand all sections" : "Collapse all sections");
            button.getAccessibleContext().setAccessibleName(button.getToolTipText());
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            button.addActionListener(event -> tree.expandSections(open)); sectionTools.add(button);
        }
        sections.add(sectionTools, BorderLayout.NORTH); sections.add(scroll, BorderLayout.CENTER);
        add(sections, BorderLayout.CENTER);
        status.getAccessibleContext().setAccessibleName("Shortcut search status");
        var bottom = new JPanel(new BorderLayout(0, 8)); bottom.setOpaque(false);
        bottom.add(editor, BorderLayout.CENTER); bottom.add(status, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        search.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() { if (!editingCapture) { captured = null; filter(); } }
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
        });
        record.addActionListener(event -> {
            editor.stopRecording();
            recording = !recording;
            search.setEditable(!recording);
            record.setText(recording ? "Stop recording" : "Record shortcut");
            if (recording) { captured = null; search.setText(""); }
            filter(); search.requestFocusInWindow();
        });
        clear.addActionListener(event -> { captured = null; search.setText(""); filter(); search.requestFocusInWindow(); });
        filter();
    }

    void setRows(List<ShortcutEntry> rows) { all = List.copyOf(rows); editor.setRows(all); filter(); }

    private void editRow(ShortcutEntry entry) {
        stopSearchRecording();
        editor.begin(entry); filter();
    }

    private void stopSearchRecording() {
        recording = false; search.setEditable(true); record.setText("Record shortcut"); filter();
    }

    private void filter() {
        visible = all.stream().filter(row -> ShortcutText.matches(row, search.getText(), captured)).toList();
        tree.showRows(visible, captured != null || !search.getText().isBlank());
        status.setText(recording ? "Press a shortcut to find it. Click Stop recording when finished."
            : visible.isEmpty() ? "No matching shortcuts. Try another name or key combination."
            : visible.size() + (visible.size() == 1 ? " shortcut" : " shortcuts") + " · Select an action to edit · Contextual keys are read-only");
    }

    boolean capture(KeyStroke stroke) {
        if (closed) return false;
        if (editor.capture(stroke)) return true;
        if (!recording) return false;
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
        if (closed || !(recording || editor.recording()) || type != KeyEvent.KEY_PRESSED || !belongs(event.getComponent())) return false;
        held.add(code); swallowTyped = true;
        if (!modifier(code)) capture(KeyStroke.getKeyStrokeForEvent(event));
        event.consume(); return true;
    }

    /** Losing focus can hide key releases from AWT; never retain physical keys across that boundary. */
    void focusLeftWindow() {
        held.clear(); swallowTyped = false;
        editor.stopRecording();
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

    @Override public void close() { closed = true; recording = false; editor.close(); uninstall(); javax.swing.ToolTipManager.sharedInstance().unregisterComponent(tree); }

}

package dev.jasper.app.shortcuthelp;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.WindowInput;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * The Keymap page body: a layout-only shell around kit views for the search row, the section tools, the shortcut tree,
 * the edit card and the status line, plus window-scoped key capture. Native dispatch exists only while mounted. EDT only.
 */
final class ShortcutPanel extends JPanel implements AutoCloseable {
    static final String SEARCH = "search";
    final ShortcutTree tree;
    final ShortcutEditPanel editor;
    private final KitView top, tools, list, footer;
    private List<ShortcutEntry> all = List.of();
    private List<ShortcutEntry> visible = List.of();
    private String query = "";
    private KeyStroke captured;
    private boolean recording;
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

    ShortcutPanel(KitContext context, boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer) {
        super(new BorderLayout(0, scaled(context, DesignMetrics.Role.KEYMAP_SECTION_GAP)));
        setOpaque(false);
        tree = new ShortcutTree(macOs);
        editor = new ShortcutEditPanel(context, macOs, writer, this::stopSearchRecording);
        int side = scaled(context, DesignMetrics.Role.KEYMAP_PAGE_PADDING);
        setBorder(BorderFactory.createEmptyBorder(side, side, scaled(context, DesignMetrics.Role.KEYMAP_PAGE_BOTTOM_PADDING), side));
        top = new KitView(context, topSpec(), this::event);
        tools = new KitView(context, toolsSpec(), this::event);
        list = new KitView(context, new ViewSpec(tree.descriptor()), this::event);
        footer = new KitView(context, statusSpec(), this::event);
        add(top, BorderLayout.NORTH);
        var sections = new JPanel(new BorderLayout(0, scaled(context, DesignMetrics.Role.KEYMAP_TOOLS_GAP)));
        sections.setOpaque(false);
        sections.add(tools, BorderLayout.NORTH);
        sections.add(list, BorderLayout.CENTER);
        add(sections, BorderLayout.CENTER);
        var bottom = new JPanel(new BorderLayout(0, scaled(context, DesignMetrics.Role.KEYMAP_STATUS_GAP)));
        bottom.setOpaque(false);
        bottom.add(editor, BorderLayout.CENTER);
        bottom.add(footer, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        filter();
    }

    private static int scaled(KitContext context, DesignMetrics.Role role) { return UIScale.scale(context.metrics().value(role)); }

    void setRows(List<ShortcutEntry> rows) { all = List.copyOf(rows); editor.setRows(all); filter(); }

    /** Puts {@code text} in the search field, as typing it would. */
    void setQuery(String text) { captured = null; query = text; filter(); }
    String query() { return query; }

    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(SEARCH) -> {
                if (recording) return;
                captured = null; query = edited.text(); filter();
            }
            case UiEvent.Action action -> {
                switch (action.id()) {
                    case "record" -> {
                        editor.stopRecording();
                        recording = !recording;
                        if (recording) { captured = null; query = ""; }
                        filter();
                        top.focus(SEARCH);
                    }
                    case "clear" -> { captured = null; query = ""; filter(); top.focus(SEARCH); }
                    case "expand" -> { tree.expandSections(true); render(); }
                    case "collapse" -> { tree.expandSections(false); render(); }
                    case "edit" -> { if (!(EventQueue.getCurrentEvent() instanceof MouseEvent)) activate(tree.selected()); }
                    default -> { }
                }
            }
            case UiEvent.Selected selected when selected.id().equals(ShortcutTree.ID) -> {
                String id = selected.ids().stream().findFirst().orElse(null);
                tree.select(id);
                // A single click edits a shortcut or opens or closes a section, as the old tree did; arrow keys,
                // type-ahead and the second click of a double click only select. The kit reports Selected while it
                // dispatches the click, so the event being dispatched tells a click from a key.
                if (EventQueue.getCurrentEvent() instanceof MouseEvent mouse && mouse.getClickCount() == 1) activate(id);
                else render();
            }
            case UiEvent.Expanded expanded when expanded.id().equals(ShortcutTree.ID) -> { tree.expanded(expanded.itemId(), expanded.expanded()); render(); }
            default -> { }
        }
    }

    /** Enter or a click: a section opens or closes; an editable shortcut starts editing. */
    private void activate(String id) {
        if (id == null) return;
        if (tree.isGroup(id)) {
            tree.expanded(id, !tree.isOpenItem(id));
            render();
            return;
        }
        tree.entry(id).ifPresent(this::editRow);
        render();
    }

    private void editRow(ShortcutEntry entry) {
        stopSearchRecording();
        editor.begin(entry); filter();
    }

    private void stopSearchRecording() { recording = false; filter(); }

    private void filter() {
        visible = all.stream().filter(row -> ShortcutText.matches(row, query, captured)).toList();
        tree.showRows(visible, captured != null || !query.isBlank());
        render();
    }

    private void render() {
        if (closed) return;
        top.update(topSpec());
        tools.update(toolsSpec());
        list.update(new ViewSpec(tree.descriptor()));
        footer.update(statusSpec());
        revalidate();
        repaint();
    }

    private ViewSpec topSpec() {
        var field = new Nodes.Field(SEARCH, new ControlState(true, !recording, false, ""), Nodes.FieldKind.SEARCH, query,
            "Search actions, plugins, or Cmd+Shift+H", "Find a shortcut", "");
        var buttons = new Nodes.Stack("search.actions", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Button("record", ControlState.READY, recording ? "Stop recording" : "Record shortcut", "", Nodes.ButtonKind.SECONDARY, "record", ""),
            new Nodes.Button("clear", ControlState.READY, "Clear", "", Nodes.ButtonKind.SECONDARY, "clear", "")));
        return new ViewSpec(new Nodes.FormRow("find", ControlState.READY, "Find a shortcut", "", field, Optional.of(buttons)));
    }

    private static ViewSpec toolsSpec() {
        return new ViewSpec(new Nodes.Stack("sections", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Button("expand", ControlState.READY, "", "chevron.down", Nodes.ButtonKind.GHOST, "expand", "Expand all sections"),
            new Nodes.Button("collapse", ControlState.READY, "", "collapse", Nodes.ButtonKind.GHOST, "collapse", "Collapse all sections"))));
    }

    private ViewSpec statusSpec() {
        String text = recording ? "Press a shortcut to find it. Click Stop recording when finished."
            : visible.isEmpty() ? "No matching shortcuts. Try another name or key combination."
            : visible.size() + (visible.size() == 1 ? " shortcut" : " shortcuts") + " · Select an action to edit · Contextual keys are read-only";
        return new ViewSpec(new Nodes.Text("status", ControlState.READY, text, Nodes.TextRole.DESCRIPTION));
    }

    boolean capture(KeyStroke stroke) {
        if (closed) return false;
        if (editor.capture(stroke)) return true;
        if (!recording) return false;
        captured = stroke;
        query = ShortcutText.format(stroke);
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
        recording = false;
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

    @Override public void close() {
        if (closed) return;
        recording = false; editor.close(); uninstall();
        closed = true;
        top.close(); tools.close(); list.close(); footer.close();
    }
}

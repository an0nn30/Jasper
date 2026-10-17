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
import dev.jasper.ui.swing.SettingsShell;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
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
    private final KeymapShape shape;
    private final Map<Integer,javax.swing.MenuElement> popupHeld = new java.util.HashMap<>();
    private javax.swing.MenuElement popupTypedOwner;
    private final KeyEventDispatcher dispatcher = this::dispatch;
    private final java.beans.PropertyChangeListener focusListener = event -> {
        if (SwingUtilities.getWindowAncestor(this) != event.getNewValue()) focusLeftWindow();
    };
    private final WindowInput.ShortcutRecorder nativeRecorder = new WindowInput.ShortcutRecorder(this::capture);
    private JRootPane mountedRoot;

    ShortcutPanel(KitContext context, boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer) {
        this(context, macOs, writer, KeymapShape.ROWS);
    }

    /** The page body in {@code shape}, the composition its page chose (spec 12.2). */
    ShortcutPanel(KitContext context, boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer, KeymapShape shape) {
        super(new BorderLayout(0, scaled(context, DesignMetrics.Role.KEYMAP_SECTION_GAP)));
        setOpaque(false);
        this.shape = shape;
        tree = new ShortcutTree(macOs, shape.columns());
        editor = new ShortcutEditPanel(context, macOs, writer, this::stopSearchRecording);
        int side = scaled(context, DesignMetrics.Role.KEYMAP_PAGE_PADDING);
        setBorder(BorderFactory.createEmptyBorder(scaled(context, DesignMetrics.Role.KEYMAP_PAGE_TOP_PADDING), side, scaled(context, DesignMetrics.Role.KEYMAP_PAGE_BOTTOM_PADDING), side));
        top = new KitView(context, topSpec(), this::event);
        SettingsShell.settingsPage(top);
        tools = new KitView(context, toolsSpec(), this::event);
        list = new KitView(context, new ViewSpec(tree.descriptor()), this::event);
        footer = new KitView(context, statusSpec(), this::event);
        list.getAccessibleContext().setAccessibleName("Keyboard shortcuts grouped by section or plugin");
        // The tree has no activate action, so Enter passes on to this binding: it edits or toggles the selected row.
        list.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "keymap.activate");
        list.getActionMap().put("keymap.activate", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { if (!closed) activate(tree.selected()); }
        });
        footer.getAccessibleContext().setAccessibleName("Shortcut search status");
        // The shortcut search stands a little taller than an ordinary field; a larger font still grows it.
        var searchBar = new JPanel(new BorderLayout(scaled(context, DesignMetrics.Role.KEYMAP_TOOLS_GAP),0)) {
            @Override public java.awt.Dimension getPreferredSize() {
                var size = super.getPreferredSize();
                size.height = Math.max(size.height, scaled(context, DesignMetrics.Role.KEYMAP_SEARCH_HEIGHT));
                return size;
            }
        };
        searchBar.setOpaque(false);
        searchBar.add(shape.textTools() ? centred(top) : top,BorderLayout.CENTER); searchBar.add(tools,BorderLayout.EAST);
        add(searchBar, BorderLayout.NORTH);
        var sections = new JPanel(shape.textTools() ? new FittedLayout() : new BorderLayout());
        sections.setOpaque(false);
        sections.add(list, BorderLayout.CENTER);
        add(sections, BorderLayout.CENTER);
        // The status line sits further below the list than the list sits below the search row.
        var bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false); bottom.setBorder(BorderFactory.createEmptyBorder(scaled(context, DesignMetrics.Role.KEYMAP_STATUS_GAP), 0, 0, 0));
        bottom.add(footer, BorderLayout.CENTER);
        // The classic tree stands as tall as its rows, the status line just below it (the Keymap mock).
        if (shape.textTools()) sections.add(bottom); else add(bottom, BorderLayout.SOUTH);
        filter();
    }

    /** Rows, recorder and search actions wrap to the page; the status label is not a width constraint. */
    @Override public java.awt.Dimension getMinimumSize() {
        var minimum=super.getMinimumSize();var insets=getInsets();
        minimum.width=insets.left+insets.right;
        return minimum;
    }

    private static int scaled(KitContext context, DesignMetrics.Role role) { return UIScale.scale(context.metrics().value(role)); }

    /** {@code field} at its own height, centred on the taller tool buttons beside it (the Keymap mock's 22 against 24). */
    private static JPanel centred(JComponent field) {
        var holder = new JPanel(new java.awt.GridBagLayout());
        holder.setOpaque(false);
        var constraints = new java.awt.GridBagConstraints();
        constraints.weightx = 1; constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        holder.add(field, constraints);
        return holder;
    }

    /**
     * The classic list and status line: the list as tall as its rows, at most the room the status line leaves, and the
     * status line right below it.
     */
    private static final class FittedLayout implements java.awt.LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public java.awt.Dimension preferredLayoutSize(java.awt.Container parent) {
            int width = 0, height = 0;
            for (var child : parent.getComponents()) { var size = child.getPreferredSize(); width = Math.max(width, size.width); height += size.height; }
            return new java.awt.Dimension(width, height);
        }
        @Override public java.awt.Dimension minimumLayoutSize(java.awt.Container parent) { return new java.awt.Dimension(0, 0); }
        @Override public void layoutContainer(java.awt.Container parent) {
            var children = parent.getComponents();
            if (children.length < 2) return;
            int width = parent.getWidth(), status = children[1].getPreferredSize().height;
            int list = Math.max(0, Math.min(children[0].getPreferredSize().height, parent.getHeight() - status));
            children[0].setBounds(0, 0, width, list);
            children[1].setBounds(0, list, width, status);
        }
    }

    void setRows(List<ShortcutEntry> rows) { all = List.copyOf(rows); editor.setRows(all); filter(); }

    /** Puts {@code text} in the search field, as typing it would. */
    void setQuery(String text) { captured = null; query = text; filter(); }
    String query() { return query; }

    void event(UiEvent event) {
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
                    default -> { }
                }
            }
            // Keys only select; a plain left click (once, even in a double click) or Enter edits a shortcut or opens or
            // closes a section, as the old tree did. A right click never reaches here.
            case UiEvent.Selected selected when selected.id().equals(ShortcutTree.ID) -> {
                tree.select(selected.ids().stream().findFirst().orElse(null));
                render();
            }
            case UiEvent.Clicked clicked when clicked.id().equals(ShortcutTree.ID) -> activate(clicked.itemId());
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
        if(tree.visibleIds().contains(ShortcutTree.actionId(entry)))editor.attach(list,ShortcutTree.actionId(entry));
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
        top.setFieldTrailing(SEARCH, shape.textTools() ? Optional.empty()
            : Optional.of(new Nodes.Button("record", ControlState.READY, recording ? "Stop recording" : "Record", "keyboard", Nodes.ButtonKind.GHOST, "record", "Record shortcut", Nodes.ButtonPresentation.EMBEDDED)));
        tools.update(toolsSpec());
        list.update(new ViewSpec(tree.descriptor()));
        list.setTreeRowPresentation(ShortcutTree.ID,tree.presentation());
        footer.update(statusSpec());
        revalidate();
        repaint();
    }

    private ViewSpec topSpec() {
        // While recording, the field shows the query and takes no typing (spec 8.2's Field.readOnly).
        return new ViewSpec(new Nodes.Field(SEARCH, ControlState.READY, Nodes.FieldKind.SEARCH, query,
            "Search actions, plugins, or Cmd+Shift+H", "Search keyboard shortcuts", "", Optional.empty(), Nodes.FieldPresentation.STANDARD, "", "", "", recording));
    }

    /** Clear appears beside the section tools once there is a query or a recorded key to clear. */
    ViewSpec toolsSpec() {
        var clear = new Nodes.Button("clear", new ControlState(captured != null || !query.isEmpty(), true, false, ""), "Clear", "", Nodes.ButtonKind.SECONDARY, "clear", "");
        // Spec 12.2: the classic tools are text buttons; Find by Shortcut is the recording search.
        if (shape.textTools()) return new ViewSpec(new Nodes.Stack("sections", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(clear,
            new Nodes.Button("record", ControlState.READY, recording ? "Stop" : "Find by Shortcut", "", Nodes.ButtonKind.SECONDARY, "record", "Find an action by its shortcut"),
            new Nodes.Button("expand", ControlState.READY, "Expand All", "", Nodes.ButtonKind.SECONDARY, "expand", ""),
            new Nodes.Button("collapse", ControlState.READY, "Collapse All", "", Nodes.ButtonKind.SECONDARY, "collapse", ""))));
        return new ViewSpec(new Nodes.Stack("sections", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(clear,
            new Nodes.Button("expand", ControlState.READY, "", "expand.all", Nodes.ButtonKind.GHOST, "expand", "Expand all sections"),
            new Nodes.Button("collapse", ControlState.READY, "", "collapse.all", Nodes.ButtonKind.GHOST, "collapse", "Collapse all sections"))));
    }

    ViewSpec statusSpec() {
        if (recording || visible.isEmpty()) {
            String text = recording ? "Press a shortcut to find it. Click " + (shape.textTools() ? "Stop" : "Stop recording") + " when finished."
                : "No matching shortcuts. Try another name or key combination.";
            return new ViewSpec(new Nodes.Text("status", ControlState.READY, text, Nodes.TextRole.CAPTION));
        }
        String count = visible.size() + (visible.size() == 1 ? " shortcut" : " shortcuts");
        // Spec 12.2: the classic status is one caption line, without the lock glyph.
        if (shape.textTools()) return new ViewSpec(new Nodes.Text("status", ControlState.READY, count + " · Contextual keys are read-only", Nodes.TextRole.CAPTION));
        // The count, then the read-only marker that the contextual rows carry, explained.
        return new ViewSpec(new Nodes.Stack("status", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Text("status.count", ControlState.READY, count, Nodes.TextRole.CAPTION),
            new Nodes.Text("status.separator", ControlState.READY, "·", Nodes.TextRole.CAPTION),
            new Nodes.Stack("status.contextual-line", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
                new Nodes.Icon("status.lock", ControlState.READY, "lock", Nodes.Hue.NEUTRAL, Nodes.IconSize.CAPTION, "Read-only contextual key"),
                new Nodes.Text("status.contextual", ControlState.READY, "Contextual keys are read-only", Nodes.TextRole.CAPTION))))));
    }

    boolean capture(KeyStroke stroke) {
        if (closed) return false;
        if (javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length>0) return false;
        if (!recording && editor.gesture(stroke)) return true;
        if (editor.capture(stroke)) return true;
        if (!recording) return false;
        captured = stroke;
        query = ShortcutText.format(stroke);
        filter(); return true;
    }

    boolean dispatch(KeyEvent event) {
        int type = event.getID(), code = event.getKeyCode();
        var popupPath=javax.swing.MenuSelectionManager.defaultManager().getSelectedPath();
        var popupOwner=type==KeyEvent.KEY_TYPED ? popupTypedOwner
                : type==KeyEvent.KEY_RELEASED ? popupHeld.remove(code) : popupHeld.get(code);
        if (popupOwner!=null) {
            if (type==KeyEvent.KEY_PRESSED) {popupTypedOwner=popupOwner;swallowTyped=false;}
            if (type==KeyEvent.KEY_RELEASED && popupHeld.isEmpty()) popupTypedOwner=null;
            // Repeats still navigate their original open menu; after it closes, drain without reinterpreting.
            if (java.util.Arrays.asList(popupPath).contains(popupOwner)) return false;
            event.consume();return true;
        }
        boolean tail = type == KeyEvent.KEY_RELEASED ? held.remove(code)
            : type == KeyEvent.KEY_TYPED ? swallowTyped && !held.isEmpty()
            : type == KeyEvent.KEY_PRESSED && held.contains(code);
        if (tail) {
            if(type==KeyEvent.KEY_PRESSED) {swallowTyped=true;popupTypedOwner=null;}
            event.consume(); return true;
        }
        if (closed || type != KeyEvent.KEY_PRESSED || !belongs(event.getComponent())) return false;
        swallowTyped=false;popupTypedOwner=null;
        if(popupPath.length>0) {
            popupHeld.put(code,popupPath[0]);popupTypedOwner=popupPath[0];return false;
        }
        if(!recording && editor.auxiliaryKey(event.getComponent(),KeyStroke.getKeyStrokeForEvent(event)))return false;
        if(!(recording || editor.recording() || editor.special(KeyStroke.getKeyStrokeForEvent(event))))return false;
        held.add(code); swallowTyped = true;
        if (!modifier(code)) capture(KeyStroke.getKeyStrokeForEvent(event));
        event.consume(); return true;
    }

    /** Losing focus can hide key releases from AWT; never retain physical keys across that boundary. */
    void focusLeftWindow() {
        held.clear(); swallowTyped = false; popupHeld.clear(); popupTypedOwner=null;
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

    @Override public void removeNotify() { recording=false; editor.dismiss(); uninstall(); super.removeNotify(); }

    private void uninstall() {
        if (installed) KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
        if (installed) KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusedWindow", focusListener);
        installed = false;
        if (mountedRoot != null && mountedRoot.getClientProperty(WindowInput.SHORTCUT_RECORDER) == nativeRecorder)
            mountedRoot.putClientProperty(WindowInput.SHORTCUT_RECORDER, null);
        mountedRoot = null; held.clear(); swallowTyped = false; popupHeld.clear(); popupTypedOwner=null;
    }

    @Override public void close() {
        if (closed) return;
        recording = false; editor.close(); uninstall();
        closed = true;
        top.close(); tools.close(); list.close(); footer.close();
    }
}

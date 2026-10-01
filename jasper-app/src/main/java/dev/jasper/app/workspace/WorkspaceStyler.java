package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ResolvedTheme;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Objects;
import javax.swing.*;

/** Observes a pane's live component graph and owns only its reversible workspace styles. EDT only. */
public final class WorkspaceStyler implements AutoCloseable {
    private static final String ROOT = "Jasper.workspaceStylerRoot";
    private static final String OWNER = "Jasper.workspaceStylerOwner";
    private static final String EXEMPT = "Jasper.workspace.exempt";
    private final JComponent root;
    private final IdentityHashMap<Component, Node> nodes = new IdentityHashMap<>();
    private final IdentityHashMap<Component, HierarchyListener> associationWatches = new IdentityHashMap<>();
    private ResolvedTheme theme;
    private int suspension;
    private long generation;
    private boolean reconciling;
    private boolean pending;
    private boolean queued;
    private boolean closed;

    public WorkspaceStyler(JComponent root) {
        requireEdt();
        this.root = Objects.requireNonNull(root);
        if (root.getClientProperty(ROOT) instanceof WorkspaceStyler)
            throw new IllegalStateException("Component already has a workspace styler");
        // Notify an enclosing owner before capturing any baseline values here.
        try {
            root.putClientProperty(ROOT, this);
            refresh();
        } catch (RuntimeException | Error failure) {
            try { close(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public void apply(ResolvedTheme theme) {
        requireEdt();
        if (closed) throw new IllegalStateException("Workspace styler is closed");
        this.theme = Objects.requireNonNull(theme);
        generation++;
        refresh();
    }

    public void beforeUiUpdate() {
        requireEdt();
        if (closed || suspension++ != 0) return;
        for (Node node : new ArrayList<>(nodes.values())) node.suspend();
    }

    public void afterUiUpdate() {
        requireEdt();
        if (closed || suspension == 0 || --suspension != 0) return;
        generation++;
        refresh();
        for (Node node : new ArrayList<>(nodes.values())) node.resume();
        refresh();
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        generation++;
        associationWatches.forEach(Component::removeHierarchyListener);
        associationWatches.clear();
        for (Node node : new ArrayList<>(nodes.values())) release(node);
        root.putClientProperty(ROOT, null);
    }

    private void refresh() {
        if (closed) return;
        pending = true;
        if (reconciling) return;
        reconciling = true;
        try {
            int passes = 0;
            while (pending && passes++ < 16) {
                pending = false;
                var wanted = new IdentityHashMap<Component, Boolean>();
                var associated = new IdentityHashMap<Component, Boolean>();
                collect(root, wanted, associated);
                reconcileAssociationWatches(associated);
                for (Node node : new ArrayList<>(nodes.values()))
                    if (!wanted.containsKey(node.component) || wanted.get(node.component) != node.full
                        || node.exempt != isExempt(node.component)) release(node);
                // Release all previous ownership before any new layer captures a baseline.
                for (Component c : wanted.keySet()) {
                    if (Boolean.TRUE.equals(wanted.get(c)) && c instanceof JComponent jc
                        && jc.getClientProperty(OWNER) instanceof WorkspaceStyler old && old != this) {
                        Node previous = old.nodes.get(c);
                        if (previous != null) old.release(previous);
                    }
                }
                for (var entry : wanted.entrySet()) {
                    Node node = nodes.get(entry.getKey());
                    if (node == null) {
                        node = new Node(entry.getKey(), entry.getValue());
                        nodes.put(entry.getKey(), node);
                        node.observe();
                    }
                    node.apply();
                }
            }
            if (pending) queueRefresh();
        } finally { reconciling = false; }
    }

    private void queueRefresh() {
        if (queued || closed) return;
        queued = true;
        long expected = generation;
        SwingUtilities.invokeLater(() -> {
            queued = false;
            if (closed) return;
            if (expected == generation) refresh();
            else if (pending) queueRefresh(); // Preserve work requested by a newer bounded pass.
        });
    }

    private void reconcileAssociationWatches(IdentityHashMap<Component, Boolean> wanted) {
        for (Component component : associationWatches.keySet().toArray(Component[]::new))
            if (!wanted.containsKey(component)) component.removeHierarchyListener(associationWatches.remove(component));
        for (Component component : wanted.keySet()) {
            if (associationWatches.containsKey(component)) continue;
            // The eligible source owns this narrow watch even while the associated component
            // is inside an excluded subtree. No style/property/container listeners are retained there.
            HierarchyListener listener = event -> {
                if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) refresh();
            };
            associationWatches.put(component, listener);
            component.addHierarchyListener(listener);
        }
    }

    private void collect(Component c, IdentityHashMap<Component, Boolean> wanted,
                         IdentityHashMap<Component, Boolean> associated) {
        if (c == null || wanted.containsKey(c) || transientBoundary(c)) return;
        boolean full = true;
        if (c instanceof JComponent jc) {
            full = !Boolean.TRUE.equals(jc.getClientProperty(EXEMPT))
                && !"chrome".equals(jc.getClientProperty("Jasper.zone"))
                && (jc == root || !(jc.getClientProperty(ROOT) instanceof WorkspaceStyler));
        }
        wanted.put(c, full);
        if (!full) return;
        if (c instanceof Container container)
            for (Component child : container.getComponents()) collect(child, wanted, associated);
        if (c instanceof JTable table) associated(table.getTableHeader(), wanted, associated);
        if (c instanceof JComboBox<?> combo && combo.isEditable() && combo.getEditor() != null)
            associated(combo.getEditor().getEditorComponent(), wanted, associated);
    }

    private void associated(Component c, IdentityHashMap<Component, Boolean> wanted,
                            IdentityHashMap<Component, Boolean> associated) {
        if (c == null) return;
        associated.put(c, true);
        // Physical ownership wins over a stale association after reparenting.
        for (Component parent = c.getParent(); parent != null; parent = parent.getParent()) {
            if (transientBoundary(parent) || isExempt(parent)) return;
            if (parent instanceof JComponent jc) {
                if ("chrome".equals(jc.getClientProperty("Jasper.zone"))) return;
                if (jc != root && jc.getClientProperty(ROOT) instanceof WorkspaceStyler owner && owner != this) return;
                if (jc.getClientProperty(OWNER) instanceof WorkspaceStyler owner && owner != this) return;
            }
        }
        collect(c, wanted, associated);
    }

    private static boolean isExempt(Component c) {
        return c instanceof JComponent jc && Boolean.TRUE.equals(jc.getClientProperty(EXEMPT));
    }

    private static boolean transientBoundary(Component c) {
        return c instanceof JPopupMenu || c instanceof MenuElement || c instanceof JToolTip
            || c instanceof JRootPane || c instanceof Window || c instanceof CellRendererPane;
    }

    private void release(Node node) {
        nodes.remove(node.component);
        node.detach();
    }

    private final class Node {
        final Component component;
        final boolean full;
        final boolean exempt;
        final PropertyChangeListener properties = event -> {
            String name = event.getPropertyName();
            if (EXEMPT.equals(name) || "Jasper.zone".equals(name) || ROOT.equals(name)
                || "UI".equals(name) || "viewport".equals(name) || "rowHeader".equals(name)
                || "columnHeader".equals(name) || "tableHeader".equals(name) || "editor".equals(name)
                || "editable".equals(name) || "model".equals(name)) {
                applied = -1;
                refresh();
            }
        };
        final ContainerAdapter children = new ContainerAdapter() {
            @Override public void componentAdded(ContainerEvent e) { refresh(); }
            @Override public void componentRemoved(ContainerEvent e) { refresh(); }
        };
        WorkspaceStyleLayer layer;
        WorkspaceComboPopup popup;
        long applied = -1;
        boolean suspended;

        Node(Component component, boolean full) {
            this.component = component; this.full = full; this.exempt = isExempt(component);
        }

        void observe() {
            if (!full) {
                component.addPropertyChangeListener(EXEMPT, properties);
                if (!(component instanceof JComponent jc) || !Boolean.TRUE.equals(jc.getClientProperty(EXEMPT))) {
                    component.addPropertyChangeListener("Jasper.zone", properties);
                    component.addPropertyChangeListener(ROOT, properties);
                }
                return;
            }
            component.addPropertyChangeListener(properties);
            if (component instanceof Container container) container.addContainerListener(children);
            if (component instanceof JComponent jc) {
                jc.putClientProperty(OWNER, WorkspaceStyler.this);
                layer = new WorkspaceStyleLayer(jc);
                if (jc instanceof JComboBox<?> combo) popup = new WorkspaceComboPopup(combo);
                if (suspension != 0) suspend();
            }
        }

        void apply() {
            if (!full || layer == null || theme == null || applied == generation) return;
            applied = generation;
            if (suspension != 0) return;
            if (theme.matching()) layer.clear();
            else layer.apply(WorkspaceStyles.recipe((JComponent) component, theme.workspace()));
            if (popup != null) popup.apply(theme);
        }

        void suspend() {
            if (layer == null || suspended) return;
            suspended = true;
            layer.beforeUiUpdate();
            if (popup != null) popup.beforeUiUpdate();
        }

        void resume() {
            if (layer == null || !suspended) return;
            suspended = false;
            layer.afterUiUpdate();
            if (popup != null) popup.afterUiUpdate();
        }

        void detach() {
            component.removePropertyChangeListener(properties);
            component.removePropertyChangeListener(EXEMPT, properties);
            component.removePropertyChangeListener("Jasper.zone", properties);
            component.removePropertyChangeListener(ROOT, properties);
            if (component instanceof Container container) container.removeContainerListener(children);
            if (popup != null) popup.close();
            if (layer != null) layer.close();
            if (component instanceof JComponent jc && jc.getClientProperty(OWNER) == WorkspaceStyler.this)
                jc.putClientProperty(OWNER, null);
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Workspace styles require EDT");
    }
}

package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.appearance.Token;
import java.beans.PropertyChangeListener;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;

/**
 * Owns only a combo delegate's exposed dropdown; other popups remain chrome.
 * Custom UIs without a ComboPopup keep the combo recipe and must paint their own dropdown palette.
 */
final class WorkspaceComboPopup implements AutoCloseable {
    private static final String[] ZONE_KEYS = {"Jasper.zone", "Jasper.workspaceTheme",
        "Jasper.workspace.dark", "Jasper.workspace.foreground"};
    private final JComboBox<?> combo;
    private final IdentityHashMap<JComponent, WorkspaceStyleLayer> layers = new IdentityHashMap<>();
    private final Map<String, Object> originalZone = new LinkedHashMap<>();
    private final Map<String, Object> writtenZone = new LinkedHashMap<>();
    private final PropertyChangeListener listener = event -> {
        if ("UI".equals(event.getPropertyName())) refresh();
    };
    private ResolvedTheme theme;
    private JList<?> list;
    private int suspension;
    private boolean closed;
    private boolean refreshing;

    WorkspaceComboPopup(JComboBox<?> combo) {
        requireEdt();
        this.combo = Objects.requireNonNull(combo);
        combo.addPropertyChangeListener(listener);
    }

    void apply(ResolvedTheme theme) {
        requireEdt();
        if (closed) throw new IllegalStateException("Workspace combo popup is closed");
        this.theme = Objects.requireNonNull(theme);
        refresh();
    }

    void beforeUiUpdate() {
        requireEdt();
        if (closed || suspension++ != 0) return;
        release();
    }

    void afterUiUpdate() {
        requireEdt();
        if (closed || suspension == 0 || --suspension != 0) return;
        refresh();
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        combo.removePropertyChangeListener(listener);
        release();
    }

    private void refresh() {
        if (closed || suspension != 0 || refreshing) return;
        refreshing = true;
        try {
            if (theme == null || theme.matching()) { release(); return; }
            var child = combo.getUI() == null ? null : combo.getUI().getAccessibleChild(combo, 0);
            JList<?> next = child instanceof ComboPopup popup ? popup.getList() : null;
            if (next != list) { release(); list = next; }
            if (list == null) return;
            if (originalZone.isEmpty())
                for (String key : ZONE_KEYS) originalZone.put(key, list.getClientProperty(key));
            WorkspaceTheme.apply(list, theme);
            for (String key : ZONE_KEYS) writtenZone.put(key, list.getClientProperty(key));
            var wanted = new IdentityHashMap<JComponent, Boolean>();
            wanted.put(list, true);
            var scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, list);
            if (scroll != null) {
                wanted.put(scroll, true);
                if (scroll.getViewport() != null) wanted.put(scroll.getViewport(), true);
                if (scroll.getVerticalScrollBar() != null) wanted.put(scroll.getVerticalScrollBar(), true);
                if (scroll.getHorizontalScrollBar() != null) wanted.put(scroll.getHorizontalScrollBar(), true);
            }
            for (JComponent c : layers.keySet().toArray(JComponent[]::new))
                if (!wanted.containsKey(c)) layers.remove(c).close();
            for (JComponent c : wanted.keySet())
                layers.computeIfAbsent(c, WorkspaceStyleLayer::new).apply(recipe(c));
        } finally { refreshing = false; }
    }

    private WorkspaceStyles.Recipe recipe(JComponent component) {
        var recipe = WorkspaceStyles.recipe(component, theme.workspace());
        if (component != list) return recipe;
        // BasicComboPopup installs ComboBox selection defaults, which FlatListUI's
        // identity-based List-default replacement does not recognize.
        var direct = new LinkedHashMap<>(recipe.direct());
        direct.put("selectionBackground", theme.workspace().tokens().get(Token.SELECTION));
        direct.put("selectionForeground", theme.workspace().tokens().get(Token.SELECTION_TEXT));
        return new WorkspaceStyles.Recipe(recipe.type(), recipe.style(), direct);
    }

    private void release() {
        for (WorkspaceStyleLayer layer : layers.values()) layer.close();
        layers.clear();
        if (list != null) {
            for (String key : ZONE_KEYS)
                if (list.getClientProperty(key) == writtenZone.get(key)) list.putClientProperty(key, originalZone.get(key));
        }
        originalZone.clear(); writtenZone.clear(); list = null;
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Workspace styles require EDT");
    }
}

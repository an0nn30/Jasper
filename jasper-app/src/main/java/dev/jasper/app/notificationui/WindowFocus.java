package dev.jasper.app.notificationui;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Follows whether a component's window is the active one, across re-parenting. EDT only. */
final class WindowFocus {
    private WindowFocus() {}

    /** Calls {@code changed} with the window's state when it gets one, then on every activation change; returns the stop. */
    static Runnable track(Component component, Consumer<Boolean> changed) {
        var listener = new WindowAdapter() {
            @Override public void windowActivated(WindowEvent event) { changed.accept(true); }
            @Override public void windowDeactivated(WindowEvent event) { changed.accept(false); }
        };
        Window[] current = {null};
        Runnable sync = () -> {
            Window next = SwingUtilities.getWindowAncestor(component);
            if (next == current[0]) return;
            if (current[0] != null) current[0].removeWindowListener(listener);
            current[0] = next;
            if (next != null) {
                next.addWindowListener(listener);
                changed.accept(next.isActive());
            }
        };
        HierarchyListener hierarchy = event -> {
            if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) sync.run();
        };
        component.addHierarchyListener(hierarchy);
        sync.run();
        return () -> {
            component.removeHierarchyListener(hierarchy);
            if (current[0] != null) current[0].removeWindowListener(listener);
            current[0] = null;
        };
    }
}

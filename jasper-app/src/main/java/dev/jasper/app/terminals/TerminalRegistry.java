package dev.jasper.app.terminals;

import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * The open terminal windows and what happens in them. Structure is always read through the entries, so
 * it cannot go stale; the one thing derived here is the active pane: the focused pane of the selected tab
 * of the window the user used last. EDT only.
 */
public final class TerminalRegistry {
    private static final System.Logger LOG = System.getLogger(TerminalRegistry.class.getName());
    private final Map<UUID, WindowEntry> windows = new LinkedHashMap<>();
    private final List<Consumer<TerminalEvent>> listeners = new ArrayList<>();
    private UUID lastActive;
    private UUID reportedActivePane;
    private int holding;

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("The terminal registry belongs to the EDT");
    }

    /** Registers a window and announces it. Closing the subscription removes and announces that. */
    public Subscription addWindow(WindowEntry entry) {
        requireEdt();
        Objects.requireNonNull(entry, "entry");
        if (windows.putIfAbsent(entry.id(), entry) != null) throw new IllegalArgumentException("Window already registered: " + entry.id());
        publish(new TerminalEvent.WindowOpened(entry.id()));
        return new Subscription(() -> {
            if (windows.remove(entry.id()) == null) return;
            if (entry.id().equals(lastActive)) lastActive = null;
            publish(new TerminalEvent.WindowClosed(entry.id()));
        });
    }

    /** The user turned to this window. Unknown ids are ignored. */
    public void windowActivated(UUID windowId) {
        requireEdt();
        if (!windows.containsKey(windowId)) return;
        lastActive = windowId;
        publish(new TerminalEvent.WindowActivated(windowId));
    }

    /** Forwards a window's fact to every listener, then reports the active pane if it changed. */
    public void publish(TerminalEvent event) {
        requireEdt();
        Objects.requireNonNull(event, "event");
        emit(event);
        if (holding == 0) refresh();
    }

    /** Runs a change that publishes several facts, and looks at the active pane only when it is complete. */
    public void atomically(Runnable change) {
        requireEdt();
        holding++;
        try { change.run(); }
        finally { if (--holding == 0) refresh(); }
    }

    /** Reports the active pane if it changed without an event, for example by keyboard navigation inside a tab. */
    public void refresh() {
        requireEdt();
        if (holding > 0) return;
        UUID now = activePane().map(PaneEntry::id).orElse(null);
        if (Objects.equals(now, reportedActivePane)) return;
        reportedActivePane = now;
        emit(new TerminalEvent.ActivePaneChanged(Optional.ofNullable(now)));
    }

    private void emit(TerminalEvent event) {
        for (Consumer<TerminalEvent> listener : List.copyOf(listeners)) {
            try { listener.accept(event); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A terminal event listener failed", failure); }
        }
    }

    public Subscription onEvent(Consumer<TerminalEvent> listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    public List<WindowEntry> windows() { requireEdt(); return List.copyOf(windows.values()); }
    public Optional<WindowEntry> window(UUID id) { requireEdt(); return Optional.ofNullable(windows.get(id)); }
    public Optional<WindowEntry> activeWindow() { requireEdt(); return Optional.ofNullable(lastActive).map(windows::get); }

    public Optional<PaneEntry> activePane() {
        return activeWindow().flatMap(window -> window.selectedTab().get()).flatMap(tab -> tab.focusedPane().get());
    }

    public Optional<TabEntry> tab(UUID id) {
        requireEdt();
        for (WindowEntry window : windows.values())
            for (TabEntry tab : window.tabs().get()) if (tab.id().equals(id)) return Optional.of(tab);
        return Optional.empty();
    }

    public Optional<PaneEntry> pane(UUID id) {
        requireEdt();
        for (WindowEntry window : windows.values())
            for (TabEntry tab : window.tabs().get())
                for (PaneEntry pane : tab.panes().get()) if (pane.id().equals(id)) return Optional.of(pane);
        return Optional.empty();
    }
}

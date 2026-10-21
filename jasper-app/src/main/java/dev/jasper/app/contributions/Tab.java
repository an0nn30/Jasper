package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.layout.TabRestore;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.JComponent;

/**
 * One item of any window, the one a pane shows (workspace tabs spec 9.1; tabs and panes spec 3; the code calls an item
 * a tab): its id and kind, its content, its presentation (title, detail, icon, unsaved marker, location, host, status
 * items), its commands and focus target, and the window that holds it. Moving the
 * tab between windows never replaces this object or its content; only {@link TabRegistry#remove(Tab)} disposes it. EDT
 * only.
 */
public final class Tab implements AutoCloseable {
    /** What a tab holds. */
    public enum Kind {
        /** One terminal pane. */
        TERMINAL,
        /** A plugin's view. */
        HOSTED
    }

    /** The registry this tab lives in; a window's own until the window joins the application's. */
    TabRegistry registry;
    /** The window the registry is taking the tab out of while it moves it to another; null otherwise. */
    UUID movingFrom;
    private final TabId id;
    private final Kind kind;
    private final String plugin, key;
    private final TabContent content;
    private final List<Runnable> changed = new ArrayList<>(), disposed = new ArrayList<>();
    private final List<Consumer<Boolean>> activation = new ArrayList<>();
    private String title, detail = "", location = "", host = "";
    private Icon icon;
    private boolean dirty, closed, active;
    private int pins;
    private UUID window;
    private Runnable focus;
    private List<ContentCommand> commands = List.of();
    private List<ContentStatus> status = List.of();
    private Optional<TabRestore> restore = Optional.empty();

    Tab(TabRegistry registry, TabId id, Kind kind, String plugin, String key, String title, TabContent content) {
        this.registry = registry;
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.key = Objects.requireNonNull(key, "key");
        this.title = Objects.requireNonNull(title, "title");
        this.content = Objects.requireNonNull(content, "content");
        JComponent component = content.component();
        focus = component::requestFocusInWindow;
    }

    /** @return the tab's id, for the life of the tab */
    public TabId id() { return id; }
    /** @return what the tab holds */
    public Kind kind() { return kind; }
    /** @return the plugin that opened a hosted tab; empty for a terminal tab */
    public String plugin() { return plugin; }
    /** @return the plugin's key for a hosted tab; the id for a terminal tab */
    public String key() { return key; }
    /** @return the tab's content */
    public TabContent content() { return content; }
    /** @return the component the window shows, the content's */
    public JComponent component() { return content.component(); }
    public String title() { return title; }
    public String detail() { return detail; }
    public Icon icon() { return icon; }
    public String location() { return location; }
    public String host() { return host; }
    public boolean dirty() { return dirty; }
    public boolean closed() { return closed; }
    public boolean active() { return active; }
    /**
     * @return whether the registry is moving this tab between windows right now: a window that detaches or attaches it
     *     meanwhile sees a move, not a close or an open (workspace tabs spec 15)
     */
    public boolean moving() { return movingFrom != null; }
    /**
     * @return the window the registry is taking this tab out of while it moves it to another, else empty: the window it
     *     arrives in reports the move (workspace plan 3b Rulings 5)
     */
    public Optional<UUID> movingFrom() { return Optional.ofNullable(movingFrom); }
    /** @return the id of the window holding the tab, or null while no window does */
    public UUID window() { return window; }
    public List<ContentCommand> commands() { return commands; }
    public List<ContentStatus> status() { return status; }
    /**
     * How the item comes back when the windows are restored (workspace tabs spec 13).
     *
     * @return its plugin's restorer kind and restore state; empty while it does not come back
     */
    public Optional<TabRestore> restore() { return restore; }

    /**
     * How the tab comes back when the windows are restored, as its plugin says; a change is saved as a layout change is.
     * Ignored once the tab has closed.
     *
     * @param value the restorer kind and restore state, or empty
     */
    public void restore(Optional<TabRestore> value) {
        Objects.requireNonNull(value, "value");
        if (closed || value.equals(restore)) return;
        restore = value;
        registry.restoreChanged.run();
    }

    /**
     * The group that holds the tab in its window's layout.
     *
     * @return the group, or empty for a closed tab or a window that keeps no groups
     */
    public Optional<GroupId> group() { return closed ? Optional.empty() : registry.groupOf(this); }

    /** A modal owner keeps the tab where it is and open until the returned subscription closes. */
    public Subscription pin() { pins++; return new Subscription(() -> pins--); }
    public boolean pinned() { return pins > 0; }

    /** Sets the presentation and tells the listeners; ignored once closed. */
    public void presentation(String title, String detail, Icon icon, boolean dirty, String location, String host) {
        if (closed) return;
        this.title = Objects.requireNonNull(title); this.detail = Objects.requireNonNull(detail); this.icon = icon;
        this.dirty = dirty; this.location = Objects.requireNonNull(location); this.host = Objects.requireNonNull(host);
        fire();
    }
    public void commands(List<ContentCommand> value) { if (!closed) { commands = List.copyOf(value); fire(); } }
    public void status(List<ContentStatus> value) { if (!closed) { status = List.copyOf(value); fire(); } }
    /** What {@link #focus()} runs; the component's own focus request by default. */
    public void focusTarget(Runnable value) { if (!closed) focus = Objects.requireNonNull(value); }
    public void focus() { if (!closed) focus.run(); }
    /** Shows the tab in its window and raises the window, wherever the tab is now. */
    public void select() { registry.select(this); }
    /** A new full window takes the tab, as {@code ContentTab.moveToNewWindow()} asks (tabs and panes spec 13). */
    public void moveToNewWindow() { registry.moveToNewWindow(this); }
    /** The main window takes the tab, as {@code ContentTab.moveToMainWindow()} asks (workspace tabs spec 6.5). */
    public void moveToMainWindow() { registry.moveToMainWindow(this); }
    /** Closes the tab once its plugin's guard allows it. */
    public CompletableFuture<Boolean> requestClose() { return registry.requestClose(List.of(this)); }

    public Subscription onChanged(Runnable listener) {
        if (closed) return new Subscription(() -> {});
        changed.add(listener);
        return new Subscription(() -> changed.remove(listener));
    }
    public Subscription onClosed(Runnable listener) {
        if (closed) return new Subscription(() -> {});
        disposed.add(listener);
        return new Subscription(() -> disposed.remove(listener));
    }
    public Subscription onActive(Consumer<Boolean> listener) {
        if (closed) return new Subscription(() -> {});
        activation.add(listener);
        return new Subscription(() -> activation.remove(listener));
    }
    /** Whether the tab is its window's active tab; the window sets it. */
    public void active(boolean value) {
        if (closed || active == value) return;
        active = value;
        for (var listener : List.copyOf(activation)) listener.accept(value);
    }

    void window(UUID value) { window = value; fire(); }

    private void fire() { for (var listener : List.copyOf(changed)) listener.run(); }

    /**
     * Marks the tab closed, tells its activation and close listeners, then releases its content. A listener that throws
     * keeps neither the other listeners nor the content from being told; the first failure is rethrown at the end, the
     * others suppressed under it.
     */
    void dispose() {
        if (closed) return;
        Throwable failure = null;
        if (active) {
            active = false;
            failure = tell(activation, listener -> listener.accept(false), failure);
        }
        closed = true;
        failure = tell(disposed, Runnable::run, failure);
        changed.clear(); disposed.clear(); activation.clear();
        try { content.dispose(); }
        catch (RuntimeException | Error thrown) { failure = keep(failure, thrown); }
        rethrow(failure);
    }

    private static <T> Throwable tell(List<T> listeners, Consumer<T> call, Throwable failure) {
        for (var listener : List.copyOf(listeners)) {
            try { call.accept(listener); }
            catch (RuntimeException | Error thrown) { failure = keep(failure, thrown); }
        }
        return failure;
    }

    /**
     * The failure to throw once the cleanup is done: the first one, with each later one suppressed under it; the first
     * thrown again is not suppressed under itself.
     */
    static Throwable keep(Throwable first, Throwable later) {
        if (first == null) return later;
        if (first != later) first.addSuppressed(later);
        return first;
    }

    /** Throws {@code failure}, which {@code keep} collected from runtime exceptions and errors only; null throws nothing. */
    static void rethrow(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure != null) throw (RuntimeException) failure;
    }

    /** Removes the tab from its registry at once, without asking a guard. */
    @Override public void close() { registry.remove(this); }

    @Override public String toString() { return "Tab[" + kind + " " + id + " " + title + "]"; }
}

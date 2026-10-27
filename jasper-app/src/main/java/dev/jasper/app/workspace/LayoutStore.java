package dev.jasper.app.workspace;

import dev.jasper.app.persistence.LayoutsFile;
import dev.jasper.app.persistence.LayoutsFile.Layouts;
import dev.jasper.app.persistence.LayoutsFile.Named;
import dev.jasper.app.persistence.WorkspaceFile.WindowRecord;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.swing.SwingUtilities;

/**
 * The saved layouts (full windows plan 4): {@code layouts.toml}, read once, lazily, and rewritten on {@code writer}
 * after every change, each write after the one before it. A write that fails or that the writer refuses is logged and
 * costs only that write. EDT only.
 */
public final class LayoutStore {
    /** The longest a layout name may be. */
    public static final int MAX_NAME = 64;
    private static final System.Logger LOG = System.getLogger(LayoutStore.class.getName());

    private final Path file;
    private final Executor writer;
    private final List<Runnable> listeners = new ArrayList<>();
    private Layouts layouts;
    private volatile CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);

    /**
     * The store of {@code file}.
     *
     * @param file {@code layouts.toml}
     * @param writer where writes run, off the EDT
     */
    public LayoutStore(Path file, Executor writer) {
        this.file = Objects.requireNonNull(file, "file");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /**
     * The name {@code typed} makes: trimmed, 1 to {@value #MAX_NAME} characters.
     *
     * @param typed what the user typed
     * @return the name, or empty when it is blank or too long
     */
    public static Optional<String> validName(String typed) {
        String name = typed.strip();
        return name.isEmpty() || name.length() > MAX_NAME ? Optional.empty() : Optional.of(name);
    }

    /** @return the saved layouts */
    public Layouts layouts() {
        requireEdt();
        if (layouts == null) layouts = LayoutsFile.read(file);
        return layouts;
    }

    /** @return the layout a new window opens with, if one is saved */
    public Optional<WindowRecord> defaultLayout() { return layouts().defaultLayout(); }

    /** @return the named layouts in order */
    public List<Named> named() { return layouts().named(); }

    /**
     * Saves {@code window} as the default layout.
     *
     * @param window the window's state; its bounds are not kept
     */
    public void saveAsDefault(WindowRecord window) {
        change(new Layouts(Optional.of(window), named()));
    }

    /** Forgets the saved default layout. */
    public void resetDefault() {
        change(new Layouts(Optional.empty(), named()));
    }

    /**
     * Saves {@code window} under {@code name}.
     *
     * @param name a valid name ({@link #validName})
     * @param window the window's state; its bounds are not kept
     * @return false, and nothing is saved, when the name is in use
     */
    public boolean saveAsNew(String name, WindowRecord window) {
        if (inUse(name)) return false;
        var all = new ArrayList<>(named());
        all.add(new Named(name, window));
        change(new Layouts(defaultLayout(), all));
        return true;
    }

    /**
     * Renames a layout.
     *
     * @param from its name
     * @param to the new, valid name
     * @return false, and nothing changes, when there is no layout {@code from} or {@code to} is in use by another
     */
    public boolean rename(String from, String to) {
        List<Named> all = named();
        int index = indexOf(from);
        if (index < 0 || (!from.equals(to) && inUse(to))) return false;
        var changed = new ArrayList<>(all);
        changed.set(index, new Named(to, all.get(index).layout()));
        change(new Layouts(defaultLayout(), changed));
        return true;
    }

    /**
     * Deletes a layout; nothing happens when there is none of that name.
     *
     * @param name its name
     */
    public void delete(String name) {
        int index = indexOf(name);
        if (index < 0) return;
        var changed = new ArrayList<>(named());
        changed.remove(index);
        change(new Layouts(defaultLayout(), changed));
    }

    /**
     * Runs {@code listener} after every change.
     *
     * @param listener what to run
     */
    public void onChanged(Runnable listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** @return completes, never exceptionally, once every write asked for so far has finished; any thread */
    public CompletableFuture<Void> written() { return writes.handle((done, failure) -> (Void) null); }

    private boolean inUse(String name) { return indexOf(name) >= 0; }

    private int indexOf(String name) {
        List<Named> all = named();
        for (int i = 0; i < all.size(); i++) if (all.get(i).name().equals(name)) return i;
        return -1;
    }

    private void change(Layouts changed) {
        layouts = changed;
        writes = writes.handle((done, failure) -> (Void) null).thenRunAsync(() -> write(changed), writer)
            .whenComplete((done, refused) -> {
                if (refused != null) LOG.log(System.Logger.Level.WARNING, "Could not save the layouts to " + file, refused);
            });
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private void write(Layouts writing) {
        try {
            LayoutsFile.write(file, writing);
        } catch (IOException | RuntimeException | Error failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not save the layouts to " + file, failure);
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("The layout store belongs to the EDT");
    }
}

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
    /** What a save or a rename did. */
    public enum Saved {
        /** It was saved. */
        SAVED,
        /** Nothing changed: another layout has the name. */
        NAME_IN_USE,
        /** Nothing changed: there is no layout of the name to rename. */
        MISSING,
        /** Nothing changed: {@code layouts.toml} would pass {@link LayoutsFile#MAX_BYTES}. */
        TOO_LARGE
    }

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
     * @return {@link Saved#SAVED}, or {@link Saved#TOO_LARGE} and nothing changes
     */
    public Saved saveAsDefault(WindowRecord window) {
        return change(new Layouts(Optional.of(window), named()));
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
     * @return {@link Saved#SAVED}; else {@link Saved#NAME_IN_USE} or {@link Saved#TOO_LARGE}, and nothing is saved
     */
    public Saved saveAsNew(String name, WindowRecord window) {
        if (inUse(name)) return Saved.NAME_IN_USE;
        var all = new ArrayList<>(named());
        all.add(new Named(name, window));
        return change(new Layouts(defaultLayout(), all));
    }

    /**
     * Renames a layout.
     *
     * @param from its name
     * @param to the new, valid name
     * @return {@link Saved#SAVED}; else {@link Saved#MISSING} when there is no layout {@code from},
     *     {@link Saved#NAME_IN_USE} when another has {@code to}, or {@link Saved#TOO_LARGE}, and nothing changes
     */
    public Saved rename(String from, String to) {
        List<Named> all = named();
        int index = indexOf(from);
        if (index < 0) return Saved.MISSING;
        if (!from.equals(to) && inUse(to)) return Saved.NAME_IN_USE;
        var changed = new ArrayList<>(all);
        changed.set(index, new Named(to, all.get(index).layout()));
        return change(new Layouts(defaultLayout(), changed));
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

    /** Keeps {@code changed} and writes it, unless it is too large to write; deleting and resetting always fit. */
    private Saved change(Layouts changed) {
        if (!LayoutsFile.fits(changed)) return Saved.TOO_LARGE;
        layouts = changed;
        writes = writes.handle((done, failure) -> (Void) null).thenRunAsync(() -> write(changed), writer)
            .whenComplete((done, refused) -> {
                if (refused != null) LOG.log(System.Logger.Level.WARNING, "Could not save the layouts to " + file, refused);
            });
        for (Runnable listener : List.copyOf(listeners)) listener.run();
        return Saved.SAVED;
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

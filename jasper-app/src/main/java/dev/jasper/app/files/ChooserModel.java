package dev.jasper.app.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The chooser without Swing: the place shown (a folder in some source's root), back and forward history across
 * sources, connecting to roots, the listing, filter, sort order, selection, previews and what the primary button does.
 * Browsers come from the sources and are kept until {@link #close()}. Browser calls run on {@code worker}; results
 * are applied through {@code ui} and dropped when a newer request has started. UI thread only.
 */
public final class ChooserModel {
    /** Sort keys; folders always come first. */
    public enum Column { NAME, MODIFIED, SIZE }

    /** What the primary button, Enter in the name field, or opening an entry leads to. */
    public sealed interface Outcome {
        /** A folder is now shown. */
        record Navigated(Location folder) implements Outcome { }
        /** The choice is made. */
        record Chosen(List<Location> locations) implements Outcome { }
        /** Saving would replace an existing file: ask first, then treat it as chosen. */
        record ConfirmReplace(Location file) implements Outcome { }
        /** Nothing can be chosen; the message says why. */
        record Invalid(String message) implements Outcome { }
    }

    static final int PREVIEW_BYTES = 8 * 1024;
    static final int BATCH = 256;
    private final ChooserRequest request;
    private final Map<String, ChooserSource> sources = new LinkedHashMap<>();
    private final Executor worker;
    private final Executor ui;
    private final Map<String, Browser> browsers = new HashMap<>();
    private final Deque<Location> back = new ArrayDeque<>();
    private final Deque<Location> forward = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private Location location;
    private Browser browser;
    private CompletableFuture<Browser> connecting;
    private Optional<String> connectingTo = Optional.empty();
    private List<ChooserEntry> listing = List.of();
    private List<ChooserEntry> visible = List.of();
    private List<ChooserEntry> selection = List.of();
    private String filter = "";
    private boolean showHidden;
    private Column sortColumn = Column.NAME;
    private boolean ascending = true;
    private boolean loading;
    private boolean retryable;
    private Optional<String> error = Optional.empty();
    private Optional<PreviewInfo> preview = Optional.empty();
    private Optional<String> pendingSelection = Optional.empty();
    private long generation;
    private long previewGeneration;
    private boolean closed;

    /**
     * @param sources where the chooser can browse; the local source must be among them
     * @param worker runs browser calls, one at a time
     * @param ui applies results on the UI thread
     * @param showHidden whether dotfiles are listed at first
     */
    public ChooserModel(ChooserRequest request, List<ChooserSource> sources, Executor worker, Executor ui, boolean showHidden) {
        this.request = Objects.requireNonNull(request);
        for (ChooserSource source : sources) this.sources.putIfAbsent(source.id(), source);
        this.worker = Objects.requireNonNull(worker);
        this.ui = Objects.requireNonNull(ui);
        this.showHidden = showHidden;
        this.location = request.start();
        if (request.mode() == ChooserRequest.Mode.OPEN)
            pendingSelection = request.suggestedName().filter(name -> !name.contains("/") && !name.contains(File.separator));
    }

    /** Opens the start location. */
    public void start() { show(location); }

    /** Runs {@code listener} after every change. */
    public void addListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    public ChooserRequest request() { return request; }
    public List<ChooserSource> sources() { return List.copyOf(sources.values()); }
    public Location location() { return location; }
    /** The listing after the dotfile, mode, suffix and search filters, sorted. */
    public List<ChooserEntry> entries() { return visible; }
    public List<ChooserEntry> selection() { return selection; }
    public boolean loading() { return loading; }
    /** The root being connected to, by label. */
    public Optional<String> connectingTo() { return connectingTo; }
    public Optional<String> error() { return error; }
    /** Whether {@link #retry()} may help with the error. */
    public boolean retryable() { return retryable; }
    public Optional<PreviewInfo> preview() { return preview; }
    public String filter() { return filter; }
    public boolean showHidden() { return showHidden; }
    public Column sortColumn() { return sortColumn; }
    public boolean ascending() { return ascending; }
    public boolean canGoBack() { return !back.isEmpty(); }
    public boolean canGoForward() { return !forward.isEmpty(); }
    public boolean canGoUp() { return parent(location).isPresent(); }

    /** The folder containing {@code place}, when its root is open. */
    public Optional<Location> parent(Location place) {
        Browser open = browsers.get(key(place));
        if (open == null || place.path().equals(Location.HOME)) return Optional.empty();
        try { return open.parent(place.path()).map(place::at); }
        catch (RuntimeException failed) { return Optional.empty(); }
    }

    /** A location's last path element; the root's label while it is not open. */
    public String name(Location place) {
        Browser open = browsers.get(key(place));
        if (open == null || place.path().equals(Location.HOME)) return rootLabel(place);
        try { return open.name(place.path()); }
        catch (RuntimeException failed) { return place.path(); }
    }

    /** The label of a location's root, such as a host's name. */
    public String rootLabel(Location place) {
        ChooserSource source = sources.get(place.source());
        if (source == null) return place.root();
        try {
            return source.roots().stream().filter(root -> root.id().equals(place.root())).map(ChooserSource.Root::label).findFirst().orElse(place.root());
        } catch (RuntimeException failed) {
            return place.root();
        }
    }

    /** The folders from the root down to the one shown, for the path bar. */
    public List<Location> chain() {
        var chain = new ArrayDeque<Location>();
        Location at = location;
        for (int depth = 0; at != null && depth < 256; depth++) {
            chain.addFirst(at);
            at = parent(at).orElse(null);
        }
        return List.copyOf(chain);
    }

    /** Shows {@code target}, recording the current place for Back; the place already shown is listed again. */
    public void navigate(Location target) {
        if (!target.equals(location)) {
            back.push(location);
            forward.clear();
        }
        pendingSelection = Optional.empty();
        filter = "";
        show(target);
    }

    public void goBack() {
        if (back.isEmpty()) return;
        forward.push(location);
        filter = "";
        show(back.pop());
    }

    public void goForward() {
        if (forward.isEmpty()) return;
        back.push(location);
        filter = "";
        show(forward.pop());
    }

    public void goUp() { parent(location).ifPresent(this::navigate); }

    public void refresh() { show(location); }

    /** Tries the place shown again, reconnecting when its root is not open. */
    public void retry() { show(location); }

    /** Abandons a connection in progress. */
    public void cancelConnection() {
        if (connecting == null) return;
        generation++;
        connecting.cancel(true);
        connecting = null;
        connectingTo = Optional.empty();
        error = Optional.of("Connection cancelled.");
        retryable = true;
        fire();
    }

    /** Shows only entries whose names contain {@code value}, ignoring case. */
    public void setFilter(String value) {
        filter = Objects.requireNonNull(value);
        recompute();
        fire();
    }

    public void setShowHidden(boolean value) {
        showHidden = value;
        recompute();
        fire();
    }

    /** Sorts by {@code column}; choosing the current column again reverses the order. */
    public void sortBy(Column column) {
        if (column == sortColumn) ascending = !ascending;
        else { sortColumn = column; ascending = true; }
        recompute();
        fire();
    }

    /** Sorts by {@code column} in the given direction; the chooser restores its remembered sort with it. */
    public void sortBy(Column column, boolean ascending) {
        sortColumn = Objects.requireNonNull(column);
        this.ascending = ascending;
        recompute();
        fire();
    }

    /** Selects visible entries; without multiple selection only the first is kept. */
    public void select(List<ChooserEntry> entries) {
        List<ChooserEntry> chosen = entries.stream().filter(visible::contains).toList();
        if (!request.multiple() && chosen.size() > 1) chosen = List.of(chosen.getFirst());
        if (chosen.equals(selection)) return;
        selection = chosen;
        preview = Optional.empty();
        previewGeneration++;
        fire();
    }

    /**
     * The name-field text for the selection, or empty to leave the field alone: the selected files in Open (each quoted
     * when there are several), a selected folder in Directory mode, a selected file in Save.
     */
    public Optional<String> nameForSelection() {
        if (selection.isEmpty()) return Optional.empty();
        return switch (request.mode()) {
            case OPEN -> {
                List<String> names = selection.stream().filter(entry -> !entry.directory()).map(ChooserEntry::name).toList();
                yield names.isEmpty() ? Optional.empty() : Optional.of(names.size() == 1 ? names.getFirst()
                    : names.stream().map(name -> "\"" + name + "\"").collect(Collectors.joining(" ")));
            }
            case DIRECTORY -> selection.getFirst().directory() ? Optional.of(selection.getFirst().name()) : Optional.empty();
            case SAVE -> selection.getFirst().directory() ? Optional.empty() : Optional.of(selection.getFirst().name());
        };
    }

    /**
     * The primary button, given the name field's text. The selection answers at once; a typed name is resolved and
     * checked on the worker. {@code done} runs on the UI thread, after a typed folder has been shown; it does not run
     * when the user moved elsewhere meanwhile.
     */
    public void accept(String typed, Consumer<Outcome> done) {
        String text = typed.strip();
        if (!text.isEmpty() && nameForSelection().filter(text::equals).isPresent()) {
            if (request.mode() == ChooserRequest.Mode.OPEN) {
                done.accept(new Outcome.Chosen(selection.stream().filter(entry -> !entry.directory()).map(entry -> location.at(entry.path())).toList()));
                return;
            }
            if (request.mode() == ChooserRequest.Mode.DIRECTORY) {
                done.accept(new Outcome.Chosen(List.of(location.at(selection.getFirst().path()))));
                return;
            }
        }
        if (text.isEmpty()) {
            if (request.mode() == ChooserRequest.Mode.DIRECTORY && browser != null) done.accept(new Outcome.Chosen(List.of(location)));
            else if (selection.size() == 1 && selection.getFirst().directory()) done.accept(activate(selection.getFirst()));
            else done.accept(new Outcome.Invalid(request.mode() == ChooserRequest.Mode.SAVE ? "Enter a file name." : "Select a file."));
            return;
        }
        Browser current = browser;
        if (current == null) {
            done.accept(new Outcome.Invalid("Not connected yet."));
            return;
        }
        Location here = location;
        long ticket = generation;
        submit(() -> {
            Outcome outcome;
            try {
                String path = current.resolve(here.path(), text);
                outcome = decide(current, text, here.at(path), current.stat(path));
            } catch (IllegalArgumentException invalid) {
                outcome = new Outcome.Invalid("\"" + text + "\" is not a valid name.");
            } catch (IOException | RuntimeException failed) {
                outcome = new Outcome.Invalid(describe(failed));
            }
            Outcome result = outcome;
            ui.execute(() -> {
                if (ticket != generation || closed) return;
                if (result instanceof Outcome.Navigated navigated) navigate(navigated.folder());
                done.accept(result);
            });
        });
    }

    /** Runs on the worker. */
    private Outcome decide(Browser current, String text, Location target, Optional<ChooserEntry> found) throws IOException {
        if (found.filter(ChooserEntry::directory).isPresent())
            return request.mode() == ChooserRequest.Mode.DIRECTORY ? new Outcome.Chosen(List.of(target)) : new Outcome.Navigated(target);
        return switch (request.mode()) {
            case DIRECTORY -> new Outcome.Invalid("There is no folder named \"" + text + "\".");
            case OPEN -> found.filter(entry -> entry.kind() == ChooserEntry.Kind.FILE).isPresent()
                ? new Outcome.Chosen(List.of(target)) : new Outcome.Invalid("There is no file named \"" + text + "\".");
            case SAVE -> {
                Optional<String> parent = current.parent(target.path());
                boolean folderExists = parent.isPresent() && current.stat(parent.get()).filter(ChooserEntry::directory).isPresent();
                if (!folderExists) yield new Outcome.Invalid("The folder for \"" + text + "\" doesn't exist.");
                yield found.isPresent() ? new Outcome.ConfirmReplace(target) : new Outcome.Chosen(List.of(target));
            }
        };
    }

    /** Double-click or Enter on an entry: a folder is shown; a file is opened, or in Save, replaced after asking. */
    public Outcome activate(ChooserEntry entry) {
        Location target = location.at(entry.path());
        if (entry.directory()) {
            navigate(target);
            return new Outcome.Navigated(target);
        }
        return switch (request.mode()) {
            case OPEN -> new Outcome.Chosen(List.of(target));
            case SAVE -> new Outcome.ConfirmReplace(target);
            case DIRECTORY -> new Outcome.Invalid("Choose a folder.");
        };
    }

    /** The path bar: shows the folder {@code text} names, or reports why not, on the UI thread. */
    public void goTo(String text, Consumer<Optional<String>> problem) {
        Browser current = browser;
        if (current == null) {
            problem.accept(Optional.of("Not connected yet."));
            return;
        }
        Location here = location;
        long ticket = generation;
        String typed = text.strip();
        submit(() -> {
            Optional<String> result;
            Location target = null;
            try {
                String path = current.resolve(here.path(), typed);
                target = here.at(path);
                result = current.stat(path).filter(ChooserEntry::directory).isPresent() ? Optional.empty()
                    : Optional.of("There is no folder at " + path + ".");
            } catch (IllegalArgumentException invalid) {
                result = Optional.of("\"" + typed + "\" is not a valid path.");
            } catch (IOException | RuntimeException failed) {
                result = Optional.of(describe(failed));
            }
            Optional<String> outcome = result;
            Location destination = target;
            ui.execute(() -> {
                if (ticket != generation || closed) return;
                if (outcome.isEmpty()) navigate(destination);
                problem.accept(outcome);
            });
        });
    }

    /** Loads the preview of the single selected entry; a result that arrives after the selection changed is dropped. */
    public void requestPreview() {
        long ticket = ++previewGeneration;
        preview = Optional.empty();
        Browser current = browser;
        if (selection.size() != 1 || current == null) return;
        ChooserEntry entry = selection.getFirst();
        submit(() -> {
            Optional<PreviewInfo> result;
            try {
                if (entry.directory()) {
                    int[] items = { 0 };
                    current.list(entry.path(), ignored -> items[0]++);
                    result = Optional.of(PreviewInfo.folder(items[0]));
                } else {
                    result = Optional.of(PreviewInfo.file(current.read(entry.path(), PREVIEW_BYTES)));
                }
            } catch (IOException | RuntimeException unreadable) {
                result = Optional.empty();
            }
            Optional<PreviewInfo> loaded = result;
            ui.execute(() -> {
                if (ticket != previewGeneration || closed) return;
                preview = loaded;
                fire();
            });
        });
    }

    /**
     * Creates a folder in the folder shown, then lists it again with the new folder selected.
     *
     * @param done receives a message when it failed, or empty on success, on the UI thread
     */
    public void createFolder(String name, Consumer<Optional<String>> done) {
        String trimmed = name.strip();
        Optional<String> invalid = Optional.of("\"" + trimmed + "\" is not a valid folder name.");
        Browser current = browser;
        if (current == null || trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..") || trimmed.equals("~")
                || trimmed.contains("/") || trimmed.contains(File.separator)) {
            done.accept(current == null ? Optional.of("Not connected yet.") : invalid);
            return;
        }
        Location shown = location;
        submit(() -> {
            String failure = null;
            try { current.mkdir(current.resolve(shown.path(), trimmed)); }
            catch (FileAlreadyExistsException exists) { failure = "\"" + trimmed + "\" already exists."; }
            catch (IllegalArgumentException bad) { failure = invalid.get(); }
            catch (IOException | RuntimeException failed) { failure = failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage(); }
            String result = failure;
            ui.execute(() -> {
                if (closed) return;
                if (result == null && location.equals(shown)) {
                    pendingSelection = Optional.of(trimmed);
                    show(location);
                }
                done.accept(Optional.ofNullable(result));
            });
        });
    }

    /** Cancels a connection in progress and closes every browser this model opened. */
    public void close() {
        if (closed) return;
        closed = true;
        generation++;
        if (connecting != null) connecting.cancel(true);
        List<Browser> open = List.copyOf(browsers.values());
        browsers.clear();
        for (Browser each : open) {
            try { each.abort(); } catch (RuntimeException ignored) { }
        }
        for (Browser each : open) closeLater(each);
    }

    private static String key(Location place) { return place.source() + "|" + place.root(); }

    private void show(Location target) {
        long ticket = ++generation;
        location = target;
        browser = null;
        loading = false;
        retryable = false;
        error = Optional.empty();
        connectingTo = Optional.empty();
        listing = List.of();
        selection = List.of();
        preview = Optional.empty();
        previewGeneration++;
        if (connecting != null) {
            connecting.cancel(true);
            connecting = null;
        }
        if (closed) return;
        Browser known = browsers.get(key(target));
        if (known != null) {
            load(ticket, known, target);
            return;
        }
        ChooserSource source = sources.get(target.source());
        if (source == null) {
            error = Optional.of("This place is no longer available.");
            recompute();
            fire();
            return;
        }
        String label = rootLabel(target);
        connectingTo = Optional.of(label);
        recompute();
        fire();
        CompletableFuture<Browser> attempt;
        try { attempt = Objects.requireNonNull(source.connect(target.root(), request.ownerWindow())); }
        catch (RuntimeException failed) { attempt = CompletableFuture.failedFuture(failed); }
        connecting = attempt;
        attempt.whenComplete((opened, failure) -> ui.execute(() -> connected(ticket, target, label, opened, failure)));
    }

    private void connected(long ticket, Location target, String label, Browser opened, Throwable failure) {
        if (opened != null && (closed || browsers.putIfAbsent(key(target), opened) != null)) closeLater(opened);
        if (closed || ticket != generation) return;
        connecting = null;
        connectingTo = Optional.empty();
        Browser ready = browsers.get(key(target));
        if (ready == null) {
            error = Optional.of("Couldn't connect to " + label + ": " + reason(failure));
            retryable = true;
            fire();
            return;
        }
        load(ticket, ready, target);
    }

    private void load(long ticket, Browser current, Location target) {
        browser = current;
        loading = true;
        recompute();
        fire();
        submit(() -> {
            String folder;
            try {
                folder = target.path().equals(Location.HOME) ? current.home() : target.path();
            } catch (IOException | RuntimeException failed) {
                ui.execute(() -> finish(ticket, List.of(), describe(failed), true));
                return;
            }
            if (!folder.equals(target.path())) ui.execute(() -> {
                if (ticket == generation) {
                    location = target.at(folder);
                    fire();
                }
            });
            var batch = new ArrayList<ChooserEntry>(BATCH);
            String failure = null;
            try {
                current.list(folder, entry -> {
                    batch.add(entry);
                    if (batch.size() == BATCH) {
                        List<ChooserEntry> chunk = List.copyOf(batch);
                        batch.clear();
                        ui.execute(() -> append(ticket, chunk));
                    }
                });
            } catch (IOException | RuntimeException failed) {
                failure = describe(failed);
            }
            List<ChooserEntry> rest = List.copyOf(batch);
            String failed = failure;
            ui.execute(() -> finish(ticket, rest, failed, false));
        });
    }

    private void append(long ticket, List<ChooserEntry> chunk) {
        if (ticket != generation) return;
        listing = Stream.concat(listing.stream(), chunk.stream()).toList();
        recompute();
        fire();
    }

    private void finish(long ticket, List<ChooserEntry> rest, String failure, boolean retry) {
        if (ticket != generation) return;
        loading = false;
        listing = Stream.concat(listing.stream(), rest.stream()).toList();
        error = Optional.ofNullable(failure);
        retryable = retry;
        recompute();
        pendingSelection.flatMap(name -> visible.stream().filter(entry -> entry.name().equals(name)).findFirst())
            .ifPresent(entry -> selection = List.of(entry));
        pendingSelection = Optional.empty();
        fire();
    }

    private void submit(Runnable task) {
        if (closed) return;
        try { worker.execute(task); }
        catch (RejectedExecutionException stopped) { /* the dialog is closing */ }
    }

    private void closeLater(Browser opened) {
        try { worker.execute(opened::close); }
        catch (RejectedExecutionException stopped) { opened.close(); }
    }

    private void recompute() {
        String needle = filter.strip().toLowerCase(Locale.ROOT);
        Comparator<ChooserEntry> byName = Comparator.comparing((ChooserEntry entry) -> entry.name().toLowerCase(Locale.ROOT))
            .thenComparing(ChooserEntry::name);
        Comparator<ChooserEntry> order = switch (sortColumn) {
            case NAME -> byName;
            case MODIFIED -> Comparator.comparingLong(ChooserEntry::modified);
            case SIZE -> Comparator.comparingLong(ChooserEntry::size);
        };
        if (!ascending) order = order.reversed();
        Comparator<ChooserEntry> full = Comparator.comparing((ChooserEntry entry) -> !entry.directory()).thenComparing(order).thenComparing(byName);
        visible = listing.stream()
            .filter(entry -> showHidden || !entry.hidden())
            .filter(entry -> request.mode() != ChooserRequest.Mode.DIRECTORY || entry.directory())
            .filter(entry -> entry.directory() || request.suffix()
                .map(suffix -> entry.name().toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))).orElse(true))
            .filter(entry -> needle.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(needle))
            .sorted(full)
            .toList();
        selection = selection.stream().filter(visible::contains).toList();
    }

    /** A short, user-facing reason a folder could not be listed. */
    static String describe(Throwable failure) {
        return switch (failure) {
            case AccessDeniedException denied -> "You don't have permission to open this folder.";
            case NoSuchFileException missing -> "This folder doesn't exist.";
            case NotDirectoryException notFolder -> "This is not a folder.";
            default -> failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        };
    }

    static String reason(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        if (cause == null) return "no connection";
        if (cause instanceof CancellationException) return "cancelled";
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private void fire() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}

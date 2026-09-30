package dev.jasper.app.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The chooser without Swing: the folder shown, back and forward history, the listing, filter, sort order and
 * selection, previews, new folders and what the primary button does. Listing runs on {@code worker}; results are
 * applied through {@code ui} and dropped when a newer request has started. UI thread only.
 */
public final class ChooserModel {
    /** Sort keys; folders always come first. */
    public enum Column { NAME, MODIFIED, SIZE }

    /** What the primary button, Enter in the name field, or opening an entry leads to. */
    public sealed interface Outcome {
        /** The name was a folder, which is now shown. */
        record Navigated(Path folder) implements Outcome { }
        /** The choice is made. */
        record Chosen(List<Path> paths) implements Outcome { }
        /** Saving would replace an existing file: ask first, then treat it as chosen. */
        record ConfirmReplace(Path file) implements Outcome { }
        /** Nothing can be chosen; the message says why. */
        record Invalid(String message) implements Outcome { }
    }

    static final int PREVIEW_BYTES = 8 * 1024;

    private final ChooserRequest request;
    private final LocalFiles files;
    private final Executor worker;
    private final Executor ui;
    private final Deque<Path> back = new ArrayDeque<>();
    private final Deque<Path> forward = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private Path folder;
    private List<ChooserEntry> listing = List.of();
    private List<ChooserEntry> visible = List.of();
    private List<ChooserEntry> selection = List.of();
    private String filter = "";
    private boolean showHidden;
    private Column sortColumn = Column.NAME;
    private boolean ascending = true;
    private boolean loading;
    private Optional<String> error = Optional.empty();
    private Optional<LocalFiles.PreviewInfo> preview = Optional.empty();
    private long generation;
    private long previewGeneration;
    private Optional<Path> pendingSelection = Optional.empty();

    /**
     * @param worker runs blocking file access
     * @param ui applies results on the UI thread
     * @param showHidden whether dotfiles are listed at first
     */
    public ChooserModel(ChooserRequest request, LocalFiles files, Executor worker, Executor ui, boolean showHidden) {
        this.request = Objects.requireNonNull(request);
        this.files = Objects.requireNonNull(files);
        this.worker = Objects.requireNonNull(worker);
        this.ui = Objects.requireNonNull(ui);
        this.showHidden = showHidden;
        this.folder = request.start();
        if (request.mode() == ChooserRequest.Mode.OPEN)
            request.suggestedName().ifPresent(name -> {
                try { pendingSelection = Optional.of(folder.resolve(name)); } catch (InvalidPathException ignored) { }
            });
    }

    /** Lists the start folder. */
    public void start() { load(); }

    /** Runs {@code listener} after every change. */
    public void addListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    public ChooserRequest request() { return request; }
    public Path folder() { return folder; }
    /** The listing after the dotfile, mode, suffix and search filters, sorted. */
    public List<ChooserEntry> entries() { return visible; }
    public List<ChooserEntry> selection() { return selection; }
    public boolean loading() { return loading; }
    public Optional<String> error() { return error; }
    public Optional<LocalFiles.PreviewInfo> preview() { return preview; }
    public String filter() { return filter; }
    public boolean showHidden() { return showHidden; }
    public Column sortColumn() { return sortColumn; }
    public boolean ascending() { return ascending; }
    public boolean canGoBack() { return !back.isEmpty(); }
    public boolean canGoForward() { return !forward.isEmpty(); }
    public boolean canGoUp() { return folder.getParent() != null; }

    /** Shows {@code target}, recording the current folder for Back; the folder already shown is listed again. */
    public void navigate(Path target) {
        Path next = target.toAbsolutePath().normalize();
        if (!next.equals(folder)) {
            back.push(folder);
            forward.clear();
            folder = next;
        }
        filter = "";
        load();
    }

    public void goBack() {
        if (back.isEmpty()) return;
        forward.push(folder);
        folder = back.pop();
        filter = "";
        load();
    }

    public void goForward() {
        if (forward.isEmpty()) return;
        back.push(folder);
        folder = forward.pop();
        filter = "";
        load();
    }

    public void goUp() { if (folder.getParent() != null) navigate(folder.getParent()); }

    public void refresh() { load(); }

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
     * Resolves name-field text: {@code ~}, {@code ~/…}, an absolute path, or a path relative to the folder shown.
     *
     * @throws InvalidPathException when the text is not a path on this platform
     */
    public Path resolve(String text) {
        Path home = files.home();
        if (text.equals("~")) return home;
        if (text.startsWith("~/") || text.startsWith("~" + File.separator)) return home.resolve(text.substring(2)).normalize();
        Path path = Path.of(text);
        return (path.isAbsolute() ? path : folder.resolve(path)).toAbsolutePath().normalize();
    }

    /** The primary button, given the name field's text. A typed folder is shown, except in Directory mode, where it is chosen. */
    public Outcome accept(String typed) {
        String text = typed.strip();
        if (!text.isEmpty() && nameForSelection().filter(text::equals).isPresent()) {
            if (request.mode() == ChooserRequest.Mode.OPEN)
                return new Outcome.Chosen(selection.stream().filter(entry -> !entry.directory()).map(ChooserEntry::path).toList());
            if (request.mode() == ChooserRequest.Mode.DIRECTORY) return new Outcome.Chosen(List.of(selection.getFirst().path()));
        }
        if (text.isEmpty()) {
            if (request.mode() == ChooserRequest.Mode.DIRECTORY) return new Outcome.Chosen(List.of(folder));
            if (selection.size() == 1 && selection.getFirst().directory()) return show(selection.getFirst().path());
            return new Outcome.Invalid(request.mode() == ChooserRequest.Mode.SAVE ? "Enter a file name." : "Select a file.");
        }
        Path target;
        try { target = resolve(text); }
        catch (InvalidPathException invalid) { return new Outcome.Invalid("\"" + text + "\" is not a valid name."); }
        if (Files.isDirectory(target))
            return request.mode() == ChooserRequest.Mode.DIRECTORY ? new Outcome.Chosen(List.of(target)) : show(target);
        return switch (request.mode()) {
            case DIRECTORY -> new Outcome.Invalid("There is no folder named \"" + text + "\".");
            case OPEN -> Files.isRegularFile(target) ? new Outcome.Chosen(List.of(target))
                : new Outcome.Invalid("There is no file named \"" + text + "\".");
            case SAVE -> {
                Path parent = target.getParent();
                if (parent == null || !Files.isDirectory(parent)) yield new Outcome.Invalid("The folder for \"" + text + "\" doesn't exist.");
                yield Files.exists(target) ? new Outcome.ConfirmReplace(target) : new Outcome.Chosen(List.of(target));
            }
        };
    }

    /** Double-click or Enter on an entry: a folder is shown; a file is opened, or in Save, replaced after asking. */
    public Outcome activate(ChooserEntry entry) {
        if (entry.directory()) return show(entry.path());
        return switch (request.mode()) {
            case OPEN -> new Outcome.Chosen(List.of(entry.path()));
            case SAVE -> new Outcome.ConfirmReplace(entry.path());
            case DIRECTORY -> new Outcome.Invalid("Choose a folder.");
        };
    }

    private Outcome show(Path target) {
        navigate(target);
        return new Outcome.Navigated(folder);
    }

    /** Loads the preview of the single selected entry; a result that arrives after the selection changed is dropped. */
    public void requestPreview() {
        long ticket = ++previewGeneration;
        preview = Optional.empty();
        if (selection.size() != 1) return;
        ChooserEntry entry = selection.getFirst();
        worker.execute(() -> {
            Optional<LocalFiles.PreviewInfo> result;
            try { result = Optional.of(files.preview(entry, PREVIEW_BYTES)); }
            catch (IOException | RuntimeException unreadable) { result = Optional.empty(); }
            Optional<LocalFiles.PreviewInfo> loaded = result;
            ui.execute(() -> {
                if (ticket != previewGeneration) return;
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
        if (trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..") || trimmed.contains("/") || trimmed.contains(File.separator)) {
            done.accept(invalid);
            return;
        }
        Path target;
        try { target = folder.resolve(trimmed); }
        catch (InvalidPathException bad) { done.accept(invalid); return; }
        Path shown = folder;
        worker.execute(() -> {
            String failure = null;
            try { files.mkdir(target); }
            catch (FileAlreadyExistsException exists) { failure = "\"" + trimmed + "\" already exists."; }
            catch (IOException | RuntimeException failed) {
                failure = failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage();
            }
            String result = failure;
            ui.execute(() -> {
                if (result == null && folder.equals(shown)) {
                    pendingSelection = Optional.of(target);
                    load();
                }
                done.accept(Optional.ofNullable(result));
            });
        });
    }

    private void load() {
        long ticket = ++generation;
        Path target = folder;
        loading = true;
        error = Optional.empty();
        listing = List.of();
        selection = List.of();
        preview = Optional.empty();
        previewGeneration++;
        recompute();
        fire();
        worker.execute(() -> {
            List<ChooserEntry> result = List.of();
            String failure = null;
            try { result = files.list(target); }
            catch (IOException | RuntimeException unreadable) { failure = describe(unreadable); }
            List<ChooserEntry> listed = result;
            String failed = failure;
            ui.execute(() -> {
                if (ticket != generation) return;
                loading = false;
                listing = List.copyOf(listed);
                error = Optional.ofNullable(failed);
                recompute();
                listed(ticket);
                fire();
            });
        });
    }

    /** Called once a listing is applied, before listeners run: selects the entry a new folder or the request named. */
    private void listed(long ticket) {
        pendingSelection.flatMap(path -> visible.stream().filter(entry -> entry.path().equals(path)).findFirst())
            .ifPresent(entry -> selection = List.of(entry));
        pendingSelection = Optional.empty();
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
    static String describe(Exception failure) {
        return switch (failure) {
            case AccessDeniedException denied -> "You don't have permission to open this folder.";
            case NoSuchFileException missing -> "This folder doesn't exist.";
            case NotDirectoryException notFolder -> "This is not a folder.";
            default -> failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        };
    }

    private void fire() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}

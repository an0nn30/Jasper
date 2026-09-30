package dev.jasper.app.files;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
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

/**
 * The chooser without Swing: the folder shown, back and forward history, the listing, filter, sort order and
 * selection, and (Task 5) previews and what the primary button does. Listing runs on {@code worker}; results are
 * applied through {@code ui} and dropped when a newer request has started. UI thread only.
 */
public final class ChooserModel {
    /** Sort keys; folders always come first. */
    public enum Column { NAME, MODIFIED, SIZE }

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
    private Optional<LocalFiles.Preview> preview = Optional.empty();
    private long generation;
    private long previewGeneration;

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
    public Optional<LocalFiles.Preview> preview() { return preview; }
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

    /** Called once a listing is applied, before listeners run; Task 5 selects a pending entry here. */
    private void listed(long ticket) { }

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

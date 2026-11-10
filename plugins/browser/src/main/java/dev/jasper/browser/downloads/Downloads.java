package dev.jasper.browser.downloads;

import dev.jasper.browser.downloads.Download.State;
import dev.jasper.browser.downloads.DownloadList.Outcome;
import dev.jasper.browser.page.DownloadProgress;
import dev.jasper.browser.page.DownloadStart;
import dev.jasper.browser.page.DownloadTarget;
import dev.jasper.browser.page.Page;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.ui.MenuEntries;
import dev.jasper.sdk.ui.MenuItem;
import java.awt.EventQueue;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * The Browser's downloads (spec section 10): what Chromium is told as it starts each one, the Downloads menu, their
 * notifications and the downloaded-file mark. It names no Chromium type: the {@code page} package's
 * {@code PageDownloads} hands it each start and update in plain values on Chromium's thread.
 *
 * <p><b>Where a file goes.</b> Files go, without asking, into the Downloads folder, which the Downloads folder setting
 * names ({@link DownloadFolder}) over the system's own ({@code Platform.downloadsFolder()}). {@link FolderResolution}
 * reads and makes it on the plugin's background executor, never on Chromium's thread or the UI thread, at the first
 * page start ({@link #start}) and after each change of the setting (ruling F3), and only the folder itself is made and
 * checked, so macOS asks for the Downloads folder at the first download, not at the first page (ruling D4); before that
 * a download is refused. {@link #before}, on Chromium's thread, only refuses, with the folder's reason, or reserves a
 * free name there ({@link DownloadNames#reserve}), which every outcome releases; a download the user asked to save, as
 * Save Link As asks, reserves nothing and starts the save chooser at the folder. A refused download posts "Download
 * failed for “NAME”." with the reason.
 *
 * <p><b>A page's downloads.</b> A download counts for its page from the moment {@code before} gives it a target to its
 * outcome (ruling F1), and once the page's close has begun ({@link #drained}), its later starts are refused, under the
 * same lock, so none slips past a close; the page's browser closes once its count ends. A start Chromium could not be
 * given is abandoned ({@link #abandon}), and a save chooser cancelled for the page ends its count of an ask start that
 * had no update, the download itself kept ({@link #saveCancelled}, rulings F1.3 and D24). A download Chromium starts
 * before its page committed any document, given a target or refused, tells the page, whose item closes if a page opened
 * it (ruling F28). As Chrome limits them (ruling D1, the user's "Match
 * Chrome"), a page's first download without a user activation in a document goes and each later one in that document is
 * refused, the first refusal posting "This site tried to download multiple files" with the page's host, never its user
 * name or password ({@link DownloadAddresses}); an activated start, one the user asked to save, a new document, a load
 * Jasper starts for the user (ruling D20) and a browser that is no page's always go.
 *
 * <p><b>The list and the menu.</b> A download joins the {@link DownloadList}, and the Downloads menu, at its first
 * update: the first creates the menu, whose entries ({@link DownloadsMenu}) are set again only when a title, an enabled
 * state or the structure changed (ruling F19). Stop cancels it, Open and Show in Finder, Explorer or Folder go to the
 * {@link FileManager}, and Remove from List and Clear All change the list. A /dev/null group's finished downloads leave
 * the list as its store closes, and a running one after its outcome (ruling F7). Updates reach the list on the UI
 * thread.
 *
 * <p><b>Outcomes.</b> A completed file is marked on the background executor ({@code Platform.markDownloaded}) with
 * the download's address and, as referrer, the page's when it is a web page, or for a /dev/null download with no
 * address at all, then finished and told: "Download finished for “NAME”."; a failed mark also warns that the file has
 * no downloaded-file mark, and on macOS and Windows takes Open away (ruling F6). An interrupted download is cancelled,
 * so Chromium never resumes it, and posts "Download failed for “NAME”." once; a stopped one posts nothing. These
 * notifications are the {@link #GROUP Downloads} group's.
 *
 * <p><b>Quitting.</b> {@link #stopAll} cancels every running download and completes every page's hold, after which
 * starts, updates and marks are dropped and no menu is made (ruling F30); once Chromium has closed,
 * {@link #deleteIntermediates} deletes the partial files the running downloads left (ruling F29).
 */
public final class Downloads {
    /** The downloads' notifications: their own group, which Settings lists. */
    public static final GroupSpec GROUP = new GroupSpec("dev.jasper.browser.downloads", "Downloads", DisplayMode.CARD);
    /** The Downloads menu's id. */
    static final String MENU = "dev.jasper.browser.downloads";
    /** Why a download is refused before the folder is known. */
    static final String NOT_READY = "Jasper is still preparing the Downloads folder.";
    /** Why a download is refused when every name for it is taken, or, on Windows, the folder is too deep for one. */
    static final String NO_FREE_NAME = "Jasper found no free name for it in the Downloads folder.";
    /** Ruling D1's notice. */
    static final String MULTIPLE = "This site tried to download multiple files";
    private static final String OPEN_QUOTE = "\u201C", CLOSE_QUOTE = "\u201D";
    private static final System.Logger LOG = System.getLogger(Downloads.class.getName());
    private final PluginContext context;
    private final Setting<String> setting;
    private final FileManager files;
    private final NotificationGroup group;
    private final FolderResolution folders;
    private final String showLabel;
    // Whether the system has a downloaded-file mark, which Open waits for: macOS and Windows (ruling F6).
    private final boolean marks;
    private final DownloadNames names = new DownloadNames();
    // Guards the pages' counts and closing flags, and the stop (F1).
    private final Object lock = new Object();
    private final Map<Page, PageState> pages = new WeakHashMap<>();
    // Guarded by the lock (F1.3, D24): a page's ask starts that have had no update yet, and the starts whose page count a
    // cancelled save chooser ended, which their outcome then ends no more.
    private final Set<DownloadKey> unupdated = new HashSet<>(), uncounted = new HashSet<>();
    private volatile boolean stopped;
    // A test's, run between a start's reservation and the lock that counts it.
    private volatile Runnable afterReservation = () -> { };
    // Each download given a target, until its outcome, and its newest cancel; written on Chromium's thread.
    private final Map<DownloadKey, Started> starts = new ConcurrentHashMap<>();
    private final Map<DownloadKey, Runnable> cancels = new ConcurrentHashMap<>();
    // Each running download's intermediate file, which a quit may leave (F29); written on Chromium's thread.
    private final Map<DownloadKey, Intermediate> intermediates = new ConcurrentHashMap<>();
    // UI thread: the list, the downloads listed whose outcome has not come, the /dev/null ones that leave the list at
    // their end, and the menu.
    private final DownloadList list = new DownloadList();
    private final Set<DownloadKey> listed = new HashSet<>(), leaving = new HashSet<>();
    private boolean started;
    private MenuEntries entries;
    private List<MenuItem> shown = List.of();

    /**
     * A download Chromium was given a target for, as its first update lists it.
     *
     * @param folder the folder its intermediate file is in: its reservation's; null for one the user asked to save
     */
    private record Started(DownloadKey key, String name, Path target, String url, String pageUrl, Optional<Page> page, boolean inMemory,
                           Optional<DownloadNames.Reservation> reservation, Path folder) { }

    /** A running download's intermediate file, and the folder it must be in to be deleted; null for any. */
    private record Intermediate(Path file, Path folder) { }

    /** One page's downloads: guarded by the lock. */
    private static final class PageState {
        int running;
        boolean closing;
        CompletableFuture<Void> hold;
        // The document whose one download without an activation went, and the one whose refusal was told (D1).
        long automatic = Long.MIN_VALUE, refused = Long.MIN_VALUE;
    }

    /**
     * The plugin's downloads, made at its start with no I/O and no menu (ruling F35); the Downloads folder is read once a
     * page first starts ({@link #start}).
     *
     * @param context the plugin's context
     * @param group the plugin's {@link #GROUP}, which the plugin declares
     * @param folder the Downloads folder setting
     * @param files shows and opens finished files
     */
    public Downloads(PluginContext context, NotificationGroup group, Setting<String> folder, FileManager files) {
        this(context, group, folder, files, Path.of(System.getProperty("user.home", "")), System.getProperty("os.name", ""));
    }

    /**
     * The downloads of a test (ruling F15), whose home folder and system are its own.
     *
     * @param context the plugin's context
     * @param group the plugin's {@link #GROUP}
     * @param folder the Downloads folder setting
     * @param files shows and opens finished files
     * @param home the home folder, which {@code ~} names and under which {@code Downloads} is the folder until it is read
     * @param osName the {@code os.name} system property
     */
    Downloads(PluginContext context, NotificationGroup group, Setting<String> folder, FileManager files, Path home, String osName) {
        this.context = Objects.requireNonNull(context, "context");
        this.group = Objects.requireNonNull(group, "group");
        this.setting = Objects.requireNonNull(folder, "folder");
        this.files = Objects.requireNonNull(files, "files");
        folders = new FolderResolution(task -> context.background().execute(task), () -> context.platform().downloadsFolder(), home);
        showLabel = DownloadsMenu.showLabel(osName);
        marks = osName.startsWith("Mac") || osName.startsWith("Windows");
        folder.onChanged(value -> { if (started && !stopped) folders.resolve(value); });
    }

    /**
     * A page starts: the first time, the Downloads folder is read and made, on the background executor. UI thread.
     */
    public void start() {
        if (started || stopped) return;
        started = true;
        folders.resolve(setting.get());
    }

    /**
     * The downloads' names, which downloads reserve in the Downloads folder and Save Page As reserves its folder in
     * (plan 7's D43), so neither takes a name the other holds. Any thread.
     *
     * @return the names
     */
    public DownloadNames names() {
        return names;
    }

    /**
     * The Downloads folder: as last read and made, whether or not a download can go there; before the first page start
     * has read it, {@code Downloads} in the home folder, which nothing creates. Any thread.
     *
     * @return the folder
     */
    public Path folder() {
        return folders.folder();
    }

    /**
     * Where a download Chromium starts goes, or empty to refuse it, which CEF cancels. Chromium's thread: it reads only
     * whether names are free in the folder. A download of a page that has committed no document, its first navigation
     * become a download, tells the page on the UI thread whether it goes or is refused
     * ({@link Page#firstNavigationDownloaded}, ruling F28), as either way its tab is left empty; a refusal's notice
     * says why.
     *
     * @param start the download
     * @return a free file in the Downloads folder, with no ask; the folder and the safe name, with the ask, for a download
     *     the user asked to save; empty after {@link #stopAll}, for a page whose close has begun, past a page's one
     *     download without an activation in its document, and when the folder or a free name is missing
     */
    public Optional<DownloadTarget> before(DownloadStart start) {
        Objects.requireNonNull(start, "start");
        Optional<DownloadTarget> target = target(start);
        // Ruling F28: a page that closed, and downloads that stopped, hear nothing of it.
        start.page().filter(page -> page.committedAddress().isEmpty()).ifPresent(page -> later(page::firstNavigationDownloaded));
        return target;
    }

    /** {@link #before}'s answer for {@code start}. Chromium's thread. */
    private Optional<DownloadTarget> target(DownloadStart start) {
        if (stopped) return Optional.empty();
        String name = DownloadNames.safeName(start.suggestedName());
        Page page = start.page().orElse(null);
        boolean automatic = page != null && !start.ask() && !start.activated();
        boolean refused = false, told = false;
        synchronized (lock) {
            if (stopped || closing(page)) return Optional.empty();
            if (automatic && state(page).automatic == start.document()) {
                PageState state = state(page);
                refused = true;
                told = state.refused != start.document();
                state.refused = start.document();
            }
        }
        if (refused) {
            // Posted outside the lock, which guards only the counts.
            if (told) later(() -> group.warning(MULTIPLE, DownloadAddresses.host(start.pageUrl())));
            return Optional.empty();
        }
        Path target;
        Optional<DownloadNames.Reservation> reservation = Optional.empty();
        if (start.ask()) target = folder().resolve(name);
        else {
            FolderResolution.Resolved now = folders.now();
            if (now == null || now.refusal() != null) {
                failed(name, now == null ? NOT_READY : now.refusal());
                return Optional.empty();
            }
            reservation = names.reserve(now.folder(), name);
            if (reservation.isEmpty()) {
                failed(name, NO_FREE_NAME);
                return Optional.empty();
            }
            target = reservation.get().file();
            afterReservation.run();
        }
        var key = new DownloadKey(start.browser(), start.id());
        synchronized (lock) {
            if (stopped || closing(page)) {
                reservation.ifPresent(names::release);
                return Optional.empty();
            }
            if (page != null) {
                PageState state = state(page);
                state.running++;
                if (automatic) state.automatic = start.document();
            }
            starts.put(key, new Started(key, name, target, start.url(), start.pageUrl(), start.page(), start.inMemory(), reservation,
                reservation.map(held -> held.file().getParent()).orElse(null)));
            if (page != null && start.ask()) unupdated.add(key);
        }
        return Optional.of(new DownloadTarget(target, start.ask()));
    }

    /**
     * A start {@link #before} gave a target that Chromium could not be given, as its continue threw: it is forgotten, its
     * name freed and its page's count ended, so the page's hold may complete on this thread, and a close that touches
     * Swing then hops to the UI thread, as the page's does; a start not kept, and one abandoned before, change nothing.
     * Chromium's thread.
     *
     * @param start the start
     */
    public void abandon(DownloadStart start) {
        Objects.requireNonNull(start, "start");
        var key = new DownloadKey(start.browser(), start.id());
        Started kept = starts.remove(key);
        if (kept == null) return;
        cancels.remove(key);
        intermediates.remove(key);
        kept.reservation().ifPresent(names::release);
        if (uncount(key)) kept.page().ifPresent(this::ended);
    }

    /**
     * A save chooser Chromium asked for {@code page} was cancelled (rulings F1.3 and D24): the page's count of its oldest
     * start that asked where it goes and has had no update yet ends, as Chromium may tell nothing of a download cancelled
     * before it began, which would otherwise hold the page's browser until Jasper quits. Only the count ends: the
     * download is kept, so a late update still lists it and its file is marked, and its outcome ends no count again.
     * Only a download or the page's own {@code showSaveFilePicker} opens such a chooser, so at worst a count ends early,
     * and a page that then closes may close its browser while that download runs; a start that has had an update counts
     * until its own outcome. Whether a start has had an update is read here and written by {@link #updated} under one
     * lock. UI thread.
     *
     * @param page the page the chooser was for
     */
    public void saveCancelled(Page page) {
        Objects.requireNonNull(page, "page");
        synchronized (lock) {
            Optional<DownloadKey> oldest = unupdated.stream().filter(key -> {
                Started start = starts.get(key);
                return start != null && start.page().orElse(null) == page;
            }).min(Comparator.comparingInt(DownloadKey::id));
            if (oldest.isEmpty()) return;
            unupdated.remove(oldest.get());
            uncounted.add(oldest.get());
        }
        // Outside the lock, as the page's hold may complete and close its browser.
        ended(page);
    }

    /**
     * Whether the download {@code key} still counts for its page, which it then no longer does: false once a cancelled
     * save chooser ended its count (D24). Any thread.
     */
    private boolean uncount(DownloadKey key) {
        synchronized (lock) {
            unupdated.remove(key);
            return !uncounted.remove(key);
        }
    }

    /**
     * Has {@code hook} run in each start between its name's reservation and the lock that counts it, for a test of what
     * happens meanwhile.
     *
     * @param hook what runs, on Chromium's thread
     */
    void afterReservation(Runnable hook) {
        afterReservation = Objects.requireNonNull(hook, "hook");
    }

    /**
     * Chromium's update of a download given a target: {@code cancel} is kept, and the update reaches the list on the UI
     * thread; dropped after {@link #stopAll}, and for a download this did not start. Chromium's thread.
     *
     * @param progress the update
     * @param cancel cancels the download in Chromium
     */
    public void updated(DownloadProgress progress, Runnable cancel) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(cancel, "cancel");
        if (stopped) return;
        var key = new DownloadKey(progress.browser(), progress.id());
        Started start = starts.get(key);
        if (start == null) return;
        synchronized (lock) {
            unupdated.remove(key);
        }
        cancels.put(key, cancel);
        if (progress.complete() || progress.canceled() || !progress.inProgress()) intermediates.remove(key);
        else intermediate(progress.fullPath()).ifPresent(file -> intermediates.put(key, new Intermediate(file, start.folder())));
        EventQueue.invokeLater(() -> apply(key, progress));
    }

    /**
     * Whether {@code page}'s downloads have ended: its close has begun, so its later downloads are refused (ruling F1),
     * and the answer completes once its last running download has its outcome, on the UI thread, or its last start is
     * {@linkplain #abandon abandoned}, on Chromium's, or its count ended by a cancelled save chooser
     * ({@link #saveCancelled}), at once when it has none, and at {@link #stopAll}. A close that waits for it and
     * touches Swing hops to the UI thread, as the page's does ({@link Page#setCloseHold}). Any thread.
     *
     * @param page the page that closes
     * @return complete once it holds no download
     */
    public CompletableFuture<Void> drained(Page page) {
        Objects.requireNonNull(page, "page");
        synchronized (lock) {
            PageState state = state(page);
            state.closing = true;
            if (stopped || state.running == 0) return CompletableFuture.completedFuture(null);
            if (state.hold == null) state.hold = new CompletableFuture<>();
            return state.hold.copy();
        }
    }

    /**
     * Jasper quits, or the plugin stops: every running download is cancelled and every page's hold completes, at once,
     * so each held browser closes before Chromium does; later starts are refused and updates and marks dropped, and the
     * menu is not made. Once; UI thread.
     */
    public void stopAll() {
        List<CompletableFuture<Void>> holds = new ArrayList<>();
        synchronized (lock) {
            if (stopped) return;
            stopped = true;
            for (PageState state : pages.values()) {
                if (state.hold != null) holds.add(state.hold);
                state.hold = null;
            }
        }
        for (DownloadKey key : List.copyOf(starts.keySet())) {
            Runnable cancel = cancels.get(key);
            if (cancel == null) continue;
            try {
                cancel.run();
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A download could not be stopped", failure);
            }
        }
        holds.forEach(hold -> hold.complete(null));
    }

    /**
     * Deletes the intermediate file each download running at the quit left (ruling F29), once Chromium has closed: each
     * that is still a regular file, not a link, in its download's folder. Chromium keeps a stopped download's partial
     * file for a resumption that never comes. Background executor.
     */
    public void deleteIntermediates() {
        for (Intermediate left : List.copyOf(intermediates.values())) {
            try {
                if (left.folder() != null && !Files.isSameFile(left.folder(), left.file().getParent())) continue;
                if (Files.readAttributes(left.file(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile())
                    Files.deleteIfExists(left.file());
            } catch (IOException | SecurityException gone) {
                // Gone already, as Chromium deletes a cancelled download's file itself, or out of reach: left as it is.
            }
        }
        intermediates.clear();
    }

    /** An update reaches the list: a download joins it at its first, and its outcome is acted on. UI thread. */
    private void apply(DownloadKey key, DownloadProgress progress) {
        if (stopped) return;
        Started start = starts.get(key);
        if (start == null) return;
        if (listed.add(key)) {
            list.begin(key, start.name(), start.target(), DownloadAddresses.source(start.url()));
            start.page().filter(Page::inMemory).ifPresent(page -> page.context().onClosed(() -> groupClosed(key)));
        }
        list.update(key, progress).ifPresent(outcome -> ended(start, outcome));
        refresh();
    }

    /**
     * {@code start}'s outcome: an interrupted download is cancelled, so Chromium never resumes it, before its page's count
     * ends, which may close the page's browser; its name is freed; then it is marked, told or left. UI thread.
     */
    private void ended(Started start, Outcome outcome) {
        DownloadKey key = start.key();
        listed.remove(key);
        starts.remove(key);
        Runnable cancel = cancels.remove(key);
        if (outcome == Outcome.FAILED && cancel != null) cancel.run();
        start.reservation().ifPresent(names::release);
        if (uncount(key)) start.page().ifPresent(this::ended);
        switch (outcome) {
            case COMPLETED -> mark(start);
            case FAILED -> {
                download(key).ifPresent(download -> failed(download.name(), ""));
                leave(key);
            }
            case STOPPED -> leave(key);
        }
    }

    /** One of {@code page}'s downloads has its outcome: its hold completes once none runs. UI thread. */
    private void ended(Page page) {
        CompletableFuture<Void> hold = null;
        synchronized (lock) {
            PageState state = state(page);
            state.running = Math.max(0, state.running - 1);
            if (state.running == 0 && state.hold != null) {
                hold = state.hold;
                state.hold = null;
            }
        }
        if (hold != null) hold.complete(null);
    }

    /** The completed file is marked on the background executor, then finished. UI thread. */
    private void mark(Started start) {
        DownloadKey key = start.key();
        Optional<Download> completed = download(key);
        if (completed.isEmpty()) return;
        Path file = completed.get().file();
        // Ruling F7: a /dev/null download's mark keeps no address; a page that is no web page is no referrer.
        URI source = start.inMemory() ? DownloadAddresses.NO_ADDRESS : completed.get().source();
        Optional<URI> referrer = start.inMemory() ? Optional.empty() : DownloadAddresses.web(start.pageUrl());
        try {
            context.background().execute(() -> {
                String failure = null;
                try {
                    context.platform().markDownloaded(file, source, referrer);
                } catch (IOException | RuntimeException refused) {
                    failure = reason(refused);
                }
                String why = failure;
                EventQueue.invokeLater(() -> marked(key, why));
            });
        } catch (RejectedExecutionException closed) {
            // The plugin has stopped: no mark, and nothing told.
            marked(key, "The plugin has stopped");
        }
    }

    /** The mark is done, or failed for {@code failure}: the download is finished and told. UI thread. */
    private void marked(DownloadKey key, String failure) {
        list.finish(key, failure == null || !marks);
        Optional<Download> finished = download(key);
        if (!stopped && finished.isPresent()) {
            String name = DownloadsMenu.elided(finished.get().name());
            group.info("Download finished for " + OPEN_QUOTE + name + CLOSE_QUOTE + ".", "");
            if (failure != null) group.warning(OPEN_QUOTE + name + CLOSE_QUOTE + " has no downloaded-file mark", "Jasper could not mark it "
                + "as downloaded from the internet, so the system may open it without checking it first: " + sentence(failure)
                + ". Open it from the file manager only if you trust it.");
        }
        leave(key);
        refresh();
    }

    /** A /dev/null group's store closed: its download leaves the list now if it no longer runs, else at its end. UI thread. */
    private void groupClosed(DownloadKey key) {
        Optional<Download> download = download(key);
        if (download.isEmpty()) return;
        if (download.get().state() == State.RUNNING) leaving.add(key);
        else list.remove(download.get().serial());
        refresh();
    }

    /** A download whose group closed while it ran leaves the list, now that it has ended. UI thread. */
    private void leave(DownloadKey key) {
        if (leaving.remove(key)) download(key).ifPresent(download -> list.remove(download.serial()));
    }

    /** The Downloads menu shows the list: made with the first download, set again only when what it shows changed. UI thread. */
    private void refresh() {
        if (stopped) return;
        List<Download> all = list.all();
        List<MenuItem> items = DownloadsMenu.items(all, showLabel);
        if (entries == null) {
            if (all.isEmpty()) return;
            entries = context.menus().create(MENU, "Downloads").addEntries(items, this::chosen);
        } else if (!items.equals(shown)) entries.set(items);
        shown = items;
    }

    /** A menu entry is chosen. UI thread. */
    private void chosen(String id) {
        if (stopped) return;
        if (id.equals(DownloadsMenu.CLEAR_ALL)) list.clearAll();
        else if (id.startsWith(DownloadsMenu.PREFIX)) {
            String rest = id.substring(DownloadsMenu.PREFIX.length());
            int dot = rest.indexOf('.');
            Optional<Download> chosen = dot < 0 ? Optional.empty() : serial(rest.substring(0, dot));
            if (chosen.isPresent()) {
                Download download = chosen.get();
                switch (rest.substring(dot)) {
                    case DownloadsMenu.STOP -> {
                        Runnable cancel = download.state() == State.RUNNING ? cancels.get(download.key()) : null;
                        if (cancel != null) cancel.run();
                    }
                    case DownloadsMenu.OPEN -> { if (download.state() == State.FINISHED && download.openable()) files.open(download.file()); }
                    case DownloadsMenu.SHOW -> { if (download.state() == State.FINISHED) files.reveal(download.file()); }
                    case DownloadsMenu.REMOVE -> list.remove(download.serial());
                    default -> { }
                }
            }
        }
        refresh();
    }

    /** Posts "Download failed for “NAME”." with {@code reason}, on the UI thread. */
    private void failed(String name, String reason) {
        later(() -> group.warning("Download failed for " + OPEN_QUOTE + DownloadsMenu.elided(name) + CLOSE_QUOTE + ".", reason));
    }

    /** Runs {@code task} on the UI thread unless the downloads have stopped by then. */
    private void later(Runnable task) {
        EventQueue.invokeLater(() -> { if (!stopped) task.run(); });
    }

    /** Whether {@code page}'s close has begun; false for no page. Under the lock. */
    private boolean closing(Page page) {
        if (page == null) return false;
        PageState state = pages.get(page);
        return state != null && state.closing;
    }

    /** {@code page}'s state, made the first time. Under the lock. */
    private PageState state(Page page) {
        return pages.computeIfAbsent(page, any -> new PageState());
    }

    private Optional<Download> download(DownloadKey key) {
        return list.all().stream().filter(download -> download.key().equals(key)).findFirst();
    }

    private Optional<Download> serial(String number) {
        try {
            long serial = Long.parseLong(number);
            return list.all().stream().filter(download -> download.serial() == serial).findFirst();
        } catch (NumberFormatException notOurs) {
            return Optional.empty();
        }
    }

    /** The intermediate file a running update names: Chromium's {@code <name>.crdownload}; empty for any other. */
    private static Optional<Path> intermediate(String fullPath) {
        if (!fullPath.endsWith(DownloadNames.INTERMEDIATE)) return Optional.empty();
        try {
            Path file = Path.of(fullPath);
            return file.isAbsolute() && file.getParent() != null ? Optional.of(file) : Optional.empty();
        } catch (InvalidPathException unreadable) {
            return Optional.empty();
        }
    }

    /** {@code reason} without a closing full stop, for a sentence that goes on after it. */
    private static String sentence(String reason) {
        String text = reason.strip();
        while (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }

    /** A failure in the user's terms: the system's reason when it gives one. */
    static String reason(Exception failure) {
        if (failure instanceof AccessDeniedException) return "Permission denied";
        if (failure instanceof FileSystemException system && system.getReason() != null && !system.getReason().isBlank()) return system.getReason();
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}

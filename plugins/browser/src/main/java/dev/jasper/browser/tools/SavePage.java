package dev.jasper.browser.tools;

import dev.jasper.browser.downloads.Downloads;
import dev.jasper.browser.downloads.FileManager;
import dev.jasper.browser.page.Page;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.EventQueue;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * One Save Page As (spec section 10, plan 7's Task 7), from the page's right-click menu. On the background executor the
 * first free folder for the page's host is found in the Downloads folder, {@code host}, then {@code host (1)} and on,
 * numbered at its end as Finder and Explorer number a folder (ruling D7), since Jasper's chooser opens an existing folder
 * rather than choosing it; then Jasper's save chooser asks where, starting there. Cancel does nothing. If the page
 * started another document meanwhile, the save fails; otherwise {@code save-page.js} runs in its top frame under a fresh
 * prefix ({@link ToolScripts#savePage}), and its answer comes in parts ({@link #part}), which a load start
 * ({@link #loadStarted}) or the page's close ({@link #pageClosed}) cuts short, and which must all come within
 * {@value #ANSWER_SECONDS} seconds. Then, on the background executor, {@link PageSaver} fetches and writes the folder
 * with a client of its own through the HTTP proxy as it was ({@link Network}), which is shut down at the save's end, and
 * the folder is shown in the file manager. A failure says "Save Failed" / "Could not save the page: REASON." over the
 * item, or, once its tab has closed, as a warning in the Downloads group, which also warns once if a file has no
 * downloaded-file mark. Nothing is shown once the plugin has stopped. UI thread, but for the work it hands the background
 * executor.
 */
public final class SavePage {
    /** The opener bridge's op that carries the answer's parts. */
    public static final String OP = "savePage";
    /** The chooser's title. */
    static final String TITLE = "Save Page As";
    /** The failure's title, and its dialog's form and OK button. */
    static final String FAILED = "Save Failed", FAILED_FORM = "dev.jasper.browser.save-failed", FAILED_OK = FAILED_FORM + ".ok";
    /** Why a save fails: the page started another document, sent no answer in time, or closed. */
    static final String NAVIGATED = "the page navigated away", NO_ANSWER = "the page did not answer", CLOSED = "the page closed";
    /** How long the page has to answer. */
    static final Duration ANSWER_WAIT = Duration.ofSeconds(30);
    /** How many folder names are tried for a free one: the host's, then numbered up to 999. */
    static final int MOST_FOLDERS = 1000;
    private static final long ANSWER_SECONDS = 30;
    private static final String OPEN_QUOTE = "\u201C", CLOSE_QUOTE = "\u201D";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final System.Logger LOG = System.getLogger(SavePage.class.getName());
    private static final Runnable NOTHING = () -> { };

    /**
     * How a save fetches, as its item's plugin has it when the save starts.
     *
     * @param proxy the HTTP proxy; empty while it is off, when names are looked up here (ruling D5)
     * @param current whether the save's client may still be asked: false once the proxy changed (ruling D6). Any thread
     * @param running whether the plugin still runs, so the save's end may show anything. UI thread
     * @param clients makes the save's client through the proxy given ({@link PageSaver#client}), on the background
     *     executor; a test's fails if asked
     */
    public record Network(Optional<InetSocketAddress> proxy, BooleanSupplier current, BooleanSupplier running,
                          Function<Optional<InetSocketAddress>, HttpClient> clients) {
        /** Rejects nulls. */
        public Network {
            Objects.requireNonNull(proxy, "proxy");
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(clients, "clients");
        }
    }

    private enum State { NAMING, CHOOSING, ANSWERING, SAVING, DONE }

    private final Page page;
    private final ContentTab tab;
    private final PluginContext context;
    private final Path startFolder;
    private final FileManager files;
    private final Network network;
    private final BiFunction<Duration, Runnable, Runnable> later;
    // The page as the save began: its address without user information, whether it is a /dev/null one, and its document.
    private final URI address;
    private final boolean inMemory;
    private final long document;
    private State state = State.NAMING;
    private Path folder;
    private SavePayload payload;
    private Runnable wait = NOTHING;

    private SavePage(Page page, ContentTab tab, PluginContext context, Path startFolder, FileManager files, Network network,
                     BiFunction<Duration, Runnable, Runnable> later) {
        this.page = page;
        this.tab = tab;
        this.context = context;
        this.startFolder = startFolder;
        this.files = files;
        this.network = network;
        this.later = later;
        address = address(page.committedAddress());
        inMemory = page.inMemory();
        document = page.document();
    }

    /**
     * Starts a Save Page As of {@code page}: its folder's free name is found on the background executor, then the chooser
     * asks over {@code tab}.
     *
     * @param page the page, whose document it saves
     * @param tab its item's tab, over which the chooser and a failure show
     * @param context the plugin's context
     * @param startFolder the folder the chooser starts in: the Downloads folder
     * @param files shows the saved folder
     * @param network how the save fetches
     * @param later runs a task on the UI thread after a delay, answering what cancels it
     * @return the save, whose item hands it the page's parts and load starts
     */
    public static SavePage start(Page page, ContentTab tab, PluginContext context, Path startFolder, FileManager files, Network network,
                                 BiFunction<Duration, Runnable, Runnable> later) {
        var save = new SavePage(Objects.requireNonNull(page, "page"), Objects.requireNonNull(tab, "tab"),
            Objects.requireNonNull(context, "context"), Objects.requireNonNull(startFolder, "startFolder"),
            Objects.requireNonNull(files, "files"), Objects.requireNonNull(network, "network"), Objects.requireNonNull(later, "later"));
        save.name();
        return save;
    }

    /**
     * A {@code savePage} report of the page, as the opener bridge hands it on: a part of the answer under this save's
     * prefix is taken, and the last starts the saving; one under another prefix is ignored; anything else that is no part
     * fails the save. Nothing outside the wait for the answer.
     *
     * @param json the request, as the page sent it
     */
    public void part(String json) {
        if (state != State.ANSWERING || json == null) return;
        try {
            if (payload.take(json)) save();
        } catch (IOException refused) {
            fail(refused.getMessage());
        }
    }

    /** The page's main frame started loading a document: while the answer is awaited, the save fails. */
    public void loadStarted() {
        if (state == State.ANSWERING) fail(NAVIGATED);
    }

    /** The page's item closed: a save still to choose ends, and one awaiting its answer fails. */
    public void pageClosed() {
        switch (state) {
            case NAMING, CHOOSING -> state = State.DONE;
            case ANSWERING -> fail(CLOSED);
            default -> { }
        }
    }

    /**
     * Whether the save has ended, saved, cancelled or failed: it takes nothing more.
     *
     * @return whether it has
     */
    public boolean done() {
        return state == State.DONE;
    }

    /** Finds the folder's free name on the background executor, then asks where. */
    private void name() {
        String host = ResourceNames.folderFor(address);
        try {
            context.background().execute(() -> {
                String free = free(startFolder, host);
                EventQueue.invokeLater(() -> choose(free));
            });
        } catch (RejectedExecutionException stopped) {
            state = State.DONE;
        }
    }

    /** The first of {@code host}, {@code host (1)}, … up to 999 that nothing in {@code folder} has; {@code host} if none. */
    static String free(Path folder, String host) {
        for (int n = 0; n < MOST_FOLDERS; n++) {
            String name = n == 0 ? host : host + " (" + n + ")";
            try {
                if (!Files.exists(folder.resolve(name), LinkOption.NOFOLLOW_LINKS)) return name;
            } catch (SecurityException unreadable) {
                return host;
            }
        }
        return host;
    }

    /** Asks where, starting at {@code name} in the start folder; then, unless cancelled or navigated, asks the page. */
    private void choose(String name) {
        if (state != State.NAMING) return;
        if (tab.isClosed() || !network.running().getAsBoolean()) {
            state = State.DONE;
            return;
        }
        state = State.CHOOSING;
        Optional<Path> chosen;
        try {
            chosen = context.windows().chooseSaveFile(tab, TITLE, Optional.of(startFolder.resolve(name)));
        } catch (IllegalArgumentException | IllegalStateException gone) {
            state = State.DONE;
            return;
        }
        if (state != State.CHOOSING) return;
        if (chosen.isEmpty()) {
            state = State.DONE;
            return;
        }
        if (page.document() != document) {
            fail(NAVIGATED);
            return;
        }
        folder = chosen.get();
        String prefix = "jasper-save-" + HexFormat.of().formatHex(random(8)) + "-";
        payload = new SavePayload(prefix);
        state = State.ANSWERING;
        wait = later.apply(ANSWER_WAIT, () -> {
            wait = NOTHING;
            if (state == State.ANSWERING) fail(NO_ANSWER);
        });
        page.run(ToolScripts.savePage(prefix), ToolScripts.SAVE_PAGE_URL);
    }

    /** The whole answer is in: it is read, fetched and written on the background executor. */
    private void save() {
        endWait();
        state = State.SAVING;
        SavePayload answer = payload;
        payload = null;
        Path target = folder;
        try {
            context.background().execute(() -> {
                try {
                    PageSaver.Saved saved = write(answer, target);
                    EventQueue.invokeLater(() -> saved(saved));
                } catch (IOException | RuntimeException failure) {
                    String why = reason(failure);
                    EventQueue.invokeLater(() -> fail(why));
                }
            });
        } catch (RejectedExecutionException stopped) {
            state = State.DONE;
        }
    }

    /** Reads the snapshot and saves it with a client of the save's own, shut down at its end. Background executor. */
    private PageSaver.Saved write(SavePayload answer, Path target) throws IOException {
        SavePayload.Snapshot snapshot = answer.snapshot();
        HttpClient http = network.clients().apply(network.proxy());
        try {
            return new PageSaver(address, inMemory, network.proxy().isPresent(), network.current(), context.platform())
                .save(target, snapshot, http);
        } finally {
            http.shutdownNow();
        }
    }

    /** The folder is saved: it shows in the file manager, and a file left without its mark is told once. */
    private void saved(PageSaver.Saved saved) {
        state = State.DONE;
        if (!network.running().getAsBoolean()) return;
        Path written = saved.index().getParent();
        files.reveal(written);
        saved.unmarked().ifPresent(why -> {
            Path name = written.getFileName();
            context.notifications().group(Downloads.GROUP).warning(OPEN_QUOTE + (name == null ? written : name) + CLOSE_QUOTE
                + " has files without a downloaded-file mark", "Jasper could not mark them as downloaded from the internet, so the system "
                + "may open them without checking them first: " + sentence(why) + ". Open them from the file manager only if you trust them.");
        });
    }

    /** The save failed for {@code reason}: "Save Failed" over the item, or a warning once its tab has closed. */
    private void fail(String reason) {
        endWait();
        state = State.DONE;
        payload = null;
        if (!network.running().getAsBoolean()) return;
        String message = "Could not save the page: " + sentence(reason) + ".";
        if (tab.isClosed()) {
            context.notifications().group(Downloads.GROUP).warning(FAILED, message);
            return;
        }
        try {
            show(message);
        } catch (IllegalArgumentException | IllegalStateException gone) {
            LOG.log(System.Logger.Level.DEBUG, "No window for Save Failed", gone);
            context.notifications().group(Downloads.GROUP).warning(FAILED, message);
        }
    }

    /** Shows "Save Failed" with {@code message} and OK, over the item's window, as the plugin's own prompts show. */
    private void show(String message) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec(FAILED, tab.window(), true));
        var ok = new Nodes.Button(FAILED_OK, ControlState.READY, "OK", "", Nodes.ButtonKind.PRIMARY, FAILED_OK, "");
        UiView view = context.kit().create(new ViewSpec(new Nodes.Form(FAILED_FORM, ControlState.READY, Nodes.FormKind.SHEET, FAILED,
            Optional.empty(), List.of(new Nodes.Text(FAILED_FORM + ".message", ControlState.READY, message, Nodes.TextRole.BODY)),
            Optional.empty(), List.of(ok), FAILED_OK)), event -> {
                if (event instanceof UiEvent.Action action && action.id().equals(FAILED_OK)) dialog.close();
            });
        dialog.setContent(view);
        dialog.onClosed(view::close);
        dialog.show();
    }

    private void endWait() {
        Runnable cancel = wait;
        wait = NOTHING;
        cancel.run();
    }

    /** The page's address as a URI, without user information; {@code about:blank} for one no URI is made of. */
    private static URI address(String committed) {
        return SaveAddresses.parse(committed == null ? "" : committed).filter(URI::isAbsolute).orElse(URI.create("about:blank"));
    }

    private static byte[] random(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /** {@code reason} without the stops at its end, for the sentence it ends. */
    private static String sentence(String reason) {
        String text = reason == null || reason.isBlank() ? "an unknown error" : reason.strip();
        while (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }

    /** A failure in the user's terms: the system's reason when it gives one. */
    private static String reason(Exception failure) {
        if (failure instanceof AccessDeniedException denied) return "permission denied for " + denied.getFile();
        if (failure instanceof FileSystemException system && system.getReason() != null && !system.getReason().isBlank())
            return system.getReason();
        if (failure instanceof FileSystemException system && system.getFile() != null) return system.getFile() + " could not be written";
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}

package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.json.StrictJson;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.HierarchyEvent;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JComponent;
import org.cef.CefClient;
import org.cef.browser.CefFrame;

/**
 * A Markdown preview's page, in plain types: the canvas it is drawn on, made at once, and its browser, made on Chromium's
 * preview client the first time the canvas shows, which loads the preview's shell. Until then it asks nothing of
 * Chromium. The preview client's handlers ({@link PreviewHandlers}) hand it its main frame's router requests, which it
 * has read where they arrive, and the failures of its page; it hands on what they tell on the UI thread. Every method
 * but those runs on the UI thread. With
 * {@link Page}, this is the one class that makes a {@link PageView}: the preview's, which tells screen readers to use
 * the Editor's Basic preview, paints the theme's background until the page's first frame and refuses drops.
 */
public final class PreviewPage implements AutoCloseable {
    /** The canvas's accessible name. */
    static final String NAME = "Markdown preview";
    /** The canvas's accessible description. */
    static final String DESCRIPTION = "Screen readers cannot read this preview. Set Markdown preview to Basic in the Editor's settings to read it.";
    // The name Jasper's calls go by in the page's console and stack traces.
    private static final String SCRIPT_URL = "jasper-preview:call.js";
    private final PageView view = new PageView();
    private final Chromium chromium;
    private final String shell, token;
    private final Runnable created;
    private final Function<String, Optional<Runnable>> requests;
    private final Consumer<Throwable> failed;
    // UI thread: whether the browser was asked for, the browser, whether it hears what it is asked, and the close.
    private boolean started, ready, closed;
    private PageBrowser browser;
    // Chromium's thread: whether the shell's first load went ahead, and whether the page closed.
    private volatile boolean loaded, gone;

    /**
     * The page of a preview whose origin's shell is {@code shell}, with its canvas and no browser yet.
     *
     * @param chromium the plugin's Chromium, asked for the preview client the first time the canvas shows
     * @param shell the shell's address, {@code jasper-preview://<token>/}, which the browser loads
     * @param created told once the page's browser exists, on the UI thread
     * @param requests reads each router request of the page's main frame on the thread it arrives on, Chromium's, so the
     *     UI thread never parses what the page sends, and answers what the request tells, which runs on the UI thread;
     *     empty for a request that tells nothing. It must not throw.
     * @param failed told why the page cannot show, on the UI thread: its client could not be made, or its renderer ended
     */
    public PreviewPage(Chromium chromium, URI shell, Runnable created, Function<String, Optional<Runnable>> requests,
            Consumer<Throwable> failed) {
        this.chromium = Objects.requireNonNull(chromium, "chromium");
        this.shell = shell.toString();
        token = Objects.requireNonNull(shell.getHost(), "the shell's token");
        this.created = Objects.requireNonNull(created, "created");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.failed = Objects.requireNonNull(failed, "failed");
        view.describe(NAME, DESCRIPTION);
        view.refuseDrops();
        view.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && view.isShowing()) start();
        });
    }

    /**
     * The script that runs {@code find.js} in a preview's page with {@code command}, its answers going through the
     * preview client's router, as the preview's Find has it (plan 9's N-F13).
     *
     * @param command a {@code find.js} command, a JSON object Jasper wrote
     * @return the script
     */
    public static String find(String command) {
        return PageFind.SOURCE + "\n(" + command + ", " + StrictJson.quote(PreviewHandlers.QUERY) + ");";
    }

    /**
     * The canvas the page is drawn on, for its preview's caller to lay out.
     *
     * @return the canvas
     */
    public JComponent view() {
        return view;
    }

    /**
     * Paints {@code color} where the page has not drawn yet, as before its first frame.
     *
     * @param color the theme's background
     */
    public void background(Color color) {
        if (!closed) view.fill(color);
    }

    /**
     * Runs {@code script} in the page's main frame; nothing before the page's browser exists, or after its close.
     *
     * @param script the script, which Jasper wrote
     */
    public void run(String script) {
        Objects.requireNonNull(script, "script");
        if (hearing()) onMainFrame(frame -> frame.executeJavaScript(script, SCRIPT_URL, 0));
    }

    /** Copies the page's selection to the clipboard; nothing before the page's browser exists, or after its close. */
    public void copy() {
        if (hearing()) onMainFrame(CefFrame::copy);
    }

    /** Selects the whole page; nothing before the page's browser exists, or after its close. */
    public void selectAll() {
        if (hearing()) onMainFrame(CefFrame::selectAll);
    }

    /** Gives the canvas the keyboard focus. */
    public void focus() {
        if (!closed) view.requestFocusInWindow();
    }

    /** Closes the page's browser, once, and hands on nothing more. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        gone = true;
        PageBrowser made = browser;
        if (made != null) made.close(true);
    }

    /**
     * The canvas shows for the first time: the page asks for the preview client, starting Chromium when nothing has yet,
     * and makes its browser once the client is ready. The client's handlers are installed before any preview is made on
     * it.
     */
    private void start() {
        if (started || closed) return;
        started = true;
        PreviewHandlers.prepare(chromium);
        // The preview client completes on the UI thread, a failure too.
        chromium.previewClient().whenComplete((client, failure) -> {
            if (failure != null) {
                if (!closed) failed.accept(failure);
            } else {
                make(client);
            }
        });
    }

    /** The preview client is ready: makes the browser, which loads the shell once its native browser exists. */
    private void make(CefClient client) {
        if (closed) return;
        var made = new PageBrowser(client, shell, null, new PageRenderer(view), view);
        made.previewOwner(this);
        browser = made;
        made.attach();
    }

    /** The browser's native browser exists: from now on it hears what it is asked. A browser made after close closes. */
    void created(PageBrowser made) {
        if (made != browser) return;
        if (closed) {
            made.close(true);
            return;
        }
        ready = true;
        created.run();
    }

    /**
     * Whether the page's browser hears what it is asked: it exists, and the page has not closed. Checked before a command
     * on its frame is formed, as forming one names the frame's Chromium type, which a preview that never showed must not
     * load.
     */
    private boolean hearing() {
        return ready && !closed;
    }

    /** Runs {@code command} on the page's main frame, then releases only this handle to the frame. */
    private void onMainFrame(Consumer<CefFrame> command) {
        CefFrame frame = browser.getMainFrame();
        if (frame == null) return;
        try {
            command.accept(frame);
        } finally {
            frame.dispose();
        }
    }

    /** The shell's address, {@code jasper-preview://<token>/}. Any thread. */
    String shell() {
        return shell;
    }

    /** The token of the preview's origin. Any thread. */
    String token() {
        return token;
    }

    /**
     * Whether the main frame may load {@code url}: only the shell, and only once ({@link PreviewHandlers#navigates}).
     * Chromium's thread, which asks one navigation at a time.
     */
    boolean mayLoad(String url) {
        if (gone || !PreviewHandlers.navigates(url, shell, !loaded)) return false;
        loaded = true;
        return true;
    }

    /**
     * A router request of the main frame, which the router checked: read on this thread, and what it tells run on the
     * UI thread. Any thread.
     */
    void received(String request) {
        if (gone) return;
        requests.apply(request).ifPresent(told -> EventQueue.invokeLater(() -> {
            if (!closed) told.run();
        }));
    }

    /** The user activated link {@code id} and the page's script did not take it: handed on as the script would. Any thread. */
    void linked(int id) {
        received("{\"op\":\"link\",\"id\":\"" + id + "\"}");
    }

    /** The page cannot show any more, for {@code reason}: told on the UI thread. Any thread. */
    void ended(String reason) {
        EventQueue.invokeLater(() -> {
            if (!closed) failed.accept(new IllegalStateException(reason));
        });
    }

    /**
     * Chromium gives the focus away past the page's last link, or before its first: Swing's focus moves on from the
     * canvas, unless JCEF's client already moved it. Any thread.
     */
    void focusLeft(boolean next) {
        EventQueue.invokeLater(() -> {
            if (closed || !view.isFocusOwner()) return;
            if (next) view.transferFocus();
            else view.transferFocusBackward();
        });
    }
}

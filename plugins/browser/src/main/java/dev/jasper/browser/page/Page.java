package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.sdk.Subscription;
import java.awt.EventQueue;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.JComponent;
import org.cef.CefClient;
import org.cef.browser.CefFrame;

/**
 * One page as its item drives it, in plain types: the canvas it is drawn on, where it is, its title, whether it loads
 * and where it can go, and what it is asked to do. Its Chromium browser is made only once {@link #start} asks for it,
 * the first time its item shows; until then the page holds the address it will load and asks nothing of Chromium.
 * Every method runs on the UI thread, and so does every callback it makes. This is the one class that makes a
 * {@link PageView}.
 */
public final class Page implements AutoCloseable {
    private final PageView view = new PageView();
    private final Runnable changed;
    private final List<Consumer<String>> loadStarts = new CopyOnWriteArrayList<>();
    private final List<Consumer<NavigationButton>> navigationButtons = new CopyOnWriteArrayList<>();
    // What the page loads once its browser can: first its start, then the last address asked for before that.
    private URI requested;
    // Where the page is, as Chromium last reported it, or what it was last asked to load since.
    private String address;
    private String title = "";
    private boolean loading, canGoBack, canGoForward, started, ready, closed;
    private PageBrowser browser;
    // The address the browser was made with, which it loads by itself.
    private String made;

    /**
     * A page that will load {@code url}, with its canvas and no browser yet.
     *
     * @param url what it loads first
     * @param changed told whenever its address, title, loading state or where it can go changes
     */
    public Page(URI url, Runnable changed) {
        requested = Objects.requireNonNull(url, "url");
        address = url.toString();
        this.changed = Objects.requireNonNull(changed, "changed");
        view.onNavigationButton(this::navigationButton);
    }

    /**
     * The canvas the page is drawn on, for its item's custom content.
     *
     * @return the canvas
     */
    public JComponent view() {
        return view;
    }

    /**
     * Makes the page's browser: asks {@code chromium} for its client, starting Chromium when no page has yet, and makes
     * the browser once the client is ready, which then loads what the page was last asked to. The client's display and
     * load handlers are installed before any page is made on it. Once; nothing after {@link #close()}.
     *
     * @param chromium the plugin's Chromium
     */
    public void start(Chromium chromium) {
        Objects.requireNonNull(chromium, "chromium");
        if (started || closed) return;
        started = true;
        PageEvents.prepare(chromium);
        // A ready client completes on the UI thread; a failure can complete anywhere, and the plugin tells it once.
        chromium.client().whenComplete((client, failure) -> {
            if (failure == null) EventQueue.invokeLater(() -> make(client));
        });
    }

    /**
     * Whether {@link #start} has been asked.
     *
     * @return whether the page asked for its browser
     */
    public boolean started() {
        return started;
    }

    /**
     * Loads {@code url}. Before the page has its browser, it is what the browser loads first.
     *
     * @param url the address
     */
    public void load(URI url) {
        Objects.requireNonNull(url, "url");
        if (closed) return;
        requested = url;
        address = url.toString();
        if (ready) browser.loadURL(address);
        changed.run();
    }

    /** Goes back in the page's history, when it can. */
    public void back() {
        if (ready && !closed) browser.goBack();
    }

    /** Goes forward in the page's history, when it can. */
    public void forward() {
        if (ready && !closed) browser.goForward();
    }

    /** Loads the page again. */
    public void reload() {
        if (ready && !closed) browser.reload();
    }

    /** Stops the page loading. */
    public void stop() {
        if (ready && !closed) browser.stopLoad();
    }

    /** Copies the selection of the page's focused frame, as its Edit menu's Copy would. */
    public void copy() {
        CefFrame frame = focusedFrame();
        if (frame == null) return;
        try {
            frame.copy();
        } finally {
            frame.dispose();
        }
    }

    /** Pastes the clipboard into the page's focused frame, as its Edit menu's Paste would. */
    public void paste() {
        CefFrame frame = focusedFrame();
        if (frame == null) return;
        try {
            frame.paste();
        } finally {
            frame.dispose();
        }
    }

    /** Gives the page the keyboard focus. */
    public void focus() {
        view.requestFocusInWindow();
    }

    /**
     * Where the page is: its main frame's address, or what it was last asked to load.
     *
     * @return the address, as Chromium writes it
     */
    public String address() {
        return address;
    }

    /**
     * The page's title.
     *
     * @return the title, or empty while the page has none
     */
    public String title() {
        return title;
    }

    /**
     * Whether the page loads.
     *
     * @return whether it loads
     */
    public boolean loading() {
        return loading;
    }

    /**
     * Whether the page can go back.
     *
     * @return whether its history has an entry before this one
     */
    public boolean canGoBack() {
        return canGoBack;
    }

    /**
     * Whether the page can go forward.
     *
     * @return whether its history has an entry after this one
     */
    public boolean canGoForward() {
        return canGoForward;
    }

    /**
     * Tells {@code listener} each time the mouse's back or forward button is released over the page, on Windows and
     * Linux. The page itself never sees those buttons.
     *
     * @param listener told which, on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onNavigationButton(Consumer<NavigationButton> listener) {
        Objects.requireNonNull(listener, "listener");
        navigationButtons.add(listener);
        return () -> navigationButtons.remove(listener);
    }

    /**
     * Tells {@code listener} each time the page's main frame starts loading, with the address it loads.
     *
     * @param listener told on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onLoadStart(Consumer<String> listener) {
        Objects.requireNonNull(listener, "listener");
        loadStarts.add(listener);
        return () -> loadStarts.remove(listener);
    }

    /** Closes the page's browser, which ends only this page, and stops telling anything of it. Once. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        loadStarts.clear();
        navigationButtons.clear();
        view.setHoverUrl("");
        PageBrowser page = browser;
        if (page != null) page.close(true);
    }

    /**
     * The frame that has the page's focus, or none before the page has its browser or after its close; the caller
     * disposes it, which releases only this handle to the frame.
     */
    private CefFrame focusedFrame() {
        return ready && !closed ? browser.getFocusedFrame() : null;
    }

    /** The mouse's back or forward button was released over the page. */
    private void navigationButton(NavigationButton button) {
        if (closed) return;
        for (Consumer<NavigationButton> listener : navigationButtons) listener.accept(button);
    }

    /** The client is ready: makes the browser, which loads what the page was last asked to. */
    private void make(CefClient client) {
        if (closed) return;
        made = requested.toString();
        PageBrowser page = new PageBrowser(client, made, null, new PageRenderer(view), view);
        page.owner(this);
        browser = page;
        page.attach();
    }

    /** The browser's native browser exists: from now on it hears what it is asked, and loads what it missed. */
    void created(PageBrowser created) {
        if (closed || created != browser) return;
        ready = true;
        String next = requested.toString();
        if (!next.equals(made)) browser.loadURL(next);
    }

    /** The main frame's address changed. */
    void addressChanged(String url) {
        if (closed || url == null) return;
        address = url;
        changed.run();
    }

    /** The page's title changed. */
    void titleChanged(String value) {
        if (closed) return;
        title = value == null ? "" : value;
        changed.run();
    }

    /** The page started or stopped loading, and where it can go changed. */
    void loadingChanged(boolean isLoading, boolean back, boolean forward) {
        if (closed) return;
        loading = isLoading;
        canGoBack = back;
        canGoForward = forward;
        changed.run();
    }

    /** The main frame started loading {@code url}. */
    void loadStarted(String url) {
        if (closed) return;
        for (Consumer<String> listener : loadStarts) listener.accept(url == null ? "" : url);
    }

    /** The address of the link under the pointer, or empty. Any thread. */
    void hovered(String url) {
        if (!closed) view.setHoverUrl(url);
    }
}

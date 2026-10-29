package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.EventQueue;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
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
    private final List<Runnable> closes = new CopyOnWriteArrayList<>();
    private final List<Runnable> navigations = new CopyOnWriteArrayList<>();
    private final List<Runnable> dialogResets = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> addressChanges = new CopyOnWriteArrayList<>();
    private BiFunction<String, TabPlacement, Optional<Page>> opener = (url, placement) -> Optional.empty();
    private Runnable itemCloser = () -> { }, itemShower = () -> { };
    // Where the page's dialogs and file choosers show; its item gives it.
    private DialogPlace dialogPlace;
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
     * the browser once the client is ready, which then loads what the page was last asked to. The client's display, load
     * and keyboard handlers are installed before any page is made on it. Once; nothing after {@link #close()}.
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
        navigated();
        if (ready) browser.loadURL(address);
        changed.run();
    }

    /** Goes back in the page's history, when it can: a page with nothing before this entry does nothing. */
    public void back() {
        if (!ready || closed || !canGoBack) return;
        navigated();
        browser.goBack();
    }

    /** Goes forward in the page's history, when it can: a page with nothing after this entry does nothing. */
    public void forward() {
        if (!ready || closed || !canGoForward) return;
        navigated();
        browser.goForward();
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

    /**
     * Tells {@code listener} each time Chromium reports a new address for the page's main frame: a navigation that
     * commits, to another document or within this one. A load that only starts, or is asked for, tells nothing.
     *
     * @param listener told the address, as Chromium writes it, on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onAddressChange(Consumer<String> listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) return () -> { };
        addressChanges.add(listener);
        return () -> addressChanges.remove(listener);
    }

    /**
     * Has {@code opener} open each link or popup of the page that goes to an item of its own, beside the page's item: the
     * page's item sets it. It replaces the one before.
     *
     * @param opener told the address Chromium gave and where the new item goes, on the UI thread; it answers the new
     *     item's page, or empty when it opened none
     */
    public void onOpenItem(BiFunction<String, TabPlacement, Optional<Page>> opener) {
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /**
     * A link or popup of the page goes to an item of its own: tells the page's opener. Nothing after {@link #close()}.
     *
     * @param url the address, as Chromium gave it
     * @param placement where the new item goes: next to the page's tab, or split from its pane
     * @return the new item's page, or empty when none opened
     */
    public Optional<Page> openItem(String url, TabPlacement placement) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(placement, "placement");
        return closed ? Optional.empty() : Objects.requireNonNull(opener.apply(url, placement), "opened");
    }

    /**
     * Has {@code closer} close the page's item, as Close Pane would, when the page asks: the page's item sets it. It
     * replaces the one before.
     *
     * @param closer closes the item, on the UI thread
     */
    public void onCloseItem(Runnable closer) {
        itemCloser = Objects.requireNonNull(closer, "closer");
    }

    /** The page asks to close its item, as a script's {@code window.close()} does. Nothing after {@link #close()}. */
    public void closeItem() {
        if (!closed) itemCloser.run();
    }

    /**
     * Whether Chromium holds back the close of {@code page}'s browser, as its {@code DoClose} asks: false lets Chromium
     * destroy the browser at once. A close Java asked for, Jasper's close of the page or JCEF's of its client's browsers,
     * goes ahead. Any other comes from the page itself, a script's {@code window.close()}: it is held back, and the
     * page's item closes on the UI thread, as Chrome closes the tab, which closes the browser in turn. Any thread.
     *
     * @param asked whether Java asked the browser to close
     * @param page the browser's page, or null before it has one
     * @param ui runs a task on the UI thread
     * @return whether Chromium holds the close back
     */
    static boolean holdsClose(boolean asked, Page page, Consumer<Runnable> ui) {
        if (asked) return false;
        if (page != null) ui.accept(page::closeItem);
        return true;
    }

    /**
     * Has {@code shower} select the page's item in its window and raise that window, when the page asks: the page's item
     * sets it. It replaces the one before.
     *
     * @param shower shows the item, on the UI thread
     */
    public void onShowItem(Runnable shower) {
        itemShower = Objects.requireNonNull(shower, "shower");
    }

    /** The page asks to show its item, as a script's {@code focus()} of its window does. Nothing after {@link #close()}. */
    public void showItem() {
        if (!closed) itemShower.run();
    }

    /**
     * Has the page's dialogs and file choosers show at {@code place}: the page's item gives it once it has its tab. It
     * replaces the one before.
     *
     * @param place over the item's tab, with the plugin's kit and windows
     */
    public void setDialogPlace(DialogPlace place) {
        Objects.requireNonNull(place, "place");
        if (!closed) dialogPlace = place;
    }

    /**
     * Where the page's dialogs and file choosers show.
     *
     * @return the place its item gave, or empty before that and after {@link #close()}
     */
    public Optional<DialogPlace> dialogPlace() {
        return closed ? Optional.empty() : Optional.ofNullable(dialogPlace);
    }

    /**
     * Tells {@code listener} each time Chromium cancels the page's dialogs, as it does when a new document replaces the
     * page's: a dialog shown or waiting to show for the page is then no longer asked.
     *
     * @param listener told on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onDialogsReset(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) return () -> { };
        dialogResets.add(listener);
        return () -> dialogResets.remove(listener);
    }

    /** Chromium cancels the page's dialogs: tells those who asked {@link #onDialogsReset}. Nothing after {@link #close()}. */
    public void dialogsReset() {
        if (closed) return;
        for (Runnable listener : dialogResets) listener.run();
    }

    /**
     * Tells {@code listener} each time Jasper, not the page's document, sends the page elsewhere: {@link #load}, or a
     * {@link #back} or {@link #forward} the page's history allows, as the user's address field, Web menu and mouse buttons
     * do. Chromium puts such a
     * page in a window group of its own, apart from its opener and its popups.
     *
     * @param listener told on the UI thread, before the page goes
     * @return the registration; the page's close ends it
     */
    public Subscription onNavigate(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) return () -> { };
        navigations.add(listener);
        return () -> navigations.remove(listener);
    }

    /**
     * Tells {@code listener} once, when the page closes with its item; at once when it has closed already.
     *
     * @param listener told on the UI thread
     * @return the registration
     */
    public Subscription onClose(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) {
            listener.run();
            return () -> { };
        }
        closes.add(listener);
        return () -> closes.remove(listener);
    }

    /**
     * Loads {@code url} in the page itself, as a plain click on a link to it would: for a modified click Chromium would
     * have opened elsewhere. Nothing before the page has its browser or after {@link #close()}.
     *
     * @param url the address, as Chromium gave it
     */
    public void follow(String url) {
        Objects.requireNonNull(url, "url");
        if (ready && !closed) browser.loadURL(url);
    }

    /**
     * Closes the page's browser, which ends only this page, tells those who asked {@link #onClose}, and stops telling
     * anything of it. Once.
     */
    @Override public void close() {
        if (closed) return;
        closed = true;
        loadStarts.clear();
        navigationButtons.clear();
        navigations.clear();
        dialogResets.clear();
        addressChanges.clear();
        opener = (url, placement) -> Optional.empty();
        itemCloser = () -> { };
        itemShower = () -> { };
        dialogPlace = null;
        view.setHoverUrl("");
        PageBrowser page = browser;
        if (page != null) page.close(true);
        List<Runnable> told = List.copyOf(closes);
        closes.clear();
        for (Runnable listener : told) listener.run();
    }

    /**
     * The frame that has the page's focus, or none before the page has its browser or after its close; the caller
     * disposes it, which releases only this handle to the frame.
     */
    private CefFrame focusedFrame() {
        return ready && !closed ? browser.getFocusedFrame() : null;
    }

    /** Jasper sends the page elsewhere. */
    private void navigated() {
        for (Runnable listener : navigations) listener.run();
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
        for (Consumer<String> listener : addressChanges) listener.accept(url);
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

    /**
     * The main frame started loading {@code url}: the page has no title until the new document gives one, whatever
     * Chromium reports for a document without one.
     */
    void loadStarted(String url) {
        if (closed) return;
        if (!title.isEmpty()) {
            title = "";
            changed.run();
        }
        for (Consumer<String> listener : loadStarts) listener.accept(url == null ? "" : url);
    }

    /** The address of the link under the pointer, or empty. Any thread. */
    void hovered(String url) {
        if (!closed) view.setHoverUrl(url);
    }
}

package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.store.Reached;
import java.awt.EventQueue;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefKeyboardHandler;
import org.cef.handler.CefKeyboardHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;

/**
 * The client's one display, load and keyboard handler. JCEF keeps the first handler of each kind a client is given,
 * so these serve every page: each callback goes to the {@link Page} of the browser it is about. Chromium calls them
 * on its own UI thread (on macOS, AppKit's main thread), never Swing's, so what a page shows changes on the UI thread.
 * The hover address goes straight to the canvas, which takes it from any thread. A main frame's load start also goes,
 * at once and on Chromium's thread, to the hooks the {@code links} package gave the client
 * ({@link #onMainFrameLoadStart}), and the main frame then runs {@code detect-favicon.js}, which names the document's
 * icon through the opener bridge's router for the page to fetch ({@link Page#faviconDeclared}). Each main-frame load
 * start shows the page at its own zoom again, there and then, as Chromium keeps one zoom per host. A main frame's failed
 * load goes to its page with Chromium's name for the error ({@link Page#onLoadFailure}), for its item's error page. Each
 * main-frame load start reaches its page with how the load reached it ({@link #reached}), for its item's history
 * ({@link Page#onVisit}). A page that enters fullscreen is told at once to leave it: Jasper refuses fullscreen (spec
 * section 5). A page's {@link DevTools} browser is no page's: none of this reaches it, but for the editing keys.
 *
 * <p>JCEF hands every load, on every client, the same six {@link CefRequest.TransitionType} constants, and ORs each
 * load's qualifiers into the constant's own value without ever clearing them, so a flag one load set would be read by
 * every later load of that source. {@link Load#onLoadStart}, and the preview client's load handler
 * ({@code PreviewHandlers}), therefore read a transition before anything else and clear there and then, whatever the
 * load, the six qualifiers JCEF names ({@link CefRequest.TransitionFlags}). Chromium's other qualifier bits, which JCEF
 * names no flag for (from the address bar {@code 0x02000000}, a home page {@code 0x04000000}, from an API
 * {@code 0x08000000}), cannot be cleared and stay on the constants: only the named flags are ever tested, never
 * {@code getValue()}. No other code may take a {@code TransitionType}, {@code CefRequest.getTransitionType()} included:
 * it would leave its load's flags for the next.
 */
public final class PageEvents {
    private static final Consumer<CefClient> INSTALL = PageEvents::install;
    private static final Predicate<CefBrowser> PAGE_BROWSER = PageEvents::answersAbout;
    /**
     * What a page that entered fullscreen runs in its main frame: it leaves fullscreen. Chromium may tell Jasper before
     * the document's fullscreen element is set, so a document without one yet leaves on its next fullscreen change.
     */
    private static final String EXIT_FULLSCREEN = "(function () { function leave() { if (document.fullscreenElement) "
        + "document.exitFullscreen().catch(function () { }); } if (document.fullscreenElement) leave(); "
        + "else document.addEventListener('fullscreenchange', leave, { once: true }); })();";
    private static final String EXIT_FULLSCREEN_URL = "jasper-about:exit-fullscreen.js";
    /** What each main frame runs as its document starts loading: it names the document's icon once it is parsed. */
    private static final String DETECT_FAVICON = script("detect-favicon.js");
    private static final String DETECT_FAVICON_URL = "jasper-about:detect-favicon.js";
    private static final System.Logger LOG = System.getLogger(PageEvents.class.getName());
    // The hooks each client was given, which hear its pages' main-frame load starts on Chromium's thread.
    private static final Map<CefClient, List<BiConsumer<PageBrowser, CefFrame>>> LOAD_STARTS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private PageEvents() { }

    /**
     * Has {@code hook} hear each main-frame load start of the pages made on {@code client}, at once on Chromium's own
     * thread, as Chromium starts loading the new document and before Swing hears of it: for what must reach the document
     * itself, such as a script Jasper runs in it. Sub-frames' load starts are not told. Give it once, as the client is
     * made, before any page.
     *
     * @param client the client the pages are made on
     * @param hook told the page's browser and its main frame, which the hook must not dispose; it must return quickly
     *     and must not throw
     */
    public static void onMainFrameLoadStart(CefClient client, BiConsumer<PageBrowser, CefFrame> hook) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(hook, "hook");
        LOAD_STARTS.computeIfAbsent(client, any -> new CopyOnWriteArrayList<>()).add(hook);
    }

    /**
     * Has Chromium install the handlers on its client before any page is made on it, once, however often asked, and
     * answer {@code jasper-about:} for pages' browsers only (plan 4b's P47).
     */
    static void prepare(Chromium chromium) {
        chromium.onClient(INSTALL);
        chromium.answerAboutFor(PAGE_BROWSER);
    }

    /**
     * Whether {@code browser} is a page's, whose main-frame requests {@code jasper-about:} answers: not a page's
     * DevTools', which have no page of their own, nor any other. Chromium's IO thread.
     */
    static boolean answersAbout(CefBrowser browser) {
        return page(browser) != null;
    }

    private static void install(CefClient client) {
        client.addDisplayHandler(new Display());
        client.addLoadHandler(new Load());
        client.addKeyboardHandler(new Keyboard());
    }

    /** The page script {@code name}, one of this package's resources. */
    static String script(String name) {
        try (InputStream in = PageEvents.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("The Browser plugin has no " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** The page {@code browser} shows; null for a browser that is not a page's. */
    private static Page page(CefBrowser browser) {
        return browser instanceof PageBrowser page ? page.owner() : null;
    }

    private static void later(CefBrowser browser, Consumer<Page> event) {
        Page page = page(browser);
        if (page != null) EventQueue.invokeLater(() -> event.accept(page));
    }

    /**
     * How the load {@code transition} names reached its page, read from it, whose named qualifiers are then cleared, as
     * JCEF never clears them itself; JCEF leaves a source it does not map without a transition. Chromium's thread.
     *
     * @return the way; {@link Reached#OTHER} without a transition
     */
    static Reached reached(CefRequest.TransitionType transition) {
        if (transition == null) return Reached.OTHER;
        int source = transition.getSource(), qualifiers = transition.getQualifiers();
        for (CefRequest.TransitionFlags flag : CefRequest.TransitionFlags.values()) transition.removeQualifier(flag);
        return reached(source, qualifiers);
    }

    /**
     * How a load reached its page, by Chromium's transition: Back or Forward, then a client or server redirect, then the
     * source decides, a reload, a form submitted, a link or an address typed, which Jasper's own loads are too.
     *
     * @param source the transition's source, as {@code getSource()} gives it
     * @param qualifiers the transition's qualifiers, as {@code getQualifiers()} gives them
     * @return the way; {@link Reached#OTHER} for any other source
     */
    static Reached reached(int source, int qualifiers) {
        if ((qualifiers & CefRequest.TransitionFlags.TT_FORWARD_BACK_FLAG.getValue()) != 0) return Reached.BACK_FORWARD;
        int redirects = CefRequest.TransitionFlags.TT_CLIENT_REDIRECT_FLAG.getValue()
            | CefRequest.TransitionFlags.TT_SERVER_REDIRECT_FLAG.getValue();
        if ((qualifiers & redirects) != 0) return Reached.REDIRECT;
        // getSource() masks the qualifiers off, so a constant's source is its own whatever flags it holds.
        if (source == CefRequest.TransitionType.TT_RELOAD.getSource()) return Reached.RELOAD;
        if (source == CefRequest.TransitionType.TT_FORM_SUBMIT.getSource()) return Reached.FORM_SUBMIT;
        if (source == CefRequest.TransitionType.TT_LINK.getSource()) return Reached.LINK;
        if (source == CefRequest.TransitionType.TT_EXPLICIT.getSource()) return Reached.TYPED;
        return Reached.OTHER;
    }

    /**
     * A load of {@code page} in {@code frame} failed with {@code errorCode}: a main frame's failure is handed to the UI
     * thread through {@code ui}, for the page, by Chromium's name for the error, or empty for one it does not name; a
     * sub-frame's, and one without a page or frame, are not. Chromium's thread.
     */
    static void loadError(Page page, CefFrame frame, CefLoadHandler.ErrorCode errorCode, String failedUrl, Consumer<Runnable> ui) {
        if (page == null || frame == null || !frame.isMain()) return;
        String code = errorCode == null ? "" : errorCode.name();
        ui.accept(() -> page.loadFailed(failedUrl, code));
    }

    /**
     * The main frame's address and the page's title, the address of the link under the pointer, and the fullscreen a
     * page is refused.
     */
    private static final class Display extends CefDisplayHandlerAdapter {
        @Override public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
            if (frame != null && frame.isMain()) later(browser, page -> page.addressChanged(url));
        }

        @Override public void onTitleChange(CefBrowser browser, String title) {
            later(browser, page -> page.titleChanged(title));
        }

        @Override public void onStatusMessage(CefBrowser browser, String value) {
            Page page = page(browser);
            if (page != null) page.hovered(value);
        }

        /** Jasper refuses fullscreen: a page that enters it leaves it at once. */
        @Override public void onFullscreenModeChange(CefBrowser browser, boolean fullscreen) {
            if (fullscreen && page(browser) != null) browser.executeJavaScript(EXIT_FULLSCREEN, EXIT_FULLSCREEN_URL, 0);
        }
    }

    /**
     * The keys a page and its scripts left alone that are an {@link EditingCommand}: on macOS, Select All, Cut, Undo and
     * Redo, which Chromium leaves to the browser's Edit menu there. Chromium asks only after the page declined the key,
     * so a script that takes Cmd+Z itself undoes once. So it is in a page's DevTools.
     */
    static final class Keyboard extends CefKeyboardHandlerAdapter {
        @Override public boolean onKeyEvent(CefBrowser browser, CefKeyboardHandler.CefKeyEvent event) {
            return edit(browser, EditingCommand.of(event.type == CefKeyboardHandler.CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN,
                event.modifiers, event.windows_key_code), CefBrowser::getFocusedFrame);
        }
    }

    /**
     * Runs {@code command} on the frame {@code focused} gives as {@code browser}'s focused one, then releases only this
     * handle to it, when {@code browser} is a page's or a page's DevTools browser. Chromium's thread.
     *
     * @return whether a command ran, which Chromium then does not
     */
    static boolean edit(CefBrowser browser, Optional<EditingCommand> command, Function<CefBrowser, CefFrame> focused) {
        if (!(browser instanceof PageBrowser) || command.isEmpty()) return false;
        CefFrame frame = focused.apply(browser);
        if (frame == null) return false;
        try {
            switch (command.get()) {
                case SELECT_ALL -> frame.selectAll();
                case CUT -> frame.cut();
                case UNDO -> frame.undo();
                case REDO -> frame.redo();
            }
        } finally {
            frame.dispose();
        }
        return true;
    }

    /** Whether the page loads and where it can go, and each main-frame load's start, end and failure. */
    static final class Load extends CefLoadHandlerAdapter {
        @Override public void onLoadingStateChange(CefBrowser browser, boolean loading, boolean canGoBack, boolean canGoForward) {
            later(browser, page -> page.loadingChanged(loading, canGoBack, canGoForward));
        }

        @Override public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transition) {
            Reached reached = reached(transition); // first, before any return: it clears the flags JCEF left
            if (frame == null || !frame.isMain()) return;
            if (browser instanceof PageBrowser shown && shown.owner() != null) {
                List<BiConsumer<PageBrowser, CefFrame>> hooks = LOAD_STARTS.get(browser.getClient());
                if (hooks != null) for (BiConsumer<PageBrowser, CefFrame> hook : hooks) hook.accept(shown, frame);
                shown.keepZoom();
                try {
                    frame.executeJavaScript(DETECT_FAVICON, DETECT_FAVICON_URL, 0);
                } catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.WARNING, "A page's favicon script could not run", failure);
                }
            }
            String url = frame.getURL();
            later(browser, page -> page.loadStarted(url, reached));
        }

        @Override public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
            if (frame != null && frame.isMain()) later(browser, Page::loadEnded);
        }

        @Override public void onLoadError(CefBrowser browser, CefFrame frame, CefLoadHandler.ErrorCode errorCode, String errorText,
                                          String failedUrl) {
            loadError(page(browser), frame, errorCode, failedUrl, EventQueue::invokeLater);
        }
    }
}

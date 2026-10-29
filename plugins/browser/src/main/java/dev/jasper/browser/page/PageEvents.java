package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import java.awt.EventQueue;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefKeyboardHandler;
import org.cef.handler.CefKeyboardHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;

/**
 * The client's one display, load and keyboard handler. JCEF keeps the first handler of each kind a client is given,
 * so these serve every page: each callback goes to the {@link Page} of the browser it is about. Chromium calls them
 * on its own UI thread (on macOS, AppKit's main thread), never Swing's, so what a page shows changes on the UI thread.
 * The hover address goes straight to the canvas, which takes it from any thread. A main frame's load start also goes,
 * at once and on Chromium's thread, to the hooks the {@code links} package gave the client
 * ({@link #onMainFrameLoadStart}). A page that enters fullscreen is told at once to leave it: Jasper refuses
 * fullscreen (spec section 5).
 */
public final class PageEvents {
    private static final Consumer<CefClient> INSTALL = PageEvents::install;
    /**
     * What a page that entered fullscreen runs in its main frame: it leaves fullscreen. Chromium may tell Jasper before
     * the document's fullscreen element is set, so a document without one yet leaves on its next fullscreen change.
     */
    private static final String EXIT_FULLSCREEN = "(function () { function leave() { if (document.fullscreenElement) "
        + "document.exitFullscreen().catch(function () { }); } if (document.fullscreenElement) leave(); "
        + "else document.addEventListener('fullscreenchange', leave, { once: true }); })();";
    private static final String EXIT_FULLSCREEN_URL = "jasper-about:exit-fullscreen.js";
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

    /** Has Chromium install the handlers on its client before any page is made on it; once, however often asked. */
    static void prepare(Chromium chromium) {
        chromium.onClient(INSTALL);
    }

    private static void install(CefClient client) {
        client.addDisplayHandler(new Display());
        client.addLoadHandler(new Load());
        client.addKeyboardHandler(new Keyboard());
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
     * so a script that takes Cmd+Z itself undoes once.
     */
    private static final class Keyboard extends CefKeyboardHandlerAdapter {
        @Override public boolean onKeyEvent(CefBrowser browser, CefKeyboardHandler.CefKeyEvent event) {
            if (page(browser) == null) return false;
            var command = EditingCommand.of(event.type == CefKeyboardHandler.CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN,
                event.modifiers, event.windows_key_code);
            if (command.isEmpty()) return false;
            CefFrame frame = browser.getFocusedFrame();
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
    }

    /** Whether the page loads and where it can go, and each main-frame load's start. */
    private static final class Load extends CefLoadHandlerAdapter {
        @Override public void onLoadingStateChange(CefBrowser browser, boolean loading, boolean canGoBack, boolean canGoForward) {
            later(browser, page -> page.loadingChanged(loading, canGoBack, canGoForward));
        }

        @Override public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transition) {
            if (frame == null || !frame.isMain()) return;
            if (browser instanceof PageBrowser shown) {
                List<BiConsumer<PageBrowser, CefFrame>> hooks = LOAD_STARTS.get(browser.getClient());
                if (hooks != null) for (BiConsumer<PageBrowser, CefFrame> hook : hooks) hook.accept(shown, frame);
            }
            String url = frame.getURL();
            later(browser, page -> page.loadStarted(url));
        }
    }
}

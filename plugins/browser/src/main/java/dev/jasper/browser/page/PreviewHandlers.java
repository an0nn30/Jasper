package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.engine.PreviewScheme;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefJSDialogCallback;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefQueryCallback;
import org.cef.callback.CefRunContextMenuCallback;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefFocusHandlerAdapter;
import org.cef.handler.CefJSDialogHandler;
import org.cef.handler.CefJSDialogHandlerAdapter;
import org.cef.handler.CefKeyboardHandler;
import org.cef.handler.CefKeyboardHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefRequestHandler;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;

/**
 * The preview client's own handlers, one of each kind, which none of the pages' reach (spec section 18: the file is
 * untrusted). Each callback goes to the {@link PreviewPage} of the browser it is about:
 *
 * <ul>
 *   <li><b>Navigation.</b> Only the shell's first load goes ahead ({@link #navigates}); every other navigation is
 *   cancelled, and one to a {@code jasper-link:} address the page's script did not catch is that link activated
 *   ({@link #link}).</li>
 *   <li><b>Requests.</b> Every request outside the preview's own origin ({@link PreviewScheme#allows}) is cancelled.</li>
 *   <li><b>Refused.</b> Popups are cancelled, script dialogs suppressed and the right-click menu emptied, as the Editor's
 *   Basic preview has none.</li>
 *   <li><b>Focus.</b> When Chromium gives the focus away, past the page's last link or before its first, Swing's moves on
 *   from the canvas to the next or previous component.</li>
 *   <li><b>Keys.</b> On macOS, Select All, which Chromium leaves to the Edit menu there (4a's R35); the preview has no
 *   other editing key.</li>
 *   <li><b>The router</b>, {@value #QUERY} / {@value #CANCEL}: a request counts only from the main frame of a preview
 *   whose address is on that preview's origin, up to 4 MiB ({@link #counts}), and goes to its page. Every request is
 *   answered {@code success("")}.</li>
 *   <li><b>The renderer.</b> When the page's renderer ends, the page cannot show any more.</li>
 *   <li><b>Load starts</b> clear the qualifiers JCEF leaves on the transition's shared constants, as the pages' client
 *   does ({@link PageEvents#reached(CefRequest.TransitionType)}): the two clients share them.</li>
 * </ul>
 *
 * Chromium calls them on its own threads; what reaches a page goes to the UI thread there.
 */
final class PreviewHandlers {
    /** The page function the router adds to every preview's frames, and the one that cancels a request. */
    static final String QUERY = "jasperPreviewQuery", CANCEL = "jasperPreviewCancel";
    /** The most characters one request may hold: 4 MiB. */
    static final int MESSAGE_LIMIT = 4 << 20;
    private static final String LINK = "jasper-link:";
    private static final Consumer<CefClient> INSTALL = PreviewHandlers::install;
    private static final Function<CefBrowser, String> TOKENS = PreviewHandlers::token;
    private static final System.Logger LOG = System.getLogger(PreviewHandlers.class.getName());

    private PreviewHandlers() { }

    /**
     * Has Chromium install the handlers on its preview client before any preview is made on it, once, however often
     * asked, and answer {@code jasper-preview:} for each preview's browser on its own origin only (plan 9's N6). Call it
     * when a preview first asks for Chromium: this class names Chromium's types.
     */
    static void prepare(Chromium chromium) {
        chromium.onPreviewClient(INSTALL);
        chromium.answerPreviewFor(TOKENS);
    }

    private static void install(CefClient client) {
        client.addRequestHandler(new Requests());
        client.addLifeSpanHandler(new Popups());
        client.addJSDialogHandler(new Dialogs());
        client.addContextMenuHandler(new Menu());
        client.addFocusHandler(new Focus());
        client.addKeyboardHandler(new Keys());
        client.addLoadHandler(new Loads());
        client.addMessageRouter(CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig(QUERY, CANCEL), new Router()));
    }

    /**
     * Whether a navigation of a preview's main frame to {@code url} goes ahead: only the load of its {@code shell}, and
     * only the first, so a second load of the shell, a reload, and anything elsewhere are cancelled.
     *
     * @param url where the frame would go
     * @param shell the preview's shell, {@code jasper-preview://<token>/}
     * @param firstLoad whether no load of the shell has gone ahead yet
     * @return whether it goes ahead
     */
    static boolean navigates(String url, String shell, boolean firstLoad) {
        return firstLoad && shell.equals(url);
    }

    /**
     * The link a navigation to {@code url} activates: {@code jasper-link:<n>}, the address the Editor gives each link,
     * when the page's script did not catch the click.
     *
     * @param url where the page would go
     * @return the link's id, a non-negative decimal written as {@code Integer.toString} writes it; empty for any other
     *     address
     */
    static OptionalInt link(String url) {
        if (url == null || !url.startsWith(LINK)) return OptionalInt.empty();
        String id = url.substring(LINK.length());
        if (id.isEmpty() || id.length() > 10 || id.length() > 1 && id.charAt(0) == '0') return OptionalInt.empty();
        for (int at = 0; at < id.length(); at++) if (id.charAt(at) < '0' || id.charAt(at) > '9') return OptionalInt.empty();
        long value = Long.parseLong(id);
        return value > Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of((int) value);
    }

    /**
     * Whether a router request counts: it comes from a main frame, at an address on the origin of {@code token}, the
     * preview's, and holds at most 4 MiB.
     *
     * @param main whether the frame that sent it is its page's main frame
     * @param frameUrl the frame's address
     * @param token the token of the preview the browser shows; null for a browser that is no preview's
     * @param request what the frame sent
     * @return whether it reaches the preview
     */
    static boolean counts(boolean main, String frameUrl, String token, String request) {
        return main && token != null && request != null && request.length() <= MESSAGE_LIMIT && PreviewScheme.allows(frameUrl, token);
    }

    /** The preview's page {@code browser} shows; null for a browser that is no preview's. */
    private static PreviewPage page(CefBrowser browser) {
        return browser instanceof PageBrowser shown ? shown.previewOwner() : null;
    }

    /** The token of the origin {@code browser}'s preview shows; null for a browser that is no preview's. Chromium's IO thread. */
    private static String token(CefBrowser browser) {
        PreviewPage page = page(browser);
        return page == null ? null : page.token();
    }

    /** Navigations, requests and the renderer's end. */
    private static final class Requests extends CefRequestHandlerAdapter {
        private final CefResourceRequestHandler resources = new Resources();

        @Override public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean userGesture, boolean isRedirect) {
            PreviewPage page = page(browser);
            String url = request == null ? null : request.getURL();
            if (page == null || frame == null || !frame.isMain()) return true;
            if (page.mayLoad(url)) return false;
            link(url).ifPresent(page::linked);
            return true;
        }

        @Override public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String url, boolean userGesture) {
            PreviewPage page = page(browser);
            if (page != null) link(url).ifPresent(page::linked);
            return true;
        }

        @Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request,
                                                                             boolean isNavigation, boolean isDownload, String initiator,
                                                                             BoolRef disableDefaultHandling) {
            return resources;
        }

        @Override public void onRenderProcessTerminated(CefBrowser browser, CefRequestHandler.TerminationStatus status, int errorCode,
                                                        String errorString) {
            PreviewPage page = page(browser);
            if (page != null) page.ended("Chromium's page for the Markdown preview stopped.");
        }
    }

    /** Every request outside the preview's own origin is cancelled, as is any request of a browser that is no preview's. */
    private static final class Resources extends CefResourceRequestHandlerAdapter {
        @Override public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
            String token = token(browser);
            return token == null || request == null || !PreviewScheme.allows(request.getURL(), token);
        }
    }

    /** No popup opens. */
    private static final class Popups extends CefLifeSpanHandlerAdapter {
        @Override public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String url, String name) {
            return true;
        }
    }

    /** No script dialog shows: each is suppressed, and the page leaves without asking. */
    private static final class Dialogs extends CefJSDialogHandlerAdapter {
        @Override public boolean onJSDialog(CefBrowser browser, String origin, CefJSDialogHandler.JSDialogType type, String message,
                                            String defaultPrompt, CefJSDialogCallback callback, BoolRef suppress) {
            suppress.set(true);
            return false;
        }

        @Override public boolean onBeforeUnloadDialog(CefBrowser browser, String message, boolean reload, CefJSDialogCallback callback) {
            callback.Continue(true, "");
            return true;
        }
    }

    /** No right-click menu: its model is emptied, and a menu Chromium would still show is cancelled. */
    private static final class Menu extends CefContextMenuHandlerAdapter {
        @Override public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
            model.clear();
        }

        @Override public boolean runContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model,
                                                CefRunContextMenuCallback callback) {
            callback.cancel();
            return true;
        }
    }

    /** Chromium gives the focus away past the page's last link or before its first: Swing's moves on from the canvas. */
    private static final class Focus extends CefFocusHandlerAdapter {
        @Override public void onTakeFocus(CefBrowser browser, boolean next) {
            PreviewPage page = page(browser);
            if (page != null) page.focusLeft(next);
        }
    }

    /** On macOS, Select All, which Chromium leaves to the Edit menu there. */
    private static final class Keys extends CefKeyboardHandlerAdapter {
        @Override public boolean onKeyEvent(CefBrowser browser, CefKeyboardHandler.CefKeyEvent event) {
            if (page(browser) == null) return false;
            Optional<EditingCommand> command = EditingCommand.of(event.type == CefKeyboardHandler.CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN,
                event.modifiers, event.windows_key_code).filter(EditingCommand.SELECT_ALL::equals);
            return PageEvents.edit(browser, command, CefBrowser::getFocusedFrame);
        }
    }

    /** Each load start's transition is read, which clears what JCEF left on it, and nothing else is done. */
    static final class Loads extends CefLoadHandlerAdapter {
        @Override public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transition) {
            PageEvents.reached(transition);
        }
    }

    /** The router: each request that counts goes to its page, and every request is answered at once. */
    private static final class Router extends CefMessageRouterHandlerAdapter {
        @Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent,
                                         CefQueryCallback callback) {
            try {
                PreviewPage page = page(browser);
                if (page != null && frame != null && counts(frame.isMain(), frame.getURL(), page.token(), request)) page.received(request);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "The Markdown preview could not take its page's request", failure);
            } finally {
                try {
                    callback.success("");
                } catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.WARNING, "The Markdown preview could not answer its page", failure);
                }
            }
            return true;
        }
    }
}

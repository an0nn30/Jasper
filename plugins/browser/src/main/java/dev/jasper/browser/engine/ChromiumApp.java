package dev.jasper.browser.engine;

import com.jetbrains.cef.JCefAppConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.SystemBootstrap;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefAppHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.network.CefCookieManager;
import org.cef.network.CefRequest;

/**
 * The JCEF side of {@link Chromium}: the in-process {@link CefApp}, its pages' client, its previews' client, its
 * in-memory request contexts, the hand-over to its own UI thread and its dispose. Only Chromium's start loads this class,
 * so a plugin start that never shows a page loads no Chromium class.
 */
final class ChromiumApp {
    private final CefApp app;
    // Serves jasper-about: to the global context as its scheme handler factory, and to in-memory contexts' pages through
    // their request handler (F1): the factory is registered on the global context alone.
    private final AboutSchemeFactory about;
    private CefClient client, preview;
    private boolean disposed;

    private ChromiumApp(CefApp app, AboutSchemeFactory about) {
        this.app = app;
        this.about = about;
    }

    /**
     * Starts in-process Chromium as the spike does and returns its app, which then initializes on Chromium's own thread.
     * Runs on Chromium's worker, never the UI thread: the app's pre-initialisation needs the UI thread free.
     *
     * @param dataDir the plugin's data directory
     * @param about the pages {@code jasper-about:} serves
     * @param pageBrowser whether a browser is a page's, whose main-frame requests {@code jasper-about:} answers
     * @param previews the previews' origins {@code jasper-preview:} serves
     * @param previewBrowser whether {@code jasper-preview:} answers a browser's request for an address: a preview's, on
     *     its own origin
     * @param terminated run once Chromium has ended, on the UI thread
     * @param contextInitialized run once Chromium's context has initialized, on Chromium's own UI thread
     */
    static ChromiumApp start(Path dataDir, AboutScheme about, Predicate<CefBrowser> pageBrowser, PreviewScheme previews,
            BiPredicate<CefBrowser, String> previewBrowser, Runnable terminated, Runnable contextInitialized) {
        JCefAppConfig config = JCefAppConfig.getInstance();
        CefApp.setIsRemoteEnabled(false);
        SystemBootstrap.setLoader(config.getLoader());
        CefApp.startup(config.getAppArgs());
        CefSettings settings = config.getCefSettings();
        ChromiumOptions.apply(settings, dataDir);
        String[] args = ChromiumOptions.arguments(List.of(config.getAppArgs())).toArray(String[]::new);
        CefApp.addAppHandler(new Handler(args, terminated, contextInitialized));
        CefApp app = CefApp.getInstance(args, settings, config.getServerExe());
        var pages = new AboutSchemeFactory(about, pageBrowser);
        // JCEF holds the factories until the app has initialized.
        app.registerSchemeHandlerFactory(AboutScheme.NAME, "", pages);
        app.registerSchemeHandlerFactory(PreviewScheme.NAME, "", new PreviewSchemeFactory(previews, previewBrowser));
        return new ChromiumApp(app, pages);
    }

    /**
     * A new request context in memory: CEF's defaults, whose empty cache path keeps its cookies, cache and storage in
     * memory. Chromium's own UI thread, where CEF initializes it at once.
     *
     * @return the context
     * @throws IllegalStateException when CEF makes none
     */
    static CefRequestContext inMemoryContext() {
        CefRequestContext made = CefRequestContext.createContext(null);
        if (made == null) throw new IllegalStateException("Chromium could not make an in-memory request context");
        return made;
    }

    /** Releases Java's hold on {@code context}, which CEF destroys once its last browser has let it go too. */
    static void dispose(CefRequestContext context) {
        context.dispose();
    }

    /**
     * What hands a task to Chromium's own UI thread: the global cookie manager's {@code flushStore}, which has no other
     * effect, calls back there, and answers whether it will. The manager is got once, now: getting it takes {@code CefApp}'s
     * class monitor and its initialization listeners' monitor, which JCEF holds as it runs its listeners, so getting it
     * under {@code ChromiumThread}'s lock, which those listeners take, could deadlock; {@code flushStore} takes neither.
     * Once JCEF has initialized, in its initialization listener.
     *
     * @return the hand-over
     */
    Predicate<Runnable> handOver() {
        CefCookieManager cookies = CefCookieManager.getGlobalManager();
        return task -> cookies != null && cookies.flushStore(task::run);
    }

    /**
     * The handler of {@code browser}'s {@code jasper-about:} request, as the scheme handler factory answers it.
     * Chromium's IO thread.
     */
    CefResourceHandler aboutResource(CefBrowser browser, CefRequest request) {
        return about.handler(browser, request.getResourceType(), request.getURL());
    }

    /** Runs {@code then} once the app has initialized, on Chromium's thread. */
    void onInitialized(Runnable then) {
        app.onInitialization(state -> then.run());
    }

    /**
     * Makes the pages' client, on the UI thread, has the owner's installers set it up, and hands it over, unless the
     * start ended meanwhile.
     */
    void createClient(Chromium owner) {
        if (!owner.mayCreateClient()) return;
        try {
            client = app.createClient();
            owner.install(client);
        } catch (Throwable failure) { // As in the start: an Error too must fail the start, or nobody hears of it.
            owner.clientFailed(failure);
            return;
        }
        owner.clientCreated(client);
    }

    /**
     * Makes the previews' client, a second client of the same app, which none of the pages' handlers reach. UI thread,
     * once, while Chromium runs.
     *
     * @return the client, set up by no installer yet
     */
    CefClient createPreviewClient() {
        preview = app.createClient();
        return preview;
    }

    /**
     * Disposes the previews' client and the pages' client, which closes their every browser, then the app; once, on the
     * UI thread. Chromium ends later, on its own thread.
     */
    void dispose() {
        if (disposed) return;
        disposed = true;
        try {
            if (preview != null) preview.dispose();
        } finally {
            try {
                if (client != null) client.dispose();
            } finally {
                app.dispose();
            }
        }
    }

    /**
     * Registers {@code jasper-about} and {@code jasper-preview}, reports the context's initialization, on Chromium's own UI
     * thread, and reports Chromium's end. JCEF tells it of each state on the UI thread.
     */
    private static final class Handler extends CefAppHandlerAdapter {
        private final Runnable terminated, contextInitialized;

        Handler(String[] args, Runnable terminated, Runnable contextInitialized) {
            super(args);
            this.terminated = terminated;
            this.contextInitialized = contextInitialized;
        }

        @Override public void onContextInitialized() {
            contextInitialized.run();
        }

        @Override public void onRegisterCustomSchemes(CefSchemeRegistrar registrar) {
            AboutSchemeFactory.register(registrar);
            PreviewSchemeFactory.register(registrar);
        }

        @Override public void stateHasChanged(CefApp.CefAppState state) {
            if (state == CefApp.CefAppState.TERMINATED) terminated.run();
        }
    }
}

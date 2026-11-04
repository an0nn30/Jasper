package dev.jasper.browser.engine;

import com.jetbrains.cef.JCefAppConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.SystemBootstrap;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefAppHandlerAdapter;

/**
 * The JCEF side of {@link Chromium}: the in-process {@link CefApp}, its pages' client, its previews' client and its
 * dispose. Only Chromium's start loads this class, so a plugin start that never shows a page loads no Chromium class.
 */
final class ChromiumApp {
    private final CefApp app;
    private CefClient client, preview;
    private boolean disposed;

    private ChromiumApp(CefApp app) { this.app = app; }

    /**
     * Starts in-process Chromium as the spike does and returns its app, which then initializes on Chromium's own thread.
     * Runs on Chromium's worker, never the UI thread: the app's pre-initialisation needs the UI thread free.
     *
     * @param dataDir the plugin's data directory
     * @param about the pages {@code jasper-about:} serves
     * @param pageBrowser whether a browser is a page's, whose main-frame requests {@code jasper-about:} answers
     * @param previews the previews' origins {@code jasper-preview:} serves
     * @param previewBrowser whether a browser is on the preview client, whose requests {@code jasper-preview:} answers
     * @param terminated run once Chromium has ended, on the UI thread
     */
    static ChromiumApp start(Path dataDir, AboutScheme about, Predicate<CefBrowser> pageBrowser, PreviewScheme previews,
            Predicate<CefBrowser> previewBrowser, Runnable terminated) {
        JCefAppConfig config = JCefAppConfig.getInstance();
        CefApp.setIsRemoteEnabled(false);
        SystemBootstrap.setLoader(config.getLoader());
        CefApp.startup(config.getAppArgs());
        CefSettings settings = config.getCefSettings();
        ChromiumOptions.apply(settings, dataDir);
        String[] args = ChromiumOptions.arguments(List.of(config.getAppArgs())).toArray(String[]::new);
        CefApp.addAppHandler(new Handler(args, terminated));
        CefApp app = CefApp.getInstance(args, settings, config.getServerExe());
        // JCEF holds the factories until the app has initialized.
        app.registerSchemeHandlerFactory(AboutScheme.NAME, "", new AboutSchemeFactory(about, pageBrowser));
        app.registerSchemeHandlerFactory(PreviewScheme.NAME, "", new PreviewSchemeFactory(previews, previewBrowser));
        return new ChromiumApp(app);
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
     * Registers {@code jasper-about} and {@code jasper-preview} and reports Chromium's end. JCEF tells it of each state on
     * the UI thread.
     */
    private static final class Handler extends CefAppHandlerAdapter {
        private final Runnable terminated;

        Handler(String[] args, Runnable terminated) {
            super(args);
            this.terminated = terminated;
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

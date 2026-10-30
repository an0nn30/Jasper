package dev.jasper.browser.engine;

import com.jetbrains.cef.JCefAppConfig;
import java.nio.file.Path;
import java.util.List;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.SystemBootstrap;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefAppHandlerAdapter;

/**
 * The JCEF side of {@link Chromium}: the in-process {@link CefApp}, its one client and its dispose. Only Chromium's start
 * loads this class, so a plugin start that never shows a page loads no Chromium class.
 */
final class ChromiumApp {
    private final CefApp app;
    private CefClient client;
    private boolean disposed;

    private ChromiumApp(CefApp app) { this.app = app; }

    /**
     * Starts in-process Chromium as the spike does and returns its app, which then initializes on Chromium's own thread.
     * Runs on Chromium's worker, never the UI thread: the app's pre-initialisation needs the UI thread free.
     *
     * @param dataDir the plugin's data directory
     * @param about the pages {@code jasper-about:} serves
     * @param terminated run once Chromium has ended, on the UI thread
     */
    static ChromiumApp start(Path dataDir, AboutScheme about, Runnable terminated) {
        JCefAppConfig config = JCefAppConfig.getInstance();
        CefApp.setIsRemoteEnabled(false);
        SystemBootstrap.setLoader(config.getLoader());
        CefApp.startup(config.getAppArgs());
        CefSettings settings = config.getCefSettings();
        ChromiumOptions.apply(settings, dataDir);
        String[] args = ChromiumOptions.arguments(List.of(config.getAppArgs())).toArray(String[]::new);
        CefApp.addAppHandler(new Handler(args, terminated));
        CefApp app = CefApp.getInstance(args, settings, config.getServerExe());
        // JCEF holds the factory until the app has initialized.
        app.registerSchemeHandlerFactory(AboutScheme.NAME, "", new AboutSchemeFactory(about));
        return new ChromiumApp(app);
    }

    /** Runs {@code then} once the app has initialized, on Chromium's thread. */
    void onInitialized(Runnable then) {
        app.onInitialization(state -> then.run());
    }

    /**
     * Makes the one client, on the UI thread, has the owner's installers set it up, and hands it over, unless the start
     * ended meanwhile.
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
     * Disposes the client, which closes its every browser, then the app; once, on the UI thread. Chromium ends later, on
     * its own thread.
     */
    void dispose() {
        if (disposed) return;
        disposed = true;
        try {
            if (client != null) client.dispose();
        } finally {
            app.dispose();
        }
    }

    /** Registers {@code jasper-about} and reports Chromium's end. JCEF tells it of each state on the UI thread. */
    private static final class Handler extends CefAppHandlerAdapter {
        private final Runnable terminated;

        Handler(String[] args, Runnable terminated) {
            super(args);
            this.terminated = terminated;
        }

        @Override public void onRegisterCustomSchemes(CefSchemeRegistrar registrar) {
            AboutSchemeFactory.register(registrar);
        }

        @Override public void stateHasChanged(CefApp.CefAppState state) {
            if (state == CefApp.CefAppState.TERMINATED) terminated.run();
        }
    }
}

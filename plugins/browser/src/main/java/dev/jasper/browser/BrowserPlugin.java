package dev.jasper.browser;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The Browser: web pages as Jasper items. Its start makes Chromium's engine, which starts Chromium only when a page
 * first asks for it, so starting the plugin loads no Chromium class. Registers nothing yet.
 */
public class BrowserPlugin implements Plugin {
    private PluginContext context;
    private ExecutorService worker;
    private Chromium chromium;
    private boolean running;

    /** Created by the runtime. */
    public BrowserPlugin() { }

    @Override public void start(PluginContext context) {
        this.context = context;
        // Chromium starts on a platform thread of the plugin's own: its pre-initialisation needs the UI thread free, and
        // it must not hold one of the SDK's virtual threads. The executor makes no thread until a page asks for Chromium.
        worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("jasper-browser-chromium").factory());
        chromium = new Chromium(context.dataDirectory(), worker, new AboutScheme(), this::startFailed);
        running = true;
    }

    /**
     * Jasper quits, or the plugin is turned off. On the UI thread {@link Chromium#close()} only starts Chromium's dispose.
     * The second close runs on the plugin's background executor and waits for Chromium's end: Jasper's quit waits for that
     * work within its own shutdown grace, so quitting holds briefly for Chromium and never hangs on it.
     */
    @Override public void stop() {
        running = false;
        chromium.close();
        try {
            context.background().execute(chromium::close);
        } catch (RejectedExecutionException closed) {
            // Nothing holds the quit for Chromium's end, which comes on its own.
        }
        worker.shutdown();
    }

    /**
     * Chromium cannot serve pages: the reason goes to the plugin's log and, while the plugin runs, to one notification.
     * UI thread.
     */
    void startFailed(Throwable cause) {
        String reason = cause.getMessage() == null || cause.getMessage().isBlank() ? cause.toString() : cause.getMessage();
        context.log().log(System.Logger.Level.WARNING, "The browser could not start: " + reason, cause);
        if (running) context.notifications().group().error("The browser could not start", reason);
    }
}

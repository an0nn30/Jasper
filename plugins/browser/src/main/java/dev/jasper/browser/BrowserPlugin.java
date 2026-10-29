package dev.jasper.browser;

import dev.jasper.browser.dialogs.PageDialogs;
import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.WebMenu;
import dev.jasper.browser.links.PageNavigation;
import dev.jasper.browser.restore.BrowserRestore;
import dev.jasper.browser.routing.BrowserRole;
import dev.jasper.browser.routing.WebAddressItems;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.SplitItemSpec;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.TabPlacement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The Browser: web pages as Jasper items. Its start registers New Browser Tab, New Browser Window and the Browser split
 * item, the globe they show, the Web menu, Settings › Browser, the welcome page, the Browser as Jasper's web browser,
 * the terminal's Open URL in Split items and the restorer of saved browser items, and makes Chromium's engine, which
 * starts Chromium only when a browser item first shows, so starting the plugin, and restoring its items, loads no
 * Chromium class.
 */
public class BrowserPlugin implements Plugin {
    static final String NEW_TAB = "dev.jasper.browser.new_tab", NEW_WINDOW = "dev.jasper.browser.new_window";
    /** The Browser split item: Split Right with Browser and Split Down with Browser. */
    static final SplitItemSpec SPLIT = SplitItemSpec.of("dev.jasper.browser.browser", "Browser").withIcon(BrowserItems.GLOBE);
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
        var about = new AboutScheme();
        chromium = new Chromium(context.dataDirectory(), worker, about, this::startFailed);
        BrowserSettings.Declared settings = BrowserSettings.declare(context.settings());
        // A page is made when its item first shows: the client's handlers are installed then, before any page is made on
        // it, as naming their types loads Chromium's classes.
        Chromium engine = chromium;
        var items = new BrowserItems(context, page -> {
            PageNavigation.prepare(engine);
            PageDialogs.prepare(engine);
            page.start(engine);
        });
        var welcome = new WelcomePage(context.appearance());
        about.register(WelcomePage.NAME, welcome::html);
        about.registerAction(WelcomePage.SETTINGS, () -> { if (running) items.showSettings(); });
        // Spec section 5: New Browser Tab and New Browser Window, in the File menu and the palette, with no default key.
        context.actions().register(ActionSpec.of(NEW_TAB, "New Browser Tab").withIcon(BrowserItems.GLOBE)
                .withKeywords(List.of("browser", "web", "page", "new", "tab")),
            invoked -> items.open(settings.read().startAddress(), TabPlacement.NEW_TAB, Optional.empty(), Optional.of(invoked.window())));
        context.actions().register(ActionSpec.of(NEW_WINDOW, "New Browser Window").withIcon(BrowserItems.GLOBE)
                .withKeywords(List.of("browser", "web", "page", "new", "window")),
            invoked -> items.open(settings.read().startAddress(), TabPlacement.NEW_WINDOW, Optional.empty(), Optional.of(invoked.window())));
        // The host's Split submenus offer Browser Right and Browser Down.
        context.windows().splitItem(SPLIT,
            (shown, placement) -> items.open(settings.read().startAddress(), placement, Optional.empty(), Optional.of(shown)));
        PluginMenu file = context.menus().standard(StandardMenu.FILE);
        file.add(NEW_TAB);
        file.add(NEW_WINDOW);
        WebMenu.register(context, items);
        // Spec section 9: the links Jasper opens come to the Browser first.
        var role = new BrowserRole(context, items, settings);
        context.platform().browser(role);
        WebAddressItems.register(context, role, settings);
        // Spec section 16: a saved item comes back at once, and its page loads the first time it shows.
        context.windows().restorer(BrowserRestore.KIND, request -> {
            items.restore(request.state(), settings.read().startAddress(), request.window());
            return Optional.empty();
        });
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

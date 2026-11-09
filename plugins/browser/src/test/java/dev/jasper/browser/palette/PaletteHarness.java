package dev.jasper.browser.palette;

import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.DataStores;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.browser.store.Stores;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * A fake host with a stand-in Browser: the two palette scopes over stores in memory, registered as the plugin registers
 * them, and either a recording opener or the real {@link PaletteOpener} over the Browser's own items, which the fake host
 * never shows, so no Chromium is needed. The test closes the host and the stores.
 */
final class PaletteHarness {
    static final String ID = "dev.jasper.browser";
    final MemoryStores memory = new MemoryStores();
    final Stores stores = memory.make();
    final List<String> opened = new ArrayList<>();
    final AtomicReference<Optional<Throwable>> unavailable = new AtomicReference<>(Optional.empty());
    final List<Throwable> told = new ArrayList<>();
    final FakePluginHost host;
    final BrowserItems items;
    final UUID window;

    /** Starts the stand-in plugin; with {@code real}, its scopes open through {@link PaletteOpener}. UI thread. */
    PaletteHarness(boolean real) {
        host = new FakePluginHost();
        window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        var context = new AtomicReference<PluginContext>();
        var settings = new AtomicReference<BrowserSettings.Declared>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of(Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(), plugin -> {
            context.set(plugin);
            settings.set(BrowserSettings.declare(plugin.settings()));
            plugin.palette().register(new BookmarksScope(stores.bookmarkIndex(), this::open));
            plugin.palette().register(new VisitedScope(stores.visitIndex(), this::open));
        });
        items = new BrowserItems(context.get(), settings.get()::read, new DataStores(stores, () -> false), page -> { });
        opener = real ? new PaletteOpener(items, unavailable::get, told::add) : null;
    }

    private final PaletteOpener opener;

    /**
     * The window's handle, which an item opened in it knows; that opens one item, and then that is the item the window has
     * focused. UI thread.
     */
    dev.jasper.sdk.terminal.WindowHandle handle() {
        return items.open(URI.create("about:blank"), dev.jasper.sdk.ui.TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()).tab().window();
    }

    private void open(URI address, dev.jasper.sdk.terminal.WindowHandle target) {
        if (opener != null) opener.open(address, target);
        else opened.add(address.toString());
    }

    /** Waits for the stores and the UI thread; not on the UI thread. */
    void settle() throws Exception {
        memory.settle();
    }

    void close() throws Exception {
        SwingUtilities.invokeAndWait(host::close);
        memory.close();
    }

    static void onUiThread(ThrowingRunnable body) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Exception exception) throw exception;
        if (failure.get() instanceof Error error) throw error;
    }

    @FunctionalInterface interface ThrowingRunnable { void run() throws Exception; }
}

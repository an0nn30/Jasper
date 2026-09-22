package dev.jasper.snippets;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.snippets.api.SnippetService;
import dev.jasper.snippets.api.SnippetView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;

/**
 * Saved commands: {@code snippets.toml} in this plugin's data directory, a palette scope over it and
 * {@link SnippetService} for other plugins. Moves the pre-plugin file, {@code <home>/snippets.toml},
 * into place once; the home is the grandparent of the data directory ({@code <home>/plugin-data/<id>}).
 */
public class SnippetsPlugin implements Plugin {
    static final String OPEN = "dev.jasper.snippets.open";
    private SnippetStore store;
    private ExecutorService worker;

    /** Created by the runtime. */
    public SnippetsPlugin() { }

    @Override public void start(PluginContext context) throws Exception {
        Path file = context.dataDirectory().resolve("snippets.toml");
        migrate(context, file);
        // One serial worker of the plugin's own: the SDK executor is unordered, and reads must follow appends.
        worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("jasper-snippets").factory());
        Executor onUi = SwingUtilities::invokeLater;
        store = new SnippetStore(file, context.platform()::openInEditor, worker, onUi);
        context.services().publish(SnippetService.class, new SnippetService() {
            @Override public Optional<SnippetView> byName(String name) { return store.snapshot().byName(name).map(SnippetsPlugin::view); }
            @Override public void append(String name, String command, BiConsumer<Optional<SnippetView>, Optional<String>> done) {
                store.append(name, command, (saved, error) ->
                    done.accept(Optional.ofNullable(saved).map(SnippetsPlugin::view), Optional.ofNullable(error)));
            }
        });
        context.actions().register(ActionSpec.of(OPEN, "Snippets…").withKeywords(java.util.List.of("snippet", "palette")).withDefaultBinding("cmd+j"),
            invoked -> context.palette().open(invoked.window(), SnippetService.SCOPE_ID, Optional.empty(), Optional.empty()));
        context.palette().register(new SnippetsScope(store, context.notices()::error, OPEN));
        context.events().subscribe(AppEvents.CONFIG_RELOADED, event -> store.reload());
        store.reload();
    }

    @Override public void stop() {
        if (store != null) store.close();
        if (worker != null) worker.shutdownNow();
    }

    static SnippetView view(Snippet snippet) { return new SnippetView(snippet.name(), snippet.command(), snippet.keywords()); }

    /** Moves the pre-plugin file into place when the plugin has none yet; both present is left alone and logged. */
    static void migrate(PluginContext context, Path file) {
        Path home = context.dataDirectory().toAbsolutePath().getParent() == null ? null
            : context.dataDirectory().toAbsolutePath().getParent().getParent();
        if (home == null) return;
        Path legacy = home.resolve("snippets.toml");
        if (!Files.isRegularFile(legacy)) return;
        if (Files.exists(file)) {
            context.log().log(System.Logger.Level.WARNING, "Both " + legacy + " and " + file + " exist; using the latter and leaving both");
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.move(legacy, file);
            context.log().log(System.Logger.Level.INFO, "Moved " + legacy + " to " + file);
        } catch (IOException failure) {
            context.log().log(System.Logger.Level.WARNING, "Could not move " + legacy + " to " + file, failure);
        }
    }
}

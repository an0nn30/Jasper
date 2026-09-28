package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the Editor window needs from the plugin.
 *
 * @param context the plugin's context
 * @param ui the UI thread
 * @param askers builds the window's question asker
 * @param waits builds the window's Save All wait
 * @param settings the current settings
 * @param saveVim saves Vim mode to the settings file; completes on any thread
 * @param timers whether the watcher, notice and typing timers run; tests drive them by hand
 */
public record EditorEnvironment(PluginContext context, Executor ui, Function<WindowOwner, Function<Question, String>> askers,
                                Function<WindowOwner, BiFunction<String, CompletableFuture<Boolean>, Boolean>> waits,
                                Supplier<EditorSettings> settings, Function<Boolean, CompletableFuture<Void>> saveVim, boolean timers) {
    /**
     * @param context the plugin's context
     * @param ui the UI thread
     * @param settings the declared settings
     * @return modal dialogs, the nested-loop wait and running timers
     */
    public static EditorEnvironment production(PluginContext context, Executor ui, EditorSettings.Declared settings) {
        return new EditorEnvironment(context, ui, owner -> new DialogAsker(context.windows(), owner),
            owner -> new SaveWait(context.windows(), owner), settings::current, settings::saveVim, true);
    }
}

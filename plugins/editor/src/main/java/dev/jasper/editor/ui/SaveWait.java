package dev.jasper.editor.ui;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.Windows;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import javax.swing.JButton;

/**
 * Waits for Save All inside a closing guard. A modal dialog would block the window's own prompts (conflicts,
 * Save As), since plugin dialogs are document-modal and can only be owned by windows; so this shows a
 * non-modal dialog and pumps events in a nested loop until the saves finish or the user cancels.
 */
public final class SaveWait implements BiFunction<String, CompletableFuture<Boolean>, Boolean> {
    private final Windows windows;
    private final WindowOwner owner;

    /**
     * @param windows the plugin's windows
     * @param owner the Editor window
     */
    public SaveWait(Windows windows, WindowOwner owner) {
        this.windows = Objects.requireNonNull(windows);
        this.owner = Objects.requireNonNull(owner);
    }

    @Override public Boolean apply(String message, CompletableFuture<Boolean> saves) {
        if (saves.isDone()) return succeeded(saves);
        PluginDialog dialog = windows.dialog(new DialogSpec("Saving", owner, false));
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        boolean[] cancelled = {false};
        var cancel = new JButton("Cancel");
        cancel.addActionListener(event -> { cancelled[0] = true; loop.exit(); });
        dialog.onClosed(() -> { if (!saves.isDone()) cancelled[0] = true; loop.exit(); });
        dialog.setContent(Forms.progress(message, cancel));
        saves.whenComplete((saved, error) -> loop.exit());
        dialog.show();
        // Saves complete on the UI thread, so none can finish between this check and entering the loop.
        if (!saves.isDone() && !cancelled[0]) loop.enter();
        boolean result = !cancelled[0] && succeeded(saves);
        dialog.close();
        return result;
    }

    private static boolean succeeded(CompletableFuture<Boolean> saves) {
        return saves.isDone() && !saves.isCompletedExceptionally() && Boolean.TRUE.equals(saves.getNow(false));
    }
}

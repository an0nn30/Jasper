package dev.jasper.vault.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Timer;

/** Clipboard support for deliberate user copies, with a timed clear for secrets. */
public final class SecretClipboard implements AutoCloseable {
    public static final int CLEAR_MILLIS = 30_000;
    private final Consumer<String> put;
    private final Supplier<Optional<String>> read;
    private final Consumer<Runnable> armClear;
    private String pending;
    private long generation;

    public SecretClipboard(Consumer<String> put, Supplier<Optional<String>> read, Consumer<Runnable> armClear) {
        this.put = put; this.read = read; this.armClear = armClear;
    }

    /** Uses the system clipboard and a one-shot Swing timer. */
    public static SecretClipboard system() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        var timer = new Timer(CLEAR_MILLIS, null);
        timer.setRepeats(false);
        return new SecretClipboard(text -> clipboard.setContents(new StringSelection(text), null), () -> {
            try { return Optional.ofNullable((String) clipboard.getData(DataFlavor.stringFlavor)); }
            catch (Exception unavailable) { return Optional.empty(); }
        }, clear -> {
            for (var listener : timer.getActionListeners()) timer.removeActionListener(listener);
            timer.addActionListener(event -> clear.run());
            timer.restart();
        });
    }

    /** Copies a secret and arms its clear callback. */
    public void copySecret(String secret) {
        put.accept(secret);
        pending = secret;
        long copied = ++generation;
        armClear.accept(() -> { if (copied == generation) clearIfUnchanged(); });
    }

    /** Copies ordinary user-visible text without arming a clear. */
    public void copy(String text) { put.accept(text); pending = null; generation++; }

    /** Clears the pending secret only when the clipboard still contains it. */
    public void clearIfUnchanged() {
        if (pending == null) return;
        try { if (read.get().map(pending::equals).orElse(false)) put.accept(""); }
        finally { pending = null; generation++; }
    }

    @Override public void close() { clearIfUnchanged(); }
}

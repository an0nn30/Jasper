package dev.jasper.app.platform;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The one clipboard Jasper and its plugins put text on. A secret copy is cleared later, when the plugin that copied it
 * stops, or at quit, but only while the clipboard still holds it; any later copy through here ends it. UI thread.
 */
public final class HostClipboard {
    /** How long a secret stays on the clipboard. */
    public static final Duration SECRET_LIFETIME = Duration.ofSeconds(30);

    private record Pending(String text, String owner, Runnable cancel) { }

    private final Supplier<Clipboard> clipboard;
    private final BiFunction<Duration, Runnable, Runnable> after;
    private Pending pending;

    /**
     * @param clipboard the clipboard, asked for on every use
     * @param after runs a task once after a delay, returning what cancels it
     */
    public HostClipboard(Supplier<Clipboard> clipboard, BiFunction<Duration, Runnable, Runnable> after) {
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.after = Objects.requireNonNull(after, "after");
    }

    /** The desktop's clipboard, with secrets cleared by a Swing timer. */
    public static HostClipboard system() {
        return new HostClipboard(() -> Toolkit.getDefaultToolkit().getSystemClipboard(), (delay, task) -> {
            var timer = new javax.swing.Timer((int) delay.toMillis(), event -> task.run());
            timer.setRepeats(false);
            timer.start();
            return timer::stop;
        });
    }

    /**
     * Puts text on the clipboard and ends any pending secret.
     *
     * @throws IllegalStateException when another program holds the clipboard; nothing changes
     * @throws HeadlessException when there is no clipboard; nothing changes
     */
    public void copy(String text) {
        put(text);
        end();
    }

    /** As {@link #copy}, then clears the clipboard after {@link #SECRET_LIFETIME}, when {@code owner} stops or at quit. */
    public void copySecret(String text, String owner) {
        Objects.requireNonNull(owner, "owner");
        put(text);
        end();
        Pending[] armed = new Pending[1];
        armed[0] = new Pending(text, owner, after.apply(SECRET_LIFETIME, () -> { if (pending == armed[0]) clear(); }));
        pending = armed[0];
    }

    /** The clipboard's text; empty when it holds none or cannot be read. */
    public Optional<String> text() {
        try {
            Transferable contents = clipboard.get().getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.stringFlavor)) return Optional.empty();
            return Optional.of((String) contents.getTransferData(DataFlavor.stringFlavor));
        } catch (HeadlessException | IllegalStateException | UnsupportedFlavorException | IOException unreadable) {
            return Optional.empty();
        }
    }

    /** Clears the pending secret if {@code owner} copied it. */
    public void ownerStopped(String owner) {
        if (pending != null && pending.owner().equals(owner)) clear();
    }

    /** Clears any pending secret: Jasper is quitting. */
    public void quit() {
        if (pending != null) clear();
    }

    private void clear() {
        Pending secret = pending;
        end();
        try {
            if (text().map(secret.text()::equals).orElse(false)) put("");
        } catch (IllegalStateException | HeadlessException unavailable) {
            // Another program holds the clipboard now; there is nothing Jasper can clear.
        }
    }

    private void end() {
        if (pending == null) return;
        pending.cancel().run();
        pending = null;
    }

    private void put(String text) {
        var selection = new StringSelection(Objects.requireNonNull(text, "text"));
        clipboard.get().setContents(selection, selection);
    }
}

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
 * stops, or at quit, but only while the clipboard still holds it; any later copy through here ends it. A timed clear the
 * clipboard refuses is retried every {@link #CLEAR_RETRY}, up to {@link #CLEAR_RETRIES} times, then logged and dropped;
 * a stop or quit gets one try. UI thread.
 */
public final class HostClipboard {
    /** How long a secret stays on the clipboard. */
    public static final Duration SECRET_LIFETIME = Duration.ofSeconds(30);
    /** How long to wait before retrying a clear the clipboard refused. */
    public static final Duration CLEAR_RETRY = Duration.ofSeconds(1);
    /** How many times a refused clear is retried before it is abandoned. */
    public static final int CLEAR_RETRIES = 5;

    private static final System.Logger LOG = System.getLogger(HostClipboard.class.getName());

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
        arm(text, owner, SECRET_LIFETIME, CLEAR_RETRIES);
    }

    /** The clipboard's text; empty when it holds none or cannot be read. */
    public Optional<String> text() {
        try {
            return read();
        } catch (HeadlessException | IllegalStateException unreadable) {
            return Optional.empty();
        }
    }

    /** Clears the pending secret if {@code owner} copied it; one try, no retry. */
    public void ownerStopped(String owner) {
        if (pending != null && pending.owner().equals(owner)) clear(0);
    }

    /** Clears any pending secret: Jasper is quitting. One try, no retry. */
    public void quit() {
        if (pending != null) clear(0);
    }

    /** One attempt to clear the pending secret; a refused attempt is re-armed while {@code retries} remain. */
    private void clear(int retries) {
        Pending secret = pending;
        secret.cancel().run();
        try {
            if (read().map(secret.text()::equals).orElse(false)) put("");
            pending = null;
        } catch (IllegalStateException | HeadlessException unavailable) {
            if (retries > 0) {
                arm(secret.text(), secret.owner(), CLEAR_RETRY, retries - 1);
            } else {
                pending = null;
                LOG.log(System.Logger.Level.WARNING,
                        "Could not clear a copied secret from the clipboard: " + unavailable.getMessage());
            }
        }
    }

    /** Arms the clear of {@code text} after {@code delay}, replacing the pending secret. */
    private void arm(String text, String owner, Duration delay, int retries) {
        Pending[] armed = new Pending[1];
        armed[0] = new Pending(text, owner, after.apply(delay, () -> { if (pending == armed[0]) clear(retries); }));
        pending = armed[0];
    }

    private void end() {
        if (pending == null) return;
        pending.cancel().run();
        pending = null;
    }

    /** The clipboard's text; empty when it holds no string. Throws when the clipboard is busy or absent. */
    private Optional<String> read() {
        try {
            Transferable contents = clipboard.get().getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.stringFlavor)) return Optional.empty();
            return Optional.of((String) contents.getTransferData(DataFlavor.stringFlavor));
        } catch (UnsupportedFlavorException | IOException unreadable) {
            return Optional.empty();
        }
    }

    private void put(String text) {
        var selection = new StringSelection(Objects.requireNonNull(text, "text"));
        clipboard.get().setContents(selection, selection);
    }
}

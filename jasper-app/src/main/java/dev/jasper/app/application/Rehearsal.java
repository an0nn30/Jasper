package dev.jasper.app.application;

import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.workspace.WindowCallbacks;
import dev.jasper.app.workspace.WindowContent;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.VolatileImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * The resident warm-up's rehearsal window: composed like a real one, fed a canned prompt from memory (no shell, no PTY,
 * no child process), painted once into an image and disposed without ever being shown.
 */
final class Rehearsal {
    private static final System.Logger LOG = System.getLogger(Rehearsal.class.getName());
    /** A title (OSC 2), a coloured prompt, a listing with box-drawing, accented and wide characters, and a prompt. */
    static final String OUTPUT = "\033]2;jasper\007"
        + "\033[1;32mjasper\033[0m \033[34m~\033[0m % ls\r\n"
        + "\033[1;34mDocuments\033[0m  \033[1;34mprojects\033[0m  notes.txt  ── été 中文\r\n"
        + "\033[1;32mjasper\033[0m \033[34m~\033[0m % ";
    /** How long the rehearsal waits for the canned output to reach the screen before it paints anyway. */
    static final Duration FALLBACK = Duration.ofSeconds(2);
    /** A rehearsal window asks nothing of the application. */
    static final WindowCallbacks CALLBACKS = new WindowCallbacks(directory -> { }, () -> { }, window -> { }, window -> { },
        state -> { });

    private Rehearsal() { }

    /** A canned session, and what lets its output play. */
    record Feed(TerminalSession session, Runnable release) { }

    /** The unseen window as the rehearsal uses it: what paints it once, and what disposes it. */
    record Stage(Runnable paint, Runnable close) {
        Stage {
            Objects.requireNonNull(paint, "paint");
            Objects.requireNonNull(close, "close");
        }
    }

    static Feed feed(int columns, int lines) {
        var released = new CountDownLatch(1);
        var ended = new CountDownLatch(1);
        var exited = new CompletableFuture<Integer>();
        InputStream canned = new ByteArrayInputStream(OUTPUT.getBytes(StandardCharsets.UTF_8));
        var output = new InputStream() {
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
            }

            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                try {
                    released.await();
                    int count = canned.read(buffer, offset, length);
                    if (count >= 0) return count;
                    ended.await();
                    return -1;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Rehearsal output interrupted");
                }
            }

            @Override public void close() {
                released.countDown();
                ended.countDown();
            }
        };
        TerminalSession session = TerminalSession.attach(new AttachedConnection(output, OutputStream.nullOutputStream(),
            (width, height) -> { }, exited, () -> { output.close(); exited.complete(0); }), new GridSize(columns, lines), 1_000);
        return new Feed(session, released::countDown);
    }

    /** A launcher whose launch is the canned session; its completion still arrives on the EDT, like a shell's. */
    static ShellLauncher launcher(Feed feed) {
        return new ShellLauncher(Runnable::run, directory -> feed.session(), "shell");
    }

    /**
     * The whole rehearsal: builds the canned session with {@code newFeed}, composes the unseen window around it with
     * {@code compose}, and queues the play, which paints once and closes everything. {@code done} runs exactly once,
     * on the EDT after the play or at once when there was nothing to play: whether building or composing fails with
     * an exception (logged: the first window then pays for what the rehearsal would have), or with an
     * {@link Error}, which propagates only after {@code done} ran. Whatever was built before a failure is closed.
     * {@code compose} closes a window it only half-built. EDT.
     */
    static void run(Supplier<Feed> newFeed, Function<Feed, Stage> compose, Runnable done, Duration fallback) {
        Objects.requireNonNull(done, "done");
        Feed feed = null;
        Stage stage = null;
        boolean handedOff = false;
        try {
            feed = newFeed.get();
            stage = compose.apply(feed);
            Feed canned = feed;
            Stage unseen = stage;
            // Queued behind the launcher's completion, so the pane has adopted the canned session and has a view by then.
            SwingUtilities.invokeLater(() -> play(canned, unseen.paint(), () -> end(unseen, canned, done), fallback));
            handedOff = true;
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING,
                "The warm-up rehearsal could not be built (its canned terminal or its window); the first window will pay for it",
                failure);
        } finally {
            if (!handedOff) end(stage, feed, done);
        }
    }

    /**
     * A rehearsal window shows nothing, ever: what it would tell the user (a launch that failed, a rejected choice) is
     * only logged. The application never connects this window's receivers, so without this a failed canned launch would
     * depend on {@code WindowContent}'s defaults; it must never become a notification or an incident. EDT.
     */
    static void silence(WindowContent content) {
        silence(content, message -> LOG.log(System.Logger.Level.WARNING,
            "The warm-up rehearsal's window reported a problem, which is not shown: " + message));
    }

    static void silence(WindowContent content, Consumer<String> report) {
        content.onError = (title, details) -> report.accept(title + ": " + details);
        content.onInfo = (title, details) -> report.accept(title + ": " + details);
        content.onLaunchFailure = (details, cause) -> report.accept("Could not start the terminal: " + details);
        content.onOpenFile = (file, line) -> { };
        content.fileChooser = folder -> java.util.Optional.empty();
    }

    /**
     * Runs {@code close}. A window whose close failed part way may still have its frame up, so then {@code dispose} runs
     * too, its own failure added to the original as suppressed, and the original propagates. EDT.
     */
    static void closeAndDispose(Runnable close, Runnable dispose) {
        try {
            close.run();
        } catch (RuntimeException | Error failure) {
            try {
                dispose.run();
            } catch (RuntimeException | Error disposing) {
                failure.addSuppressed(disposing);
            }
            throw failure;
        }
    }

    /** Closes what the rehearsal made, best effort, then reports it finished; an Error from closing propagates after that. */
    private static void end(Stage stage, Feed feed, Runnable done) {
        try {
            try {
                if (stage != null) closeQuietly("window", stage.close());
            } finally {
                // The window's pane closes the session it adopted; this is for a session no pane ever adopted.
                if (feed != null) closeQuietly("canned session", feed.session()::close);
            }
        } finally {
            done.run();
        }
    }

    private static void closeQuietly(String what, Runnable close) {
        try {
            close.run();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The warm-up rehearsal could not close its " + what, failure);
        }
    }

    /**
     * Lets the canned output play, paints once it has reached the screen (or after {@code fallback} if it never does),
     * then finishes. One paint and one finish, whatever happens: a failing paint is logged and finishes anyway, and an
     * {@link Error} from it propagates only after {@code finish} ran. EDT.
     */
    static void play(Feed feed, Runnable paint, Runnable finish, Duration fallback) {
        var done = new AtomicBoolean();
        var timer = new Timer((int) fallback.toMillis(), null);
        Runnable once = () -> {
            if (!done.compareAndSet(false, true)) return;
            timer.stop();
            try {
                paint.run();
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "The warm-up rehearsal could not paint; the first window pays for it", failure);
            } finally {
                finish.run();
            }
        };
        timer.addActionListener(event -> once.run());
        timer.setRepeats(false);
        feed.session().addListener(new TerminalSessionListener() {
            @Override public void screenChanged() { SwingUtilities.invokeLater(once); }
        });
        timer.start();
        feed.release().run();
    }

    /** Lays out and paints {@code window} once into an image from its own graphics configuration (Metal on macOS). EDT. */
    static void paint(Window window) {
        window.validate();
        JRootPane root = ((RootPaneContainer) window).getRootPane();
        VolatileImage image = window.getGraphicsConfiguration()
            .createCompatibleVolatileImage(Math.max(1, root.getWidth()), Math.max(1, root.getHeight()));
        try {
            Graphics2D graphics = image.createGraphics();
            try {
                root.paint(graphics);
            } finally {
                graphics.dispose();
            }
        } finally {
            image.flush();
        }
    }
}

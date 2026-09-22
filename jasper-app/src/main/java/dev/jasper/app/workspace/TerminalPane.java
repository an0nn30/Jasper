package dev.jasper.app.workspace;

import dev.jasper.app.config.ShellExitBehavior;
import dev.jasper.app.history.ShellHistoryEntry;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import dev.jasper.terminal.view.TerminalView;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import dev.jasper.app.terminals.PaneSnapshot;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.app.terminals.RemoteLocation;
import dev.jasper.terminal.session.RemoteDirectory;
import java.util.function.BiConsumer;
import javax.swing.*;

/** Exactly one asynchronously launched shell and its retained output. Owned on the EDT. */
public final class TerminalPane extends JPanel implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(TerminalPane.class.getName());
    static final float DEFAULT_FONT_SIZE = 16f;
    static final int PADDING = 4;
    private final UUID id = UUID.randomUUID();
    private final Path launchDirectory;
    private final ShellLauncher launcher;
    private final AtomicBoolean updateQueued = new AtomicBoolean();
    private volatile String shellLabel;
    private TerminalSession session;
    /** EDT snapshots preserve the order of titles, command starts and command ends from the reader. */
    private String reportedTitle = "";
    private String runningCommand = "";
    private String foregroundJob = "";
    private boolean jobQueryPending;
    private final Timer jobTimer = new Timer(500, event -> refreshJob());
    private TerminalView view;
    private FindBar findBar;
    private boolean closed;
    private boolean active;
    private float configuredDim = .3f;
    private ShellExitBehavior onExit = ShellExitBehavior.KEEP_OPEN;
    java.util.function.BooleanSupplier allowLaunchFocus = () -> true;
    Runnable onChanged = () -> {};
    Runnable onFocused = () -> {};
    Runnable onClose = () -> {};
    Consumer<String> onFailure = message -> {};
    Consumer<TerminalView> onReady = terminal -> {};
    Consumer<ShellHistoryEntry> onCommandExecuted = entry -> {};

    /** A command finished: its text, exit status, how long it ran and where, locally or remotely. Delivered on the EDT. */
    interface CommandFinished {
        void accept(String command, java.util.OptionalInt exitStatus, java.time.Duration duration, java.util.Optional<Path> workingDirectory,
                    java.util.Optional<RemoteLocation> remote);
    }

    CommandFinished onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> {};
    /** The shell reported a working directory: a local one, or one that is not on this machine. Delivered on the EDT. */
    BiConsumer<java.util.Optional<Path>, java.util.Optional<RemoteLocation>> onDirectoryChanged = (directory, remote) -> {};
    /** The terminal rang the bell. Delivered on the EDT. */
    Runnable onBell = () -> {};
    /** The session started. Delivered on the EDT. */
    Runnable onStarted = () -> {};
    /** The session ended, with its exit status when known. Delivered on the EDT, before the exit policy runs. */
    Consumer<java.util.OptionalInt> onExited = status -> {};

    /** A command began. Delivered on the EDT. */
    Consumer<String> onCommandStarted = command -> {};
    /** Effective program title, including the command fallback; delivered on the EDT. */
    Consumer<String> onTitleChanged = title -> {};

    /** This pane is gone: anything holding it as a key should let go. Fired once, on the EDT. */
    Runnable onClosed = () -> {};

    /**
     * This pane took keyboard focus. Separate from {@link #onFocused}, which the tab owns to track
     * its active pane; this one is for whoever cares that the user is now looking here.
     */
    Runnable onPaneFocused = () -> {};

    /**
     * This pane stopped being watched — another pane, another tab, another window, or Jasper itself
     * going to the background. Swing reports all four as a focus loss, the last as a temporary one.
     */
    Runnable onPaneBlurred = () -> {};
    private final SessionRequest request;
    private SessionAttempt attempt;
    private final JLabel noticeLabel = new JLabel();
    private final JButton noticePrimary = new JButton();
    private final JButton noticeSecondary = new JButton();
    private final JPanel notice = new JPanel(new BorderLayout(12, 0));
    private Runnable primaryAction = () -> {};
    private Runnable secondaryAction = () -> {};
    private boolean noticeTransient;
    private final Timer droppedTimer = new Timer(4000, event -> { if (noticeTransient) hideNotice(); });
    /** A provided session started connecting, first or again. Delivered on the EDT. */
    Runnable onConnecting = () -> {};
    private final TerminalSessionListener listener = new TerminalSessionListener() {
        @Override public void screenChanged() { queueUpdate(); }

        @Override public void commandStarted(String command) {
            // commandStarted arrives on the reader thread; everything downstream is Swing.
            SwingUtilities.invokeLater(() -> {
                if (closed) return;
                runningCommand = command;
                onCommandStarted.accept(command);
                onTitleChanged.accept(title());
                onChanged.run();
            });
        }
        @Override public void titleChanged(String title) {
            // Do not read session.title() later: another OSC (including the next prompt's title)
            // may already have overwritten it by the time this event reaches Swing.
            SwingUtilities.invokeLater(() -> {
                if (closed) return;
                reportedTitle = title == null ? "" : title;
                onTitleChanged.accept(title());
                onChanged.run();
            });
        }
        @Override public void workingDirectoryChanged(Path directory) {
            queueUpdate();
            SwingUtilities.invokeLater(() -> { if (!closed) onDirectoryChanged.accept(java.util.Optional.ofNullable(directory), java.util.Optional.empty()); });
        }
        @Override public void remoteDirectoryChanged(RemoteDirectory directory) {
            queueUpdate();
            var remote = java.util.Optional.of(new RemoteLocation(directory.host(), directory.path()));
            SwingUtilities.invokeLater(() -> { if (!closed) onDirectoryChanged.accept(java.util.Optional.empty(), remote); });
        }
        @Override public void bell() { SwingUtilities.invokeLater(() -> { if (!closed) onBell.run(); }); }
        @Override public void inputDropped() {
            SwingUtilities.invokeLater(() -> { if (!closed && !notice.isVisible()) showDropped(); });
        }

        @Override public void commandExecuted(String command, java.util.OptionalInt exitStatus,
                java.util.Optional<Path> workingDirectory, java.time.Duration duration) {
            // Read on the reader thread, in step with the tracker, before hopping to the EDT.
            var remote = remoteOf(session);
            try {
                onCommandExecuted.accept(new ShellHistoryEntry(command, java.time.Instant.now().getEpochSecond(),
                    java.util.Set.of(shellLabel), workingDirectory.orElse(null),
                    exitStatus.isPresent() ? exitStatus.getAsInt() : null));
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "History listener failed for a captured command", failure);
            }
            // commandExecuted arrives on the reader thread; everything downstream is Swing.
            SwingUtilities.invokeLater(() -> {
                if (closed) return;
                onCommandFinished.accept(command, exitStatus, duration, workingDirectory, remote);
                runningCommand = "";
                onChanged.run();
            });
        }
    };

    TerminalPane(Path directory, ShellLauncher launcher) { this(directory, launcher, null); }

    /** {@code requestOrNull} makes this a pane whose session somebody else provides; it never starts a local shell. */
    TerminalPane(Path directory, ShellLauncher launcher, SessionRequest requestOrNull) {
        super(new BorderLayout());
        this.launchDirectory = directory;
        this.launcher = launcher;
        this.request = requestOrNull;
        this.shellLabel = requestOrNull == null ? launcher.label() : requestOrNull.title();
        if (requestOrNull != null) reportedTitle = requestOrNull.title();
        setPreferredSize(new Dimension(958, 821));
        add(new JLabel("Starting terminal…", SwingConstants.CENTER));
        setBackground(UIManager.getColor("Panel.background"));
        noticeLabel.putClientProperty("html.disable", Boolean.TRUE);
        var buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        buttons.setOpaque(false);
        buttons.add(noticeSecondary); buttons.add(noticePrimary);
        notice.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        notice.add(noticeLabel, BorderLayout.CENTER);
        notice.add(buttons, BorderLayout.EAST);
        noticePrimary.addActionListener(event -> primaryAction.run());
        noticeSecondary.addActionListener(event -> secondaryAction.run());
        droppedTimer.setRepeats(false);
        setActive(false);
    }

    /**
     * Whether the user is watching this pane right now. The focus owner only exists inside the
     * active window, so this is false whenever Jasper itself is in the background.
     */
    boolean watched() { return view != null && view.isFocusOwner(); }

    @Override public Dimension getMinimumSize() {
        Dimension layout = super.getMinimumSize();
        return new Dimension(Math.max(140, layout.width), Math.max(90, layout.height));
    }

    void start() {
        if (request != null) { connect(); return; }
        shellLabel = launcher.launch(launchDirectory, (created, failure) -> {
            if (closed) { if (created != null) created.close(); return; }
            if (failure != null) {
                LOG.log(System.Logger.Level.ERROR, "Terminal pane launch failed", failure);
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                removeAll(); add(new JLabel("Could not start terminal: " + cause.getMessage()));
                revalidate(); repaint();
                onFailure.accept("Could not start terminal in " + launchDirectory + ":\n" + cause.getMessage());
                return;
            }
            adopt(created);
        });
    }

    /** Shows a running session here: the local shell, a provider's connection, or a reconnect replacing the last one. */
    private void adopt(TerminalSession created) {
        if (findBar != null) findBar.dispose();
        if (session != null) session.removeListener(listener);
        session = created;
        view = new TerminalView(session, applicationOptions());
        findBar = new FindBar(view);
        view.setOnCloseRequest(() -> onClose.run());
        view.addPropertyChangeListener("minimumSize", event -> onChanged.run());
        trackFocus(view);
        trackFocus(findBar);
        view.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { queueUpdate(); }
        });
        session.addListener(listener);
        if (request == null) reportedTitle = session.title();
        refreshJob(); jobTimer.start();
        // Always defer: the process may already have exited before launch delivery.
        // Read the policy on the EDT after onReady has applied the latest configuration.
        created.exitFuture().whenComplete((code, error) -> SwingUtilities.invokeLater(() -> {
            if (closed || session != created) return;
            jobTimer.stop();
            onExited.accept(error == null && code != null ? java.util.OptionalInt.of(code) : java.util.OptionalInt.empty());
            if (request != null) { disconnected(created, code, error); return; }
            if (onExit == ShellExitBehavior.CLOSE
                || (onExit == ShellExitBehavior.CLOSE_ON_SUCCESS && error == null && Integer.valueOf(0).equals(code))) {
                onClose.run();
            } else {
                onChanged.run();
            }
        }));
        removeAll(); add(findBar, BorderLayout.NORTH); add(view, BorderLayout.CENTER);
        if (request != null) { add(notice, BorderLayout.SOUTH); hideNotice(); }
        onReady.accept(view); setActive(active);
        onStarted.run();
        revalidate(); repaint(); onChanged.run();
        // A shell finishing its launch must not steal focus from a newer pane or a find field.
        if (active && isShowing() && allowLaunchFocus.getAsBoolean()) focusTerminal();
    }

    /** One attempt: the first connect and every reconnect share this path. */
    private void connect() {
        if (closed) return;
        ShellLauncher.SessionDefaults defaults = launcher.sessionDefaults();
        var current = new SessionAttempt(id, defaults.columns(), defaults.lines(), request.cleanup(), SwingUtilities::invokeLater);
        attempt = current;
        current.onStatus = text -> { if (!closed && attempt == current) showPending(text); };
        current.onAttached = connection -> {
            // The pane may have closed, or moved on, between the attach and this hop to the EDT.
            if (closed || attempt != current) { connection.close().run(); return; }
            adopt(TerminalSession.attach(connection, new GridSize(defaults.columns(), defaults.lines()), defaults.scrollback()));
        };
        current.onFailed = message -> { if (!closed && attempt == current) showDisconnected(message); };
        showPending("Connecting…");
        onConnecting.run();
        onChanged.run();
        try { request.connector().accept(current); }
        catch (RuntimeException | LinkageError failure) {
            LOG.log(System.Logger.Level.WARNING, "A session provider failed to start connecting", failure);
            current.fail(failure.getMessage());
        }
    }

    private void disconnected(TerminalSession ended, Integer code, Throwable error) {
        // Reconnect is offered only after the previous connection's close has been invoked.
        ended.close();
        if (request.closeOnExit()) { onClose.run(); return; }
        Throwable cause = error;
        while (cause != null && cause.getCause() != null) cause = cause.getCause();
        showDisconnected(error == null ? "Disconnected (exit " + code + ")" : "Disconnected: " + (cause.getMessage() == null ? "the connection failed" : cause.getMessage()));
    }

    private void showPending(String text) {
        showNotice(text, null, () -> {}, "Cancel", this::cancelAttempt);
    }

    private void showDisconnected(String message) {
        showNotice(message, view == null ? "Retry" : "Reconnect", this::connect, "Close", () -> onClose.run());
        onChanged.run();
    }

    private void cancelAttempt() {
        if (attempt != null) attempt.cancel();
        // Nothing was ever shown in a pane whose first attempt is cancelled; a reconnect returns to the banner.
        if (view == null) onClose.run(); else showDisconnected("Connection cancelled");
    }

    private void showDropped() {
        showNotice("Input dropped: the remote side is not accepting input", null, () -> {}, null, () -> {});
        noticeTransient = true;
        droppedTimer.restart();
    }

    private void showNotice(String text, String primary, Runnable onPrimary, String secondary, Runnable onSecondary) {
        noticeTransient = false;
        droppedTimer.stop();
        noticeLabel.setText(text);
        noticePrimary.setVisible(primary != null); if (primary != null) noticePrimary.setText(primary);
        noticeSecondary.setVisible(secondary != null); if (secondary != null) noticeSecondary.setText(secondary);
        primaryAction = onPrimary; secondaryAction = onSecondary;
        if (view == null) { removeAll(); add(notice, BorderLayout.CENTER); }
        notice.setVisible(true);
        revalidate(); repaint();
    }

    private void hideNotice() { noticeTransient = false; droppedTimer.stop(); notice.setVisible(false); revalidate(); repaint(); }

    String noticeText() { return notice.isVisible() && notice.getParent() == this ? noticeLabel.getText() : ""; }
    JButton noticePrimary() { return noticePrimary; }
    JButton noticeSecondary() { return noticeSecondary; }

    private void refreshJob() {
        if (jobQueryPending || !running()) return;
        jobQueryPending = true;
        TerminalSession current = session;
        Thread.ofVirtual().name("jasper-foreground-job").start(() -> {
            String job = current.foregroundJob().orElse("");
            SwingUtilities.invokeLater(() -> {
                jobQueryPending = false;
                if (closed || current != session || !running()) return;
                if (!job.equals(foregroundJob)) { foregroundJob = job; onChanged.run(); }
            });
        });
    }

    private static TerminalOptions applicationOptions() {
        return TerminalOptions.defaults().toBuilder().fontSize(DEFAULT_FONT_SIZE).build();
    }

    private void trackFocus(Component component) {
        component.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { onFocused.run(); onPaneFocused.run(); }

            @Override public void focusLost(FocusEvent event) { onPaneBlurred.run(); }
        });
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) trackFocus(child);
        }
    }

    private void queueUpdate() {
        if (updateQueued.compareAndSet(false, true)) SwingUtilities.invokeLater(() -> {
            updateQueued.set(false);
            if (!closed) onChanged.run();
        });
    }

    UUID id() { return id; }
    public TerminalView view() { return view; }
    FindBar findBar() { return findBar; }
    public TerminalSession session() { return session; }

    private static java.util.Optional<RemoteLocation> remoteOf(TerminalSession session) {
        return session == null ? java.util.Optional.empty()
            : session.remoteDirectory().map(directory -> new RemoteLocation(directory.host(), directory.path()));
    }

    /** Where this pane is, for display: {@code host:path} when the program reports a remote directory. */
    String locationLabel() { return remoteOf(session).map(RemoteLocation::label).orElseGet(() -> directory().toString()); }

    /** What this pane is right now, for the terminal registry. */
    PaneSnapshot snapshot() {
        Optional<String> provider = request == null ? Optional.empty() : Optional.of(request.providerId());
        boolean connecting = attempt != null && attempt.state() == SessionAttempt.State.PENDING;
        if (session == null || connecting) {
            boolean failedBeforeAnySession = request != null && !connecting;
            return new PaneSnapshot(title(), request == null ? Optional.of(launchDirectory) : Optional.empty(), 0, 0, false,
                failedBeforeAnySession ? PaneSnapshot.State.EXITED : PaneSnapshot.State.STARTING, OptionalInt.empty(), provider, Optional.empty(), shellLabel);
        }
        CompletableFuture<Integer> exit = session.exitFuture();
        boolean exited = exit.isDone();
        Integer code = exited && !exit.isCompletedExceptionally() ? exit.getNow(null) : null;
        return new PaneSnapshot(title(), session.workingDirectory(), session.columns(), session.rows(), session.shellIntegrationDetected(),
            exited ? PaneSnapshot.State.EXITED : PaneSnapshot.State.RUNNING, code == null ? OptionalInt.empty() : OptionalInt.of(code), provider, remoteOf(session), shellLabel);
    }

    /** The foreground job, asked off the EDT like the pane's own poll; empty when nothing runs here. */
    CompletableFuture<Optional<String>> queryForegroundJob() {
        TerminalSession current = session;
        if (current == null || !running()) return CompletableFuture.completedFuture(Optional.empty());
        var result = new CompletableFuture<Optional<String>>();
        Thread.ofVirtual().name("jasper-foreground-job").start(() -> {
            try { result.complete(current.foregroundJob()); }
            catch (RuntimeException failure) { result.complete(Optional.empty()); }
        });
        return result;
    }

    /** Raw bytes to the running session; dropped when nothing runs here. */
    void write(byte[] bytes) { if (running()) session.write(bytes); }
    /** Through the view's paste path, so bracketed paste applies; dropped when nothing runs here. */
    void paste(String text) { if (running() && view != null) view.paste(text); }
    Optional<String> selectedText() { return view == null ? Optional.empty() : view.selectedText(); }
    boolean running() { return !closed && session != null && !session.exitFuture().isDone(); }
    boolean shellIntegrationDetected() { return session != null && session.shellIntegrationDetected(); }
    public Path directory() { return session == null ? launchDirectory : session.workingDirectory().orElse(launchDirectory); }
    String title() {
        return TerminalTitle.singleLine(!reportedTitle.isBlank() ? reportedTitle : runningCommand.strip());
    }
    String tabTitle() {
        return TerminalTitle.tab(reportedTitle, directory(), foregroundJob.isBlank() ? shellLabel : foregroundJob);
    }
    String shellLabel() { return shellLabel; }
    void focusTerminal() { if (view != null) view.requestFocusInWindow(); }
    void setActive(boolean selected) {
        active = selected;
        setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        if (view != null) view.setInactiveDim(selected ? 0 : configuredDim);
    }

    /** Only future exit deliveries use this policy; retained output is never closed retroactively. */
    void setShellExitBehavior(ShellExitBehavior behavior) {
        onExit = java.util.Objects.requireNonNull(behavior);
    }

    void setConfiguredDim(float amount) {
        configuredDim = amount;
        setActive(active);
    }

    void applyTheme(dev.jasper.terminal.config.Palette palette) {
        setBackground(palette.background());
        if (view != null) view.setPalette(palette);
        setActive(active);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        jobTimer.stop();
        Runnable closedHook = onClosed;
        onClosed = () -> {};
        closedHook.run();
        if (findBar != null) findBar.dispose();
        if (view != null) {
            view.setShortcutHandler(null); view.setContextMenuHandler(null); view.setOnCloseRequest(() -> {});
        }
        if (attempt != null) attempt.cancel();
        droppedTimer.stop();
        if (session != null) { session.removeListener(listener); session.close(); }
        onChanged = () -> {}; onFocused = () -> {}; onClose = () -> {}; onCommandStarted = command -> {};
        onPaneFocused = () -> {}; onPaneBlurred = () -> {};
        onTitleChanged = title -> {};
        onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> {};
        onDirectoryChanged = (directory, remote) -> {}; onBell = () -> {}; onStarted = () -> {}; onExited = status -> {};
        onConnecting = () -> {};
        allowLaunchFocus = () -> false;
        onReady = terminal -> {}; onFailure = message -> {};
        onCommandExecuted = entry -> {};
        removeAll();
    }
}

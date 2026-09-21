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

    /** A command finished: its text, exit status and how long it ran. Delivered on the EDT. */
    interface CommandFinished {
        void accept(String command, java.util.OptionalInt exitStatus, java.time.Duration duration);
    }

    CommandFinished onCommandFinished = (command, exitStatus, duration) -> {};

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
        @Override public void workingDirectoryChanged(Path directory) { queueUpdate(); }

        @Override public void commandExecuted(String command, java.util.OptionalInt exitStatus,
                java.util.Optional<Path> workingDirectory, java.time.Duration duration) {
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
                onCommandFinished.accept(command, exitStatus, duration);
                runningCommand = "";
                onChanged.run();
            });
        }
    };

    TerminalPane(Path directory, ShellLauncher launcher) {
        super(new BorderLayout());
        this.launchDirectory = directory;
        this.launcher = launcher;
        this.shellLabel = launcher.label();
        setPreferredSize(new Dimension(958, 821));
        add(new JLabel("Starting terminal\u2026", SwingConstants.CENTER));
        setBackground(UIManager.getColor("Panel.background"));
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
            reportedTitle = session.title();
            refreshJob(); jobTimer.start();
            // Always defer: the process may already have exited before launch delivery.
            // Read the policy on the EDT after onReady has applied the latest configuration.
            created.exitFuture().whenComplete((code, error) -> SwingUtilities.invokeLater(() -> {
                if (closed || session != created) return;
                jobTimer.stop();
                if (onExit == ShellExitBehavior.CLOSE
                    || (onExit == ShellExitBehavior.CLOSE_ON_SUCCESS && error == null && Integer.valueOf(0).equals(code))) {
                    onClose.run();
                } else {
                    onChanged.run();
                }
            }));
            removeAll(); add(findBar, BorderLayout.NORTH); add(view, BorderLayout.CENTER);
            onReady.accept(view); setActive(active);
            revalidate(); repaint(); onChanged.run();
            // A shell finishing its launch must not steal focus from a newer pane or a find field.
            if (active && isShowing() && allowLaunchFocus.getAsBoolean()) focusTerminal();
        });
    }

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
        if (session != null) { session.removeListener(listener); session.close(); }
        onChanged = () -> {}; onFocused = () -> {}; onClose = () -> {}; onCommandStarted = command -> {};
        onPaneFocused = () -> {}; onPaneBlurred = () -> {};
        onTitleChanged = title -> {};
        onCommandFinished = (command, exitStatus, duration) -> {};
        allowLaunchFocus = () -> false;
        onReady = terminal -> {}; onFailure = message -> {};
        onCommandExecuted = entry -> {};
        removeAll();
    }
}

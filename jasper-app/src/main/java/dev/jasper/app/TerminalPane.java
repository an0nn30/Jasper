package dev.jasper.app;

import dev.jasper.terminal.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.*;

/** Exactly one asynchronously launched shell and its retained output. Owned on the EDT. */
final class TerminalPane extends JPanel implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(TerminalPane.class.getName());
    static final float DEFAULT_FONT_SIZE = 16f;
    static final int PADDING = 4;
    private final UUID id = UUID.randomUUID();
    private final Path launchDirectory;
    private final ShellLauncher launcher;
    private final AtomicBoolean updateQueued = new AtomicBoolean();
    private volatile String shellLabel;
    private TerminalSession session;
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
    private final TerminalSession.Listener listener = new TerminalSession.Listener() {
        @Override public void screenChanged() { queueUpdate(); }
        @Override public void titleChanged(String title) { queueUpdate(); }
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
            CommandFinished finished = onCommandFinished;
            javax.swing.SwingUtilities.invokeLater(() -> finished.accept(command, exitStatus, duration));
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
            // Always defer: the process may already have exited before launch delivery.
            // Read the policy on the EDT after onReady has applied the latest configuration.
            created.exitFuture().whenComplete((code, error) -> SwingUtilities.invokeLater(() -> {
                if (closed || session != created) return;
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

    private static TerminalOptions applicationOptions() {
        TerminalOptions defaults = TerminalOptions.defaults();
        return new TerminalOptions(defaults.fontFamily(), DEFAULT_FONT_SIZE, defaults.fallbackFonts(), defaults.ligatures(),
            defaults.palette(), defaults.cursorStyle(), defaults.cursorBlink(), defaults.optionAsMeta(),
            defaults.scrollback(), defaults.copyOnSelect());
    }

    private void trackFocus(Component component) {
        component.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { onFocused.run(); }
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
    TerminalView view() { return view; }
    FindBar findBar() { return findBar; }
    TerminalSession session() { return session; }
    boolean running() { return !closed && session != null && !session.exitFuture().isDone(); }
    boolean shellIntegrationDetected() { return session != null && session.shellIntegrationDetected(); }
    Path directory() { return session == null ? launchDirectory : session.workingDirectory().orElse(launchDirectory); }
    String title() { return session == null ? "" : session.title(); }
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

    void applyTheme(dev.jasper.terminal.Palette palette) {
        setBackground(palette.background());
        if (view != null) view.setPalette(palette);
        setActive(active);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (findBar != null) findBar.dispose();
        if (view != null) {
            view.setShortcutHandler(null); view.setContextMenuHandler(null); view.setOnCloseRequest(() -> {});
        }
        if (session != null) { session.removeListener(listener); session.close(); }
        onChanged = () -> {}; onFocused = () -> {}; onClose = () -> {};
        allowLaunchFocus = () -> false;
        onReady = terminal -> {}; onFailure = message -> {};
        onCommandExecuted = entry -> {};
        removeAll();
    }
}

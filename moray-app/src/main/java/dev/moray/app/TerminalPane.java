package dev.moray.app;

import dev.moray.terminal.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.*;

/** Exactly one asynchronously launched shell and its retained output. Owned on the EDT. */
final class TerminalPane extends JPanel implements AutoCloseable {
    private final UUID id = UUID.randomUUID();
    private final Path launchDirectory;
    private final ShellLauncher launcher;
    private final AtomicBoolean updateQueued = new AtomicBoolean();
    private TerminalSession session;
    private TerminalView view;
    private FindBar findBar;
    private boolean closed;
    private boolean active;
    Runnable onChanged = () -> {};
    Runnable onFocused = () -> {};
    Runnable onClose = () -> {};
    Consumer<String> onFailure = message -> {};
    Consumer<TerminalView> onReady = terminal -> {};
    private final TerminalSession.Listener listener = new TerminalSession.Listener() {
        @Override public void screenChanged() { queueUpdate(); }
        @Override public void titleChanged(String title) { queueUpdate(); }
        @Override public void workingDirectoryChanged(Path directory) { queueUpdate(); }
    };

    TerminalPane(Path directory, ShellLauncher launcher) {
        super(new BorderLayout());
        this.launchDirectory = directory;
        this.launcher = launcher;
        setPreferredSize(new Dimension(960, 570));
        add(new JLabel("Starting terminal\u2026", SwingConstants.CENTER));
        setActive(false);
    }

    @Override public Dimension getMinimumSize() {
        Dimension layout = super.getMinimumSize();
        return new Dimension(Math.max(140, layout.width), Math.max(90, layout.height));
    }

    void start() {
        launcher.launch(launchDirectory, (created, failure) -> {
            if (closed) { if (created != null) created.close(); return; }
            if (failure != null) {
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                removeAll(); add(new JLabel("Could not start terminal: " + cause.getMessage()));
                revalidate(); repaint();
                onFailure.accept("Could not start terminal in " + launchDirectory + ":\n" + cause.getMessage());
                return;
            }
            session = created;
            view = new TerminalView(session, TerminalOptions.defaults());
            findBar = new FindBar(view);
            view.setOnCloseRequest(() -> onClose.run());
            view.addPropertyChangeListener("minimumSize", event -> onChanged.run());
            trackFocus(view);
            trackFocus(findBar);
            view.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent event) { queueUpdate(); }
            });
            session.addListener(listener);
            session.exitFuture().whenComplete((code, error) -> queueUpdate());
            removeAll(); add(findBar, BorderLayout.NORTH); add(view, BorderLayout.CENTER);
            onReady.accept(view); setActive(active);
            revalidate(); repaint(); onChanged.run();
            // A shell finishing its launch must not steal focus from a newer pane or a find field.
            if (active && isShowing()) focusTerminal();
        });
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
    Path directory() { return session == null ? launchDirectory : session.workingDirectory().orElse(launchDirectory); }
    String title() { return session == null ? "" : session.title(); }
    String shellLabel() { return launcher.label(); }
    void focusTerminal() { if (view != null) view.requestFocusInWindow(); }
    void setActive(boolean selected) {
        active = selected;
        Color color = selected ? UIManager.getColor("Component.focusColor") : UIManager.getColor("Panel.background");
        setBorder(BorderFactory.createLineBorder(color == null ? Color.GRAY : color, 1));
        if (view != null) view.setInactiveDim(selected ? 0 : 0.3f);
    }

    void applyTheme(BuiltinTheme theme) {
        if (view != null) view.setPalette(theme.palette());
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
        onReady = terminal -> {}; onFailure = message -> {};
        removeAll();
    }
}

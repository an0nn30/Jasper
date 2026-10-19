package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.terminal.view.FileLink;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.swing.KeyStroke;

/**
 * One window's side of the terminal tabs it shows (workspace tabs spec 9.3): the shortcut, context-menu and file-link
 * handlers a pane's view gets, the window its activity and terminal-registry facts report from, the window's theme and
 * opacity, and focus. A tab moving to another window is detached from this host and attached to the next window's; its
 * session is never touched (spec 2.3). A "go to" callback selects its tab through the registry when it runs, so it
 * follows the tab to whichever window holds it then. EDT only.
 */
final class TabHost {
    private final WindowContent owner;

    TabHost(WindowContent owner) { this.owner = Objects.requireNonNull(owner, "owner"); }

    /**
     * Wires a terminal tab to this window; a tab of another kind needs nothing. With {@code opened} its panes are
     * announced as new first; a tab arriving from another window keeps its panes unannounced.
     */
    void attach(Tab tab, boolean opened) {
        if (!(tab.content() instanceof TerminalTab terminal)) return;
        terminal.onChanged = () -> {
            for (TerminalPane pane : terminal.panes()) if (pane.view() == null) pane.applyTheme(owner.theme());
            present(tab, terminal);
        };
        terminal.onEmpty = () -> owner.closeTab(tab);
        terminal.onError = (message, cause) -> owner.onLaunchFailure.accept(message, cause);
        terminal.lastInWindow = () -> owner.tabCount() == 1;
        terminal.configure = pane -> configure(tab, terminal, pane);
        terminal.onPaneCreated = pane -> { announce(tab, pane); wire(tab, terminal, pane); };
        terminal.setBackground(owner.theme().surface());
        terminal.setBackgroundOpacity((float) owner.backgroundOpacity());
        for (TerminalPane pane : terminal.panes()) {
            pane.applyTheme(owner.theme());
            if (opened) announce(tab, pane);
            wire(tab, terminal, pane);
            if (pane.view() != null) { bind(tab, terminal, pane); behave(pane); }
        }
        present(tab, terminal);
    }

    /** Unwires a terminal tab from this window before it closes or moves on. */
    void detach(Tab tab) {
        if (!(tab.content() instanceof TerminalTab terminal)) return;
        terminal.onChanged = () -> { };
        terminal.onEmpty = () -> { };
        terminal.onError = (message, cause) -> { };
        terminal.lastInWindow = () -> false;
        terminal.configure = pane -> { };
        terminal.onPaneCreated = pane -> { };
        for (TerminalPane pane : terminal.panes()) unwire(pane);
    }

    /** A terminal tab's presentation: its title and icon. */
    static void present(Tab tab, TerminalTab terminal) {
        tab.presentation(terminal.title(), "", terminal.icon(), false, "", "");
    }

    /** A pane that has just opened: this window's activity and the terminal registry hear of it. */
    private void announce(Tab tab, TerminalPane pane) {
        owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.OPENED));
        owner.report(new TerminalEvent.PaneOpened(tab.id().value(), pane.id()));
    }

    /** The pane reports its activity and facts to this window from now on. */
    private void wire(Tab tab, TerminalTab terminal, TerminalPane pane) {
        UUID tabId = tab.id().value();
        pane.onCommandStarted = command -> {
            long startedAt = System.nanoTime();
            owner.emit(new WorkspaceActivity.Started(pane.id(), command, () -> System.nanoTime() - startedAt,
                () -> goTo(tab, terminal, pane), pane.watched() && owner.isActiveAndOpen() && tab == owner.selectedTab()));
            owner.report(new TerminalEvent.CommandStarted(pane.id(), command));
        };
        pane.onTitleChanged = title -> {
            owner.emit(new WorkspaceActivity.TitleChanged(pane.id(), title));
            owner.report(new TerminalEvent.TitleChanged(pane.id(), title));
        };
        pane.onClosed = () -> {
            owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.CLOSED));
            owner.report(new TerminalEvent.PaneClosed(tabId, pane.id()));
        };
        pane.onPaneFocused = () -> {
            owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.FOCUSED));
            owner.report(new TerminalEvent.PaneFocused(tabId, pane.id()));
        };
        pane.onPaneBlurred = () -> owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.BLURRED));
        pane.onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> {
            owner.emit(new WorkspaceActivity.Finished(pane.id(), command, exitStatus, duration,
                new WorkspaceActivity.Origin(owner.anyWindowActive.getAsBoolean(), owner.isActiveAndOpen(),
                    tab == owner.selectedTab(), pane.view() != null && pane.view().isFocusOwner()),
                () -> goTo(tab, terminal, pane)));
            owner.report(new TerminalEvent.CommandFinished(pane.id(), command, exitStatus, duration, workingDirectory, remote));
        };
        pane.onDirectoryChanged = (directory, remote) -> owner.report(new TerminalEvent.DirectoryChanged(pane.id(), directory, remote));
        pane.onBell = () -> owner.report(new TerminalEvent.Bell(tabId, pane.id()));
        pane.onStarted = () -> owner.report(new TerminalEvent.SessionStarted(pane.id()));
        pane.onExited = status -> owner.report(new TerminalEvent.SessionExited(pane.id(), status));
        pane.onConnecting = () -> owner.report(new TerminalEvent.SessionConnecting(pane.id()));
    }

    /** "Go to": the tab, wherever it is now, then the pane (workspace plan 3a Rulings 18). */
    private static void goTo(Tab tab, TerminalTab terminal, TerminalPane pane) {
        tab.select();
        terminal.focus(pane);
        pane.focusTerminal();
    }

    /** A pane whose view just became ready: this window's configuration, its handlers and the first-terminal timing. */
    private void configure(Tab tab, TerminalTab terminal, TerminalPane pane) {
        pane.applyTheme(owner.theme());
        ConfigSnapshot configured = owner.configured();
        if (configured == null) pane.view().setFontSize(owner.configuredFontSize());
        else pane.view().applyOptions(configured.viewOptions(owner.configuredFontSize(), owner.theme().palette()));
        behave(pane);
        bind(tab, terminal, pane);
        owner.terminalConfigured(pane);
    }

    /** This window's dimming and shell-exit policy; a moved pane keeps its view's own options. */
    private void behave(TerminalPane pane) {
        ConfigSnapshot configured = owner.configured();
        if (configured == null) return;
        pane.setConfiguredDim(configured.terminal().dimInactivePanes());
        pane.setShellExitBehavior(configured.terminal().onExit());
    }

    /** The view's shortcut, context-menu and file-link handlers, and its launch focus, go to this window. */
    private void bind(Tab tab, TerminalTab terminal, TerminalPane pane) {
        pane.allowLaunchFocus = () -> owner.commandPalette() == null || !owner.commandPalette().blocksActions();
        pane.view().setShortcutHandler(event -> owner.dispatchShortcut(KeyStroke.getKeyStrokeForEvent(event), pane.view()));
        pane.view().setContextMenuHandler(event -> {
            owner.selectTab(tab); terminal.focus(pane); pane.focusTerminal(); owner.update();
            owner.chrome().contextMenu().show(pane.view(), event.getX(), event.getY());
        });
        pane.view().setFileLinkHandler(link -> openFileLink(pane, link));
    }

    private static void unwire(TerminalPane pane) {
        pane.onCommandStarted = command -> { };
        pane.onTitleChanged = title -> { };
        pane.onClosed = () -> { };
        pane.onPaneFocused = () -> { };
        pane.onPaneBlurred = () -> { };
        pane.onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> { };
        pane.onDirectoryChanged = (directory, remote) -> { };
        pane.onBell = () -> { };
        pane.onStarted = () -> { };
        pane.onExited = status -> { };
        pane.onConnecting = () -> { };
        pane.allowLaunchFocus = () -> true;
        if (pane.view() != null) {
            pane.view().setShortcutHandler(null); pane.view().setContextMenuHandler(null); pane.view().setFileLinkHandler(null);
        }
    }

    /**
     * A Cmd-click on a file in {@code pane}: resolved against the pane's local directory and given to the window's
     * {@code onOpenFile}. A provided session, or a shell that reports a directory on another host, opens nothing.
     */
    private void openFileLink(TerminalPane pane, FileLink link) {
        Optional<Path> local = pane.localDirectory();
        if (local.isEmpty()) return;
        FileLinks.resolve(link.path(), local, Path.of(System.getProperty("user.home")),
                System.getProperty("os.name", "").startsWith("Windows"), pane.wsl())
            .ifPresent(file -> owner.onOpenFile.accept(file, link.line()));
    }
}

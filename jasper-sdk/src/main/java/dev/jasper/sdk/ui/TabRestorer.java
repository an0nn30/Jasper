package dev.jasper.sdk.ui;

/**
 * Rebuilds a plugin's saved tabs when Jasper restores its windows (workspace tabs spec 13 and 14): at start, and whenever
 * the main window opens again after it closed. Registered with {@link Windows#restorer} for one restorer kind; called on
 * the UI thread once for each saved tab of that kind, while its window is being restored and before it shows.
 *
 * @since 0.21.0
 */
@FunctionalInterface
public interface TabRestorer {
    /**
     * Opens one saved tab again, before returning, the way the plugin opened it: a content tab with
     * {@link Windows#openTab(ContentSpec, dev.jasper.sdk.WindowOwner, UiView)}, or a provided session with
     * {@code Terminals.openTab}, with the same restore state. The first content tab the plugin opens meanwhile is the saved
     * tab, whatever window or placement it names, and so is the first session it opens in {@code request.window()}; Jasper
     * puts it where it was saved, with the selection it had, and a session's tab connects when it is first shown. Open only
     * that tab: while the restorer runs, the window being restored takes no other, so another content tab opens where an
     * owner-less one would, and another session does not open. Ask the user nothing here (no dialog, sign-in or prompt);
     * a tab that needs one opens waiting, as a session that offers Reconnect does. When the tab
     * cannot come back now for a reason the plugin expects (its file or host is gone), return why: a placeholder in its
     * place says so, with Retry, and nothing is reported as the plugin's failure. Opening nothing without a reason, or
     * throwing, also leaves a placeholder; a throw is the plugin's failure.
     *
     * @param request the window, the saved title and the restore state
     * @return empty once the tab is open again; else why it cannot come back, which its placeholder shows after
     *     "could not restore this tab:"
     */
    java.util.Optional<String> restore(RestoreRequest request);
}

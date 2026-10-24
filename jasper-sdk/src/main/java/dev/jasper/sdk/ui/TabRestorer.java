package dev.jasper.sdk.ui;

/**
 * Rebuilds a plugin's saved tabs when Jasper restores its windows: at start, and whenever
 * the main window opens again after it closed. Registered with {@link Windows#restorer} for one restorer kind; called on
 * the UI thread for a saved tab of that kind:
 * <ul>
 *   <li>once for each saved tab while its window is restored, before that window shows;</li>
 *   <li>again when the user presses Retry on its placeholder, while the window shows;</li>
 *   <li>when the plugin itself opens a tab while no Jasper window is open, which restores the windows first: inside that
 *       call, before the call's tab opens.</li>
 * </ul>
 *
 * @since 0.21.0
 */
@FunctionalInterface
public interface TabRestorer {
    /**
     * Opens one saved tab again, before returning, the way the plugin opened it: a content tab with
     * {@link Windows#openTab(ContentSpec, dev.jasper.sdk.WindowOwner, UiView)}, or a provided session with
     * {@code Terminals.openTab}, with the same restore state. The first content tab the plugin opens meanwhile is the
     * saved tab, whatever window or placement it names, and so is the first session it opens in
     * {@code request.window()}; Jasper puts it where it was saved, with the selection it had, and a session's tab
     * connects when it is first shown. Open only that tab: while the restorer runs, the window being restored takes no
     * other, so another content tab opens where an owner-less one would, and another session does not open. Ask the
     * user nothing here (no dialog, sign-in or prompt); a tab that needs one opens waiting, as a session that offers
     * Reconnect does. It runs on the UI thread, so it must not block (a quick check that a local file exists is fine,
     * but reading it or calling the network is not); a tab that needs either opens waiting. The saved tab stays in the
     * window being restored while the restorer runs, so a move asked of it then does nothing. When the tab cannot come
     * back now for a reason the plugin expects (its file or host is gone), return why instead of opening it: a
     * placeholder in its place says so, with Retry, and nothing is reported as the plugin's failure. Opening nothing
     * without a reason, or throwing before the tab opens, also leaves a placeholder; a throw is the plugin's failure. A
     * restorer that has opened its tab keeps it whatever it then returns or throws, except an {@link Error} other than
     * a {@link LinkageError}: that closes the tab, which comes back as a placeholder, and is the plugin's failure as a
     * throw is, while the other saved tabs and windows come back. A {@link VirtualMachineError} (such as
     * {@link OutOfMemoryError} or {@link StackOverflowError}) is not contained: the tab it opened is closed and the
     * error propagates, so the restore of that window fails. Jasper then keeps the saved windows aside and, for the
     * main window, opens a new one in its place.
     *
     * @param request the window, the saved title and the restore state
     * @return empty once the tab is open again; else why it cannot come back, which its placeholder shows after
     *     "could not restore this tab:"
     */
    java.util.Optional<String> restore(RestoreRequest request);
}

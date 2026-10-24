package dev.jasper.sdk.ui;

import dev.jasper.sdk.RestoreState;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Objects;

/**
 * One saved tab a {@link TabRestorer} opens again.
 *
 * @param window the Jasper window being restored: the owner of a content tab, or the window of a session's tab
 * @param title the tab's title when it was saved
 * @param state the restore state the tab had when it was saved
 * @since 0.21.0
 */
public record RestoreRequest(WindowHandle window, String title, RestoreState state) {
    /** Requires every value. */
    public RestoreRequest {
        Objects.requireNonNull(window, "window"); Objects.requireNonNull(title, "title"); Objects.requireNonNull(state, "state");
    }
}

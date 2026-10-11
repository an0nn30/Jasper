package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.util.function.BooleanSupplier;

/** A plugin's own top-level window; it can own dialogs. */
public interface PluginWindow extends WindowSurface, WindowOwner {
    /**
     * Installs this plugin's owned trailing title actions, or clears them. The view must be an INLINE Stack
     * containing only Buttons and MenuButtons. Admission and later updates are checked before mutation.
     * A view cannot occupy both body and title slots. Replacement or clearing closes the accepted old view;
     * rejection leaves both the previous slot and the caller's unaccepted view intact. Reinstalling the same view
     * does nothing. Platforms without a unified title bar place these actions in a toolbar below native decorations.
     *
     * @param actions non-null optional action view
     */
    void setTitleActions(java.util.Optional<UiView> actions);

    /**
     * Adds a handler for the window's Close Window command: File → Close Window and its shortcut, Cmd+W on
     * macOS and Ctrl+W elsewhere. Handlers run on the UI thread, newest first. The first that returns true has
     * handled the command, for example by closing a tab, and the window stays open. When none returns true,
     * the window closes through its {@link #onClosing} guards, as if the user had clicked its close button. The
     * close button does not consult these handlers. A handler that throws has not handled the command.
     *
     * @param handler returns whether the content handled the command
     * @return the registration
     * @since 0.8.4
     */
    Subscription onCloseCommand(BooleanSupplier handler);
}

package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;

/** A registered action. Closing it removes the action and every placement of it. Mutators are UI-thread only. */
public interface PluginAction extends Subscription {
    /**
     * Enables or disables the action everywhere it appears. A disabled action's shortcut is still consumed.
     *
     * @param enabled whether the action can run
     */
    void setEnabled(boolean enabled);

    /**
     * Changes the title everywhere the action appears.
     *
     * @param title non-blank title
     */
    void setTitle(String title);
}

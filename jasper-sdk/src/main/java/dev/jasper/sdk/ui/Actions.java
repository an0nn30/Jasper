package dev.jasper.sdk.ui;

import java.util.function.Consumer;

/** Registers actions. Every registered action appears in each window's command palette and can be bound to a key. */
public interface Actions {
    /**
     * Registers an action on the UI thread.
     *
     * @param spec what the action is
     * @param handler runs on the UI thread each time the action is invoked; failures are contained
     * @return the registration
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot, or is already registered
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    PluginAction register(ActionSpec spec, Consumer<ActionContext> handler);
}

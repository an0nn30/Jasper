package dev.jasper.sdk.testing.contract;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Adapts one plugin runtime to {@link PluginContractTest}. Implemented by the fake and by the application. */
public interface ContractHarness extends AutoCloseable {
    /**
     * Starts one plugin on the UI thread and waits. A plugin whose hard requirement is not active is
     * skipped without its {@code start} being called. A failing {@code start} is contained.
     */
    void start(PluginInfo info, Set<String> requires, Set<String> optional, Plugin plugin);

    /** Whether the plugin started successfully and has not been stopped. */
    boolean active(String pluginId);

    /** Runs the action on the UI thread and waits; assertion errors propagate to the caller. */
    void ui(Runnable action);

    /** Returns once every event queued so far, and every event those deliveries queued, has been delivered. */
    void flush();

    /** Publishes as the application. */
    <T> void publishApp(Topic<T> topic, T payload);

    /** Every activity event delivered so far, in delivery order, as the application's own observer sees them. */
    List<ActivityEvent> activityLog();

    /** Registered actions as {@code id|title|enabled}, in registration order. */
    List<String> actions();

    /** Invokes an action as a window would; true when an enabled action ran. */
    boolean invoke(String actionId, UUID windowId, UUID paneIdOrNull);

    /** The toolbar's plugin section: {@code button:id} or {@code menu:title:id,id}; closed actions omitted. */
    List<String> toolbar();

    /**
     * One menu target ({@code FILE}, {@code EDIT}, {@code VIEW}, {@code PANE}, {@code TAB}, {@code context},
     * {@code top:<menuId>}): {@code item:id}, {@code ---}, {@code submenu:title}, children indented two
     * spaces, sections separated by {@code ===}; items of closed actions omitted.
     */
    List<String> menu(String target);

    /** Visible status items as {@code id|side|text|tooltip|actionId}, ascending priority. */
    List<String> status();

    /** Registered panels as {@code id|title|LEFT|RIGHT|BOTTOM} (the default anchor), registration order. */
    List<String> panels();

    /** UI thread: invokes the panel's factory as a window showing it would; null when there is no such panel or the factory failed. */
    javax.swing.JComponent openPanel(String panelId, java.util.UUID windowId);

    /** Rail buttons as action ids in placement order; buttons of closed actions omitted. */
    List<String> rail();

    /** Open plugin windows and dialogs as {@code id|title|shown} in creation order; a dialog's id is {@code dialog}. */
    List<String> windows();

    /** UI thread: closes the first open window with this id as the user would; false when vetoed or absent. */
    boolean requestClose(String windowId);

    /** UI thread: adds a terminal window, which announces itself on the terminal topics. */
    java.util.UUID addTerminalWindow();

    /** UI thread: adds and selects a tab in an open window. */
    java.util.UUID addTerminalTab(java.util.UUID windowId, String title);

    /** UI thread: adds a running local pane, 80 by 24, with shell integration; a tab's first pane is its focused pane. */
    java.util.UUID addTerminalPane(java.util.UUID tabId, String title, java.nio.file.Path directory);

    /** UI thread: the user turned to this window. */
    void activateTerminalWindow(java.util.UUID windowId);

    /** UI thread: the user focused this pane. */
    void focusTerminalPane(java.util.UUID paneId);

    /** UI thread: closes a pane; the last pane takes its tab with it, and the last tab its window. */
    void closeTerminalPane(java.util.UUID paneId);

    /** UI thread: sets the text selected in a pane. */
    void selectInPane(java.util.UUID paneId, String text);

    /** What plugins sent to a pane, in order: {@code write:<the bytes as UTF-8 text>} or {@code paste:<text>}. */
    List<String> sentToPane(java.util.UUID paneId);

    /** What plugins asked to open: {@code tab|<window id>|<directory or ->} or {@code split|<pane id>|<RIGHT or DOWN>|<directory or ->}. */
    List<String> openRequests();

    /** UI thread: publishes that a command finished in a pane after 1.5 seconds, in the pane's directory. */
    void finishCommand(java.util.UUID paneId, String command, int exitStatus);

    /** Changes the look and announces it. */
    void setVariant(Variant variant);

    /** Stops every active plugin in reverse start order. */
    void stopAll();

    @Override void close();
}

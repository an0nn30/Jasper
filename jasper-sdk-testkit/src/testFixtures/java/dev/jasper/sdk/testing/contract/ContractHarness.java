package dev.jasper.sdk.testing.contract;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ConnectionEvent;
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

    /** Every connection event delivered so far, in delivery order, as the application's own observer sees them. */
    List<ConnectionEvent> connectionLog();

    /** Registered actions as {@code id|title|enabled}, in registration order. */
    List<String> actions();

    /** Invokes an action as a window would; true when an enabled action ran. */
    boolean invoke(String actionId, UUID windowId, UUID paneIdOrNull);

    /** The toolbar's plugin section: {@code button:id}, {@code menu:title:id,id} or {@code view}; closed actions omitted. */
    List<String> toolbar();

    /**
     * UI thread: builds a window's toolbar views as the window does when it opens, once per window (a second call reports
     * the same views), and reports each built view as its root's id, in toolbar order; a view whose factory threw or whose
     * tree the toolbar does not admit is left out.
     */
    List<String> openToolbarViews(java.util.UUID windowId);

    /** UI thread: closes a window's toolbar views, as the window does when it closes. */
    void closeToolbarViews(java.util.UUID windowId);

    /** UI thread: clicks the button with this node id in one of a window's open toolbar views, as the user would. */
    void clickToolbarViewButton(java.util.UUID windowId, String buttonId);

    /**
     * One menu target ({@code FILE}, {@code EDIT}, {@code VIEW}, {@code TAB}, {@code TOOLS},
     * {@code SESSION}, {@code TRANSFER}, {@code context},
     * {@code top:<menuId>}): {@code item:id}, {@code ---}, {@code submenu:title}, children indented two
     * spaces, sections separated by {@code ===}; items of closed actions omitted.
     */
    List<String> menu(String target);

    /** Visible status items as {@code id|side|text|tooltip|actionId}, ascending priority. */
    List<String> status();

    /**
     * Registered panels as {@code id|title|LEFT|RIGHT|BOTTOM} (the default anchor, with {@code +split} when the panel
     * starts in its side's secondary half), followed by {@code |actionId} when the panel names its own toggle action;
     * registration order.
     */
    List<String> panels();

    /**
     * UI thread: invokes the panel's factory as the main window showing it would, or returns the panel built there;
     * null when no main window is open, there is no such panel or the factory failed.
     */
    javax.swing.JComponent openPanel(String panelId);

    /**
     * UI thread: the header metadata the open panel published through {@code PanelHost.setHeader}, as
     * {@code detail|active|label;label} with {@code -} for no actions; {@code |false|-} when it published none or the
     * panel is not open.
     */
    String panelHeader(String panelId);

    /** UI thread: activates the labelled header action of the open panel as its header would; false when there is none. */
    boolean runPanelHeaderAction(String panelId, String label);

    /** UI thread: discards the open panel as the main window would when it closes or the registration goes away. */
    void closePanel(String panelId);
    /**
     * UI thread: the theme slot the host paints a kit view of this runtime from, {@code CHROME} or {@code WORKSPACE}. A view
     * mounted as a workspace content tab ({@code Windows.openTab}) takes {@code WORKSPACE}, as terminals and editors do;
     * windows, dialogs and panels keep {@code CHROME}. Plan 2 Task 9-kit (host-only; no SDK member).
     */
    String viewRoute(dev.jasper.sdk.ui.UiView view);

    /** Rail buttons as action ids in placement order; buttons of closed actions omitted. */
    List<String> rail();

    /** Open plugin dialogs and overlays as {@code id|title|shown} in creation order; the id is {@code dialog} or {@code overlay}. */
    List<String> windows();

    /** UI thread: the hue name of the square an open plugin dialog with this title draws before its title, or {@code -} when there is none. */
    String dialogSwatch(String pluginId, String title);

    /** UI thread: closes the first open dialog ({@code dialog}) or overlay ({@code overlay}) as the user would; false when vetoed or absent. */
    boolean requestClose(String windowId);

    /** UI thread: asks every plugin's content tabs' closing guard, then every quit guard, as quitting Jasper does, closing nothing; true when the quit may go ahead. */
    boolean requestQuit();

    /**
     * UI thread: adds a Jasper window, which announces itself on the terminal topics: the main window when none is open,
     * which stays open when its last tab leaves, else a child window, which closes with its last tab.
     */
    java.util.UUID addTerminalWindow();

    /** UI thread: adds and selects a terminal tab in an open window; {@link #addTerminalPane} gives it its one pane. */
    java.util.UUID addTerminalTab(java.util.UUID windowId, String title);

    /** UI thread: adds a terminal tab's one pane: running, local, 80 by 24, with shell integration. */
    java.util.UUID addTerminalPane(java.util.UUID tabId, String title, java.nio.file.Path directory);

    /**
     * UI thread: the local pane waits as a restored terminal tab not yet shown does (workspace tabs spec 13): not started,
     * with no grid, dropping what is sent to it; it starts on its tab's next selection or focus.
     */
    void holdTerminalPane(java.util.UUID paneId);

    /** UI thread: the user turned to this window. */
    void activateTerminalWindow(java.util.UUID windowId);

    /** UI thread: the user focused this pane, which selects its tab. */
    void focusTerminalPane(java.util.UUID paneId);

    /** UI thread: closes a pane with its tab; a child window's last tab takes the window with it. */
    void closeTerminalPane(java.util.UUID paneId);

    /** UI thread: adds and selects a content tab, as the application or another plugin shows a document; it has no pane. */
    java.util.UUID addContentTab(java.util.UUID windowId, String title);

    /**
     * UI thread: a window's tabs as its strip shows them (tabs and panes spec 4), each tab's items pane by pane, by the
     * ids {@code TabHandle.id()} reports; empty for a window that is not open.
     */
    List<List<java.util.UUID>> windowTabs(java.util.UUID windowId);

    /** UI thread: moves a tab the test added to another open window, where it is selected; a child window left with no tab closes. */
    void moveTab(java.util.UUID tabId, java.util.UUID windowId);

    /**
     * UI thread: restores one saved tab through its plugin's restorer, as Jasper does when it restores its windows (workspace
     * tabs spec 13).
     *
     * @return the tab the restorer opened in the window, whatever it then returned or threw; empty when the plugin has no
     *     restorer for the kind, or its restorer opened nothing before it returned or threw. An {@link Error} other than a
     *     {@link LinkageError} closes the tab, and gives empty; a {@link VirtualMachineError} is thrown on.
     */
    java.util.Optional<java.util.UUID> restoreTab(String pluginId, java.util.UUID windowId, String title, dev.jasper.sdk.RestoreState state);

    /** UI thread: the restore state a tab gives, a content tab's or a provided session's; empty when it does not come back. */
    java.util.Optional<dev.jasper.sdk.RestoreState> restoreState(java.util.UUID tabId);

    /** UI thread: closes a tab the test added, of either kind, its pane with it; a child window's last tab takes the window with it. */
    void closeTab(java.util.UUID tabId);

    /** UI thread: sets the text selected in a pane. */
    void selectInPane(java.util.UUID paneId, String text);

    /** What plugins sent to a pane, in order: {@code write:<the bytes as UTF-8 text>} or {@code paste:<text>}. */
    List<String> sentToPane(java.util.UUID paneId);

    /**
     * What plugins asked to open: {@code tab|<window id>|<directory or ->} or {@code split|<pane id>|<RIGHT or DOWN>|<directory or ->},
     * each followed by {@code |<profile id>} when the request named a shell profile, then by
     * {@code |run:<the command's words joined by spaces>} when it runs a program; or
     * {@code beside|<content tab id>|<RIGHT or DOWN>}, a content tab's Terminal Right or Terminal Down.
     */
    List<String> openRequests();

    /** UI thread: replaces the shell profiles the application detected and runs every plugin's change listener. */
    void setShellProfiles(List<dev.jasper.sdk.terminal.ShellProfileInfo> profiles);

    /** UI thread: publishes that a command finished in a pane after 1.5 seconds, in the pane's directory. */
    void finishCommand(java.util.UUID paneId, String command, int exitStatus);

    /** A provided session's state: {@code CONNECTING|<status>}, {@code RUNNING|}, {@code EXITED|<how it ended>}, or {@code CLOSED|} for a pane that is gone. */
    String sessionState(java.util.UUID paneId);

    /** UI thread: what the pane's shell reports as its working directory; an empty host is a local report. */
    void reportDirectory(java.util.UUID paneId, String hostOrEmpty, String path);

    /** UI thread: the user pressed Cancel. A pane that never showed a session closes; otherwise it returns to the disconnected state. */
    void cancelSession(java.util.UUID paneId);

    /** UI thread: the user pressed Reconnect or Retry on a session that ended. */
    void reconnectSession(java.util.UUID paneId);

    /** UI thread: writes to the attached connection's input and flushes. */
    void typeIntoSession(java.util.UUID paneId, String text);

    /** UI thread: what the attached connection's output has available, without blocking. */
    String sessionOutput(java.util.UUID paneId);

    /** Changes the look and announces it. */
    void setVariant(Variant variant);

    /** Sets the presentation every plugin reads; called before any plugin starts.
     * @param presentation the look the runtime runs with
     */
    void setPresentation(dev.jasper.sdk.ui.Presentation presentation);

    /** Publishes saved terminal typography; its change handlers have run once {@link #flush()} returns.
     * @param typography the saved terminal typography
     */
    void setTerminalTypography(dev.jasper.sdk.ui.TerminalTypography typography);

    /** Sets independently chosen workspace values.
     * @param variant workspace variant
     * @param palette workspace controls
     * @param editor document colors
     * @param syntax syntax styles
     */
    void setWorkspace(Variant variant, dev.jasper.sdk.ui.WorkspacePalette palette,
        dev.jasper.sdk.ui.EditorPalette editor, dev.jasper.sdk.ui.SyntaxPalette syntax);

    /** Sets the opacity every plugin component reports and announces it as a workspace change.
     * @param opacity from 0.1 to 1
     */
    void setWorkspaceOpacity(double opacity);

    /** Stops every active plugin in reverse start order. */
    void stopAll();

    /**
     * Sets a raw value in a plugin's settings file as a user editing it would, then returns once the plugin has seen
     * the file again. Raw values are what TOML reads: String, Long, Double, Boolean or List of String.
     */
    void setSetting(String pluginId, String key, Object rawValue);

    /** The raw value a plugin's settings file holds for a dotted key. */
    java.util.Optional<Object> savedSetting(String pluginId, String key);

    /** The problems reported for a plugin's settings file and not yet cleared, as {@code key: message}. */
    List<String> settingsReports(String pluginId);

    /**
     * Gives a plugin's setting a look default, as a kit's {@code [defaults."<plugin id>"]} table would; called before
     * any plugin starts. Raw values are what TOML reads: String, Long, Double, Boolean or List of String.
     */
    void setKitDefault(String pluginId, String key, Object rawValue);

    /**
     * The look defaults a plugin could not use, in the order reported: a value its declared kind rejects, or a key it
     * never declares. Each line starts with the plugin's id and a colon.
     */
    List<String> lookDefaultProblems(String pluginId);

    @Override void close();

        /** Contributed scopes as {@code id|label|verbIds}, in registration order. */
        List<String> scopes();
        /**
         * A registered scope's row presentation as {@code HUE|itemNoun|ROLE,ROLE}: its hue (NONE taken as the plugin's
         * icon hue, NEUTRAL without one), its item noun (its label when it names none) and each verb's role, in order.
         */
        String scopePresentation(String scopeId);
        /** Registered file sources as {@code id|heading}, in registration order. */
        List<String> fileSources();
        /** Searches a scope from a window and optional pane, as {@code rowId|title|enabled}; later row lookups use this answer. */
        List<String> searchScope(String scopeId, String query, java.util.UUID windowId, java.util.UUID paneIdOrNull);
        boolean availableInScope(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull);
        /** The step's title, or empty when the verb runs at once. */
        java.util.Optional<String> stepInScope(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull);
        /** {@code done}, {@code error:<message>} or {@code reopen:<scopeId>:<rowId or ->:<query or ->}. */
        String completeStep(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull, java.util.Map<String, String> values);
        void executeInScope(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull);
        /** Every palette open request as {@code windowId scopeId query rowId}, {@code -} for an absent value. */
        List<String> paletteOpens();
        /**
         * Every notification plugins posted, oldest first, as {@code group|SEVERITY|title|content|labels|cause|window|state}:
         * the plugin's action labels joined with {@code ;}, or {@code -}; {@code cause} when it carried one (the application
         * then offers Show more…), or {@code -}; the target window's id, or {@code -}; and {@code live} or {@code expired}.
         */
        List<String> notifications();
        /** Every notification group declared for plugins, in declaration order, as {@code id|name|MODE}. */
        List<String> notificationGroups();
        /** UI thread: clicks the labelled link of the newest notification with this title; false when there is none or it expired. */
        boolean runNotificationAction(String title, String label);
        /** UI thread: clicks the body of the newest notification with this title; false when there is none or it expired. */
        boolean clickNotification(String title);
        List<java.nio.file.Path> openedInEditor();
        /** Every web address a plugin asked to open in the browser, in request order. */
        List<java.net.URI> browsed();
        /** What the clipboard holds now; empty when it holds no text. */
        java.util.Optional<String> clipboard();
        /** As if the user copied this text in another program. */
        void userCopies(String text);
        /** Whether clipboard writes fail, as while another program holds the clipboard. */
        void clipboardBusy(boolean busy);
        /** Runs the pending secret's clear now, as its 30 seconds would. */
        void expireClipboardSecret();

        /** Declares a subcommand as the plugin's {@code [[subcommands]]} would; before that plugin starts. */
        void declareSubcommand(String pluginId, String name, String summary);
        /**
         * Calls {@code jasper name arguments...} as if run in the pane {@code paneOrNull} (null: outside Jasper); returns
         * once the handler has run.
         */
        ContractCall call(String name, List<String> arguments, java.nio.file.Path workingDirectory, java.util.UUID paneOrNull);
        /**
         * What plugins' {@code Terminals.environment} contributors add to a new local shell, given Jasper's own environment;
         * asked on the UI thread.
         */
        java.util.Map<String, String> shellVariables(java.util.Map<String, String> jasperEnvironment);
        /** The values plugins' tab name pieces hold for a tab, by piece name; asked on the UI thread. */
        java.util.Map<String, String> tabNamePieces(java.util.UUID tabId);
        /** Marks a plugin as one that ships with Jasper, whose editor replaces a non-bundled plugin's; before that plugin starts. */
        void declareBundled(String pluginId);
        /**
         * Opens a file as Jasper's own opens do, with the editor a plugin provides; false when none does, or it declined or
         * threw. Asked on the UI thread.
         */
        boolean edit(java.nio.file.Path file, java.util.OptionalInt line, java.util.Optional<java.util.UUID> windowId);
        /** Sets the {@code jasper} command {@code CommandLine.client()} names, as the application names its installed or built one. */
        void commandClient(java.nio.file.Path client);
}

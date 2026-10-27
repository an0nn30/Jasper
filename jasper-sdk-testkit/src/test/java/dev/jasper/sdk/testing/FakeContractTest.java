package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.testing.contract.ContractCall;
import dev.jasper.sdk.testing.contract.ContractHarness;
import dev.jasper.sdk.testing.contract.PluginContractTest;
import java.util.List;
import java.util.Set;

/** The fake must behave like the application's runtime. */
class FakeContractTest extends PluginContractTest {
    @Override protected ContractHarness newHarness() {
        var host = new FakePluginHost();
        return new ContractHarness() {
            @Override public void setSetting(String pluginId, String key, Object rawValue) { host.setSetting(pluginId, key, rawValue); }
            @Override public java.util.Optional<Object> savedSetting(String pluginId, String key) {
                return java.util.Optional.ofNullable(host.savedSettings(pluginId).get(key));
            }
            @Override public List<String> settingsReports(String pluginId) { return host.settingsReports(pluginId); }
            @Override public void setKitDefault(String pluginId, String key, Object rawValue) { host.setKitDefault(pluginId, key, rawValue); }
            @Override public List<String> lookDefaultProblems(String pluginId) { return host.lookDefaultProblems(pluginId); }
            @Override public void start(PluginInfo info, Set<String> requires, Set<String> optional, Plugin plugin) {
                ui(() -> host.start(info, requires, optional, plugin));
            }
            @Override public boolean active(String pluginId) { return host.active(pluginId); }
            @Override public void setWorkspace(dev.jasper.sdk.Variant variant, dev.jasper.sdk.ui.WorkspacePalette palette,
                    dev.jasper.sdk.ui.EditorPalette editor, dev.jasper.sdk.ui.SyntaxPalette syntax) {
                host.setWorkspace(variant, palette, editor, syntax);
            }
            @Override public void setWorkspaceOpacity(double opacity) { host.setWorkspaceOpacity(opacity); }
            @Override public void ui(Runnable action) {
                if(javax.swing.SwingUtilities.isEventDispatchThread()){action.run();return;}
                try {javax.swing.SwingUtilities.invokeAndWait(action);}catch(Exception failure){throw new AssertionError(failure);}
            }
            @Override public void flush() { host.flush(); }
            @Override public <T> void publishApp(Topic<T> topic, T payload) { host.publishApp(topic, payload); }
            @Override public List<ActivityEvent> activityLog() { return host.activityLog(); }
            @Override public List<dev.jasper.sdk.activity.ConnectionEvent> connectionLog() { return host.connectionLog(); }
            @Override public List<String> actions() { return host.actions(); }
            @Override public boolean invoke(String actionId, java.util.UUID windowId, java.util.UUID paneIdOrNull) { return host.invoke(actionId, windowId, paneIdOrNull); }
            @Override public List<String> toolbar() { return host.toolbar(); }
            @Override public List<String> openToolbarViews(java.util.UUID windowId) {
                return host.toolbarViews(windowId).stream().map(view -> view.spec().root().id()).toList();
            }
            @Override public void closeToolbarViews(java.util.UUID windowId) { host.closeToolbarViews(windowId); }
            @Override public void clickToolbarViewButton(java.util.UUID windowId, String buttonId) {
                for (var view : host.toolbarViews(windowId))
                    for (var child : ((dev.jasper.sdk.ui.Nodes.Stack) view.spec().root()).children())
                        if (child instanceof dev.jasper.sdk.ui.Nodes.Button button && button.id().equals(buttonId)) {
                            view.emit(new dev.jasper.sdk.ui.UiEvent.Action(button.action()));
                            return;
                        }
                throw new AssertionError("No toolbar view button " + buttonId + " in " + windowId);
            }
            @Override public List<String> menu(String target) { return host.menu(target); }
            @Override public List<String> status() { return host.status(); }
            @Override public List<String> panels() { return host.panels(); }
            @Override public javax.swing.JComponent openPanel(String panelId) { return host.openPanel(panelId); }
            @Override public String panelHeader(String panelId) {
                var header = host.panelHeader(panelId);
                var labels = header.actions().stream().map(dev.jasper.sdk.ui.PanelHeader.Action::label).toList();
                return header.detail() + "|" + header.active() + "|" + (labels.isEmpty() ? "-" : String.join(";", labels));
            }
            @Override public boolean runPanelHeaderAction(String panelId, String label) {
                var action = host.panelHeader(panelId).actions().stream().filter(a -> a.label().equals(label)).findFirst();
                action.ifPresent(a -> a.invoke().run());
                return action.isPresent();
            }
            @Override public void closePanel(String panelId) { host.closePanel(panelId); }
            @Override public void placePanel(String panelId, dev.jasper.sdk.ui.PanelPlacement placement) { host.placePanel(panelId, placement); }
            @Override public String viewRoute(dev.jasper.sdk.ui.UiView view) { return ((FakeKitView) view).route; }
            @Override public List<String> rail() { return host.rail(); }
            @Override public List<String> windows() { return host.windows(); }
            @Override public String dialogSwatch(String pluginId, String title) { return host.dialogSwatch(pluginId, title).map(Enum::name).orElse("-"); }
            @Override public boolean requestClose(String windowId) { return host.requestClose(windowId); }
            @Override public boolean requestQuit() { return host.requestQuit(); }
            @Override public java.util.UUID addTerminalWindow() { return host.addTerminalWindow(); }
            @Override public java.util.UUID addTerminalTab(java.util.UUID windowId, String title) { return host.addTerminalTab(windowId, title); }
            @Override public java.util.UUID addContentTab(java.util.UUID windowId, String title) { return host.addContentTab(windowId, title); }
            @Override public void moveTab(java.util.UUID tabId, java.util.UUID windowId) { host.moveTab(tabId, windowId); }
            @Override public java.util.Optional<java.util.UUID> restoreTab(String pluginId, java.util.UUID windowId, String title,
                    dev.jasper.sdk.RestoreState state) { return host.restoreTab(pluginId, windowId, title, state); }
            @Override public java.util.Optional<dev.jasper.sdk.RestoreState> restoreState(java.util.UUID tabId) { return host.restoreState(tabId); }
            @Override public void closeTab(java.util.UUID tabId) { host.closeTab(tabId); }
            @Override public List<List<java.util.UUID>> windowTabs(java.util.UUID windowId) { return host.windowTabs(windowId); }
            @Override public List<String> scopes() { return host.scopes(); }
            @Override public String scopePresentation(String scopeId) { return host.scopePresentation(scopeId); }
            @Override public List<String> fileSources() { return host.fileSources(); }
            @Override public List<String> searchScope(String scopeId, String query, java.util.UUID windowId, java.util.UUID paneIdOrNull) { return host.searchScope(scopeId, query, windowId, paneIdOrNull); }
            @Override public boolean availableInScope(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull) { return host.availableInScope(scopeId, rowId, verbId, windowId, paneIdOrNull); }
            @Override public java.util.Optional<String> stepInScope(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull) { return host.stepInScope(scopeId, rowId, verbId, windowId, paneIdOrNull); }
            @Override public String completeStep(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull, java.util.Map<String, String> values) { return host.completeStep(scopeId, rowId, verbId, windowId, paneIdOrNull, values); }
            @Override public void executeInScope(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull) { host.executeInScope(scopeId, rowId, verbId, windowId, paneIdOrNull); }
            @Override public List<String> paletteOpens() { return host.paletteOpens(); }
            @Override public List<String> notifications() { return host.notifications().posts().stream().map(FakeContractTest::contractLine).toList(); }
            @Override public List<String> notificationGroups() { return host.notifications().groups(); }
            @Override public boolean runNotificationAction(String title, String label) { return host.notifications().runAction(title, label); }
            @Override public boolean clickNotification(String title) { return host.notifications().click(title); }
            @Override public List<java.nio.file.Path> openedInEditor() { return host.openedInEditor(); }
            @Override public List<java.net.URI> browsed() { return host.browsed(); }
            @Override public java.util.UUID addTerminalPane(java.util.UUID tabId, String title, java.nio.file.Path directory) {
                return host.addTerminalPane(tabId, new dev.jasper.sdk.terminal.PaneInfo(title, java.util.Optional.ofNullable(directory), java.util.Optional.empty(), 80, 24,
                    true, dev.jasper.sdk.terminal.SessionKind.LOCAL, java.util.Optional.empty(), dev.jasper.sdk.terminal.SessionState.RUNNING, java.util.OptionalInt.empty(), "zsh"));
            }
            @Override public void holdTerminalPane(java.util.UUID paneId) { host.holdPane(paneId); }
            @Override public void activateTerminalWindow(java.util.UUID windowId) { host.activateTerminalWindow(windowId); }
            @Override public void focusTerminalPane(java.util.UUID paneId) { host.focusTerminalPane(paneId); }
            @Override public void closeTerminalPane(java.util.UUID paneId) { host.closeTerminalPane(paneId); }
            @Override public void selectInPane(java.util.UUID paneId, String text) { host.setSelection(paneId, text); }
            @Override public List<String> sentToPane(java.util.UUID paneId) { return host.sent(paneId); }
            @Override public String sessionState(java.util.UUID paneId) { return host.sessionState(paneId); }
            @Override public void reportDirectory(java.util.UUID paneId, String hostOrEmpty, String path) {
                if (hostOrEmpty.isEmpty()) host.cwdChanged(paneId, java.nio.file.Path.of(path)); else host.remoteCwdChanged(paneId, hostOrEmpty, path);
            }
            @Override public void cancelSession(java.util.UUID paneId) { host.cancelSession(paneId); }
            @Override public void reconnectSession(java.util.UUID paneId) { host.reconnectSession(paneId); }
            @Override public void typeIntoSession(java.util.UUID paneId, String text) { host.typeIntoSession(paneId, text); }
            @Override public String sessionOutput(java.util.UUID paneId) { return host.sessionOutput(paneId); }
            @Override public List<String> openRequests() { return host.openRequests().stream().filter(line -> !line.startsWith("front|")).toList(); }
            @Override public void setShellProfiles(List<dev.jasper.sdk.terminal.ShellProfileInfo> profiles) { host.setShellProfiles(profiles); }
            @Override public void finishCommand(java.util.UUID paneId, String command, int exitStatus) {
                host.commandFinished(paneId, command, java.util.OptionalInt.of(exitStatus), java.time.Duration.ofMillis(1500));
            }
            @Override public void setVariant(dev.jasper.sdk.Variant variant) { host.setVariant(variant); }
            @Override public void setTerminalTypography(dev.jasper.sdk.ui.TerminalTypography typography) { host.setTerminalTypography(typography); }
            @Override public void setPresentation(dev.jasper.sdk.ui.Presentation presentation) { host.setPresentation(presentation); }
            @Override public java.util.Optional<String> clipboard() { return host.clipboard().text(); }
            @Override public void userCopies(String text) { host.clipboard().setText(text); }
            @Override public void clipboardBusy(boolean busy) { host.clipboard().failWrites(busy); }
            @Override public void expireClipboardSecret() { host.clipboard().expireSecrets(); }
            @Override public void stopAll() { host.stopAll(); }
            @Override public void declareSubcommand(String pluginId, String name, String summary) { host.declareSubcommand(pluginId, name, summary); }
            @Override public ContractCall call(String name, List<String> arguments, java.nio.file.Path workingDirectory, java.util.UUID paneOrNull) {
                FakeCommandCall call = host.call(name, arguments, workingDirectory, paneOrNull);
                return new ContractCall() {
                    @Override public int exitCode() {
                        try { return call.exit().get(5, java.util.concurrent.TimeUnit.SECONDS); }
                        catch (Exception failure) { throw new AssertionError(failure); }
                    }
                    @Override public boolean finished() { return call.exit().isDone(); }
                    @Override public String out() { return call.out(); }
                    @Override public String err() { return call.err(); }
                    @Override public void cancel() { call.cancel(); }
                };
            }
            @Override public java.util.Map<String, String> shellVariables(java.util.Map<String, String> jasperEnvironment) {
                return host.shellEnvironment(jasperEnvironment);
            }
            @Override public java.util.Map<String, String> tabNamePieces(java.util.UUID tabId) { return host.tabNamePieces(tabId); }
            @Override public void declareBundled(String pluginId) { host.declareBundled(pluginId); }
            @Override public boolean edit(java.nio.file.Path file, java.util.OptionalInt line, java.util.Optional<java.util.UUID> windowId) {
                return host.edit(file, line, windowId);
            }
            @Override public boolean openLink(java.net.URI address, dev.jasper.sdk.ui.LinkOrigin origin, dev.jasper.sdk.ui.LinkPlacement placement,
                                              java.util.Optional<String> target, java.util.Optional<java.util.UUID> windowId) {
                return host.openLink(address, origin, placement, target, windowId);
            }
            @Override public void commandClient(java.nio.file.Path client) { host.setCommandClient(client); }
            @Override public void close() { host.close(); }
        };
    }

    /**
     * Workspace plan 6b Rulings 24, the fake alone: while a restorer runs, the window being restored opens no session but
     * the saved one. The application pins it in {@code PluginTabRestoreTest}.
     */
    @org.junit.jupiter.api.Test void whileARestorerRunsTheWindowBeingRestoredOpensNoOtherSession() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            java.util.UUID window = host.addTerminalWindow();
            var other = new java.util.concurrent.atomic.AtomicReference<java.util.Optional<dev.jasper.sdk.terminal.PaneHandle>>();
            host.start(new PluginInfo("test.alpha", "Alpha", "1.0.0", Set.of(dev.jasper.sdk.Capabilities.SESSION_PROVIDE)), Set.of(), Set.of(),
                context -> context.windows().restorer("ssh", request -> {
                    var terminals = context.terminals();
                    terminals.openTab(request.window(), dev.jasper.sdk.terminal.OpenRequest.session(
                        dev.jasper.sdk.terminal.SessionSpec.of("saved", pending -> { }).withRestore(request.state())));
                    other.set(terminals.openTab(request.window(), dev.jasper.sdk.terminal.OpenRequest.session(
                        dev.jasper.sdk.terminal.SessionSpec.of("other", pending -> { }))));
                    return java.util.Optional.empty();
                }));
            org.assertj.core.api.Assertions.assertThat(host.restoreTab("test.alpha", window, "saved",
                new dev.jasper.sdk.RestoreState("ssh", java.util.Map.of()))).isPresent();
            org.assertj.core.api.Assertions.assertThat(other.get()).as("the window being restored takes only the saved tab").isEmpty();
        } });
    }

    /**
     * Workspace plan 6b Rulings 24 for a split, the fake alone, as the application's {@code WindowContent.openTerminal}
     * decides it: while a restorer runs, the window being restored splits only into the saved session, which takes the
     * slot, placed, not shown and not started; a local split and any other session are refused.
     */
    @org.junit.jupiter.api.Test void whileARestorerRunsTheWindowBeingRestoredSplitsOnlyIntoTheSavedSession() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            java.util.UUID window = host.addTerminalWindow();
            java.util.UUID shell = host.addTerminalTab(window, "shell");
            host.addTerminalPane(shell, FakeTerminalsTest.info("shell"));
            var local = new java.util.concurrent.atomic.AtomicReference<java.util.Optional<dev.jasper.sdk.terminal.PaneHandle>>();
            var saved = new java.util.concurrent.atomic.AtomicReference<java.util.Optional<dev.jasper.sdk.terminal.PaneHandle>>();
            var other = new java.util.concurrent.atomic.AtomicReference<java.util.Optional<dev.jasper.sdk.terminal.PaneHandle>>();
            var alpha = new java.util.concurrent.atomic.AtomicReference<dev.jasper.sdk.plugin.PluginContext>();
            host.start(new PluginInfo("test.alpha", "Alpha", "1.0.0", Set.of(dev.jasper.sdk.Capabilities.SESSION_PROVIDE,
                    dev.jasper.sdk.Capabilities.TERMINAL_OPEN)), Set.of(), Set.of(),
                context -> { alpha.set(context); context.windows().restorer("ssh", request -> {
                    var terminals = context.terminals();
                    var target = terminals.window(request.window().id()).orElseThrow().activeTab().orElseThrow().terminal().orElseThrow();
                    local.set(terminals.split(target, dev.jasper.sdk.terminal.Direction.RIGHT, dev.jasper.sdk.terminal.OpenRequest.local()));
                    saved.set(terminals.split(target, dev.jasper.sdk.terminal.Direction.RIGHT, dev.jasper.sdk.terminal.OpenRequest.session(
                        dev.jasper.sdk.terminal.SessionSpec.of("saved", pending -> { }).withRestore(request.state()))));
                    other.set(terminals.split(target, dev.jasper.sdk.terminal.Direction.DOWN, dev.jasper.sdk.terminal.OpenRequest.session(
                        dev.jasper.sdk.terminal.SessionSpec.of("other", pending -> { }))));
                    return java.util.Optional.empty();
                }); });
            java.util.Optional<java.util.UUID> back = host.restoreTab("test.alpha", window, "saved", new dev.jasper.sdk.RestoreState("ssh", java.util.Map.of()));
            org.assertj.core.api.Assertions.assertThat(local.get()).as("a local split is refused").isEmpty();
            org.assertj.core.api.Assertions.assertThat(other.get()).as("another session is refused").isEmpty();
            org.assertj.core.api.Assertions.assertThat(saved.get().map(pane -> pane.tab().id())).as("the saved session takes the slot").isEqualTo(back).isPresent();
            org.assertj.core.api.Assertions.assertThat(host.sessionState(saved.get().orElseThrow().id())).as("not started").isEqualTo("NOT_STARTED|");
            org.assertj.core.api.Assertions.assertThat(alpha.get().terminals().window(window).orElseThrow().activeTab().map(tab -> tab.id()))
                .as("not shown").contains(shell);
        } });
    }

    /**
     * Workspace plan 6b Rulings 34 and 35, the fake alone: a restored content tab is neither selected nor active until its
     * tab is shown, and a restored session's pane waits, not started, until its tab is first shown, when it connects.
     */
    @org.junit.jupiter.api.Test void aRestoredTabWaitsUnshownAndItsSessionConnectsWhenItsTabIsFirstShown() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            java.util.UUID window = host.addTerminalWindow();
            int[] connects = {0};
            var content = new java.util.concurrent.atomic.AtomicReference<dev.jasper.sdk.ui.ContentTab>();
            var alpha = new java.util.concurrent.atomic.AtomicReference<dev.jasper.sdk.plugin.PluginContext>();
            host.start(new PluginInfo("test.alpha", "Alpha", "1.0.0", Set.of(dev.jasper.sdk.Capabilities.SESSION_PROVIDE)), Set.of(), Set.of(), context -> {
                alpha.set(context);
                context.windows().restorer("note", request -> {
                    content.set(context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("note", request.title()), request.window(), view(context)));
                    return java.util.Optional.empty();
                });
                context.windows().restorer("ssh", request -> {
                    context.terminals().openTab(request.window(), dev.jasper.sdk.terminal.OpenRequest.session(
                        dev.jasper.sdk.terminal.SessionSpec.of("saved", pending -> connects[0]++).withRestore(request.state())));
                    return java.util.Optional.empty();
                });
            });
            var terminals = alpha.get().terminals();
            java.util.UUID note = host.restoreTab("test.alpha", window, "Note", new dev.jasper.sdk.RestoreState("note", java.util.Map.of())).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(terminals.window(window).orElseThrow().activeTab().map(tab -> tab.id())).as("not selected").isNotEqualTo(java.util.Optional.of(note));
            org.assertj.core.api.Assertions.assertThat(content.get().isActive()).as("not active").isFalse();
            terminals.tab(note).orElseThrow().select();
            org.assertj.core.api.Assertions.assertThat(content.get().isActive()).as("active once shown").isTrue();
            java.util.UUID session = host.restoreTab("test.alpha", window, "saved", new dev.jasper.sdk.RestoreState("ssh", java.util.Map.of())).orElseThrow();
            java.util.UUID pane = terminals.tab(session).orElseThrow().terminal().orElseThrow().id();
            org.assertj.core.api.Assertions.assertThat(host.sessionState(pane)).isEqualTo("NOT_STARTED|");
            org.assertj.core.api.Assertions.assertThat(connects[0]).as("no attempt before its tab shows").isZero();
            host.focusTerminalPane(pane);
            org.assertj.core.api.Assertions.assertThat(connects[0]).as("one attempt once it shows").isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(host.sessionState(pane)).isEqualTo("CONNECTING|");
            terminals.tab(note).orElseThrow().select();
            terminals.tab(session).orElseThrow().select();
            org.assertj.core.api.Assertions.assertThat(connects[0]).as("shown again, no second attempt").isEqualTo(1);
        } });
    }

    /**
     * 6b Task 1's review, the fake alone, as the application: a restorer's reason is recorded only when no tab came back, a
     * checked exception is contained as Jasper's containment holds one, and an Error closes the tab it opened: a
     * VirtualMachineError goes on, another is recorded as the plugin's restore failure (6b's final review, M1).
     */
    @org.junit.jupiter.api.Test void aRestorersReasonCountsOnlyWithoutATabAndAnErrorClosesTheTabItOpened() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            java.util.UUID window = host.addTerminalWindow();
            var fatal = new java.util.concurrent.atomic.AtomicReference<dev.jasper.sdk.ui.ContentTab>();
            var broken = new java.util.concurrent.atomic.AtomicReference<dev.jasper.sdk.ui.ContentTab>();
            host.start(new PluginInfo("test.alpha", "Alpha", "1.0.0", Set.of()), Set.of(), Set.of(), context -> {
                var windows = context.windows();
                windows.restorer("kept", request -> {
                    windows.openTab(new dev.jasper.sdk.ui.ContentSpec("kept", request.title()), request.window(), view(context));
                    return java.util.Optional.of("declined once open");
                });
                windows.restorer("declined", request -> java.util.Optional.of("its file is gone"));
                windows.restorer("checked", request -> { throw FakeContractTest.<RuntimeException>sneaky(new java.io.IOException("unreadable")); });
                windows.restorer("fatal", request -> {
                    fatal.set(windows.openTab(new dev.jasper.sdk.ui.ContentSpec("fatal", request.title()), request.window(), view(context)));
                    throw new InternalError("the restorer broke");
                });
                windows.restorer("broken", request -> {
                    broken.set(windows.openTab(new dev.jasper.sdk.ui.ContentSpec("broken", request.title()), request.window(), view(context)));
                    throw new AssertionError("the index is broken");
                });
            });
            org.assertj.core.api.Assertions.assertThat(host.restoreTab("test.alpha", window, "Kept", new dev.jasper.sdk.RestoreState("kept", java.util.Map.of())))
                .as("its tab came back").isPresent();
            org.assertj.core.api.Assertions.assertThat(host.restoreTab("test.alpha", window, "Declined", new dev.jasper.sdk.RestoreState("declined", java.util.Map.of()))).isEmpty();
            org.assertj.core.api.Assertions.assertThat(host.declinedRestores()).as("only the decline that left no tab").containsExactly("test.alpha declined: its file is gone");
            org.assertj.core.api.Assertions.assertThat(host.restoreTab("test.alpha", window, "Checked", new dev.jasper.sdk.RestoreState("checked", java.util.Map.of()))).isEmpty();
            org.assertj.core.api.Assertions.assertThat(host.failures()).singleElement().asString().contains("unreadable");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> host.restoreTab("test.alpha", window, "Fatal",
                new dev.jasper.sdk.RestoreState("fatal", java.util.Map.of()))).isInstanceOf(InternalError.class).hasMessage("the restorer broke");
            org.assertj.core.api.Assertions.assertThat(fatal.get().isClosed()).as("the tab it opened closed").isTrue();
            org.assertj.core.api.Assertions.assertThat(host.restoreTab("test.alpha", window, "Broken", new dev.jasper.sdk.RestoreState("broken", java.util.Map.of())))
                .as("another Error restores nothing").isEmpty();
            org.assertj.core.api.Assertions.assertThat(broken.get().isClosed()).as("the tab it opened closed").isTrue();
            org.assertj.core.api.Assertions.assertThat(host.failures()).as("the plugin's failure").last().asString().contains("the index is broken");
            org.assertj.core.api.Assertions.assertThat(host.contentTabs()).as("only the kept tab").singleElement().satisfies(tab -> org.assertj.core.api.Assertions.assertThat(tab.isClosed()).isFalse());
        } });
    }

    /** A plugin's view for a fake-only case. */
    private static dev.jasper.sdk.ui.UiView view(dev.jasper.sdk.plugin.PluginContext context) {
        return context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("body", dev.jasper.sdk.ui.ControlState.READY, "Content",
            dev.jasper.sdk.ui.Nodes.TextRole.BODY)), event -> { });
    }

    /** Throws {@code failure} unchecked, as code in another JVM language may throw a checked exception. */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneaky(Throwable failure) throws T { throw (T) failure; }

    /** The contract's line for a fake post. */
    static String contractLine(FakeNotifications.Post post) {
        var spec = post.spec();
        List<String> labels = spec.actions().stream().map(dev.jasper.sdk.notifications.NotificationAction::label).toList();
        return String.join("|", post.group(), spec.severity().name(), spec.title(), spec.content(),
            labels.isEmpty() ? "-" : String.join(";", labels), spec.cause().isPresent() ? "cause" : "-",
            spec.window().map(window -> window.id().toString()).orElse("-"), post.expired() ? "expired" : "live");
    }
}

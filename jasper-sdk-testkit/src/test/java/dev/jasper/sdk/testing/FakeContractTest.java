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
            @Override public List<String> menu(String target) { return host.menu(target); }
            @Override public List<String> status() { return host.status(); }
            @Override public List<String> panels() { return host.panels(); }
            @Override public javax.swing.JComponent openPanel(String panelId, java.util.UUID windowId) { return host.openPanel(panelId, windowId); }
            @Override public String panelHeader(String panelId, java.util.UUID windowId) {
                var header = host.panelHeader(panelId, windowId);
                var labels = header.actions().stream().map(dev.jasper.sdk.ui.PanelHeader.Action::label).toList();
                return header.detail() + "|" + header.active() + "|" + (labels.isEmpty() ? "-" : String.join(";", labels));
            }
            @Override public boolean runPanelHeaderAction(String panelId, java.util.UUID windowId, String label) {
                var action = host.panelHeader(panelId, windowId).actions().stream().filter(a -> a.label().equals(label)).findFirst();
                action.ifPresent(a -> a.invoke().run());
                return action.isPresent();
            }
            @Override public void closePanel(String panelId, java.util.UUID windowId) { host.closePanel(panelId, windowId); }
            @Override public String viewRoute(dev.jasper.sdk.ui.UiView view) { return ((FakeKitView) view).route; }
            @Override public List<String> rail() { return host.rail(); }
            @Override public List<String> windows() { return host.windows(); }
            @Override public boolean requestClose(String windowId) { return host.requestClose(windowId); }
            @Override public boolean closeCommand(String windowId) { return host.closeCommand(windowId); }
            @Override public boolean requestQuit() { return host.requestQuit(); }
            @Override public java.util.UUID addTerminalWindow() { return host.addTerminalWindow(); }
            @Override public java.util.UUID addTerminalTab(java.util.UUID windowId, String title) { return host.addTerminalTab(windowId, title); }
            @Override public List<String> scopes() { return host.scopes(); }
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
            @Override public void close() { host.close(); }
        };
    }

    /** The contract's line for a fake post. */
    static String contractLine(FakeNotifications.Post post) {
        var spec = post.spec();
        List<String> labels = spec.actions().stream().map(dev.jasper.sdk.notifications.NotificationAction::label).toList();
        return String.join("|", post.group(), spec.severity().name(), spec.title(), spec.content(),
            labels.isEmpty() ? "-" : String.join(";", labels), spec.cause().isPresent() ? "cause" : "-",
            spec.window().map(window -> window.id().toString()).orElse("-"), post.expired() ? "expired" : "live");
    }
}

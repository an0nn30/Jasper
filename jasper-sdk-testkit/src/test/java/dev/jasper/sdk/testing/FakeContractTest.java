package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.testing.contract.ContractHarness;
import dev.jasper.sdk.testing.contract.PluginContractTest;
import java.util.List;
import java.util.Set;

/** The fake must behave like the application's runtime. */
class FakeContractTest extends PluginContractTest {
    @Override protected ContractHarness newHarness() {
        var host = new FakePluginHost();
        return new ContractHarness() {
            @Override public void start(PluginInfo info, Set<String> requires, Set<String> optional, Plugin plugin) {
                host.start(info, requires, optional, plugin);
            }
            @Override public boolean active(String pluginId) { return host.active(pluginId); }
            @Override public void ui(Runnable action) { action.run(); }
            @Override public void flush() { host.flush(); }
            @Override public <T> void publishApp(Topic<T> topic, T payload) { host.publishApp(topic, payload); }
            @Override public List<ActivityEvent> activityLog() { return host.activityLog(); }
            @Override public List<String> actions() { return host.actions(); }
            @Override public boolean invoke(String actionId, java.util.UUID windowId, java.util.UUID paneIdOrNull) { return host.invoke(actionId, windowId, paneIdOrNull); }
            @Override public List<String> toolbar() { return host.toolbar(); }
            @Override public List<String> menu(String target) { return host.menu(target); }
            @Override public List<String> status() { return host.status(); }
            @Override public List<String> panels() { return host.panels(); }
            @Override public javax.swing.JComponent openPanel(String panelId, java.util.UUID windowId) { return host.openPanel(panelId, windowId); }
            @Override public List<String> rail() { return host.rail(); }
            @Override public List<String> windows() { return host.windows(); }
            @Override public boolean requestClose(String windowId) { return host.requestClose(windowId); }
            @Override public java.util.UUID addTerminalWindow() { return host.addTerminalWindow(); }
            @Override public java.util.UUID addTerminalTab(java.util.UUID windowId, String title) { return host.addTerminalTab(windowId, title); }
            @Override public java.util.UUID addTerminalPane(java.util.UUID tabId, String title, java.nio.file.Path directory) {
                return host.addTerminalPane(tabId, new dev.jasper.sdk.terminal.PaneInfo(title, java.util.Optional.ofNullable(directory), java.util.Optional.empty(), 80, 24,
                    true, dev.jasper.sdk.terminal.SessionKind.LOCAL, java.util.Optional.empty(), dev.jasper.sdk.terminal.SessionState.RUNNING, java.util.OptionalInt.empty()));
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
            @Override public void finishCommand(java.util.UUID paneId, String command, int exitStatus) {
                host.commandFinished(paneId, command, java.util.OptionalInt.of(exitStatus), java.time.Duration.ofMillis(1500));
            }
            @Override public void setVariant(dev.jasper.sdk.Variant variant) { host.setVariant(variant); }
            @Override public void stopAll() { host.stopAll(); }
            @Override public void close() { host.close(); }
        };
    }
}

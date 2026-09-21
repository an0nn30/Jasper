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
            @Override public void setVariant(dev.jasper.sdk.Variant variant) { host.setVariant(variant); }
            @Override public void stopAll() { host.stopAll(); }
            @Override public void close() { host.close(); }
        };
    }
}

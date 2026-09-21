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

    /** Changes the look and announces it. */
    void setVariant(Variant variant);

    /** Stops every active plugin in reverse start order. */
    void stopAll();

    @Override void close();
}

package dev.jasper.app.plugins;

import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.lifecycle.Subscription;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A scripted shell profile catalog: {@link #set} replaces the list and runs every listener on the caller's thread. */
final class TestShellProfiles {
    private volatile List<ShellProfile> current = List.of();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    void set(List<ShellProfile> profiles) {
        current = List.copyOf(profiles);
        for (Runnable listener : listeners) listener.run();
    }

    int listeners() { return listeners.size(); }

    PluginRuntime.ShellProfiles source() {
        return new PluginRuntime.ShellProfiles(() -> current, listener -> {
            Runnable entry = listener::run;
            listeners.add(entry);
            return new Subscription(() -> listeners.remove(entry));
        });
    }
}

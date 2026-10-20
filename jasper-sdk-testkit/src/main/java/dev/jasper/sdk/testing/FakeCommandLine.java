package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.cli.CommandHandler;
import dev.jasper.sdk.cli.CommandLine;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** One plugin's subcommand handlers, stored in the host; they vanish when the plugin stops. */
final class FakeCommandLine implements CommandLine {
    /** A handler as the host keeps it. */
    record Registered(FakePluginContext context, CommandHandler handler) { }

    private final FakePluginHost host;
    private final FakePluginContext context;

    FakeCommandLine(FakePluginHost host, FakePluginContext context) {
        this.host = host;
        this.context = context;
    }

    @Override public Subscription handle(String name, CommandHandler handler) {
        context.requireOpen();
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        FakePluginHost.Declared declared = host.subcommands.get(name);
        if (declared == null)
            throw new IllegalArgumentException("plugin.toml does not declare the subcommand " + name + " under [[subcommands]]");
        if (!declared.pluginId().equals(context.plugin().id()))
            throw new IllegalArgumentException("The subcommand " + name + " is provided by " + declared.pluginId());
        var registered = new Registered(context, handler);
        if (host.subcommandHandlers.putIfAbsent(name, registered) != null)
            throw new IllegalArgumentException("The subcommand " + name + " already has a handler");
        Subscription subscription = () -> host.subcommandHandlers.remove(name, registered);
        context.own(subscription);
        return subscription;
    }

    @Override public Optional<Path> client() { return host.commandClient; }

    void closeAll() { host.subcommandHandlers.values().removeIf(registered -> registered.context() == context); }
}

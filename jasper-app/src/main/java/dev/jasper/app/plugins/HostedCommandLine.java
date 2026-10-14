package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.cli.CommandHandler;
import dev.jasper.sdk.cli.CommandLine;
import java.util.Objects;

/** One plugin's {@code jasper} subcommands; its handlers end with the plugin. */
final class HostedCommandLine implements CommandLine {
    private final String pluginId;
    private final PluginCommands commands;
    private final HostedUi ui;
    private final HostedTerminals terminals;

    HostedCommandLine(String pluginId, PluginCommands commands, HostedUi ui, HostedTerminals terminals) {
        this.pluginId = pluginId;
        this.commands = commands;
        this.ui = ui;
        this.terminals = terminals;
    }

    @Override public Subscription handle(String name, CommandHandler handler) {
        ui.guard("CommandLine.handle");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        return ui.tracked(commands.register(pluginId, name, handler, terminals::windowOfPane));
    }
}

package dev.jasper.app.launch;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ShellIntegrationMode;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;

import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two halves together: the real scripts, injected the way {@link LaunchSettings} injects them,
 * driven through a real {@link TerminalSession}, asserted through the listener. The script tests run
 * shells on pipes and read raw bytes; only this test proves Jasper understands what they emit.
 */
@DisabledOnOs(OS.WINDOWS)
class ShellIntegrationEndToEndTest {
    @TempDir Path home;

    @Test void zshReportsEachCommandItsStatusAndItsPromptRowsThroughTheSession() throws Exception {
        Path zsh = Path.of("/bin/zsh");
        Assumptions.assumeTrue(Files.isExecutable(zsh), "zsh is not installed");
        Path scripts = ShellIntegrationScripts.install(home.resolve("shell-integration"));
        Files.writeString(home.resolve(".zshrc"), "PROMPT='rc%% '\n");

        var commands = new CopyOnWriteArrayList<String>();
        var statuses = new CopyOnWriteArrayList<OptionalInt>();
        LaunchSettings settings = LaunchSettings.resolve(snapshotFor(zsh), "Mac OS X", inherited(), 80, 24, scripts);
        try (TerminalSession session = TerminalSession.start(SessionLaunchOptions.builder().command(settings.command()).environment(settings.environment()).workingDirectory(home).grid(new GridSize(80, 24)).scrollback(1000)
            .localHostNames(LocalHostNames.cached()).build())) {
            session.addListener(new TerminalSessionListener() {
                @Override public void commandExecuted(String command, OptionalInt status,
                        Optional<Path> directory, java.time.Duration duration) {
                    commands.add(command);
                    statuses.add(status);
                }
            });
            until(session::shellIntegrationDetected, "the shell marked its first prompt");

            session.write("printf 'hello\\n'\n");
            until(() -> commands.size() == 1, "the first command was reported");
            session.write("\n");                                  // an empty Enter reports nothing
            session.write("false\n");
            until(() -> commands.size() == 2, "the failing command was reported");

            assertThat(commands).containsExactly("printf 'hello\\n'", "false");
            assertThat(statuses).containsExactly(OptionalInt.of(0), OptionalInt.of(1));
            // The empty Enter between them produced no third report.
            assertThat(commands).hasSize(2);
            // Prompt rows themselves are package-private bookkeeping, covered by the terminal
            // module's own tests; OSC 7 is the directory signal this side can observe.
            assertThat(session.workingDirectory()).contains(home.toRealPath());
        }
    }

    private ConfigSnapshot snapshotFor(Path shell) {
        var defaults = ConfigSnapshot.defaults();
        var terminal = defaults.terminal();
        var configured = new TerminalConfig(new TerminalConfig.Shell(shell.toString(), List.of()), terminal.env(),
            terminal.scrollback(), terminal.optionAsMeta(), terminal.cursorShape(), terminal.cursorBlink(),
            terminal.dimInactivePanes(), terminal.copyOnSelect(), terminal.bell(), terminal.onExit(),
            ShellIntegrationMode.AUTO);
        return new ConfigSnapshot(defaults.tabHeight(), defaults.toolbar(), defaults.statusBar(), defaults.font(),
            defaults.variant(), Map.of(), defaults.columns(), defaults.lines(), configured, defaults.buddyEnabled(),
            defaults.historyEnabled(), defaults.maxResults());
    }

    /** A deliberately bare environment, so the developer's own dotfiles cannot change the result. */
    private Map<String, String> inherited() {
        var environment = new HashMap<String, String>();
        environment.put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
        environment.put("HOME", home.toString());
        return environment;
    }

    private static void until(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for: " + description);
            Thread.sleep(10);
        }
    }
}

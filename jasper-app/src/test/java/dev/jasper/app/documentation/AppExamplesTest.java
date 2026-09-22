package dev.jasper.app.documentation;

import dev.jasper.app.commands.*;
import dev.jasper.app.config.*;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.palette.*;
import dev.jasper.app.bootstrap.StartupResources;
import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.notice.*;
import dev.jasper.buddy.view.BuddyCompanion;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Guide snippets are copied exactly and exercised against real production signatures. */
class AppExamplesTest {
    @Test void command() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // example:command:start
            var calls = new AtomicInteger();
            try (var registry = new CommandRegistry()) {
                var action = new AbstractAction("Refresh index") {
                    @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                        calls.incrementAndGet();
                    }
                };
                var registration = registry.register(new Command("refresh_index", action, List.of("reload")));
                registry.find("refresh_index").orElseThrow().action().actionPerformed(null);
                registration.close();
                assertThat(registry.find("refresh_index")).isEmpty();
                assertThat(calls).hasValue(1);
            }
            // example:command:end
        });
    }
    @Test void scope() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // example:scope:start
            var copied = new ArrayList<String>();
            var scope = new PaletteScope() {
                public String id() { return "example.notes"; }
                public String label() { return "Notes"; }
                public String placeholder() { return "Find a note…"; }
                public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("copy", "Copy")); }
                public PaletteResults search(String query, PaletteContext context) {
                    var row = PaletteRow.of("welcome", "Welcome");
                    return new PaletteResults(List.of(row), "Notes", row.id());
                }
                public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
                    copied.add(row.title());
                }
                public Subscription onChanged(Runnable listener) { return new Subscription(() -> {}); }
            };
            var context = new PaletteContext(false, PaletteTarget.none());
            var row = scope.search("", context).rows().getFirst();
            scope.execute(row, scope.verbs().getFirst(), context);
            assertThat(copied).containsExactly("Welcome");
            // example:scope:end
        });
    }
    @Test void settings() {
        // example:settings:start
        var original = ConfigSnapshot.defaults();
        var updated = original.toBuilder().font(original.font().withSize(18f)).build();
        assertThat(updated.fontSize()).isEqualTo(18f);
        assertThat(updated.terminal()).isEqualTo(original.terminal());
        assertThat(updated.keybindings()).isEqualTo(original.keybindings());
        // example:settings:end
    }
    @Test void producer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // example:producer:start
            var options = BuddyOptions.builder(new java.awt.Font("Dialog", 0, 13)).build();
            try (var buddy = new BuddyCompanion(options)) {
                var id = new BuddyNoticeId("example.transfer", UUID.randomUUID());
                buddy.post(new BuddyNotice(id, BuddyNotice.Kind.TASK, "Copy logs", BuddyNotice.State.RUNNING,
                    () -> "3 files remaining", () -> {}));
                // At producer close: cancel queued work first, then freeze and disconnect the notice.
                buddy.orphan(id, "Stopped after 3 files");
                buddy.acknowledge(id);
            }
            // example:producer:end
        });
    }
    @Test void platform() {
        // example:platform:start
        var hooks = new ArrayList<Runnable>();
        Runnable requestActivation = () -> {};
        hooks.add(requestActivation);
        try (var resources = new StartupResources()) {
            resources.own(new Subscription(() -> hooks.remove(requestActivation)));
            // If platform installation fails, close rolls back the acquired registration.
        }
        assertThat(hooks).isEmpty();
        // example:platform:end
    }
    @Test void session() {
        // example:session:start
        var launch = SessionLaunchOptions.builder().command(List.of("/bin/sh"))
            .environment(Map.of("TERM", "xterm-256color", "COLORTERM", "truecolor"))
            .workingDirectory(java.nio.file.Path.of(System.getProperty("user.home")))
            .grid(new GridSize(80, 24)).scrollback(1000).build();
        assertThat(launch.grid()).isEqualTo(new GridSize(80, 24));
        // Building options acquires nothing. ShellLauncher starts on its executor; the pane owns close.
        // example:session:end
    }
}

package dev.jasper.sample;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class SamplePluginTest {
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.sample", "Sample", "0.1.0", Set.of());

    @Test void isInertByDefault() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.active("dev.jasper.sample")).isTrue();
            assertThat(host.runBackground()).isZero();
            host.flush();
            assertThat(host.activityLog()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void runsItsDemoActivityWhenConfigured() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_activity", true, "demo_step_millis", 0L));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.runBackground()).isEqualTo(1);
            host.flush();
            assertThat(host.activityLog()).first().satisfies(event -> {
                assertThat(event.state()).isEqualTo(ActivityEvent.State.STARTED);
                assertThat(event.title()).isEqualTo("Sample plugin");
            });
            assertThat(host.activityLog()).last().satisfies(event -> {
                assertThat(event.state()).isEqualTo(ActivityEvent.State.SUCCEEDED);
                assertThat(event.detail()).isEqualTo("Ready");
            });
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void reportsAnOutOfRangeStepDelay() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_step_millis", 60_000L));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.reports()).containsExactly("dev.jasper.sample: demo_step_millis: Use 0 to 5000; using 300.");
        }
    }

    @Test void contributesNothingToTheChromeByDefault() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.actions()).isEmpty();
            assertThat(host.toolbar()).isEmpty();
            assertThat(host.status()).isEmpty();
        }
    }

    @Test void theDemoUiPlacesOneActionEverywhereAndItsStatusFollowsTheActivity() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_ui", true, "demo_step_millis", 0L));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).contains("dev.jasper.sample.demo|Run Sample Activity|true");
            assertThat(host.toolbar()).containsExactly("button:dev.jasper.sample.demo");
            assertThat(host.menu("top:dev.jasper.sample.menu")).containsExactly("item:dev.jasper.sample.demo");
            assertThat(host.menu("VIEW")).containsExactly("item:dev.jasper.sample.demo");
            assertThat(host.menu("context")).containsExactly("item:dev.jasper.sample.demo");
            assertThat(host.status()).containsExactly("dev.jasper.sample.status|RIGHT|Sample: idle|Run the sample activity|dev.jasper.sample.demo");

            assertThat(host.invoke("dev.jasper.sample.demo", java.util.UUID.randomUUID(), null)).isTrue();
            assertThat(host.actions()).as("no second run while one is going").contains("dev.jasper.sample.demo|Run Sample Activity|false");
            assertThat(host.runBackground()).isEqualTo(1);
            host.flush();
            assertThat(host.status()).singleElement().asString().contains("|Sample: ready|");
            assertThat(host.actions()).contains("dev.jasper.sample.demo|Run Sample Activity|true");
        }
    }

    @Test void theDemoUiAddsAPanelARailButtonAndAWindow() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_ui", true, "demo_step_millis", 0L));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.panels()).containsExactly("dev.jasper.sample.panel|Sample|LEFT");
            assertThat(host.rail()).containsExactly("dev.jasper.sample.about");
            assertThat(host.openPanel("dev.jasper.sample.panel", java.util.UUID.randomUUID())).isNotNull();
            assertThat(host.windows()).isEmpty();
            assertThat(host.invoke("dev.jasper.sample.about", java.util.UUID.randomUUID(), null)).isTrue();
            assertThat(host.invoke("dev.jasper.sample.about", java.util.UUID.randomUUID(), null)).isTrue();
            assertThat(host.windows()).as("a singleton").containsExactly("dev.jasper.sample.about-window|About Sample|true");
            assertThat(host.failures()).isEmpty();
        }
    }

    private static final PluginInfo OBSERVING = new PluginInfo("dev.jasper.sample", "Sample", "0.1.0",
        Set.of(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT));

    @Test void theTerminalDemoTypesIntoAPaneAndShowsTheLastExitStatus() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "build");
            UUID pane = host.addTerminalPane(tab, new PaneInfo("zsh", Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true,
                SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.empty(), "zsh"));
            host.activateTerminalWindow(window);
            host.setConfig("dev.jasper.sample", Map.of("demo_terminal", true));
            host.start(OBSERVING, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.menu("context")).contains("item:dev.jasper.sample.greet");
            assertThat(host.status()).as("hidden until a command finishes").noneMatch(line -> line.startsWith("dev.jasper.sample.last"));

            assertThat(host.invoke("dev.jasper.sample.greet", window, pane)).isTrue();
            assertThat(host.invoke("dev.jasper.sample.greet", window, null)).as("falls back to the active pane").isTrue();
            assertThat(host.sent(pane)).containsExactly("write:echo 'hello from the sample plugin'", "write:echo 'hello from the sample plugin'");

            host.commandFinished(pane, "make test", OptionalInt.of(2), Duration.ofMillis(1500));
            host.flush();
            assertThat(host.status()).anyMatch(line -> line.startsWith("dev.jasper.sample.last|LEFT|make test: exit 2"));
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void theTerminalDemoIsSkippedWithoutItsCapabilities() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_terminal", true));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.active("dev.jasper.sample")).isTrue();
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).noneMatch(line -> line.startsWith("dev.jasper.sample.greet"));
        }
    }

    @Test void theSessionDemoProvidesAnEchoThatEndsOnControlD() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.setConfig("dev.jasper.sample", Map.of("demo_session", true, "demo_step_millis", 0L));
            host.start(new PluginInfo("dev.jasper.sample", "Sample", "0.1.0", Set.of(Capabilities.SESSION_PROVIDE)), Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.invoke("dev.jasper.sample.echo", window, null)).isTrue();
            assertThat(host.openRequests()).containsExactly("session-tab|" + window + "|Sample echo");
            UUID pane = host.terminalPanes().stream().filter(id -> host.sessionState(id).startsWith("CONNECTING")).findFirst().orElseThrow();
            assertThat(host.sessionState(pane)).isEqualTo("CONNECTING|");
            host.runBackground();
            assertThat(host.sessionState(pane)).isEqualTo("RUNNING|");
            assertThat(host.sessionOutput(pane)).contains("Sample echo session").endsWith("\r\n");
            host.typeIntoSession(pane, "hi\r");
            assertThat(host.sessionOutput(pane)).isEqualTo("hi\r\n");
            host.typeIntoSession(pane, "\u0004");
            host.flush();
            assertThat(host.sessionState(pane)).isEqualTo("EXITED|exit 0");
            assertThat(host.failures()).isEmpty();
        }
    }

        @Test void thePaletteDemoContributesAGreetingsScopeThatPastesIntoTheOriginPane() {
            try (var host = new FakePluginHost()) {
                UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "build");
                UUID pane = host.addTerminalPane(tab, new PaneInfo("zsh", Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true,
                    SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.empty(), "zsh"));
                host.setConfig("dev.jasper.sample", Map.of("demo_scope", true));
                host.start(new PluginInfo("dev.jasper.sample", "Sample", "0.1.0", Set.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT)),
                    Set.of(), Set.of(), new SamplePlugin());
                assertThat(host.failures()).isEmpty();
                assertThat(host.scopes()).containsExactly("dev.jasper.sample.greetings|Greetings|paste,paste_run");
                assertThat(host.searchScope("dev.jasper.sample.greetings", "", window, pane)).hasSize(3);
                assertThat(host.searchScope("dev.jasper.sample.greetings", "morn", window, pane)).containsExactly("greeting.0|good morning|true");
                assertThat(host.availableInScope("dev.jasper.sample.greetings", "greeting.0", "paste", window, null)).as("needs an origin pane").isFalse();
                host.executeInScope("dev.jasper.sample.greetings", "greeting.0", "paste_run", window, pane);
                assertThat(host.sent(pane)).as("the fake records paste: and write: lines").containsExactly("paste:echo 'good morning'", "write:\r");
                assertThat(host.invoke("dev.jasper.sample.greetings.open", window, pane)).isTrue();
                assertThat(host.paletteOpens()).containsExactly(window + " dev.jasper.sample.greetings - -");
            }
        }
}

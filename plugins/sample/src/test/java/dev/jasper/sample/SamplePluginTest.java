package dev.jasper.sample;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.testing.FakePluginHost;
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
    private static void start(FakePluginHost host,PluginInfo info,Set<String> requires,Set<String> optional,dev.jasper.sdk.plugin.Plugin plugin) {
        Runnable start=() -> host.start(info,requires,optional,plugin);
        if(javax.swing.SwingUtilities.isEventDispatchThread()){start.run();return;}
        try{javax.swing.SwingUtilities.invokeAndWait(start);}catch(Exception failure){throw new AssertionError(failure);}
    }
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.sample", "Sample", "0.1.0", Set.of());

    @Test void isInertByDefault() {
        try (var host = new FakePluginHost()) {
            start(host,INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.active("dev.jasper.sample")).isTrue();
            assertThat(host.runBackground()).isZero();
            host.flush();
            assertThat(host.activityLog()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void runsItsDemoActivityWhenConfigured() {
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.sample", "demo_activity", true);
            host.setSetting("dev.jasper.sample", "demo_step_millis", 0L);
            start(host,INFO, Set.of(), Set.of(), new SamplePlugin());
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
            host.setSetting("dev.jasper.sample", "demo_step_millis", 60_000L);
            start(host,INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.settingsReports("dev.jasper.sample")).containsExactly("demo_step_millis: Use a whole number from 0–5000.");
        }
    }

    @Test void offersItsOwnSettingsPage() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            start(host,INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.declared("dev.jasper.sample"))
                .containsExactly("demo_activity", "demo_ui", "demo_terminal", "demo_session", "demo_scope", "demo_step_millis");
            var page = host.settingsPanel("dev.jasper.sample").orElseThrow();
            assertThat(host.failures()).isEmpty();
            var view = host.settingsPanelView("dev.jasper.sample").orElseThrow();
            // The canonical Settings page: section labels above settings cards of the host's rows.
            var root = (dev.jasper.sdk.ui.Nodes.Stack) view.spec().root();
            assertThat(root.role()).isEqualTo(dev.jasper.sdk.ui.Nodes.LayoutRole.SETTINGS_PAGE);
            assertThat(root.children()).extracting(node -> ((dev.jasper.sdk.ui.Nodes.Section) node).title()).containsExactly("Demos", "Timing");
            assertThat(root.children()).allSatisfy(node -> assertThat(((dev.jasper.sdk.ui.Nodes.Section) node).body())
                .isInstanceOfSatisfying(dev.jasper.sdk.ui.Nodes.Card.class, card -> assertThat(card.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.CardKind.SETTINGS)));
            assertThat(rows(view)).hasSize(6);
            assertThat(find(page, javax.swing.JSlider.class)).isNull();
            var stepRow = rows(view).getLast();
            var controls = (dev.jasper.sdk.ui.Nodes.Stack) stepRow.control();
            assertThat(controls.role()).as("the generated pages' trailing control slot").isEqualTo(dev.jasper.sdk.ui.Nodes.LayoutRole.SETTINGS_TRAILING);
            var step = (dev.jasper.sdk.ui.Nodes.Field) controls.children().getFirst();
            var reset = (dev.jasper.sdk.ui.Nodes.Button) controls.children().getLast();
            assertThat(reset.label()).as("an icon-only Reset").isEmpty();
            assertThat(reset.icon()).as("the Settings reset glyph").isEqualTo("reset");
            assertThat(reset.state().visible()).as("nothing to reset at the default").isFalse();
            assertThat(step.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.FieldKind.NUMBER);
            assertThat(step.value()).isEqualTo("300");
            assertThat(step.numeric()).contains(new dev.jasper.sdk.ui.Nodes.Numeric(0, 5000, 1, 0));
            host.highlightSettingsPanel("dev.jasper.sample", "step delay");
            assertThat(rows(view).getLast().highlighted()).isTrue();
            view.emit(new dev.jasper.sdk.ui.UiEvent.Edited("settings:demo_step_millis", "1200"));
            assertThat(host.pendingSettings("dev.jasper.sample")).containsEntry("demo_step_millis", 1200);
            assertThat(rows(view).getLast().highlighted()).isTrue();
            assertThat(((dev.jasper.sdk.ui.Nodes.Stack) rows(view).getLast().control()).children().getLast().state().visible())
                .as("a changed value can be reset").isTrue();
            host.highlightSettingsPanel("dev.jasper.sample", "milliseconds");
            assertThat(rows(view).getLast().highlighted()).isTrue();
            host.highlightSettingsPanel("dev.jasper.sample", "");
            assertThat(rows(view).getLast().highlighted()).isFalse();
            assertThat(host.settingsPanelChanges("dev.jasper.sample")).isPositive();
        } });
    }

    @Test void mountedPanelAndAboutTabButtonsDispatchTheirDeclaredActions() throws Exception {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.setSetting("dev.jasper.sample", "demo_ui", true);
            host.setSetting("dev.jasper.sample", "demo_step_millis", 0L);
            start(host,INFO, Set.of(), Set.of(), new SamplePlugin());
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                host.openPanel("dev.jasper.sample.panel", window);
                var panel = host.panelView("dev.jasper.sample.panel", window).orElseThrow();
                var body = (dev.jasper.sdk.ui.Nodes.Empty) ((dev.jasper.sdk.ui.Nodes.Panel) panel.spec().root()).body();
                assertThat(body.title()).isEqualTo("Sample Activity");
                var run = (dev.jasper.sdk.ui.Nodes.Button) body.actions().getLast();
                assertThat(run.kind()).as("an empty state's one secondary action").isEqualTo(dev.jasper.sdk.ui.Nodes.ButtonKind.SECONDARY);
                panel.emit(new dev.jasper.sdk.ui.UiEvent.Action(run.action()));
                assertThat(host.invoke("dev.jasper.sample.about", window, null)).isTrue();
                var about = host.contentView("dev.jasper.sample", "About Sample").orElseThrow();
                var details = (dev.jasper.sdk.ui.Nodes.Button) ((dev.jasper.sdk.ui.Nodes.Empty) about.spec().root()).actions().getLast();
                about.emit(new dev.jasper.sdk.ui.UiEvent.Action(details.action()));
                var dialog = host.windowView("dev.jasper.sample", "Sample Details").orElseThrow();
                var sheet = (dev.jasper.sdk.ui.Nodes.Form) dialog.spec().root();
                assertThat(sheet.sections()).singleElement().isInstanceOf(dev.jasper.sdk.ui.Nodes.Card.class);
                assertThat(sheet.actions()).singleElement().satisfies(close -> assertThat(close.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.ButtonKind.PRIMARY));
                dialog.emit(new dev.jasper.sdk.ui.UiEvent.Action(sheet.cancelAction()));
                assertThat(host.windowView("dev.jasper.sample", "Sample Details")).isEmpty();
            });
            assertThat(host.runBackground()).isEqualTo(1);
            host.flush();
            assertThat(host.activityLog()).anyMatch(event -> event.state() == ActivityEvent.State.STARTED);
            assertThat(host.failures()).isEmpty();
        }
    }

    /** The page's setting rows, in order, across its section cards. */
    private static java.util.List<dev.jasper.sdk.ui.Nodes.SettingRow> rows(dev.jasper.sdk.testing.FakeKitView view) {
        return ((dev.jasper.sdk.ui.Nodes.Stack) view.spec().root()).children().stream()
            .flatMap(section -> ((dev.jasper.sdk.ui.Nodes.Card) ((dev.jasper.sdk.ui.Nodes.Section) section).body()).children().stream())
            .map(dev.jasper.sdk.ui.Nodes.SettingRow.class::cast).toList();
    }

    private static <T> T find(java.awt.Container root, Class<T> type) {
        for (java.awt.Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof java.awt.Container nested) { T found = find(nested, type); if (found != null) return found; }
        }
        return null;
    }

    @Test void contributesNothingToTheChromeByDefault() {
        try (var host = new FakePluginHost()) {
            start(host,INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.actions()).isEmpty();
            assertThat(host.toolbar()).isEmpty();
            assertThat(host.status()).isEmpty();
        }
    }

    @Test void theDemoUiPlacesOneActionEverywhereAndItsStatusFollowsTheActivity() {
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.sample", "demo_ui", true);
            host.setSetting("dev.jasper.sample", "demo_step_millis", 0L);
            start(host,INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).contains("dev.jasper.sample.demo|Run Sample Activity|true");
            assertThat(host.toolbar()).containsExactly("button:dev.jasper.sample.demo");
            assertThat(host.menu("top:dev.jasper.sample.menu")).containsExactly("item:dev.jasper.sample.demo");
            assertThat(host.menu("TOOLS")).containsExactly("item:dev.jasper.sample.demo");
            assertThat(host.menu("VIEW")).isEmpty();
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

    @Test void theDemoUiAddsAPanelARailButtonAndAnAboutTab() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.sample", "demo_ui", true);
            host.setSetting("dev.jasper.sample", "demo_step_millis", 0L);
            start(host,INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.panels()).containsExactly("dev.jasper.sample.panel|Sample|LEFT");
            assertThat(host.rail()).containsExactly("dev.jasper.sample.about");
            assertThat(host.openPanel("dev.jasper.sample.panel", java.util.UUID.randomUUID())).isNotNull();
            assertThat(host.windows()).isEmpty();
            assertThat(host.invoke("dev.jasper.sample.about", java.util.UUID.randomUUID(), null)).isTrue();
            assertThat(host.invoke("dev.jasper.sample.about", java.util.UUID.randomUUID(), null)).isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).as("one About tab").hasSize(1);
            assertThat(host.contentView("dev.jasper.sample", "About Sample")).isPresent();
            assertThat(host.failures()).isEmpty();
        } });
    }

    private static final PluginInfo OBSERVING = new PluginInfo("dev.jasper.sample", "Sample", "0.1.0",
        Set.of(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT));

    @Test void theTerminalDemoTypesIntoAPaneAndShowsTheLastExitStatus() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "build");
            UUID pane = host.addTerminalPane(tab, new PaneInfo("zsh", Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true,
                SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.empty(), "zsh"));
            host.activateTerminalWindow(window);
            host.setSetting("dev.jasper.sample", "demo_terminal", true);
            start(host,OBSERVING, Set.of(), Set.of(), new SamplePlugin());
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
            host.setSetting("dev.jasper.sample", "demo_terminal", true);
            start(host,INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.active("dev.jasper.sample")).isTrue();
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).noneMatch(line -> line.startsWith("dev.jasper.sample.greet"));
        }
    }

    @Test void theSessionDemoProvidesAnEchoThatEndsOnControlD() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.setSetting("dev.jasper.sample", "demo_session", true);
            host.setSetting("dev.jasper.sample", "demo_step_millis", 0L);
            start(host,new PluginInfo("dev.jasper.sample", "Sample", "0.1.0", Set.of(Capabilities.SESSION_PROVIDE)), Set.of(), Set.of(), new SamplePlugin());
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
                host.setSetting("dev.jasper.sample", "demo_scope", true);
                start(host,new PluginInfo("dev.jasper.sample", "Sample", "0.1.0", Set.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT)),
                    Set.of(), Set.of(), new SamplePlugin());
                assertThat(host.failures()).isEmpty();
                assertThat(host.scopes()).containsExactly("dev.jasper.sample.greetings|Greetings|paste,paste_run");
                assertThat(host.scopePresentation("dev.jasper.sample.greetings")).as("spec 14.6").isEqualTo("NEUTRAL|greeting|STANDARD,STANDARD");
                assertThat(host.searchScope("dev.jasper.sample.greetings", "", window, pane)).hasSize(3);
                assertThat(host.searchScope("dev.jasper.sample.greetings", "morn", window, pane)).containsExactly("greeting.0|good morning|true");
                assertThat(host.availableInScope("dev.jasper.sample.greetings", "greeting.0", "paste", window, null)).as("needs an origin pane").isFalse();
                host.executeInScope("dev.jasper.sample.greetings", "greeting.0", "paste_run", window, pane);
                assertThat(host.sent(pane)).as("the fake records paste: and write: lines").containsExactly("paste:echo 'good morning'", "write:\r");
                assertThat(host.invoke("dev.jasper.sample.greetings.open", window, pane)).isTrue();
                assertThat(host.paletteOpens()).containsExactly(window + " dev.jasper.sample.greetings - -");
            }
        }

    @Test void demoActionAndPanelUseRegisteredMonochromeFlask() {
        try(var host=new FakePluginHost()) {
            host.setSetting("dev.jasper.sample","demo_ui",true);
            start(host,INFO,Set.of(),Set.of(),new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.actionSpec("dev.jasper.sample.demo").orElseThrow().icon()).contains("dev.jasper.sample:flask");
        }
    }
}

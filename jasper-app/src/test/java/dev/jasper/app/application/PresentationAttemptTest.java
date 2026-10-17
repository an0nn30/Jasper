package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginSelection;
import dev.jasper.app.presentation.AttemptStore;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.testsupport.LookFixtures;
import dev.jasper.app.workspace.DesktopTestSupport;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 4.6: the attempt record across a start, a failed provider, a skipped look and an orderly quit. */
class PresentationAttemptTest {
    static final String ID = "dev.example.look";
    @TempDir Path home;

    AppDirs dirs() { return new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")); }

    /** A provider with no classes: its look is chosen, and its plugin fails to load. */
    Path provider(String kit) throws Exception {
        return LookFixtures.plugin(home.resolve("dev"), ID, LookFixtures.descriptor(ID, "Example") + LookFixtures.provider("look.kit.toml"),
            Map.of("look.kit.toml", kit));
    }

    /** A provider whose plugin loads and starts, so only the paint decides its attempt. */
    Path loadable() throws Exception {
        return LookFixtures.loadable(home.resolve("dev"), ID, LookFixtures.descriptor(ID, "Example") + LookFixtures.provider("look.kit.toml"),
            Map.of("look.kit.toml", LookFixtures.KIT));
    }

    Optional<AttemptStore.State> state() { return new AttemptStore(home).read().of(ID).state(); }

    JasperApplication started(PluginSelection selection) throws Exception {
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
            application[0].startPlugins(dirs());
        });
        return application[0];
    }

    void quit(JasperApplication application, LookAndFeel original) throws Exception {
        edt(application::quitNow);
        edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
    }

    @Test void aProviderThatFailsToLoadRecordsFailedAndTheSessionKeepsItsLook() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var selection = PluginSelection.of(dirs(), null, "B", provider(LookFixtures.KIT), false);
        var application = started(selection);
        try {
            assertThat(new AttemptStore(home).read().of(ID).state()).contains(AttemptStore.State.FAILED);
            assertThat(application.presentation().look()).as("this session keeps the look, without the plugin").isEqualTo(ID);
            var next = PluginSelection.of(dirs(), null, "B", home.resolve("dev").resolve(ID), false);
            assertThat(next.skipped()).as("the next start skips it").isPresent();
        } finally { quit(application, original); }
    }

    @Test void aSkippedLookPostsOneWarningWithTryAgain() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        Path dev = provider(LookFixtures.KIT);
        PluginSelection.of(dirs(), null, "B", dev, false).attempt().pending();
        var selection = PluginSelection.of(dirs(), null, "B", dev, false);
        var application = started(selection);
        try {
            edt(() -> {
                var warnings = application.notificationCenter().history().stream().map(entry -> entry.request())
                    .filter(request -> request.title().equals("Jasper started with its standard look")).toList();
                assertThat(warnings).singleElement().satisfies(request -> {
                    assertThat(request.severity()).isEqualTo(Severity.WARNING);
                    assertThat(request.content()).isEqualTo("The last start with the Example look did not finish, so Jasper uses its standard look until you try again.");
                    assertThat(request.actions()).extracting(action -> action.label()).containsExactly("Try Again");
                });
                assertThat(application.notificationCenter().history()).as("the skipped look posts no plugin problem")
                    .noneMatch(entry -> entry.request().title().equals("Example was not started"));
                assertThat(application.presentationAttempt().records()).isFalse();
            });
        } finally { quit(application, original); }
    }

    @Test void anInvalidKitIsAnnouncedOncePerDigest() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        Path dev = provider("kit = 1\nbase = \"default\"\n[metrics]\nrow_height = 40\n");
        for (int start = 1; start <= 3; start++) {
            if (start == 3) LookFixtures.plugin(home.resolve("dev"), ID, LookFixtures.descriptor(ID, "Example") + LookFixtures.provider("look.kit.toml"),
                Map.of("look.kit.toml", "kit = 1\nbase = \"default\"\n[metrics]\nrow_height = 41\n"));
            var application = started(PluginSelection.of(dirs(), null, "B", dev, false));
            int shown = start;
            try {
                edt(() -> assertThat(application.notificationCenter().history().stream()
                        .filter(entry -> entry.request().title().equals("Example was not started")).count())
                    .as("start " + shown).isEqualTo(shown == 2 ? 0 : 1));
            } finally { quit(application, original); }
        }
    }

    @Test void theFirstWindowsFirstPaintProvesTheLook() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var selection = PluginSelection.of(dirs(), null, "B", loadable(), false);
        var trace = StartupTrace.detached();
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
            application[0].trace(trace);
            application[0].startPlugins(dirs());
            application[0].presentationAttempt().pending();
        });
        try {
            assertThat(state()).contains(AttemptStore.State.PENDING);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            DesktopTestSupport.until(() -> state().equals(Optional.of(AttemptStore.State.PROVEN)));
        } finally { quit(application[0], original); }
    }

    @Test void theRehearsalsPaintProvesTheLook() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var selection = PluginSelection.of(dirs(), null, "B", loadable(), false);
        var application = started(selection);
        try {
            edt(() -> {
                application.presentationAttempt().pending();
                // What the rehearsal stage runs once Rehearsal.paint returned (a real paint needs a display).
                application.rehearsalPainted();
            });
            DesktopTestSupport.until(() -> state().equals(Optional.of(AttemptStore.State.PROVEN)));
        } finally { quit(application, original); }
    }

    @Test void tryAgainForgetsTheRecordAndRestarts() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        Path dev = provider(LookFixtures.KIT);
        PluginSelection.of(dirs(), null, "B", dev, false).attempt().pending();
        var selection = PluginSelection.of(dirs(), null, "B", dev, false);
        List<String> spawned = new CopyOnWriteArrayList<>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown,
                null, null, selection);
            application[0].restartPlanner = mode -> Optional.of(List.of("jasper", mode.name()));
            application[0].spawner = command -> spawned.add(String.join(" ", command));
            application[0].startPlugins(dirs());
        });
        try {
            NotificationAction[] tryAgain = new NotificationAction[1];
            edt(() -> tryAgain[0] = application[0].notificationCenter().history().stream().map(entry -> entry.request())
                .filter(request -> request.title().equals("Jasper started with its standard look")).findFirst().orElseThrow()
                .actions().getFirst());
            edt(() -> tryAgain[0].run().run());
            assertThat(terminated.await(10, TimeUnit.SECONDS)).as("Try Again restarts Jasper").isTrue();
            assertThat(spawned).containsExactly("jasper SAME");
            assertThat(state()).as("the record is forgotten").isEmpty();
            assertThat(PluginSelection.of(dirs(), null, "B", dev, false).skipped()).as("so the next start tries the look").isEmpty();
        } finally {
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }

    @Test void anExitThatIsNotOrderlyBeforeProofKeepsPending() throws Exception {
        Path dev = provider(LookFixtures.KIT);
        var selection = PluginSelection.of(dirs(), null, "B", dev, false);
        selection.attempt().pending();
        // A crash or a nonzero System.exit runs the shutdown hook without an orderly quit.
        selection.attempt().exiting();
        assertThat(state()).contains(AttemptStore.State.PENDING);
        assertThat(PluginSelection.of(dirs(), null, "B", dev, false).skipped()).as("the next start skips the look").isPresent();
    }

    @Test void anOrderlyQuitBeforeProofClearsPendingAtExit() throws Exception {
        var selection = PluginSelection.of(dirs(), null, "B", provider(LookFixtures.KIT), false);
        selection.attempt().pending();
        selection.attempt().orderlyQuit();
        selection.attempt().exiting();
        assertThat(new AttemptStore(home).read().of(ID).state()).isEmpty();
    }
}

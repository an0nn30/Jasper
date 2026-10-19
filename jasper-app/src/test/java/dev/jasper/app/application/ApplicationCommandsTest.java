package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.commandline.Invocation;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.workspace.DesktopTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationCommandsTest {
    @TempDir Path dir;

    private JasperApplication application(Runnable terminated) throws Exception {
        var made = new AtomicReference<JasperApplication>();
        // The initial read is refused on the EDT.
        var service = new ConfigService(dir.resolve("config.toml"), true);
        DesktopTestSupport.edt(() -> made.set(new JasperApplication(service, null, new CommandHistory(), null, terminated)));
        return made.get();
    }

    @Test void aPanesShellIsToldItsPaneAndTheCommandSocket() throws Exception {
        JasperApplication application = application(() -> { });
        UUID pane = UUID.randomUUID();
        assertThat(application.paneVariables(pane)).isEqualTo(Map.of("JASPER_PANE", pane.toString()));
        Path socket = dir.resolve("daemon").resolve("commands");
        application.commandSocket(socket);
        assertThat(application.paneVariables(pane))
            .isEqualTo(Map.of("JASPER_PANE", pane.toString(), "JASPER_COMMAND_SOCKET", socket.toString()));
        DesktopTestSupport.edt(application::quitNow);
    }

    @Test void aSubcommandBeforePluginsStartIsUnavailable() throws Exception {
        JasperApplication application = application(() -> { });
        var err = new StringBuilder();
        var exit = application.commands().dispatch(new Invocation(List.of("editor"), dir, Optional.empty(), text -> { }, err::append));
        assertThat(exit.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(err).hasToString("jasper: Jasper's plugins are not running\n");
        DesktopTestSupport.edt(application::quitNow);
    }

    @Test void commandArgumentsArePathsOnlyWhenEveryOneExistsAndNoSubcommandClaimsTheFirst() throws Exception {
        Path notes = Files.writeString(dir.resolve("notes.txt"), "");
        Path src = Files.createDirectory(dir.resolve("src"));
        Path editor = Files.writeString(dir.resolve("editor"), "");
        assertThat(JasperApplication.commandPaths(dir, List.of(), name -> false)).as("no arguments: a raise").contains(List.of());
        assertThat(JasperApplication.commandPaths(dir, List.of("notes.txt", "./src", src.toString()), name -> false))
            .contains(List.of(notes, src, src));
        assertThat(JasperApplication.commandPaths(dir, List.of("notes.txt", "missing.txt"), name -> false)).isEmpty();
        assertThat(JasperApplication.commandPaths(dir, List.of("editor"), "editor"::equals)).as("a subcommand wins").isEmpty();
        assertThat(JasperApplication.commandPaths(dir, List.of("./editor"), "editor"::equals)).contains(List.of(editor));
        assertThat(JasperApplication.commandPaths(dir, List.of("editr"), "editor"::equals)).as("a word that names nothing").isEmpty();
    }

    @Test void aRaiseOrAPathCallToAQuittingJasperFailsWithTheQuittingMessageAndOpensNothing() throws Exception {
        JasperApplication application = application(() -> { });
        Files.writeString(dir.resolve("notes.txt"), "");
        DesktopTestSupport.edt(application::quitNow);
        for (List<String> arguments : List.of(List.<String>of(), List.of("notes.txt"))) {
            var err = new StringBuilder();
            var answer = application.commands().dispatch(new Invocation(arguments, dir, Optional.empty(), text -> { }, err::append));
            assertThatThrownBy(() -> answer.toCompletableFuture().get(5, TimeUnit.SECONDS))
                .as("%s: not an exit 0 for work that was not done", arguments)
                .isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(IllegalStateException.class).hasMessage("Jasper is quitting; run the command again");
            assertThat(err).as("a path call, not a plugin's subcommand").isEmpty();
        }
    }

    @Test void aCallIsAnsweredZeroOnceItsPathsAreOpenAndNeverBeforeTheEventThreadRunsIt() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        var opened = new ArrayDeque<String>();
        var answer = JasperApplication.onEventThread(pending::add, () -> true, () -> opened.add("notes.txt")).toCompletableFuture();
        assertThat(answer).as("queued on the event thread").isNotDone();
        pending.getFirst().run();
        assertThat(answer.get(5, TimeUnit.SECONDS)).isZero();
        assertThat(opened).containsExactly("notes.txt");
    }

    @Test void aCallWhoseOpenThrowsIsFailedSoItIsNeverLeftWaiting() throws Exception {
        var failure = new IllegalStateException("no window");
        var failed = JasperApplication.onEventThread(Runnable::run, () -> true, () -> { throw failure; }).toCompletableFuture();
        assertThatThrownBy(() -> failed.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCause(failure);
    }

    @Test void anErrorFromOpenFailsTheCallAndStillReachesTheEventThreadsHandler() throws Exception {
        var error = new AssertionError("an error from open");
        var pending = new ArrayDeque<Runnable>();
        var failed = JasperApplication.onEventThread(pending::add, () -> true, () -> { throw error; }).toCompletableFuture();
        assertThatThrownBy(() -> pending.getFirst().run()).as("left to the event thread's handler").isSameAs(error);
        assertThatThrownBy(() -> failed.get(5, TimeUnit.SECONDS)).as("the call is not left waiting")
            .isInstanceOf(ExecutionException.class).hasCause(error);
    }

    @Test void aQuittingApplicationOpensNothingAndFailsTheCall() throws Exception {
        var opened = new ArrayDeque<String>();
        var answer = JasperApplication.onEventThread(Runnable::run, () -> false, () -> opened.add("never")).toCompletableFuture();
        assertThatThrownBy(() -> answer.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .cause().isInstanceOf(IllegalStateException.class).hasMessage("Jasper is quitting; run the command again");
        assertThat(opened).isEmpty();
    }

    @Test void aCallWhoseWindowCannotOpenFailsInsteadOfWaiting() throws Exception {
        JasperApplication application = application(() -> { });
        Files.writeString(dir.resolve("notes.txt"), "");
        var answer = application.commands().dispatch(new Invocation(List.of("notes.txt"), dir, Optional.empty(), text -> { }, text -> { }));
        assertThatThrownBy(() -> answer.toCompletableFuture().get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class).hasRootCauseInstanceOf(java.awt.HeadlessException.class);
        DesktopTestSupport.edt(application::quitNow);
    }

    @Test void aWordThatNamesNothingIsStillACommand() throws Exception {
        JasperApplication application = application(() -> { });
        var err = new StringBuilder();
        var exit = application.commands().dispatch(new Invocation(List.of("editr"), dir, Optional.empty(), text -> { }, err::append));
        assertThat(exit.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(err).hasToString("jasper: Jasper's plugins are not running\n");
        DesktopTestSupport.edt(application::quitNow);
    }

    @Test void aCallThatStartsWithAPathSaysWhichOthersAreMissing() throws Exception {
        Files.writeString(dir.resolve("notes.txt"), "");
        assertThat(JasperApplication.missingPaths(dir, List.of("notes.txt", "gone.txt", "src/x"), name -> false))
            .containsExactly("gone.txt", "src/x");
        assertThat(JasperApplication.missingPaths(dir, List.of("gone.txt", "notes.txt"), name -> false)).as("a command, as before").isEmpty();
        assertThat(JasperApplication.missingPaths(dir, List.of("notes.txt", "gone.txt"), "notes.txt"::equals)).as("a subcommand").isEmpty();
        JasperApplication application = application(() -> { });
        var err = new StringBuilder();
        var exit = application.commands().dispatch(new Invocation(List.of("notes.txt", "gone.txt"), dir, Optional.empty(), text -> { }, err::append));
        assertThat(exit.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(err).hasToString("jasper: no such file or folder: gone.txt\n");
        DesktopTestSupport.edt(application::quitNow);
    }

    @Test void aJasperThatIsNotResidentQuitsOnceNothingKeepsIt() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        DesktopTestSupport.edt(() -> application.quitIfUnused(() -> true));
        assertThat(terminated.await(500, TimeUnit.MILLISECONDS)).as("a call is still running").isFalse();
        DesktopTestSupport.edt(() -> application.quitIfUnused(() -> false));
        assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
    }
}

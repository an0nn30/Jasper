package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commandline.Invocation;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.workspace.DesktopTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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

    @Test void aCallNamingFilesOpensThemAndIsAnsweredOnceTheyAreOpen() throws Exception {
        JasperApplication application = application(() -> { });
        Files.writeString(dir.resolve("notes.txt"), "");
        DesktopTestSupport.edt(application::quitNow);   // open is then inert: no window can be composed headlessly
        var err = new StringBuilder();
        var exit = application.commands().dispatch(new Invocation(List.of("notes.txt"), dir, Optional.empty(), text -> { }, err::append));
        assertThat(exit.toCompletableFuture().get(5, TimeUnit.SECONDS)).isZero();
        assertThat(err).as("a path call, not a plugin's subcommand").isEmpty();
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

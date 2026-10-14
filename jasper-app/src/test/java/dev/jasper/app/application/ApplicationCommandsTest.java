package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commandline.Invocation;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.workspace.DesktopTestSupport;
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

    @Test void aJasperThatIsNotResidentQuitsOnceNothingKeepsIt() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        DesktopTestSupport.edt(() -> application.quitIfUnused(() -> true));
        assertThat(terminated.await(500, TimeUnit.MILLISECONDS)).as("a call is still running").isFalse();
        DesktopTestSupport.edt(() -> application.quitIfUnused(() -> false));
        assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
    }
}

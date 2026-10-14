package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** The built native client (packaging/cli) against a real listener. */
@Timeout(60)
class CommandClientTest {
    private static final String CLIENT = System.getProperty("jasper.commandClient");
    @TempDir Path dir;
    private final List<CommandListener> bound = new ArrayList<>();

    private record Result(int exit, String out, String err) { }

    @BeforeEach void needsTheClient() {
        assumeTrue(CLIENT != null && Files.isExecutable(Path.of(CLIENT)), "the jasper command is built by buildCommandClient");
    }

    @AfterEach void closeAll() { bound.forEach(CommandListener::close); }

    private Path socket() { return dir.resolve("daemon").resolve("commands"); }

    private void bind(CommandDispatcher dispatcher) {
        CommandListener listener = CommandListener.bind(socket(), dispatcher);
        assertThat(listener).isNotNull();
        bound.add(listener);
    }

    private Process start(Map<String, String> environment, List<String> arguments) throws IOException {
        return start(CLIENT, dir, environment, arguments);
    }

    private Process start(String client, Path workingDirectory, Map<String, String> environment, List<String> arguments)
            throws IOException {
        var command = new ArrayList<String>();
        command.add(client);
        command.addAll(arguments);
        var builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
        builder.environment().keySet().removeIf(name -> name.startsWith("JASPER_"));
        builder.environment().putAll(environment);
        Process process = builder.start();
        process.getOutputStream().close();
        return process;
    }

    private Result run(Map<String, String> environment, List<String> arguments) throws Exception {
        return run(CLIENT, dir, environment, arguments);
    }

    private Result run(String client, Path workingDirectory, Map<String, String> environment, List<String> arguments)
            throws Exception {
        Process process = start(client, workingDirectory, environment, arguments);
        var out = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
        var err = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("the client exits").isTrue();
        return new Result(process.exitValue(), out.get(), err.get());
    }

    private static String read(InputStream stream) {
        try { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    private Map<String, String> atSocket() { return Map.of("JASPER_COMMAND_SOCKET", socket().toString()); }

    /** A non-ASCII argument survives only where the JVM can hand it to a child process. */
    private static String fileName() {
        boolean unicode = System.getProperty("os.name").startsWith("Windows")
            || System.getProperty("sun.jnu.encoding", "").equalsIgnoreCase("UTF-8");
        return unicode ? "notes \u00fc.txt" : "notes u.txt";
    }

    @Test void aCallReachesJasperAndItsOutputAndExitCodeComeBack() throws Exception {
        var seen = new AtomicReference<Invocation>();
        bind(invocation -> {
            seen.set(invocation);
            invocation.out("hello \u00fc\n");
            invocation.err("careful\n");
            return CompletableFuture.completedFuture(7);
        });
        Result result = run(atSocket(), List.of("editor", "--wait", fileName(), "a b"));
        assertThat(result).isEqualTo(new Result(7, "hello \u00fc\n", "careful\n"));
        assertThat(seen.get().arguments()).containsExactly("editor", "--wait", fileName(), "a b");
        assertThat(seen.get().workingDirectory().toRealPath()).isEqualTo(dir.toRealPath());
    }

    @Test void theCallNamesItsPaneWhenJasperPaneIsAUuid() throws Exception {
        var panes = new ArrayList<java.util.Optional<UUID>>();
        bind(invocation -> { panes.add(invocation.pane()); return CompletableFuture.completedFuture(0); });
        UUID pane = UUID.randomUUID();
        run(Map.of("JASPER_COMMAND_SOCKET", socket().toString(), "JASPER_PANE", pane.toString()), List.of("editor"));
        run(Map.of("JASPER_COMMAND_SOCKET", socket().toString(), "JASPER_PANE", "garbage"), List.of("editor"));
        assertThat(panes).containsExactly(java.util.Optional.of(pane), java.util.Optional.empty());
    }

    @Test void noArgumentsIsACallWithNone() throws Exception {
        var seen = new AtomicReference<List<String>>();
        bind(invocation -> { seen.set(invocation.arguments()); return CompletableFuture.completedFuture(0); });
        assertThat(run(atSocket(), List.of()).exit()).isZero();
        assertThat(seen.get()).isEmpty();
    }

    @Test void theHomeComesFromJasperHomeWhenNoSocketIsNamed() throws Exception {
        bind(invocation -> CompletableFuture.completedFuture(5));
        assertThat(run(Map.of("JASPER_HOME", dir.toString()), List.of("editor")).exit()).isEqualTo(5);
    }

    @Test void killingTheClientCancelsItsCall() throws Exception {
        var started = new CompletableFuture<Void>();
        var cancelled = new CountDownLatch(1);
        bind(invocation -> {
            invocation.onCancelled(cancelled::countDown);
            started.complete(null);
            return new CompletableFuture<>();
        });
        Process process = start(atSocket(), List.of("editor", "--wait", "notes.txt"));
        started.get(20, TimeUnit.SECONDS);
        process.destroy();
        assertThat(cancelled.await(20, TimeUnit.SECONDS)).isTrue();
    }

    @Test void aJasperFromAnotherBuildARefusalAndAQuitAreExplained() throws Exception {
        Path raw = dir.resolve("raw");
        for (String reply : List.of(CommandProtocol.STALE, CommandProtocol.REFUSED, "")) {
            Files.deleteIfExists(raw);
            try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                server.bind(UnixDomainSocketAddress.of(raw));
                Files.writeString(CommandListener.tokenFile(raw), "token\n");
                var answered = CompletableFuture.runAsync(() -> {
                    try (SocketChannel peer = server.accept()) {
                        ByteBuffer buffer = ByteBuffer.allocate(4096);
                        while (peer.read(buffer) >= 0 && buffer.position() > 0 && buffer.get(buffer.position() - 1) != '\n') { }
                        peer.write(ByteBuffer.wrap(reply.getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                });
                Result result = run(Map.of("JASPER_COMMAND_SOCKET", raw.toString()), List.of("editor"));
                answered.get(10, TimeUnit.SECONDS);
                assertThat(result.exit()).as(reply).isEqualTo(1);
                assertThat(result.err()).as(reply).isEqualTo(switch (reply) {
                    case CommandProtocol.STALE -> "jasper: Jasper was updated; quit it completely and try again\n";
                    case CommandProtocol.REFUSED -> "jasper: Jasper refused the connection\n";
                    default -> "jasper: Jasper quit before the command finished\n";
                });
            }
        }
    }

    @Test void aMissingTokenIsARefusal() throws Exception {
        bind(invocation -> CompletableFuture.completedFuture(0));
        Files.delete(CommandListener.tokenFile(socket()));
        Result result = run(atSocket(), List.of("editor"));
        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.err()).isEqualTo("jasper: Jasper refused the connection\n");
    }

    @Test void withNothingListeningAndNoAppImageItSaysSo() throws Exception {
        Result result = run(atSocket(), List.of("editor", "notes.txt"));
        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.err()).isEqualTo("jasper: can't find Jasper; reinstall the command from Settings\n");
    }

    @Test void aSocketPathTooLongIsExplained() throws Exception {
        Path deep = dir.resolve("x".repeat(120)).resolve("commands");
        Result result = run(Map.of("JASPER_COMMAND_SOCKET", deep.toString()), List.of("editor"));
        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.err()).startsWith("jasper: Jasper's command socket path is too long");
    }

    /** A fake app image whose launcher records its working directory and arguments; Jasper itself never starts. */
    @Test void anOptionLaunchRunsTheLauncherFromTheCallersFolderWithItsArguments() throws Exception {
        boolean mac = System.getProperty("os.name").startsWith("Mac");
        assumeFalse(System.getProperty("os.name").startsWith("Windows"), "Windows starts the launcher with CreateProcess");
        Path image = dir.resolve(mac ? "Jasper.app" : "image");
        Path client = image.resolve(mac ? "Contents/Resources/bin/jasper" : "lib/bin/jasper");
        Path launcher = image.resolve(mac ? "Contents/MacOS/Jasper" : "bin/Jasper");
        Files.createDirectories(client.getParent());
        Files.createDirectories(launcher.getParent());
        Files.copy(Path.of(CLIENT), client, StandardCopyOption.REPLACE_EXISTING);
        assertThat(client.toFile().setExecutable(true)).isTrue();
        // The record appears whole, by rename, so the wait below never reads half of it.
        Files.writeString(launcher, """
            #!/bin/sh
            { pwd -P; for argument in "$@"; do printf '%s\\n' "$argument"; done; } > "$RECORD.part" && mv "$RECORD.part" "$RECORD"
            """);
        assertThat(launcher.toFile().setExecutable(true)).isTrue();
        Path folder = Files.createDirectories(dir.resolve("project"));
        Path record = dir.resolve("record");

        Result result = run(client.toString(), folder,
            Map.of("RECORD", record.toString(), "JASPER_HOME", dir.toString()), List.of("--config", "jasper.toml", "--safe-mode"));

        assertThat(result).isEqualTo(new Result(0, "", ""));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!Files.exists(record) && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(Files.readAllLines(record))
            .containsExactly(folder.toRealPath().toString(), "--config", "jasper.toml", "--safe-mode");
    }

    @Test void helpListsTheSubcommandsJasperLastRecorded() throws Exception {
        Result bare = run(atSocket(), List.of("--help"));
        assertThat(bare.exit()).isZero();
        assertThat(bare.out()).startsWith("Usage: jasper").contains("Start Jasper once to list the plugin commands.");
        SubcommandList.write(dir.resolve("daemon").resolve("subcommands"),
            List.of(new SubcommandList.Entry("editor", "Open files in Jasper's Editor", "Editor")));
        Result listed = run(atSocket(), List.of("--help"));
        assertThat(listed.out()).contains("Commands:\n  editor      Open files in Jasper's Editor\n");
    }
}

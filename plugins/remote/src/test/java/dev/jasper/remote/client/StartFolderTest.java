package dev.jasper.remote.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import dev.jasper.remote.sftp.SftpFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StartFolderTest {
    static boolean posix() { return !System.getProperty("os.name").startsWith("Windows"); }

    /** The user shells installed here that parse the exec command; absent ones are skipped. */
    static List<String> shells() {
        var found = new java.util.ArrayList<String>();
        for (String shell : List.of("/bin/sh", "/bin/bash", "/bin/zsh", "/bin/ksh", "/bin/tcsh", "/bin/dash")) if (Files.isExecutable(Path.of(shell))) found.add(shell);
        var fish = java.util.stream.Stream.concat(
                java.util.Arrays.stream(System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)).filter(dir -> !dir.isEmpty()).map(dir -> Path.of(dir, "fish")),
                java.util.stream.Stream.of(Path.of("/opt/homebrew/bin/fish")))
            .filter(Files::isExecutable).findFirst();
        fish.ifPresent(path -> found.add(path.toString()));
        return found;
    }

    @Test void theCommandHoldsOnlyOctalEscapesOfThePath() {
        String command = StartFolder.command("/srv/it's \\ here");
        assertThat(command).startsWith("exec sh -c '").doesNotContain("it's").doesNotContain("\\\\");
        assertThat(command).endsWith("'\\0057\\0163\\0162\\0166\\0057\\0151\\0164\\0047\\0163\\0040\\0134\\0040\\0150\\0145\\0162\\0145'");
    }

    @Test void aPosixShellStartsTheLoginShellInTheFolder(@TempDir Path dir) throws Exception {
        assumeTrue(posix());
        Path folder = Files.createDirectories(dir.resolve("it's here"));
        Path fakeShell = Files.writeString(dir.resolve("fake-shell"), "#!/bin/sh\npwd -P\n");
        Files.setPosixFilePermissions(fakeShell, PosixFilePermissions.fromString("rwx------"));
        for (String shell : shells()) {
            var builder = new ProcessBuilder(shell, "-c", StartFolder.command(folder.toString())).redirectErrorStream(true);
            builder.environment().put("SHELL", fakeShell.toString());
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            // The fake login shell's pwd is the last line; a startup file (such as ~/.tcshrc) may print before it.
            assertThat(output.lines().reduce((first, second) -> second).orElse("")).as(shell).isEqualTo(folder.toRealPath().toString());
        }
    }

    @Test void aShellWithAFolderOpensAPtyExecChannel(@TempDir Path dir) throws Exception {
        try (var fixture = new SftpFixture(dir)) {
            var terms = new CopyOnWriteArrayList<String>();
            var sizes = new CopyOnWriteArrayList<String>();
            fixture.server.server.setCommandFactory((channel, command) -> new org.apache.sshd.server.command.Command() {
                @Override public void setInputStream(java.io.InputStream in) { }
                @Override public void setOutputStream(java.io.OutputStream out) { }
                @Override public void setErrorStream(java.io.OutputStream err) { }
                @Override public void setExitCallback(org.apache.sshd.server.ExitCallback callback) { }
                @Override public void start(org.apache.sshd.server.channel.ChannelSession session, org.apache.sshd.server.Environment env) {
                    fixture.server.execCommands.add(command);
                    terms.add(String.valueOf(env.getEnv().get(org.apache.sshd.server.Environment.ENV_TERM)));
                    // Only a pty-req sets the size; TERM alone also arrives as an env request.
                    sizes.add(env.getEnv().get(org.apache.sshd.server.Environment.ENV_COLUMNS) + "x" + env.getEnv().get(org.apache.sshd.server.Environment.ENV_LINES));
                }
                @Override public void destroy(org.apache.sshd.server.channel.ChannelSession session) { }
            });
            var shell = fixture.onUi(() -> fixture.connections.shell(fixture.host.id(), null, 100, 30, status -> { }, false, Optional.of("/srv/app")))
                .get(10, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (fixture.server.execCommands.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(fixture.server.execCommands).containsExactly(StartFolder.command("/srv/app"));
            assertThat(terms).containsExactly("xterm-256color");
            assertThat(sizes).as("the exec channel asks for a PTY").containsExactly("100x30");
            shell.connection().close().run();
        }
    }
}

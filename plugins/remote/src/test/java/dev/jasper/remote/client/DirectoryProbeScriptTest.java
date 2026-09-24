package dev.jasper.remote.client;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.LINUX, OS.MAC})
class DirectoryProbeScriptTest {
    @TempDir Path root;
    private final List<Process> started = new ArrayList<>();

    @AfterEach void stop() {
        for (Process process : started) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
    }

    /** Runs {@code sh file args} as the child of script(1), so it has a controlling terminal. */
    private Process underTerminal(Path file, String... args) throws IOException {
        var command = new ArrayList<String>();
        if (OS.MAC.isCurrentOs()) {
            command.addAll(List.of("script", "-q", "/dev/null", "sh", file.toString()));
            command.addAll(List.of(args));
        } else {
            var line = new StringBuilder("exec sh '").append(file).append('\'');
            for (String arg : args) line.append(" '").append(arg).append('\'');
            command.addAll(List.of("script", "-q", "-c", line.toString(), "/dev/null"));
        }
        var builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("SHELL", "/bin/sh");
        Process process = builder.start();
        started.add(process);
        return process;
    }

    private static Optional<String> probe(long ancestor) throws Exception {
        var builder = new ProcessBuilder("sh", "-s").redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("JASPER_PROBE_ANCESTOR", Long.toString(ancestor));
        Process process = builder.start();
        try (var in = process.getOutputStream()) { in.write(DirectoryProbe.script()); }
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        return DirectoryProbe.parse(output);
    }

    /** script(1) gives its child the terminal asynchronously, so probe until the expected answer or a deadline. */
    private static Optional<String> probeUntil(long ancestor, Optional<String> expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        Optional<String> last;
        do {
            last = probe(ancestor);
            if (last.equals(expected)) return last;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        return last;
    }

    private Path file(String name, String text) throws IOException { return Files.writeString(root.resolve(name), text); }

    @Test void printsTheDirectoryOfTheOnlyTerminalChild() throws Exception {
        Path folder = Files.createDirectory(root.resolve("dir with space")).toRealPath();
        Process terminal = underTerminal(file("run.sh", "cd \"$1\" && exec sleep 30\n"), folder.toString());
        assertThat(probeUntil(terminal.pid(), Optional.of(folder.toString()))).contains(folder.toString());
    }

    @Test void followsTheForegroundProcessGroupIntoAnotherDirectory() throws Exception {
        Path folder = Files.createDirectories(root.resolve("dir with space").resolve("sub")).getParent().toRealPath();
        String sub = folder.resolve("sub").toString();
        Process terminal = underTerminal(file("run.sh", "set -m\ncd \"$1\"\n(cd sub && exec sleep 30)\n:\n"), folder.toString());
        assertThat(probeUntil(terminal.pid(), Optional.of(sub))).contains(sub);
    }

    @Test void printsNothingWhenTwoChildrenHaveTerminals() throws Exception {
        Process terminal = underTerminal(file("run.sh", "sleep 30 &\nsleep 30 &\nwait\n"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        Optional<ProcessHandle> shell = Optional.empty();
        while (System.nanoTime() < deadline) {
            shell = terminal.toHandle().children().findFirst();
            if (shell.isPresent() && shell.get().children().count() == 2) break;
            Thread.sleep(100);
        }
        assertThat(shell).isPresent();
        assertThat(shell.get().children().count()).isEqualTo(2);
        Thread.sleep(300); // both children have exec'd sleep and hold the shell's terminal
        assertThat(probe(shell.get().pid())).isEmpty();
    }

    @Test void printsNothingWithoutAnAncestor() throws Exception {
        assertThat(probe(999_999_999L)).isEmpty();
    }
}

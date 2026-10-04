package dev.jasper.app.platform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LinuxNotificationsTest {
    /** A process a test finishes by hand, with what it printed. */
    private static final class FakeProcess extends Process {
        final List<String> arguments;
        private final CompletableFuture<Process> exit = new CompletableFuture<>();
        private volatile byte[] output = new byte[0];
        volatile boolean destroyed;

        FakeProcess(List<String> arguments) { this.arguments = List.copyOf(arguments); }

        void finish(String printed) {
            printing(printed);
            exit.complete(this);
        }

        /** Has printed {@code printed} already but not exited, as a notification clicked just before Jasper kills it. */
        void printing(String printed) {
            output = printed.getBytes(StandardCharsets.UTF_8);
        }

        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(output); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return exit.isDone(); }
        @Override public int exitValue() {
            if (!exit.isDone()) throw new IllegalThreadStateException("running");
            return 0;
        }
        @Override public void destroy() {
            destroyed = true;
            exit.complete(this);
        }
        @Override public CompletableFuture<Process> onExit() { return exit; }
    }

    private static final Path PROGRAM = Path.of("/usr/bin/notify-send");
    private static final Path ICON = Path.of("/home/me/.config/jasper/notification-icon.png");
    private static final String WITH_ACTIONS =
        "Usage:\n  notify-send [OPTION...] <SUMMARY> [BODY]\n  -A, --action=[NAME=]Text...\n  -w, --wait\n";
    private static final String WITHOUT_ACTIONS = "Usage:\n  notify-send [OPTION...] <SUMMARY> [BODY]\n  -u, --urgency=LEVEL\n";

    private final List<FakeProcess> started = new ArrayList<>();
    private final List<String> clicks = new ArrayList<>();
    private int located;
    private Optional<Path> program = Optional.of(PROGRAM);
    private String help = WITH_ACTIONS;
    private boolean helpFinishes = true;

    private LinuxNotifications linux() { return linux(() -> Optional.of(ICON)); }

    private LinuxNotifications linux(Supplier<Optional<Path>> icon) {
        return new LinuxNotifications(() -> {
            located++;
            return program;
        }, arguments -> {
            var process = new FakeProcess(arguments);
            started.add(process);
            if (arguments.equals(List.of(PROGRAM.toString(), "--help")) && helpFinishes) process.finish(help);
            return process;
        }, icon, clicks::add);
    }

    private static SystemNotifier.Message message(String id, String title, String body) {
        return new SystemNotifier.Message(id, SystemNotifier.Level.INFO, title, body);
    }

    /** The notifications started, without the --help probe. */
    private List<FakeProcess> shown() {
        return started.stream().filter(process -> !process.arguments.equals(List.of(PROGRAM.toString(), "--help"))).toList();
    }

    private long probes() {
        return started.stream().filter(process -> process.arguments.equals(List.of(PROGRAM.toString(), "--help"))).count();
    }

    @Test void argumentsCarryTheTextAsIsWithTheBodysMarkupEscaped() {
        help = WITHOUT_ACTIONS;
        linux().show(message("id-1", "-rf <b>build</b> & co", "a <b>bold</b> & \"quoted\" $(rm -rf ~)"));
        assertThat(shown()).singleElement().satisfies(process -> assertThat(process.arguments).containsExactly(
            PROGRAM.toString(), "--app-name=Jasper", "--icon=" + ICON, "--", "-rf <b>build</b> & co",
            "a &lt;b&gt;bold&lt;/b&gt; &amp; \"quoted\" $(rm -rf ~)"));
    }

    @Test void withoutAnIconTheArgumentIsLeftOut() {
        help = WITHOUT_ACTIONS;
        linux(Optional::empty).show(message("id-1", "make", "done"));
        assertThat(shown().getFirst().arguments).containsExactly(PROGRAM.toString(), "--app-name=Jasper", "--", "make", "done");
    }

    @Test void withActionsANotificationWaitsAndDefaultIsAClick() {
        var linux = linux();
        linux.show(message("id-1", "make", "Finished in 30s"));
        linux.show(message("id-2", "test", "Exited 1"));
        List<FakeProcess> shown = shown();
        assertThat(shown.getFirst().arguments).containsSubsequence("--action=default=Open", "--wait", "--", "make");
        shown.get(0).finish("default\n");
        shown.get(1).finish(""); // dismissed, or the server let it go
        assertThat(clicks).containsExactly("id-1");
    }

    @Test void withoutActionsNotificationsAreFireAndForgetAndTheProbeRunsOnce() {
        help = WITHOUT_ACTIONS;
        var linux = linux();
        linux.show(message("id-1", "make", ""));
        linux.show(message("id-2", "again", ""));
        assertThat(shown()).hasSize(2).allSatisfy(process -> assertThat(process.arguments).doesNotContain("--wait"));
        shown().getFirst().finish("default");
        assertThat(clicks).isEmpty();
        assertThat(probes()).isEqualTo(1);
    }

    @Test void atMostEightWaitAndTheOldestIsKilled() {
        var linux = linux();
        for (int i = 0; i < LinuxNotifications.MAX_WAITING; i++) linux.show(message("id-" + i, "n" + i, ""));
        shown().getFirst().printing("default\n"); // clicked, but crowded out before its exit was seen
        linux.show(message("id-" + LinuxNotifications.MAX_WAITING, "n" + LinuxNotifications.MAX_WAITING, ""));
        List<FakeProcess> shown = shown();
        assertThat(shown).hasSize(LinuxNotifications.MAX_WAITING + 1);
        assertThat(shown.getFirst().destroyed).isTrue();
        assertThat(shown.subList(1, shown.size())).noneMatch(process -> process.destroyed);
        assertThat(clicks).as("a killed process is no click").isEmpty();
    }

    @Test void readingANotificationOrShuttingDownKillsItsWaitingProcess() {
        var linux = linux();
        linux.show(message("id-1", "first", ""));
        linux.show(message("id-2", "second", ""));
        linux.show(message("id-3", "third", ""));
        shown().get(0).printing("default\n"); // clicked, but killed before its exit was seen
        linux.withdraw("id-1");
        assertThat(shown().get(0).destroyed).isTrue();
        assertThat(shown().get(1).destroyed).isFalse();
        assertThat(clicks).as("a withdrawn notification is no click").isEmpty();
        shown().get(1).printing("default\n");
        shown().get(2).printing("default\n");
        linux.withdrawAll();
        assertThat(shown().get(1).destroyed).isTrue();
        assertThat(shown().get(2).destroyed).isTrue();
        assertThat(clicks).as("withdrawing all is no click").isEmpty();
        linux.show(message("id-4", "fourth", ""));
        shown().get(3).printing("default\n");
        linux.close();
        assertThat(shown().get(3).destroyed).isTrue();
        assertThat(clicks).as("shutting down is no click").isEmpty();
        linux.show(message("id-5", "after", ""));
        assertThat(shown()).hasSize(4);
        shown().get(3).finish("default");
        assertThat(clicks).isEmpty();
    }

    @Test void withoutNotifySendNothingStartsAndThePathIsSearchedOnce() {
        program = Optional.empty();
        var linux = linux();
        linux.show(message("id-1", "first", ""));
        linux.show(message("id-2", "second", ""));
        assertThat(started).isEmpty();
        assertThat(located).isEqualTo(1);
    }

    @Test void aHelpThatNeverFinishesMeansNoActions() {
        helpFinishes = false;
        linux().show(message("id-1", "make", ""));
        FakeProcess probe = started.getFirst();
        assertThat(probe.arguments).containsExactly(PROGRAM.toString(), "--help");
        assertThat(probe.destroyed).as("the hung probe is killed").isTrue();
        assertThat(shown().getFirst().arguments).doesNotContain("--wait");
    }

    @Test void aProgramThatCannotStartFailsTheCallForTheNotifierToLog() {
        var linux = new LinuxNotifications(() -> Optional.of(PROGRAM), arguments -> {
            throw new UncheckedIOException(new IOException("cannot run"));
        }, Optional::empty, clicks::add);
        assertThatThrownBy(() -> linux.show(message("id-1", "make", ""))).isInstanceOf(UncheckedIOException.class);
    }

    @Test void notifySendIsFoundOnThePathAsAnExecutableFile(@TempDir Path directory) throws IOException {
        Path empty = Files.createDirectories(directory.resolve("empty"));
        Path bin = Files.createDirectories(directory.resolve("bin"));
        Path tool = Files.writeString(bin.resolve("notify-send"), "#!/bin/sh\n");
        assumeTrue(tool.toFile().setExecutable(true));
        Path other = Files.createDirectories(directory.resolve("other"));
        Files.writeString(other.resolve("notify-send"), "not executable");
        String path = String.join(java.io.File.pathSeparator, "", empty.toString(), bin.toString(), other.toString());
        assertThat(LinuxNotifications.onPath(path)).contains(tool);
        assertThat(LinuxNotifications.onPath(empty.toString())).isEmpty();
        assertThat(LinuxNotifications.onPath(null)).isEmpty();
        // Windows reports every readable file as executable; elsewhere a plain file is passed over.
        if (!System.getProperty("os.name", "").startsWith("Windows"))
            assertThat(LinuxNotifications.onPath(other.toString())).isEmpty();
    }

    @Test void theIconIsWrittenWhereNotifySendCanReadIt(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("config/jasper/notification-icon.png");
        assertThat(LinuxNotifications.iconIn(target)).contains(target);
        assertThat(Files.readAllBytes(target)).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(LinuxNotifications.iconIn(target)).as("again, over the old one").contains(target);
    }

    @Test void anIconThatCannotBeWrittenIsLeftOut(@TempDir Path directory) throws IOException {
        Path blocker = Files.writeString(directory.resolve("file"), "");
        assertThat(LinuxNotifications.iconIn(blocker.resolve("notification-icon.png"))).isEmpty();
    }
}

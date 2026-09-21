package dev.jasper.terminal.internal.shell;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
class ShellCommandTrackerTest {
@Test void repeatedPromptDoesNotFinishACommandBeforeItsStatus() {
    AtomicLong clock = new AtomicLong(0);
    List<CompletedCommand> done = new ArrayList<>();
    AtomicBoolean firstPrompt = new AtomicBoolean(true);
    ShellCommandTracker tracker = new ShellCommandTracker(clock::get,
        () -> new CommandLocation(0,0), at -> "echo test",
        () -> firstPrompt.getAndSet(false), path -> {}, text -> {}, done::add,
        () -> {}, () -> {}, new DirectoryProvenance(true, java.util.Set.of("workstation")), location -> {});
    tracker.accept(List.of("jasper","mark","A"));
    tracker.accept(List.of("jasper","mark","B"));
    tracker.accept(List.of("jasper","mark","C"));
    tracker.accept(List.of("jasper","mark","A"));
    assertThat(done).isEmpty();
    clock.set(25);
    tracker.accept(List.of("jasper","mark","D","7"));
    assertThat(done).hasSize(1);
    assertThat(done.getFirst().status()).hasValue(7);
    assertThat(done.getFirst().duration()).isEqualTo(Duration.ofNanos(25));
}

@Test void aRemoteReportClearsTheLocalDirectoryAndALocalOneClearsTheRemote() {
    List<String> events = new ArrayList<>();
    List<CompletedCommand> done = new ArrayList<>();
    ShellCommandTracker tracker = new ShellCommandTracker(() -> 0L, () -> new CommandLocation(0, 0), at -> "make",
        () -> true, path -> events.add("local " + path), text -> {}, done::add, () -> {}, () -> {},
        new DirectoryProvenance(true, java.util.Set.of("workstation")), location -> events.add("remote " + location.host() + ":" + location.path()));
    tracker.accept(List.of("jasper", "cwd", "file://workstation/home/me"));
    assertThat(tracker.workingDirectory()).contains(java.nio.file.Path.of("/home/me"));
    assertThat(tracker.remoteDirectory()).isEmpty();

    tracker.accept(List.of("jasper", "cwd", "file://build-host/srv/app"));
    assertThat(tracker.workingDirectory()).as("a remote path must never look local").isEmpty();
    assertThat(tracker.remoteDirectory()).contains(new RemoteLocation("build-host", "/srv/app"));
    tracker.accept(List.of("jasper", "mark", "A"));
    tracker.accept(List.of("jasper", "mark", "B"));
    tracker.accept(List.of("jasper", "mark", "C"));
    tracker.accept(List.of("jasper", "mark", "D", "0"));
    assertThat(done).singleElement().satisfies(command -> assertThat(command.directory()).isEmpty());

    tracker.accept(List.of("jasper", "cwd", "not a uri"));
    assertThat(tracker.remoteDirectory()).as("an unparseable report changes nothing").isPresent();
    tracker.accept(List.of("jasper", "cwd", "file:///tmp"));
    assertThat(tracker.workingDirectory()).contains(java.nio.file.Path.of("/tmp"));
    assertThat(tracker.remoteDirectory()).isEmpty();
    assertThat(events).containsExactly("local /home/me", "remote build-host:/srv/app", "local /tmp");
}
}

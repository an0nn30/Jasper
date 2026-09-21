package dev.jasper.terminal;
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
        () -> {}, () -> {});
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
}

package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InvocationTest {
    private static Invocation call(StringBuilder out, StringBuilder err) {
        return new Invocation(List.of("editor"), Path.of("/"), Optional.empty(), out::append, err::append);
    }

    @Test void outputReachesItsStreamsUntilTheCallFinishes() {
        var out = new StringBuilder();
        var err = new StringBuilder();
        Invocation invocation = call(out, err);
        invocation.out("one");
        invocation.err("two");
        invocation.finish();
        invocation.out("late");
        invocation.err("late");
        assertThat(out).hasToString("one");
        assertThat(err).hasToString("two");
    }

    @Test void cancellingRunsEachListenerOnceAndDropsLaterOutput() {
        var out = new StringBuilder();
        var runs = new AtomicInteger();
        Invocation invocation = call(out, new StringBuilder());
        invocation.onCancelled(runs::incrementAndGet);
        invocation.cancel();
        invocation.cancel();
        invocation.onCancelled(runs::incrementAndGet);
        invocation.out("late");
        assertThat(runs).as("the first once, the late one at once").hasValue(2);
        assertThat(invocation.cancelled()).isTrue();
        assertThat(out).isEmpty();
    }

    @Test void aFinishedCallIsNeverCancelled() {
        var runs = new AtomicInteger();
        Invocation invocation = call(new StringBuilder(), new StringBuilder());
        invocation.onCancelled(runs::incrementAndGet);
        invocation.finish();
        invocation.cancel();
        assertThat(runs).hasValue(0);
        assertThat(invocation.cancelled()).isFalse();
    }

    @Test void aThrowingListenerDoesNotStopTheOthers() {
        var runs = new AtomicInteger();
        Invocation invocation = call(new StringBuilder(), new StringBuilder());
        invocation.onCancelled(() -> { throw new IllegalStateException("boom"); });
        invocation.onCancelled(runs::incrementAndGet);
        invocation.cancel();
        assertThat(runs).hasValue(1);
    }
}

package dev.jasper.app.platform;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NativeNotifierTest {
    @Test void sendsLiteralUnicodeTextOffTheCallingThread() throws Exception {
        String title = "echo \"hi\"; say 'boo'\n\\ $() " + (char) 0xD83D + (char) 0xDE80;
        String detail = "Finished\0in 1m";
        var delivered = new CountDownLatch(1);
        var values = new CopyOnWriteArrayList<String>();
        var caller = Thread.currentThread();
        var threads = new CopyOnWriteArrayList<Thread>();
        try (var notifier = new NativeNotifier(true, (t, d) -> {
            values.addAll(List.of(t, d));
            threads.add(Thread.currentThread());
            delivered.countDown();
        })) {
            notifier.send(title, detail);
            assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(values).containsExactly(title, detail);
            assertThat(threads).noneMatch(thread -> thread == caller);
        }
    }

    @Test void unsupportedAndClosedNotifiersSendNothing() {
        var values = new CopyOnWriteArrayList<String>();
        try (var notifier = new NativeNotifier(false, (t, d) -> values.add(t))) {
            notifier.send("build", "Finished");
        }
        var notifier = new NativeNotifier(true, (t, d) -> values.add(t));
        notifier.close();
        notifier.send("build", "Finished");
        assertThat(values).isEmpty();
    }

    @Test void aNativeLinkFailureDoesNotKillSubsequentDelivery() throws Exception {
        var delivered = new CountDownLatch(1);
        try (var notifier = new NativeNotifier(true, (t, d) -> {
            if (t.equals("first")) throw new UnsatisfiedLinkError("test missing library");
            delivered.countDown();
        })) {
            notifier.send("first", "Finished");
            notifier.send("second", "Finished");
            assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}

package dev.jasper.app.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NativeNotifierTest {
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "jasper.testNotificationLibrary", matches = ".+")
    @SuppressWarnings("restricted")
    void realNativeBridgeLoadsAndDeclinesAnUnbundledJvm() throws Exception {
        System.load(System.getProperty("jasper.testNotificationLibrary"));
        var post = MacNotifications.class.getDeclaredMethod("post", String.class, String.class);
        post.setAccessible(true);
        // Exercises real JNI linkage without a permission prompt or notification side effect.
        post.invoke(null, "build", "Finished");
    }

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

    @Test void nativeLibraryBelongsToTheRunningBundleIncludingRenamedApps() {
        assertThat(MacNotifications.libraryPath("/Applications/Jasper Preview.app/Contents/MacOS/Jasper"))
            .isEqualTo(Path.of("/Applications/Jasper Preview.app/Contents/app/libjasper-notifications.dylib")
                .toAbsolutePath());
    }

    @Test void unpackagedLaunchesDoNotUseAnotherApplicationsIdentity() {
        assertThat(MacNotifications.libraryPath(null)).isNull();
        assertThat(MacNotifications.libraryPath(" ")).isNull();
        assertThat(MacNotifications.libraryPath("/usr/bin/java")).isNull();
        assertThat(MacNotifications.libraryPath("/tmp/Contents/MacOS/Jasper")).isNull();
        assertThat(MacNotifications.libraryPath("/Contents/MacOS/Jasper")).isNull();
    }
}

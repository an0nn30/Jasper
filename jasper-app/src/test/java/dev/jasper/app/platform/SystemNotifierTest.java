package dev.jasper.app.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SystemNotifierTest {
    private final List<String> calls = new ArrayList<>();
    private Consumer<String> clicked;

    private SystemNotifier notifier() {
        return SystemNotifier.using(Runnable::run, clicks -> {
            clicked = clicks;
            return new SystemNotifier.Backend(
                message -> calls.add("show " + message.id() + " " + message.level() + " " + message.title() + "|" + message.body()),
                id -> calls.add("withdraw " + id), () -> calls.add("withdraw all"), () -> calls.add("focused"),
                () -> calls.add("close"));
        });
    }

    @Test void aNotificationReachesTheBackendWithItsIdLevelAndText() {
        UUID id = UUID.randomUUID();
        notifier().send(id, SystemNotifier.Level.WARNING, "make", "Exited 1 · 2m 03s");
        assertThat(calls).containsExactly("show " + id + " WARNING make|Exited 1 · 2m 03s");
    }

    @Test void withdrawingAndFocusAreForwarded() {
        var notifier = notifier();
        UUID id = UUID.randomUUID();
        notifier.withdraw(id);
        notifier.withdrawAll();
        notifier.focused();
        assertThat(calls).containsExactly("withdraw " + id, "withdraw all", "focused");
    }

    @Test void theTitleIsOneLineOfPlainText() {
        String hostile = "make" + (char) 0x1B + "[31m\n\tdone" + (char) 0x202E + "gpj.exe" + (char) 0x0000 + (char) 0xD83D;
        assertThat(SystemNotifier.title(hostile)).isEqualTo("make[31m  donegpj.exe");
        assertThat(SystemNotifier.title(" \n" + (char) 0x200F + "\t")).as("nothing left").isEqualTo("Jasper");
        assertThat(SystemNotifier.title(null)).isEqualTo("Jasper");
    }

    @Test void theBodyKeepsItsLinesAndDropsTheRest() {
        assertThat(SystemNotifier.body("first\r\nsecond\rthird" + (char) 0x07 + (char) 0x2066 + "\tend"))
            .isEqualTo("first\nsecond\nthird end");
        assertThat(SystemNotifier.body(null)).isEmpty();
    }

    @Test void longTextIsCutWithAnEllipsisAndNeverSplitsACharacter() {
        String rocket = new String(Character.toChars(0x1F680));
        String title = "a".repeat(SystemNotifier.TITLE_LIMIT - 2) + rocket + rocket + "tail";
        String cut = SystemNotifier.title(title);
        assertThat(cut.codePointCount(0, cut.length())).isEqualTo(SystemNotifier.TITLE_LIMIT);
        assertThat(cut).isEqualTo("a".repeat(SystemNotifier.TITLE_LIMIT - 2) + rocket + "…");
        assertThat(SystemNotifier.body("b".repeat(5_000))).hasSize(SystemNotifier.BODY_LIMIT).endsWith("…");
        assertThat(SystemNotifier.title("a".repeat(SystemNotifier.TITLE_LIMIT))).as("exactly at the limit").doesNotContain("…");
    }

    @Test void aBackendFailureIsLoggedOnceAndLaterNotificationsStillGo() {
        var shown = new ArrayList<String>();
        var notifier = SystemNotifier.using(Runnable::run, clicks -> new SystemNotifier.Backend(message -> {
            if (message.title().equals("first")) throw new UnsatisfiedLinkError("test missing library");
            if (message.title().equals("second")) throw new IllegalStateException("broken");
            shown.add(message.title());
        }, id -> { }, () -> { }, () -> { }, () -> { }));
        notifier.send(UUID.randomUUID(), SystemNotifier.Level.INFO, "first", "");
        notifier.send(UUID.randomUUID(), SystemNotifier.Level.INFO, "second", "");
        notifier.send(UUID.randomUUID(), SystemNotifier.Level.INFO, "third", "");
        assertThat(shown).containsExactly("third");
    }

    @Test void closingReleasesTheBackendOnceAndShowsNothingAfterwards() {
        var notifier = notifier();
        notifier.close();
        notifier.close();
        notifier.send(UUID.randomUUID(), SystemNotifier.Level.INFO, "late", "");
        notifier.withdrawAll();
        notifier.focused();
        assertThat(calls).containsExactly("close");
    }

    @Test void aClickReachesTheHandlerOnTheEdtAsTheNotificationsId() throws Exception {
        var notifier = notifier();
        var heard = new CopyOnWriteArrayList<UUID>();
        var onEdt = new CopyOnWriteArrayList<Boolean>();
        notifier.onClick(id -> {
            heard.add(id);
            onEdt.add(SwingUtilities.isEventDispatchThread());
        });
        UUID id = UUID.randomUUID();
        clicked.accept(id.toString());
        clicked.accept("not one of Jasper's");
        clicked.accept(null);
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(heard).containsExactly(id);
        assertThat(onEdt).containsExactly(true);
        notifier.close();
        clicked.accept(UUID.randomUUID().toString());
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(heard).containsExactly(id);
    }

    @Test void theWorkerRunsOffTheCallingThreadAsADaemon() throws Exception {
        var delivered = new CountDownLatch(1);
        var threads = new CopyOnWriteArrayList<Thread>();
        var notifier = SystemNotifier.onWorker(clicks -> new SystemNotifier.Backend(message -> {
            threads.add(Thread.currentThread());
            delivered.countDown();
        }, id -> { }, () -> { }, () -> { }, () -> { }));
        notifier.send(UUID.randomUUID(), SystemNotifier.Level.INFO, "build", "Finished");
        assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(threads).singleElement().satisfies(thread -> {
            assertThat(thread).isNotSameAs(Thread.currentThread());
            assertThat(thread.isDaemon()).isTrue();
            assertThat(thread.getName()).isEqualTo("jasper-system-notifications");
        });
        notifier.close();
    }

    @Test void anUnpackagedMacAndUnknownSystemsShowNothing(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) {
        var dirs = new AppDirs(root, root.resolve("config.toml"), root.resolve("logs"));
        // A test JVM has no jpackage.app-path, so no Jasper.app bundle to speak for.
        assertThat(SystemNotifier.forThisPlatform("Mac OS X", dirs).silent()).isTrue();
        assertThat(SystemNotifier.forThisPlatform("FreeBSD", dirs).silent()).isTrue();
        assertThat(SystemNotifier.forThisPlatform(null, dirs).silent()).isTrue();
        assertThat(SystemNotifier.none().silent()).isTrue();
        assertThat(notifier().silent()).isFalse();
    }

    @Test void linuxHasNotifySend(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) {
        var dirs = new AppDirs(root, root.resolve("config.toml"), root.resolve("logs"));
        // Nothing is looked up or started until the first notification.
        try (var linux = SystemNotifier.forThisPlatform("Linux", dirs)) {
            assertThat(linux.silent()).isFalse();
        }
    }
}
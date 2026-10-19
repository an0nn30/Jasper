package dev.jasper.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.residency.InstanceLock;
import dev.jasper.app.residency.InstanceLockProbe;
import dev.jasper.app.testsupport.ChildJvm;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Spec 17, single instance, with real child JVMs. */
@Timeout(240)
class SingleInstanceProcessTest {
    @TempDir Path home;

    private ChildJvm launch(String handoffMillis, String... arguments) throws Exception {
        return ChildJvm.start(LaunchProbe.class, InstanceLock.class,
            Stream.concat(Stream.of(home.toString(), handoffMillis), Stream.of(arguments)).toArray(String[]::new));
    }

    /** Takes the lock, which records its process id, but binds nothing: an owner that never answers. */
    private ChildJvm silentOwner() throws Exception {
        return ChildJvm.start(InstanceLockProbe.class, InstanceLock.class, "hold", home.resolve("daemon").resolve("instance.lock").toString());
    }

    @Test void racingColdStartsEndWithOneOwnerAndEveryOtherLaunchHandingOff() throws Exception {
        int launches = 6;
        var children = new ChildJvm[launches];
        try {
            for (int i = 0; i < launches; i++) children[i] = launch("10000");
            ChildJvm owner = null;
            int owners = 0;
            for (ChildJvm child : children) {
                String first = child.line(90);
                if ("owner".equals(first)) {
                    owners++;
                    owner = child;
                } else {
                    assertThat(first).as("every other launch hands off").isEqualTo("done 0");
                    assertThat(child.exit(30)).isZero();
                }
            }
            assertThat(owners).as("exactly one owner").isEqualTo(1);
            for (int i = 1; i < launches; i++) assertThat(owner.line(30)).isEqualTo("handoff RAISE 0");
            assertThat(owner.alive()).isTrue();
        } finally {
            for (ChildJvm child : children) if (child != null) child.close();
        }
    }

    @Test void aKilledOwnerLeavesNothingThatStopsTheNextLaunch() throws Exception {
        try (ChildJvm owner = launch("10000")) {
            assertThat(owner.line(90)).isEqualTo("owner");
            owner.kill();
        }
        try (ChildJvm next = launch("10000")) {
            assertThat(next.line(90)).as("the lock was released and the stale socket replaced").isEqualTo("owner");
        }
    }

    @Test void anUnresponsiveOwnerYieldsTheErrorAndNeverASecondInstance() throws Exception {
        try (ChildJvm silent = silentOwner()) {
            assertThat(silent.line(90)).isEqualTo("owner");
            try (ChildJvm second = launch("1500")) {
                assertThat(second.line(90)).isEqualTo("failed Jasper is already running but not responding. Quit it (process "
                    + silent.pid() + ") and try again.");
                assertThat(second.line(30)).isEqualTo("done 1");
                assertThat(second.exit(30)).isEqualTo(1);
            }
            assertThat(home.resolve("daemon").resolve("socket")).as("the second launch never bound an endpoint").doesNotExist();
        }
    }

    @Test void theReplacementWaitsForTheLock() throws Exception {
        try (ChildJvm old = silentOwner()) {
            assertThat(old.line(90)).isEqualTo("owner");
            try (ChildJvm replacement = launch("10000", "--replacing", Long.toString(old.pid()))) {
                assertThat(replacement.line(3)).as("it waits while the old process lives").isNull();
                old.finish();
                assertThat(old.exit(30)).isZero();
                assertThat(replacement.line(60)).isEqualTo("owner");
            }
        }
    }
}

package dev.jasper.app.residency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.ChildJvm;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.1 and 17: the instance lock in this JVM and across real child JVMs. */
@Timeout(120)
class InstanceLockTest {
    @TempDir Path dir;

    private Path file() { return dir.resolve("daemon").resolve("instance.lock"); }

    private ChildJvm probe(String... arguments) throws Exception {
        return ChildJvm.start(InstanceLockProbe.class, InstanceLock.class, arguments);
    }

    @Test void theLockIsTakenOnceInAProcessInAnOwnerOnlyFolderAndReleasedByClose() throws Exception {
        try (InstanceLock lock = InstanceLock.tryAcquire(file()).orElseThrow()) {
            assertThat(lock.file()).isEqualTo(file());
            assertThat(InstanceLock.tryAcquire(file())).as("held here already").isEmpty();
            assertThat(InstanceLock.tryAcquire(dir.resolve("daemon/../daemon/instance.lock"))).as("by any spelling").isEmpty();
            if (dir.getFileSystem().supportedFileAttributeViews().contains("posix"))
                assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file().getParent()))).isEqualTo("rwx------");
        }
        try (InstanceLock again = InstanceLock.tryAcquire(file()).orElseThrow()) {
            assertThat(again.file()).as("released by close").isEqualTo(file());
            again.close();
            again.close();
        }
    }

    @Test void askingAgainHereNeverDropsTheLockForAnotherProcess() throws Exception {
        // Opening and closing a second channel on the file would release this process's POSIX lock (plan Ruling 25).
        try (InstanceLock lock = InstanceLock.tryAcquire(file()).orElseThrow()) {
            assertThat(InstanceLock.tryAcquire(file())).isEmpty();
            try (ChildJvm other = probe("hold", file().toString())) {
                assertThat(other.line(60)).as("still held by this process").isEqualTo("held");
            }
        }
    }

    @Test void theOwnerRecordsItsProcessIdBesideTheLock() throws Exception {
        assertThat(InstanceLock.ownerPid(file())).as("no owner yet").isEqualTo(-1);
        try (InstanceLock lock = InstanceLock.tryAcquire(file()).orElseThrow()) {
            assertThat(file().resolveSibling("instance.pid")).exists();
            assertThat(InstanceLock.ownerPid(file())).isEqualTo(ProcessHandle.current().pid());
        }
        assertThat(InstanceLock.ownerPid(file())).as("removed by close").isEqualTo(-1);
        Files.writeString(file().resolveSibling("instance.pid"), "not a number\n");
        assertThat(InstanceLock.ownerPid(file())).isEqualTo(-1);
    }

    @Test void aSecondProcessCannotTakeTheLockAndReadsItsOwner() throws Exception {
        try (ChildJvm owner = probe("hold", file().toString())) {
            assertThat(owner.line(60)).isEqualTo("owner");
            assertThat(InstanceLock.ownerPid(file())).isEqualTo(owner.pid());
            try (ChildJvm second = probe("hold", file().toString())) {
                assertThat(second.line(60)).isEqualTo("held");
                assertThat(second.exit(60)).isZero();
            }
            assertThat(InstanceLock.tryAcquire(file())).as("nor can this process").isEmpty();
        }
    }

    @Test void aKilledOwnerReleasesTheLock() throws Exception {
        try (ChildJvm owner = probe("hold", file().toString())) {
            assertThat(owner.line(60)).isEqualTo("owner");
            owner.kill();
            assertThat(owner.alive()).isFalse();
        }
        // Windows releases a dead process's locks asynchronously, so the next process waits a moment for it.
        try (ChildJvm next = probe("await", file().toString(), "10000")) {
            assertThat(next.line(60)).as("the operating system released it").isEqualTo("owner");
        }
    }

    @Test void closeWaitsForAnAskInProgressAndTheLockStaysHeldUntilItReleases() throws Exception {
        // close and tryAcquire share the class monitor: an ask that sees the lock gone from the held set must also find it
        // released, never an open channel on a file this process still holds (plan Ruling 25).
        InstanceLock lock = InstanceLock.tryAcquire(file()).orElseThrow();
        Thread closer = Thread.ofPlatform().unstarted(lock::close);
        try {
            synchronized (InstanceLock.class) {
                closer.start();
                long deadline = System.nanoTime() + 10_000_000_000L;
                while (closer.getState() != Thread.State.BLOCKED && closer.isAlive() && System.nanoTime() < deadline) Thread.sleep(5);
                assertThat(closer.getState()).as("close waits for the class monitor").isEqualTo(Thread.State.BLOCKED);
                assertThat(InstanceLock.tryAcquire(file())).as("still held while close waits").isEmpty();
                try (ChildJvm other = probe("hold", file().toString())) {
                    assertThat(other.line(60)).as("still held for other processes").isEqualTo("held");
                }
            }
            closer.join(10_000);
            assertThat(closer.isAlive()).isFalse();
            try (InstanceLock again = InstanceLock.tryAcquire(file()).orElseThrow()) {
                assertThat(again.file()).as("released once close ran").isEqualTo(file());
            }
        } finally {
            closer.join(10_000);
            lock.close();
        }
    }

    @Test void closingOnAnotherThreadNeverMakesAnAskHereFail() throws Exception {
        for (int round = 0; round < 50; round++) {
            InstanceLock lock = InstanceLock.tryAcquire(file()).orElseThrow();
            Thread closer = Thread.ofPlatform().start(lock::close);
            long deadline = System.nanoTime() + 5_000_000_000L;
            Optional<InstanceLock> taken;
            do {
                taken = InstanceLock.tryAcquire(file());
                assertThat(taken.isPresent() || System.nanoTime() < deadline).as("close finished within 5 s in round " + round).isTrue();
            } while (taken.isEmpty());
            closer.join();
            taken.get().close();
        }
    }

    @Test void aWaiterTakesTheLockOnceItsOwnerExits() throws Exception {
        try (ChildJvm owner = probe("hold", file().toString())) {
            assertThat(owner.line(60)).isEqualTo("owner");
            try (ChildJvm waiter = probe("await", file().toString(), "60000")) {
                assertThat(waiter.line(2)).as("still waiting").isNull();
                owner.finish();
                assertThat(owner.exit(60)).isZero();
                assertThat(waiter.line(60)).isEqualTo("owner");
            }
        }
    }
}

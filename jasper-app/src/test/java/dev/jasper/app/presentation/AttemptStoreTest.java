package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 4.6: the record a look leaves when it starts, and how it is read back. */
class AttemptStoreTest {
    static final String ID = "dev.jasper.enterprise", DIGEST = "9f2c4e0b7a1d3c5e8f6a0b2d4c6e8f0a1b3d5c7e9f1a2b4c6d8e0f1a3b5c7d9e",
        BUILD = "/Applications/Jasper.app/Contents/app/jasper-app.jar@1760500000000";
    @TempDir Path home;

    @Test void aTriedLookIsWrittenAsTheSpecShowsAndReadBack() throws Exception {
        var store = new AttemptStore(home);
        assertThat(store.read()).isEqualTo(AttemptStore.Record.EMPTY);
        assertThat(store.update(ID, attempt -> attempt.tried("0.1.0", DIGEST, BUILD, AttemptStore.State.PENDING), AttemptStore.WAIT)).isTrue();
        assertThat(Files.readString(home.resolve("presentation-attempt.toml"))).isEqualTo("""
            version = 1

            [providers."dev.jasper.enterprise"]
            version = "0.1.0"
            digest = "9f2c4e0b7a1d3c5e8f6a0b2d4c6e8f0a1b3d5c7e9f1a2b4c6d8e0f1a3b5c7d9e"
            build = "/Applications/Jasper.app/Contents/app/jasper-app.jar@1760500000000"
            state = "pending"
            """);
        var attempt = store.read().of(ID);
        assertThat(attempt).isEqualTo(new AttemptStore.Attempt("0.1.0", DIGEST, BUILD, Optional.of(AttemptStore.State.PENDING), ""));
        assertThat(store.read().of("dev.other")).isEqualTo(AttemptStore.Attempt.NONE);
        store.update(ID, previous -> previous.announcing("abc"), AttemptStore.WAIT);
        assertThat(store.read().of(ID).announced()).isEqualTo("abc");
        store.update(ID, AttemptStore.Attempt::cleared, AttemptStore.WAIT);
        assertThat(store.read().of(ID).state()).isEmpty();
        store.update(ID, previous -> AttemptStore.Attempt.NONE, AttemptStore.WAIT);
        assertThat(Files.readString(home.resolve("presentation-attempt.toml"))).as("an empty attempt drops its table").isEqualTo("version = 1\n");
    }

    @Test void onlyAPendingOrFailedAttemptForTheSameLookAndBuildBlocks() {
        var pending = new AttemptStore.Attempt("0.1.0", DIGEST, BUILD, Optional.of(AttemptStore.State.PENDING), "");
        assertThat(pending.blocks(DIGEST, BUILD)).isTrue();
        assertThat(pending.tried("0.1.0", DIGEST, BUILD, AttemptStore.State.FAILED).blocks(DIGEST, BUILD)).isTrue();
        assertThat(pending.tried("0.1.0", DIGEST, BUILD, AttemptStore.State.PROVEN).blocks(DIGEST, BUILD)).isFalse();
        assertThat(pending.blocks("another digest", BUILD)).as("a changed kit gets a fresh try").isFalse();
        assertThat(pending.blocks(DIGEST, "another build")).as("a new build gets a fresh try").isFalse();
        assertThat(pending.cleared().blocks(DIGEST, BUILD)).isFalse();
    }

    @Test void unreadableContentIsAbsent() throws Exception {
        var store = new AttemptStore(home);
        for (String text : java.util.List.of("version = 2\n", "not toml [", "version = 1\n[providers.\"a.b\"]\nstate = \"maybe\"\n",
                "version = 1\nproviders = 3\n", "version = 1\n[providers.\"a.b\"]\ndigest = 4\n", "x = " + "[".repeat(10_000))) {
            Files.writeString(home.resolve("presentation-attempt.toml"), text);
            assertThat(store.read()).as(text).isEqualTo(AttemptStore.Record.EMPTY);
        }
    }

    @Test void aBusyLockFailsTheWriteWithinItsWaitAndTheLaunchGoesOn() throws Exception {
        var store = new AttemptStore(home);
        try (var channel = FileChannel.open(home.resolve("presentation-attempt.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var held = channel.lock()) {
            long began = System.nanoTime();
            assertThat(store.update(ID, attempt -> attempt.tried("0.1.0", DIGEST, BUILD, AttemptStore.State.PENDING), Duration.ofMillis(100))).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofSeconds(2));
        }
        assertThat(store.read()).isEqualTo(AttemptStore.Record.EMPTY);
    }

    @Test void theProcessHandleMarksItsLookAndKeepsAFailureOverALaterPaint() {
        var store = new AttemptStore(home);
        var attempt = new PresentationAttempt(store, ID, "0.1.0", DIGEST, BUILD);
        attempt.pending();
        assertThat(store.read().of(ID).state()).contains(AttemptStore.State.PENDING);
        attempt.exiting();
        assertThat(store.read().of(ID).state()).as("an exit that was not orderly leaves pending").contains(AttemptStore.State.PENDING);
        attempt.orderlyQuit();
        attempt.exiting();
        assertThat(store.read().of(ID).state()).as("an orderly quit before proof clears pending").isEmpty();
        attempt.pending();
        attempt.proven();
        assertThat(store.read().of(ID).state()).contains(AttemptStore.State.PROVEN);
        var failing = new PresentationAttempt(store, ID, "0.1.0", DIGEST, BUILD);
        failing.pending();
        failing.failed();
        failing.proven();
        assertThat(store.read().of(ID).state()).as("a provider that failed stays failed when the window paints").contains(AttemptStore.State.FAILED);
        failing.pending();
        failing.orderlyQuit();
        failing.exiting();
        assertThat(store.read().of(ID).state()).as("a later window and an orderly quit keep the failure").contains(AttemptStore.State.FAILED);
        failing.clear();
        assertThat(store.read().of(ID).state()).as("Try Again").isEmpty();
        assertThat(PresentationAttempt.announce(store, ID, "d1")).isTrue();
        assertThat(PresentationAttempt.announce(store, ID, "d1")).as("once per digest").isFalse();
        assertThat(PresentationAttempt.announce(store, ID, "d2")).isTrue();
        PresentationAttempt.NONE.pending();
        PresentationAttempt.NONE.proven();
        assertThat(PresentationAttempt.NONE.records()).isFalse();
    }
}

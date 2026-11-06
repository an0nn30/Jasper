package dev.jasper.browser.pages;

import dev.jasper.browser.store.CapturedLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether the onboarding has been finished: kept in memory for Chromium's IO thread, which asks, and in one marker file
 * in the plugin's data folder, which only the background executor reads and writes.
 */
class OnboardingTest {
    @TempDir Path folder;

    private Path marker() {
        return folder.resolve("onboarding-finished");
    }

    @Test void aFreshFolderIsNotFinished() {
        var onboarding = new Onboarding(marker(), Runnable::run);
        onboarding.load();
        assertThat(onboarding.finished()).isFalse();
    }

    @Test void finishWritesTheMarkerAndANewInstanceReadsIt() {
        var onboarding = new Onboarding(marker(), Runnable::run);
        onboarding.finish();
        assertThat(onboarding.finished()).isTrue();
        assertThat(marker()).exists();
        var next = new Onboarding(marker(), Runnable::run);
        assertThat(next.finished()).as("nothing is read before load").isFalse();
        next.load();
        assertThat(next.finished()).isTrue();
    }

    /** The marker's folder may not exist yet. */
    @Test void finishMakesTheFolderOfTheMarker() {
        Path nested = folder.resolve("data").resolve("onboarding-finished");
        new Onboarding(nested, Runnable::run).finish();
        assertThat(nested).exists();
    }

    /** Nothing reads or writes a file on the asking thread: both wait for the executor, and the answer comes from memory. */
    @Test void theMarkerIsReadAndWrittenOnlyByTheExecutor() throws IOException {
        Files.createFile(marker());
        List<Runnable> queued = new ArrayList<>();
        var onboarding = new Onboarding(marker(), queued::add);
        onboarding.load();
        assertThat(onboarding.finished()).as("the read has not run: not finished").isFalse();
        queued.removeFirst().run();
        assertThat(onboarding.finished()).isTrue();

        Path other = folder.resolve("other");
        var finishing = new Onboarding(other, queued::add);
        finishing.finish();
        assertThat(finishing.finished()).as("finished at once").isTrue();
        assertThat(other).as("the write has not run").doesNotExist();
        queued.removeFirst().run();
        assertThat(other).exists();
    }

    /** A request asked before the read has run is answered "not finished", and the same request after it, "finished". */
    @Test void aRequestAskedBeforeTheReadSeesNotFinishedAndOneAfterSeesFinished() throws IOException {
        Files.createFile(marker());
        List<Runnable> held = new ArrayList<>();
        var onboarding = new Onboarding(marker(), held::add);
        onboarding.load();
        boolean before = onboarding.finished();
        held.removeFirst().run();
        assertThat(before).isFalse();
        assertThat(onboarding.finished()).isTrue();
    }

    @Test void anUnwritableFolderStillFinishesTheRunAndTheFailureIsLogged() throws IOException {
        Path file = Files.createFile(folder.resolve("a-file"));
        var onboarding = new Onboarding(file.resolve("onboarding-finished"), Runnable::run);
        try (var log = new CapturedLog(Onboarding.class.getName())) {
            onboarding.finish();
            assertThat(log.records()).singleElement().satisfies(record ->
                assertThat(record.getMessage()).startsWith("The onboarding's marker could not be written"));
        }
        assertThat(onboarding.finished()).isTrue();
    }
}

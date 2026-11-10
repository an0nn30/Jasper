package dev.jasper.browser.downloads;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Downloads folder as the setting names it (ruling F3, D4): read and made on the background executor, here a queue
 * the test runs, over a system folder and a home folder under a {@code @TempDir}; only the folder itself is checked and
 * made, and the newest resolution asked for wins.
 */
class FolderResolutionTest {
    @TempDir Path temp;
    private final ArrayDeque<Runnable> background = new ArrayDeque<>();

    @Test void beforeTheFirstResolutionTheFolderIsAPathInTheHomeFolderAndNothingIsRead() {
        var reads = new AtomicInteger();
        var resolution = new FolderResolution(background::add, () -> {
            reads.incrementAndGet();
            return temp.resolve("Downloads");
        }, temp.resolve("home"));
        assertThat(resolution.now()).isNull();
        assertThat(resolution.folder()).isEqualTo(temp.resolve("home").resolve("Downloads"));
        resolution.resolve("");
        assertThat(reads).as("on the background executor only").hasValue(0);
        assertThat(temp.resolve("Downloads")).doesNotExist();
        assertThat(temp.resolve("home")).doesNotExist();
    }

    @Test void aMissingFolderIsCreated() {
        Path folder = temp.resolve("Downloads");
        FolderResolution resolution = resolution(folder);
        resolution.resolve("");
        run();
        assertThat(folder).isDirectory();
        assertThat(resolution.now()).isEqualTo(new FolderResolution.Resolved(folder, null));
        assertThat(resolution.folder()).isEqualTo(folder);
    }

    @Test void aFolderThatIsAFileOrCannotBeCreatedIsRefusedWithWhy() throws Exception {
        Path file = Files.writeString(temp.resolve("Downloads"), "not a folder");
        FolderResolution resolution = resolution(file);
        resolution.resolve("");
        run();
        assertThat(resolution.now()).isEqualTo(new FolderResolution.Resolved(file, "\u201C" + file + "\u201D is a file, not a folder."));
        Path under = file.resolve("Saved");
        resolution.resolve(under.toString());
        run();
        assertThat(resolution.now().folder()).isEqualTo(under);
        assertThat(resolution.now().refusal()).startsWith("Jasper could not create the folder \u201C" + under + "\u201D: ");
        assertThat(resolution.now().refusal()).as("the system's reason, not the path again").doesNotEndWith(": " + under);
    }

    @Test void theSettingNamesAFolderUnderTheHomeFolderOrAnAbsoluteOne() {
        FolderResolution resolution = resolution(temp.resolve("Downloads"));
        resolution.resolve("~/Saved");
        run();
        assertThat(resolution.folder()).isEqualTo(temp.resolve("home").resolve("Saved"));
        assertThat(temp.resolve("home").resolve("Saved")).isDirectory();
        resolution.resolve(temp.resolve("Elsewhere").toString());
        run();
        assertThat(resolution.folder()).isEqualTo(temp.resolve("Elsewhere"));
    }

    /** The newest resolution asked for wins, whichever finishes last. */
    @Test void theNewestResolutionAskedForWins() {
        FolderResolution resolution = resolution(temp.resolve("Downloads"));
        resolution.resolve(temp.resolve("Older").toString());
        resolution.resolve(temp.resolve("Newer").toString());
        Runnable older = background.poll(), newer = background.poll();
        newer.run();
        older.run();
        assertThat(resolution.folder()).isEqualTo(temp.resolve("Newer"));
    }

    @Test void aSystemFolderThatCannotBeReadIsARefusalAndARefusedExecutorReadsNothing() {
        Supplier<Path> broken = () -> { throw new UnsupportedOperationException("The Downloads folder is unavailable"); };
        var resolution = new FolderResolution(background::add, broken, temp.resolve("home"));
        resolution.resolve("");
        run();
        assertThat(resolution.now()).isEqualTo(new FolderResolution.Resolved(temp.resolve("home").resolve("Downloads"),
            "The Downloads folder is unavailable"));
        var stopped = new FolderResolution(task -> { throw new RejectedExecutionException("stopped"); }, () -> temp.resolve("Downloads"),
            temp.resolve("home"));
        stopped.resolve("");
        assertThat(stopped.now()).isNull();
        assertThat(temp.resolve("Downloads")).doesNotExist();
    }

    private FolderResolution resolution(Path system) {
        return new FolderResolution(background::add, () -> system, temp.resolve("home"));
    }

    private void run() {
        for (Runnable task; (task = background.poll()) != null; ) task.run();
    }
}

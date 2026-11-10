package dev.jasper.browser.downloads;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Show in Finder, Explorer or Folder, and Open (ruling F31), on the background executor: the desktop's own file
 * browser where Java's desktop has one, Explorer selecting the file on Windows, {@code xdg-open} on Linux, else the
 * desktop opening the folder. The desktop and the processes are recorded, never run, and every path is made up.
 */
class FileManagerTest {
    private static final Path FILE = Path.of("/made/up/Downloads/report.pdf");
    private final List<String> done = new CopyOnWriteArrayList<>(), failures = new CopyOnWriteArrayList<>();

    @Test void showUsesTheDesktopsFileBrowserWhereItHasOne() {
        for (String os : List.of("Mac OS X", "Windows 11", "Linux")) {
            done.clear();
            manager(os, Set.of(Desktop.Action.BROWSE_FILE_DIR, Desktop.Action.OPEN)).reveal(FILE);
            assertThat(done).as(os).containsExactly("BROWSE_FILE_DIR " + FILE);
        }
    }

    @Test void showSelectsTheFileInExplorerOnWindowsAndOpensTheFolderWithXdgOpenOnLinux() {
        var commands = new CopyOnWriteArrayList<List<String>>();
        var windows = new FileManager(Runnable::run, (title, reason) -> failures.add(title + "|" + reason), "Windows 10",
            Set.of(Desktop.Action.OPEN)::contains, (action, file) -> done.add(action + " " + file), commands::add);
        windows.reveal(FILE);
        assertThat(commands).as("ruling D21: /select, and the path in quotes, two arguments")
            .containsExactly(List.of("explorer.exe", "/select,", "\"" + FILE + "\""));
        Path awkward = Path.of("/made/up/My Downloads/report, final.pdf");
        windows.reveal(awkward);
        assertThat(commands.getLast()).as("a comma and a space stay inside the quoted path")
            .containsExactly("explorer.exe", "/select,", "\"/made/up/My Downloads/report, final.pdf\"");
        assertThat(done).isEmpty();
        manager("Linux", Set.of()).reveal(FILE);
        assertThat(done).containsExactly("[xdg-open, " + FILE.getParent() + "]");
        done.clear();
        manager("SunOS", Set.of(Desktop.Action.OPEN)).reveal(FILE);
        assertThat(done).as("elsewhere, the desktop opens the folder").containsExactly("OPEN " + FILE.getParent());
        assertThat(failures).isEmpty();
    }

    @Test void openUsesTheDesktopAndXdgOpenOnLinuxWithoutIt() {
        manager("Mac OS X", Set.of(Desktop.Action.OPEN)).open(FILE);
        assertThat(done).containsExactly("OPEN " + FILE);
        done.clear();
        manager("Linux", Set.of()).open(FILE);
        assertThat(done).containsExactly("[xdg-open, " + FILE + "]");
        assertThat(failures).isEmpty();
    }

    @Test void aFailurePostsWhyWithTheFilesName() {
        manager("SunOS", Set.of()).open(FILE);
        manager("SunOS", Set.of()).reveal(FILE);
        assertThat(failures).containsExactly("Could not open \u201Creport.pdf\u201D|This system cannot open files",
            "Could not show \u201Creport.pdf\u201D|This system cannot show files");
        failures.clear();
        var broken = new FileManager(Runnable::run, (title, reason) -> failures.add(title + "|" + reason), "Mac OS X", action -> true,
            (action, file) -> { throw new UncheckedIOException(new IOException("No application can open it")); },
            command -> { throw new UncheckedIOException(new IOException("Cannot run program")); });
        broken.open(FILE);
        assertThat(failures).containsExactly("Could not open \u201Creport.pdf\u201D|No application can open it");
        failures.clear();
        var failing = new FileManager(Runnable::run, (title, reason) -> failures.add(title + "|" + reason), "Windows 11", action -> false,
            (action, file) -> { }, command -> { throw new UncheckedIOException(new IOException("Cannot run program \"explorer.exe\"")); });
        String longName = "a".repeat(70) + ".pdf";
        failing.reveal(FILE.resolveSibling(longName));
        assertThat(failures).containsExactly("Could not show \u201C" + DownloadsMenu.elided(longName) + "\u201D|Cannot run program \"explorer.exe\"");
    }

    @Test void bothRunOnTheBackgroundExecutorAndNothingAfterItStops() {
        var queued = new ArrayDeque<Runnable>();
        var manager = new FileManager(queued::add, (title, reason) -> failures.add(title), "Mac OS X", action -> true,
            (action, file) -> done.add(action + " " + file), command -> done.add(command.toString()));
        manager.open(FILE);
        manager.reveal(FILE);
        assertThat(done).as("not on the caller's thread").isEmpty();
        queued.forEach(Runnable::run);
        assertThat(done).containsExactly("OPEN " + FILE, "BROWSE_FILE_DIR " + FILE);
        var stopped = new FileManager(task -> { throw new RejectedExecutionException("stopped"); }, (title, reason) -> failures.add(title),
            "Mac OS X", action -> true, (action, file) -> done.add("late"), command -> done.add("late"));
        stopped.open(FILE);
        stopped.reveal(FILE);
        assertThat(done).doesNotContain("late");
        assertThat(failures).isEmpty();
    }

    private FileManager manager(String os, Set<Desktop.Action> supported) {
        return new FileManager(Runnable::run, (title, reason) -> failures.add(title + "|" + reason), os, supported::contains,
            (action, file) -> done.add(action + " " + file), command -> done.add(command.toString()));
    }
}

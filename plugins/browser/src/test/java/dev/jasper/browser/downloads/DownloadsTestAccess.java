package dev.jasper.browser.downloads;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * The {@code downloads} package's seams for the plugin's tests in other packages (ruling F15): file managers that reach
 * no desktop and start no process.
 */
public final class DownloadsTestAccess {
    private static final AtomicInteger USED = new AtomicInteger();

    private DownloadsTestAccess() { }

    /**
     * A file manager as the plugin makes one, whose desktop and processes fail the test if it shows or opens a file.
     *
     * @param background where it would show and open files
     * @param failed hears a failure's title and reason
     * @return the file manager
     */
    public static FileManager refusingFiles(Executor background, BiConsumer<String, String> failed) {
        return new FileManager(background, failed, System.getProperty("os.name", ""), action -> refused(), (action, file) -> refused(),
            command -> refused());
    }

    /**
     * A file manager whose desktop and processes record what they were asked, as macOS's would be asked, and run nothing.
     *
     * @param background where it shows and opens files
     * @param asked hears each request as {@code ACTION path}, and each failure as {@code failed title: reason}
     * @return the file manager
     */
    public static FileManager recordingFiles(Executor background, java.util.List<String> asked) {
        return new FileManager(background, (title, reason) -> asked.add("failed " + title + ": " + reason), "Mac OS X", action -> true,
            (action, file) -> asked.add(action + " " + file), command -> asked.add(String.join(" ", command)));
    }

    /**
     * How many times any of these file managers was asked to reach the desktop in this run: none, unless a test failed.
     *
     * @return the count
     */
    public static int filesUsed() {
        return USED.get();
    }

    private static boolean refused() {
        USED.incrementAndGet();
        throw new AssertionError("A test must not show or open a file through the desktop");
    }
}

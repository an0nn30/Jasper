package dev.jasper.browser.downloads;

import dev.jasper.browser.page.DownloadProgress;
import dev.jasper.browser.page.DownloadStart;
import dev.jasper.browser.page.DownloadTarget;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageContext;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.testing.FakePluginHost.MarkedDownload;
import dev.jasper.sdk.ui.MenuEntries;
import dev.jasper.sdk.ui.Menus;
import dev.jasper.sdk.ui.Platform;
import dev.jasper.sdk.ui.PluginMenu;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Downloads (spec section 10) on the SDK's fake host, without Chromium: the test thread plays Chromium's, calling
 * {@link Downloads#before} and {@link Downloads#updated} as the {@code page} package's handler does, the fake's
 * background queue runs when the test says, and the fake's Downloads folder and every file are under a
 * {@code @TempDir} (ruling F15). The file manager's desktop and processes are recorded, never run.
 */
class DownloadsTest {
    private static final String ID = "dev.jasper.browser", MENU = "top:dev.jasper.browser.downloads", GROUP = "dev.jasper.browser.downloads";
    private static final PluginInfo INFO = new PluginInfo(ID, "Browser", "0.2.0", Set.of());
    private static final String MAC = "Mac OS X", WINDOWS = "Windows 11", LINUX = "Linux";
    /** The identifier of the browser the downloads start in. */
    private static final int BROWSER = 3;
    private static final String PAGE = "https://example.com/page", DASH = " \u2014 ";
    @TempDir Path temp;
    private FakePluginHost host;
    private Downloads downloads;
    private Path folder;
    private final List<String> desktop = new CopyOnWriteArrayList<>(), fileFailures = new CopyOnWriteArrayList<>();
    /** Each Downloads folder read and mark as {@code call@thread}, and each time the menu's entries were set. */
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final AtomicInteger sets = new AtomicInteger();
    /** Whether the context's background executor refuses work, as it does once the plugin stops. */
    private volatile boolean rejectBackground;
    private final List<Page> pages = new ArrayList<>();

    @AfterEach void close() throws Exception {
        onUi(() -> {
            pages.forEach(Page::close);
            if (host != null) host.close();
            return null;
        });
    }

    @Test void aFileGoesIntoTheFolderWithoutAskingAndAMissingFolderIsCreated() throws Exception {
        make(MAC);
        assertThat(folder).doesNotExist();
        resolve();
        assertThat(folder).isDirectory();
        assertThat(downloads.folder()).isEqualTo(folder);
        assertThat(downloads.before(start(null, 1, "report.pdf"))).contains(new DownloadTarget(folder.resolve("report.pdf"), false));
        assertThat(downloads.before(start(null, 2, "report.pdf"))).as("the first keeps its name: the next takes (1)")
            .contains(new DownloadTarget(folder.resolve("report (1).pdf"), false));
        assertThat(downloads.before(start(null, 3, "../../.ssh/authorized_keys"))).as("a name of the server's stays in the folder")
            .contains(new DownloadTarget(folder.resolve("authorized_keys"), false));
        flush();
        assertThat(notices()).isEmpty();
    }

    @Test void aFolderThatIsAFileRefusesTheDownloadAndPostsWhy() throws Exception {
        make(MAC);
        Files.writeString(folder, "not a folder");
        resolve();
        assertThat(downloads.before(start(null, 1, "report.pdf"))).isEmpty();
        flush();
        assertThat(notices()).containsExactly(GROUP + "|WARNING|Download failed for \u201Creport.pdf\u201D.|\u201C" + folder
            + "\u201D is a file, not a folder.");
        assertThat(menu()).as("refused before it began: not listed").isEmpty();
    }

    /** Ruling F3: the folder is read and made only on the background executor, after the first page start. */
    @Test void beforeTheFirstPageStartNothingIsReadAndADownloadIsRefused() throws Exception {
        make(MAC);
        assertThat(downloads.folder()).as("a path only").isEqualTo(temp.resolve("home").resolve("Downloads"));
        onUi(() -> {
            host.setSetting(ID, "downloads_folder", temp.resolve("Changed").toString());
            return null;
        });
        assertThat(host.runBackground()).as("a change before any page reads nothing").isZero();
        assertThat(downloads.before(start(null, 1, "report.pdf"))).isEmpty();
        flush();
        assertThat(notices()).containsExactly(GROUP + "|WARNING|Download failed for \u201Creport.pdf\u201D.|"
            + "Jasper is still preparing the Downloads folder.");
        assertThat(calls).as("no Downloads folder read").isEmpty();
        assertThat(folder).doesNotExist();
        assertThat(temp.resolve("home")).doesNotExist();
        assertThat(temp.resolve("Changed")).doesNotExist();
    }

    /** Ruling F3: a change of the setting resolves the folder again, on the background executor. */
    @Test void aFolderChangeResolvesTheFolderAgain() throws Exception {
        make(MAC);
        resolve();
        Path other = temp.resolve("Other");
        onUi(() -> {
            host.setSetting(ID, "downloads_folder", other.toString());
            return null;
        });
        assertThat(other).doesNotExist();
        assertThat(host.runBackground()).isEqualTo(1);
        assertThat(other).isDirectory();
        assertThat(downloads.folder()).isEqualTo(other);
        assertThat(downloads.before(start(null, 1, "report.pdf"))).contains(new DownloadTarget(other.resolve("report.pdf"), false));
    }

    @Test void anAskStartReservesNothingAndContinuesWithAsk() throws Exception {
        make(MAC);
        resolve();
        assertThat(downloads.before(ask(null, 1, "report.pdf"))).contains(new DownloadTarget(folder.resolve("report.pdf"), true));
        assertThat(downloads.before(start(null, 2, "report.pdf"))).as("nothing reserved")
            .contains(new DownloadTarget(folder.resolve("report.pdf"), false));
    }

    @Test void noMenuExistsBeforeTheFirstUpdateAndOneDoesAfterItAndProgressRetitlesTheEntry() throws Exception {
        make(MAC);
        resolve();
        Path target = downloads.before(start(null, 1, "report.pdf")).orElseThrow().file();
        flush();
        assertThat(menu()).isEmpty();
        update(running(1, 0, 0, ""), () -> { });
        assertThat(menu()).containsExactly("entry:downloads.clear-all:Clear All:disabled", "---", "entries:report.pdf" + DASH + "0 bytes",
            "  entry:downloads.1.stop:Stop");
        update(running(1, 42, 100, target + ".crdownload"), () -> { });
        assertThat(menu()).containsExactly("entry:downloads.clear-all:Clear All:disabled", "---", "entries:report.pdf" + DASH + "42%",
            "  entry:downloads.1.stop:Stop");
    }

    /**
     * The spec's mark (rulings F2 and F33): the completed file is marked on the background executor with the download's
     * address and the page's as referrer, and only then is it finished and told; Open, Show and Remove from List follow.
     * A page that is no web page gives no referrer.
     */
    @Test void aCompletionIsMarkedWithThePagesAddressAsReferrerThenToldAndOpenIsOffered() throws Exception {
        make(MAC);
        resolve();
        Page page = page(PageContext.persistent());
        Path target = downloads.before(new DownloadStart(BROWSER, 1, "report.pdf", "https://files.example/report.pdf", PAGE, Optional.of(page),
            false, true, 1, false)).orElseThrow().file();
        update(running(1, 10, 100, target + ".crdownload"), () -> { });
        complete(1, target);
        assertThat(notices()).as("not before the mark").isEmpty();
        assertThat(host.markedDownloads()).isEmpty();
        assertThat(menu()).contains("entries:report.pdf" + DASH + "100%");
        host.runBackground();
        flush();
        assertThat(host.markedDownloads()).containsExactly(new MarkedDownload(target, URI.create("https://files.example/report.pdf"),
            Optional.of(URI.create(PAGE))));
        assertThat(notices()).containsExactly(GROUP + "|INFO|Download finished for \u201Creport.pdf\u201D.|");
        assertThat(menu()).containsExactly("entry:downloads.clear-all:Clear All", "---", "entries:report.pdf" + DASH + "Finished",
            "  entry:downloads.1.open:Open", "  entry:downloads.1.show:Show in Finder", "  entry:downloads.1.remove:Remove from List");
        assertThat(onUi(() -> downloads.drained(page))).as("its count ended at its outcome").isCompleted();

        Path welcome = downloads.before(new DownloadStart(BROWSER, 2, "a.txt", "https://files.example/a.txt", "jasper-about:welcome",
            Optional.empty(), false, false, 0, false)).orElseThrow().file();
        complete(2, welcome);
        host.runBackground();
        assertThat(host.markedDownloads().getLast()).isEqualTo(new MarkedDownload(welcome, URI.create("https://files.example/a.txt"),
            Optional.empty()));
    }

    /** An ask download's chosen file, which Chromium names at completion, is the one marked and listed (ruling F2). */
    @Test void anAskDownloadsChosenFileIsMarked() throws Exception {
        make(MAC);
        resolve();
        downloads.before(ask(null, 1, "report.pdf")).orElseThrow();
        Path chosen = Files.createDirectories(temp.resolve("Chosen")).resolve("mine.pdf");
        update(running(1, 1, 10, chosen + ".crdownload"), () -> { });
        assertThat(menu()).contains("entries:mine.pdf" + DASH + "10%");
        complete(1, chosen);
        host.runBackground();
        flush();
        assertThat(host.markedDownloads()).extracting(MarkedDownload::file).containsExactly(chosen);
        assertThat(notices()).containsExactly(GROUP + "|INFO|Download finished for \u201Cmine.pdf\u201D.|");
    }

    /** Ruling F6: without its mark a finished file is not offered to open on macOS and Windows; Linux has no mark. */
    @Test void aFailedMarkWarnsAndOffersNoOpenOnMacOsAndWindowsButOnLinux() throws Exception {
        for (String os : List.of(MAC, WINDOWS, LINUX)) {
            String show = DownloadsMenu.showLabel(os);
            make(os);
            host.failMarks(new IOException("Operation not permitted"));
            resolve();
            Path target = downloads.before(start(null, 1, "report.pdf")).orElseThrow().file();
            complete(1, target);
            host.runBackground();
            flush();
            assertThat(host.markedDownloads()).as(os).isEmpty();
            assertThat(notices()).as(os).containsExactly(GROUP + "|INFO|Download finished for \u201Creport.pdf\u201D.|",
                GROUP + "|WARNING|\u201Creport.pdf\u201D has no downloaded-file mark|Jasper could not mark it as downloaded from the "
                    + "internet, so the system may open it without checking it first: Operation not permitted. Open it from the file "
                    + "manager only if you trust it.");
            List<String> entries = os.equals(LINUX)
                ? List.of("  entry:downloads.1.open:Open", "  entry:downloads.1.show:" + show, "  entry:downloads.1.remove:Remove from List")
                : List.of("  entry:downloads.1.show:" + show, "  entry:downloads.1.remove:Remove from List");
            assertThat(menu().subList(3, menu().size())).as(os).isEqualTo(entries);
            onUi(() -> {
                host.close();
                return null;
            });
            host = null;
            Files.walk(folder).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test void anInterruptedDownloadIsCancelledOncePostsOneWarningAndFreesItsName() throws Exception {
        make(MAC);
        resolve();
        var cancels = new AtomicInteger();
        Path target = downloads.before(start(null, 1, "report.pdf")).orElseThrow().file();
        update(running(1, 10, 100, target + ".crdownload"), cancels::incrementAndGet);
        update(interrupted(1), cancels::incrementAndGet);
        update(interrupted(1), cancels::incrementAndGet);
        update(cancelled(1), cancels::incrementAndGet);
        assertThat(cancels).as("so Chromium never resumes it").hasValue(1);
        assertThat(notices()).containsExactly(GROUP + "|WARNING|Download failed for \u201Creport.pdf\u201D.|");
        assertThat(menu()).containsExactly("entry:downloads.clear-all:Clear All", "---", "entries:report.pdf" + DASH + "Failed",
            "  entry:downloads.1.remove:Remove from List");
        assertThat(downloads.before(start(null, 2, "report.pdf"))).as("its name is free again")
            .contains(new DownloadTarget(target, false));
    }

    @Test void stopGivesStoppedWithNoNotificationAndFreesTheName() throws Exception {
        make(MAC);
        resolve();
        var cancels = new AtomicInteger();
        Path target = downloads.before(start(null, 1, "report.pdf")).orElseThrow().file();
        update(running(1, 10, 100, target + ".crdownload"), cancels::incrementAndGet);
        assertThat(onUi(() -> host.chooseMenuEntry("downloads.1.stop"))).isTrue();
        assertThat(cancels).hasValue(1);
        update(cancelled(1), cancels::incrementAndGet);
        assertThat(menu()).containsExactly("entry:downloads.clear-all:Clear All", "---", "entries:report.pdf" + DASH + "Stopped",
            "  entry:downloads.1.remove:Remove from List");
        assertThat(notices()).isEmpty();
        assertThat(downloads.before(start(null, 2, "report.pdf"))).contains(new DownloadTarget(target, false));
    }

    @Test void openAndShowReachTheFileManager() throws Exception {
        make(MAC);
        resolve();
        Path target = finished(1, "report.pdf");
        assertThat(onUi(() -> host.chooseMenuEntry("downloads.1.open"))).isTrue();
        assertThat(onUi(() -> host.chooseMenuEntry("downloads.1.show"))).isTrue();
        assertThat(desktop).containsExactly("OPEN " + target, "BROWSE_FILE_DIR " + target);
        assertThat(fileFailures).isEmpty();
    }

    @Test void removeFromListAndClearAllLeaveTheRunningDownloads() throws Exception {
        make(MAC);
        resolve();
        finished(1, "a.pdf");
        finished(2, "b.pdf");
        Path c = downloads.before(start(null, 3, "c.pdf")).orElseThrow().file();
        update(running(3, 1, 10, c + ".crdownload"), () -> { });
        update(interrupted(2), () -> { });
        assertThat(menu()).as("finished already: its later updates change nothing").contains("entries:b.pdf" + DASH + "Finished");
        assertThat(onUi(() -> host.chooseMenuEntry("downloads.3.remove"))).as("running: no Remove from List").isFalse();
        assertThat(onUi(() -> host.chooseMenuEntry("downloads.1.remove"))).isTrue();
        assertThat(menu()).containsExactly("entry:downloads.clear-all:Clear All", "---", "entries:b.pdf" + DASH + "Finished",
            "  entry:downloads.2.open:Open", "  entry:downloads.2.show:Show in Finder", "  entry:downloads.2.remove:Remove from List",
            "entries:c.pdf" + DASH + "10%", "  entry:downloads.3.stop:Stop");
        assertThat(onUi(() -> host.chooseMenuEntry("downloads.clear-all"))).isTrue();
        assertThat(menu()).containsExactly("entry:downloads.clear-all:Clear All:disabled", "---", "entries:c.pdf" + DASH + "10%",
            "  entry:downloads.3.stop:Stop");
    }

    /**
     * Ruling F30: quitting cancels the running downloads only, completes every page's hold at once and refuses later
     * starts; updates after it are dropped, and no menu is made.
     */
    @Test void stopAllCancelsOnlyTheRunningDownloadsAndDropsWhatComesAfter() throws Exception {
        make(MAC);
        resolve();
        Page page = page(PageContext.persistent());
        var running = new AtomicInteger();
        var finished = new AtomicInteger();
        var stopped = new AtomicInteger();
        Path a = downloads.before(start(page, 1, "a.pdf")).orElseThrow().file();
        update(running(1, 1, 10, a + ".crdownload"), running::incrementAndGet);
        Path b = downloads.before(start(null, 2, "b.pdf")).orElseThrow().file();
        update(running(2, 1, 10, b + ".crdownload"), finished::incrementAndGet);
        complete(2, b);
        host.runBackground();
        Path c = downloads.before(start(null, 3, "c.pdf")).orElseThrow().file();
        update(running(3, 1, 10, c + ".crdownload"), stopped::incrementAndGet);
        onUi(() -> host.chooseMenuEntry("downloads.3.stop"));
        update(cancelled(3), stopped::incrementAndGet);
        CompletableFuture<Void> hold = onUi(() -> downloads.drained(page));
        assertThat(hold).isNotDone();
        List<String> before = menu();
        onUi(() -> {
            downloads.stopAll();
            return null;
        });
        assertThat(running).hasValue(1);
        assertThat(finished).hasValue(0);
        assertThat(stopped).hasValue(1);
        assertThat(hold).as("every hold completes at once").isCompleted();
        update(running(1, 9, 10, a + ".crdownload"), running::incrementAndGet);
        assertThat(menu()).as("dropped").isEqualTo(before);
        assertThat(downloads.before(start(null, 4, "d.pdf"))).isEmpty();
        onUi(() -> {
            downloads.stopAll();
            return null;
        });
        assertThat(running).as("once").hasValue(1);
        flush();
        assertThat(notices()).containsExactly(GROUP + "|INFO|Download finished for \u201Cb.pdf\u201D.|");
    }

    @Test void afterStopAllNoMenuIsMade() throws Exception {
        make(MAC);
        resolve();
        Path a = downloads.before(start(null, 1, "a.pdf")).orElseThrow().file();
        onUi(() -> {
            downloads.stopAll();
            return null;
        });
        update(running(1, 1, 10, a + ".crdownload"), () -> { });
        assertThat(menu()).isEmpty();
    }

    /**
     * Ruling F1: a download counts for its page from the moment it is given a target, and a start after the page's
     * close began is refused, telling nothing; the hold completes once the page's last download ends, and at once for
     * a page with none.
     */
    @Test void aStartAfterThePagesCloseBeganIsRefusedAndTheHoldCompletesAtItsLastOutcome() throws Exception {
        make(MAC);
        resolve();
        Page page = page(PageContext.persistent()), other = page(PageContext.persistent()), idle = page(PageContext.persistent());
        assertThat(onUi(() -> downloads.drained(idle))).isCompleted();
        Path a = downloads.before(start(page, 1, "a.pdf")).orElseThrow().file();
        downloads.before(start(other, 2, "b.pdf")).orElseThrow();
        CompletableFuture<Void> hold = onUi(() -> downloads.drained(page));
        assertThat(hold).as("counted from before(), with no update yet").isNotDone();
        assertThat(downloads.before(start(page, 3, "c.pdf"))).as("the close began").isEmpty();
        assertThat(downloads.before(ask(page, 4, "d.pdf"))).isEmpty();
        update(running(1, 1, 10, a + ".crdownload"), () -> { });
        assertThat(hold).isNotDone();
        update(cancelled(1), () -> { });
        assertThat(hold).isCompleted();
        assertThat(notices()).isEmpty();
        assertThat(downloads.before(start(other, 5, "e.pdf"))).as("another page's").isPresent();
    }

    /** The plan's row: a page's hold completes once its last download ends, and is complete at once for a page with none. */
    @Test void drainedCompletesWhenThePagesLastDownloadEnds() throws Exception {
        make(MAC);
        resolve();
        Page page = page(PageContext.persistent()), none = page(PageContext.persistent());
        assertThat(onUi(() -> downloads.drained(none))).as("no download").isCompleted();
        Path a = downloads.before(start(page, 1, "a.pdf")).orElseThrow().file();
        Path b = downloads.before(start(page, 2, "b.pdf")).orElseThrow().file();
        update(running(1, 1, 10, a + ".crdownload"), () -> { });
        update(running(2, 1, 10, b + ".crdownload"), () -> { });
        CompletableFuture<Void> hold = onUi(() -> downloads.drained(page));
        assertThat(hold).isNotDone();
        complete(1, a);
        assertThat(hold).as("one still runs").isNotDone();
        update(interrupted(2), () -> { });
        assertThat(hold).isCompleted();
        assertThat(onUi(() -> downloads.drained(page))).as("asked again").isCompleted();
    }

    /**
     * Ruling F1 and D19, the whole path: a page that starts a download and then closes itself, as {@code window.close()}
     * closes its item, keeps its browser, which loads {@code about:blank}, until the download's outcome; its entry stays
     * in the menu meanwhile, and the finished file is marked. Closing first refuses the download instead.
     */
    @Test void aPageThatDownloadsAndClosesItselfKeepsItsBrowserUntilTheFileIsMarked() throws Exception {
        make(MAC);
        resolve();
        Page page = page(PageContext.persistent());
        var browser = new CopyOnWriteArrayList<String>();
        onUi(() -> {
            PageTestAccess.useBrowserEnding(page, blanked -> browser.add("about:blank"), closing -> browser.add("close"));
            page.setCloseHold(downloads::drained);
            page.onCloseItem(page::close);
            return null;
        });
        Path target = downloads.before(new DownloadStart(BROWSER, 1, "report.pdf", "https://files.example/report.pdf", PAGE,
            Optional.of(page), false, true, 1, false)).orElseThrow().file();
        onUi(() -> {
            page.closeItem();
            return null;
        });
        assertThat(browser).as("held before its first update").containsExactly("about:blank");
        update(running(1, 5, 10, target + ".crdownload"), () -> { });
        assertThat(menu()).contains("entries:report.pdf" + DASH + "50%");
        complete(1, target);
        assertThat(browser).as("closed at the outcome").containsExactly("about:blank", "close");
        host.runBackground();
        flush();
        assertThat(host.markedDownloads()).containsExactly(new MarkedDownload(target, URI.create("https://files.example/report.pdf"),
            Optional.of(URI.create(PAGE))));
        assertThat(menu()).contains("entries:report.pdf" + DASH + "Finished");
        assertThat(notices()).containsExactly(GROUP + "|INFO|Download finished for \u201Creport.pdf\u201D.|");

        Page closedFirst = page(PageContext.persistent());
        onUi(() -> {
            PageTestAccess.useBrowserEnding(closedFirst, blanked -> browser.add("blank, closed first"),
                closing -> browser.add("closed first"));
            closedFirst.setCloseHold(downloads::drained);
            closedFirst.close();
            return null;
        });
        assertThat(downloads.before(start(closedFirst, 2, "late.pdf"))).as("a start after the close").isEmpty();
        assertThat(browser).endsWith("closed first");
    }

    /**
     * Ruling F30 with F1: Jasper's quit completes every hold in {@code stopAll}, on the UI thread, and a held browser
     * closes in that same turn, before the plugin closes Chromium; a start Chromium could not be given, abandoned on
     * Chromium's thread, closes its page's held browser on the UI thread.
     */
    @Test void aHeldBrowserClosesAsJasperQuitsBeforeChromiumDoesAndAnAbandonClosesItOnTheUiThread() throws Exception {
        make(MAC);
        resolve();
        Page quitting = page(PageContext.persistent()), abandoning = page(PageContext.persistent());
        var browser = new CopyOnWriteArrayList<String>();
        DownloadStart abandoned = start(abandoning, 2, "b.pdf");
        Path a = downloads.before(start(quitting, 1, "a.pdf")).orElseThrow().file();
        downloads.before(abandoned).orElseThrow();
        update(running(1, 1, 10, a + ".crdownload"), () -> { });
        onUi(() -> {
            PageTestAccess.useBrowserEnding(quitting, blanked -> browser.add("quitting blank"),
                closing -> browser.add("quitting closed on the UI thread: " + SwingUtilities.isEventDispatchThread()));
            PageTestAccess.useBrowserEnding(abandoning, blanked -> browser.add("abandoning blank"),
                closing -> browser.add("abandoning closed on the UI thread: " + SwingUtilities.isEventDispatchThread()));
            quitting.setCloseHold(downloads::drained);
            abandoning.setCloseHold(downloads::drained);
            quitting.close();
            abandoning.close();
            return null;
        });
        assertThat(browser).containsExactly("quitting blank", "abandoning blank");
        downloads.abandon(abandoned);
        flush();
        assertThat(browser).endsWith("abandoning closed on the UI thread: true");
        List<String> seen = onUi(() -> {
            downloads.stopAll();
            return List.copyOf(browser);
        });
        assertThat(seen).as("in stopAll's own turn").endsWith("quitting closed on the UI thread: true");
    }

    /**
     * Ruling F1.3: a save chooser cancelled for a page ends its oldest ask start that has had no update, as Chromium may
     * tell nothing of a download cancelled before it began; one that had an update ends with its own outcome, and
     * another page's are its own.
     */
    @Test void aCancelledSaveChooserEndsThePagesOldestAskStartThatHadNoUpdate() throws Exception {
        make(MAC);
        resolve();
        Page page = page(PageContext.persistent()), other = page(PageContext.persistent());
        downloads.before(ask(page, 1, "a.pdf")).orElseThrow();
        Path updated = downloads.before(ask(page, 2, "b.pdf")).orElseThrow().file();
        update(running(2, 1, 10, updated + ".crdownload"), () -> { });
        downloads.before(ask(page, 3, "c.pdf")).orElseThrow();
        downloads.before(ask(other, 4, "d.pdf")).orElseThrow();
        CompletableFuture<Void> hold = onUi(() -> downloads.drained(page)), otherHold = onUi(() -> downloads.drained(other));
        onUi(() -> {
            downloads.saveCancelled(page);
            return null;
        });
        update(running(1, 1, 10, folder.resolve("a.pdf") + ".crdownload"), () -> { });
        assertThat(menu()).as("its update is dropped").noneMatch(line -> line.startsWith("entries:a.pdf"));
        assertThat(hold).isNotDone();
        onUi(() -> {
            downloads.saveCancelled(page);
            return null;
        });
        assertThat(hold).as("the one with an update still runs").isNotDone();
        update(cancelled(2), () -> { });
        assertThat(hold).isCompleted();
        assertThat(otherHold).isNotDone();
        onUi(() -> {
            downloads.saveCancelled(page);
            return null;
        });
        assertThat(otherHold).as("another page's").isNotDone();
        assertThat(notices()).isEmpty();
    }

    /**
     * Ruling F28: a download given a target while its page has committed no document, its first navigation become a
     * download, tells the page on the UI thread, which tells its item; a page showing a document, and a refused start,
     * tell nothing.
     */
    @Test void aDownloadBeforeThePagesFirstDocumentTellsThePageOnTheUiThread() throws Exception {
        make(MAC);
        resolve();
        Page fresh = page(PageContext.persistent()), shown = page(PageContext.persistent()), closing = page(PageContext.persistent());
        var told = new CopyOnWriteArrayList<String>();
        onUi(() -> {
            PageTestAccess.addressChanged(shown, PAGE);
            fresh.onFirstNavigationDownload(() -> told.add("fresh on the UI thread: " + SwingUtilities.isEventDispatchThread()));
            shown.onFirstNavigationDownload(() -> told.add("shown"));
            closing.onFirstNavigationDownload(() -> told.add("closing"));
            downloads.drained(closing);
            return null;
        });
        assertThat(downloads.before(start(fresh, 1, "a.pdf"))).isPresent();
        assertThat(downloads.before(start(shown, 2, "b.pdf"))).isPresent();
        assertThat(downloads.before(start(closing, 3, "c.pdf"))).isEmpty();
        flush();
        assertThat(told).containsExactly("fresh on the UI thread: true");
    }

    /**
     * Ruling F7: a /dev/null download's mark names no address, and its group's finished downloads leave the list as the
     * group's store closes; a running one leaves at its end, and another group's stay.
     */
    @Test void aDevNullDownloadIsMarkedWithoutAddressesAndLeavesTheListWithItsGroup() throws Exception {
        make(WINDOWS);
        resolve();
        PageContext group = PageContext.memory();
        Page secret = page(group), open = page(PageContext.persistent());
        Path a = downloads.before(new DownloadStart(BROWSER, 1, "a.pdf", "https://secret.example/a.pdf", "https://secret.example/",
            Optional.of(secret), false, false, 1, true)).orElseThrow().file();
        complete(1, a);
        host.runBackground();
        assertThat(host.markedDownloads()).containsExactly(new MarkedDownload(a, URI.create("about:internet"), Optional.empty()));
        Path b = downloads.before(new DownloadStart(BROWSER, 2, "b.pdf", "https://secret.example/b.pdf", "https://secret.example/",
            Optional.of(secret), false, true, 1, true)).orElseThrow().file();
        update(running(2, 1, 10, b + ".crdownload"), () -> { });
        Path c = downloads.before(start(open, 3, "c.pdf")).orElseThrow().file();
        complete(3, c);
        host.runBackground();
        flush();
        onUi(() -> {
            group.close();
            return null;
        });
        assertThat(menu()).containsExactly("entry:downloads.clear-all:Clear All", "---", "entries:b.pdf" + DASH + "10%",
            "  entry:downloads.2.stop:Stop", "entries:c.pdf" + DASH + "Finished", "  entry:downloads.3.open:Open",
            "  entry:downloads.3.show:Show in Explorer", "  entry:downloads.3.remove:Remove from List");
        complete(2, b);
        host.runBackground();
        flush();
        assertThat(menu()).as("after it is finished").containsExactly("entry:downloads.clear-all:Clear All", "---",
            "entries:c.pdf" + DASH + "Finished", "  entry:downloads.3.open:Open", "  entry:downloads.3.show:Show in Explorer",
            "  entry:downloads.3.remove:Remove from List");
        assertThat(host.markedDownloads().get(2)).isEqualTo(new MarkedDownload(b, URI.create("about:internet"), Optional.empty()));
    }

    /**
     * Ruling D1 (the user's "Match Chrome"): a page's first download without a user activation in a document goes, and
     * each later one in that document is refused, the first refusal telling the page's host once; an activated start,
     * an ask, a new document and a browser that is no page's each go.
     */
    @Test void aPageDownloadsOnceWithoutActivationInEachDocument() throws Exception {
        make(MAC);
        resolve();
        Page page = page(PageContext.persistent());
        assertThat(downloads.before(automatic(page, 1, 1))).isPresent();
        assertThat(downloads.before(automatic(page, 2, 1))).isEmpty();
        flush();
        String notice = GROUP + "|WARNING|This site tried to download multiple files|example.com";
        assertThat(notices()).containsExactly(notice);
        assertThat(downloads.before(automatic(page, 3, 1))).isEmpty();
        flush();
        assertThat(notices()).as("once a document").containsExactly(notice);
        assertThat(onUi(() -> host.notifications().posts().getLast().count())).isEqualTo(1);
        assertThat(downloads.before(new DownloadStart(BROWSER, 4, "4.bin", "https://example.com/4.bin", PAGE, Optional.of(page), false, true,
            1, false))).as("a click").isPresent();
        assertThat(downloads.before(new DownloadStart(BROWSER, 5, "5.bin", "https://example.com/5.bin", PAGE, Optional.of(page), true, false,
            1, false))).as("Save Link As").isPresent();
        assertThat(downloads.before(new DownloadStart(BROWSER, 6, "6.bin", "https://example.com/6.bin", PAGE, Optional.empty(), false, false,
            0, false))).as("DevTools'").isPresent();
        assertThat(downloads.before(new DownloadStart(BROWSER, 7, "7.bin", "https://example.com/7.bin", PAGE, Optional.empty(), false, false,
            0, false))).isPresent();
        assertThat(downloads.before(automatic(page, 8, 2))).as("a new document").isPresent();
        assertThat(downloads.before(automatic(page, 9, 2))).isEmpty();
        flush();
        assertThat(notices()).as("the new document's first refusal, counted on the same notice as Jasper counts a repeat")
            .containsExactly(notice);
        assertThat(onUi(() -> host.notifications().posts().getLast().count())).isEqualTo(2);
        Page other = page(PageContext.persistent());
        assertThat(downloads.before(automatic(other, 10, 2))).as("another page's").isPresent();
    }

    /**
     * Ruling D20: a load Jasper starts for the user, as the address field's or a bookmark's, gives the page a fresh
     * download allowance, so two such loads in a row that each become a download both go; within one load the second
     * automatic download is refused, and a page's own navigation, such as {@code location.href = ...}, renews nothing.
     */
    @Test void aLoadJasperStartsGivesThePageAFreshDownloadAllowance() throws Exception {
        make(MAC);
        resolve();
        Page page = page(PageContext.persistent());
        assertThat(downloads.before(pageStart(page, 1))).isPresent();
        assertThat(downloads.before(pageStart(page, 2))).as("the page's own navigation to a second file").isEmpty();
        load(page, "https://example.com/a.zip");
        assertThat(downloads.before(pageStart(page, 3))).as("a load Jasper started").isPresent();
        load(page, "https://example.com/b.zip");
        assertThat(downloads.before(pageStart(page, 4))).as("the next load too").isPresent();
        assertThat(downloads.before(pageStart(page, 5))).as("within one load, the second").isEmpty();
        assertThat(downloads.before(pageStart(page, 6))).isEmpty();
    }

    /** Review finding 6: a mark the background executor refuses still finishes the download, without Open on macOS. */
    @Test void aMarkTheBackgroundExecutorRefusesStillFinishesTheDownload() throws Exception {
        make(MAC);
        resolve();
        Path target = downloads.before(start(null, 1, "report.pdf")).orElseThrow().file();
        rejectBackground = true;
        complete(1, target);
        assertThat(host.markedDownloads()).isEmpty();
        assertThat(menu()).containsExactly("entry:downloads.clear-all:Clear All", "---", "entries:report.pdf" + DASH + "Finished",
            "  entry:downloads.1.show:Show in Finder", "  entry:downloads.1.remove:Remove from List");
    }

    /**
     * Review finding 6: an interrupted download is cancelled before its page's count ends, since the page's hold may then
     * close its browser.
     */
    @Test void anInterruptedDownloadIsCancelledBeforeItsPagesHoldCompletes() throws Exception {
        make(MAC);
        resolve();
        Page page = page(PageContext.persistent());
        var seen = new CopyOnWriteArrayList<String>();
        Path target = downloads.before(start(page, 1, "report.pdf")).orElseThrow().file();
        CompletableFuture<Void> hold = onUi(() -> downloads.drained(page));
        hold.thenRun(() -> seen.add("hold"));
        Runnable cancel = () -> seen.add(hold.isDone() ? "cancel after the hold" : "cancel");
        update(running(1, 1, 10, target + ".crdownload"), cancel);
        update(interrupted(1), cancel);
        assertThat(seen).containsExactly("cancel", "hold");
    }

    /**
     * Review finding 6: a start refused at the second lock, as its page's close began while its name was reserved,
     * gives the name back.
     */
    @Test void aStartRefusedAfterItsNameWasReservedGivesTheNameBack() throws Exception {
        make(MAC);
        resolve();
        Page page = page(PageContext.persistent());
        downloads.afterReservation(() -> downloads.drained(page));
        assertThat(downloads.before(start(page, 1, "report.pdf"))).as("the close began meanwhile").isEmpty();
        downloads.afterReservation(() -> { });
        assertThat(downloads.before(start(null, 2, "report.pdf"))).as("its name is free")
            .contains(new DownloadTarget(folder.resolve("report.pdf"), false));
    }

    /**
     * Review finding 9: a start Chromium could not be given is abandoned: its name is free again, its page's hold
     * completes, and an update for it is dropped.
     */
    @Test void anAbandonedStartGivesBackItsNameAndItsPagesCount() throws Exception {
        make(MAC);
        resolve();
        Page page = page(PageContext.persistent());
        DownloadStart start = start(page, 1, "report.pdf");
        Path target = downloads.before(start).orElseThrow().file();
        CompletableFuture<Void> hold = onUi(() -> downloads.drained(page));
        assertThat(hold).isNotDone();
        downloads.abandon(start);
        downloads.abandon(start);
        assertThat(hold).isCompleted();
        update(running(1, 1, 10, target + ".crdownload"), () -> { });
        assertThat(menu()).as("dropped").isEmpty();
        assertThat(downloads.before(start(null, 2, "report.pdf"))).contains(new DownloadTarget(target, false));
    }

    /** Ruling F19: Chromium's updates set the entries only when a title, an enabled state or the structure changes. */
    @Test void anUpdateThatChangesNoLabelSetsNothing() throws Exception {
        make(MAC);
        resolve();
        Path target = downloads.before(start(null, 1, "report.pdf")).orElseThrow().file();
        update(running(1, 10, 1000, target + ".crdownload"), () -> { });
        assertThat(sets).as("made with its entries").hasValue(0);
        update(running(1, 10, 1000, target + ".crdownload"), () -> { });
        update(running(1, 19, 1000, target + ".crdownload"), () -> { });
        assertThat(sets).as("still 1%").hasValue(0);
        update(running(1, 20, 1000, target + ".crdownload"), () -> { });
        assertThat(sets).hasValue(1);
        assertThat(menu()).contains("entries:report.pdf" + DASH + "2%");
    }

    /**
     * Ruling F29: after Chromium has closed, each running download's intermediate file goes, if it is still a regular
     * file in its download's folder; a link of that name, and a file elsewhere, stay.
     */
    @Test void theIntermediatesOfRunningDownloadsAreDeletedAndALinkIsLeftAlone() throws Exception {
        make(MAC);
        resolve();
        Path a = downloads.before(start(null, 1, "a.pdf")).orElseThrow().file();
        Path partial = Files.writeString(Path.of(a + ".crdownload"), "partial");
        update(running(1, 1, 10, partial.toString()), () -> { });
        Path b = downloads.before(start(null, 2, "b.pdf")).orElseThrow().file();
        Path outside = Files.writeString(temp.resolve("outside.txt"), "kept");
        Path link = Files.createSymbolicLink(Path.of(b + ".crdownload"), outside);
        update(running(2, 1, 10, link.toString()), () -> { });
        downloads.before(start(null, 3, "c.pdf")).orElseThrow();
        Path elsewhere = Files.writeString(Files.createDirectories(temp.resolve("elsewhere")).resolve("c.pdf.crdownload"), "kept");
        update(running(3, 1, 10, elsewhere.toString()), () -> { });
        Path d = downloads.before(start(null, 4, "d.pdf")).orElseThrow().file();
        Path done = Files.writeString(Path.of(d + ".crdownload"), "partial");
        update(running(4, 1, 10, done.toString()), () -> { });
        complete(4, d);
        Files.writeString(done, "a file of that name made afterwards");
        onUi(() -> {
            downloads.stopAll();
            return null;
        });
        downloads.deleteIntermediates();
        assertThat(partial).doesNotExist();
        assertThat(link).isSymbolicLink();
        assertThat(outside).hasContent("kept");
        assertThat(elsewhere).hasContent("kept");
        assertThat(done).as("no longer running at its completion").exists();
    }

    /**
     * Task 1c's rule: the first Downloads folder read and every mark run on the background executor, never on the UI
     * thread or on Chromium's, whichever thread asks.
     */
    @Test void theFolderReadAndTheMarkRunOffTheUiThreadAndChromiumsThread() throws Exception {
        make(MAC);
        ExecutorService chromium = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("chromium-stand-in").factory());
        try {
            onUi(() -> {
                downloads.start();
                return null;
            });
            assertThat(calls).isEmpty();
            host.runBackground();
            Path target = chromium.submit(() -> downloads.before(start(null, 1, "report.pdf")).orElseThrow().file()).get(10, TimeUnit.SECONDS);
            Files.writeString(target, "content");
            chromium.submit(() -> downloads.updated(new DownloadProgress(BROWSER, 1, false, true, false, 7, 7, target.toString()), () -> { }))
                .get(10, TimeUnit.SECONDS);
            flush();
            assertThat(calls).as("not yet").containsExactly("downloadsFolder@" + Thread.currentThread().getName());
            host.runBackground();
            assertThat(calls).containsExactly("downloadsFolder@" + Thread.currentThread().getName(),
                "markDownloaded@" + Thread.currentThread().getName());
            assertThat(calls).noneMatch(call -> call.endsWith("@chromium-stand-in") || call.contains("AWT-EventQueue"));
        } finally {
            chromium.shutdownNow();
        }
    }

    /** Makes the Downloads of a plugin on the fake host, on {@code osName}, whose Downloads folder is {@link #folder}. */
    private void make(String osName) throws Exception {
        folder = temp.resolve("Downloads");
        onUi(() -> {
            host = new FakePluginHost(Files.createDirectories(temp.resolve("plugins")));
            host.setDownloadsFolder(folder);
            var context = new AtomicReference<PluginContext>();
            var setting = new AtomicReference<Setting<String>>();
            host.start(INFO, Set.of(), Set.of(), plugin -> {
                context.set(plugin);
                setting.set(BrowserSettings.declare(plugin.settings()).downloadsFolder());
            });
            var files = new FileManager(Runnable::run, (title, reason) -> fileFailures.add(title + "|" + reason), osName, action -> true,
                (action, file) -> desktop.add(action + " " + file), command -> desktop.add(String.join(" ", command)));
            PluginContext recorded = recording(context.get());
            downloads = new Downloads(recorded, recorded.notifications().group(Downloads.GROUP), setting.get(), files, temp.resolve("home"),
                osName);
            return null;
        });
    }

    /** The first page start, then the background executor: the folder is resolved. */
    private void resolve() throws Exception {
        onUi(() -> {
            downloads.start();
            return null;
        });
        host.runBackground();
    }

    /** A download listed, completed, marked and finished. */
    private Path finished(int id, String name) throws Exception {
        Path target = downloads.before(start(null, id, name)).orElseThrow().file();
        complete(id, target);
        host.runBackground();
        flush();
        return target;
    }

    /** Chromium's completed update for {@code id}, which wrote {@code file}. */
    private void complete(int id, Path file) throws Exception {
        if (!Files.exists(file)) Files.writeString(file, "content");
        update(new DownloadProgress(BROWSER, id, false, true, false, 100, 100, file.toString()), () -> { });
    }

    private void update(DownloadProgress progress, Runnable cancel) throws Exception {
        downloads.updated(progress, cancel);
        flush();
    }

    private static DownloadStart start(Page page, int id, String name) {
        return new DownloadStart(BROWSER, id, name, "https://example.com/" + name, PAGE, Optional.ofNullable(page), false, true, 1,
            page != null && page.inMemory());
    }

    private static DownloadStart ask(Page page, int id, String name) {
        return new DownloadStart(BROWSER, id, name, "https://example.com/" + name, PAGE, Optional.ofNullable(page), true, false, 1, false);
    }

    /** A download {@code page}'s own browser starts now, as the download handler reads it: no activation here. */
    private static DownloadStart pageStart(Page page, int id) {
        return PageTestAccess.downloadStart(page, BROWSER, id, "https://example.com/" + id + ".bin", id + ".bin", PAGE,
            System.currentTimeMillis());
    }

    /** A load Jasper starts for the user, as the address field's. */
    private static void load(Page page, String address) throws Exception {
        onUi(() -> {
            page.load(URI.create(address));
            return null;
        });
    }

    /** A download a page starts in its {@code document}th document without a user activation. */
    private static DownloadStart automatic(Page page, int id, long document) {
        return new DownloadStart(BROWSER, id, id + ".bin", "https://example.com/" + id + ".bin", PAGE, Optional.of(page), false, false,
            document, false);
    }

    private static DownloadProgress running(int id, long received, long total, String fullPath) {
        return new DownloadProgress(BROWSER, id, true, false, false, received, total, fullPath);
    }

    private static DownloadProgress interrupted(int id) {
        return new DownloadProgress(BROWSER, id, false, false, false, 10, 100, "");
    }

    private static DownloadProgress cancelled(int id) {
        return new DownloadProgress(BROWSER, id, false, false, true, 10, 100, "");
    }

    private Page page(PageContext context) throws Exception {
        Page page = onUi(() -> new Page(URI.create(PAGE), () -> { }, PageTestAccess.noFavicons(), Runnable::run, context));
        pages.add(page);
        return page;
    }

    private List<String> menu() throws Exception {
        return onUi(() -> host.menu(MENU));
    }

    private List<String> notices() throws Exception {
        return onUi(() -> host.notifications().lines().stream().filter(line -> line.startsWith(GROUP + "|")).toList());
    }

    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * The fake's context, with each Downloads folder read and mark recorded with its thread ({@link #calls}) and each
     * setting of the menu's entries counted ({@link #sets}).
     */
    private PluginContext recording(PluginContext context) {
        return wrapped(PluginContext.class, context);
    }

    @SuppressWarnings("unchecked")
    private <T> T wrapped(Class<T> type, T target) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            String name = method.getName();
            if (type == Platform.class && (name.equals("downloadsFolder") || name.equals("markDownloaded")))
                calls.add(name + "@" + Thread.currentThread().getName());
            if (type == MenuEntries.class && name.equals("set")) sets.incrementAndGet();
            if (type == PluginContext.class && name.equals("background") && rejectBackground)
                return (java.util.concurrent.Executor) task -> { throw new RejectedExecutionException("The plugin has stopped"); };
            Object result;
            try {
                result = method.invoke(target, args);
            } catch (InvocationTargetException thrown) {
                throw thrown.getCause();
            }
            if (result instanceof Platform platform && type == PluginContext.class) return wrapped(Platform.class, platform);
            if (result instanceof Menus menus && type == PluginContext.class) return wrapped(Menus.class, menus);
            if (result instanceof PluginMenu menu && type == Menus.class) return wrapped(PluginMenu.class, menu);
            if (result instanceof MenuEntries entries && type == PluginMenu.class) return wrapped(MenuEntries.class, entries);
            return result;
        });
    }

    private static <T> T onUi(Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Exception thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() != null) throw failure.get();
        return result.get();
    }
}

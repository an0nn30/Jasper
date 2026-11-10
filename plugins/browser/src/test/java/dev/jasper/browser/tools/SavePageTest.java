package dev.jasper.browser.tools;

import dev.jasper.browser.downloads.DownloadNames;
import dev.jasper.browser.downloads.DownloadsTestAccess;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageContext;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.ContentSpec;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.ViewSpec;
import dev.jasper.sdk.ui.Windows;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Save Page As over the SDK's fake host (plan 7's Task 7, F15, F26 and D7), with a stand-in page whose scripts are
 * recorded: the chooser asks where, starting at the host's first free folder in the Downloads folder; Cancel does
 * nothing; a chosen folder runs {@code save-page.js} under a fresh prefix, and its answer is saved on the background
 * executor and the folder shown in the file manager; a failure says "Save Failed" over the item, or posts a warning once
 * the tab has closed. The file manager is recorded, the snapshots name no web resource, so nothing is fetched, and every
 * folder is under {@code @TempDir}.
 */
class SavePageTest {
    private static final String ID = "dev.jasper.browser", ADDRESS = "https://user:pw@example.com/news/";
    @TempDir Path temp;
    private FakePluginHost host;
    private PluginContext context;
    private ContentTab tab;
    private Page page;
    private Path downloads;
    private final List<String> ran = new CopyOnWriteArrayList<>(), shown = new CopyOnWriteArrayList<>(), chooser = new CopyOnWriteArrayList<>();
    private final List<Runnable> timers = new ArrayList<>();
    private final List<Duration> delays = new ArrayList<>();
    private final AtomicInteger clients = new AtomicInteger(), cancelled = new AtomicInteger();
    // The plugin's names, which downloads and saves reserve.
    private final DownloadNames names = new DownloadNames();

    @BeforeEach void open() throws Exception {
        downloads = Files.createDirectories(temp.resolve("Downloads"));
        onUi(() -> {
            host = new FakePluginHost(Files.createDirectories(temp.resolve("plugins")));
            var started = new AtomicReference<PluginContext>();
            host.start(new PluginInfo(ID, "Browser", "0.2.0", Set.of()), Set.of(), Set.of(), started::set);
            context = recording(started.get());
            host.activateTerminalWindow(host.addTerminalWindow());
            tab = context.windows().openTab(new ContentSpec("save-test", "Page"), null,
                context.kit().create(new ViewSpec(new Nodes.Text("save-test.text", ControlState.READY, "page", Nodes.TextRole.BODY)), event -> { }),
                TabPlacement.NEW_TAB);
            page = new Page(URI.create(ADDRESS), () -> { }, PageTestAccess.noFavicons(), Runnable::run, PageContext.persistent());
            PageTestAccess.useScriptRunner(page, (script, url) -> ran.add(url + "\n" + script));
            PageTestAccess.loadStarted(page, ADDRESS);
            PageTestAccess.addressChanged(page, ADDRESS);
            return null;
        });
    }

    @AfterEach void close() throws Exception {
        onUi(() -> {
            page.close();
            host.close();
            return null;
        });
    }

    /** D7 and F26: the chooser starts at the host's folder in the Downloads folder, without the user name; Cancel does nothing. */
    @Test void theChooserStartsAtTheHostsFolderAndCancelDoesNothing() throws Exception {
        SavePage save = start(List.of());
        assertThat(chooser).containsExactly("Save Page As|" + Optional.of(downloads.resolve("example.com")));
        assertThat(ran).isEmpty();
        assertThat(save.done()).isTrue();
        assertThat(timers).isEmpty();
        assertThat(onUi(() -> host.windowView(ID, SavePage.FAILED))).isEmpty();
        assertThat(shown).isEmpty();
        assertThat(clients).hasValue(0);
    }

    /** D7: a host already saved offers its next free folder, numbered at its end, as Finder and Explorer number a folder. */
    @Test void aSecondSaveOfAHostOffersTheNextFreeFolder() throws Exception {
        Files.createDirectories(downloads.resolve("example.com"));
        Files.writeString(downloads.resolve("example.com (1)"), "taken");
        start(List.of());
        assertThat(chooser).containsExactly("Save Page As|" + Optional.of(downloads.resolve("example.com (2)")));
    }

    /**
     * Ruling D43: a save holds its suggested folder's name until it ends, so a second save of the host meanwhile is offered
     * the next one; once the first has ended, its name is free again.
     */
    @Test void twoSavesOfOneHostAreOfferedTwoFolders() throws Exception {
        SavePage first = start(List.of(downloads.resolve("example.com")));
        assertThat(first.done()).isFalse();
        start(List.of());
        assertThat(chooser).containsExactly("Save Page As|" + Optional.of(downloads.resolve("example.com")),
            "Save Page As|" + Optional.of(downloads.resolve("example.com (1)")));
        onUi(() -> {
            first.loadStarted();
            host.windowView(ID, SavePage.FAILED).orElseThrow().emit(new UiEvent.Action(SavePage.FAILED_OK));
            return null;
        });
        start(List.of());
        assertThat(chooser.getLast()).as("both have ended").isEqualTo("Save Page As|" + Optional.of(downloads.resolve("example.com")));
    }

    /**
     * A chosen folder runs {@code save-page.js} in the page with a fresh prefix and waits 30 seconds for its answer; the
     * answer is saved on the background executor, each file marked, and the folder shown in the file manager.
     */
    @Test void aChosenFolderIsSavedAndShown() throws Exception {
        Path folder = downloads.resolve("example.com");
        SavePage save = start(List.of(folder));
        assertThat(ran).hasSize(1);
        assertThat(ran.getFirst()).startsWith(ToolScripts.SAVE_PAGE_URL + "\n" + ToolScripts.savePageScript());
        String prefix = prefix();
        assertThat(prefix).matches("jasper-save-[0-9a-f]{16}-");
        assertThat(delays).containsExactly(Duration.ofSeconds(30));
        assertThat(save.done()).isFalse();

        onUi(() -> {
            save.part(part("jasper-save-ffffffffffffffff-", 0, 1, answer("jasper-save-ffffffffffffffff-")));
            save.part(part(prefix, 0, 1, answer(prefix)));
            return null;
        });
        assertThat(cancelled).as("the answer's wait ends").hasValue(1);
        assertThat(folder.resolve("index.html")).as("not on the UI thread").doesNotExist();
        host.runBackground();
        flush();
        assertThat(Files.readString(folder.resolve("index.html"))).isEqualTo("<!DOCTYPE html>\n<html><body><p>Saved</p></body></html>");
        assertThat(host.markedDownloads()).containsExactly(new FakePluginHost.MarkedDownload(folder.resolve("index.html"),
            URI.create("https://example.com/news/"), Optional.of(URI.create("https://example.com/news/"))));
        assertThat(shown).containsExactly("BROWSE_FILE_DIR " + folder);
        assertThat(clients).as("one client, made for the save and shut down").hasValue(1);
        assertThat(save.done()).isTrue();
        assertThat(onUi(() -> host.windowView(ID, SavePage.FAILED))).isEmpty();
        assertThat(notices()).isEmpty();
    }

    /** A failure shows "Save Failed" over the item, with the reason, and OK closes it. */
    @Test void aFailureSaysSaveFailedOverTheItem() throws Exception {
        Path file = Files.writeString(downloads.resolve("example.com"), "a file");
        SavePage save = start(List.of(file));
        String prefix = prefix();
        onUi(() -> {
            save.part(part(prefix, 0, 1, answer(prefix)));
            return null;
        });
        host.runBackground();
        flush();
        assertThat(message()).isEqualTo("Could not save the page: " + file + " is a file.");
        onUi(() -> {
            host.windowView(ID, SavePage.FAILED).orElseThrow().emit(new UiEvent.Action(SavePage.FAILED_OK));
            return null;
        });
        assertThat(onUi(() -> host.windowView(ID, SavePage.FAILED))).isEmpty();
        assertThat(shown).isEmpty();
        assertThat(Files.readString(file)).isEqualTo("a file");
    }

    /** F26: a load start while the parts arrive fails the save: the snapshot would be of another document. */
    @Test void aLoadStartWhileTheAnswerComesFails() throws Exception {
        SavePage save = start(List.of(downloads.resolve("example.com")));
        String prefix = prefix();
        onUi(() -> {
            save.loadStarted();
            save.part(part(prefix, 0, 1, answer(prefix)));
            return null;
        });
        assertThat(host.runBackground()).isZero();
        assertThat(message()).isEqualTo("Could not save the page: the page navigated away.");
        assertThat(downloads.resolve("example.com")).doesNotExist();
        assertThat(cancelled).hasValue(1);
    }

    /** F26: a page that navigates while the chooser is open fails the save, and runs no script. */
    @Test void aNavigationWhileTheChooserIsOpenFails() throws Exception {
        host.duringPathSelection(() -> PageTestAccess.loadStarted(page, "https://elsewhere.example/"));
        start(List.of(downloads.resolve("example.com")));
        assertThat(ran).isEmpty();
        assertThat(message()).isEqualTo("Could not save the page: the page navigated away.");
    }

    /** No answer within 30 seconds fails the save. */
    @Test void noAnswerFails() throws Exception {
        SavePage save = start(List.of(downloads.resolve("example.com")));
        onUi(() -> {
            timers.getFirst().run();
            return null;
        });
        assertThat(message()).isEqualTo("Could not save the page: the page did not answer.");
        assertThat(save.done()).isTrue();
    }

    /** F27: an answer that is not one fails at once, and so does one of more than 64 parts. */
    @Test void aMalformedOrTooLargeAnswerFails() throws Exception {
        SavePage save = start(List.of(downloads.resolve("example.com")));
        String prefix = prefix();
        onUi(() -> {
            save.part(part(prefix, 0, 65, ""));
            return null;
        });
        assertThat(message()).isEqualTo("Could not save the page: the page is too large to save.");
        onUi(() -> {
            host.windowView(ID, SavePage.FAILED).orElseThrow().emit(new UiEvent.Action(SavePage.FAILED_OK));
            return null;
        });
        SavePage again = start(List.of(downloads.resolve("example.com")));
        String second = prefix();
        onUi(() -> {
            again.part(part(second, 0, 1, "{\"prefix\":\"" + second + "\"}"));
            return null;
        });
        host.runBackground();
        flush();
        assertThat(message()).isEqualTo("Could not save the page: the page's answer could not be read.");
    }

    /** Once the tab has closed, a failure is a warning notification instead, as there is no item to show it over. */
    @Test void aFailureAfterTheTabClosedIsAWarning() throws Exception {
        Path file = Files.writeString(downloads.resolve("example.com"), "a file");
        SavePage save = start(List.of(file));
        String prefix = prefix();
        onUi(() -> {
            save.part(part(prefix, 0, 1, answer(prefix)));
            tab.close();
            return null;
        });
        host.runBackground();
        flush();
        assertThat(onUi(() -> host.windowView(ID, SavePage.FAILED))).isEmpty();
        assertThat(notices()).containsExactly("dev.jasper.browser.downloads|WARNING|Save Failed|Could not save the page: " + file + " is a file.");
    }

    /** The tab closing while the answer is awaited ends the save, with a warning. */
    @Test void theTabClosingWhileTheAnswerComesEndsTheSave() throws Exception {
        SavePage save = start(List.of(downloads.resolve("example.com")));
        onUi(() -> {
            tab.close();
            save.pageClosed();
            return null;
        });
        assertThat(save.done()).isTrue();
        assertThat(notices()).containsExactly("dev.jasper.browser.downloads|WARNING|Save Failed|Could not save the page: the page closed.");
    }

    /** F13: a mark that fails leaves the save done and shown, and posts one warning. */
    @Test void aFailedMarkPostsOneWarning() throws Exception {
        host.failMarks(new IOException("This volume cannot keep a downloaded-file mark"));
        Path folder = downloads.resolve("example.com");
        SavePage save = start(List.of(folder));
        String prefix = prefix();
        onUi(() -> {
            save.part(part(prefix, 0, 1, answer(prefix)));
            return null;
        });
        host.runBackground();
        flush();
        assertThat(shown).containsExactly("BROWSE_FILE_DIR " + folder);
        assertThat(notices()).containsExactly("dev.jasper.browser.downloads|WARNING|\u201Cexample.com\u201D has files without a downloaded-file mark|"
            + "Jasper could not mark them as downloaded from the internet, so the system may open them without checking them first: "
            + "This volume cannot keep a downloaded-file mark. Open them from the file manager only if you trust them.");
    }

    /** Starts a save over the stand-in page, the chooser given {@code chosen}, and runs it up to the script. */
    private SavePage start(List<Path> chosen) throws Exception {
        int asked = chooser.size();
        onUi(() -> {
            host.queuePathSelection(chosen);
            return null;
        });
        SavePage save = onUi(() -> SavePage.start(page, tab, context, downloads, names, DownloadsTestAccess.recordingFiles(Runnable::run, shown),
            new SavePage.Network(Optional.empty(), () -> true, () -> true, proxy -> {
                clients.incrementAndGet();
                return PageSaver.client(proxy);
            }), (delay, task) -> {
                delays.add(delay);
                timers.add(task);
                return cancelled::incrementAndGet;
            }));
        assertThat(chooser).as("the folder is found on the background executor first").hasSize(asked);
        host.runBackground();
        flush();
        return save;
    }

    /** The prefix the last script ran with. */
    private String prefix() {
        String script = ran.getLast();
        int start = script.lastIndexOf("\n(\"") + 3;
        return script.substring(start, script.indexOf('"', start));
    }

    /** The snapshot of a page that names no resource, as {@code save-page.js} answers. */
    private static String answer(String prefix) {
        var answer = new LinkedHashMap<String, Object>();
        answer.put("prefix", prefix);
        answer.put("html", "<!DOCTYPE html>\n<html><body><p>Saved</p></body></html>");
        answer.put("resources", List.of());
        answer.put("styles", List.of());
        answer.put("attributes", List.of());
        answer.put("base", "https://example.com/news/");
        return StrictJson.write(answer);
    }

    private static String part(String prefix, int part, int parts, String text) {
        var request = new LinkedHashMap<String, Object>();
        request.put("op", "savePage");
        request.put("save", prefix);
        request.put("part", (long) part);
        request.put("parts", (long) parts);
        request.put("text", text);
        return StrictJson.write(request);
    }

    /** The open "Save Failed" dialog's message. */
    private String message() throws Exception {
        return onUi(() -> {
            FakeKitView view = host.windowView(ID, SavePage.FAILED).orElseThrow(() -> new AssertionError("no Save Failed"));
            var form = (Nodes.Form) view.spec().root();
            return ((Nodes.Text) form.sections().getFirst()).text();
        });
    }

    private List<String> notices() throws Exception {
        return onUi(() -> host.notifications().lines().stream().filter(line -> line.startsWith("dev.jasper.browser.downloads|")).toList());
    }

    /** The context, whose windows record each save chooser asked for as {@code title|initial}. */
    private PluginContext recording(PluginContext real) {
        return wrapped(PluginContext.class, real);
    }

    @SuppressWarnings("unchecked")
    private <T> T wrapped(Class<T> type, T target) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (type == Windows.class && method.getName().equals("chooseSaveFile")) chooser.add(args[1] + "|" + args[2]);
            Object result;
            try {
                result = method.invoke(target, args);
            } catch (InvocationTargetException thrown) {
                throw thrown.getCause();
            }
            return result instanceof Windows windows && type == PluginContext.class ? wrapped(Windows.class, windows) : result;
        });
    }

    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
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

package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.InsertedText;
import dev.jasper.sdk.ui.TabPlacement;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 15's Snippets in page fields, as a browser item takes text another plugin inserts: into the page's focused
 * field, then Return when asked; with none, a paste with the clipboard, which gets its text back, then Return. The page
 * is made by a stand-in, as a page cannot be made without Chromium: the test answers its insertions, and records its
 * pastes and Returns with what the clipboard held at each. The item's delays wait for the test ({@link Later}).
 */
class BrowserItemInsertTest {
    private static final String ID = "dev.jasper.browser";
    private final MemoryStores memory = new MemoryStores();
    private final Later later = new Later();
    private final List<String> log = new CopyOnWriteArrayList<>();
    // The answer the page gives its next insertion.
    private CompletableFuture<Boolean> answer = new CompletableFuture<>();
    private FakePluginHost host;

    @AfterEach void close() throws Exception {
        if (host != null) onUi(() -> {
            host.close();
            return null;
        });
        memory.close();
    }

    @Test void aFieldThatTakesTheTextGetsReturnOnceWhenAskedAndTheClipboardIsLeftAlone() throws Exception {
        BrowserItem item = open();
        onUi(() -> host.clipboard().setText("old"));
        answer = CompletableFuture.completedFuture(true);
        assertThat(onUi(() -> host.insertText(item.tab(), new InsertedText("ls -la", true)))).as("the item takes text").isTrue();
        onUi(() -> { });
        assertThat(log).containsExactly("insert:ls -la", "return:old");
        onUi(() -> host.insertText(item.tab(), new InsertedText("pwd", false)));
        onUi(() -> { });
        assertThat(log).as("no Return unasked").containsExactly("insert:ls -la", "return:old", "insert:pwd");
        assertThat(host.clipboard().copies()).isEmpty();
        assertThat(onUi(later::waiting)).isEmpty();
        assertThat(onUi(() -> host.failures())).isEmpty();
    }

    @Test void withNoFieldTheTextIsPastedTheClipboardComesBackAndThenReturnIsPressed() throws Exception {
        BrowserItem item = open();
        onUi(() -> host.clipboard().setText("old"));
        answer = CompletableFuture.completedFuture(false);
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        onUi(() -> { });
        assertThat(log).as("pasted with the snippet on the clipboard").containsExactly("insert:make deploy", "paste:make deploy");
        assertThat(onUi(later::waiting)).containsExactly(Duration.ofMillis(ClipboardPaste.RESTORE_MILLIS));
        onUi(later::end);
        assertThat(log).as("Return after the clipboard is back").containsExactly("insert:make deploy", "paste:make deploy", "return:old");
        assertThat(host.clipboard().text()).contains("old");
        assertThat(onUi(() -> host.failures())).isEmpty();
    }

    @Test void withNoFieldAndNoReturnAskedOnlyThePasteHappens() throws Exception {
        BrowserItem item = open();
        onUi(() -> host.clipboard().setText("old"));
        answer = CompletableFuture.completedFuture(false);
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", false)));
        onUi(() -> { });
        onUi(later::end);
        assertThat(log).containsExactly("insert:make deploy", "paste:make deploy");
        assertThat(host.clipboard().text()).contains("old");
    }

    @Test void anItemClosedBeforeThePageAnswersDoesNothing() throws Exception {
        BrowserItem item = open();
        onUi(() -> host.clipboard().setText("old"));
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        onUi(item::close);
        onUi(() -> answer.complete(false));
        onUi(() -> { });
        assertThat(log).containsExactly("insert:make deploy");
        assertThat(host.clipboard().copies()).as("nothing copied").isEmpty();
        assertThat(onUi(later::waiting)).isEmpty();
        assertThat(onUi(() -> host.insertText(item.tab(), new InsertedText("pwd", true)))).as("a closed item takes no text").isFalse();
        assertThat(log).containsExactly("insert:make deploy");
    }

    /** The user's clipboard comes back however the item ends; the closed item presses nothing. */
    @Test void anItemClosedWhileThePasteWaitsGetsNoReturnButTheClipboardComesBack() throws Exception {
        BrowserItem item = open();
        onUi(() -> host.clipboard().setText("old"));
        answer = CompletableFuture.completedFuture(false);
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        onUi(() -> { });
        onUi(item::close);
        onUi(later::end);
        assertThat(log).containsExactly("insert:make deploy", "paste:make deploy");
        assertThat(host.clipboard().text()).contains("old");
    }

    /** An insertion DevTools did not answer in time may yet go in: the item neither pastes nor presses Return. */
    @Test void anInsertionWithNoAnswerInTimeDoesNothingMore() throws Exception {
        BrowserItem item = open();
        onUi(() -> host.clipboard().setText("old"));
        answer = CompletableFuture.failedFuture(new TimeoutException());
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        onUi(() -> { });
        assertThat(log).containsExactly("insert:make deploy");
        assertThat(host.clipboard().copies()).isEmpty();
        assertThat(onUi(later::waiting)).isEmpty();
    }

    /**
     * An item of a stand-in plugin, the test's one, on the test's fake host, with stores in memory; its page's insertions,
     * pastes and Returns go to the test.
     */
    private BrowserItem open() throws Exception {
        return onUi(() -> {
            host = new FakePluginHost();
            var context = new AtomicReference<PluginContext>();
            var settings = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of(Capabilities.CLIPBOARD_READ)), Set.of(), Set.of(), plugin -> {
                context.set(plugin);
                settings.set(BrowserSettings.declare(plugin.settings()));
            });
            var items = new BrowserItems(context.get(), settings.get()::read, memory.make(), page -> { }, later, request -> new CompletableFuture<>());
            BrowserItem item = items.open(URI.create("https://example.com/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            Page page = item.page();
            PageTestAccess.useTextInserter(page, (inserting, text) -> {
                log.add("insert:" + text);
                return answer;
            });
            PageTestAccess.usePaster(page, pasting -> log.add("paste:" + host.clipboard().text().orElse("")));
            PageTestAccess.useReturnPresser(page, pressing -> log.add("return:" + host.clipboard().text().orElse("")));
            return item;
        });
    }

    private static <T> T onUi(Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
        return result.get();
    }

    private static void onUi(Runnable task) throws Exception {
        onUi(() -> {
            task.run();
            return null;
        });
    }

    /** The UI thread's delays, which wait until the test ends them. UI thread. */
    private static final class Later implements BiFunction<Duration, Runnable, Runnable> {
        private final List<Delayed> waiting = new ArrayList<>();

        private record Delayed(Duration delay, Runnable task) { }

        @Override public Runnable apply(Duration delay, Runnable task) {
            var delayed = new Delayed(delay, task);
            waiting.add(delayed);
            return () -> waiting.remove(delayed);
        }

        List<Duration> waiting() {
            return waiting.stream().map(Delayed::delay).toList();
        }

        /** Each delay waiting ends, and its task runs. */
        void end() {
            var due = List.copyOf(waiting);
            waiting.clear();
            due.forEach(delayed -> delayed.task().run());
        }
    }
}

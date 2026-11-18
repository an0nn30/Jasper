package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.InsertedText;
import dev.jasper.sdk.ui.TabPlacement;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 15's Snippets in page fields, as a browser item takes text another plugin inserts (H30, H38). The text
 * goes into the page's focused field through the isolated world's script, then Return when asked. When the script
 * answers that the focus is where it cannot reach, as in a cross-origin frame, the text is typed with DevTools'
 * {@code Input.insertText}, then Return. When it answers that the focus takes no text, as a button's or a read-only
 * field's, nothing more happens: Return would press the page's control. A main-frame load start after the insertion
 * began skips the typing and Return. Any other outcome inserts nothing more, and the clipboard is never touched. The
 * page is made by a stand-in, as a page cannot be made without Chromium: the test answers its script and its typing,
 * and records its Returns.
 */
class BrowserItemInsertTest {
    private static final String ID = "dev.jasper.browser";
    private final MemoryStores memory = new MemoryStores();
    private final List<String> log = new CopyOnWriteArrayList<>();
    // The answers the page gives its next script and its next typing.
    private CompletableFuture<Page.Insertion> script = new CompletableFuture<>();
    private CompletableFuture<Void> typing = CompletableFuture.completedFuture(null);
    private FakePluginHost host;

    @AfterEach void close() throws Exception {
        if (host != null) onUi(() -> {
            assertThat(host.clipboard().copies()).as("the clipboard is never touched").isEmpty();
            host.close();
            return null;
        });
        memory.close();
    }

    @Test void aFieldThatTakesTheTextGetsReturnOnceWhenAskedAndNothingIsTyped() throws Exception {
        BrowserItem item = open(true);
        script = CompletableFuture.completedFuture(Page.Insertion.INSERTED);
        assertThat(onUi(() -> host.insertText(item.tab(), new InsertedText("ls -la", true)))).as("the item takes text").isTrue();
        settle();
        assertThat(log).containsExactly("field:ls -la", "return");
        onUi(() -> host.insertText(item.tab(), new InsertedText("pwd", false)));
        settle();
        assertThat(log).as("no Return unasked").containsExactly("field:ls -la", "return", "field:pwd");
        assertThat(onUi(() -> host.failures())).isEmpty();
    }

    @Test void aFocusTheScriptCannotReachIsTypedIntoThenReturnIsPressed() throws Exception {
        BrowserItem item = open(true);
        script = CompletableFuture.completedFuture(Page.Insertion.UNREACHABLE);
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        settle();
        assertThat(log).containsExactly("field:make deploy", "type:make deploy", "return");
        onUi(() -> host.insertText(item.tab(), new InsertedText("make test", false)));
        settle();
        assertThat(log).as("no Return unasked").endsWith("field:make test", "type:make test").hasSize(5);
        assertThat(onUi(() -> host.failures())).isEmpty();
    }

    /**
     * H38: a focus that takes no text, as a button, a link, a read-only field or the page's body, gets no typing and no
     * Return.
     */
    @Test void aFocusThatTakesNoTextGetsNoTypingAndNoReturn() throws Exception {
        BrowserItem item = open(true);
        script = CompletableFuture.completedFuture(Page.Insertion.NOT_EDITABLE);
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        settle();
        assertThat(log).containsExactly("field:make deploy");
        assertThat(onUi(() -> host.failures())).isEmpty();
    }

    /** H38: a main-frame load start after the insertion began skips the typing, and Return in the next document. */
    @Test void aLoadStartDuringTheInsertionSkipsTheTypingAndReturn() throws Exception {
        BrowserItem item = open(true);
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        onUi(() -> PageTestAccess.loadStarted(item.page(), "https://example.com/next"));
        onUi(() -> script.complete(Page.Insertion.UNREACHABLE));
        settle();
        assertThat(log).as("no typing").containsExactly("field:make deploy");
        script = new CompletableFuture<>();
        onUi(() -> host.insertText(item.tab(), new InsertedText("ls", true)));
        onUi(() -> PageTestAccess.loadStarted(item.page(), "https://example.com/later"));
        onUi(() -> script.complete(Page.Insertion.INSERTED));
        settle();
        assertThat(log).as("no Return after a field took it").containsExactly("field:make deploy", "field:ls");
        script = CompletableFuture.completedFuture(Page.Insertion.UNREACHABLE);
        typing = new CompletableFuture<>();
        onUi(() -> host.insertText(item.tab(), new InsertedText("pwd", true)));
        settle();
        onUi(() -> PageTestAccess.loadStarted(item.page(), "https://example.com/last"));
        onUi(() -> typing.complete(null));
        settle();
        assertThat(log).as("no Return after the typing").containsExactly("field:make deploy", "field:ls", "field:pwd", "type:pwd");
    }

    /** Return waits for the typing, and typing that fails, late or refused, gets none. */
    @Test void typingThatFailsGetsNoReturn() throws Exception {
        BrowserItem item = open(true);
        script = CompletableFuture.completedFuture(Page.Insertion.UNREACHABLE);
        typing = CompletableFuture.failedFuture(new TimeoutException());
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        settle();
        assertThat(log).containsExactly("field:make deploy", "type:make deploy");
    }

    /** A script DevTools did not answer, or answered with an error, may yet have run: nothing is typed or pressed. */
    @Test void aScriptThatFailsDoesNothingMore() throws Exception {
        BrowserItem item = open(true);
        script = CompletableFuture.failedFuture(new TimeoutException());
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        settle();
        assertThat(log).containsExactly("field:make deploy");
    }

    /** A page without its browser yet, as a restored item before it loads, inserts nothing and presses nothing. */
    @Test void aPageWithoutItsBrowserDoesNothing() throws Exception {
        BrowserItem item = open(false);
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        settle();
        assertThat(log).isEmpty();
    }

    @Test void anItemClosedBeforeTheScriptAnswersDoesNothing() throws Exception {
        BrowserItem item = open(true);
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        onUi(item::close);
        onUi(() -> script.complete(Page.Insertion.UNREACHABLE));
        settle();
        assertThat(log).containsExactly("field:make deploy");
        assertThat(onUi(() -> host.insertText(item.tab(), new InsertedText("pwd", true)))).as("a closed item takes no text").isFalse();
        assertThat(log).containsExactly("field:make deploy");
    }

    @Test void anItemClosedBeforeTheTypingAnswersPressesNothing() throws Exception {
        BrowserItem item = open(true);
        script = CompletableFuture.completedFuture(Page.Insertion.UNREACHABLE);
        typing = new CompletableFuture<>();
        onUi(() -> host.insertText(item.tab(), new InsertedText("make deploy", true)));
        settle();
        onUi(item::close);
        onUi(() -> typing.complete(null));
        settle();
        assertThat(log).containsExactly("field:make deploy", "type:make deploy");
    }

    /**
     * An item of a stand-in plugin, the test's one, on the test's fake host, with stores in memory. Its page's Returns go
     * to the test, and with {@code answering}, so do its scripts and typing; without, they are the page's own, which has
     * no browser.
     */
    private BrowserItem open(boolean answering) throws Exception {
        return onUi(() -> {
            host = new FakePluginHost();
            var context = new AtomicReference<PluginContext>();
            var settings = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                context.set(plugin);
                settings.set(BrowserSettings.declare(plugin.settings()));
            });
            var items = new BrowserItems(context.get(), settings.get()::read, new DataStores(memory.make(), () -> false), Optional.empty(), page -> { },
                (Duration delay, Runnable task) -> () -> { }, request -> new CompletableFuture<>());
            BrowserItem item = items.open(URI.create("https://example.com/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            Page page = item.page();
            if (answering) {
                PageTestAccess.useFieldInserter(page, (inserting, text) -> {
                    log.add("field:" + text);
                    return script;
                });
                PageTestAccess.useTextInserter(page, (typed, text) -> {
                    log.add("type:" + text);
                    return typing;
                });
            }
            PageTestAccess.useReturnPresser(page, pressing -> log.add("return"));
            return item;
        });
    }

    /** The item's answers, which come back to the UI thread, have arrived. */
    private static void settle() throws Exception {
        onUi(() -> { });
        onUi(() -> { });
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
}

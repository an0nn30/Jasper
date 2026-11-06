package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
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
 * Spec section 15's Copy on select in a browser item: each selection gesture its page tells, while
 * {@code Platform.copyOnSelect()} is on, reads the page's selection and copies its text, if any, with the plugin's
 * clipboard on the UI thread. A selection in a frame of another origin copies nothing, not even through Chromium's own
 * copy, which would let that frame's scripts write the clipboard (F2). One read runs at a time, and only the latest is
 * copied (H34); a read dropped for a later one keeps its text for it (H36). The page is a stand-in, as a page cannot be
 * made without Chromium: the test fires its gestures, answers its selection reads, as {@code selection.js} would with its
 * flag where that matters, and records any copy Chromium would run.
 */
class SelectionCopyTest {
    private static final String ID = "dev.jasper.browser";
    private final MemoryStores memory = new MemoryStores();
    private final List<String> log = new CopyOnWriteArrayList<>();
    // What the page answers its next selection read.
    private CompletableFuture<Page.Selection> selection = CompletableFuture.completedFuture(new Page.Selection.Text("abc"));
    // With flagged, the page answers as selection.js does with its flag (H35): what is selected, and whether a
    // selectionchange came since the last read, which each read consumes as it runs; the answer then waits for stall.
    private boolean flagged;
    private String selected = "";
    private boolean changed;
    private CompletableFuture<Void> stall = CompletableFuture.completedFuture(null);
    private FakePluginHost host;
    private Page page;

    @AfterEach void close() throws Exception {
        if (host != null) onUi(host::close);
        memory.close();
    }

    @Test void withCopyOnSelectOffTheSelectionIsNeverRead() throws Exception {
        open(false);
        gesture();
        assertThat(log).isEmpty();
        assertThat(copies()).isEmpty();
    }

    @Test void aSelectedTextIsCopied() throws Exception {
        open(true);
        gesture();
        assertThat(log).containsExactly("read");
        assertThat(copies()).containsExactly("copy|" + ID + "|abc");
        assertThat(onUi(() -> host.failures())).isEmpty();
    }

    @Test void anEmptySelectionLeavesTheClipboard() throws Exception {
        open(true);
        onUi(() -> host.clipboard().setText("kept"));
        selection = CompletableFuture.completedFuture(new Page.Selection.Text(""));
        gesture();
        selection = CompletableFuture.completedFuture(new Page.Selection.None());
        gesture();
        assertThat(log).containsExactly("read", "read");
        assertThat(copies()).isEmpty();
        assertThat(onUi(() -> host.clipboard().text())).contains("kept");
    }

    /** F2: a cross-origin frame's selection is not copied on select, and Chromium's copy, which runs its scripts, never runs. */
    @Test void aSelectionInAFrameOfAnotherOriginCopiesNothing() throws Exception {
        open(true);
        selection = CompletableFuture.completedFuture(new Page.Selection.InFrame());
        gesture();
        assertThat(log).containsExactly("read");
        assertThat(copies()).isEmpty();
    }

    /** The setting is read at each gesture: turned off between two, the second copies nothing. */
    @Test void turningCopyOnSelectOffBetweenTwoGesturesStopsTheSecondCopy() throws Exception {
        open(true);
        gesture();
        host.copyOnSelect(false);
        selection = CompletableFuture.completedFuture(new Page.Selection.Text("def"));
        gesture();
        assertThat(log).containsExactly("read");
        assertThat(copies()).containsExactly("copy|" + ID + "|abc");
        host.copyOnSelect(true);
        gesture();
        assertThat(copies()).containsExactly("copy|" + ID + "|abc", "copy|" + ID + "|def");
    }

    /** A page script's own selection is no gesture, so without one nothing is read or copied. */
    @Test void withNoGestureNothingIsCopied() throws Exception {
        open(true);
        settle();
        assertThat(log).isEmpty();
        assertThat(copies()).isEmpty();
    }

    /** F33: the read completes off the UI thread, where the clipboard cannot be written; the copy waits for the UI thread. */
    @Test void aReadAnsweredOffTheUiThreadCopiesOnIt() throws Exception {
        open(true);
        selection = new CompletableFuture<>();
        gesture();
        var answering = new Thread(() -> selection.complete(new Page.Selection.Text("off")));
        answering.start();
        answering.join();
        settle();
        assertThat(copies()).containsExactly("copy|" + ID + "|off");
    }

    /** A page without its browser yet, as a restored item before it loads, has nothing selected, through its own reader. */
    @Test void aPageWithoutItsBrowserCopiesNothing() throws Exception {
        open(true, false);
        gesture();
        assertThat(onUi(() -> page.selectedText().getNow(null))).isEqualTo(new Page.Selection.None());
        assertThat(log).isEmpty();
        assertThat(copies()).isEmpty();
    }

    /**
     * H34: one read at a time. Gestures during a read the page has not answered start none; once it answers, the
     * selection is read once more, and only that latest read's answer is copied, so a stalled page queues no late copies.
     */
    @Test void gesturesDuringAReadReadOnceMoreAndOnlyTheLatestCopies() throws Exception {
        open(true);
        var stalled = new CompletableFuture<Page.Selection>();
        selection = stalled;
        gesture();
        var latest = new CompletableFuture<Page.Selection>();
        selection = latest;
        gesture();
        gesture();
        assertThat(log).as("no second read while one is under way").containsExactly("read");
        onUi(() -> stalled.complete(new Page.Selection.Text("stale")));
        settle();
        assertThat(log).as("one more read for the gestures meanwhile").containsExactly("read", "read");
        assertThat(copies()).as("the stale answer is not copied").isEmpty();
        onUi(() -> latest.complete(new Page.Selection.Text("latest")));
        settle();
        assertThat(copies()).containsExactly("copy|" + ID + "|latest");
        selection = CompletableFuture.completedFuture(new Page.Selection.Text("next"));
        gesture();
        assertThat(log).as("reads start again once none is under way").containsExactly("read", "read", "read");
        assertThat(copies()).containsExactly("copy|" + ID + "|latest", "copy|" + ID + "|next");
    }

    /** H32: the page menu's Select All is a selection gesture: once the canvas's 100 ms pass, the selection is read once. */
    @Test void theMenusSelectAllReadsTheSelectionOnce() throws Exception {
        open(true);
        onUi(() -> page.selectAll());
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (log.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(log).as("read within 10 s").containsExactly("read");
        Thread.sleep(300);
        settle();
        assertThat(log).as("once").containsExactly("read");
        assertThat(copies()).containsExactly("copy|" + ID + "|abc");
    }

    /**
     * H36: a read dropped for a later gesture (H34) keeps its text. In the page, the dropped read already consumed the
     * selection's change (H35), so the read after it finds the selection unchanged, and the kept text is copied, once.
     */
    @Test void aDroppedReadsTextIsCopiedWhenTheReadAfterItFindsTheSelectionUnchanged() throws Exception {
        openFlagged();
        var stalled = new CompletableFuture<Void>();
        stall = stalled;
        select("ls -la");
        gesture();
        stall = CompletableFuture.completedFuture(null);
        gesture();
        onUi(() -> stalled.complete(null));
        settle();
        assertThat(log).containsExactly("read", "read");
        assertThat(copies()).containsExactly("copy|" + ID + "|ls -la");
        gesture();
        assertThat(log).as("a gesture that changed nothing, as a scrollbar drag").hasSize(3);
        assertThat(copies()).as("copied once").hasSize(1);
    }

    /** H36: when the read after a dropped one finds a selection made since, that one is copied instead. */
    @Test void aReadAfterADroppedOneThatFindsANewSelectionCopiesThatInstead() throws Exception {
        openFlagged();
        var stalled = new CompletableFuture<Void>();
        stall = stalled;
        select("old");
        gesture();
        stall = CompletableFuture.completedFuture(null);
        select("new");
        gesture();
        onUi(() -> stalled.complete(null));
        settle();
        assertThat(copies()).containsExactly("copy|" + ID + "|new");
    }

    /** H36: a kept text outlasts reads dropped meanwhile that found the selection unchanged, and is copied by the last. */
    @Test void aKeptTextOutlastsADroppedReadThatFoundTheSelectionUnchanged() throws Exception {
        openFlagged();
        var first = new CompletableFuture<Void>();
        stall = first;
        select("kept");
        gesture();
        var second = new CompletableFuture<Void>();
        stall = second;
        gesture();
        onUi(() -> first.complete(null));
        settle();
        stall = CompletableFuture.completedFuture(null);
        gesture();
        onUi(() -> second.complete(null));
        settle();
        assertThat(log).containsExactly("read", "read", "read");
        assertThat(copies()).containsExactly("copy|" + ID + "|kept");
    }

    /**
     * H36's cost: a read that ran in the page but failed, as one DevTools answered too late, consumed the change and
     * keeps no text, so the next read finds the selection unchanged and copies nothing.
     */
    @Test void aReadThatRanButFailedLeavesTheNextUnchangedReadNothingToCopy() throws Exception {
        openFlagged();
        stall = CompletableFuture.failedFuture(new TimeoutException());
        select("lost");
        gesture();
        stall = CompletableFuture.completedFuture(null);
        gesture();
        assertThat(log).containsExactly("read", "read");
        assertThat(copies()).isEmpty();
    }

    @Test void aReadThatFailsCopiesNothing() throws Exception {
        open(true);
        selection = CompletableFuture.failedFuture(new TimeoutException());
        gesture();
        assertThat(log).containsExactly("read");
        assertThat(copies()).isEmpty();
    }

    /** What is copied follows the setting when the answer arrives, and an item closed by then copies nothing. */
    @Test void anAnswerAfterTheSettingTurnedOffOrTheItemClosedCopiesNothing() throws Exception {
        BrowserItem item = open(true);
        selection = new CompletableFuture<>();
        gesture();
        host.copyOnSelect(false);
        onUi(() -> selection.complete(new Page.Selection.Text("late")));
        settle();
        host.copyOnSelect(true);
        selection = new CompletableFuture<>();
        gesture();
        onUi(item::close);
        onUi(() -> selection.complete(new Page.Selection.Text("closed")));
        settle();
        assertThat(log).containsExactly("read", "read");
        assertThat(copies()).isEmpty();
    }

    /**
     * An item of a stand-in plugin, the test's one, on the test's fake host with Copy on select {@code on}, with stores
     * in memory. Its page's selection reads go to the test, and so do Chromium copies.
     */
    private BrowserItem open(boolean on) throws Exception {
        return open(on, true);
    }

    /** As {@link #open(boolean)}; without {@code answering}, the page reads its selection itself, and has no browser. */
    private BrowserItem open(boolean on, boolean answering) throws Exception {
        return onUi(() -> {
            host = new FakePluginHost();
            host.copyOnSelect(on);
            var context = new AtomicReference<PluginContext>();
            var settings = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                context.set(plugin);
                settings.set(BrowserSettings.declare(plugin.settings()));
            });
            var items = new BrowserItems(context.get(), settings.get()::read, memory.make(), started -> { },
                (Duration delay, Runnable task) -> () -> { }, request -> new CompletableFuture<>());
            BrowserItem item = items.open(URI.create("https://example.com/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            page = item.page();
            if (answering) PageTestAccess.useSelectionReader(page, read -> {
                log.add("read");
                if (!flagged) return selection;
                Page.Selection found = changed ? new Page.Selection.Text(selected) : new Page.Selection.Unchanged();
                changed = false;
                return stall.thenApply(released -> found);
            });
            PageTestAccess.useCopier(page, copying -> log.add("chromium copy"));
            return item;
        });
    }

    /** As {@link #open(boolean)} with Copy on select on, the page answering as {@code selection.js} would with its flag. */
    private void openFlagged() throws Exception {
        flagged = true;
        open(true);
    }

    /** The user selects {@code text} in the page, which tells Jasper's world a selectionchange. */
    private void select(String text) {
        selected = text;
        changed = true;
    }

    /** The page tells a selection gesture, as its canvas does 100 ms after the last, and the item's answer arrives. */
    private void gesture() throws Exception {
        onUi(() -> PageTestAccess.selectionGesture(page));
        settle();
    }

    private List<String> copies() throws Exception {
        return onUi(() -> host.clipboard().copies());
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

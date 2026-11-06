package dev.jasper.browser.item;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakeClipboard;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 15's paste for a page with no field the insertion reaches: the snippet goes on the clipboard and into the
 * page, and the clipboard gets its text back {@value ClipboardPaste#RESTORE_MILLIS} ms later, on a clock the test runs.
 * The plugin's clipboard is the fake host's.
 */
class ClipboardPasteTest {
    private final Clock clock = new Clock();
    private final List<String> log = new ArrayList<>();
    private FakePluginHost host;
    private FakeClipboard clipboard;
    private ClipboardPaste paste;

    @BeforeEach void start() {
        host = new FakePluginHost();
        var context = new AtomicReference<PluginContext>();
        host.start(new PluginInfo("dev.jasper.browser", "Browser", "0.1.0", Set.of(Capabilities.CLIPBOARD_READ)), Set.of(), Set.of(), context::set);
        clipboard = host.clipboard();
        paste = new ClipboardPaste(context.get().platform(), clock);
    }

    @AfterEach void stop() {
        host.close();
    }

    @Test void theSnippetIsPastedAndTheOldTextComesBackLater() {
        clipboard.setText("old");
        paste.paste("snippet", this::intoPage, () -> log.add("then:" + clipboard.text().orElse("")));
        assertThat(log).as("pasted with the snippet on the clipboard").containsExactly("page:snippet");
        assertThat(clock.delays).as("one wait").containsExactly((long) ClipboardPaste.RESTORE_MILLIS);
        clock.run();
        assertThat(clipboard.text()).contains("old");
        assertThat(log).as("then runs once, after the clipboard is back").containsExactly("page:snippet", "then:old");
        assertThat(clock.delays).isEmpty();
    }

    @Test void aClipboardChangedMeanwhileIsLeftAlone() {
        clipboard.setText("old");
        paste.paste("snippet", this::intoPage, () -> log.add("then"));
        clipboard.setText("copied meanwhile");
        clock.run();
        assertThat(clipboard.text()).contains("copied meanwhile");
        assertThat(log).containsExactly("page:snippet", "then");
    }

    /** Only text comes back: a clipboard that held none, as one holding only an image, keeps the snippet. */
    @Test void aClipboardWithNoTextKeepsTheSnippet() {
        assertThat(clipboard.text()).as("nothing copied yet").isEmpty();
        paste.paste("snippet", this::intoPage, () -> log.add("then"));
        clock.run();
        assertThat(clipboard.text()).contains("snippet");
        assertThat(clipboard.copies()).containsExactly("copy|dev.jasper.browser|snippet");
        assertThat(log).containsExactly("page:snippet", "then");
    }

    /** A clipboard that cannot take the snippet would have the page paste what it holds: nothing is pasted, nothing follows. */
    @Test void aClipboardThatCannotBeWrittenPastesNothing() {
        clipboard.setText("password");
        clipboard.failWrites(true);
        paste.paste("snippet", this::intoPage, () -> log.add("then"));
        assertThat(log).isEmpty();
        assertThat(clock.delays).isEmpty();
        assertThat(clipboard.text()).contains("password");
    }

    @Test void theRestoreWaitsFiveHundredMilliseconds() {
        assertThat(ClipboardPaste.RESTORE_MILLIS).as("H1: iTerm2's 100 ms lengthened").isEqualTo(500);
    }

    /** The page pastes what the clipboard holds now. */
    private void intoPage() {
        log.add("page:" + clipboard.text().orElse(""));
    }

    /** A clock that runs its tasks when the test says: each delay asked for, then its task. */
    private static final class Clock implements BiConsumer<Long, Runnable> {
        final List<Long> delays = new ArrayList<>();
        private final List<Runnable> tasks = new ArrayList<>();

        @Override public void accept(Long millis, Runnable task) {
            delays.add(millis);
            tasks.add(task);
        }

        /** Each task waiting runs. */
        void run() {
            var due = List.copyOf(tasks);
            tasks.clear();
            delays.clear();
            due.forEach(Runnable::run);
        }
    }
}

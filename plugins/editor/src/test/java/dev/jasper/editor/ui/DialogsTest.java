package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.WindowSpec;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.editor.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

/** DialogAsker and SaveWait mount owned kit views in the plugin's dialogs (Plan 2 Task 9a). */
class DialogsTest {
    @TempDir Path home;
    private FakePluginHost host;
    private PluginContext context;
    private WindowOwner owner;

    @BeforeEach void start() throws Exception {
        host = new FakePluginHost(home.resolve("plugins"));
        context = host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });
        edt(() -> {
            var window = context.windows().create(new WindowSpec("dev.jasper.editor.window", "Editor", new Dimension(100, 100), true));
            window.show();
            owner = window;
        });
    }

    @AfterEach void stop() throws Exception { edt(host::close); }

    @Test void theAskerBuildsAModalDialogWhoseButtonsCloseIt() throws Exception {
        edt(() -> {
            // The testkit's modal show() returns at once, so the answer is the dismissal and the dialog stays for inspection.
            assertThat(new DialogAsker(context.windows(), context.kit(), owner).apply(new Question("Replace", "Replace it?", List.of("Yes", "No")))).isEqualTo("No");
            var view = host.windowView("dev.jasper.editor", "Replace").orElseThrow();
            assertThat(form(view).actions()).extracting(Nodes.Button::label).containsExactly("Yes", "No");
            assertThat(primaries(view)).extracting(Nodes.Button::label).containsExactly("Yes");
            click(view, form(view).actions().getFirst().id());
            assertThat(host.windows()).containsExactly("dev.jasper.editor.window|Editor|true");
            assertThat(closed(view)).as("closing the dialog disposed its view").isTrue();
            new DialogAsker(context.windows(), context.kit(), owner).apply(new Question("Overwrite", "Overwrite it?", List.of("Overwrite", "Cancel"), true));
            var destructive = host.windowView("dev.jasper.editor", "Overwrite").orElseThrow();
            assertThat(primaries(destructive)).extracting(Nodes.Button::label).containsExactly("Cancel");
            escape(destructive);
            assertThat(host.windows()).as("Escape closes the question").containsExactly("dev.jasper.editor.window|Editor|true");
            assertThat(context.kit()).isInstanceOfSatisfying(dev.jasper.sdk.testing.FakeKit.class, kit -> assertThat(kit.failures()).isEmpty());
        });
    }

    @Test void aFinishedSaveNeedsNoDialog() throws Exception {
        edt(() -> {
            var wait = new SaveWait(context.windows(), context.kit(), owner);
            assertThat(wait.apply("Saving 1 file…", CompletableFuture.completedFuture(true))).isTrue();
            assertThat(wait.apply("Saving 1 file…", CompletableFuture.completedFuture(false))).isFalse();
            assertThat(wait.apply("Saving 1 file…", CompletableFuture.failedFuture(new IllegalStateException("x")))).isFalse();
            assertThat(host.windows()).as("a finished save needs no dialog").hasSize(1);
        });
    }

    @Test @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void theWaitShowsANonModalSavingDialogUntilTheSavesFinish() throws Exception {
        var saves = new CompletableFuture<Boolean>();
        var result = waitFor("Saving 2 files…", saves);
        edt(() -> {
            var view = host.windowView("dev.jasper.editor", "Saving").orElseThrow();
            assertThat(node(view, Forms.SAVING_MESSAGE, Nodes.Text.class).text()).isEqualTo("Saving 2 files…");
            assertThat(host.windows()).contains("dev.jasper.editor.window|Editor|true").hasSize(2);
            saves.complete(true);
        });
        assertThat(result.get(10, TimeUnit.SECONDS)).isTrue();
        edt(() -> assertThat(host.windows()).as("the dialog closes once the saves finish").containsExactly("dev.jasper.editor.window|Editor|true"));
    }

    @Test @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void cancelOrEscapeStopsWaitingAndALateCompletionIsIgnored() throws Exception {
        for (boolean escape : List.of(false, true)) {
            var saves = new CompletableFuture<Boolean>();
            var result = waitFor("Saving 1 file…", saves);
            edt(() -> {
                var view = host.windowView("dev.jasper.editor", "Saving").orElseThrow();
                if (escape) escape(view); else click(view, Forms.SAVING_CANCEL);
            });
            assertThat(result.get(10, TimeUnit.SECONDS)).as("cancelled (escape %s)", escape).isFalse();
            edt(() -> {
                assertThat(host.windows()).containsExactly("dev.jasper.editor.window|Editor|true");
                saves.complete(true);
                assertThat(host.windows()).as("a save finishing after the dialog closed opens nothing").containsExactly("dev.jasper.editor.window|Editor|true");
                assertThat(((dev.jasper.sdk.testing.FakeKit) context.kit()).failures()).isEmpty();
            });
        }
    }

    @Test @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void closingTheSavingDialogCancels() throws Exception {
        var saves = new CompletableFuture<Boolean>();
        var result = waitFor("Saving 1 file…", saves);
        edt(() -> assertThat(host.requestClose("dialog")).as("the user closes the Saving dialog").isTrue());
        assertThat(result.get(10, TimeUnit.SECONDS)).isFalse();
        edt(() -> saves.complete(true));
    }

    /** Runs the wait on the UI thread, where its nested loop keeps events flowing, and returns once the dialog shows. */
    private CompletableFuture<Boolean> waitFor(String message, CompletableFuture<Boolean> saves) throws Exception {
        var result = new CompletableFuture<Boolean>();
        var wait = new AtomicReference<SaveWait>();
        edt(() -> wait.set(new SaveWait(context.windows(), context.kit(), owner)));
        SwingUtilities.invokeLater(() -> {
            try { result.complete(wait.get().apply(message, saves)); } catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!value(() -> host.windowView("dev.jasper.editor", "Saving").isPresent())) {
            if (System.nanoTime() > deadline) throw new AssertionError("the Saving dialog never showed");
            Thread.sleep(10);
        }
        return result;
    }
}

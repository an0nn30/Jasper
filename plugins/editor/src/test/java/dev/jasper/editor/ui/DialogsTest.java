package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.WindowSpec;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class DialogsTest {
    @TempDir Path home;

    @Test void theAskerBuildsAModalDialogWhoseButtonsCloseIt() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var context = host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });
            var owner = context.windows().create(new WindowSpec("dev.jasper.editor.window", "Editor", new Dimension(100, 100), true));
            owner.show();
            // The testkit's modal show() returns at once, so the answer is the dismissal and the dialog stays for inspection.
            assertThat(new DialogAsker(context.windows(), owner).apply(new Question("Replace", "Replace it?", List.of("Yes", "No")))).isEqualTo("No");
            var content = host.windowContent("dev.jasper.editor", "Replace").orElseThrow();
            FormsTest.buttons(content).getFirst().doClick();
            assertThat(host.windows()).containsExactly("dev.jasper.editor.window|Editor|true");
            var wait = new SaveWait(context.windows(), owner);
            assertThat(wait.apply("Saving 1 file…", CompletableFuture.completedFuture(true))).isTrue();
            assertThat(wait.apply("Saving 1 file…", CompletableFuture.completedFuture(false))).isFalse();
            assertThat(wait.apply("Saving 1 file…", CompletableFuture.failedFuture(new IllegalStateException("x")))).isFalse();
            assertThat(host.windows()).as("a finished save needs no dialog").hasSize(1);
        }
    }
}

package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.ui.LinkOrigin;
import dev.jasper.sdk.ui.LinkPlacement;
import dev.jasper.sdk.ui.WebLink;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakePluginHostTest {
    private static final PluginInfo INFO = new PluginInfo("test.alpha", "Alpha", "1.0.0", Set.of());

    @Test void backgroundWorkWaitsUntilRunAndDataDirectoriesArePerPlugin() {
        try (var host = new FakePluginHost()) {
            List<String> ran = new ArrayList<>();
            var context = host.start(INFO, Set.of(), Set.of(), c -> c.background().execute(() -> ran.add("task")));
            assertThat(ran).isEmpty();
            assertThat(host.runBackground()).isEqualTo(1);
            assertThat(ran).containsExactly("task");
            assertThat(context.dataDirectory()).isDirectory().hasFileName("data");
            assertThat(context.dataDirectory().getParent()).as("the application layout, <plugins>/<id>/data").hasFileName("test.alpha");
            assertThat(Files.isDirectory(context.dataDirectory())).isTrue();
        }
    }

    /**
     * SDK 0.31.0 (F15): the fake's Downloads folder is {@code home/Downloads} under its data root, never the user's own,
     * and reading it creates nothing; {@code setDownloadsFolder} replaces it with an absolute folder.
     */
    @Test void theDownloadsFolderIsUnderTheDataRootUntilOneIsSet(@TempDir Path root) {
        try (var host = new FakePluginHost(root)) {
            var context = host.start(INFO, Set.of(), Set.of(), c -> { });
            assertThat(context.platform().downloadsFolder()).isEqualTo(root.resolve("home").resolve("Downloads"));
            assertThat(root.resolve("home")).as("the plugin creates it").doesNotExist();
            Path chosen = root.resolve("chosen");
            host.setDownloadsFolder(chosen);
            assertThat(context.platform().downloadsFolder()).isEqualTo(chosen);
            assertThatIllegalArgumentException().isThrownBy(() -> host.setDownloadsFolder(Path.of("relative")));
            assertThat(context.platform().downloadsFolder()).isEqualTo(chosen);
        }
    }

    /**
     * SDK 0.31.0 (F7, F33): each mark is recorded as the plugin asked, the /dev/null one with {@code about:internet} and no
     * referrer; after {@code failMarks} every mark throws that failure and nothing more is recorded, while a missing file
     * still throws {@code NoSuchFileException}.
     */
    @Test void marksAreRecordedUntilTheyFail(@TempDir Path root) throws Exception {
        try (var host = new FakePluginHost(root)) {
            var context = host.start(INFO, Set.of(), Set.of(), c -> { });
            Path file = Files.writeString(root.resolve("a.zip"), "zip");
            URI none = URI.create("about:internet");
            URI source = URI.create("https://example.com/a.zip");
            Optional<URI> page = Optional.of(URI.create("https://example.com/"));
            context.platform().markDownloaded(file, none, Optional.empty());
            context.platform().markDownloaded(file, source, page);
            assertThat(host.markedDownloads()).containsExactly(new FakePluginHost.MarkedDownload(file, none, Optional.empty()),
                new FakePluginHost.MarkedDownload(file, source, page));
            var failure = new java.io.IOException("Operation not permitted");
            host.failMarks(failure);
            assertThatThrownBy(() -> context.platform().markDownloaded(file, source, page)).isSameAs(failure);
            assertThatThrownBy(() -> context.platform().markDownloaded(file, source, page)).as("every later mark").isSameAs(failure);
            assertThatThrownBy(() -> context.platform().markDownloaded(root.resolve("gone.zip"), source, page))
                .isInstanceOf(java.nio.file.NoSuchFileException.class);
            assertThat(host.markedDownloads()).hasSize(2);
        }
    }

    /**
     * SDK 0.31.0 (F33): a mark on the EDT throws, but a background task is off the UI thread, as in Jasper, even when a
     * test that builds views runs {@code runBackground} inside {@code invokeAndWait}.
     */
    @Test void aBackgroundTaskMarksEvenWhenRunFromTheEdt(@TempDir Path root) throws Exception {
        try (var host = new FakePluginHost(root)) {
            var context = host.start(INFO, Set.of(), Set.of(), c -> { });
            Path file = Files.writeString(root.resolve("a.zip"), "zip");
            URI source = URI.create("https://example.com/a.zip");
            List<Throwable> thrown = new ArrayList<>();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { context.platform().markDownloaded(file, source, Optional.empty()); }
                catch (Exception refused) { thrown.add(refused); }
                context.background().execute(() -> {
                    try { context.platform().markDownloaded(file, source, Optional.empty()); }
                    catch (Exception unexpected) { thrown.add(unexpected); }
                });
                host.runBackground();
                try { context.platform().markDownloaded(file, source, Optional.empty()); }
                catch (Exception refused) { thrown.add(refused); }
            });
            assertThat(thrown).hasSize(2).allSatisfy(failure -> assertThat(failure).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UI thread"));
            assertThat(host.markedDownloads()).containsExactly(new FakePluginHost.MarkedDownload(file, source, Optional.empty()));
        }
    }

    /** An address with an authority but no host, such as http://my_host/, is no WebLink: openInBrowser sends it to the system browser, as the application does. */
    @Test void openInBrowserSendsAnAddressNoWebLinkTakesToTheSystemBrowser() {
        try (var host = new FakePluginHost()) {
            List<WebLink> asked = new ArrayList<>();
            var context = host.start(new PluginInfo("test.alpha", "Alpha", "1.0.0", Set.of(Capabilities.BROWSER_PROVIDE)), Set.of(), Set.of(),
                c -> c.platform().browser(link -> asked.add(link)));
            URI noHost = URI.create("http://my_host/");
            context.platform().openInBrowser(noHost);
            assertThat(asked).isEmpty();
            assertThat(host.browsed()).containsExactly(noHost);
            assertThatIllegalArgumentException().isThrownBy(() -> host.openLink(noHost, LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
            context.platform().openInBrowser(URI.create("https://example.com/"));
            assertThat(asked).extracting(link -> link.address().toString()).containsExactly("https://example.com/");
            assertThat(host.browsed()).as("the provided browser opened it").containsExactly(noHost);
        }
    }
}

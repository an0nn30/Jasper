package dev.jasper.app.themestore;

import static dev.jasper.app.appearance.ThemeFixtures.plugin;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.appearance.ThemeCatalog;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeStoreTest {
    @TempDir Path home;
    private FakeMarketplace market;
    private ThemeCatalog catalog;
    private ThemeStore store;
    private final int[] rescans = new int[1];
    private final List<URI> browsed = new ArrayList<>();

    @BeforeEach void start() throws Exception {
        market = new FakeMarketplace();
        catalog = ThemeCatalog.beside(home.resolve("config.toml"));
        catalog.scan();
        store = new ThemeStore(catalog, market.client(), Runnable::run, Runnable::run, new ThemeStore.Hooks(
            () -> { rescans[0]++; catalog.scan(); return CompletableFuture.completedFuture(null); }, browsed::add, folder -> { },
            (parent, question) -> true), () -> { });
    }

    @AfterEach void stop() { store.close(); market.close(); }

    @Test void installingDownloadsTheLatestReleaseRecordsItRescansAndChecksForUpdates() throws Exception {
        market.theme(home.resolve("served"), 12275, "com.vermouthx.idea", "Dracula Theme", "Dracula", "2.3.1");
        var installed = store.install(12275, "com.vermouthx.idea", "Dracula Theme").get();
        assertThat(installed.file()).isEqualTo(home.resolve("themes/com.vermouthx.idea-2.3.1.jar"));
        assertThat(installed.themes()).containsExactly("Dracula");
        assertThat(rescans[0]).isEqualTo(1);
        assertThat(catalog.latest().find("Dracula")).isPresent();
        var listed = store.installed().get();
        assertThat(listed).singleElement().satisfies(theme -> {
            assertThat(theme.title()).isEqualTo("Dracula Theme");
            assertThat(theme.themes()).containsExactly("Dracula");
            assertThat(theme.version()).isEqualTo("2.3.1");
            assertThat(theme.origin()).isEqualTo("Marketplace");
        });
        assertThat(store.updates(listed).get()).isEmpty();
        market.theme(home.resolve("served"), 12275, "com.vermouthx.idea", "Dracula Theme", "Dracula", "2.4.0");
        assertThat(store.updates(listed).get()).containsOnlyKeys(installed.file());
    }

    @Test void downloadsAreClearedOnlyOnTheFirstCall() throws Exception {
        Path downloads = home.resolve("themes/.downloads");
        Files.createDirectories(downloads);
        Files.writeString(downloads.resolve("old.part"), "x");
        store.clearDownloads();
        assertThat(downloads.resolve("old.part")).doesNotExist();
        Files.writeString(downloads.resolve("running.part"), "x");
        store.clearDownloads();
        assertThat(downloads.resolve("running.part")).isRegularFile();
    }

    @Test void aFailedDownloadLeavesNothingAndExplainsItself() {
        market.json("/api/plugins/7/updates", FakeMarketplace.release(1, "1.0", "7/1/gone.zip"));
        var failure = catchThrowable(() -> store.install(7, "x.gone", "Gone").get());
        assertThat(ThemeStore.cause(failure)).isInstanceOf(MarketplaceClient.MarketplaceException.class);
        assertThat(ThemeStore.message(failure)).isEqualTo("The Marketplace answered HTTP 404.");
        assertThat(home.resolve("themes/.downloads")).isEmptyDirectory();
        assertThat(rescans[0]).isZero();
    }

    @Test void theInstalledListShowsDiskInstallsAndFilesWithOnlyProblems() throws Exception {
        plugin(home.resolve("themes/nord.jar"), "Nord", true, "#2e3440", "2e3440");
        Files.writeString(home.resolve("themes/broken.zip"), "not a zip");
        catalog.scan();
        var listed = store.installed().get();
        assertThat(listed).extracting(InstalledTheme::title).containsExactly("broken", "nord");
        assertThat(listed.get(0).themes()).isEmpty();
        assertThat(listed.get(0).problems()).isNotEmpty();
        assertThat(listed.get(1).themes()).containsExactly("Nord");
        assertThat(listed.get(1).origin()).isEqualTo("From disk");
        assertThat(listed.get(1).version()).isEmpty();
        assertThat(store.updates(listed).get()).isEmpty();
        assertThat(market.requests).isEmpty();
    }

    @Test void rescansAndCompletionsRunOnTheEdtWhileTheWorkRunsOffIt() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "test-worker"));
        ExecutorService edt = Executors.newSingleThreadExecutor(task -> new Thread(task, "test-edt"));
        var rescanThreads = new ArrayList<String>();
        var real = new ThemeStore(catalog, market.client(), worker, edt, new ThemeStore.Hooks(
            () -> { rescanThreads.add(Thread.currentThread().getName()); catalog.scan(); return CompletableFuture.completedFuture(null); },
            browsed::add, folder -> { }, (parent, question) -> true), () -> { });
        try {
            market.theme(home.resolve("served"), 12275, "com.vermouthx.idea", "Dracula Theme", "Dracula", "2.3.1");
            var completedOn = new CompletableFuture<String>();
            var future = real.install(12275, "com.vermouthx.idea", "Dracula Theme");
            future.thenRun(() -> completedOn.complete(Thread.currentThread().getName()));
            future.get();
            assertThat(rescanThreads).containsExactly("test-edt");
            assertThat(completedOn.get()).isEqualTo("test-edt");

            market.json("/api/plugins/7/updates", FakeMarketplace.release(1, "1.0", "7/1/gone.zip"));
            var failure = catchThrowable(() -> real.install(7, "x.gone", "Gone").get());
            assertThat(failure.getCause()).isInstanceOf(MarketplaceClient.MarketplaceException.class);
        } finally {
            real.close();
            worker.shutdownNow();
            edt.shutdownNow();
        }
    }

    @Test void browsingOpensOnlyWebLinksResolvedAgainstTheMarketplace() {
        store.browse("/plugin/12275-dracula-theme");
        store.browse("https://draculatheme.com/jetbrains");
        store.browse("javascript:alert(1)");
        store.browse("file:///etc/passwd");
        assertThat(browsed).containsExactly(URI.create("https://plugins.jetbrains.com/plugin/12275-dracula-theme"),
            URI.create("https://draculatheme.com/jetbrains"));
    }
}

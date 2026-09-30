package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.files.Browser;
import dev.jasper.app.files.ChooserEntry;
import dev.jasper.app.files.ChooserSource;
import dev.jasper.app.files.Location;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileEntry;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.files.FileRoot;
import dev.jasper.sdk.files.FileSource;
import dev.jasper.sdk.files.FileSourceSpec;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.PaneHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

class HostedFileSourcesTest {
    Contributions contributions = onEdtValue(Contributions::new);
    TerminalFixture fixture = onEdtValue(TerminalFixture::new);
    PluginHost host;
    final List<String> calls = new ArrayList<>();
    boolean failRoots;

    FileSource source(String id) {
        return new FileSource() {
            @Override public FileSourceSpec spec() { return new FileSourceSpec(id, "Remote"); }
            @Override public List<FileRoot> roots() {
                if (failRoots) throw new IllegalStateException("boom");
                return List.of(new FileRoot("box", "Box", "me@box:22", true));
            }
            @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
            @Override public Optional<FileLocation.Sourced> locate(PaneHandle pane) {
                calls.add("locate:" + pane.id());
                return Optional.of(new FileLocation.Sourced("test.other.hosts", "box", "/elsewhere"));
            }
            @Override public CompletionStage<FileBrowser> connect(String rootId, WindowOwner owner) {
                calls.add("connect:" + rootId);
                return CompletableFuture.completedFuture(new FileBrowser() {
                    @Override public String home() { return "/home/me"; }
                    @Override public void list(String directory, Consumer<FileEntry> visitor) {
                        visitor.accept(new FileEntry("notes.txt", FileEntry.Kind.FILE, 5, 7));
                        visitor.accept(new FileEntry(".cache", FileEntry.Kind.DIRECTORY, -1, 7));
                    }
                    @Override public Optional<FileEntry> stat(String path) { return Optional.of(new FileEntry("x", FileEntry.Kind.DIRECTORY, -1, 1)); }
                    @Override public byte[] read(String path, int maxBytes) { return new byte[] { 'h', 'i' }; }
                    @Override public void mkdir(String path) { calls.add("mkdir:" + path); }
                    @Override public void abort() { calls.add("abort"); }
                    @Override public void close() { calls.add("close"); }
                });
            }
        };
    }

    void start(FileSource source) throws Exception {
        Path data = Files.createTempDirectory("jasper-files");
        host = onEdtValue(() -> new PluginHost(new PluginHost.Environment(javax.swing.SwingUtilities::invokeLater,
            javax.swing.SwingUtilities::isEventDispatchThread, data::resolve, diagnostic -> { },
            Duration.ofMillis(200), contributions, () -> true, AppContractTest.headlessWindows(), fixture.registry,
            notice -> { }, file -> { }, new PluginSettingsFiles(data, diagnostic -> { }), file -> { }, PluginRuntime.ShellProfiles.NONE)));
        var info = new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.FILES_SOURCE));
        onEdt(() -> host.start(new HostedPlugin(info, Set.of(), Set.of(), Set.of(getClass().getPackageName()), getClass().getClassLoader(),
            () -> new dev.jasper.sdk.plugin.Plugin() {
                @Override public void start(PluginContext context) { context.fileSources().register(source); }
            })));
    }

    @AfterEach void stop() {
        if (host == null) return;
        var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
        onEdt(() -> pending.set(host.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).join();
    }

    ChooserSource adapted() { return onEdtValue(() -> contributions.fileSources().getFirst()); }

    @Test void rootsLocateAndConnectAreAdaptedAndContained() throws Exception {
        start(source("test.a.hosts"));
        var adapted = adapted();
        assertThat(adapted.id()).isEqualTo("test.a.hosts");
        assertThat(adapted.heading()).isEqualTo("Remote");
        assertThat(onEdtValue(adapted::roots)).containsExactly(new ChooserSource.Root("box", "Box", "me@box:22", true));
        failRoots = true;
        assertThat(onEdtValue(adapted::roots)).as("a failing plugin shows no roots").isEmpty();
        UUID pane = UUID.randomUUID();
        assertThat(onEdtValue(() -> adapted.locate(pane))).as("another source's location is ignored").isEmpty();
        assertThat(calls).contains("locate:" + pane);
        Browser browser = onEdtValue(() -> adapted.connect("box", Optional.empty())).join();
        assertThat(calls).contains("connect:box");
        var listed = new ArrayList<ChooserEntry>();
        browser.list("/home/me", listed::add);
        assertThat(listed).extracting(ChooserEntry::path, ChooserEntry::hidden, ChooserEntry::kind).containsExactly(
            tuple("/home/me/notes.txt", false, ChooserEntry.Kind.FILE), tuple("/home/me/.cache", true, ChooserEntry.Kind.DIRECTORY));
        assertThat(browser.home()).isEqualTo("/home/me");
        assertThat(browser.resolve("/srv", "~/x")).isEqualTo("/home/me/x");
        assertThat(browser.stat("/srv/x")).hasValueSatisfying(entry -> assertThat(entry.path()).isEqualTo("/srv/x"));
        assertThat(browser.read("/srv/x", 10)).containsExactly((byte) 'h', (byte) 'i');
        assertThat(browser.parent("/srv/x")).contains("/srv");
        browser.mkdir("/srv/new");
        browser.abort();
        browser.close();
        assertThat(calls).contains("mkdir:/srv/new", "abort", "close");
    }

    @Test void aStoppedPluginsSourceIsGoneAndItsAdapterRefusesToConnect() throws Exception {
        start(source("test.a.hosts"));
        var adapted = adapted();
        stop();
        host = null;
        assertThat(onEdtValue(contributions::fileSources)).isEmpty();
        assertThat(onEdtValue(adapted::roots)).isEmpty();
        assertThat(onEdtValue(() -> adapted.connect("box", Optional.empty()))).isCompletedExceptionally();
    }
}

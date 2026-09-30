package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileEntry;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.files.FileRoot;
import dev.jasper.sdk.files.FileSource;
import dev.jasper.sdk.files.FileSourceSpec;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.PaneHandle;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FakeFileSourcesTest {
    @TempDir Path home;

    static final class Hosts implements FileSource {
        private final String id;
        Hosts(String id) { this.id = id; }
        @Override public FileSourceSpec spec() { return new FileSourceSpec(id, "Hosts"); }
        @Override public List<FileRoot> roots() { return List.of(new FileRoot("box", "Box", "me@box", true)); }
        @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
        @Override public Optional<FileLocation.Sourced> locate(PaneHandle pane) { return Optional.of(new FileLocation.Sourced(id, "box", "/srv")); }
        @Override public CompletionStage<FileBrowser> connect(String rootId, WindowOwner owner) {
            return CompletableFuture.completedFuture(new FileBrowser() {
                @Override public String home() { return "/home/me"; }
                @Override public void list(String directory, Consumer<FileEntry> visitor) { visitor.accept(new FileEntry("a.txt", FileEntry.Kind.FILE, 1, 1)); }
                @Override public Optional<FileEntry> stat(String path) { return Optional.empty(); }
                @Override public byte[] read(String path, int maxBytes) { return new byte[0]; }
                @Override public void mkdir(String path) { }
                @Override public void abort() { }
                @Override public void close() { }
            });
        }
    }

    @Test void registeringNeedsTheCapabilityAndTheNamespaceAndStopsWithThePlugin() throws Exception {
        var host = new FakePluginHost(home);
        var failure = new AtomicReference<Throwable>();
        host.start(new PluginInfo("test.bare", "Bare", "1.0.0", Set.of()), Set.of(), Set.of(), context -> {
            try { context.fileSources().register(new Hosts("test.bare.hosts")); } catch (RuntimeException caught) { failure.set(caught); }
        });
        assertThat(failure.get()).isInstanceOf(MissingCapabilityException.class);
        var context = new AtomicReference<PluginContext>();
        host.start(new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.FILES_SOURCE)), Set.of(), Set.of(), context::set);
        assertThatIllegalArgumentException().isThrownBy(() -> context.get().fileSources().register(new Hosts("test.b.hosts")));
        Subscription first = context.get().fileSources().register(new Hosts("test.a.hosts"));
        assertThatIllegalArgumentException().isThrownBy(() -> context.get().fileSources().register(new Hosts("test.a.hosts")));
        assertThat(host.fileSources()).containsExactly("test.a.hosts|Hosts");
        assertThat(host.fileRoots("test.a.hosts")).containsExactly("box|Box|true");
        UUID window = host.addTerminalWindow();
        assertThat(host.locateFile("test.a.hosts", UUID.randomUUID())).contains(new FileLocation.Sourced("test.a.hosts", "box", "/srv"));
        FileBrowser browser = host.connectFileSource("test.a.hosts", "box", window);
        assertThat(browser.home()).isEqualTo("/home/me");
        first.close();
        assertThat(host.fileSources()).isEmpty();
        context.get().fileSources().register(new Hosts("test.a.more"));
        host.stopAll();
        assertThat(host.fileSources()).isEmpty();
    }

    @Test void chooseTakesScriptedLocationsAndRecordsTheChoice() {
        var host = new FakePluginHost(home);
        var context = new AtomicReference<PluginContext>();
        host.start(new PluginInfo("test.a", "A", "1.0.0", Set.of()), Set.of(), Set.of(), context::set);
        UUID window = host.addTerminalWindow();
        var owner = context.get().terminals().window(window).orElseThrow();
        var remote = new FileLocation.Sourced("test.b.hosts", "box", "/etc/hosts");
        host.queueFileChoice(List.of(remote));
        var choice = new FileChoice(owner, "Open", FileChoice.Mode.OPEN, false, Optional.empty(), Optional.empty(), Set.of("test.b.hosts"));
        assertThat(context.get().windows().choose(choice)).containsExactly(remote);
        assertThat(host.fileChoices()).containsExactly(choice);
        assertThat(context.get().windows().choose(choice)).as("nothing queued cancels").isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> context.get().windows().choose(
            new FileChoice(new WindowOwner() { }, "Open", FileChoice.Mode.OPEN, false, Optional.empty(), Optional.empty(), Set.of())));
    }
}

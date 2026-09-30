package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.files.Browser;
import dev.jasper.app.files.ChooserEntry;
import dev.jasper.app.files.ChooserSource;
import dev.jasper.app.files.Location;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileEntry;
import dev.jasper.sdk.files.FileSource;
import dev.jasper.sdk.files.FileSourceSpec;
import dev.jasper.sdk.files.FileSources;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * One plugin's file-chooser sources, adapted onto the chooser's {@link ChooserSource} and {@link Browser}. UI-thread
 * calls into the plugin are contained: a failing source shows no roots, locates nothing or fails to connect. Browser
 * calls run on the chooser's worker; their failures reach the chooser as errors.
 */
final class HostedFileSources implements FileSources {
    private final String pluginId;
    private final CapabilityGate gate;
    private final Contributions model;
    private final Containment containment;
    private final HostedUi ui;
    private final HostedTerminals terminals;
    private boolean audited;

    HostedFileSources(String pluginId, CapabilityGate gate, Contributions model, Containment containment, HostedUi ui, HostedTerminals terminals) {
        this.pluginId = pluginId; this.gate = gate; this.model = model; this.containment = containment; this.ui = ui; this.terminals = terminals;
    }

    @Override public Subscription register(FileSource source) {
        ui.guard("register");
        Objects.requireNonNull(source, "source");
        gate.require(Capabilities.FILES_SOURCE);
        if (!audited) { audited = true; gate.audit(Capabilities.FILES_SOURCE, "added places to the file chooser"); }
        FileSourceSpec spec = Objects.requireNonNull(source.spec(), "spec");
        if (!spec.id().startsWith(pluginId + "."))
            throw new IllegalArgumentException("A file source id must start with " + pluginId + ".: " + spec.id());
        var adapted = new Adapted(source, spec);
        dev.jasper.app.lifecycle.Subscription added = model.addFileSource(adapted);
        return ui.tracked(() -> { adapted.retire(); added.close(); });
    }

    private <T> T contained(String what, T fallback, Callable<T> call) {
        var result = new AtomicReference<>(fallback);
        containment.attempt(pluginId, what, () -> { result.set(call.call()); return null; });
        return result.get();
    }

    /** A plugin source as the chooser sees it. */
    final class Adapted implements ChooserSource {
        private final FileSource source;
        private final FileSourceSpec spec;
        private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
        private Subscription pluginListener;
        private volatile boolean retired;

        Adapted(FileSource source, FileSourceSpec spec) { this.source = source; this.spec = spec; }

        @Override public String id() { return spec.id(); }
        @Override public String heading() { return spec.heading(); }

        @Override public List<Root> roots() {
            if (retired) return List.of();
            List<dev.jasper.sdk.files.FileRoot> roots = contained("file source " + spec.id() + " roots", List.of(), source::roots);
            return roots == null ? List.of() : roots.stream().map(root -> new Root(root.id(), root.label(), root.detail(), root.connected())).toList();
        }

        @Override public Runnable onChanged(Runnable listener) {
            listeners.add(listener);
            if (pluginListener == null && !retired)
                pluginListener = contained("file source " + spec.id() + " listener", null, () -> source.onChanged(this::changed));
            return () -> listeners.remove(listener);
        }

        private void changed() { for (Runnable listener : listeners) listener.run(); }

        void retire() {
            retired = true;
            if (pluginListener != null) pluginListener.close();
            changed();
        }

        @Override public Optional<Location> locate(UUID pane) {
            if (retired) return Optional.empty();
            Optional<dev.jasper.sdk.files.FileLocation.Sourced> found = contained("file source " + spec.id() + " locate", Optional.empty(),
                () -> source.locate(terminals.paneHandle(pane)));
            return found == null ? Optional.empty() : found.filter(sourced -> sourced.sourceId().equals(spec.id()))
                .map(sourced -> new Location(sourced.sourceId(), sourced.rootId(), sourced.path()));
        }

        @Override public CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow) {
            if (retired) return CompletableFuture.failedFuture(new IOException("This place is no longer available."));
            WindowOwner owner = ownerWindow.<WindowOwner>map(terminals::windowHandle).orElse(null);
            CompletionStage<FileBrowser> stage = contained("file source " + spec.id() + " connect", null, () -> source.connect(root, owner));
            if (stage == null) return CompletableFuture.failedFuture(new IOException("The plugin could not connect."));
            var result = new CompletableFuture<Browser>();
            stage.whenComplete((browser, failure) -> {
                if (failure != null) result.completeExceptionally(failure);
                else if (browser == null) result.completeExceptionally(new IOException("The plugin returned no browser."));
                else if (!result.complete(new AdaptedBrowser(browser))) browser.close();
            });
            result.whenComplete((ignored, failure) -> {
                if (result.isCancelled()) {
                    try { stage.toCompletableFuture().cancel(true); } catch (UnsupportedOperationException notAFuture) { }
                }
            });
            return result;
        }
    }

    /** A plugin browser as the chooser sees it; entry names become absolute paths. */
    static final class AdaptedBrowser implements Browser {
        private final FileBrowser browser;
        private volatile String home;

        AdaptedBrowser(FileBrowser browser) { this.browser = browser; }

        @Override public String home() throws IOException {
            String known = home;
            if (known == null) home = known = Objects.requireNonNull(browser.home(), "home");
            return known;
        }

        @Override public void list(String folder, Consumer<ChooserEntry> visitor) throws IOException {
            browser.list(folder, entry -> visitor.accept(entry(join(folder, entry.name()), entry)));
        }

        @Override public Optional<ChooserEntry> stat(String path) throws IOException {
            return browser.stat(path).map(entry -> entry(path, entry));
        }

        @Override public byte[] read(String path, int maxBytes) throws IOException {
            byte[] bytes = browser.read(path, maxBytes);
            return bytes.length > maxBytes ? java.util.Arrays.copyOf(bytes, maxBytes) : bytes;
        }

        @Override public void mkdir(String path) throws IOException { browser.mkdir(path); }

        @Override public Optional<String> parent(String path) { return browser.parent(path); }

        @Override public String name(String path) { return browser.name(path); }

        @Override public String resolve(String folder, String text) throws IOException { return browser.resolve(folder, text, home()); }

        @Override public void abort() {
            try { browser.abort(); } catch (RuntimeException ignored) { }
        }

        @Override public void close() {
            try { browser.close(); } catch (RuntimeException ignored) { }
        }

        private static String join(String folder, String name) { return folder.endsWith("/") ? folder + name : folder + "/" + name; }

        private static ChooserEntry entry(String path, FileEntry entry) {
            ChooserEntry.Kind kind = switch (entry.kind()) {
                case DIRECTORY -> ChooserEntry.Kind.DIRECTORY;
                case FILE -> ChooserEntry.Kind.FILE;
                case OTHER -> ChooserEntry.Kind.OTHER;
            };
            String name = path.equals("/") ? "/" : path.substring(path.lastIndexOf('/') + 1);
            return new ChooserEntry(path, name, kind, kind == ChooserEntry.Kind.DIRECTORY ? -1 : entry.size(), entry.modifiedMillis(), name.startsWith("."));
        }
    }
}

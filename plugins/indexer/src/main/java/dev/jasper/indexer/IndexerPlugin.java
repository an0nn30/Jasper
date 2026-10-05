package dev.jasper.indexer;

import dev.jasper.editor.api.Editor;
import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.detect.LocalProbe;
import dev.jasper.indexer.detect.Markers;
import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.IndexStore;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.LocalLister;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.index.RemotePlace;
import dev.jasper.indexer.remote.RemoteBridge;
import dev.jasper.indexer.ui.FilesScope;
import dev.jasper.indexer.ui.IndexerSettingsPanel;
import dev.jasper.indexer.watch.CwdWatcher;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.StatusProgress;
import dev.jasper.sdk.ui.StatusProgressState;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Project Index: offers to index the project a terminal enters, locally or on Remote's saved hosts, keeps indexes
 * fresh, and finds their files from the palette's Files scope. Owns every registration below; {@link #stop()} cancels
 * jobs and pending refreshes.
 */
public final class IndexerPlugin implements Plugin {
    /** The plugin id. */
    public static final String ID = "dev.jasper.indexer";
    /** Opens the palette on the Files scope. */
    public static final String GOTO = ID + ".goto";
    /** Indexes a folder the user picks. */
    public static final String INDEX_FOLDER = ID + ".index-folder";
    /** Reindexes the focused pane's project. */
    public static final String REINDEX = ID + ".reindex";
    /** Cancels foreground indexing. */
    public static final String CANCEL = ID + ".cancel";
    static final String PROGRESS = ID + ".progress";

    /**
     * What the plugin takes from the machine; tests replace each part.
     *
     * @param ui the UI thread
     * @param clock the current time
     * @param after runs a task on the UI thread after a delay and returns its cancel
     * @param clipboard the system clipboard
     * @param home the user's home folder
     * @param git the command that runs Git
     * @param confirm asks a yes-or-no question
     */
    record Environment(Executor ui, Supplier<Instant> clock, BiFunction<Duration, Runnable, Runnable> after, Consumer<String> clipboard,
                       Path home, List<String> git, Predicate<String> confirm) {
        static Environment system() {
            return new Environment(SwingUtilities::invokeLater, Instant::now, (delay, task) -> {
                var timer = new Timer((int) delay.toMillis(), event -> task.run());
                timer.setRepeats(false);
                timer.start();
                return timer::stop;
            }, IndexerPlugin::copy, Path.of(System.getProperty("user.home")), List.of("git"),
                question -> JOptionPane.showConfirmDialog(null, question, "Project Index", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION);
        }
    }

    private final Environment environment;
    private PluginContext context;
    private IndexerSettings settings;
    private Projects projects;
    private Indexer indexer;
    private CwdWatcher watcher;
    private Optional<Editor> editor = Optional.empty();
    private Optional<RemoteBridge> remote = Optional.empty();
    private PluginAction cancel;
    private StatusProgress progress;
    /** Terminal events received before the saved projects loaded, replayed in order once they have; null afterwards. */
    private List<Runnable> beforeLoad = new ArrayList<>();

    /** Created by the runtime. */
    public IndexerPlugin() { this(Environment.system()); }

    IndexerPlugin(Environment environment) { this.environment = environment; }

    @Override public void start(PluginContext context) {
        this.context = context;
        settings = new IndexerSettings(context.settings());
        try { editor = context.services().find(Editor.class); }
        catch (NoClassDefFoundError absent) { editor = Optional.empty(); }
        try { remote = RemoteBridge.find(context.services()); }
        catch (NoClassDefFoundError absent) { remote = Optional.empty(); }
        NotificationGroup prompts = context.notifications().group(new GroupSpec(ID + ".prompts", "Project indexing", DisplayMode.STICKY));
        NotificationGroup results = context.notifications().group(new GroupSpec(ID + ".results", "Index results", DisplayMode.HISTORY));
        projects = new Projects();
        var lister = new LocalLister(environment.git(), environment.home());
        indexer = new Indexer(projects, new IndexStore(context.dataDirectory()), job -> list(lister, job), context.background(),
            environment.ui(), environment.clock(), this::progress, results, context.notifications().group(), context.log());
        var icon = context.appearance().icon(IconName.SEARCH);
        context.actions().register(ActionSpec.of(GOTO, "Go to File…").withIcon(icon)
                .withKeywords(List.of("file", "open", "project", "index", "find"))
                .withDefaultBindings("cmd+p", "ctrl+alt+shift+p"),
            invoked -> context.palette().open(invoked.window(), FilesScope.ID, Optional.empty(), Optional.empty()));
        context.actions().register(ActionSpec.of(INDEX_FOLDER, "Index Folder…").withKeywords(List.of("index", "folder", "project", "files")),
            invoked -> indexFolder(invoked.window()));
        context.actions().register(ActionSpec.of(REINDEX, "Reindex Current Project").withKeywords(List.of("index", "refresh", "project")),
            this::reindex);
        cancel = context.actions().register(ActionSpec.of(CANCEL, "Cancel Indexing").withKeywords(List.of("index", "stop")),
            invoked -> indexer.cancelForeground());
        cancel.setEnabled(false);
        context.menus().standard(StandardMenu.FILE).add(INDEX_FOLDER);
        context.palette().register(new FilesScope(projects, new FilesScope.Hooks(this::current, this::openBlocked, this::reachable,
            this::hostLabel, this::connected, this::open, this::terminal, this::copyText, environment.clipboard()), GOTO, icon));
        context.settings().page(new IndexerSettingsPanel(settings, projects, indexer, this::location, this::reachable, this::connected,
            () -> context.terminals().activeWindow().ifPresent(this::indexFolder), environment.confirm()));
        watcher = new CwdWatcher(projects, indexer, settings, prompts, new CwdWatcher.Hooks(this::detect, this::connectedPlace,
            this::detectRemote, pane -> context.terminals().pane(pane).map(handle -> handle.tab().window()), environment.after(),
            environment.home()));
        context.events().subscribe(TerminalEvents.CWD_CHANGED, event -> deliver(() -> watcher.cwd(event)));
        context.events().subscribe(TerminalEvents.COMMAND_FINISHED, event -> deliver(() -> watcher.finished(event)));
        indexer.load().whenComplete((loaded, failure) -> {
            List<Runnable> waiting = beforeLoad;
            beforeLoad = null;
            for (Runnable event : waiting) {
                try { event.run(); }
                catch (RuntimeException broken) {
                    context.log().log(System.Logger.Level.WARNING, "Project Index could not handle a terminal event", broken);
                }
            }
        });
    }

    @Override public void stop() {
        if (beforeLoad != null) beforeLoad.clear();
        if (watcher != null) watcher.close();
        if (indexer != null) indexer.close();
    }

    /** Hands a terminal event to the watcher, or holds it until the saved projects have loaded so they are not offered again. */
    private void deliver(Runnable event) {
        if (beforeLoad != null) beforeLoad.add(event);
        else event.run();
    }

    // ---- where things are

    private Optional<ProjectKey> current(PaletteQuery query) {
        return query.target().or(() -> context.terminals().activePane()).flatMap(this::projectOf);
    }

    private Optional<ProjectKey> projectOf(PaneHandle pane) {
        Optional<Projects.Entry> entry = pane.info().workingDirectory().filter(Path::isAbsolute)
            .flatMap(folder -> projects.containing(folder.normalize()));
        if (entry.isEmpty()) entry = remote.flatMap(bridge -> bridge.locate(pane.id()))
            .flatMap(place -> projects.containing(place.host(), place.folder()));
        return entry.map(found -> found.project().key());
    }

    private Optional<RemotePlace> connectedPlace(java.util.UUID paneId) {
        return remote.flatMap(bridge -> bridge.locate(paneId).filter(place -> bridge.connected(place.host())));
    }

    private Optional<String> hostLabel(ProjectKey key) {
        return key.host().map(host -> remote.flatMap(bridge -> bridge.hostName(host)).orElse("unknown host"));
    }

    private boolean reachable(ProjectKey key) {
        return key.host().map(host -> remote.flatMap(bridge -> bridge.hostName(host)).isPresent()).orElse(true);
    }

    private boolean connected(ProjectKey key) {
        return key.host().map(host -> remote.map(bridge -> bridge.connected(host)).orElse(false)).orElse(true);
    }

    private String location(ProjectKey key) { return hostLabel(key).map(host -> host + ": " + key.root()).orElse(key.root()); }

    private Optional<String> openBlocked(ProjectKey key) {
        if (!key.isLocal()) {
            if (remote.isEmpty()) return Optional.of("Install the Remote plugin to open this file");
            if (!reachable(key)) return Optional.of("Host no longer saved");
        }
        return editor.isPresent() ? Optional.empty() : Optional.of("Install the Editor plugin to open files");
    }

    // ---- what rows do

    /** Opens a file in the Editor; a local file that is gone queues a refresh of its project (the Editor reports the error). */
    private void open(FileRef ref, WindowHandle window) {
        if (!ref.key().isLocal()) {
            remote.ifPresent(bridge -> bridge.open(ref, window));
            return;
        }
        editor.ifPresent(found -> found.open(ref.localPath(), window));
        context.background().execute(() -> {
            if (!Files.exists(ref.localPath())) environment.ui().execute(() -> indexer.refreshIfDue(ref.key(), Duration.ZERO));
        });
    }

    private void terminal(FileRef ref, WindowHandle window) {
        if (ref.key().isLocal()) context.terminals().openTab(window, OpenRequest.localIn(ref.localPath().getParent()));
        else remote.ifPresent(bridge -> bridge.openTerminal(ref, window));
    }

    private String copyText(FileRef ref) {
        return ref.key().isLocal() ? ref.localPath().toString() : hostLabel(ref.key()).orElse("") + ":" + ref.remotePath();
    }

    // ---- listing and detection

    private CompletionStage<Listing> list(LocalLister lister, Indexer.Job job) {
        List<String> excludes = settings.excludes.get();
        int max = settings.maxFiles.get();
        if (!job.key().isLocal()) {
            if (remote.isEmpty()) return CompletableFuture.failedFuture(new IOException("Install the Remote plugin to index " + job.key().root()));
            Optional<WindowHandle> signIn = job.foreground() ? job.window().or(() -> context.terminals().activeWindow()) : Optional.empty();
            return remote.get().list(job.key(), excludes, max, job::cancelled, signIn);
        }
        return CompletableFuture.supplyAsync(() -> {
            try { return lister.list(job.key().path(), excludes, max, job::cancelled); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }, context.background());
    }

    private CompletionStage<Optional<Detection<Path>>> detect(Path folder) {
        Markers markers = settings.markers();
        return probe(context.background(), environment.ui(), () -> LocalProbe.detect(folder, environment.home(), markers));
    }

    /**
     * Runs a probe in the background and completes on the UI thread; a probe that throws anything finds nothing, so the
     * watcher is never left waiting.
     *
     * @param background where the probe runs
     * @param ui where the answer is delivered
     * @param probe the probe
     * @param <T> what it finds
     * @return the answer
     */
    static <T> CompletionStage<Optional<T>> probe(Executor background, Executor ui, Supplier<Optional<T>> probe) {
        var result = new CompletableFuture<Optional<T>>();
        background.execute(() -> {
            Optional<T> found;
            try { found = probe.get(); }
            catch (Throwable failure) { found = Optional.empty(); }
            Optional<T> answer = found;
            ui.execute(() -> result.complete(answer));
        });
        return result;
    }

    private CompletionStage<Optional<Detection<String>>> detectRemote(RemotePlace place) {
        var result = new CompletableFuture<Optional<Detection<String>>>();
        if (remote.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        remote.get().detect(place, settings.markers()).whenComplete((found, failure) ->
            environment.ui().execute(() -> result.complete(failure == null ? found : Optional.empty())));
        return result;
    }

    // ---- actions

    private void indexFolder(WindowHandle window) {
        Set<String> sources = remote.map(bridge -> Set.of(bridge.fileSourceId())).orElse(Set.of());
        List<FileLocation> picked = context.windows().choose(new FileChoice(window, "Index Folder", FileChoice.Mode.DIRECTORY, false,
            Optional.empty(), Optional.empty(), sources));
        for (FileLocation location : picked) {
            Optional<ProjectKey> key = switch (location) {
                case FileLocation.Local local -> Optional.of(ProjectKey.local(local.path()));
                case FileLocation.Sourced sourced -> remote.flatMap(bridge -> bridge.folder(sourced))
                    .map(place -> ProjectKey.remote(place.host(), place.folder()));
            };
            key.ifPresent(found -> {
                indexer.add(new Project(found, found.name(), true));
                indexer.request(found, true, Optional.of(window));
            });
        }
    }

    private void reindex(ActionContext invoked) {
        Optional<ProjectKey> key = invoked.pane().flatMap(this::projectOf);
        if (key.isPresent()) indexer.request(key.get(), true, Optional.of(invoked.window()));
        else context.notifications().group().info("No indexed project here",
            "The focused terminal is not inside an indexed project. Use Index Folder… to add one.");
    }

    private void progress(Optional<String> text) {
        if (cancel != null) cancel.setEnabled(text.isPresent());
        if (text.isEmpty()) {
            if (progress != null) progress.close();
            progress = null;
            return;
        }
        if (progress == null) progress = context.statusBar().addProgress(new StatusItemSpec(PROGRESS, Side.RIGHT, 60));
        progress.update(new StatusProgressState(text.get(), "", text.get(), OptionalDouble.empty(), CANCEL, null));
        progress.setVisible(true);
    }

    static void copy(String text) {
        try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null); }
        catch (HeadlessException | IllegalStateException unavailable) { /* nothing to copy to */ }
    }
}

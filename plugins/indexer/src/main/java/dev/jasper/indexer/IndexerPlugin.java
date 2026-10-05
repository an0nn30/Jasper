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
 * Project Index: offers to index the project a terminal enters, keeps indexes fresh, and finds their files from the
 * palette's Files scope. Owns every registration below; {@link #stop()} cancels jobs and pending refreshes.
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
    private PluginAction cancel;
    private StatusProgress progress;

    /** Created by the runtime. */
    public IndexerPlugin() { this(Environment.system()); }

    IndexerPlugin(Environment environment) { this.environment = environment; }

    @Override public void start(PluginContext context) {
        this.context = context;
        settings = new IndexerSettings(context.settings());
        try { editor = context.services().find(Editor.class); }
        catch (NoClassDefFoundError absent) { editor = Optional.empty(); }
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
        context.palette().register(new FilesScope(projects, hooks(), GOTO, icon));
        context.settings().page(new IndexerSettingsPanel(settings, projects, indexer, ProjectKey::root,
            () -> context.terminals().activeWindow().ifPresent(this::indexFolder), environment.confirm()));
        watcher = new CwdWatcher(projects, indexer, settings, prompts, new CwdWatcher.Hooks(this::detect,
            pane -> context.terminals().pane(pane).map(handle -> handle.tab().window()), environment.after(), environment.home()));
        context.events().subscribe(TerminalEvents.CWD_CHANGED, watcher::cwd);
        context.events().subscribe(TerminalEvents.COMMAND_FINISHED, watcher::finished);
        indexer.load();
    }

    @Override public void stop() {
        if (watcher != null) watcher.close();
        if (indexer != null) indexer.close();
    }

    private FilesScope.Hooks hooks() {
        return new FilesScope.Hooks(this::current,
            key -> !key.isLocal() ? Optional.of("Install the Remote plugin to open this file")
                : editor.isPresent() ? Optional.empty() : Optional.of("Install the Editor plugin to open files"),
            ProjectKey::isLocal, key -> Optional.empty(), key -> true,
            this::open,
            (ref, window) -> context.terminals().openTab(window, OpenRequest.localIn(ref.localPath().getParent())),
            ref -> ref.localPath().toString(), environment.clipboard());
    }

    /** Opens a file in the Editor; a file that is gone queues a refresh of its project (the Editor reports the error). */
    private void open(FileRef ref, WindowHandle window) {
        editor.ifPresent(found -> found.open(ref.localPath(), window));
        context.background().execute(() -> {
            if (!Files.exists(ref.localPath())) environment.ui().execute(() -> indexer.refreshIfDue(ref.key(), Duration.ZERO));
        });
    }

    private Optional<ProjectKey> current(PaletteQuery query) {
        return query.target().or(() -> context.terminals().activePane())
            .flatMap(pane -> pane.info().workingDirectory()).filter(Path::isAbsolute)
            .flatMap(folder -> projects.containing(folder.normalize())).map(entry -> entry.project().key());
    }

    private CompletionStage<Listing> list(LocalLister lister, Indexer.Job job) {
        if (!job.key().isLocal()) return CompletableFuture.failedFuture(new IOException("Remote projects need the Remote plugin"));
        List<String> excludes = settings.excludes.get();
        int max = settings.maxFiles.get();
        return CompletableFuture.supplyAsync(() -> {
            try { return lister.list(job.key().path(), excludes, max, job::cancelled); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }, context.background());
    }

    private CompletionStage<Optional<Detection<Path>>> detect(Path folder) {
        Markers markers = settings.markers();
        var result = new CompletableFuture<Optional<Detection<Path>>>();
        context.background().execute(() -> {
            Optional<Detection<Path>> found;
            try { found = LocalProbe.detect(folder, environment.home(), markers); }
            catch (RuntimeException failure) { found = Optional.empty(); }
            Optional<Detection<Path>> answer = found;
            environment.ui().execute(() -> result.complete(answer));
        });
        return result;
    }

    private void indexFolder(WindowHandle window) {
        List<FileLocation> picked = context.windows().choose(new FileChoice(window, "Index Folder", FileChoice.Mode.DIRECTORY, false,
            Optional.empty(), Optional.empty(), Set.of()));
        for (FileLocation location : picked) {
            if (!(location instanceof FileLocation.Local local)) continue;
            ProjectKey key = ProjectKey.local(local.path());
            indexer.add(new Project(key, key.name(), true));
            indexer.request(key, true, Optional.of(window));
        }
    }

    private void reindex(ActionContext invoked) {
        Optional<ProjectKey> key = invoked.pane().flatMap(pane -> pane.info().workingDirectory()).filter(Path::isAbsolute)
            .flatMap(folder -> projects.containing(folder.normalize())).map(entry -> entry.project().key());
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

package dev.jasper.editor;

import dev.jasper.editor.api.LocationResolver;
import dev.jasper.editor.open.Locations;
import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.editor.command.EditorCommand;
import dev.jasper.editor.external.DefaultEditor;
import dev.jasper.editor.external.Detection;
import dev.jasper.editor.open.Openers;
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.recent.RecentFiles;
import dev.jasper.editor.recent.RecentFilesScope;
import dev.jasper.editor.recent.Reopeners;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.ui.EditorEnvironment;
import dev.jasper.editor.ui.EditorSettings;
import dev.jasper.editor.ui.EditorSettingsPanel;
import dev.jasper.editor.ui.EditorWindow;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.SplitItemSpec;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.ToolbarItem;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Edits local and remote text files in app-owned workspace tabs; publishes {@link Editor} for other plugins. */
public final class EditorPlugin implements Plugin {
    static final String OPEN = "dev.jasper.editor.open", NEW_TAB = "dev.jasper.editor.new_editor_tab", SHOW = "dev.jasper.editor.show",
        NEW_WINDOW = "dev.jasper.editor.new_window", REVERT = "dev.jasper.editor.revert",
        RELOAD_VIMRC = "dev.jasper.editor.reload_vimrc";
    /**
     * The Editor's split item (tabs and panes spec 8 and 10): Split Right with Editor, which is New Editor Pane, and Split
     * Down with Editor (workspace plan 8b Rulings 13).
     */
    static final SplitItemSpec SPLIT = SplitItemSpec.of("dev.jasper.editor.editor", "Editor").withIcon("file");

    private final Executor ui;
    private final BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments;
    private final Supplier<Detection.Probe> probes;
    private final BiFunction<System.Logger, NotificationGroup, DefaultEditor.Launcher> launchers;
    private EditorEnvironment environment;
    private EditorWindow window;
    private RecentFiles recent;
    private Openers openers;
    private final Locations locations = new Locations();

    /** The production plugin: Swing's event queue, modal dialogs, running timers, this machine's editors and real programs. */
    public EditorPlugin() {
        this(SwingUtilities::invokeLater, (context, settings) -> EditorEnvironment.production(context, SwingUtilities::invokeLater, settings),
            Detection.Probe::system, DefaultEditor.Launcher::detached);
    }

    /** For tests: finds no installed editor and starts no program. */
    EditorPlugin(Executor ui, BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments) {
        this(ui, environments, () -> new Detection.Probe(System.getProperty("os.name", ""), List.of(), Path.of(System.getProperty("user.home")),
            Map.of(), file -> false), (log, notices) -> (command, program) -> false);
    }

    EditorPlugin(Executor ui, BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments,
                 Supplier<Detection.Probe> probes, BiFunction<System.Logger, NotificationGroup, DefaultEditor.Launcher> launchers) {
        this.ui = ui;
        this.environments = environments;
        this.probes = probes;
        this.launchers = launchers;
    }

    @Override public void start(PluginContext context) {
        Languages.register();
        // Set.copyOf tolerates a platform that lists a family twice; Set.of would throw.
        Set<String> fonts = Set.copyOf(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        EditorSettings.Declared settings = new EditorSettings.Declared(context.settings(), fonts);
        environment = environments.apply(context, settings);
        openers = new Openers(failure -> context.log().log(System.Logger.Level.WARNING,
            "Open in Editor: an opener failed, so the next opener or the local chooser answers instead", failure));
        Reopeners reopeners = new Reopeners(file -> new LocalDocument(file, context.background()));
        recent = RecentFiles.load(context.dataDirectory().resolve("recent-files.toml"), context.background(), settings::recentFilesDays, Instant.now());
        var scope = new RecentFilesScope(recent, reopeners, Instant::now, (document, owner) -> window().open(document, owner),
            context.notifications().group(), "file");
        context.settings().onChanged(() -> ui.execute(() -> { if (window != null) window.apply(); }));
        context.settings().onChanged(() -> ui.execute(() -> { recent.prune(Instant.now()); scope.changed(); }));
        context.appearance().onWorkspaceChanged(variant -> ui.execute(() -> { if (window != null) window.apply(); }));
        context.services().publish(Editor.class, new Editor() {
            @Override public void open(Path file) { window().openLocal(file); }
            @Override public void open(EditorDocument document) { window().open(document); }
            @Override public void open(Path file, WindowOwner owner) { window().openLocal(file, owner); }
            @Override public void open(EditorDocument document, WindowOwner owner) { window().open(document, owner); }
            @Override public Subscription reopener(String keyPrefix, Function<String, Optional<Function<WindowOwner, EditorDocument>>> resolve) {
                return reopeners.register(keyPrefix, resolve);
            }
            @Override public Subscription opener(Predicate<OpenRequest> handler) { return openers.register(handler); }
            @Override public Subscription locationDocuments(String sourceId, LocationResolver resolver) { return locations.register(sourceId, resolver); }
        });
        context.actions().register(ActionSpec.of(OPEN, "Open in Editor…").withIcon("file")
                .withKeywords(List.of("editor", "open", "file", "edit")).withDefaultBinding("cmd+o"),
            invoked -> window().openFrom(invoked.window(), invoked.pane()));
        // Tabs and panes spec 8 and 10: an untitled file in a new tab, or, through the split item, in a new pane beside the
        // focused one; none has a default key.
        context.actions().register(ActionSpec.of(NEW_TAB, "New Editor Tab").withKeywords(List.of("editor", "new", "tab", "file", "untitled")),
            invoked -> window().newUntitled(invoked.window(), dev.jasper.sdk.ui.TabPlacement.NEW_TAB));
        context.windows().splitItem(SPLIT, (shown, placement) -> window().newUntitled(shown, placement));
        // Workspace plan 8b Rulings 20: the tab strip's menu, which hides with one tab, was these two's only place; as
        // actions with no default key they reach the palette and the keymap. Each acts on the window it runs in.
        context.actions().register(ActionSpec.of(REVERT, "Revert to Saved").withKeywords(List.of("editor", "revert", "saved", "discard")),
            invoked -> window().revert(invoked.window()));
        context.actions().register(ActionSpec.of(RELOAD_VIMRC, "Reload vimrc").withKeywords(List.of("editor", "vim", "vimrc", "reload")),
            invoked -> window().reloadVimrc(invoked.window()));
        // Workspace tabs spec 6.5: an untitled document in a child window of its own; no default key. Its icon is the toolbar button's.
        context.actions().register(ActionSpec.of(NEW_WINDOW, "New Editor Window").withIcon("file")
                .withKeywords(List.of("editor", "new", "window", "untitled")),
            invoked -> window().newUntitled(invoked.window(), dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW));
        context.actions().register(ActionSpec.of(SHOW, "Show Editor").withKeywords(List.of("editor", "tabs")), invoked -> window().show());
        context.actions().register(ActionSpec.of(RecentFilesScope.ACTION, "Recent Files…").withKeywords(List.of("editor", "recent", "files", "open")),
            invoked -> context.palette().open(invoked.window(), RecentFilesScope.ID, Optional.empty(), Optional.empty()));
        context.commandLine().handle("editor", new EditorCommand(this::window)::run);
        DefaultEditor defaults = new DefaultEditor(() -> new DefaultEditor.Preference(settings.defaultEditor(), settings.customCommand(),
                settings.customInTerminal(), settings.keepEnvironmentEditor()),
            probes, context.background(), ui, context.terminals(), (file, line, owner) -> window().edit(file, line, owner),
            launchers.apply(context.log(), context.notifications().group()), context.commandLine().client(), System.getProperty("os.name", ""), context.log());
        defaults.findSaved();
        defaults.detect();
        context.terminals().environment(defaults::variables);
        try {
            context.platform().editor(defaults::edit);
        } catch (IllegalStateException provided) {
            // A bundled Editor replaces another plugin's editor; a copy that is not bundled keeps running without it.
            context.log().log(System.Logger.Level.INFO, "Another plugin provides the editor Jasper opens files with, so the default "
                + "editor setting does not choose it: " + provided.getMessage());
        }
        context.settings().page(new EditorSettingsPanel(context.kit(), settings, defaults, System.getenv()));
        context.palette().register(scope);
        PluginMenu file = context.menus().standard(StandardMenu.FILE);
        file.add(OPEN);
        file.add(NEW_TAB);
        file.add(SPLIT.rightAction()); // New Editor Pane (workspace plan 8b Rulings 21)
        file.add(NEW_WINDOW);
        context.toolbar().add(ToolbarItem.action(NEW_WINDOW));
    }

    @Override public void stop() {
        if (window != null) window.dispose();
    }

    private EditorWindow window() {
        if (window == null) window = new EditorWindow(environment, () -> window = null, document -> recent.record(document, Instant.now()), openers::handle, locations);
        return window;
    }
}

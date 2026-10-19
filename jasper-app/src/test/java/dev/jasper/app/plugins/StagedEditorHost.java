package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.app.windows.DialogFit;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginWindow;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.WindowSpec;
import dev.jasper.ui.swing.KitView;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.SwingUtilities;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;

/**
 * The staged Editor jar in the application's real plugin host (Plan 2 Task 9a, the {@link StagedRemoteHost} shape): the
 * production {@code EditorPlugin} starts with headless windows, and its dialogs are mounted from its own classes as the
 * Editor mounts them (the question sheet as {@code DialogAsker} does, Go to Line and Language as the Editor window does,
 * Saving through the plugin's own {@code SaveWait}), owned by a plugin window of the Editor's (production owns them by
 * the selected content tab; the owner does not change the dialog's content). Plugin dialogs are packed stand-ins that
 * follow the production {@link DialogFit} rule. No document, file or terminal is touched. Call from the test thread.
 */
final class StagedEditorHost implements AutoCloseable {
    static final PluginInfo EDITOR = new PluginInfo("dev.jasper.editor", "Editor", "0.9.0", Set.of(Capabilities.PALETTE_CONTRIBUTE,
        Capabilities.TERMINAL_ENVIRONMENT, Capabilities.EDITOR_PROVIDE, Capabilities.TERMINAL_OPEN));

    final Contributions contributions;
    final AuxiliaryWindows auxiliary;
    final TerminalFixture terminals;
    final PluginHost host;
    final PluginClassLoader loader;
    final List<String> problems = new CopyOnWriteArrayList<>();
    final Plugin plugin;
    final PluginContext context;
    final PluginWindow owner;
    final javax.swing.JPanel mainHolder = new javax.swing.JPanel(new java.awt.BorderLayout());
    final javax.swing.JPanel separateHolder = new javax.swing.JPanel(new java.awt.BorderLayout());
    private final Map<AuxiliarySurface, StagedRemoteHost.PackedDialog> dialogs = new HashMap<>();

    StagedEditorHost(Path data, boolean dark) throws Exception {
        this(data, dark, null);
    }

    StagedEditorHost(Path data, boolean dark, WorkspaceAppearanceFixture appearance) throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve(EDITOR.id());
        contributions = onEdtValue(Contributions::new);
        auxiliary = onEdtValue(() -> new AuxiliaryWindows(dev.jasper.app.persistence.UiState.inMemory(), this::shell));
        terminals = onEdtValue(TerminalFixture::new);
        onEdt(() -> {
            var main = terminals.addMainWindow(); var separate = terminals.addWindow();
            contributions.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(main,
                e -> mainHolder.add(e.component()), e -> mainHolder.remove(e.component()), e -> e.active(true), () -> { }));
            contributions.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(separate,
                e -> separateHolder.add(e.component()), e -> separateHolder.remove(e.component()), e -> e.active(true), () -> { }));
            contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, main::equals,
                tab -> contributions.tabs().move(tab, separate)));
        });
        host = AppContractTest.host(data.resolve("home"), Duration.ofMillis(200), contributions, new AtomicBoolean(dark), auxiliary,
            terminals.registry, new NotificationsFixture(problems), new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>(),
            PluginRuntime.ShellProfiles.NONE, new CopyOnWriteArrayList<>(), appearance,
            new GlassSource(component -> SwingUtilities.isDescendingFrom(component, separateHolder) ? .45 : .8,
                listener -> new dev.jasper.app.lifecycle.Subscription(() -> { })));
        loader = new PluginClassLoader(EDITOR.id(), ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(), Map.of());
        plugin = (Plugin) loader.loadClass("dev.jasper.editor.EditorPlugin").getConstructor().newInstance();
        // This fixture starts the plugin without its manifest, so declare the subcommand the manifest would.
        onEdt(() -> host.commands.declare(Subcommands.of(List.of(new Subcommands.Owner("editor", "Open files in Jasper's Editor", EDITOR.id(), EDITOR.name())))));
        onEdt(() -> host.start(new HostedPlugin(EDITOR, Set.of(), Set.of(), Set.of("dev.jasper.editor.api"), loader, () -> plugin)));
        context = onEdtValue(() -> {
            try {
                var field = plugin.getClass().getDeclaredField("environment"); field.setAccessible(true);
                Object environment = field.get(plugin);
                return (PluginContext) environment.getClass().getMethod("context").invoke(environment);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        owner = onEdtValue(() -> {
            var window = context.windows().create(new WindowSpec("dev.jasper.editor.window", "Editor", new Dimension(900, 600), true));
            window.show();
            return window;
        });
    }

    /** The production controller, created lazily as its commands do; UI thread. */
    Object editorWindow() {
        try { var method=plugin.getClass().getDeclaredMethod("window"); method.setAccessible(true); return method.invoke(plugin); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    static Object call(Object target,String name,Class<?>[] types,Object... args) {
        try { java.lang.reflect.Method method;
            try { method=target.getClass().getMethod(name,types); } catch (NoSuchMethodException missing) { method=target.getClass().getDeclaredMethod(name,types); }
            method.setAccessible(true);return method.invoke(target,args); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    static Object call(Object target,String name) { return call(target,name,new Class<?>[0]); }
    Object selectedTab() { return ((Optional<?>)call(editorWindow(),"selected")).orElseThrow(); }
    UiView documentView() { return (UiView)call(selectedTab(),"view"); }
    javax.swing.JTextArea documentText() { return (javax.swing.JTextArea)call(selectedTab(),"area"); }
    dev.jasper.sdk.ui.ContentTab contentTab() {
        try { var field=editorWindow().getClass().getDeclaredField("hosted");field.setAccessible(true);
            return (dev.jasper.sdk.ui.ContentTab)((Map<?,?>)field.get(editorWindow())).get(selectedTab()); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** Plugin dialogs get a packed stand-in (as {@code NativeShells} packs them); every other surface touches no native window. */
    private AuxiliarySurface.Shell shell(AuxiliarySurface surface) {
        if (surface.kind() == AuxiliarySurface.Kind.DIALOG && surface.id().isEmpty()) {
            var dialog = new StagedRemoteHost.PackedDialog(surface.holder());
            dialog.fit = new DialogFit(surface.holder(), dialog);
            surface.fitWith(dialog.fit);
            dialogs.put(surface, dialog);
            return new AuxiliarySurface.Shell(() -> { dialog.bounds.setSize(dialog.packedSize()); dialog.fit.packed(); }, () -> { }, () -> { },
                title -> { }, () -> dialog.bounds, (title, initial) -> Optional.empty());
        }
        return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 10, 10), (title, initial) -> Optional.empty());
    }

    /** The size of a shown plugin dialog's stand-in window; UI thread. */
    Dimension dialogSize(AuxiliarySurface surface) { return dialogs.get(surface).size(); }

    /** Mounts an Editor controller's view on a modal dialog as the Editor does: closing the dialog closes the controller; UI thread. */
    private PluginDialog mount(String title, boolean modal, Object controller) {
        try {
            PluginDialog dialog = context.windows().dialog(new DialogSpec(title, (WindowOwner) owner, modal));
            var close = controller.getClass().getMethod("close"); close.setAccessible(true);
            var view = controller.getClass().getDeclaredMethod("view"); view.setAccessible(true);
            dialog.onClosed(() -> { try { close.invoke(controller); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); } });
            dialog.setContent((UiView) view.invoke(controller));
            dialog.show();
            return dialog;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /**
     * Asks the Editor's question sheet ({@code Forms.QuestionView}, as {@code DialogAsker} mounts it); each answer goes to
     * {@code answers} and closes the dialog. UI thread.
     */
    PluginDialog ask(String title, String message, List<String> choices, boolean destructive, Consumer<String> answers) {
        try {
            Object question = loader.loadClass("dev.jasper.editor.session.Question").getConstructor(String.class, String.class, List.class, boolean.class)
                .newInstance(title, message, choices, destructive);
            var type = loader.loadClass("dev.jasper.editor.ui.Forms$QuestionView");
            var constructor = type.getDeclaredConstructor(UiKit.class, question.getClass(), Consumer.class); constructor.setAccessible(true);
            var dialog = new AtomicReference<PluginDialog>();
            Object view = constructor.newInstance(context.kit(), question, (Consumer<String>) choice -> { answers.accept(choice); dialog.get().close(); });
            dialog.set(mount(title, true, view));
            return dialog.get();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** Opens the Editor's Go to Line over a document of {@code lines} lines; a valid line goes to {@code go} and closes it. UI thread. */
    PluginDialog goToLine(int current, int lines, IntConsumer go) {
        try {
            var dialog = new AtomicReference<PluginDialog>();
            Object panel = loader.loadClass("dev.jasper.editor.ui.GoToLinePanel")
                .getConstructor(UiKit.class, int.class, int.class, IntConsumer.class, Runnable.class)
                .newInstance(context.kit(), current, lines, (IntConsumer) line -> { dialog.get().close(); go.accept(line); }, (Runnable) () -> dialog.get().close());
            dialog.set(mount("Go to Line", true, panel));
            return dialog.get();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** Opens the Editor's Language picker with {@code current} selected; a choice goes to {@code chosen} and closes it. UI thread. */
    PluginDialog language(String current, Consumer<String> chosen) {
        try {
            var dialog = new AtomicReference<PluginDialog>();
            Object picker = loader.loadClass("dev.jasper.editor.ui.LanguagePicker")
                .getConstructor(UiKit.class, String.class, Consumer.class, Runnable.class)
                .newInstance(context.kit(), current, (Consumer<String>) style -> { dialog.get().close(); chosen.accept(style); }, (Runnable) () -> dialog.get().close());
            dialog.set(mount("Language", true, picker));
            return dialog.get();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /**
     * Runs the plugin's own {@code SaveWait} on the UI thread (its nested loop keeps events flowing) and returns its result
     * once known; call from the test thread, then drive the "Saving" surface.
     */
    @SuppressWarnings("unchecked")
    CompletableFuture<Boolean> saveWait(String message, CompletableFuture<Boolean> saves) {
        var result = new CompletableFuture<Boolean>();
        SwingUtilities.invokeLater(() -> {
            try {
                Object wait = loader.loadClass("dev.jasper.editor.ui.SaveWait").getConstructor(dev.jasper.sdk.ui.Windows.class, UiKit.class, WindowOwner.class)
                    .newInstance(context.windows(), context.kit(), owner);
                result.complete(((java.util.function.BiFunction<String, CompletableFuture<Boolean>, Boolean>) wait).apply(message, saves));
            } catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    AuxiliarySurface awaitSurface(String title) throws InterruptedException {
        StagedVaultHost.await(() -> auxiliary.open().stream().anyMatch(s -> s.title().equals(title) && !s.closed()), "surface " + title);
        return onEdtValue(() -> auxiliary.open().stream().filter(s -> s.title().equals(title) && !s.closed()).findFirst().orElseThrow());
    }

    /** The kit view mounted on the surface; UI thread. */
    static KitView view(AuxiliarySurface surface) { return StagedVaultHost.kitView(surface); }

    /** A key press through Swing's real binding path: the component's own bindings, then its ancestors'. */
    static void press(Component component, int key, int modifiers, char character) {
        SwingUtilities.processKeyBindings(new KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, key, character));
    }

    /** Space on a focused button, as the look and feel takes it: pressed, then released. */
    static void space(Component button) {
        SwingUtilities.processKeyBindings(new KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
        SwingUtilities.processKeyBindings(new KeyEvent(button, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
    }

    static void enter(Component component) { press(component, KeyEvent.VK_ENTER, 0, '\n'); }
    static void escape(Component component) { press(component, KeyEvent.VK_ESCAPE, 0, (char) 27); }
    static void down(Component component) { press(component, KeyEvent.VK_DOWN, 0, KeyEvent.CHAR_UNDEFINED); }
    static void up(Component component) { press(component, KeyEvent.VK_UP, 0, KeyEvent.CHAR_UNDEFINED); }

    @Override public void close() {
        var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
        try {
            onEdt(() -> pending.set(host.stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted stopping staged Editor", interrupted);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new IllegalStateException("Could not stop staged Editor", failure);
        } finally {
            try { onEdt(auxiliary::close); }
            finally {
                try { loader.close(); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                finally {
                    try { dev.jasper.app.workspace.DesktopTestSupport.closeOwners(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }
            }
        }
    }
}

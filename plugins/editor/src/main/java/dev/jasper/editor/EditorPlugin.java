package dev.jasper.editor;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.ui.EditorEnvironment;
import dev.jasper.editor.ui.EditorSettings;
import dev.jasper.editor.ui.EditorWindow;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.StandardMenu;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import javax.swing.SwingUtilities;

/** Edits local and remote text files in app-owned workspace tabs; publishes {@link Editor} for other plugins. */
public final class EditorPlugin implements Plugin {
    static final String OPEN = "dev.jasper.editor.open", NEW = "dev.jasper.editor.new", SHOW = "dev.jasper.editor.show";

    private final Executor ui;
    private final BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments;
    private EditorEnvironment environment;
    private EditorWindow window;

    /** The production plugin: Swing's event queue, modal dialogs and running timers. */
    public EditorPlugin() {
        this(SwingUtilities::invokeLater, (context, settings) -> EditorEnvironment.production(context, SwingUtilities::invokeLater, settings));
    }

    EditorPlugin(Executor ui, BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments) {
        this.ui = ui;
        this.environments = environments;
    }

    @Override public void start(PluginContext context) {
        Languages.register();
        // Set.copyOf tolerates a platform that lists a family twice; Set.of would throw.
        Set<String> fonts = Set.copyOf(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        EditorSettings.Declared settings = new EditorSettings.Declared(context.settings(), fonts);
        environment = environments.apply(context, settings);
        context.settings().onChanged(() -> ui.execute(() -> { if (window != null) window.apply(); }));
        context.appearance().onChanged(variant -> ui.execute(() -> { if (window != null) window.apply(); }));
        context.services().publish(Editor.class, new Editor() {
            @Override public void open(Path file) { window().openLocal(file); }
            @Override public void open(EditorDocument document) { window().open(document); }
            @Override public void open(Path file, dev.jasper.sdk.WindowOwner owner) { window().openLocal(file, owner); }
            @Override public void open(EditorDocument document, dev.jasper.sdk.WindowOwner owner) { window().open(document, owner); }
        });
        context.actions().register(ActionSpec.of(OPEN, "Open in Editor…").withIcon(context.appearance().icon(IconName.FILE))
                .withKeywords(List.of("editor", "open", "file", "edit")).withDefaultBinding("cmd+o"),
            invoked -> {
                List<Path> files = context.windows().chooseFiles(invoked.window(), "Open in Editor", Optional.empty());
                if (!files.isEmpty()) window().openLocalFiles(files, invoked.window());
            });
        context.actions().register(ActionSpec.of(NEW, "New Editor File").withKeywords(List.of("editor", "new", "file", "untitled")),
            invoked -> window().newUntitled(invoked.window()));
        context.actions().register(ActionSpec.of(SHOW, "Show Editor").withKeywords(List.of("editor", "tabs")), invoked -> window().show());
        PluginMenu file = context.menus().standard(StandardMenu.FILE);
        file.add(OPEN);
        file.add(NEW);
    }

    @Override public void stop() {
        if (window != null) window.dispose();
    }

    private EditorWindow window() {
        if (window == null) window = new EditorWindow(environment, () -> window = null);
        return window;
    }
}

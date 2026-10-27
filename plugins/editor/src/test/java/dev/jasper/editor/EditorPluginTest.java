package dev.jasper.editor;

import dev.jasper.editor.api.LocationResolver;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.editor.api.ContextRequest;
import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.testsupport.StubDocument;
import dev.jasper.editor.ui.EditorEnvironment;
import dev.jasper.editor.ui.EditorWindow;
import dev.jasper.editor.external.Detection;
import dev.jasper.editor.external.EditorCommands;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.EditorPalette;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.SyntaxRole;
import dev.jasper.sdk.ui.SyntaxStyle;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class EditorPluginTest {
    @TempDir Path home;
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.editor", "Editor", "0.1.0",
        Set.of("palette.contribute", "terminal.environment", "editor.provide", "terminal.open", "terminal.observe"));

    private static EditorPlugin plugin() {
        return new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
            owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false));
    }

    /** A host that knows the Editor's {@code jasper editor} subcommand, as its manifest declares it. */
    private FakePluginHost host() {
        var host = new FakePluginHost(home.resolve("plugins"));
        host.declareSubcommand("dev.jasper.editor", "editor", "Open files in Jasper's Editor");
        return host;
    }

    private static final String RECENT = "dev.jasper.editor.recent";

    private static List<String> titles(List<String> rows) { return rows.stream().map(row -> row.split("\\|")[1]).toList(); }
    private static String rowId(List<String> rows) { return rows.getFirst().split("\\|")[0]; }
    private static void drain(FakePluginHost host) { while (host.runBackground() > 0) { } }

    /** @return the plugin's Editor window, whose selected view is the content tab active last */
    private static EditorWindow window(EditorPlugin plugin) throws ReflectiveOperationException {
        var field = EditorPlugin.class.getDeclaredField("window");
        field.setAccessible(true);
        return (EditorWindow) field.get(plugin);
    }

    @Test void recentFilesListOpenedFilesReopenThemAndForgetThem() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            assertThat(host.scopes()).contains(RECENT + "|Recent files|open,remove");
            assertThat(host.scopePresentation(RECENT)).as("spec 14.6").isEqualTo("AMBER|file|STANDARD,STANDARD");
            assertThat(host.scopesInAll()).contains(RECENT);
            assertThat(host.scopesInRecents()).contains(RECENT);
            assertThat(host.actions()).contains("dev.jasper.editor.recent-files|Recent Files\u2026|true");
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(Files.writeString(home.resolve("alpha.txt"), "a\n"));
            drain(host);
            editor.open(Files.writeString(home.resolve("beta.md"), "b\n"));
            drain(host);
            assertThat(titles(host.searchScope(RECENT, "", terminal, null))).containsExactly("beta.md", "alpha.txt");
            List<String> alpha = host.searchScope(RECENT, "ALPHA", terminal, null);
            assertThat(titles(alpha)).containsExactly("alpha.txt");
            host.executeInScope(RECENT, rowId(alpha), "open", terminal, null);
            assertThat(titles(host.searchScope(RECENT, "", terminal, null))).containsExactly("alpha.txt", "beta.md");
            host.executeInScope(RECENT, rowId(alpha), "remove", terminal, null);
            assertThat(titles(host.searchScope(RECENT, "", terminal, null))).containsExactly("beta.md");
            host.setSetting("dev.jasper.editor", "recent_files_days", 0L);
            assertThat(host.searchScope(RECENT, "", terminal, null)).isEmpty();
            drain(host);
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void nameMatchesComeBeforeHostAndLocationMatches() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:1", "prod.conf", "dustin@web:/etc/prod.conf", "web"));
            drain(host);
            editor.open(new StubDocument("stub:2", "nginx.conf", "dustin@prod:/etc/nginx.conf", "prod"));
            drain(host);
            assertThat(titles(host.searchScope(RECENT, "prod", terminal, null))).containsExactly("prod.conf", "nginx.conf");
        }
    }

    @Test void otherPluginsMakeTheirKeysReopenableAndOtherKeysAreDimmed() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:/srv/app.conf", "app.conf", "dustin@prod:/srv/app.conf", "prod"));
            drain(host);
            List<String> rows = host.searchScope(RECENT, "", terminal, null);
            assertThat(rows).containsExactly(rowId(rows) + "|app.conf|false");
            assertThat(host.availableInScope(RECENT, rowId(rows), "open", terminal, null)).isFalse();
            assertThat(host.availableInScope(RECENT, rowId(rows), "remove", terminal, null)).isTrue();
            var reopened = new ArrayList<WindowOwner>();
            Subscription registration = editor.reopener("stub:", key -> Optional.of(owner -> {
                reopened.add(owner);
                return new StubDocument(key, "app.conf", "dustin@prod:/srv/app.conf", "prod");
            }));
            rows = host.searchScope(RECENT, "prod", terminal, null);
            assertThat(rows).containsExactly(rowId(rows) + "|app.conf|true");
            assertThat(host.availableInScope(RECENT, rowId(rows), "open", terminal, null)).isTrue();
            host.executeInScope(RECENT, rowId(rows), "open", terminal, null);
            assertThat(reopened).hasSize(1);
            registration.close();
            assertThat(host.searchScope(RECENT, "", terminal, null)).containsExactly(rowId(rows) + "|app.conf|false");
        }
    }

    @Test void aRecentFileThatCannotBeReopenedIsPostedWithItsCause() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:/srv/app.conf", "app.conf", "dustin@prod:/srv/app.conf", "prod"));
            drain(host);
            editor.reopener("stub:", key -> Optional.of(owner -> { throw new IllegalStateException("the host is gone"); }));
            List<String> rows = host.searchScope(RECENT, "", terminal, null);
            host.executeInScope(RECENT, rowId(rows), "open", terminal, null);
            assertThat(host.notifications().lines()).containsExactly(
                "dev.jasper.editor|ERROR|Could not reopen a recent file|dustin@prod:/srv/app.conf: the host is gone|IllegalStateException");
        }
    }

    @Test void openInEditorShowsTheLocalChooserWhenNoOpenerAnswers() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            var asked = new ArrayList<OpenRequest>();
            consumer.get().services().require(Editor.class).opener(request -> { asked.add(request); return false; });
            host.queueFileChoice(List.of(new FileLocation.Local(Files.writeString(home.resolve("local.txt"), "x\n"))));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(asked).singleElement().satisfies(request -> {
                assertThat(request.window().id()).isEqualTo(terminal);
                assertThat(request.pane()).isEmpty();
                assertThat(request.documentKey()).isEmpty();
            });
            assertThat(host.contentTabs()).hasSize(1);
        }
    }

    @Test void anOpenerThatAnswersReplacesTheChooserAndSeesTheActiveEditorTabsKey() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:/srv/app.conf", "app.conf", "dustin@prod:/srv/app.conf", "prod"));
            drain(host);
            var asked = new ArrayList<OpenRequest>();
            Subscription registration = editor.opener(request -> { asked.add(request); return true; });
            host.queueFileChoice(List.of(new FileLocation.Local(Files.writeString(home.resolve("unused.txt"), "x\n"))));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(asked).singleElement().satisfies(request -> assertThat(request.documentKey()).contains("stub:/srv/app.conf"));
            assertThat(host.contentTabs()).as("the chooser never ran").hasSize(1);
            registration.close();
        }
    }

    /** Editor 0.16.0: another plugin's item ends every Editor tab's right-click menu and gets the tab's selection. */
    @Test void aContextItemEndsTheRightClickMenuAndGetsTheWindowTheDocumentAndTheSelection() throws Exception {
        try (var host = host()) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            Path file = Files.writeString(home.resolve("a.txt"), "hello world\n");
            editor.open(file);
            drain(host);
            var chosen = new ArrayList<ContextRequest>();
            // Registered after the tab opened: the menu builds its items as it opens.
            Subscription item = editor.contextItem("Encrypt…", chosen::add);
            var area = window(plugin).selected().orElseThrow().area();
            area.select(0, 5);
            var popup = area.getPopupMenu();
            opened(popup);
            var children = List.of(popup.getComponents());
            assertThat(texts(popup).getLast()).as("the last item").isEqualTo("Encrypt…");
            assertThat(children.get(children.size() - 2)).as("a rule before it").isInstanceOf(JPopupMenu.Separator.class);
            assertThat(children.indexOf(item(popup, "Move Pane to New Tab"))).as("after the pane entries").isLessThan(children.size() - 2);
            item(popup, "Encrypt…").doClick();
            assertThat(chosen).singleElement().satisfies(request -> {
                assertThat(request.window().id()).isEqualTo(terminal);
                assertThat(request.documentKey()).isEqualTo("local:" + file.toAbsolutePath().normalize());
                assertThat(request.selection()).isEqualTo("hello");
            });
            assertThat(area.getText()).as("the document is left alone").isEqualTo("hello world\n");
            area.select(0, 0);
            opened(popup);
            item(popup, "Encrypt…").doClick();
            assertThat(chosen.getLast().selection()).as("nothing selected").isEmpty();
            item.close();
            opened(popup);
            assertThat(texts(popup)).as("removed").doesNotContain("Encrypt…");
            assertThat(host.failures()).isEmpty();
        }
    }

    /** A context item needs a title, shows it as plain text, keeps its registration order and is contained when it throws. */
    @Test void contextItemsKeepTheirOrderShowPlainTextAndAFailingOneIsContained() throws Exception {
        try (var host = host()) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> editor.contextItem(" ", request -> { }))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A context item needs a title");
            var ran = new ArrayList<String>();
            editor.contextItem("<html><b>Bold", request -> { throw new IllegalStateException("broken"); });
            editor.contextItem("Second", request -> ran.add(request.selection()));
            editor.open(Files.writeString(home.resolve("b.txt"), "text\n"));
            drain(host);
            var popup = window(plugin).selected().orElseThrow().area().getPopupMenu();
            opened(popup);
            var texts = texts(popup);
            assertThat(texts.subList(texts.size() - 2, texts.size())).containsExactly("<html><b>Bold", "Second");
            JMenuItem bold = item(popup, "<html><b>Bold");
            assertThat(bold.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)).as("renders no HTML").isNull();
            org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(bold::doClick);
            item(popup, "Second").doClick();
            assertThat(ran).containsExactly("");
        }
    }

    /** The menu opening as Swing announces it, to the listener registered last first: the Editor builds its entries again. */
    private static void opened(JPopupMenu popup) {
        var listeners = popup.getPopupMenuListeners();
        for (int i = listeners.length - 1; i >= 0; i--) listeners[i].popupMenuWillBecomeVisible(new PopupMenuEvent(popup));
    }

    private static List<String> texts(JPopupMenu popup) {
        var texts = new ArrayList<String>();
        for (var child : popup.getComponents()) if (child instanceof JMenuItem entry) texts.add(entry.getText());
        return texts;
    }

    private static JMenuItem item(JPopupMenu popup, String text) {
        for (var child : popup.getComponents()) if (child instanceof JMenuItem entry && text.equals(entry.getText())) return entry;
        throw new AssertionError("no " + text);
    }

    @Test void registersTheVimWidgetsIcon() throws Exception {
        try (var host = host()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(context.kit().icons()).containsKey("dev.jasper.editor:vim");
        }
    }

    @Test void registersItsActionsAndPublishesTheEditorService() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.actions()).contains("dev.jasper.editor.open|Open in Editor…|true",
                "dev.jasper.editor.new_editor_tab|New Editor Tab|true", "dev.jasper.editor.show|Show Editor|true");
            assertThat(host.menu("FILE")).contains("item:dev.jasper.editor.open", "item:dev.jasper.editor.new_editor_tab");
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(Files.writeString(home.resolve("a.json"), "{}\n"));
            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
            while (host.runBackground() > 0) { }
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Tabs and panes spec 8 and 10: New Editor Tab opens an untitled file in a new tab, and the Editor's split item, whose
     * Split Right is New Editor Pane, one in a new pane beside the focused pane; the File menu offers both (Rulings 21).
     * None has a default key, nor have the actions Revert to Saved and Reload vimrc (Rulings 20).
     */
    @Test void newEditorTabOpensATabAndTheEditorsSplitItemAPaneBesideTheFocusedOne() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
            UUID pane = host.addTerminalPane(shell, dev.jasper.sdk.terminal.PaneInfo.unknown());
            assertThat(host.actions()).contains("dev.jasper.editor.new_editor_tab|New Editor Tab|true",
                "dev.jasper.editor.split_right_editor|Split Right with Editor|true",
                "dev.jasper.editor.split_down_editor|Split Down with Editor|true",
                "dev.jasper.editor.revert|Revert to Saved|true", "dev.jasper.editor.reload_vimrc|Reload vimrc|true");
            for (String id : List.of("dev.jasper.editor.new_editor_tab", "dev.jasper.editor.split_right_editor",
                    "dev.jasper.editor.split_down_editor", "dev.jasper.editor.revert", "dev.jasper.editor.reload_vimrc"))
                assertThat(host.actionSpec(id)).as(id).hasValueSatisfying(spec -> assertThat(spec.defaultBinding()).as("no default key").isEmpty());
            assertThat(host.actionSpec("dev.jasper.editor.split_right_editor")).hasValueSatisfying(spec ->
                assertThat(spec.keywords()).as("the palette finds New Editor Pane").contains("new", "editor", "pane"));
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of(), Set.of(), consumer::set);
            assertThat(consumer.get().windows().splitChoices()).containsExactly(dev.jasper.sdk.ui.SplitChoice.TERMINAL,
                new dev.jasper.sdk.ui.SplitChoice("Editor", "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor",
                    false),
                new dev.jasper.sdk.ui.SplitChoice("Open File", "dev.jasper.editor.split_right_open-file",
                    "dev.jasper.editor.split_down_open-file", true));
            assertThat(host.invoke("dev.jasper.editor.new_editor_tab", main, null)).isTrue();
            assertThat(host.windowTabs(main)).as("a new tab").hasSize(2);
            host.focusTerminalPane(pane);
            assertThat(host.invoke("dev.jasper.editor.split_right_editor", main, pane)).isTrue();
            assertThat(host.windowTabs(main).getFirst()).as("Editor Right: a pane beside the focused shell").hasSize(2).startsWith(shell);
            assertThat(host.invoke("dev.jasper.editor.split_down_editor", main, null)).isTrue();
            assertThat(host.windowTabs(main).getFirst()).as("Editor Down: beside the focused editor").hasSize(3);
            assertThat(host.contentTabs()).hasSize(3).allSatisfy(tab -> assertThat(host.contentPresentation(tab).title()).startsWith("Untitled"));
            assertThat(host.menu("FILE")).as("New Editor Tab, then New Editor Pane").containsSubsequence("item:dev.jasper.editor.open",
                "item:dev.jasper.editor.new_editor_tab", "item:dev.jasper.editor.split_right_editor", "item:dev.jasper.editor.new_window");
        }
    }

    /**
     * Pane flexibility spec 3 and workspace plan 9a Rulings 1 to 3: the Editor's second split item, Open File, lists Open
     * File Right… and Open File Down…, whose actions ask for a file and open it beside the focused pane; Open in Editor
     * Pane… does what Open File Right does and sits in the File menu after New Editor Pane. None has a default key.
     */
    @Test void openFileIsASplitItemAndOpenInEditorPaneSitsInTheFileMenu() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
            UUID pane = host.addTerminalPane(shell, dev.jasper.sdk.terminal.PaneInfo.unknown());
            assertThat(host.actions()).contains("dev.jasper.editor.split_right_open-file|Split Right with Open File…|true",
                "dev.jasper.editor.split_down_open-file|Split Down with Open File…|true",
                "dev.jasper.editor.open_in_pane|Open in Editor Pane…|true");
            for (String id : List.of("dev.jasper.editor.split_right_open-file", "dev.jasper.editor.split_down_open-file",
                    "dev.jasper.editor.open_in_pane"))
                assertThat(host.actionSpec(id)).as(id).hasValueSatisfying(spec -> assertThat(spec.defaultBinding()).as("no default key").isEmpty());
            assertThat(host.menu("FILE")).as("beside New Editor Pane").containsSubsequence("item:dev.jasper.editor.split_right_editor",
                "item:dev.jasper.editor.open_in_pane", "item:dev.jasper.editor.new_window");
            Path a = Files.writeString(home.resolve("a.txt"), "a"), b = Files.writeString(home.resolve("b.txt"), "b");
            host.focusTerminalPane(pane);
            host.queueFileChoice(List.of(new FileLocation.Local(a)));
            assertThat(host.invoke("dev.jasper.editor.split_down_open-file", main, pane)).isTrue();
            drain(host);
            assertThat(host.windowTabs(main)).as("a pane beside the shell").hasSize(1);
            assertThat(host.windowTabs(main).getFirst()).hasSize(2).startsWith(shell);
            host.queueFileChoice(List.of(new FileLocation.Local(b)));
            assertThat(host.invoke("dev.jasper.editor.open_in_pane", main, null)).isTrue();
            drain(host);
            assertThat(host.windowTabs(main).getFirst()).as("Open in Editor Pane… opens one too").hasSize(3);
            assertThat(host.fileChoices()).extracting(dev.jasper.sdk.files.FileChoice::title)
                .containsExactly("Open in Editor Pane", "Open in Editor Pane");
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 8b Task 6 review: the palette's Revert to Saved acts on the window it runs in. A dirty file in another
     * window is neither asked about nor reverted; run in that window, it asks over that file and reverts it.
     */
    @Test void revertToSavedActsOnTheFileOfTheWindowItRunsIn() throws Exception {
        var asked = new ArrayList<WindowOwner>();
        var plugin = new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
            owner -> question -> { asked.add(owner); return question.choices().getFirst(); },
            owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false));
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            UUID main = host.addTerminalWindow(), other = host.addTerminalWindow();
            host.addTerminalTab(main, "zsh");
            host.addTerminalTab(other, "zsh");
            var editor = consumer.get().services().require(Editor.class);
            var terminals = consumer.get().terminals();
            editor.open(Files.writeString(home.resolve("b.txt"), "b\n"), terminals.window(main).orElseThrow());
            editor.open(Files.writeString(home.resolve("a.txt"), "a\n"), terminals.window(other).orElseThrow());
            drain(host);
            var windowField = EditorPlugin.class.getDeclaredField("window"); windowField.setAccessible(true);
            RSyntaxTextArea a = ((EditorWindow) windowField.get(plugin)).selected().orElseThrow().area();
            a.append("edit");
            assertThat(host.invoke("dev.jasper.editor.revert", main, null)).isTrue();
            drain(host);
            assertThat(asked).as("no prompt for the other window's file").isEmpty();
            assertThat(a.getText()).as("its edits kept").contains("edit");
            assertThat(host.invoke("dev.jasper.editor.revert", other, null)).isTrue();
            drain(host);
            assertThat(asked).as("asked over its own window's file").singleElement()
                .satisfies(owner -> assertThat(((dev.jasper.sdk.ui.ContentTab) owner).window().id()).isEqualTo(other));
            assertThat(a.getText()).as("reverted").doesNotContain("edit");
        }
    }

    /**
     * Workspace tabs spec 13: a document's tab gives its file's key and comes back through the Editor's restorer; an
     * untitled one is not saved, and a key nothing can open now restores nothing.
     */
    @Test void aDocumentComesBackFromItsKeyAndAnUntitledOneIsNotSaved() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            Path notes = Files.writeString(home.resolve("notes.md"), "# notes\n");
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(notes);
            drain(host);
            var tab = host.contentTab("dev.jasper.editor", "notes.md").orElseThrow();
            var state = dev.jasper.sdk.RestoreState.of("document", "key", "local:" + notes.toAbsolutePath().normalize());
            assertThat(host.restoreState(tab)).contains(state);
            tab.close();
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).isPresent();
            drain(host);
            var back = host.contentTab("dev.jasper.editor", "notes.md").orElseThrow();
            assertThat(back.window().id()).isEqualTo(main);
            assertThat(host.restoreState(back)).contains(state);
            assertThat(host.invoke("dev.jasper.editor.new_editor_tab", main, null)).isTrue();
            var untitled = host.contentTabs().stream().filter(open -> host.contentPresentation(open).title().startsWith("Untitled"))
                .findFirst().orElseThrow();
            assertThat(host.restoreState(untitled)).as("an unsaved untitled buffer is not kept").isEmpty();
            assertThat(host.restoreTab("dev.jasper.editor", main, "gone.txt",
                dev.jasper.sdk.RestoreState.of("document", "key", "remote:gone"))).isEmpty();
            // A prefix a running plugin handles, whose reopener cannot open the key now: the file, not the plugin, is gone.
            consumer.get().services().require(Editor.class).reopener("test:", key -> Optional.empty());
            assertThat(host.restoreTab("dev.jasper.editor", main, "kept.txt",
                dev.jasper.sdk.RestoreState.of("document", "key", "test:kept"))).isEmpty();
            assertThat(host.declinedRestores()).containsExactly(
                "dev.jasper.editor document: no running plugin opens remote: documents",
                "dev.jasper.editor document: its file can no longer be opened");
            assertThat(host.failures()).as("expected outcomes, no failure").isEmpty();
        }
    }

    /** Workspace plan 6b's review: a document that comes back is not an open, so Recent Files does not record it. */
    @Test void aRestoredDocumentIsNotRecordedInRecentFiles() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            Path notes = Files.writeString(home.resolve("notes.md"), "# notes\n");
            var state = dev.jasper.sdk.RestoreState.of("document", "key", "local:" + notes.toAbsolutePath().normalize());
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).isPresent();
            drain(host);
            assertThat(host.contentTab("dev.jasper.editor", "notes.md")).isPresent();
            assertThat(host.searchScope(RECENT, "", main, null)).as("restored, not opened").isEmpty();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(notes);
            drain(host);
            assertThat(titles(host.searchScope(RECENT, "", main, null))).as("opened as usual").containsExactly("notes.md");
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 6b's review: a local file deleted since its tab was saved comes back as a failed read with Retry, and
     * keeps its restore state, so it comes back again while it stays open.
     */
    @Test void aDeletedLocalFileComesBackAsAFailedReadWithRetry() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            Path gone = home.resolve("gone.md");
            var state = dev.jasper.sdk.RestoreState.of("document", "key", "local:" + gone.toAbsolutePath().normalize());
            assertThat(host.restoreTab("dev.jasper.editor", main, "gone.md", state)).isPresent();
            drain(host);
            var tab = host.contentTab("dev.jasper.editor", "gone.md").orElseThrow();
            var retry = dev.jasper.editor.testsupport.Kits.button(host.contentView("dev.jasper.editor", "gone.md").orElseThrow(), "document.retry");
            assertThat(retry.state().visible()).as("a failed read, with Retry").isTrue();
            assertThat(host.restoreState(tab)).contains(state);
            assertThat(host.declinedRestores()).isEmpty();
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes", dev.jasper.sdk.RestoreState.of("document", "key", "notes")))
                .as("a key with no prefix").isEmpty();
            assertThat(host.declinedRestores()).containsExactly("dev.jasper.editor document: no running plugin opens this document");
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace tabs spec 13: a restored document on another host waits until its tab is first active, then reads asking
     * nothing; a document already open comes back as another view of it, which reads nothing (pane flexibility spec 6).
     */
    @Test void aRestoredRemoteDocumentReadsQuietlyOnlyOnceItsTabIsActive() throws Exception {
        var plugin = plugin();
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID main = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            var quiet = new java.util.concurrent.atomic.AtomicInteger();
            var loud = new java.util.concurrent.atomic.AtomicInteger();
            dev.jasper.editor.api.EditorDocument remote = new dev.jasper.editor.api.EditorDocument() {
                @Override public String key() { return "test:/srv/notes.md"; }
                @Override public String name() { return "notes.md"; }
                @Override public String location() { return "box:/srv/notes.md"; }
                @Override public Optional<String> host() { return Optional.of("box"); }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> read(long limitBytes) {
                    loud.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.completedFuture(new dev.jasper.editor.api.Content(new byte[0], new dev.jasper.editor.api.Revision(1, 0)));
                }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> readQuietly(long limitBytes) {
                    quiet.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("Not signed in to box; Retry signs in"));
                }
                @Override public java.util.concurrent.CompletableFuture<Optional<dev.jasper.editor.api.Revision>> revision() {
                    return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
                }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Revision> write(byte[] content) {
                    return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("read only"));
                }
            };
            consumer.get().services().require(Editor.class).reopener("test:", key -> Optional.of(owner -> remote));
            var state = dev.jasper.sdk.RestoreState.of("document", "key", remote.key());
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).isPresent();
            drain(host);
            assertThat(quiet.get() + loud.get()).as("not read while its tab is not active").isZero();
            var tab = host.contentTab("dev.jasper.editor", "notes.md").orElseThrow();
            tab.select();
            drain(host);
            assertThat(quiet).as("read once it is active, asking nothing").hasValue(1);
            assertThat(loud).hasValue(0);
            // Showing the tab again does not read it again: it waits for Retry now.
            consumer.get().services().require(Editor.class).open(Files.writeString(home.resolve("other.md"), "x\n"));
            drain(host);
            tab.select();
            drain(host);
            assertThat(quiet.get() + loud.get()).as("selected again, not read again").isEqualTo(1);
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).as("already open: another view of it").isPresent();
            drain(host);
            assertThat(host.declinedRestores()).isEmpty();
            assertThat(host.contentTabs()).as("two views of notes.md, and other.md").hasSize(3);
            host.contentTabs().getLast().select();
            drain(host);
            assertThat(quiet.get() + loud.get()).as("the new view shares the file's one read").isEqualTo(1);
            var view = window(plugin).selected().orElseThrow();
            assertThat(view.session().name()).isEqualTo("notes.md");
            assertThat(dev.jasper.editor.testsupport.Kits.button(view.view(), "document.retry").state().visible())
                .as("the new view shows the failed read, with Retry").isTrue();
        }
    }

    /**
     * Pane flexibility spec 6: every view of a file comes back, each saved with the file's key; a remote file's views
     * wait, and the first of them shown reads the file once, quietly, for all of them; a local file's views share one
     * read too.
     */
    @Test void everyViewOfAFileComesBackAndItsFileIsReadOnceForAllOfThem() throws Exception {
        var plugin = plugin();
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID main = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            var reads = new java.util.concurrent.atomic.AtomicInteger();
            var quiet = new java.util.concurrent.atomic.AtomicInteger();
            dev.jasper.editor.api.EditorDocument remote = new dev.jasper.editor.api.EditorDocument() {
                @Override public String key() { return "test:/srv/two.md"; }
                @Override public String name() { return "two.md"; }
                @Override public String location() { return "box:/srv/two.md"; }
                @Override public Optional<String> host() { return Optional.of("box"); }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> read(long limitBytes) {
                    reads.incrementAndGet();
                    return content();
                }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> readQuietly(long limitBytes) {
                    quiet.incrementAndGet();
                    return content();
                }
                private java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> content() {
                    byte[] text = "# two\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    return java.util.concurrent.CompletableFuture.completedFuture(new dev.jasper.editor.api.Content(text, new dev.jasper.editor.api.Revision(1, text.length)));
                }
                @Override public java.util.concurrent.CompletableFuture<Optional<dev.jasper.editor.api.Revision>> revision() {
                    return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
                }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Revision> write(byte[] content) {
                    return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("read only"));
                }
            };
            consumer.get().services().require(Editor.class).reopener("test:", key -> Optional.of(owner -> remote));
            var state = dev.jasper.sdk.RestoreState.of("document", "key", remote.key());
            assertThat(host.restoreTab("dev.jasper.editor", main, "two.md", state)).isPresent();
            assertThat(host.restoreTab("dev.jasper.editor", main, "two.md", state)).as("another view of it").isPresent();
            drain(host);
            assertThat(reads.get() + quiet.get()).as("both views wait").isZero();
            var views = List.copyOf(host.contentTabs());
            assertThat(views).hasSize(2).allSatisfy(tab -> assertThat(host.restoreState(tab)).contains(state));
            views.getLast().select();
            drain(host);
            assertThat(quiet).as("one read for both views, asking nothing").hasValue(1);
            assertThat(reads).hasValue(0);
            views.getFirst().select();
            drain(host);
            assertThat(reads.get() + quiet.get()).isEqualTo(1);
            assertThat(window(plugin).selected().orElseThrow().area().getText()).as("the other view shows the text read").isEqualTo("# two\n");
            Path notes = Files.writeString(home.resolve("notes.md"), "# notes\n");
            var local = dev.jasper.sdk.RestoreState.of("document", "key", "local:" + notes.toAbsolutePath().normalize());
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", local)).isPresent();
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", local)).isPresent();
            drain(host);
            assertThat(window(plugin).sessions()).as("two files, four views").hasSize(2);
            assertThat(host.contentTabs()).hasSize(4);
            assertThat(host.declinedRestores()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Pane flexibility spec 6: Split File Right and Split File Down, with no default key, show the window's file in
     * another view, in a new pane beside its own; the Tab menu's item commands list them too.
     */
    @Test void splitFileRightAndDownShowTheWindowsFileInAnotherViewBesideIt() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            assertThat(host.actions()).contains("dev.jasper.editor.split_file_right|Split File Right|true",
                "dev.jasper.editor.split_file_down|Split File Down|true");
            for (String id : List.of("dev.jasper.editor.split_file_right", "dev.jasper.editor.split_file_down"))
                assertThat(host.actionSpec(id)).as(id).hasValueSatisfying(spec -> {
                    assertThat(spec.defaultBinding()).as("no default key").isEmpty();
                    assertThat(spec.keywords()).contains("split", "file");
                });
            assertThat(host.invoke("dev.jasper.editor.split_file_right", main, null)).isTrue();
            assertThat(host.contentTabs()).as("no file, nothing to split").isEmpty();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(Files.writeString(home.resolve("view.txt"), "x\n"));
            drain(host);
            assertThat(host.invoke("dev.jasper.editor.split_file_right", main, null)).isTrue();
            assertThat(host.invoke("dev.jasper.editor.split_file_down", main, null)).isTrue();
            assertThat(host.windowTabs(main)).as("one tab of three views of the file").singleElement()
                .satisfies(panes -> assertThat(panes).hasSize(3));
            assertThat(host.contentTabs()).hasSize(3)
                .allSatisfy(tab -> assertThat(host.contentPresentation(tab).title()).isEqualTo("view.txt"));
            assertThat(host.contentCommands(host.contentTabs().getFirst())).extracting(dev.jasper.sdk.ui.ContentCommand::title)
                .as("the Tab menu's item commands").contains("Split File Right", "Split File Down");
        }
    }

    /** Workspace tabs spec 6.5: New Editor Window, with no default key, opens an untitled document in a new child window. */
    @Test void newEditorWindowOpensAnUntitledDocumentInANewWindow() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            assertThat(host.actionSpec("dev.jasper.editor.new_window")).hasValueSatisfying(spec -> {
                assertThat(spec.title()).isEqualTo("New Editor Window");
                assertThat(spec.defaultBinding()).as("no default key").isEmpty();
            });
            assertThat(host.menu("FILE")).containsSubsequence("item:dev.jasper.editor.new_editor_tab", "item:dev.jasper.editor.new_window");
            assertThat(host.invoke("dev.jasper.editor.new_window", main, null)).isTrue();
            var tab = host.contentTabs().getFirst();
            assertThat(host.contentTabs()).hasSize(1);
            assertThat(tab.window().id()).isNotEqualTo(main);
            assertThat(tab.window().tabs()).hasSize(1);
            assertThat(host.contentPresentation(tab).title()).startsWith("Untitled");
        }
    }

    /** The toolbar shows New Editor Window as a button with the window-plus glyph; it runs the action the File menu runs. */
    @Test void newEditorWindowIsAToolbarButtonThatOpensTheSameWindowAsTheMenu() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            assertThat(host.toolbar()).containsExactly("button:dev.jasper.editor.new_window");
            assertThat(host.actionSpec("dev.jasper.editor.new_window")).hasValueSatisfying(spec -> {
                assertThat(spec.title()).as("the button's tooltip").isEqualTo("New Editor Window");
                assertThat(spec.icon()).as("the button's glyph").contains("file");
            });
            assertThat(host.invoke("dev.jasper.editor.new_window", main, null)).isTrue();
            var tab = host.contentTabs().getFirst();
            assertThat(host.contentTabs()).hasSize(1);
            assertThat(tab.window().id()).as("a new window, as from the File menu").isNotEqualTo(main);
            assertThat(tab.window().tabs()).hasSize(1);
            assertThat(host.contentPresentation(tab).title()).startsWith("Untitled");
        }
    }

    @Test void theOpenActionUsesAJasperWindowsChooserAndClosingItsTabLetsAFreshOneOpen() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            host.queueFileChoice(List.of(new FileLocation.Local(Files.writeString(home.resolve("b.yaml"), "a: 1\n"))));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
            while (host.runBackground() > 0) { }
            assertThat(host.contentOwner(host.contentTabs().getFirst())).isInstanceOf(dev.jasper.sdk.terminal.WindowHandle.class);
            assertThat(host.contentTabs().getFirst().requestClose().join()).isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(host.invoke("dev.jasper.editor.new_editor_tab", terminal, null)).isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
        }
    }

    @Test void sameDarkWorkspaceChangeRecoloursOpenTabsWithoutLosingEdits() throws Exception {
        try (var host = host()) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(Files.writeString(home.resolve("a.json"), "{\"a\": true}\n"));
            while (host.runBackground() > 0) { }
            var windowField = EditorPlugin.class.getDeclaredField("window"); windowField.setAccessible(true);
            RSyntaxTextArea area = ((EditorWindow) windowField.get(plugin)).selected().orElseThrow().area();
            area.append("extra");
            area.select(1, 4);
            var document = area.getDocument();
            var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
            for (var role : SyntaxRole.values()) styles.put(role, new SyntaxStyle(new Color(0x334400 + role.ordinal()), false, false));
            var appearance = consumer.get().appearance();
            var editor = new EditorPalette(new Color(0x202020), Color.WHITE, Color.YELLOW,
                Color.BLUE, Color.GRAY, Color.BLACK, Color.LIGHT_GRAY);
            host.setWorkspace(appearance.workspaceVariant(), appearance.workspacePalette(), editor, new SyntaxPalette(styles));
            host.flush();
            assertThat(area.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground)
                .isEqualTo(styles.get(SyntaxRole.KEYWORD).color());
            assertThat(area.getBackground()).isEqualTo(editor.background());
            assertThat(area.getDocument()).isSameAs(document);
            assertThat(area.getText()).endsWith("extra");
            assertThat(area.getSelectionStart()).isEqualTo(1);
            assertThat(area.getSelectionEnd()).isEqualTo(4);
            assertThat(area.canUndo()).isTrue();
        }
    }

    /** The Color scheme setting's palettes: every colour distinct from the fake workspace's. */
    private static dev.jasper.sdk.ui.EditorScheme ember() {
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        for (var role : SyntaxRole.values()) styles.put(role, new SyntaxStyle(new Color(0x551100 + role.ordinal()), false, false));
        return new dev.jasper.sdk.ui.EditorScheme("ember/light", "Ember", dev.jasper.sdk.Variant.LIGHT, new EditorPalette(new Color(0xfff4e8),
            new Color(0x301000), new Color(0x902000), new Color(0xf0c090), new Color(0xfae6d0), new Color(0xf6ead8), new Color(0x806040)),
            new SyntaxPalette(styles));
    }

    /** Opens a JSON file in the Editor and returns its text area, the consumer's context in {@code consumer}. */
    private RSyntaxTextArea openArea(FakePluginHost host, EditorPlugin plugin, AtomicReference<PluginContext> consumer) throws Exception {
        host.start(INFO, Set.of(), Set.of(), plugin);
        host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
        consumer.get().services().require(Editor.class).open(Files.writeString(home.resolve("a.json"), "{\"a\": true}\n"));
        while (host.runBackground() > 0) { }
        var windowField = EditorPlugin.class.getDeclaredField("window"); windowField.setAccessible(true);
        return ((EditorWindow) windowField.get(plugin)).selected().orElseThrow().area();
    }

    @Test void fixedSchemeIgnoresTheWorkspace() throws Exception {
        try (var host = host()) {
            var scheme = ember();
            host.setEditorSchemes(List.of(scheme));
            host.flush();
            host.setSetting("dev.jasper.editor", "color_scheme", "ember/light");
            var consumer = new AtomicReference<PluginContext>();
            RSyntaxTextArea area = openArea(host, plugin(), consumer);
            assertThat(area.getBackground()).isEqualTo(scheme.editor().background());
            assertThat(area.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground).isEqualTo(scheme.syntax().style(SyntaxRole.KEYWORD).color());
            var appearance = consumer.get().appearance();
            var other = new EditorPalette(new Color(0x101010), Color.WHITE, Color.YELLOW, Color.BLUE, Color.GRAY, Color.BLACK, Color.LIGHT_GRAY);
            host.setWorkspace(dev.jasper.sdk.Variant.DARK, appearance.workspacePalette(), other, appearance.syntaxPalette());
            host.flush();
            assertThat(area.getBackground()).as("a Dark workspace change").isEqualTo(scheme.editor().background());
        }
    }

    @Test void missingSchemeFallsBackToWorkspace() throws Exception {
        try (var host = host()) {
            var scheme = ember();
            host.setEditorSchemes(List.of(scheme));
            host.flush();
            host.setSetting("dev.jasper.editor", "color_scheme", "ember/light");
            var consumer = new AtomicReference<PluginContext>();
            RSyntaxTextArea area = openArea(host, plugin(), consumer);
            host.setEditorSchemes(List.of());
            host.flush();
            assertThat(area.getBackground()).as("the theme was removed").isEqualTo(consumer.get().appearance().editorPalette().background());
        }
    }

    @Test void settingChangeReappliesScheme() throws Exception {
        try (var host = host()) {
            var scheme = ember();
            host.setEditorSchemes(List.of(scheme));
            host.flush();
            var consumer = new AtomicReference<PluginContext>();
            RSyntaxTextArea area = openArea(host, plugin(), consumer);
            var workspace = consumer.get().appearance().editorPalette().background();
            assertThat(area.getBackground()).isEqualTo(workspace);
            host.setSetting("dev.jasper.editor", "color_scheme", "ember/light");
            host.flush();
            assertThat(area.getBackground()).isEqualTo(scheme.editor().background());
            host.setSetting("dev.jasper.editor", "color_scheme", "workspace");
            host.flush();
            assertThat(area.getBackground()).isEqualTo(workspace);
        }
    }

    @Test void fixedSchemeAppliesToGlass() throws Exception {
        try (var host = host()) {
            var scheme = ember();
            host.setEditorSchemes(List.of(scheme));
            host.flush();
            host.setSetting("dev.jasper.editor", "color_scheme", "ember/light");
            RSyntaxTextArea area = openArea(host, plugin(), new AtomicReference<>());
            host.setWorkspaceOpacity(0.5);
            host.flush();
            var line = area.getCurrentLineHighlightColor();
            assertThat(line.getRGB() & 0xffffff).isEqualTo(scheme.editor().currentLine().getRGB() & 0xffffff);
            assertThat(line.getAlpha()).isEqualTo(128);
        }
    }

    @Test void savedTerminalTypographyFollowsAcrossTabsWithoutSavingIndependentSettingsAndStopsOnShutdown() throws Exception {
        try (var host = host()) {
            var reads = new java.util.concurrent.atomic.AtomicInteger();
            var plugin = new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
                owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false),
                () -> { reads.incrementAndGet(); return settings.current(); }, settings::saveVim, false));
            host.setSetting("dev.jasper.editor", "font_size", 13.5);
            host.start(INFO, Set.of(), Set.of(), plugin);
            host.invoke("dev.jasper.editor.new_editor_tab", host.addTerminalWindow(), null);
            var field = EditorPlugin.class.getDeclaredField("window"); field.setAccessible(true);
            var window = (EditorWindow) field.get(plugin);
            var first = window.selected().orElseThrow().area();
            assertThat(first.getFont().getSize2D()).isEqualTo(13.5f);
            var terminal = new dev.jasper.sdk.ui.TerminalTypography("DialogInput", 19.5, List.of("Dialog"), true, 1.6);
            host.setTerminalTypography(terminal);
            host.flush();
            assertThat(first.getFont().getSize2D()).isEqualTo(13.5f);
            host.setSetting("dev.jasper.editor", "follow_terminal_font", true);
            host.invoke("dev.jasper.editor.new_editor_tab", host.addTerminalWindow(), null);
            var second = window.selected().orElseThrow().area();
            first.append("keep undo");
            var document = first.getDocument();
            assertThat(first.getFont().getSize2D()).isEqualTo(19.5f);
            assertThat(second.getFont().getSize2D()).isEqualTo(19.5f);
            var saved = host.savedSettings("dev.jasper.editor");
            host.setTerminalTypography(new dev.jasper.sdk.ui.TerminalTypography("Dialog", 20.5, List.of(), false, 1.2));
            host.flush();
            assertThat(first.getFont().getSize2D()).isEqualTo(20.5f);
            assertThat(second.getFont().getSize2D()).isEqualTo(20.5f);
            assertThat(first.getDocument()).isSameAs(document);
            assertThat(first.canUndo()).isTrue();
            assertThat(host.savedSettings("dev.jasper.editor")).isEqualTo(saved);
            host.setSetting("dev.jasper.editor", "follow_terminal_font", false);
            assertThat(first.getFont().getSize2D()).isEqualTo(13.5f);
            host.setSetting("dev.jasper.editor", "follow_terminal_font", true);
            plugin.stop();
            int before = reads.get();
            host.setTerminalTypography(terminal);
            host.flush();
            assertThat(reads.get()).as("stop explicitly closes the font subscription").isEqualTo(before);
        }
    }

    @Test void badSettingsAreReportedAtStartAndOnEveryChange() {
        try (var host = host()) {
            host.setSetting("dev.jasper.editor", "font_size", 99L);
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.settingsReports("dev.jasper.editor")).containsExactly("font_size: Use a number from 6\u201372.");
            host.setSetting("dev.jasper.editor", "tab_size", 0L);
            assertThat(host.settingsReports("dev.jasper.editor")).contains("tab_size: Use a whole number from 1\u201316.");
        }
    }

    @Test void openInEditorOffersRegisteredSourcesAndOpensTheirFilesThroughTheResolver() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            Subscription registration = editor.locationDocuments("test.consumer.hosts", new LocationResolver() {
                @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) {
                    return new StubDocument("stub:" + location.path(), "app.conf", "me@box:" + location.path(), "box");
                }
                @Override public Optional<FileLocation.Sourced> location(String key) {
                    return key.startsWith("stub:") ? Optional.of(new FileLocation.Sourced("test.consumer.hosts", "box", key.substring(5))) : Optional.empty();
                }
            });
            host.queueFileChoice(List.of(new FileLocation.Sourced("test.consumer.hosts", "box", "/srv/app.conf")));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(host.fileChoices().getLast().sources()).containsExactly("test.consumer.hosts");
            assertThat(host.fileChoices().getLast().initial()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);
            host.queueFileChoice(List.of());
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(host.fileChoices().getLast().initial()).as("the active remote document's folder")
                .contains(new FileLocation.Sourced("test.consumer.hosts", "box", "/srv"));
            registration.close();
            host.queueFileChoice(List.of());
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            assertThat(host.fileChoices().getLast().sources()).isEmpty();
        }
    }

    private final Set<Path> programs = new HashSet<>();
    private final List<List<String>> launched = new ArrayList<>();
    /** The notification group each launcher was given for its failures. */
    private final List<String> launcherGroups = new ArrayList<>();

    /** The Editor over a machine whose programs are {@link #programs}; app editors and detached commands go to {@link #launched}. */
    private EditorPlugin detecting() {
        return new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
                owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false),
            () -> new Detection.Probe("Mac OS X", List.of(home.resolve("bin")), home, Map.of(), programs::contains),
            (log, notices) -> {
                launcherGroups.add(notices.id());
                return (command, program) -> { launched.add(command); return true; };
            });
    }

    /** A program detection finds in {@code home/bin}. */
    private Path install(String name) throws Exception {
        Path program = Files.createDirectories(home.resolve("bin")).resolve(name).toAbsolutePath().normalize();
        programs.add(program);
        return program;
    }

    @Test void theEditorIsJaspersFileEditorAndNamesItselfInEditor() throws Exception {
        try (var host = host()) {
            Path client = home.resolve("Jasper Beta.app").resolve("bin").resolve("jasper");
            host.setCommandClient(client);
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            assertThat(host.editorPlugin()).contains("dev.jasper.editor");
            Path config = Files.writeString(home.resolve("config.toml"), "[window]\n");
            assertThat(host.edit(config, OptionalInt.of(1), Optional.empty())).isTrue();
            drain(host);
            assertThat(host.contentTabs()).hasSize(1);
            String value = EditorCommands.quote(client.toString(), System.getProperty("os.name", "")) + " editor --wait";
            assertThat(host.shellEnvironment(Map.of())).as("no VISUAL, so a startup file's VISUAL or EDITOR still wins")
                .isEqualTo(Map.of("EDITOR", value));
            assertThat(host.failures()).isEmpty();
            host.stopAll();
            assertThat(host.editorPlugin()).as("stopped").isEmpty();
            assertThat(host.shellEnvironment(Map.of())).isEmpty();
        }
    }

    @Test void anotherPluginsOpenInEditorOpensTheFileHere() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), detecting());
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of(), Set.of(), consumer::set);
            consumer.get().platform().openInEditor(Files.writeString(home.resolve("snippets.toml"), "x = 1\n"));
            drain(host);
            assertThat(host.contentTabs()).hasSize(1);
            assertThat(host.openedInEditor()).as("the desktop was not needed").isEmpty();
        }
    }

    /**
     * Review Focus 2 and Controller Ruling 11, end to end: Jasper started from a terminal with EDITOR=vim keeps it until
     * keep_environment_editor is off; then EDITOR is replaced, and VISUAL only when Jasper was started with one.
     */
    @Test void anEditorInJaspersOwnEnvironmentStaysUntilTheSettingIsOff() {
        try (var host = host()) {
            host.setCommandClient(home.resolve("jasper"));
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            assertThat(host.shellEnvironment(Map.of("EDITOR", "vim"))).as("git keeps vim").isEmpty();
            assertThat(host.shellEnvironment(Map.of("VISUAL", "nvim"))).isEmpty();
            host.setSetting("dev.jasper.editor", "keep_environment_editor", false);
            String jasper = EditorCommands.quote(home.resolve("jasper").toString(), System.getProperty("os.name", "")) + " editor --wait";
            assertThat(host.shellEnvironment(Map.of("EDITOR", "vim"))).as("only EDITOR").isEqualTo(Map.of("EDITOR", jasper));
            assertThat(host.shellEnvironment(Map.of("VISUAL", "nano"))).as("both replaced")
                .isEqualTo(Map.of("EDITOR", jasper, "VISUAL", jasper));
        }
    }

    @Test void anInstalledAppEditorStartsAtTheLineAndATerminalEditorGetsATab() throws Exception {
        try (var host = host()) {
            Path code = install("code"), nvim = install("nvim");
            host.setSetting("dev.jasper.editor", "default_editor", "code");
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            Path file = Files.writeString(home.resolve("notes.txt"), "a\nb\n");
            String os = System.getProperty("os.name", "");
            assertThat(host.shellEnvironment(Map.of())).containsEntry("EDITOR", EditorCommands.quote(code.toString(), os) + " --wait");
            assertThat(host.edit(file, OptionalInt.of(2), Optional.empty())).isTrue();
            assertThat(launched).containsExactly(List.of(code.toString(), "-g", file + ":2"));
            assertThat(launcherGroups).as("a failed start is posted in the Editor's own group").containsExactly("dev.jasper.editor");
            host.setSetting("dev.jasper.editor", "default_editor", "nvim");
            UUID window = host.addTerminalWindow();
            assertThat(host.edit(file, OptionalInt.of(2), Optional.of(window))).isTrue();
            assertThat(host.openRequests()).contains("tab|" + window + "|" + file.getParent() + "|run:" + nvim + " +2 " + file);
            assertThat(host.contentTabs()).as("the Jasper Editor opened nothing").isEmpty();
        }
    }

    /** Plugins start and the first window opens in one UI turn, so the first shell asks before any background task has run. */
    @Test void theFirstShellOfALaunchGetsTheSavedInstalledEditorBeforeDetectionHasRun() throws Exception {
        try (var host = host()) {
            Path nvim = install("nvim");
            install("vim");
            host.setSetting("dev.jasper.editor", "default_editor", "nvim");
            host.start(INFO, Set.of(), Set.of(), detecting());
            String os = System.getProperty("os.name", "");
            assertThat(host.shellEnvironment(Map.of())).as("no background task has run yet")
                .isEqualTo(Map.of("EDITOR", EditorCommands.quote(nvim.toString(), os)));
            drain(host);
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Review Focus 5, end to end: the saved editor was uninstalled. */
    @Test void anUninstalledSavedEditorGivesShellsNothingAndFilesToTheDesktop() throws Exception {
        try (var host = host()) {
            host.setSetting("dev.jasper.editor", "default_editor", "zed");
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            assertThat(host.settingsReports("dev.jasper.editor")).as("a known id stays a valid value").isEmpty();
            assertThat(host.shellEnvironment(Map.of())).isEmpty();
            assertThat(host.edit(Files.writeString(home.resolve("a.txt"), "a\n"), OptionalInt.empty(), Optional.empty())).isFalse();
            assertThat(launched).isEmpty();
            assertThat(host.contentTabs()).isEmpty();
        }
    }

    /** Final review M4: a development Editor after another plugin's editor still starts, and only that one is not its. */
    @Test void theEditorStillStartsWhenAnotherPluginProvidesTheEditor() throws Exception {
        try (var host = host()) {
            List<Path> askedOther = new ArrayList<>();
            host.start(new PluginInfo("dev.example.other", "Other", "1.0.0", Set.of("editor.provide")), Set.of(), Set.of(),
                context -> context.platform().editor((file, line, window) -> askedOther.add(file)));
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            assertThat(host.failures()).as("the Editor started").isEmpty();
            assertThat(host.editorPlugin()).contains("dev.example.other");
            assertThat(host.settingsPanel("dev.jasper.editor")).as("its page").isPresent();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(Files.writeString(home.resolve("alpha.txt"), "a\n"));
            drain(host);
            assertThat(host.contentTabs()).as("its tabs").hasSize(1);
            Files.writeString(home.resolve("beta.txt"), "b\n");
            var call = host.call("editor", List.of("beta.txt"), home);
            drain(host);
            assertThat(call.exit()).as("jasper editor").isCompletedWithValue(0);
            assertThat(host.contentTabs()).hasSize(2);
            Path notes = Files.writeString(home.resolve("notes.txt"), "n\n");
            assertThat(host.edit(notes, OptionalInt.empty(), Optional.empty())).isTrue();
            assertThat(askedOther).as("Jasper's own opens go to the other plugin's editor").containsExactly(notes);
        }
    }

    @Test void theSettingsPageIsTheEditorsOwn() {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.settingsPanel("dev.jasper.editor")).isPresent();
            assertThat(host.settingsPanelView("dev.jasper.editor").orElseThrow().spec().root().id()).isEqualTo("editor.settings");
            drain(host);
            assertThat(host.failures()).isEmpty();
        }
    }
}

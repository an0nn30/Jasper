package dev.jasper.editor;

import dev.jasper.editor.api.LocationResolver;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.testsupport.StubDocument;
import dev.jasper.editor.ui.EditorEnvironment;
import dev.jasper.editor.ui.EditorWindow;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class EditorPluginTest {
    @TempDir Path home;
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of("palette.contribute"));

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

    @Test void recentFilesListOpenedFilesReopenThemAndForgetThem() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            assertThat(host.scopes()).contains(RECENT + "|Recent files|open,remove");
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

    @Test void registersItsActionsAndPublishesTheEditorService() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.actions()).contains("dev.jasper.editor.open|Open in Editor…|true",
                "dev.jasper.editor.new|New Editor File|true", "dev.jasper.editor.show|Show Editor|true");
            assertThat(host.menu("FILE")).contains("item:dev.jasper.editor.open", "item:dev.jasper.editor.new");
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

    @Test void theOpenActionUsesATerminalWindowsChooserAndClosingTheWindowLetsAFreshOneOpen() throws Exception {
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
            assertThat(host.invoke("dev.jasper.editor.new", terminal, null)).isTrue();
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

    @Test void badSettingsAreReportedAtStartAndOnEveryChange() {
        try (var host = host()) {
            host.setSetting("dev.jasper.editor", "font_size", 99L);
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.settingsReports("dev.jasper.editor")).containsExactly("font_size: Use a whole number from 8\u201348.");
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
}

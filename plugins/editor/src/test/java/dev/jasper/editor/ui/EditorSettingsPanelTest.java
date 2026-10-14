package dev.jasper.editor.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.editor.external.DefaultEditor;
import dev.jasper.editor.external.Detection;
import dev.jasper.editor.testsupport.EdtTests;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTests.class)
class EditorSettingsPanelTest {
    private static final String ID = "dev.jasper.editor";
    private static final PluginInfo INFO = new PluginInfo(ID, "Editor", "0.9.0", Set.of());

    @TempDir Path home;
    private final Set<Path> programs = new HashSet<>();
    private FakePluginHost host;
    private DefaultEditor defaults;
    private Path bin;

    @BeforeEach void open() throws Exception {
        host = new FakePluginHost(home.resolve("plugins"));
        bin = Files.createDirectories(home.resolve("bin"));
    }

    @AfterEach void close() { host.close(); }

    /** Starts a plugin that gives the Editor's page over its real declarations, then opens the page as Settings would. */
    private FakeKitView page(Map<String, String> environment) {
        host.start(INFO, Set.of(), Set.of(), context -> {
            var settings = new EditorSettings.Declared(context.settings(), Set.of());
            defaults = new DefaultEditor(() -> new DefaultEditor.Preference(settings.defaultEditor(), settings.customCommand(),
                    settings.customInTerminal(), settings.keepEnvironmentEditor()),
                () -> new Detection.Probe("Mac OS X", List.of(bin), home, Map.of(), programs::contains), context.background(), Runnable::run,
                context.terminals(), (file, line, window) -> false, (command, program) -> false, Optional.empty(), "Mac OS X", context.log());
            context.settings().page(new EditorSettingsPanel(context.kit(), settings, defaults, environment));
        });
        host.settingsPanel(ID).orElseThrow();
        return host.settingsPanelView(ID).orElseThrow();
    }

    private void install(String name) { programs.add(bin.resolve(name).toAbsolutePath().normalize()); }

    /** Runs the detections the page and the tests asked for. */
    private void detect() { while (host.runBackground() > 0) { } }

    @Test void theStandardRowsKeepTheirGroupsAndDefaultEditorFollows() {
        FakeKitView view = page(Map.of());
        var page = (Nodes.Stack) view.spec().root();
        assertThat(page.role()).isEqualTo(Nodes.LayoutRole.SETTINGS_PAGE);
        assertThat(page.children()).extracting(node -> ((Nodes.Section) node).title()).containsExactly("Font", "Editing", "Files", "Default editor");
        assertThat(rows(view, EditorSettingsPanel.FONT)).containsExactly("settings:font_family:row", "settings:font_size:row");
        assertThat(rows(view, EditorSettingsPanel.EDITING)).containsExactly("settings:tab_size:row", "settings:soft_wrap:row", "settings:vim:row");
        assertThat(rows(view, EditorSettingsPanel.FILES)).containsExactly("settings:max_file_mib:row", "settings:recent_files_days:row");
        assertThat(rows(view, EditorSettingsPanel.DEFAULT))
            .containsExactly(EditorSettingsPanel.CHOICE + ":row", "settings:keep_environment_editor:row", EditorSettingsPanel.ENVIRONMENT);
        for (String section : List.of(EditorSettingsPanel.FONT, EditorSettingsPanel.EDITING, EditorSettingsPanel.FILES, EditorSettingsPanel.DEFAULT))
            assertThat(node(view, section + ".card", Nodes.Card.class).kind()).as(section).isEqualTo(Nodes.CardKind.SETTINGS);
        assertThat(node(view, "settings:font_family", Nodes.Choice.class).kind()).as("the font picker, as on the generated page")
            .isEqualTo(Nodes.ChoiceKind.SEARCHABLE);
        assertThat(host.failures()).isEmpty();
    }

    @Test void theDropdownListsTheJasperEditorWhatDetectionFoundTheSystemsAppAndACustomCommand() {
        install("nvim");
        install("code");
        FakeKitView view = page(Map.of());
        assertThat(options(view)).as("before detection").containsExactly("jasper=Jasper Editor", "system=System default app", "custom=Custom command");
        detect();
        assertThat(options(view)).as("detection re-renders the page")
            .containsExactly("jasper=Jasper Editor", "nvim=Neovim", "code=Visual Studio Code", "system=System default app", "custom=Custom command");
        assertThat(choice(view).kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
        assertThat(choice(view).selectedId()).isEqualTo("jasper");
    }

    /** Review Focus 5, the page part: the saved editor was uninstalled. */
    @Test void aSavedEditorThatIsNotInstalledIsListedAsNotFound() {
        host.setSetting(ID, "default_editor", "nvim");
        FakeKitView view = page(Map.of());
        detect();
        assertThat(options(view)).containsExactly("jasper=Jasper Editor", "nvim=Neovim (not found)", "system=System default app", "custom=Custom command");
        assertThat(choice(view).selectedId()).isEqualTo("nvim");
        install("nvim");
        defaults.detect();
        detect();
        assertThat(options(view)).as("installed again").contains("nvim=Neovim").doesNotContain("nvim=Neovim (not found)");
    }

    @Test void choosingStagesTheValueAndOnlyACustomCommandShowsItsRows() {
        FakeKitView view = page(Map.of());
        view.emit(new UiEvent.Selected(EditorSettingsPanel.CHOICE, Set.of("custom")));
        assertThat(host.pendingSettings(ID)).containsEntry("default_editor", "custom");
        assertThat(host.settingsPanelChanges(ID)).isPositive();
        assertThat(rows(view, EditorSettingsPanel.DEFAULT)).containsExactly(EditorSettingsPanel.CHOICE + ":row", EditorSettingsPanel.COMMAND + ":row",
            "settings:custom_in_terminal:row", "settings:keep_environment_editor:row", EditorSettingsPanel.ENVIRONMENT);
        assertThat(node(view, EditorSettingsPanel.COMMAND + ":row", Nodes.SettingRow.class).dependent()).isTrue();
        assertThat(node(view, "settings:custom_in_terminal:row", Nodes.SettingRow.class).dependent()).isTrue();
        assertThat(field(view).state().error()).as("an empty command").isEqualTo(EditorSettingsPanel.NO_COMMAND);
        view.emit(new UiEvent.Edited(EditorSettingsPanel.COMMAND, "subl -w"));
        assertThat(host.pendingSettings(ID)).containsEntry("custom_command", "subl -w");
        assertThat(field(view).state().error()).isEmpty();
        assertThat(field(view).value()).isEqualTo("subl -w");
        view.emit(new UiEvent.Edited(EditorSettingsPanel.COMMAND, "  "));
        assertThat(field(view).state().error()).as("a blank command").isEqualTo(EditorSettingsPanel.NO_COMMAND);
        view.emit(new UiEvent.Edited(EditorSettingsPanel.COMMAND, "subl -w"));
        host.applySettingsPanel(ID);
        assertThat(host.savedSettings(ID)).containsEntry("default_editor", "custom").containsEntry("custom_command", "subl -w");
        view.emit(new UiEvent.Selected(EditorSettingsPanel.CHOICE, Set.of("system")));
        assertThat(rows(view, EditorSettingsPanel.DEFAULT))
            .containsExactly(EditorSettingsPanel.CHOICE + ":row", "settings:keep_environment_editor:row", EditorSettingsPanel.ENVIRONMENT);
        view.emit(new UiEvent.Action(EditorSettingsPanel.CHOICE + ":reset"));
        assertThat(choice(view).selectedId()).as("Reset").isEqualTo("jasper");
        assertThat(host.failures()).isEmpty();
    }

    @Test void theNoticeSaysWhatJaspersEnvironmentHolds() {
        assertThat(EditorSettingsPanel.environmentText(Map.of("EDITOR", "nvim"))).isEqualTo("Jasper's environment has EDITOR=nvim");
        assertThat(EditorSettingsPanel.environmentText(Map.of("VISUAL", "code --wait"))).isEqualTo("Jasper's environment has VISUAL=code --wait");
        assertThat(EditorSettingsPanel.environmentText(Map.of("EDITOR", "vim", "VISUAL", "nvim")))
            .isEqualTo("Jasper's environment has EDITOR=vim and VISUAL=nvim");
        assertThat(EditorSettingsPanel.environmentText(Map.of("EDITOR", " "))).as("blank is unset")
            .isEqualTo("Jasper's environment has neither EDITOR nor VISUAL");
        assertThat(EditorSettingsPanel.environmentText(Map.of())).isEqualTo("Jasper's environment has neither EDITOR nor VISUAL");
    }

    /** Review Focus 2, the page part: Jasper started from a terminal with EDITOR=vim says so beside the setting that keeps it. */
    @Test void thePageShowsJaspersEditorBesideTheSettingThatKeepsIt() {
        FakeKitView view = page(Map.of("EDITOR", "vim"));
        var notice = node(view, EditorSettingsPanel.ENVIRONMENT, Nodes.SettingRow.class);
        assertThat(notice.title()).isEqualTo("Jasper's environment has EDITOR=vim");
        assertThat(notice.description()).isEqualTo("An export in a shell startup file, such as ~/.zshrc, still wins.");
        assertThat(rows(view, EditorSettingsPanel.DEFAULT)).containsSubsequence("settings:keep_environment_editor:row", EditorSettingsPanel.ENVIRONMENT);
        view.emit(new UiEvent.Toggled("settings:keep_environment_editor", false));
        assertThat(host.pendingSettings(ID)).containsEntry("keep_environment_editor", false);
    }

    @Test void searchMarksThePagesOwnRows() {
        FakeKitView view = page(Map.of());
        host.highlightSettingsPanel(ID, "default editor");
        assertThat(node(view, EditorSettingsPanel.CHOICE + ":row", Nodes.SettingRow.class).highlighted()).isTrue();
        host.highlightSettingsPanel(ID, "");
        assertThat(node(view, EditorSettingsPanel.CHOICE + ":row", Nodes.SettingRow.class).highlighted()).isFalse();
    }

    @Test void aClosedPageNoLongerFollowsDetection() {
        page(Map.of());
        detect();
        host.closeSettingsPanel(ID);
        install("vim");
        defaults.detect();
        detect();
        assertThat(defaults.detected()).hasSize(1);
        assertThat(host.failures()).as("the closed page was not rendered").isEmpty();
    }

    private static List<String> rows(FakeKitView view, String section) {
        return node(view, section + ".card", Nodes.Card.class).children().stream().map(Nodes.Node::id).toList();
    }

    private static Nodes.Choice choice(FakeKitView view) { return node(view, EditorSettingsPanel.CHOICE, Nodes.Choice.class); }

    private static Nodes.Field field(FakeKitView view) { return node(view, EditorSettingsPanel.COMMAND, Nodes.Field.class); }

    private static List<String> options(FakeKitView view) {
        return choice(view).options().stream().map(option -> option.id() + "=" + option.label()).toList();
    }

    private static <T extends Nodes.Node> T node(FakeKitView view, String id, Class<T> type) {
        Nodes.Node found = find(view.spec().root(), id);
        assertThat(found).as(id).isInstanceOf(type);
        return type.cast(found);
    }

    private static Nodes.Node find(Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        List<Nodes.Node> children = switch (node) {
            case Nodes.Stack stack -> stack.children();
            case Nodes.Section section -> List.of(section.body());
            case Nodes.Card card -> card.children();
            case Nodes.SettingRow row -> List.of(row.control());
            default -> List.of();
        };
        for (Nodes.Node child : children) {
            Nodes.Node found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }
}

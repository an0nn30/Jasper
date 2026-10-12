package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EditorsTest {
    private final PageContext context = PageKit.context(
        new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()), (title, initial) -> Optional.of(Path.of("/bin/zsh")));

    private static <T> ValueEditor<T> build(Editors.Factory<T> factory, Setting<T> setting, PageContext context) {
        return factory.build().apply(setting, context);
    }

    private static <T> Nodes.Node node(ValueEditor<T> editor, Setting<T> setting) { return editor.node(setting.name(), ControlState.READY); }

    /** The editor's primary control: the node named for the setting. */
    private static Nodes.Node control(Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        if (node instanceof Nodes.Stack stack) for (var child : stack.children()) {
            var found = control(child, id);
            if (found != null) return found;
        }
        if(node instanceof Nodes.Card card)for(var child:card.children()){var found=control(child,id);if(found!=null)return found;}
        if(node instanceof Nodes.SettingRow row)return control(row.control(),id);
        return null;
    }

    private static <T> Nodes.Node primary(Editors.Factory<T> factory, Setting<T> setting, PageContext context) {
        var editor = build(factory, setting, context);
        return control(node(editor, setting), setting.name());
    }

    @Test void schemaKindsMapToTypedFieldChoiceAndToggleRoles() {
        assertThat(primary(Editors.byKind(CoreSettings.WINDOW_STATUS_BAR), CoreSettings.WINDOW_STATUS_BAR, context)).isInstanceOf(Nodes.Toggle.class);
        assertThat(primary(Editors.byKind(CoreSettings.WINDOW_TOOLBAR), CoreSettings.WINDOW_TOOLBAR, context))
            .isInstanceOfSatisfying(Nodes.Choice.class, choice -> assertThat(choice.kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN));
        assertThat(primary(Editors.byKind(CoreSettings.TERMINAL_SHELL_PROFILE), CoreSettings.TERMINAL_SHELL_PROFILE, context))
            .isInstanceOfSatisfying(Nodes.Field.class, field -> assertThat(field.kind()).isEqualTo(Nodes.FieldKind.TEXT));
        assertThat(primary(Editors.byKind(CoreSettings.PALETTE_VISIBLE_ROWS), CoreSettings.PALETTE_VISIBLE_ROWS, context))
            .isInstanceOfSatisfying(Nodes.Field.class, field -> assertThat(field.kind()).isEqualTo(Nodes.FieldKind.NUMBER));
        assertThat(primary(Editors.byKind(CoreSettings.TERMINAL_FONT_SIZE), CoreSettings.TERMINAL_FONT_SIZE, context))
            .isInstanceOfSatisfying(Nodes.Field.class, field -> assertThat(field.kind()).isEqualTo(Nodes.FieldKind.NUMBER));
        assertThat(primary(Editors.byKind(CoreSettings.TERMINAL_SHELL_ARGS), CoreSettings.TERMINAL_SHELL_ARGS, context))
            .isInstanceOfSatisfying(Nodes.ItemList.class, list -> assertThat(list.kind()).isEqualTo(Nodes.ListKind.ORDERED));
        assertThat(primary(Editors.executable(), CoreSettings.TERMINAL_SHELL_PROGRAM, context))
            .isInstanceOfSatisfying(Nodes.Field.class, field -> assertThat(field.kind()).isEqualTo(Nodes.FieldKind.PATH));
        assertThat(primary(Editors.font(true), CoreSettings.TERMINAL_FONT_FAMILY, context))
            .isInstanceOfSatisfying(Nodes.Choice.class, choice -> {
                assertThat(choice.kind()).isEqualTo(Nodes.ChoiceKind.SEARCHABLE);
                assertThat(choice.search().orElseThrow().entry()).as("font families are free entry").isPresent();
            });
    }

    @Test void switchesCarryTheirLabelAndShowingDoesNotCountAsAnEdit() {
        var editor = build(Editors.checkbox(), CoreSettings.WINDOW_STATUS_BAR, context);
        assertThat(editor.labelled()).isTrue();
        editor.show(false);
        assertThat(editor.value()).isFalse();
        assertThat(node(editor, CoreSettings.WINDOW_STATUS_BAR)).isEqualTo(new Nodes.Toggle("window.status_bar", ControlState.READY,
            "Show status bar", false, "window.status_bar", false));
        assertThat(editor.event("window.status_bar", new UiEvent.Toggled("window.status_bar", true))).isTrue();
        assertThat(editor.value()).isTrue();
        assertThat(editor.event("window.status_bar", new UiEvent.Toggled("other", false))).as("another node's event").isFalse();
        assertThat(editor.value()).isTrue();
    }

    @Test void numbersUseTheKindsRangeAndKeepTheirValueType() {
        var height = build(Editors.integer("px"), CoreSettings.WINDOW_TAB_HEIGHT, context);
        height.show(44);
        assertThat(height.value()).isEqualTo(44);
        var line = (Nodes.Stack) node(height, CoreSettings.WINDOW_TAB_HEIGHT);
        assertThat(line.children()).last().isEqualTo(new Nodes.Text("window.tab_height:unit", ControlState.READY, "px", Nodes.TextRole.LABEL));
        var field = (Nodes.Field) line.children().getFirst();
        assertThat(field.value()).isEqualTo("44");
        assertThat(field.numeric()).contains(new Nodes.Numeric(20, 72, 1, 0));
        var size = build(Editors.number(0.5, "pt"), CoreSettings.TERMINAL_FONT_SIZE, context);
        size.show(16f);
        assertThat(size.value()).isEqualTo(16f);
        size.show(13.25f);
        assertThat(((Nodes.Field) control(node(size, CoreSettings.TERMINAL_FONT_SIZE), "terminal.font.size")).value()).isEqualTo("13.25");
        assertThat(size.event("terminal.font.size", new UiEvent.Edited("terminal.font.size", "14.5"))).isTrue();
        assertThat(size.value()).isEqualTo(14.5f);
        size.event("terminal.font.size", new UiEvent.Edited("terminal.font.size", "1."));
        assertThat(size.value()).as("as the user types").isEqualTo(1f);
        size.event("terminal.font.size", new UiEvent.Edited("terminal.font.size", ""));
        assertThatThrownBy(size::value).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void choicesShowReadableNamesAndReturnTheirValues() {
        var editor = (Editors.Pick<ToolbarMode>) build(Editors.<ToolbarMode>choice(Map.of("icons", "Icons only")), CoreSettings.WINDOW_TOOLBAR, context);
        assertThat(editor.labels().get(0)).isEqualTo("Icons and labels");
        assertThat(editor.labels().get(1)).isEqualTo("Icons only");
        editor.show(ToolbarMode.HIDDEN);
        assertThat(editor.selectedLabel()).isEqualTo("Hidden");
        assertThat(editor.value()).isEqualTo(ToolbarMode.HIDDEN);
        var choice = (Nodes.Choice) node(editor, CoreSettings.WINDOW_TOOLBAR);
        assertThat(choice.options()).extracting(dev.jasper.ui.model.Option::label).startsWith("Icons and labels", "Icons only");
        assertThat(editor.event("window.toolbar", new UiEvent.Selected("window.toolbar", Set.of(choice.options().get(1).id())))).isTrue();
        assertThat(editor.selectedLabel()).isEqualTo("Icons only");
        assertThat(Editors.pretty("close_on_success")).isEqualTo("Close on success");
        assertThat(Editors.<ToolbarMode>choice(Map.of("icons", "Icons only")).terms()).contains("Icons only");
    }

    @Test void listsTablesAndExecutablesEditTheirValues() {
        var args = (Editors.Items) build(Editors.textList(), CoreSettings.TERMINAL_SHELL_ARGS, context);
        String id = "terminal.shell.args";
        args.show(List.of("-l"));
        assertThat(args.event(id, new UiEvent.Edited(id + ":new", "-i"))).as("typing the new entry is no edit yet").isFalse();
        assertThat(args.event(id, new UiEvent.Action(id + ":add"))).isTrue();
        assertThat(args.value()).containsExactly("-l", "-i");
        assertThat(args.event(id, new UiEvent.Reordered(id, "item-1", 0))).isTrue();
        assertThat(args.value()).containsExactly("-i", "-l");
        assertThat(args.event(id, new UiEvent.Removed(id, "item-0"))).isTrue();
        assertThat(args.value()).containsExactly("-l");
        assertThat(args.event(id, new UiEvent.Action(id + ":add"))).as("a blank entry adds nothing").isFalse();
        var env = (Editors.Table) build(Editors.table(), CoreSettings.TERMINAL_ENV, context);
        String envId = "terminal.env";
        env.show(Map.of("B", "2", "A", "1"));
        assertThat(env.value()).containsExactly(Map.entry("A", "1"), Map.entry("B", "2"));
        env.event(envId, new UiEvent.Action(envId + ":add"));
        env.event(envId, new UiEvent.Edited(envId + ":name-2", "C"));
        env.event(envId, new UiEvent.Edited(envId + ":value-2", "3"));
        assertThat(env.value()).containsEntry("C", "3");
        env.event(envId, new UiEvent.Action(envId + ":remove-0"));
        assertThat(env.value()).doesNotContainKey("A");
        var shell = (Editors.Browse) build(Editors.executable(), CoreSettings.TERMINAL_SHELL_PROGRAM, context);
        assertThat(shell.event("terminal.shell.program", new UiEvent.Action("terminal.shell.program:browse"))).isTrue();
        assertThat(shell.value()).isEqualTo("/bin/zsh");
    }

    @Test void theOptionalSizeUsesThePlatformUntilTurnedOff() {
        var editor = (Editors.OptionalNumber) build(Editors.optionalNumber("Use platform size", 0.5, "pt"), CoreSettings.UI_FONT_SIZE, context);
        assertThat(editor.removesAtDefault()).isTrue();
        editor.show(0f);
        assertThat(editor.usesDefault()).isTrue();
        assertThat(editor.value()).isEqualTo(0f);
        assertThat(control(editor.node("ui.font.size", ControlState.READY), "ui.font.size").state().enabled()).as("the number waits").isFalse();
        assertThat(editor.event("ui.font.size", new UiEvent.Toggled("ui.font.size:default", false))).isTrue();
        assertThat(editor.value()).isEqualTo(14f);
        assertThat(control(editor.node("ui.font.size", ControlState.READY), "ui.font.size").state().enabled()).isTrue();
        editor.show(18f);
        assertThat(editor.usesDefault()).isFalse();
        assertThat(editor.value()).isEqualTo(18f);
    }

    @Test void fontsListInstalledFamiliesAndAMonospacedSubset() {
        assertThat(Fonts.families(false)).contains("Monospaced", "Dialog");
        assertThat(Fonts.families(true)).contains("Monospaced").doesNotContain("Dialog");
        var font = (Editors.FontField) build(Editors.font(true), CoreSettings.TERMINAL_FONT_FAMILY, context);
        assertThat(font.suggestions()).isEqualTo(Fonts.families(true));
        font.show("Iosevka");
        assertThat(font.event("terminal.font.family", new UiEvent.Edited("terminal.font.family", "Menlo"))).isTrue();
        assertThat(font.value()).isEqualTo("Menlo");
    }

    private static Set<String> pick(int index) { return Set.of("profile-" + index); }

    @Test void theProfileEditorListsDetectedShellsAndShowsTheEffectiveOne() {
        var listeners = new ArrayList<Runnable>();
        var refreshes = new int[1];
        var renders = new int[1];
        var choices = new AtomicReference<>(List.of(new ShellChoices.Choice("powershell", "PowerShell"),
            new ShellChoices.Choice("wsl:Ubuntu", "Ubuntu (WSL 1)"), new ShellChoices.Choice("custom", "Custom")));
        var shells = new ShellChoices(choices::get, () -> refreshes[0]++,
            listener -> { listeners.add(listener); return new Subscription(() -> listeners.remove(listener)); });
        var editor = (Editors.Profile) build(Editors.profile(shells), CoreSettings.TERMINAL_SHELL_PROFILE, context);
        editor.onChanged(() -> renders[0]++);
        String id = "terminal.shell.profile";
        var edits = new int[1];
        assertThat(editor.labels()).containsExactly("PowerShell", "Ubuntu (WSL 1)", "Custom");
        editor.show("");
        assertThat(editor.value()).as("empty shows the platform default").isEqualTo("powershell");
        if (editor.event(id, new UiEvent.Selected(id, pick(1)))) edits[0]++;
        assertThat(editor.value()).isEqualTo("wsl:Ubuntu");
        assertThat(edits[0]).isEqualTo(1);
        editor.show("wsl:ubuntu");
        assertThat(editor.selectedLabel()).as("WSL names ignore case").isEqualTo("Ubuntu (WSL 1)");
        editor.show("wsl:Gone");
        assertThat(editor.selectedLabel()).isEqualTo("wsl:Gone (not detected)");
        assertThat(editor.value()).isEqualTo("wsl:Gone");
        assertThat(editor.event(id, new UiEvent.Action(id + ":detect"))).isFalse();
        assertThat(refreshes[0]).isEqualTo(1);
        choices.set(List.of(new ShellChoices.Choice("powershell", "PowerShell"), new ShellChoices.Choice("custom", "Custom")));
        List.copyOf(listeners).forEach(Runnable::run);
        assertThat(renders[0]).as("a refill asks the page to render").isEqualTo(1);
        assertThat(editor.value()).as("a refill keeps the selection").isEqualTo("wsl:Gone");
        assertThat(edits[0]).as("showing and refilling are not edits").isEqualTo(1);
        editor.dispose();
        assertThat(listeners).isEmpty();
    }

    @Test void theProfileEditorKeepsOneUndetectedEntryAndIgnoresRepickingTheShownOne() {
        var shells = new ShellChoices(() -> List.of(new ShellChoices.Choice("powershell", "PowerShell"),
            new ShellChoices.Choice("custom", "Custom")), () -> { }, listener -> new Subscription(() -> { }));
        var editor = (Editors.Profile) build(Editors.profile(shells), CoreSettings.TERMINAL_SHELL_PROFILE, context);
        String id = "terminal.shell.profile";
        var edits = new ArrayList<String>();
        editor.show("wsl:Gone");
        editor.show("pwsh");
        assertThat(editor.labels()).as("an earlier undetected entry goes when another is shown").containsExactly("PowerShell", "Custom", "pwsh (not detected)");
        editor.show("");
        assertThat(editor.labels()).containsExactly("PowerShell", "Custom");
        if (editor.event(id, new UiEvent.Selected(id, pick(0)))) edits.add(editor.value());
        assertThat(edits).as("picking the entry already shown for the default changes nothing").isEmpty();
        if (editor.event(id, new UiEvent.Selected(id, pick(1)))) edits.add(editor.value());
        if (editor.event(id, new UiEvent.Selected(id, pick(0)))) edits.add(editor.value());
        assertThat(edits).as("moving away and back are both edits").containsExactly("custom", "powershell");
    }

    @Test void anOldProgramSettingShowsAsCustom() {
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "[terminal.shell]\nprogram = '/bin/fish'\n", true).values());
        var legacy = PageKit.context(draft, (title, initial) -> Optional.empty());
        var editor = build(Editors.profile(ShellChoices.NONE), CoreSettings.TERMINAL_SHELL_PROFILE, legacy);
        editor.show("");
        assertThat(editor.value()).isEqualTo("custom");
    }

    @Test void aPathEditorBrowsesForAFile() {
        var tool = Setting.of("tool", SettingKind.string(text -> true, ""), "").label("Tool").build();
        var editor = (Editors.Browse) build(Editors.path("Choose a file"), tool, context);
        assertThat(((Nodes.Field) editor.node("tool", ControlState.READY)).trailingAction()).isEqualTo("tool:browse");
        editor.browse();
        assertThat(editor.value()).isEqualTo("/bin/zsh");
    }
}

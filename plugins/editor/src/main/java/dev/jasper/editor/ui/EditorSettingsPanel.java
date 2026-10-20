package dev.jasper.editor.ui;

import dev.jasper.editor.external.DefaultEditor;
import dev.jasper.editor.external.Detected;
import dev.jasper.editor.external.KnownEditors;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Editor's page in Settings > Tools > Editor: Font, Editing and Files as Settings' standard rows, grouped as the
 * generated page grouped them, then Default editor: a dropdown of the Jasper Editor, the editors detection found, the
 * system's default app and a custom command, the custom command's rows while it is chosen, and what Jasper's own
 * environment holds for {@code EDITOR} and {@code VISUAL}. Detection runs again each time the page opens, and the page
 * follows it. UI thread.
 */
public final class EditorSettingsPanel implements SettingsPanel {
    static final String PAGE = "editor.settings", FONT = "editor.settings.font", EDITING = "editor.settings.editing",
        FILES = "editor.settings.files", DEFAULT = "editor.settings.default", CHOICE = "editor.default-editor",
        COMMAND = "editor.custom-command", ENVIRONMENT = "editor.environment";
    /** The note under what Jasper's environment holds. */
    static final String NOTE = "An export in a shell startup file, such as ~/.zshrc, still wins.";
    /** The custom command field's error while it is blank. */
    static final String NO_COMMAND = "Enter a command; until then files open with your system's default app.";

    private final UiKit kit;
    private final EditorSettings.Declared settings;
    private final DefaultEditor defaults;
    private final Map<String, String> environment;
    private SettingsPanelHost host;
    private UiView view;
    private Subscription detecting = () -> { };
    private String query = "";

    /**
     * @param kit the plugin's kit
     * @param settings the Editor's declared settings
     * @param defaults the default editor, whose detection the dropdown lists
     * @param environment Jasper's own environment, for what the page says about {@code EDITOR} and {@code VISUAL}
     */
    public EditorSettingsPanel(UiKit kit, EditorSettings.Declared settings, DefaultEditor defaults, Map<String, String> environment) {
        this.kit = Objects.requireNonNull(kit, "kit");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
        this.environment = Map.copyOf(environment);
    }

    @Override public UiView create(SettingsPanelHost host) {
        this.host = host;
        view = kit.create(new ViewSpec(node()), this::event);
        detecting = defaults.onDetected(this::render);
        defaults.detect();
        return view;
    }

    @Override public List<String> keywords() {
        return List.of("default editor", "EDITOR", "VISUAL", "git", "external editor", "custom command", "vim", "nvim", "code");
    }

    @Override public void highlight(String query) {
        this.query = Objects.requireNonNull(query, "query");
        render();
    }

    @Override public void dispose() {
        detecting.close();
        detecting = () -> { };
        view = null;
        host = null;
    }

    private void event(UiEvent event) {
        if (view == null) return;
        switch (event) {
            case UiEvent.Selected selected when selected.id().equals(CHOICE) ->
                selected.ids().stream().findFirst().ifPresent(id -> stage(settings.defaultEditor, id));
            case UiEvent.Action action when action.id().equals(CHOICE + ":reset") -> stage(settings.defaultEditor, EditorSettings.Declared.JASPER);
            case UiEvent.Edited edited when edited.id().equals(COMMAND) -> stage(settings.customCommand, edited.text());
            case UiEvent.Action action when action.id().equals(COMMAND + ":reset") -> stage(settings.customCommand, "");
            default -> { }
        }
    }

    private void stage(Setting<String> setting, String value) {
        host.stage(setting, value);
        host.changed();
        render();
    }

    private void render() { if (view != null) view.update(new ViewSpec(node())); }

    private Nodes.Stack node() {
        String chosen = host.pending(settings.defaultEditor);
        var defaultEditor = new ArrayList<Nodes.Node>();
        defaultEditor.add(choiceRow(chosen));
        if (chosen.equals("custom")) {
            defaultEditor.add(commandRow(host.pending(settings.customCommand)));
            defaultEditor.add(dependent(host.row(settings.customInTerminal)));
        }
        defaultEditor.add(host.row(settings.keepEnvironmentEditor));
        defaultEditor.add(environmentRow());
        return new Nodes.Stack(PAGE, ControlState.READY, Nodes.LayoutRole.SETTINGS_PAGE, List.of(
            section(FONT, "Font", List.of(host.row(settings.fontFamily), host.row(settings.fontSize))),
            section(EDITING, "Editing", List.of(host.row(settings.tabSize), host.row(settings.softWrap), host.row(settings.vim))),
            section(FILES, "Files", List.of(host.row(settings.maxFileMib), host.row(settings.recentFilesDays))),
            section(DEFAULT, "Default editor", defaultEditor)));
    }

    /** The dropdown; a saved editor detection did not find is its own option, "(not found)". */
    private Nodes.SettingRow choiceRow(String chosen) {
        var options = new ArrayList<Option>();
        options.add(new Option(EditorSettings.Declared.JASPER, "Jasper Editor", Optional.empty()));
        List<Detected> found = defaults.detected();
        for (Detected editor : found) options.add(new Option(editor.editor().id(), editor.editor().name(), Optional.empty()));
        if (found.stream().noneMatch(editor -> editor.editor().id().equals(chosen)))
            KnownEditors.byId(chosen).ifPresent(missing -> options.add(new Option(missing.id(), missing.name() + " (not found)", Optional.empty())));
        options.add(new Option("system", "System default app", Optional.empty()));
        options.add(new Option("custom", "Custom command", Optional.empty()));
        var choice = new Nodes.Choice(CHOICE, ControlState.READY, Nodes.ChoiceKind.DROPDOWN, options, chosen,
            EditorSettings.Declared.DEFAULT_EDITOR_LABEL, CHOICE);
        return row(CHOICE, EditorSettings.Declared.DEFAULT_EDITOR_LABEL, EditorSettings.Declared.DEFAULT_EDITOR_DESCRIPTION, choice,
            !chosen.equals(EditorSettings.Declared.JASPER), false);
    }

    private Nodes.SettingRow commandRow(String command) {
        var field = new Nodes.Field(COMMAND, new ControlState(true, true, false, command.isBlank() ? NO_COMMAND : ""), Nodes.FieldKind.TEXT,
            command, "", EditorSettings.Declared.COMMAND_LABEL, "");
        return row(COMMAND, EditorSettings.Declared.COMMAND_LABEL, EditorSettings.Declared.COMMAND_DESCRIPTION, field, !command.isEmpty(), true);
    }

    /** A row arranged as Settings' standard rows: the control, then an icon-only Reset while the value is not the default. */
    private Nodes.SettingRow row(String id, String title, String description, Nodes.Node control, boolean resettable, boolean dependent) {
        var reset = new Nodes.Button(id + ":reset", new ControlState(resettable, true, false, ""), "", "reset", Nodes.ButtonKind.GHOST,
            id + ":reset", "Reset " + title + " to default");
        var trailing = new Nodes.Stack(id + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(control, reset));
        return new Nodes.SettingRow(id + ":row", ControlState.READY, title, description, trailing, dependent, matches(title, description));
    }

    private Nodes.SettingRow environmentRow() {
        return new Nodes.SettingRow(ENVIRONMENT, ControlState.READY, environmentText(environment), NOTE,
            new Nodes.Stack(ENVIRONMENT + ":controls", ControlState.READY, Nodes.LayoutRole.INLINE, List.of()), false, false);
    }

    /**
     * What the page says about Jasper's own {@code EDITOR} and {@code VISUAL}; blank values count as unset, as they do
     * for {@code keep_environment_editor}.
     */
    static String environmentText(Map<String, String> environment) {
        String editor = environment.get("EDITOR"), visual = environment.get("VISUAL");
        boolean hasEditor = editor != null && !editor.isBlank(), hasVisual = visual != null && !visual.isBlank();
        if (hasEditor && hasVisual) return "Jasper's environment has EDITOR=" + editor + " and VISUAL=" + visual;
        if (hasEditor) return "Jasper's environment has EDITOR=" + editor;
        if (hasVisual) return "Jasper's environment has VISUAL=" + visual;
        return "Jasper's environment has neither EDITOR nor VISUAL";
    }

    /** A standard row shown under the dropdown, indented as a setting that depends on it. */
    private static Nodes.Node dependent(Nodes.Node row) {
        return row instanceof Nodes.SettingRow standard ? new Nodes.SettingRow(standard.id(), standard.state(), standard.title(),
            standard.description(), standard.control(), true, standard.highlighted(), standard.titleNote()) : row;
    }

    private boolean matches(String... texts) {
        var words = Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+")).filter(word -> !word.isEmpty()).toList();
        if (words.isEmpty()) return false;
        String text = String.join(" ", texts).toLowerCase(Locale.ROOT);
        return words.stream().allMatch(text::contains);
    }

    private static Nodes.Section section(String id, String title, List<Nodes.Node> rows) {
        return new Nodes.Section(id, ControlState.READY, title, new Nodes.Card(id + ".card", ControlState.READY, Nodes.CardKind.SETTINGS, rows));
    }
}

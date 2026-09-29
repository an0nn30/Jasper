package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.lifecycle.Subscription;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class EditorsTest {
    private final PageContext context = new PageContext(
        new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()),
        id -> { }, Runnable::run, (title, initial) -> Optional.of(Path.of("/bin/zsh")), () -> { });

    private static <T> ValueEditor<T> build(Editors.Factory<T> factory, dev.jasper.app.settings.Setting<T> setting, PageContext context) {
        return factory.build().apply(setting, context);
    }

    @Test void kindsPickTheirDefaultEditors() {
        assertThat(build(Editors.byKind(CoreSettings.WINDOW_STATUS_BAR), CoreSettings.WINDOW_STATUS_BAR, context).component()).isInstanceOf(JCheckBox.class);
        assertThat(build(Editors.byKind(CoreSettings.WINDOW_TOOLBAR), CoreSettings.WINDOW_TOOLBAR, context).component()).isInstanceOf(JComboBox.class);
        assertThat(build(Editors.byKind(CoreSettings.UI_THEME_NAME), CoreSettings.UI_THEME_NAME, context).component()).isInstanceOf(JTextField.class);
        assertThat(build(Editors.byKind(CoreSettings.PALETTE_VISIBLE_ROWS), CoreSettings.PALETTE_VISIBLE_ROWS, context).component()).isInstanceOf(JSpinner.class);
    }

    @Test void checkboxesCarryTheirLabelAndShowingDoesNotCountAsAnEdit() {
        var editor = build(Editors.checkbox(), CoreSettings.WINDOW_STATUS_BAR, context);
        var edits = new int[1];
        editor.onEdit(() -> edits[0]++);
        assertThat(editor.labelled()).isTrue();
        assertThat(((JCheckBox) editor.component()).getText()).isEqualTo("Show status bar");
        editor.show(false);
        assertThat(editor.value()).isFalse();
        assertThat(edits[0]).isZero();
        ((JCheckBox) editor.component()).doClick();
        assertThat(editor.value()).isTrue();
        assertThat(edits[0]).isEqualTo(1);
    }

    @Test void spinnersUseTheKindsRangeAndKeepTheirValueType() {
        var height = build(Editors.integer("px"), CoreSettings.WINDOW_TAB_HEIGHT, context);
        height.show(44);
        assertThat(height.value()).isEqualTo(44);
        var size = build(Editors.number(0.5, "pt"), CoreSettings.TERMINAL_FONT_SIZE, context);
        size.show(16f);
        assertThat(size.value()).isEqualTo(16f);
    }

    @Test void choicesShowReadableNamesAndReturnTheirValues() {
        var editor = build(Editors.<ToolbarMode>choice(Map.of("icons", "Icons only")), CoreSettings.WINDOW_TOOLBAR, context);
        @SuppressWarnings("unchecked") var box = (JComboBox<String>) editor.component();
        assertThat(box.getItemAt(0)).isEqualTo("Icons and labels");
        assertThat(box.getItemAt(1)).isEqualTo("Icons only");
        editor.show(ToolbarMode.HIDDEN);
        assertThat(box.getSelectedItem()).isEqualTo("Hidden");
        assertThat(editor.value()).isEqualTo(ToolbarMode.HIDDEN);
        assertThat(Editors.pretty("close_on_success")).isEqualTo("Close on success");
        assertThat(Editors.<ToolbarMode>choice(Map.of("icons", "Icons only")).terms()).contains("Icons only");
    }

    @Test void listsTablesAndExecutablesEditTheirValues() {
        var args = (Editors.Items) build(Editors.textList(), CoreSettings.TERMINAL_SHELL_ARGS, context);
        args.show(List.of("-l"));
        args.add("-i");
        assertThat(args.value()).containsExactly("-l", "-i");
        args.select(1); args.moveUp();
        assertThat(args.value()).containsExactly("-i", "-l");
        args.select(0); args.removeSelected();
        assertThat(args.value()).containsExactly("-l");
        var env = (Editors.Table) build(Editors.table(), CoreSettings.TERMINAL_ENV, context);
        env.show(Map.of("B", "2", "A", "1"));
        assertThat(env.value()).containsExactly(Map.entry("A", "1"), Map.entry("B", "2"));
        env.addRow("C", "3");
        assertThat(env.value()).containsEntry("C", "3");
        var shell = (Editors.Browse) build(Editors.executable(), CoreSettings.TERMINAL_SHELL_PROGRAM, context);
        shell.browse();
        assertThat(shell.value()).isEqualTo("/bin/zsh");
    }

    @Test void theOptionalSizeUsesThePlatformUntilUnchecked() {
        var editor = (Editors.OptionalNumber) build(Editors.optionalNumber("Use platform size", 0.5, "pt"), CoreSettings.UI_FONT_SIZE, context);
        assertThat(editor.removesAtDefault()).isTrue();
        editor.show(0f);
        assertThat(editor.platform.isSelected()).isTrue();
        assertThat(editor.value()).isEqualTo(0f);
        editor.platform.doClick();
        assertThat(editor.value()).isEqualTo(14f);
        editor.show(18f);
        assertThat(editor.platform.isSelected()).isFalse();
        assertThat(editor.value()).isEqualTo(18f);
    }

    @Test void fontsListInstalledFamiliesAndAMonospacedSubset() {
        assertThat(Fonts.families(false)).contains("Monospaced", "Dialog");
        assertThat(Fonts.families(true)).contains("Monospaced").doesNotContain("Dialog");
    }

    @Test void theProfileEditorListsDetectedShellsAndShowsTheEffectiveOne() {
        var listeners = new ArrayList<Runnable>();
        var refreshes = new int[1];
        var choices = new AtomicReference<>(List.of(new ShellChoices.Choice("powershell", "PowerShell"),
            new ShellChoices.Choice("wsl:Ubuntu", "Ubuntu (WSL 1)"), new ShellChoices.Choice("custom", "Custom")));
        var shells = new ShellChoices(choices::get, () -> refreshes[0]++,
            listener -> { listeners.add(listener); return new Subscription(() -> listeners.remove(listener)); });
        var editor = (Editors.Profile) build(Editors.profile(shells), CoreSettings.TERMINAL_SHELL_PROFILE, context);
        var edits = new int[1];
        editor.onEdit(() -> edits[0]++);
        assertThat(java.util.stream.IntStream.range(0, editor.box.getItemCount()).mapToObj(editor.box::getItemAt))
            .containsExactly("PowerShell", "Ubuntu (WSL 1)", "Custom");
        editor.show("");
        assertThat(editor.value()).as("empty shows the platform default").isEqualTo("powershell");
        editor.box.setSelectedIndex(1);
        assertThat(editor.value()).isEqualTo("wsl:Ubuntu");
        assertThat(edits[0]).isEqualTo(1);
        editor.show("wsl:ubuntu");
        assertThat(editor.box.getSelectedItem()).as("WSL names ignore case").isEqualTo("Ubuntu (WSL 1)");
        editor.show("wsl:Gone");
        assertThat(editor.box.getSelectedItem()).isEqualTo("wsl:Gone (not detected)");
        assertThat(editor.value()).isEqualTo("wsl:Gone");
        editor.detect.doClick();
        assertThat(refreshes[0]).isEqualTo(1);
        choices.set(List.of(new ShellChoices.Choice("powershell", "PowerShell"), new ShellChoices.Choice("custom", "Custom")));
        List.copyOf(listeners).forEach(Runnable::run);
        assertThat(editor.value()).as("a refill keeps the selection").isEqualTo("wsl:Gone");
        assertThat(edits[0]).as("showing and refilling are not edits").isEqualTo(1);
        editor.dispose();
        assertThat(listeners).isEmpty();
    }

    @Test void theProfileEditorKeepsOneUndetectedEntryAndIgnoresRepickingTheShownOne() {
        var shells = new ShellChoices(() -> List.of(new ShellChoices.Choice("powershell", "PowerShell"),
            new ShellChoices.Choice("custom", "Custom")), () -> { }, listener -> new Subscription(() -> { }));
        var editor = (Editors.Profile) build(Editors.profile(shells), CoreSettings.TERMINAL_SHELL_PROFILE, context);
        var edits = new ArrayList<String>();
        editor.onEdit(() -> edits.add(editor.value()));
        editor.show("wsl:Gone");
        editor.show("pwsh");
        assertThat(java.util.stream.IntStream.range(0, editor.box.getItemCount()).mapToObj(editor.box::getItemAt))
            .as("an earlier undetected entry goes when another is shown").containsExactly("PowerShell", "Custom", "pwsh (not detected)");
        editor.show("");
        assertThat(java.util.stream.IntStream.range(0, editor.box.getItemCount()).mapToObj(editor.box::getItemAt))
            .containsExactly("PowerShell", "Custom");
        editor.box.setSelectedIndex(0);
        assertThat(edits).as("picking the entry already shown for the default changes nothing").isEmpty();
        editor.box.setSelectedIndex(1);
        editor.box.setSelectedIndex(0);
        assertThat(edits).as("moving away and back are both edits").containsExactly("custom", "powershell");
    }

    @Test void anOldProgramSettingShowsAsCustom() {
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "[terminal.shell]\nprogram = '/bin/fish'\n", true).values());
        var legacy = new PageContext(draft, id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { });
        var editor = build(Editors.profile(ShellChoices.NONE), CoreSettings.TERMINAL_SHELL_PROFILE, legacy);
        editor.show("");
        assertThat(editor.value()).isEqualTo("custom");
    }

    @Test void aPathEditorBrowsesForAFile() {
        var tool = dev.jasper.app.settings.Setting.of("tool", dev.jasper.app.settings.SettingKind.string(text -> true, ""), "")
            .label("Tool").build();
        var editor = (Editors.Browse) build(Editors.path("Choose a file"), tool, context);
        editor.browse();
        assertThat(editor.value()).isEqualTo("/bin/zsh");
    }
}

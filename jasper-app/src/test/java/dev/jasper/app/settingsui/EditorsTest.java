package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.ToolbarMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        assertThat(build(Editors.byKind(CoreSettings.PALETTE_MAX_RESULTS), CoreSettings.PALETTE_MAX_RESULTS, context).component()).isInstanceOf(JSpinner.class);
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
        var shell = (Editors.Executable) build(Editors.executable(), CoreSettings.TERMINAL_SHELL_PROGRAM, context);
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
}

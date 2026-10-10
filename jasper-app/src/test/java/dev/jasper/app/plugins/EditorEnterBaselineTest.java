package dev.jasper.app.plugins;

import java.awt.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.StagedEditorHost.*;
import static dev.jasper.app.plugins.StagedVaultHost.await;
import static dev.jasper.app.plugins.StagedVaultHost.keyTarget;
import static dev.jasper.app.plugins.StagedVaultHost.named;
import static dev.jasper.app.plugins.StagedVaultHost.settle;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enter, Escape and Space in the staged Editor's dialogs, through the real host's kit components and Swing's key-binding
 * path, against baseline f16ae32f (Plan 2 Task 9a; the baseline table in task-9-report.md). Baseline: the question's
 * root default button (its first choice, or Cancel for a destructive question) took Enter from any component, since
 * FlatLaf's {@code Button.defaultButtonFollowsFocus} is false and its buttons bind no Enter, and Space pressed the
 * focused button; Go to Line submitted from its field's action listener and its default button Go elsewhere, an invalid
 * value keeping it open; Language chose on Enter in its filter or list, with Up/Down in the filter moving the selection
 * and no default button; Saving took no Enter. No dialog bound Escape; Escape now runs the dismissing choice or Cancel
 * (controller ruling, Task 7 precedent).
 */
class EditorEnterBaselineTest {
    @TempDir Path root;
    private dev.jasper.app.appearance.ThemeController controller;

    /** The application's FlatLaf theme, so buttons and fields carry the look and feel's own key bindings, as at baseline. */
    @org.junit.jupiter.api.BeforeEach void theme() {
        controller = onEdtValue(dev.jasper.app.appearance.ThemeController::new);
        onEdt(() -> controller.configure(dev.jasper.app.appearance.Theme.DARK));
        assertThat(onEdtValue(() -> javax.swing.UIManager.getLookAndFeel().getClass().getName())).as("a FlatLaf-based look and feel").containsIgnoringCase("laf");
    }

    @org.junit.jupiter.api.AfterEach void restore() { onEdt(() -> { controller.close(); new dev.jasper.app.appearance.ThemeController(); }); }

    @Test @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void theQuestionsDefaultTakesEnterFromAnywhereSpacePressesTheFocusedButtonAndEscapeDismisses() throws Exception {
        try (var editor = new StagedEditorHost(root.resolve("plain"), true)) {
            List<String> answers = new CopyOnWriteArrayList<>();
            var choices = List.of("Save", "Don't Save", "Cancel");
            onEdt(() -> editor.ask("Unsaved changes", "Save changes to notes.txt?", choices, false, answers::add));
            var sheet = editor.awaitSurface("Unsaved changes");
            onEdt(() -> enter(require(sheet, "question.choice.1")));
            await(sheet::closed, "Enter on the focused Don't Save pressed the default Save and closed the sheet");
            assertThat(answers).containsExactly("Save");

            onEdt(() -> editor.ask("Unsaved changes", "Save changes to notes.txt?", choices, false, answers::add));
            var message = editor.awaitSurface("Unsaved changes");
            onEdt(() -> enter(require(message, "question.message")));
            await(message::closed, "Enter on the message pressed the default");
            assertThat(answers).containsExactly("Save", "Save");

            onEdt(() -> editor.ask("Unsaved changes", "Save changes to notes.txt?", choices, false, answers::add));
            var spaced = editor.awaitSurface("Unsaved changes");
            onEdt(() -> space(require(spaced, "question.choice.1")));
            await(spaced::closed, "Space pressed the focused Don't Save");
            assertThat(answers).containsExactly("Save", "Save", "Don't Save");

            onEdt(() -> editor.ask("Unsaved changes", "Save changes to notes.txt?", choices, false, answers::add));
            var escaped = editor.awaitSurface("Unsaved changes");
            onEdt(() -> escape(require(escaped, "question.choice.0")));
            await(escaped::closed, "Escape dismissed the sheet");
            assertThat(answers).containsExactly("Save", "Save", "Don't Save", "Cancel");
            assertThat(editor.problems).isEmpty();
        }
    }

    @Test @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void aDestructiveQuestionsEnterAndEscapePickCancelNeverTheDestructiveChoice() throws Exception {
        try (var editor = new StagedEditorHost(root.resolve("destructive"), false)) {
            List<String> answers = new CopyOnWriteArrayList<>();
            var choices = List.of("Overwrite", "Reload", "Cancel");
            onEdt(() -> editor.ask("File changed", "notes.txt changed on disk since you opened it.", choices, true, answers::add));
            var sheet = editor.awaitSurface("File changed");
            onEdt(() -> enter(require(sheet, "question.choice.0")));
            await(sheet::closed, "Enter on the focused Overwrite pressed the default Cancel");
            assertThat(answers).containsExactly("Cancel");

            onEdt(() -> editor.ask("File changed", "notes.txt changed on disk since you opened it.", choices, true, answers::add));
            var escaped = editor.awaitSurface("File changed");
            onEdt(() -> escape(require(escaped, "question.choice.0")));
            await(escaped::closed, "Escape picked the safe choice");
            assertThat(answers).containsExactly("Cancel", "Cancel");

            onEdt(() -> editor.ask("File changed", "notes.txt changed on disk since you opened it.", choices, true, answers::add));
            var spaced = editor.awaitSurface("File changed");
            onEdt(() -> space(require(spaced, "question.choice.1")));
            await(spaced::closed, "Space pressed the focused Reload");
            assertThat(answers).containsExactly("Cancel", "Cancel", "Reload");
            assertThat(editor.problems).isEmpty();
        }
    }

    @Test @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void goToLineSubmitsFromItsFieldAndItsDefaultAndAnInvalidLineKeepsItOpen() throws Exception {
        try (var editor = new StagedEditorHost(root.resolve("line"), true)) {
            List<Integer> lines = new CopyOnWriteArrayList<>();
            onEdt(() -> editor.goToLine(1, 40, lines::add));
            var dialog = editor.awaitSurface("Go to Line");
            onEdt(() -> {
                var field = (JTextComponent) require(dialog, "go-to-line.line.input");
                assertThat(field.getText()).isEqualTo("1");
                field.setText("99");
                enter(field);
            });
            settle();
            assertThat(lines).as("an invalid line goes nowhere").isEmpty();
            assertThat(onEdtValue(dialog::closed)).as("and keeps the dialog open").isFalse();
            assertThat(onEdtValue(() -> StagedRemoteHost.labels(dialog.holder()))).as("the error shows inline").contains("Enter a line from 1 to 40");
            onEdt(() -> enter(require(dialog, "go-to-line.cancel")));
            settle();
            assertThat(lines).as("Go is disabled until the next edit, so Enter elsewhere does nothing").isEmpty();
            assertThat(onEdtValue(dialog::closed)).isFalse();
            onEdt(() -> {
                ((JTextComponent) require(dialog, "go-to-line.line.input")).setText("12");
                enter(require(dialog, "go-to-line.cancel"));
            });
            await(dialog::closed, "Enter on the focused Cancel pressed the default Go");
            assertThat(lines).containsExactly(12);

            onEdt(() -> editor.goToLine(12, 40, lines::add));
            var field = editor.awaitSurface("Go to Line");
            onEdt(() -> {
                ((JTextComponent) require(field, "go-to-line.line.input")).setText("7");
                enter(require(field, "go-to-line.line.input"));
            });
            await(field::closed, "Enter in the field submitted");
            assertThat(lines).containsExactly(12, 7);

            onEdt(() -> editor.goToLine(7, 40, lines::add));
            var escaped = editor.awaitSurface("Go to Line");
            onEdt(() -> escape(require(escaped, "go-to-line.line.input")));
            await(escaped::closed, "Escape cancelled");
            assertThat(lines).containsExactly(12, 7);
            assertThat(editor.problems).isEmpty();
        }
    }

    @Test @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void languageMovesFromItsFilterAndChoosesOnEnterOrADoubleClickWithNoDefault() throws Exception {
        try (var editor = new StagedEditorHost(root.resolve("language"), true)) {
            List<String> chosen = new CopyOnWriteArrayList<>();
            onEdt(() -> editor.language("text/python", chosen::add));
            var picker = editor.awaitSurface("Language");
            onEdt(() -> {
                var filter = (JTextComponent) require(picker, "language.filter.input");
                filter.setText("j");
            });
            await(() -> selectedRow(picker) != null && rowNames(picker).size() > 2, "the filter narrowed the list and selected its first match");
            String first = onEdtValue(() -> selectedRow(picker));
            onEdt(() -> {
                var filter = require(picker, "language.filter.input");
                down(filter);
                down(filter);
                up(filter);
            });
            await(() -> !first.equals(selectedRow(picker)), "Down, Down, Up in the filter moved the list");
            String second = onEdtValue(() -> rowNames(picker).get(1));
            assertThat(onEdtValue(() -> selectedRow(picker))).isEqualTo(second);
            assertThat(onEdtValue(() -> ((JTextComponent) require(picker, "language.filter.input")).getText())).as("the field kept its text").isEqualTo("j");
            onEdt(() -> enter(require(picker, "language.filter.input")));
            await(picker::closed, "Enter in the filter chose the selection");
            assertThat(chosen).hasSize(1);

            onEdt(() -> editor.language("text/python", chosen::add));
            var list = editor.awaitSurface("Language");
            onEdt(() -> enter(keyTarget(require(list, "language.list"))));
            await(list::closed, "Enter on the list chose its selection");
            assertThat(chosen).hasSize(2).last().isEqualTo("text/python");

            onEdt(() -> editor.language("text/python", chosen::add));
            var clicked = editor.awaitSurface("Language");
            onEdt(() -> ((JTextComponent) require(clicked, "language.filter.input")).setText("tom"));
            await(() -> rowNames(clicked).size() == 1, "the filter shows TOML alone");
            onEdt(() -> {
                var view = view(clicked);
                view.setSize(420, 480);
                StagedRemoteTunnelsTest.layout(view);
                StagedRemoteTunnelsTest.click(row(view, "TOML"), 2);
            });
            await(clicked::closed, "a double click chose the row");
            assertThat(chosen).hasSize(3).last().isEqualTo("text/toml");

            onEdt(() -> editor.language("text/python", chosen::add));
            var escaped = editor.awaitSurface("Language");
            onEdt(() -> escape(require(escaped, "language.filter.input")));
            await(escaped::closed, "Escape closed the picker");
            assertThat(chosen).as("without choosing").hasSize(3);
            assertThat(editor.problems).isEmpty();
        }
    }

    /** Removing the field-driven list's reserved height must fail here through the host's real DialogFit path. */
    @Test @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void languageKeepsItsPackedHeightWhileFilteredEmptyAndClearedInDarkAndLight() throws Exception {
        for (boolean dark : List.of(true, false)) {
            onEdt(() -> controller.configure(dark ? dev.jasper.app.appearance.Theme.DARK : dev.jasper.app.appearance.Theme.JASPER_LIGHT));
            try (var editor = new StagedEditorHost(root.resolve(dark ? "height-dark" : "height-light"), dark)) {
                List<String> chosen = new CopyOnWriteArrayList<>();
                onEdt(() -> editor.language("text/python", chosen::add));
                var picker = editor.awaitSurface("Language");
                settle();
                var full = onEdtValue(() -> editor.dialogSize(picker));
                int allRows = onEdtValue(() -> ((dev.jasper.ui.model.Nodes.ItemList) StagedVaultHost.node(picker, "language.list")).items().size());
                assertThat(allRows).isGreaterThan(14);
                for (String query : List.of("ja", "no-such-language-xyz", "")) {
                    onEdt(() -> ((JTextComponent) require(picker, "language.filter.input")).setText(query));
                    settle();
                    onEdt(() -> {
                        var list = (dev.jasper.ui.model.Nodes.ItemList) StagedVaultHost.node(picker, "language.list");
                        if (query.isEmpty()) assertThat(list.items()).hasSize(allRows);
                        else if (query.startsWith("no-such")) {
                            assertThat(list.items()).isEmpty();
                            enter(require(picker, "language.filter.input"));
                            assertThat(picker.closed()).as("Enter with no match leaves the picker open").isFalse();
                        } else assertThat(list.items()).hasSizeBetween(1, 13);
                        assertThat(editor.dialogSize(picker)).as("%s dialog size for '%s'", dark ? "dark" : "light", query).isEqualTo(full);
                        assertThat(picker.holder().getPreferredSize()).as("the preferred size stays stable too").isEqualTo(full);
                    });
                }
                assertThat(chosen).isEmpty();
                onEdt(() -> escape(require(picker, "language.filter.input")));
                await(picker::closed, "Escape closes the restored picker");
                assertThat(editor.problems).isEmpty();
            }
        }
    }

    @Test @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void savingTakesNoEnterAndEscapeCancelsWhileALateSaveIsIgnored() throws Exception {
        try (var editor = new StagedEditorHost(root.resolve("saving"), false)) {
            var saves = new CompletableFuture<Boolean>();
            var result = editor.saveWait("Saving 2 files…", saves);
            var dialog = editor.awaitSurface("Saving");
            onEdt(() -> {
                enter(require(dialog, "saving.cancel"));
                enter(require(dialog, "saving.message"));
            });
            settle();
            assertThat(result).as("Enter cancels nothing and saves nothing, as at baseline").isNotDone();
            onEdt(() -> escape(require(dialog, "saving.cancel")));
            assertThat(result.get(10, TimeUnit.SECONDS)).as("Escape cancelled the wait").isFalse();
            assertThat(onEdtValue(dialog::closed)).isTrue();
            onEdt(() -> saves.complete(true));
            settle();
            assertThat(onEdtValue(() -> editor.auxiliary.open().stream().filter(s -> !s.closed()).map(s -> s.title()).toList()))
                .as("a save finishing after the dialog closed opens nothing").doesNotContain("Saving");

            var finishing = new CompletableFuture<Boolean>();
            var finished = editor.saveWait("Saving 1 file…", finishing);
            var second = editor.awaitSurface("Saving");
            onEdt(() -> finishing.complete(true));
            assertThat(finished.get(10, TimeUnit.SECONDS)).isTrue();
            await(second::closed, "the dialog closes once the saves finish");
            assertThat(editor.problems).isEmpty();
        }
    }

    static Component require(dev.jasper.app.windows.AuxiliarySurface surface, String name) {
        var found = named(surface.holder(), name);
        assertThat(found).as("component %s in %s", name, surface.title()).isNotNull();
        return found;
    }

    /** The kit row whose accessible name (its title) is exactly {@code name}; UI thread. */
    static Component row(java.awt.Container root, String name) {
        for (var child : root.getComponents()) {
            if (child.getClass().getName().endsWith("InteractiveRows$Row") && name.equals(child.getAccessibleContext().getAccessibleName())) return child;
            if (child instanceof java.awt.Container nested) { var found = row(nested, name); if (found != null) return found; }
        }
        return null;
    }

    /** The selected row of the live Language list, by its row id, or null; UI thread. */
    static String selectedRow(dev.jasper.app.windows.AuxiliarySurface surface) {
        var list = (dev.jasper.ui.model.Nodes.ItemList) StagedVaultHost.node(surface, "language.list");
        return list == null || list.selectedIds().isEmpty() ? null : list.selectedIds().iterator().next();
    }

    /** The live Language list's row ids, in order; UI thread. */
    static List<String> rowNames(dev.jasper.app.windows.AuxiliarySurface surface) {
        var list = (dev.jasper.ui.model.Nodes.ItemList) StagedVaultHost.node(surface, "language.list");
        return list.items().stream().map(dev.jasper.ui.model.ListItem::id).toList();
    }
}

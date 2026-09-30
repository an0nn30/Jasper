package dev.jasper.app.files;

import static dev.jasper.app.files.ChooserRequest.Mode.*;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class FileChooserPanelTest {
    @TempDir Path dir;
    private final UiState state = UiState.inMemory();
    private final ChooserPrefs prefs = new ChooserPrefs(state);
    private final List<List<Path>> finished = new ArrayList<>();
    private boolean replace;
    private Optional<String> folderName = Optional.empty();

    @BeforeEach void tree() throws IOException {
        Files.createDirectory(dir.resolve("docs"));
        Files.writeString(dir.resolve("notes.txt"), "hello\n");
        Files.writeString(dir.resolve("todo.md"), "- one\n");
    }

    private FileChooserPanel panel(ChooserRequest.Mode mode, Optional<String> name) {
        var request = new ChooserRequest("Choose", mode, false, dir, Optional.of(dir), name, Optional.empty());
        var model = new ChooserModel(request, new LocalFiles(), Runnable::run, Runnable::run, prefs.showHidden());
        var panel = new FileChooserPanel(model, prefs, finished::add, file -> replace, () -> folderName);
        model.start();
        return panel;
    }

    private static List<String> rows(FileChooserPanel panel) {
        var table = panel.table();
        return IntStream.range(0, table.getRowCount()).mapToObj(row -> ((ChooserEntry) table.getValueAt(row, 0)).name()).toList();
    }

    private static void press(JComponent component, int key) {
        Object name = component.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0));
        component.getActionMap().get(name).actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test void enterOnAFolderEntersItAndEnterOnAFileOpensIt() {
        var panel = panel(OPEN, Optional.empty());
        assertThat(rows(panel)).containsExactly("docs", "notes.txt", "todo.md");
        panel.table().setRowSelectionInterval(0, 0);
        press(panel.table(), KeyEvent.VK_ENTER);
        assertThat(rows(panel)).isEmpty();
        assertThat(panel.centerMessage()).contains("This folder is empty.");
        press(panel.table(), KeyEvent.VK_BACK_SPACE);
        panel.table().setRowSelectionInterval(1, 1);
        assertThat(panel.nameField().getText()).isEqualTo("notes.txt");
        press(panel.table(), KeyEvent.VK_ENTER);
        assertThat(finished).containsExactly(List.of(dir.resolve("notes.txt")));
        assertThat(prefs.recent()).containsExactly(dir);
    }

    @Test void theNameFieldAcceptsPathsAndReportsProblems() {
        var panel = panel(OPEN, Optional.empty());
        panel.nameField().setText("docs");
        panel.approve();
        assertThat(panel.pathText()).endsWith("docs");
        panel.nameField().setText("../missing.txt");
        panel.approve();
        assertThat(panel.statusText()).isEqualTo("There is no file named \"../missing.txt\".");
        panel.nameField().setText("../todo.md");
        panel.approve();
        assertThat(finished).containsExactly(List.of(dir.resolve("todo.md")));
    }

    @Test void saveSelectsTheNamePartAndAsksBeforeReplacing() {
        var panel = panel(SAVE, Optional.of("notes.txt"));
        assertThat(panel.nameField().getSelectedText()).isEqualTo("notes");
        assertThat(panel.primaryButton().getText()).isEqualTo("Save");
        replace = false;
        panel.approve();
        assertThat(finished).isEmpty();
        replace = true;
        panel.approve();
        assertThat(finished).containsExactly(List.of(dir.resolve("notes.txt")));
    }

    @Test void newFolderCreatesAndSelectsIt() {
        folderName = Optional.of("reports");
        var panel = panel(SAVE, Optional.empty());
        panel.newFolder();
        assertThat(dir.resolve("reports")).isDirectory();
        assertThat(panel.table().getSelectedRow()).isEqualTo(rows(panel).indexOf("reports"));
    }

    @Test void cancelFinishesOnceWithNothing() {
        var panel = panel(OPEN, Optional.empty());
        panel.cancel();
        panel.cancel();
        assertThat(finished).containsExactly(List.of());
    }

    @Test void theNavigationPaneListsPlacesAndPinnedFolders() {
        var panel = panel(OPEN, Optional.empty());
        var items = panel.navigation().getModel();
        assertThat(items.getElementAt(0)).isEqualTo("Places");
        assertThat(((Places.Place) items.getElementAt(1)).label()).isEqualTo("Terminal folder");
        assertThat(panel.navigation().getSelectedValue()).as("the folder shown is highlighted").isEqualTo(items.getElementAt(1));
        panel.togglePin(dir.resolve("docs"));
        assertThat(prefs.pinned()).containsExactly(dir.resolve("docs"));
        Object pinned = IntStream.range(0, items.getSize()).mapToObj(items::getElementAt)
            .filter(item -> item instanceof Places.Place place && place.section() == Places.Section.PINNED).findFirst().orElseThrow();
        panel.navigation().setSelectedValue(pinned, false);
        assertThat(panel.pathText()).endsWith("docs");
    }

    @Test void theHiddenFilesToggleIsRememberedAndTypingSelects() throws IOException {
        Files.writeString(dir.resolve(".env"), "");
        var panel = panel(OPEN, Optional.empty());
        assertThat(rows(panel)).contains(".env");
        panel.toggleHidden();
        assertThat(rows(panel)).doesNotContain(".env");
        assertThat(prefs.showHidden()).isFalse();
        panel.typeToSelect('t');
        assertThat(panel.nameField().getText()).isEqualTo("todo.md");
    }

    @Test void thePreviewPaneShowsTheStartOfTheSelectedFile() {
        var panel = panel(OPEN, Optional.empty());
        panel.previewToggle().doClick(0);
        assertThat(prefs.preview()).isTrue();
        panel.table().setRowSelectionInterval(1, 1);
        assertThat(panel.previewText()).isEqualTo("hello\n");
    }

    @Test void thePathBarAcceptsATypedFolder() {
        var panel = panel(OPEN, Optional.empty());
        panel.editPath(dir.resolve("docs").toString());
        assertThat(panel.pathText()).endsWith("docs");
        panel.editPath(dir.resolve("nowhere").toString());
        assertThat(panel.statusText()).startsWith("There is no folder at ");
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void fileNamesAreShownAsPlainText() throws IOException {
        Files.writeString(dir.resolve("<html><b>x"), "");
        var panel = panel(OPEN, Optional.empty());
        int row = rows(panel).indexOf("<html><b>x");
        var cell = (JLabel) panel.table().prepareRenderer(panel.table().getCellRenderer(row, 0), row, 0);
        assertThat(cell.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(cell.getText()).isEqualTo("<html><b>x");
    }
}

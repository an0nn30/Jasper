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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private final List<List<Location>> finished = new ArrayList<>();
    private final List<CompletableFuture<Browser>> connects = new ArrayList<>();
    private final List<Runnable> sourceListeners = new ArrayList<>();
    private boolean replace;
    private boolean hostConnected;
    private Optional<String> folderName = Optional.empty();

    @BeforeEach void tree() throws IOException {
        Files.createDirectory(dir.resolve("docs"));
        Files.writeString(dir.resolve("notes.txt"), "hello\n");
        Files.writeString(dir.resolve("todo.md"), "- one\n");
    }

    private Location here(String name) { return Location.local(dir.resolve(name)); }

    private ChooserSource hosts() {
        return new ChooserSource() {
            @Override public String id() { return "dev.x.hosts"; }
            @Override public String heading() { return "Remote"; }
            @Override public List<Root> roots() { return List.of(new Root("box", "Box", "me@box:22", hostConnected)); }
            @Override public Runnable onChanged(Runnable listener) { sourceListeners.add(listener); return () -> sourceListeners.remove(listener); }
            @Override public CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow) {
                var attempt = new CompletableFuture<Browser>();
                connects.add(attempt);
                return attempt;
            }
        };
    }

    private FileChooserPanel panel(ChooserRequest.Mode mode, Optional<String> name, List<ChooserSource> extra) {
        var request = new ChooserRequest("Choose", mode, false, Location.local(dir), Optional.of(Location.local(dir)), name,
            Optional.empty(), Optional.empty());
        var sources = new ArrayList<ChooserSource>();
        sources.add(new LocalSource(new LocalBrowser(dir)));
        sources.addAll(extra);
        var model = new ChooserModel(request, sources, Runnable::run, Runnable::run, prefs.showHidden());
        var panel = new FileChooserPanel(model, prefs, dir, finished::add, file -> replace, () -> folderName);
        model.start();
        return panel;
    }

    private FileChooserPanel panel(ChooserRequest.Mode mode, Optional<String> name) { return panel(mode, name, List.of()); }

    private static List<String> rows(FileChooserPanel panel) {
        var table = panel.table();
        return IntStream.range(0, table.getRowCount()).mapToObj(row -> ((ChooserEntry) table.getValueAt(row, 0)).name()).toList();
    }

    private static void press(JComponent component, int key) {
        Object name = component.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0));
        component.getActionMap().get(name).actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
    }

    private static Places.Place place(FileChooserPanel panel, String label) {
        var items = panel.navigation().getModel();
        return IntStream.range(0, items.getSize()).mapToObj(items::getElementAt)
            .filter(item -> item instanceof Places.Place place && place.label().equals(label)).map(Places.Place.class::cast)
            .findFirst().orElseThrow();
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
        assertThat(finished).containsExactly(List.of(here("notes.txt")));
        assertThat(prefs.recent()).containsExactly(Location.local(dir));
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
        assertThat(finished).containsExactly(List.of(here("todo.md")));
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
        assertThat(finished).containsExactly(List.of(here("notes.txt")));
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
        panel.togglePin(here("docs"));
        assertThat(prefs.pinned()).containsExactly(here("docs"));
        panel.navigation().setSelectedValue(place(panel, "docs"), false);
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

    @Test void aSourceRootConnectsWhenOpenedAndOffersRetryWhenItFails() throws IOException {
        Path remote = Files.createDirectory(dir.resolve("remote-home"));
        Files.writeString(remote.resolve("r.txt"), "r");
        var panel = panel(OPEN, Optional.empty(), List.of(hosts()));
        var items = panel.navigation().getModel();
        assertThat(IntStream.range(0, items.getSize()).mapToObj(items::getElementAt)).contains("Remote");
        assertThat(place(panel, "Box").connected()).isFalse();
        hostConnected = true;
        sourceListeners.forEach(Runnable::run);
        assertThat(place(panel, "Box").connected()).as("the pane follows the source").isTrue();
        panel.navigation().setSelectedValue(place(panel, "Box"), false);
        assertThat(panel.centerMessage()).contains("Connecting to Box\u2026");
        assertThat(panel.centerAction()).contains("Cancel connection");
        connects.getLast().completeExceptionally(new IOException("offline"));
        assertThat(panel.centerMessage()).contains("Couldn't connect to Box: offline");
        assertThat(panel.centerAction()).contains("Retry");
        panel.pressCenterAction();
        connects.getLast().complete(new LocalBrowser(remote));
        assertThat(rows(panel)).containsExactly("r.txt");
        assertThat(panel.pathText()).startsWith("Box");
        panel.table().setRowSelectionInterval(0, 0);
        panel.approve();
        assertThat(finished).containsExactly(List.of(new Location("dev.x.hosts", "box", remote.resolve("r.txt").toString())));
        panel.close();
        assertThat(sourceListeners).isEmpty();
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

    @Test void directoryModeSaysWhichFolderChooseWillPick() {
        var panel = panel(DIRECTORY, Optional.empty());
        assertThat(panel.primaryButton().getText()).isEqualTo("Choose");
        assertThat(rows(panel)).containsExactly("docs");
        assertThat(panel.statusText()).endsWith("Choose: " + dir.getFileName());
        panel.table().setRowSelectionInterval(0, 0);
        assertThat(panel.statusText()).endsWith("Choose: docs");
        panel.approve();
        assertThat(finished).containsExactly(List.of(here("docs")));
    }
}

package dev.jasper.editor.ui;

import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.local.LocalFiles;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.sdk.Variant;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class EditorTabTest {
    @TempDir Path folder;
    private final List<Runnable> background = new ArrayList<>();
    private final SessionEnvironment environment = new SessionEnvironment(background::add, Runnable::run, Question::dismissed, () -> 1 << 20);
    private final List<EditorTab> closed = new ArrayList<>();

    @BeforeEach void languages() { Languages.register(); }

    private void drain() { while (!background.isEmpty()) background.removeFirst().run(); }

    private EditorTab open(Path file) {
        var tab = new EditorTab(new DocumentSession(new LocalDocument(file, background::add), environment), closed::add, false);
        tab.session().load();
        return tab;
    }

    @Test void showsLoadingThenTheTextInItsLanguage() throws Exception {
        var tab = open(Files.writeString(folder.resolve("a.toml"), "a = 1\n"));
        assertThat(tab.showingText()).isFalse();
        assertThat(tab.stateText()).isEqualTo("Loading a.toml…");
        assertThat(tab.dismissButton().getText()).isEqualTo("Cancel");
        drain();
        assertThat(tab.showingText()).isTrue();
        assertThat(tab.area().getText()).isEqualTo("a = 1\n");
        assertThat(tab.area().getSyntaxEditingStyle()).isEqualTo(Languages.TOML);
        assertThat(tab.area().canUndo()).as("loading is not an edit").isFalse();
        tab.area().append("b = 2\n");
        assertThat(tab.session().dirty()).isTrue();
    }

    @Test void aFailureShowsRetryAndCloseAndABinaryFileHasNoRetry() throws Exception {
        var missing = open(folder.resolve("gone.txt"));
        drain();
        assertThat(missing.stateText()).isEqualTo("gone.txt does not exist.");
        assertThat(missing.retryButton().isVisible()).isTrue();
        missing.dismissButton().doClick();
        assertThat(closed).containsExactly(missing);
        var binary = open(Files.write(folder.resolve("x.bin"), new byte[] {1, 0, 2}));
        drain();
        assertThat(binary.retryButton().isVisible()).isFalse();
    }

    @Test void theBannerOffersReloadOrKeepMineAndCloseOrKeep() throws Exception {
        Path file = Files.writeString(folder.resolve("b.yaml"), "a: 1\n");
        var tab = open(file);
        drain();
        tab.area().setText("a: mine\n");
        Files.writeString(file, "a: 22\n");
        tab.session().externalChange(LocalFiles.revision(file));
        assertThat(tab.bannerVisible()).isTrue();
        assertThat(tab.bannerText()).isEqualTo("b.yaml changed on disk.");
        assertThat(tab.bannerFirst().getText()).isEqualTo("Reload");
        assertThat(tab.bannerSecond().getText()).isEqualTo("Keep Mine");
        tab.bannerFirst().doClick();
        drain();
        assertThat(tab.area().getText()).isEqualTo("a: 22\n");
        assertThat(tab.bannerVisible()).isFalse();
        Files.delete(file);
        tab.session().externalChange(Optional.empty());
        assertThat(tab.bannerText()).isEqualTo("b.yaml was deleted.");
        tab.bannerSecond().doClick();
        assertThat(tab.session().dirty()).isTrue();
        assertThat(tab.bannerVisible()).isFalse();
        tab.session().externalChange(Optional.empty());
        assertThat(closed).isEmpty();
    }

    @Test void commandsOverrideTheTextAreasOwnBindingsAndJoinItsMenu() {
        var tab = new EditorTab(DocumentSession.untitled("Untitled", environment), closed::add, false);
        int[] ran = {0};
        var saveAs = KeyNames.stroke(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK);
        tab.bind(List.of(new Command("save-as", "Save As…", Optional.of(saveAs), true, () -> ran[0]++),
            new Command("close-tab", "Close Tab", Optional.of(KeyNames.stroke(KeyEvent.VK_W, 0)), false, () -> ran[0] += 10)));
        Object key = tab.area().getInputMap(JComponent.WHEN_FOCUSED).get(saveAs);
        assertThat(key).as("RSyntaxTextArea's own binding for this stroke is overridden").isEqualTo("save-as");
        tab.area().getActionMap().get(key).actionPerformed(new ActionEvent(tab.area(), ActionEvent.ACTION_PERFORMED, "save-as"));
        assertThat(ran[0]).isEqualTo(1);
        assertThat(tab.area().getInputMap(JComponent.WHEN_FOCUSED).get(KeyNames.stroke(KeyEvent.VK_W, 0))).as("unbound commands stay out").isNotEqualTo("close-tab");
        tab.addToPopup(List.of(new Command("save", "Save", Optional.empty(), true, () -> ran[0]++)));
        assertThat(Arrays.stream(tab.area().getPopupMenu().getComponents()).filter(JMenuItem.class::isInstance)
            .map(item -> ((JMenuItem) item).getText())).contains("Save");
    }

    @Test void settingsApplyTheFontAndTabSize() {
        var tab = new EditorTab(DocumentSession.untitled("Untitled", environment), closed::add, false);
        tab.apply(new EditorSettings("Monospaced", 17, 2, false, 10), new EditorTheme(), Variant.DARK);
        assertThat(tab.area().getFont().getSize()).isEqualTo(17);
        assertThat(tab.area().getTabSize()).isEqualTo(2);
    }
}

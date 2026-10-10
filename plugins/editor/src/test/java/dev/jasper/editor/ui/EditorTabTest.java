package dev.jasper.editor.ui;

import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.local.LocalFiles;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.sdk.ui.SyntaxRole;
import java.awt.Font;
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
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class EditorTabTest {
    /** A component without a peer is never validated, so lays each container out top-down, twice for scroll panes. */
    private static void layOut(java.awt.Component component) {
        for (int pass = 0; pass < 2; pass++) layOutOnce(component);
    }

    private static void layOutOnce(java.awt.Component component) {
        if (!(component instanceof java.awt.Container container)) return;
        container.doLayout();
        for (var child : container.getComponents()) layOutOnce(child);
    }

    /** Without a peer RSyntaxTextArea cannot take its first font metrics from its own graphics, so gives it one. */
    // Pins private members of RSyntaxTextArea 3.6.0 (refreshFontMetrics, metricsNeverRefreshed).
    private static void measure(EditorTab tab) {
        try {
            var image = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var refresh = org.fife.ui.rsyntaxtextarea.RSyntaxTextArea.class.getDeclaredMethod("refreshFontMetrics", java.awt.Graphics2D.class);
            refresh.setAccessible(true);
            refresh.invoke(tab.area(), image.createGraphics());
            var flag = org.fife.ui.rsyntaxtextarea.RSyntaxTextArea.class.getDeclaredField("metricsNeverRefreshed");
            flag.setAccessible(true);
            flag.setBoolean(tab.area(), false);
        } catch (ReflectiveOperationException failed) {
            throw new AssertionError(failed);
        }
    }

    @Test void translucentWorkspaceLetsTheGlassSurfaceFillTextAndGutterOnceAtTheWorkspaceOpacity() throws Exception {
        dev.jasper.editor.testsupport.Kits.edt(() -> {
            var tab = new EditorTab(new dev.jasper.sdk.testing.FakeKit("dev.jasper.editor"), DocumentSession.untitled("Untitled", environment), closed::add, false);
            var settings = new EditorSettings("Monospaced", 17, 2, false, 10, false);
            var syntax = EditorThemeTest.syntax(0x223300);
            tab.apply(settings, new EditorTheme(), EditorThemeTest.EDITOR, syntax, 0.5);
            tab.surface().setSize(600, 400);
            layOut(tab.surface());
            measure(tab);
            var image = new java.awt.image.BufferedImage(600, 400, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            tab.surface().paint(g);
            g.dispose();
            var view = tab.scroll().getViewport();
            var inArea = javax.swing.SwingUtilities.convertPoint(view, view.getWidth() - 20, view.getHeight() - 20, tab.surface());
            int text = image.getRGB(inArea.x, inArea.y);
            assertThat(text >>> 24).isEqualTo(128);
            assertThat(text & 0xFFFFFF).isEqualTo(EditorThemeTest.EDITOR.background().getRGB() & 0xFFFFFF);
            var header = tab.scroll().getRowHeader();
            var inGutter = javax.swing.SwingUtilities.convertPoint(header, header.getWidth() / 2, header.getHeight() - 20, tab.surface());
            int gutter = image.getRGB(inGutter.x, inGutter.y);
            assertThat(gutter >>> 24).isEqualTo(128);
            assertThat(gutter & 0xFFFFFF).isEqualTo(EditorThemeTest.EDITOR.gutter().getRGB() & 0xFFFFFF);
            // The gutter's border line at its right edge must be glass too, not a solid stripe.
            var inEdge = javax.swing.SwingUtilities.convertPoint(header, header.getWidth() - 1, header.getHeight() - 20, tab.surface());
            int edge = image.getRGB(inEdge.x, inEdge.y);
            assertThat(edge >>> 24).isEqualTo(128);
            assertThat(edge & 0xFFFFFF).isEqualTo(EditorThemeTest.EDITOR.gutter().getRGB() & 0xFFFFFF);

            tab.apply(settings, new EditorTheme(), EditorThemeTest.EDITOR, syntax, 1.0);
            layOut(tab.surface());
            var opaque = new java.awt.image.BufferedImage(600, 400, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g2 = opaque.createGraphics();
            tab.surface().paint(g2);
            g2.dispose();
            assertThat(opaque.getRGB(inArea.x, inArea.y) >>> 24).isEqualTo(255);
            tab.dispose();
        });
    }

    @TempDir Path folder;
    private final List<Runnable> background = new ArrayList<>();
    private final SessionEnvironment environment = new SessionEnvironment(background::add, Runnable::run, Question::dismissed, () -> 1 << 20);
    private final List<EditorTab> closed = new ArrayList<>();

    private static dev.jasper.sdk.ui.Nodes.Button button(EditorTab tab, String id) { return dev.jasper.editor.testsupport.Kits.button(tab.view(), id); }
    private static String stateText(EditorTab tab) { return dev.jasper.editor.testsupport.Kits.node(tab.view(), EditorTab.STATE, dev.jasper.sdk.ui.Nodes.Empty.class).title(); }
    private static void click(EditorTab tab, String id) { dev.jasper.editor.testsupport.Kits.click(tab.view(), id); }

    @BeforeEach void languages() { Languages.register(); }

    private void drain() { while (!background.isEmpty()) background.removeFirst().run(); }

    private EditorTab open(Path file) {
        var tab = new EditorTab(new dev.jasper.sdk.testing.FakeKit("dev.jasper.editor"), new DocumentSession(new LocalDocument(file, background::add), environment), closed::add, false);
        tab.session().load();
        return tab;
    }

    @Test void theSpecializedDocumentIsRegisteredWhileOrdinaryControlsAreDescriptors() {
        var tab = new EditorTab(new dev.jasper.sdk.testing.FakeKit("dev.jasper.editor"), DocumentSession.untitled("Untitled", environment), closed::add, false);
        assertThat(dev.jasper.editor.testsupport.Kits.node(tab.view(), EditorTab.DOCUMENT, dev.jasper.sdk.ui.Nodes.Custom.class).kind())
            .isEqualTo(dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR);
        assertThat(dev.jasper.editor.testsupport.Kits.node(tab.view(), EditorTab.RETRY, dev.jasper.sdk.ui.Nodes.Button.class).state().visible()).isFalse();
        tab.dispose();
    }

    @Test void focusAskedForWhileLoadingArrivesWhenTheTextShows() throws Exception {
        var tab = open(Files.writeString(folder.resolve("a.txt"), "a\n"));
        var focused = new java.util.concurrent.atomic.AtomicInteger();
        tab.focusAction(focused::incrementAndGet);
        tab.focusText();
        assertThat(focused).as("the text area is hidden while loading").hasValue(0);
        drain();
        assertThat(focused).as("granted once the text shows").hasValue(1);
        tab.session().reload();
        drain();
        assertThat(focused).as("a later reload does not take focus again").hasValue(1);
        tab.focusText();
        assertThat(focused).as("a loaded tab takes focus at once").hasValue(2);
    }

    @Test void aLoadFinishingAfterViewShutdownNeverReplacesTextFocusesOrRefreshesTheView() throws Exception {
        var kit = new dev.jasper.editor.testsupport.Kits.RecordingKit("dev.jasper.editor");
        var session = new DocumentSession(new LocalDocument(Files.writeString(folder.resolve("late.txt"), "late text"), background::add), environment);
        var tab = new EditorTab(kit, session, closed::add, true);
        var focused = new java.util.concurrent.atomic.AtomicInteger();
        tab.focusAction(focused::incrementAndGet);
        session.load();tab.focusText();tab.view().close();
        drain();tab.focusText();
        assertThat(tab.area().getText()).isEmpty();
        assertThat(focused).hasValue(0);
        assertThat(kit.lateCalls).isEmpty();
        assertThat(kit.delegate.failures()).isEmpty();
        tab.dispose();
    }

    @Test void aFailedLoadDropsTheFocusRequest() throws Exception {
        var tab = open(folder.resolve("missing.txt"));
        var focused = new java.util.concurrent.atomic.AtomicInteger();
        tab.focusAction(focused::incrementAndGet);
        tab.focusText();
        drain();
        assertThat(tab.showingText()).isFalse();
        assertThat(focused).hasValue(0);
    }

    @Test void showsLoadingThenTheTextInItsLanguage() throws Exception {
        var tab = open(Files.writeString(folder.resolve("a.toml"), "a = 1\n"));
        assertThat(tab.showingText()).isFalse();
        assertThat(stateText(tab)).isEqualTo("Loading a.toml…");
        assertThat(button(tab, EditorTab.DISMISS).label()).isEqualTo("Cancel");
        drain();
        assertThat(tab.showingText()).isTrue();
        assertThat(tab.area().getText()).isEqualTo("a = 1\n");
        assertThat(tab.area().getSyntaxEditingStyle()).isEqualTo(Languages.TOML);
        assertThat(tab.area().canUndo()).as("loading is not an edit").isFalse();
        tab.area().append("b = 2\n");
        assertThat(tab.session().dirty()).isTrue();
    }

    @Test void vimModeShowsTheVimBarAndTurnsOffCleanly() throws Exception {
        var tab = open(Files.writeString(folder.resolve("v.txt"), "one\n"));
        drain();
        assertThat(tab.vimBarVisible()).isFalse();
        tab.vim(Optional.of(new dev.jasper.editor.vim.Vim(dev.jasper.editor.vim.VimHost.detached())));
        assertThat(tab.vimEditor()).isPresent();
        assertThat(tab.vimBarVisible()).isTrue();
        assertThat(tab.area().vim()).isPresent();
        tab.vim(Optional.empty());
        assertThat(tab.vimEditor()).isEmpty();
        assertThat(tab.vimBarVisible()).isFalse();
        assertThat(tab.area().vim()).isEmpty();
        assertThat(tab.area().getText()).isEqualTo("one\n");
    }

    @Test void vimWrapsAtAnyCharacterThroughEditsAndGivesWordWrappingBack() throws Exception {
        var tab = open(Files.writeString(folder.resolve("w.txt"), "one\n"));
        drain();
        assertThat(tab.area().getWrapStyleWord()).isTrue();
        tab.vim(Optional.of(new dev.jasper.editor.vim.Vim(dev.jasper.editor.vim.VimHost.detached())));
        assertThat(tab.area().getWrapStyleWord()).isFalse();
        tab.area().append("two\n");
        assertThat(tab.session().dirty()).as("the edit refreshed the tab").isTrue();
        assertThat(tab.area().getWrapStyleWord()).as("Vim wraps at any character").isFalse();
        tab.vim(Optional.empty());
        assertThat(tab.area().getWrapStyleWord()).isTrue();
    }

    @Test void aFailureShowsRetryAndCloseAndABinaryFileHasNoRetry() throws Exception {
        var missing = open(folder.resolve("gone.txt"));
        drain();
        assertThat(stateText(missing)).isEqualTo("gone.txt does not exist.");
        assertThat(button(missing, EditorTab.RETRY).state().visible()).isTrue();
        click(missing, EditorTab.DISMISS);
        assertThat(closed).containsExactly(missing);
        var binary = open(Files.write(folder.resolve("x.bin"), new byte[] {1, 0, 2}));
        drain();
        assertThat(button(binary, EditorTab.RETRY).state().visible()).isFalse();
    }

    @Test void theBannerOffersReloadOrKeepMineAndCloseOrKeep() throws Exception {
        Path file = Files.writeString(folder.resolve("b.yaml"), "a: 1\n");
        var tab = open(file);
        drain();
        tab.area().setText("a: mine\n");
        Files.writeString(file, "a: 22\n");
        tab.session().externalChange(LocalFiles.revision(file));
        assertThat(dev.jasper.editor.testsupport.Kits.node(tab.view(), EditorTab.BANNER, dev.jasper.sdk.ui.Nodes.Stack.class).state().visible()).isTrue();
        assertThat(dev.jasper.editor.testsupport.Kits.node(tab.view(), "document.banner.text", dev.jasper.sdk.ui.Nodes.Text.class).text()).isEqualTo("b.yaml changed on disk.");
        assertThat(button(tab, EditorTab.FIRST).label()).isEqualTo("Reload");
        assertThat(button(tab, EditorTab.SECOND).label()).isEqualTo("Keep Mine");
        click(tab, EditorTab.FIRST);
        drain();
        assertThat(tab.area().getText()).isEqualTo("a: 22\n");
        assertThat(dev.jasper.editor.testsupport.Kits.node(tab.view(), EditorTab.BANNER, dev.jasper.sdk.ui.Nodes.Stack.class).state().visible()).isFalse();
        Files.delete(file);
        tab.session().externalChange(Optional.empty());
        assertThat(dev.jasper.editor.testsupport.Kits.node(tab.view(), "document.banner.text", dev.jasper.sdk.ui.Nodes.Text.class).text()).isEqualTo("b.yaml was deleted.");
        click(tab, EditorTab.SECOND);
        assertThat(tab.session().dirty()).isTrue();
        assertThat(dev.jasper.editor.testsupport.Kits.node(tab.view(), EditorTab.BANNER, dev.jasper.sdk.ui.Nodes.Stack.class).state().visible()).isFalse();
        tab.session().externalChange(Optional.empty());
        assertThat(closed).isEmpty();
    }

    @Test void commandsOverrideTheTextAreasOwnBindingsAndJoinItsMenu() {
        var tab = new EditorTab(new dev.jasper.sdk.testing.FakeKit("dev.jasper.editor"), DocumentSession.untitled("Untitled", environment), closed::add, false);
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
        var tab = new EditorTab(new dev.jasper.sdk.testing.FakeKit("dev.jasper.editor"), DocumentSession.untitled("Untitled", environment), closed::add, false);
        tab.apply(new EditorSettings("Monospaced", 17, 2, false, 10, false), new EditorTheme(),
            EditorThemeTest.EDITOR, EditorThemeTest.syntax(0x223300));
        assertThat(tab.area().getFont().getSize()).isEqualTo(17);
        assertThat(tab.area().getTabSize()).isEqualTo(2);
    }

    @Test void workspaceAndFontChangesKeepDirtyDocumentAndUpdateStyledTokenFonts() throws Exception {
        var tab = open(Files.writeString(folder.resolve("colors.txt"), "original\n"));
        drain();
        var first = new EditorSettings("Monospaced", 17, 2, false, 10, false);
        var syntax = EditorThemeTest.syntax(0x223300);
        tab.apply(first, new EditorTheme(), EditorThemeTest.EDITOR, syntax);
        tab.area().append("changed");
        tab.area().select(2, 5);
        var document = tab.area().getDocument();
        var second = new EditorSettings("Monospaced", 22, 4, false, 10, false);
        var next = EditorThemeTest.syntax(0x334400);
        tab.apply(second, new EditorTheme(), EditorThemeTest.EDITOR, next);
        assertThat(tab.area().getDocument()).isSameAs(document);
        assertThat(tab.area().getText()).isEqualTo("original\nchanged");
        assertThat(tab.area().getSelectionStart()).isEqualTo(2);
        assertThat(tab.area().getSelectionEnd()).isEqualTo(5);
        assertThat(tab.area().canUndo()).isTrue();
        assertThat(tab.session().dirty()).isTrue();
        assertThat(tab.area().getTabSize()).isEqualTo(4);
        var style = tab.area().getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD);
        assertThat(style.foreground).isEqualTo(next.style(SyntaxRole.KEYWORD).color());
        assertThat(style.font.getSize()).isEqualTo(22);
        assertThat(style.font.getStyle()).isEqualTo(Font.BOLD | Font.ITALIC);
    }
}

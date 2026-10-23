package dev.jasper.editor.ui;

import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.editor.session.*;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.testsupport.Kits;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.editor.markdown.MarkdownMode.*;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.*;

@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class MarkdownModesTest {
    @TempDir Path home;
    final List<Runnable> jobs = new ArrayList<>();
    final SessionEnvironment sessions = new SessionEnvironment(jobs::add, Runnable::run, Question::dismissed, () -> 1 << 20);
    final AtomicReference<MarkdownMode> saved = new AtomicReference<>(SOURCE);
    final List<MarkdownMode> writes = new ArrayList<>();
    final MarkdownPreferences preferences = new MarkdownPreferences(saved::get, mode -> { writes.add(mode); saved.set(mode); return CompletableFuture.completedFuture(null); });
    final List<EditorTab> tabs = new ArrayList<>();
    @BeforeEach void register() { Languages.register(); }
    @AfterEach void close() { tabs.forEach(EditorTab::dispose); }
    EditorTab untitled() {
        var tab = new EditorTab(new FakeKit("dev.jasper.editor"), DocumentSession.untitled("Untitled", sessions), t -> {}, false);
        tab.previews(jobs::add, Runnable::run); tab.markdownPreferences(preferences); tabs.add(tab); return tab;
    }
    @Test void newEligibilityUsesDefaultExistingTabsRemainIndependentAndSyntaxRoundTripRestoresMode() {
        var first = untitled(); first.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        assertThat(first.markdown().mode()).isEqualTo(SOURCE);
        first.markdownMode(PREVIEW, true);
        var second = untitled(); second.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        assertThat(second.markdown().mode()).isEqualTo(PREVIEW);
        first.markdownMode(SPLIT, true);
        assertThat(second.markdown().mode()).isEqualTo(PREVIEW);
        second.session().setStyle(SYNTAX_STYLE_JAVA); assertThat(second.markdown().mode()).isEqualTo(SOURCE);
        second.session().setStyle(SYNTAX_STYLE_MARKDOWN); assertThat(second.markdown().mode()).isEqualTo(PREVIEW);
        saved.set(SOURCE);
        assertThat(second.markdown().mode()).isEqualTo(PREVIEW);
        var third = untitled(); third.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        assertThat(third.markdown().mode()).isEqualTo(SOURCE);
        first.markdownMode(SPLIT, true); assertThat(writes).containsExactly(PREVIEW, SPLIT, SPLIT);
    }
    @Test void toolbarComesFirstCollapsesAndDisablesPendingModeActions() throws Exception {
        var tab = untitled();
        assertThat(((Nodes.Split)tab.view().spec().root()).children().getFirst().id()).isEqualTo("markdown.toolbar");
        assertThat(Kits.node(tab.view(), "markdown.toolbar", Nodes.Stack.class).state().visible()).isFalse();
        tab.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        assertThat(Kits.node(tab.view(), "markdown.toolbar", Nodes.Stack.class).state().visible()).isTrue();
        var file = Files.writeString(home.resolve("pending.md"), "# Pending");
        var pending = new EditorTab(new FakeKit("dev.jasper.editor"), new DocumentSession(new LocalDocument(file, jobs::add), sessions), t -> {}, false);
        tabs.add(pending); pending.previews(jobs::add, Runnable::run); pending.markdownPreferences(preferences); pending.session().load();
        assertThat(Kits.button(pending.view(), "markdown.preview").state().enabled()).isFalse();
        Kits.press(pending.view(), "markdown.preview"); assertThat(writes).isEmpty();
    }
    @Test void temporaryRevealPreservesDocumentUndoSelectionAndDoesNotSave() {
        var tab = untitled(); tab.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        tab.area().setText("# heading\nbody"); tab.area().discardAllEdits(); tab.area().append("!"); tab.area().select(2, 6);
        var doc = tab.area().getDocument();
        tab.markdownMode(PREVIEW, true); tab.revealSource();
        assertThat(tab.markdown().mode()).isEqualTo(SPLIT);
        assertThat(tab.area().getDocument()).isSameAs(doc); assertThat(tab.area().getSelectedText()).isEqualTo("head");
        tab.area().undoLastAction(); assertThat(tab.area().getText()).isEqualTo("# heading\nbody");
        assertThat(writes).containsExactly(PREVIEW);
        tab.markdownMode(PREVIEW, false);
        var status = new StatusLine(() -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {}); status.display(Optional.of(tab), 4, false, dev.jasper.editor.vim.Mode.NORMAL);
        assertThat(status.positionText()).startsWith("Source Ln ");
    }
    @Test void focusTargetsSurviveModeSwitchesAndRememberTheLastSplitPane() {
        var tab = untitled(); tab.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        tab.markdownMode(PREVIEW, true);
        assertThat(tab.focusedContent()).isSameAs(tab.surface().preview().editor());
        tab.focusContent();
        tab.markdownMode(SPLIT, true);
        assertThat(tab.focusedContent()).isSameAs(tab.area());
        var preview = tab.surface().preview().editor();
        for (var listener : preview.getFocusListeners()) listener.focusGained(new java.awt.event.FocusEvent(preview, java.awt.event.FocusEvent.FOCUS_GAINED));
        tab.active(false); tab.active(true); tab.focusContent();
        assertThat(tab.focusedContent()).isSameAs(preview);
        tab.markdownMode(SOURCE, true);
        assertThat(tab.focusedContent()).isSameAs(tab.area());
    }
    @Test void vimAndEditsKeepTheModeAndExistingEditorState() {
        var tab = untitled(); tab.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        { var vim = new dev.jasper.editor.vim.Vim(dev.jasper.editor.vim.VimHost.detached());
            tab.vim(Optional.of(vim)); var editor = tab.vimEditor().orElseThrow();
            var mode = editor.mode(); var document = tab.area().getDocument();
            tab.area().setText("# Heading"); tab.area().discardAllEdits(); tab.area().setCaretPosition(4);
            tab.markdownMode(PREVIEW, true); tab.area().append("!");
            assertThat(tab.markdown().mode()).isEqualTo(PREVIEW);
            tab.markdownMode(SOURCE, true);
            assertThat(tab.vimEditor()).containsSame(editor); assertThat(editor.mode()).isEqualTo(mode);
            assertThat(tab.area().getDocument()).isSameAs(document);
            tab.area().undoLastAction(); assertThat(tab.area().getText()).isEqualTo("# Heading");
            tab.vim(Optional.empty());
        }
    }
    @Test void previewKeyboardMutatorsNeverTouchSourceOrUndoHistory() {
        var tab = untitled(); tab.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        tab.area().setText("keep source"); tab.area().discardAllEdits(); tab.area().append("!");
        tab.markdownMode(PREVIEW, true);
        var preview = tab.surface().preview().editor();
        preview.setText("rendered text"); preview.selectAll();
        for (String name : List.of("cut-to-clipboard", "paste-from-clipboard", "delete-next", "delete-previous", "undo", "redo")) {
            var action = preview.getActionMap().get(name);
            if (action != null) action.actionPerformed(new java.awt.event.ActionEvent(preview, 0, name));
        }
        assertThat(tab.area().getText()).isEqualTo("keep source!");
        assertThat(tab.markdown().mode()).isEqualTo(PREVIEW);
        tab.area().undoLastAction(); assertThat(tab.area().getText()).isEqualTo("keep source");
    }

    @Test void sourceViewportDividerAndLinkedToggleSurviveModeChanges() {
        var tab = untitled(); tab.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        tab.area().setText("a source line\n".repeat(500));
        var surface = tab.surface(); surface.setSize(800, 400); layout(surface);
        tab.scroll().getViewport().setViewPosition(new java.awt.Point(0, 600));
        var position = tab.scroll().getViewport().getViewPosition();
        tab.markdownMode(SPLIT, true); layout(surface);
        var split = (javax.swing.JSplitPane) surface.getComponent(0); split.setDividerLocation(250);
        var ratio = surface.dividerRatio();
        assertThat(tab.linkedScrolling()).isTrue(); Kits.press(tab.view(), "markdown.scroll-sync");
        assertThat(tab.linkedScrolling()).isFalse();
        tab.markdownMode(PREVIEW, true); tab.markdownMode(SOURCE, false); layout(surface);
        assertThat(tab.scroll().getViewport().getViewPosition()).isEqualTo(position);
        tab.markdownMode(SPLIT, false); surface.setSize(1100, 400); layout(surface);
        assertThat(surface.dividerRatio()).isCloseTo(ratio, within(.001));
        assertThat(tab.linkedScrolling()).isFalse(); assertThat(writes).containsExactly(SPLIT, PREVIEW);
    }
    private static void layout(java.awt.Component component) {
        if (component instanceof java.awt.Container container) {
            for (int i = 0; i < 2; i++) { container.doLayout(); for (var child : container.getComponents()) layout(child); }
        }
    }

}

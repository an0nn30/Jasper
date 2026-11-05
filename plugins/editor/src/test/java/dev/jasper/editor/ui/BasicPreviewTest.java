package dev.jasper.editor.ui;

import dev.jasper.sdk.Variant;
import java.awt.Font;
import java.util.concurrent.CompletionException;
import java.util.regex.PatternSyntaxException;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import org.fife.ui.rtextarea.SearchContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.editor.testsupport.PreviewFixture.layout;
import static org.assertj.core.api.Assertions.*;

/** The Basic preview behind the preview surface: Swing's view of the SWING dialect, whose answers are ready at once. */
class BasicPreviewTest {
    private static final String TEXT = "# Start\n\n" + "paragraph\n\n".repeat(40) + "# Target\n\nfoo and foo\n";
    private DocumentArea area;
    private PreviewSurface surface;

    static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    static SearchContext query(String text, boolean regex) {
        var context = new SearchContext(text);
        context.setRegularExpression(regex);
        context.setSearchForward(true);
        context.setSearchWrap(true);
        return context;
    }

    @BeforeEach void install() throws Exception {
        edt(() -> { area = new DocumentArea(null); area.setFont(new Font("Serif", Font.PLAIN, 16)); surface = area.surface(); });
        // Preparing belongs to the render worker: here, the test thread.
        var prepared = surface.prepare(TEXT, EditorThemeTest.syntax(1));
        assertThat(prepared.swing()).isNotNull();
        var style = new DocumentArea.PreviewStyle(Variant.LIGHT, PreviewPaneTest.TYPE, PreviewPaneTest.palette(false), EditorThemeTest.syntax(1));
        edt(() -> surface.install(prepared, style, area.getFont(), 1));
        PreviewPaneTest.await(area.preview(), prepared.result());
        edt(() -> {
            assertThat(surface.installedResult()).containsSame(prepared.result());
            layout(area.previewScroll, 300, 100);
        });
    }
    @AfterEach void close() throws Exception { edt(area::close); }

    @Test void revealMovesTheCaretToTheHeading() throws Exception {
        edt(() -> {
            var editor = area.preview().editor();
            int heading = area.preview().anchor("target").orElseThrow();
            assertThat(surface.reveal("target")).isTrue();
            assertThat(editor.getCaretPosition()).isEqualTo(heading);
            assertThat(area.previewScroll.getViewport().getViewPosition().y).as("the heading comes into sight").isPositive();
            assertThat(surface.reveal("nowhere")).isFalse();
            assertThat(editor.getCaretPosition()).isEqualTo(heading);
        });
    }

    @Test void blockYIsTheAnchorsTop() throws Exception {
        edt(() -> {
            var editor = area.preview().editor();
            var block = surface.installedResult().orElseThrow().blocks().stream()
                .filter(b -> b.sourceStart() == TEXT.indexOf("# Target")).findFirst().orElseThrow();
            try {
                double top = editor.modelToView2D(area.preview().anchor(block.id()).orElseThrow()).getY();
                assertThat(top).isPositive();
                assertThat(surface.blockY(block.id())).hasValue(top);
                assertThat(surface.endY()).isEqualTo(editor.modelToView2D(editor.getDocument().getLength()).getY());
            } catch (BadLocationException impossible) { throw new AssertionError(impossible); }
            assertThat(surface.blockY("b99999")).isEmpty();
        });
    }

    @Test void markAllCompletesAtOnce() throws Exception {
        edt(() -> {
            var editor = area.preview().editor();
            assertThat(surface.markAll(query("foo", false))).isCompletedWithValue(2);
            assertThat(editor.getHighlighter().getHighlights()).hasSize(2);
            editor.setCaretPosition(0);
            assertThat(surface.find(query("foo", false))).isCompletedWithValue(true);
            assertThat(surface.selectedText()).isCompletedWithValue("foo");
            assertThat(surface.find(query("absent", false))).isCompletedWithValue(false);
            surface.clearFind();
            assertThat(editor.getHighlighter().getHighlights()).isEmpty();
        });
    }

    @Test void aRefusedPatternFailsTheFuture() throws Exception {
        edt(() -> {
            var count = surface.markAll(query("(", true));
            assertThat(count).isCompletedExceptionally();
            assertThatThrownBy(count::join).isInstanceOf(CompletionException.class).hasCauseInstanceOf(PatternSyntaxException.class);
            var found = surface.find(query("(", true));
            assertThat(found).isCompletedExceptionally();
            assertThatThrownBy(found::join).isInstanceOf(CompletionException.class).hasCauseInstanceOf(PatternSyntaxException.class);
        });
    }
}

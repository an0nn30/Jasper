package dev.jasper.editor.ui;

import java.util.Optional;
import javax.swing.SwingUtilities;
import org.fife.ui.rtextarea.SearchContext;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PreviewSearchTest {
    static SearchContext query(String text, boolean regex, boolean word) {
        var c=new SearchContext(text);c.setRegularExpression(regex);c.setWholeWord(word);c.setSearchWrap(true);return c;
    }
    @Test void searchesVisibleCodeAndProseWithoutMarkdownOrSyntheticMarkers() throws Exception {
        var pane=PreviewPaneTest.install(PreviewPaneTest.prepare("# Title\n\n**word** [label](https://example.com)\n\n```java\nint code = 1;\n```\n"));
        SwingUtilities.invokeAndWait(() -> {
            var search=new PreviewSearch(pane);
            assertThat(search.markAll(query("word",false,false))).isEqualTo(1);
            assertThat(search.markAll(query("code",false,false))).isEqualTo(1);
            for(String hidden:java.util.List.of("**", "jasper-link", "https://example.com", "b0"))
                assertThat(search.markAll(query(hidden,false,false))).isZero();
            pane.close();
        });
    }
    @Test void countOptionsWrapAndDirectionsUseRenderedOffsetsAndZeroLengthMakesProgress() throws Exception {
        var pane=PreviewPaneTest.install(PreviewPaneTest.prepare("café cafétéria CAFÉ _café_\n\nfoo foo foo"));
        SwingUtilities.invokeAndWait(() -> {
            var search=new PreviewSearch(pane);var c=query("café",false,true);
            assertThat(search.markAll(c)).isEqualTo(3);
            c.setMatchCase(true);assertThat(search.markAll(c)).isEqualTo(2);
            var f=query("foo",false,false);assertThat(search.markAll(f)).isEqualTo(3);
            pane.editor().setCaretPosition(0);
            assertThat(search.find(f)).isTrue();int first=pane.editor().getSelectionStart();
            search.find(f);int second=pane.editor().getSelectionStart();assertThat(second).isGreaterThan(first);
            f.setSearchForward(false);search.find(f);assertThat(pane.editor().getSelectionStart()).isEqualTo(first);
            search.find(f);assertThat(pane.editor().getSelectionStart()).isGreaterThan(second);
            assertThat(search.markAll(query("(?=foo)|$",true,false))).isZero();
            assertThat(search.find(query("(?=foo)|$",true,false))).isFalse();
            assertThatThrownBy(() -> search.markAll(query("(",true,false))).isInstanceOf(java.util.regex.PatternSyntaxException.class);
            search.clear();assertThat(pane.editor().getHighlighter().getHighlights()).isEmpty();pane.close();
        });
    }
    @Test void expandingLowercaseInsideQueryRetainsOriginalHighlightAndSelectionWidth() throws Exception {
        var pane=PreviewPaneTest.install(PreviewPaneTest.prepare("\u0130x"));
        SwingUtilities.invokeAndWait(() -> {
            assertLiteralMatch(pane,"\u0130",false,"\u0130");
            pane.close();
        });
    }
    @Test void expandingLowercaseBeforeMatchRetainsOriginalHighlightAndSelectionOffsets() throws Exception {
        var pane=PreviewPaneTest.install(PreviewPaneTest.prepare("\u0130 target tail"));
        SwingUtilities.invokeAndWait(() -> {
            assertLiteralMatch(pane,"target",true,"target");
            pane.close();
        });
    }
    @Test void expandingLowercaseBeforeFinalMatchDoesNotDiscardIt() throws Exception {
        var pane=PreviewPaneTest.install(PreviewPaneTest.prepare("\u0130 target"));
        SwingUtilities.invokeAndWait(() -> {
            assertLiteralMatch(pane,"target",true,"target");
            pane.close();
        });
    }
    @Test void rejectedWholeWordCandidateCanOverlapTheLaterValidMatch() throws Exception {
        var pane=PreviewPaneTest.install(PreviewPaneTest.prepare("xa-a-a"));
        SwingUtilities.invokeAndWait(() -> {
            assertLiteralMatch(pane,"a-a",true,"a-a",true);
            pane.close();
        });
    }
    @Test void rejectedWholeWordCandidateRetainsOriginalUnicodeOffsets() throws Exception {
        var pane=PreviewPaneTest.install(PreviewPaneTest.prepare("\u0130 x\u0130-\u0130-\u0130"));
        SwingUtilities.invokeAndWait(() -> {
            assertLiteralMatch(pane,"\u0130-\u0130",true,"\u0130-\u0130",true);
            pane.close();
        });
    }
    @Test void acceptedWholeWordMatchesStillAdvanceWithoutOverlapping() throws Exception {
        var pane=PreviewPaneTest.install(PreviewPaneTest.prepare("a-a-a"));
        SwingUtilities.invokeAndWait(() -> {
            assertLiteralMatch(pane,"a-a",true,"a-a");
            pane.close();
        });
    }
    private static void assertLiteralMatch(PreviewPane pane,String needle,boolean word,String selected) {
        assertLiteralMatch(pane,needle,word,selected,false);
    }
    private static void assertLiteralMatch(PreviewPane pane,String needle,boolean word,String selected,boolean last) {
        var search=new PreviewSearch(pane);var context=query(needle,false,word);
        final String text;
        try {text=pane.editor().getDocument().getText(0,pane.editor().getDocument().getLength());}
        catch(javax.swing.text.BadLocationException impossible){throw new AssertionError(impossible);}
        int expected=last?text.lastIndexOf(selected):text.indexOf(selected);assertThat(expected).isNotNegative();
        assertThat(search.markAll(context)).isEqualTo(1);
        var highlights=pane.editor().getHighlighter().getHighlights();assertThat(highlights).hasSize(1);
        assertThat(highlights[0].getStartOffset()).isEqualTo(expected);
        assertThat(highlights[0].getEndOffset()).isEqualTo(expected+selected.length());
        pane.editor().setCaretPosition(0);assertThat(search.find(context)).isTrue();
        assertThat(pane.editor().getSelectionStart()).isEqualTo(expected);
        assertThat(pane.editor().getSelectionEnd()).isEqualTo(expected+selected.length());
        assertThat(pane.editor().getSelectedText()).isEqualTo(selected);
    }

    @Test void openFindRecomputesMatchesOnlyWhenTheReplacementDocumentIsInstalled() throws Exception {
        var pane=PreviewPaneTest.install(PreviewPaneTest.prepare("old old"));var bar=new FindBar[1];
        SwingUtilities.invokeAndWait(() -> {
            bar[0]=new FindBar(() -> Optional.of(new FindTarget.Preview(pane)), () -> {}, id -> {});
            bar[0].open(false);bar[0].event(new dev.jasper.sdk.ui.UiEvent.Edited(FindBar.FIND,"old"));
            assertThat(bar[0].status()).isEqualTo("2 matches");
        });
        var next=PreviewPaneTest.prepare("old new new");
        SwingUtilities.invokeAndWait(() -> pane.install(next,PreviewPaneTest.TYPE,PreviewPaneTest.palette(false),EditorThemeTest.syntax(1),new java.awt.Font("Serif",0,12),1));
        PreviewPaneTest.await(pane,next.result());
        SwingUtilities.invokeAndWait(() -> {
            assertThat(bar[0].status()).isEqualTo("1 match");
            assertThat(pane.editor().getHighlighter().getHighlights()).hasSize(1);
            bar[0].close();assertThat(pane.editor().getHighlighter().getHighlights()).isEmpty();pane.close();
        });
    }
}

package dev.jasper.editor.textview;

import dev.jasper.sdk.ui.TerminalTypography;
import java.awt.Color;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LineLayoutTest {
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    static EditorFonts fonts(double multiplier) {
        return new EditorFonts(new TerminalTypography("Monospaced", 13.5, List.of(), true, multiplier), FRC);
    }
    static List<LineLayout.Row> rows(String text, float width, boolean wrap, boolean word, double height) {
        return LineLayout.layout(text, 17, List.of(new LineLayout.Span(17, 17 + text.length(), Font.PLAIN, Color.BLACK)),
            fonts(height), width, wrap, word, 4);
    }
    @Test void wrappedRowsRetainEverySourceOffset() {
        var graphics = new java.awt.image.BufferedImage(300, 200, 2).createGraphics();
        try {
            var fonts = new EditorFonts(new TerminalTypography("Monospaced", 13.5, List.of(), true, 1.5), graphics.getFontRenderContext());
            String source = "alpha beta gamma delta";
            var rows = LineLayout.layout(source, 0, List.of(new LineLayout.Span(0, source.length(), Font.PLAIN, Color.BLACK)), fonts, 50, true, true, 4);
            assertTrue(rows.size() > 1);
            assertEquals(0, rows.getFirst().start());
            assertEquals(source.length(), rows.getLast().end());
            for (int i = 1; i < rows.size(); i++) assertEquals(rows.get(i - 1).end(), rows.get(i).start());
        } finally { graphics.dispose(); }
    }
    @Test void uniformMetricsMultiplyOnceAndKeepFractionalSize() {
        for (double multiplier : new double[]{.5, 1, 1.5, 3}) {
            var f = fonts(multiplier);
            assertEquals(13.5f, f.primary().getSize2D());
            assertEquals(Math.max(1, (int)Math.ceil(f.naturalHeight() * multiplier)), f.height());
            var rows = rows("a\tbold", 500, false, false, multiplier);
            assertEquals(f.height(), rows.getFirst().height());
            assertEquals(f.baseline(), rows.getFirst().baseline());
        }
    }
    @Test void hitsAndWrappingNeverSplitGraphemes() {
        String text = "a\u0301\uD83D\uDE00x\uFE0Fbbbbbbbb";
        var rows = rows(text, 18, true, false, 1);
        var boundaries = List.of(17,19,21,23,24,25,26,27,28,29,30,31);
        for (var row : rows) {
            assertTrue(boundaries.contains(row.start())); assertTrue(boundaries.contains(row.end()));
            for (float x=0; x<row.width(); x+=.25f) assertTrue(boundaries.contains(LineLayout.hit(row,x)));
        }
    }
    @Test void tabsUseStopsAndAbsoluteOffsetsRoundTrip() {
        var row = rows("abc\txyz", 500, false, false, 1).getFirst();
        float stop = fonts(1).spaceAdvance() * 4;
        assertEquals(stop, LineLayout.caret(row,21).getBounds2D().getX(), .01);
        for (int i=17;i<=24;i++) {
            float x=(float)LineLayout.caret(row,i).getBounds2D().getX();
            assertEquals(i,LineLayout.hit(row,x), "offset " + i);
        }
    }
    @Test void rtlVisualNavigationUsesShapedHits() {
        var row = rows("\u05D0\u05D1\u05D2",500,false,false,1).getFirst();
        assertTrue(LineLayout.caret(row,17).getBounds2D().getX() > LineLayout.caret(row,20).getBounds2D().getX());
        assertEquals(19, LineLayout.hit(row,(float)LineLayout.caret(row,19).getBounds2D().getX()));
    }
    @Test void wordWrappingAndCharacterWrappingDifferWithoutDroppingWhitespace() {
        assertNotEquals(rows("alpha beta gamma",75,true,true,1).getFirst().end(), rows("alpha beta gamma",75,true,false,1).getFirst().end());
        assertTrue(rows("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",20,true,true,1).size()>1);
    }
    @Test void explicitFallbackPrecedesSystemAndMissingFamiliesDoNotMaskIt() throws Exception {
        dev.jasper.editor.testsupport.LayoutFonts.register();
        var f = new EditorFonts(new TerminalTypography("missing-editor-fixture-family", 16.5,
            List.of("IBM Plex Sans", "Cousine Nerd Font Mono"), true, 1), FRC);
        assertEquals("IBM Plex Sans", f.primary().getFamily());
        String glyph = String.valueOf((char)0xF113);
        assertFalse(f.primary().canDisplay(glyph.charAt(0)));
        assertEquals("Cousine Nerd Font Mono", f.forGrapheme(glyph, Font.PLAIN).getFamily());
        var row=LineLayout.layout(glyph,0,List.of(),f,100,false,false,4).getFirst();
        var expected=new java.awt.font.TextLayout(glyph,f.forGrapheme(glyph,0),FRC);
        assertEquals(expected.getAdvance(),row.width(),.001);
    }
    @Test void ligatureSelectionAndMixedStylesShareTheSameShapedRun() throws Exception {
        dev.jasper.editor.testsupport.LayoutFonts.register();
        var on=new EditorFonts(new TerminalTypography("IBM Plex Sans",24,List.of(),true,1),FRC);
        var off=new EditorFonts(new TerminalTypography("IBM Plex Sans",24,List.of(),false,1),FRC);
        var a=LineLayout.layout("office",0,List.of(),on,400,false,false,4).getFirst();
        var b=LineLayout.layout("office",0,List.of(),off,400,false,false,4).getFirst();
        assertNotEquals(a.width(),b.width(),.001,"bundled font must prove that shaping actually changes");
        assertEquals(1,a.segments().size());
        assertFalse(LineLayout.highlight(a,2,3).getBounds2D().isEmpty());
        var mixed=LineLayout.layout("plain bold italic",0,List.of(new LineLayout.Span(6,10,Font.BOLD,Color.RED),
            new LineLayout.Span(11,17,Font.ITALIC,Color.BLUE)),on,400,false,false,4).getFirst();
        assertEquals(1,mixed.segments().size(),"style boundaries are attributes, not independently shaped strings");
    }
    @Test void tokenRichMinifiedLineHasBoundedColdLayoutCost() {
        int repetitions=20_000;
        String text="int a=1;".repeat(repetitions);
        var spans=new java.util.ArrayList<LineLayout.Span>(repetitions*2);
        for(int i=0;i<text.length();i+=8) {
            spans.add(new LineLayout.Span(i,i+3,Font.BOLD,Color.BLUE));
            spans.add(new LineLayout.Span(i+3,i+8,Font.PLAIN,Color.BLACK));
        }
        long started=System.nanoTime();
        var rows=LineLayout.layout(text,0,spans,fonts(1),800,true,false,4);
        long elapsed=System.nanoTime()-started;
        System.out.println("minified: chars="+text.length()+", spans="+spans.size()+", rows="+rows.size()+", ms="+elapsed/1_000_000);
        assertEquals(text.length(),rows.getLast().end());
        assertTrue(elapsed<java.time.Duration.ofSeconds(15).toNanos(),"160k characters/40k token spans must complete within 15s; took "+elapsed/1_000_000+"ms");
    }
    @Test void unorderedDisjointStylesHaveTheSameShapingAsTokenOrder() {
        var ordered=List.of(new LineLayout.Span(0,3,Font.BOLD,Color.BLUE),new LineLayout.Span(3,8,Font.PLAIN,Color.BLACK));
        var reversed=List.of(ordered.get(1),ordered.get(0));
        var a=LineLayout.layout("int a=1;",0,ordered,fonts(1),400,false,false,4).getFirst();
        var b=LineLayout.layout("int a=1;",0,reversed,fonts(1),400,false,false,4).getFirst();
        assertEquals(a.width(),b.width());
        for(int i=0;i<=8;i++) assertEquals(LineLayout.caret(a,i).getBounds2D(),LineLayout.caret(b,i).getBounds2D());
    }
}

package dev.jasper.editor.textview;

import dev.jasper.editor.testsupport.EdtTests;
import dev.jasper.editor.testsupport.RasterTextArea;
import dev.jasper.sdk.ui.TerminalTypography;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.awt.font.TextHitInfo;
import java.util.List;
import javax.swing.text.Position;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EdtTests.class)
class EditorGeometryReviewTest {
    private static final TerminalTypography FONT = new TerminalTypography("Serif",18,List.of(),true,1.5);
    private static RasterTextArea syntaxArea(String text,String language) {
        var area=new RasterTextArea();area.typography(FONT);area.setSize(500,700);
        area.setSyntaxEditingStyle(language);
        var scheme=(org.fife.ui.rsyntaxtextarea.SyntaxScheme)area.getSyntaxScheme().clone();
        for(var style:scheme.getStyles()) if(style!=null) {style.font=area.getFont();style.foreground=Color.BLACK;}
        scheme.getStyle(TokenTypes.COMMENT_MULTILINE).font=area.getFont().deriveFont(Font.BOLD|Font.ITALIC);
        scheme.getStyle(TokenTypes.COMMENT_MULTILINE).foreground=Color.MAGENTA;
        scheme.getStyle(TokenTypes.RESERVED_WORD).font=area.getFont().deriveFont(Font.BOLD);
        scheme.getStyle(TokenTypes.RESERVED_WORD).foreground=Color.BLUE;
        area.setSyntaxScheme(scheme);area.setText(text);area.setCaretPosition(0);area.setHighlightCurrentLine(false);
        return area;
    }
    private static BufferedImage render(RasterTextArea area) {
        var image=new BufferedImage(area.getWidth(),area.getHeight(),BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();try{area.paint(g);}finally{g.dispose();}return image;
    }
    private static void sameAsFresh(RasterTextArea warm,String language,int line) throws Exception {
        try(var fresh=syntaxArea(warm.getText(),language)) {
            var expected=render(fresh);var actual=render(warm);
            int offset=warm.getLineEndOffset(line)-1;
            assertEquals(fresh.modelToView2D(offset),warm.modelToView2D(offset),"downstream syntax geometry");
            int y=(int)warm.modelToView2D(warm.getLineStartOffset(line)).getY();
            for(int py=y;py<y+warm.getLineHeight();py++)for(int x=0;x<400;x++)
                assertEquals(expected.getRGB(x,py),actual.getRGB(x,py),"downstream rendered syntax at "+x+","+py);
        }
    }
    @Test void multilineCommentChangesInvalidateTheirActualDownstreamLines() throws Exception {
        String text="int padding = 0;\n".repeat(5)+"\nint downstream = 123;\nint after = 456;\n";
        try(var area=syntaxArea(text,SyntaxConstants.SYNTAX_STYLE_JAVA)) {
            render(area);
            int change=area.getLineStartOffset(5);area.insert("/*",change);
            sameAsFresh(area,SyntaxConstants.SYNTAX_STYLE_JAVA,7);
            area.replaceRange("",change,change+2);
            sameAsFresh(area,SyntaxConstants.SYNTAX_STYLE_JAVA,7);
        }
    }
    @Test void syntaxSwitchInvalidatesPreviouslyShapedLines() throws Exception {
        try(var area=syntaxArea("int padding = 0;\n".repeat(10),SyntaxConstants.SYNTAX_STYLE_JAVA)) {
            render(area);area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            sameAsFresh(area,SyntaxConstants.SYNTAX_STYLE_NONE,8);
        }
    }
    @Test void bothBidiBiasesMapToTheirOwnCaretEdgeAndSingleCell() throws Exception {
        String text="ab \u05D0\u05D1\u05D2 cd";
        try(var area=syntaxArea(text,SyntaxConstants.SYNTAX_STYLE_NONE)) {
            render(area);
            var fonts=new EditorFonts(FONT,EditorTextView.CONTEXT);
            var layout=LineLayout.layout(text,0,List.of(),fonts,500,false,false,4).getFirst().segments().getFirst().layout();
            int offset=3;
            for(var bias:List.of(Position.Bias.Forward,Position.Bias.Backward)) {
                var hit=bias==Position.Bias.Forward?TextHitInfo.afterOffset(offset):TextHitInfo.beforeOffset(offset);
                var actual=area.getUI().modelToView2D(area,offset,bias);
                assertEquals(layout.getCaretInfo(hit)[0],actual.getX(),.001,"biased caret x");
                int character=hit.getCharIndex();
                var cell=layout.getLogicalHighlightShape(character,character+1).getBounds2D();
                assertEquals(cell.getWidth(),actual.getWidth(),.001,"one shaped cell, never the intervening run");
            }
        }
    }
    @Test void mouseBidiHitRetainsItsEdgeThroughMappingAndTheNextArrow() throws Exception {
        String text="ab \u05D0\u05D1\u05D2 cd";
        try(var area=syntaxArea(text,SyntaxConstants.SYNTAX_STYLE_NONE)) {
            render(area);var fonts=new EditorFonts(FONT,EditorTextView.CONTEXT);
            var layout=LineLayout.layout(text,0,List.of(),fonts,500,false,false,4).getFirst().segments().getFirst().layout();
            for(float x=1;x<layout.getAdvance()-1;x+=2) {
                var expected=layout.hitTestChar(x,0);
                var bias=new Position.Bias[]{Position.Bias.Forward};
                int offset=area.getUI().viewToModel2D(area,new java.awt.geom.Point2D.Float(x,4),bias);
                assertEquals(expected.getInsertionIndex(),offset);
                assertEquals(expected.isLeadingEdge()?Position.Bias.Forward:Position.Bias.Backward,bias[0],"mouse bias at "+x);
                assertEquals(layout.getCaretInfo(expected)[0],area.getUI().modelToView2D(area,offset,bias[0]).getX(),.001);
                var next=layout.getNextRightHit(expected);
                if(next!=null) {
                    int moved=area.getUI().getNextVisualPositionFrom(area,offset,bias[0],javax.swing.SwingConstants.EAST,bias);
                    assertEquals(next.getInsertionIndex(),moved);
                    assertEquals(next.isLeadingEdge()?Position.Bias.Forward:Position.Bias.Backward,bias[0]);
                }
            }
        }
    }
    @Test void largeWrappedDocumentGeometryAndGutterReuseCurrentGeneration() throws Exception {
        try(var area=syntaxArea("int repeatedValue = 123; // long wrapped line of text\n".repeat(5_000),SyntaxConstants.SYNTAX_STYLE_JAVA)) {
            area.setLineWrap(true);area.setSize(240,500);
            var scroll=new org.fife.ui.rtextarea.RTextScrollPane(area);scroll.setLineNumbersEnabled(true);
            area.getHighlighter().addHighlight(5,20,new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW));
            scroll.setSize(240,500);
            long start=System.nanoTime();scroll.doLayout();scroll.getViewport().doLayout();scroll.getRowHeader().doLayout();scroll.getGutter().doLayout();
            var image=new BufferedImage(240,500,BufferedImage.TYPE_INT_ARGB);
            var g=image.createGraphics();try{scroll.paint(g);}finally{g.dispose();}
            long cold=System.nanoTime()-start;start=System.nanoTime();
            for(int i=0;i<1_000;i++) {
                int line=i*5;
                assertTrue(area.modelToView2D(area.getLineStartOffset(line)).getY()>=0);
                assertTrue(area.yForLine(line)>=0);
            }
            for(int i=0;i<3;i++) {g=image.createGraphics();try{scroll.paint(g);}finally{g.dispose();}}
            long repeated=System.nanoTime()-start;
            System.out.println("wrapped document: lines=5000, coldPaintMs="+cold/1_000_000+", 2000QueriesAnd3PaintsMs="+repeated/1_000_000);
            double before=area.modelToView2D(area.getLineStartOffset(100)).getY();
            area.insert("extra\n",0);
            assertTrue(area.modelToView2D(area.getLineStartOffset(101)).getY()>before);
        }
    }
    @Test @SuppressWarnings("deprecation") void stockCaretUsesItsStoredBiasAndActualBlockCell() throws Exception {
        String text="ab \u05D0\u05D1\u05D2 cd";
        try(var area=syntaxArea(text,SyntaxConstants.SYNTAX_STYLE_NONE)) {
            render(area);var caret=(org.fife.ui.rtextarea.ConfigurableCaret)area.getCaret();
            var fonts=new EditorFonts(FONT,EditorTextView.CONTEXT);
            var layout=LineLayout.layout(text,0,List.of(),fonts,500,false,false,4).getFirst().segments().getFirst().layout();
            for(var bias:List.of(Position.Bias.Forward,Position.Bias.Backward)) {
                int offset=3;caret.setDot(offset,bias);
                var hit=bias==Position.Bias.Forward?TextHitInfo.afterOffset(offset):TextHitInfo.beforeOffset(offset);
                caret.setStyle(org.fife.ui.rtextarea.CaretStyle.VERTICAL_LINE_STYLE);
                assertEquals((int)Math.floor(layout.getCaretInfo(hit)[0]),area.getUI().modelToView(area,offset).x);
                caret.setStyle(org.fife.ui.rtextarea.CaretStyle.BLOCK_STYLE);
                int character=hit.getCharIndex();
                var expected=layout.getLogicalHighlightShape(character,character+1).getBounds();
                var actual=area.getUI().modelToView(area,offset);
                assertEquals(expected.x,actual.x);assertEquals(expected.width,actual.width);
            }
        }
    }
}

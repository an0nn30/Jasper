package dev.jasper.editor.textview;

import dev.jasper.editor.testsupport.EdtTests;
import dev.jasper.editor.vim.VimTextArea;
import dev.jasper.sdk.ui.TerminalTypography;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EdtTests.class)
class EditorTextViewTest {
    @Test void firstPaintRefreshAndUiReplacementKeepSharedGeometry() throws Exception {
        var area = new dev.jasper.editor.testsupport.RasterTextArea();
        area.typography(new TerminalTypography("Monospaced",13.5,List.of(),true,1.5));
        area.setText("alpha beta gamma delta\nsecond line");
        area.setSize(70,300);
        area.setLineWrap(true);
        paint(area);
        assertInstanceOf(EditorTextView.class,area.getUI().getRootView(area).getView(0));
        var first=area.modelToView2D(0);
        var second=area.modelToView2D(6);
        assertEquals(area.getLineHeight(),second.getY()-first.getY());
        assertEquals(6,area.viewToModel2D(new Point((int)second.getX(),(int)second.getY()+2)));
        area.updateUI(); paint(area);
        assertEquals(second,area.modelToView2D(6));
        area.typography(new TerminalTypography("Monospaced",13.5,List.of(),true,3)); paint(area);
        assertTrue(area.modelToView2D(6).getY()>second.getY());
    }
    @Test void wrappedLogicalChildrenMatchGutterAndFoldGeometry() throws Exception {
        var area = new dev.jasper.editor.testsupport.RasterTextArea();
        area.typography(new TerminalTypography("Monospaced",14,List.of(),true,1.5));
        area.setText("a long logical line which wraps into several rows\nlast");
        area.setLineWrap(true); area.setSize(100,400); paint(area);
        var view=area.getUI().getRootView(area).getView(0);
        var allocation=new java.awt.Rectangle(0,0,100,400);
        assertEquals(2,view.getViewCount());
        assertTrue(view.getChildAllocation(0,allocation).getBounds().height>area.getLineHeight());
        assertEquals(view.getChildAllocation(0,allocation).getBounds().height,view.getChildAllocation(1,allocation).getBounds().y);
    }
    @Test void unwrappedFirstPaintDisplayableFontRefreshAndDocumentEditsRemainConsistent() throws Exception {
        try(var area=new dev.jasper.editor.testsupport.RasterTextArea()) {
            area.typography(new TerminalTypography("Monospaced",13.5,List.of(),true,1.5));
            area.setText("first\nsecond\nthird");area.setSize(250,180);paint(area);
            area.addNotify();
            try {
                area.typography(new TerminalTypography("Monospaced",19.5,List.of(),false,.5));paint(area);
                assertEquals(area.getLineHeight(),area.modelToView2D(6).getY());
                area.insert("x",0);
                assertEquals(area.getLineHeight(),area.modelToView2D(7).getY());
                area.replaceRange("",0,7);
                assertEquals(0,area.modelToView2D(0).getY());
                assertEquals(area.getLineHeight(),area.modelToView2D(7).getY());
            } finally {area.removeNotify();}
        }
    }
    @Test void stockFoldIndicatorClicksCorrectWrappedLogicalLine() throws Exception {
        try(var area=new dev.jasper.editor.testsupport.RasterTextArea()) {
            area.typography(new TerminalTypography("Serif",18.5,List.of(),true,1.5));
            area.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA);
            area.setText("// A long introduction that wraps across several physical rows\nclass Office {\n  int value;\n}\nlast");
            area.setCaretPosition(0);area.setLineWrap(true);area.setCodeFoldingEnabled(true);
            var scroll=new org.fife.ui.rtextarea.RTextScrollPane(area);scroll.setFoldIndicatorEnabled(true);
            scroll.setSize(190,500);scroll.doLayout();scroll.getViewport().doLayout();
            scroll.getRowHeader().doLayout();scroll.getGutter().doLayout();
            area.getFoldManager().reparse();paint(area);
            var fold=area.getFoldManager().getFold(0);assertEquals(1,fold.getStartLine());
            java.awt.Component indicator=java.util.Arrays.stream(scroll.getGutter().getComponents())
                .filter(c -> c instanceof org.fife.ui.rtextarea.FoldIndicator).findFirst().orElseThrow();
            int y=(int)area.modelToView2D(area.getLineStartOffset(1)).getY()+area.getLineHeight()/2;
            assertTrue(y>area.getLineHeight());
            indicator.dispatchEvent(new java.awt.event.MouseEvent(indicator,java.awt.event.MouseEvent.MOUSE_CLICKED,
                1,0,4,y,1,false));
            assertTrue(fold.isCollapsed());
            assertEquals(-1,area.yForLine(2));
            var view=area.textView();var allocation=new java.awt.Rectangle(0,0,area.getWidth(),area.getHeight());
            assertEquals(0,view.getChildAllocation(2,allocation).getBounds().height);
            assertEquals(view.getChildAllocation(1,allocation).getBounds().getMaxY(),area.modelToView2D(area.getLineStartOffset(4)).getY());
            indicator.dispatchEvent(new java.awt.event.MouseEvent(indicator,java.awt.event.MouseEvent.MOUSE_CLICKED,
                2,0,4,y,1,false));assertFalse(fold.isCollapsed());
        }
    }
    @Test void visualArrowsTraverseTabsAndRtlWithoutSkippingToAnotherRow() throws Exception {
        try(var area=new dev.jasper.editor.testsupport.RasterTextArea()) {
            area.typography(new TerminalTypography("Monospaced",14,List.of(),true,1));
            area.setText("a\tb\n\u05D0\u05D1\u05D2");area.setSize(250,200);paint(area);
            var bias=new javax.swing.text.Position.Bias[]{javax.swing.text.Position.Bias.Forward};
            assertEquals(2,area.getUI().getNextVisualPositionFrom(area,1,bias[0],javax.swing.SwingConstants.EAST,bias));
            assertEquals(1,area.getUI().getNextVisualPositionFrom(area,2,bias[0],javax.swing.SwingConstants.WEST,bias));
            assertEquals(5,area.getUI().getNextVisualPositionFrom(area,4,bias[0],javax.swing.SwingConstants.WEST,bias));
        }
    }
    @Test void mixedBidiVisualNavigationRetainsTheHitBias() throws Exception {
        try(var area=new dev.jasper.editor.testsupport.RasterTextArea()) {
            area.typography(new TerminalTypography("Monospaced",14,List.of(),true,1));
            String text="ab \u05D0\u05D1\u05D2 cd";area.setText(text);area.setSize(350,100);paint(area);
            var fonts=new EditorFonts(new TerminalTypography("Monospaced",14,List.of(),true,1),EditorTextView.CONTEXT);
            var layout=LineLayout.layout(text,0,List.of(),fonts,350,false,false,4).getFirst().segments().getFirst().layout();
            var hit=java.awt.font.TextHitInfo.leading(0);
            var bias=new javax.swing.text.Position.Bias[]{javax.swing.text.Position.Bias.Forward};
            int offset=0;
            while((hit=layout.getNextRightHit(hit))!=null) {
                offset=area.getUI().getNextVisualPositionFrom(area,offset,bias[0],javax.swing.SwingConstants.EAST,bias);
                assertEquals(hit.getInsertionIndex(),offset);
                assertEquals(hit.isLeadingEdge()?javax.swing.text.Position.Bias.Forward:javax.swing.text.Position.Bias.Backward,bias[0]);
            }
        }
    }
    @Test void bidiSelectionBackgroundUsesDisjointShapedRanges() throws Exception {
        try(var area=new dev.jasper.editor.testsupport.RasterTextArea()) {
            var typography=new TerminalTypography("Monospaced",18,List.of(),true,1);
            area.typography(typography);String text="abc \u05D0\u05D1\u05D2 xyz";
            area.setText(text);area.setSize(300,100);area.setHighlightCurrentLine(false);
            area.setSelectionColor(java.awt.Color.MAGENTA);area.select(2,5);area.getCaret().setSelectionVisible(true);
            var fonts=new EditorFonts(typography,EditorTextView.CONTEXT);
            var row=LineLayout.layout(text,0,List.of(),fonts,300,false,false,4).getFirst();
            var expected=java.awt.geom.AffineTransform.getTranslateInstance(0,row.baseline()).createTransformedShape(LineLayout.highlight(row,2,5));
            var image=new BufferedImage(300,100,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
            try {area.paint(graphics);}finally{graphics.dispose();}
            for(int x=0;x<200;x++) if(!expected.intersects(x-1,3,3,1))
                assertNotEquals(java.awt.Color.MAGENTA.getRGB(),image.getRGB(x,3),"unselected bidi gap at "+x);
        }
    }
    static void paint(VimTextArea area) {
        var image=new BufferedImage(area.getWidth(),area.getHeight(),BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics(); try { area.paint(g); } finally {g.dispose();}
    }
}

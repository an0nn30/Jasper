package dev.jasper.editor.ui;

import dev.jasper.editor.testsupport.EdtTests;
import dev.jasper.editor.testsupport.LayoutFonts;
import dev.jasper.editor.testsupport.RasterTextArea;
import dev.jasper.sdk.ui.EditorPalette;
import dev.jasper.sdk.ui.TerminalTypography;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.text.DefaultHighlighter;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

/** Real raster paint acceptance for the specialized source surface and stock gutter, with no native peer. */
@ExtendWith(EdtTests.class)
class EditorTypographyRenderTest {
    @Test void lightDarkAndGlassRenderFallbackLigaturesHighlightsAndGutters() throws Exception {
        LayoutFonts.register();
        for(String variant:List.of("light","dark","glass")) try(var area=new RasterTextArea()) {
            area.typography(new TerminalTypography("IBM Plex Sans",20.5,List.of("Cousine Nerd Font Mono"),true,1.5));
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            area.setText("class Office {\n  // office ffi fi fallback "+(char)0xF113+"\n  String text = \"a\u0301 \uD83D\uDE00 \u05D0\u05D1\u05D2\";\n  // https://example.com/office\n  int count = 42;\n}\n");
            area.setCaretPosition(0); area.setLineWrap(true);area.setWrapStyleWord(true);
            area.setHighlightCurrentLine(true);area.setBracketMatchingEnabled(true);area.setAnimateBracketMatching(false);
            var scroll=new RTextScrollPane(area);scroll.setLineNumbersEnabled(true);scroll.setFoldIndicatorEnabled(true);
            Color background=variant.equals("light")?new Color(0xf4f2e9):new Color(0x182028);
            Color foreground=variant.equals("light")?new Color(0x223344):new Color(0xe7e9ed);
            var palette=new EditorPalette(background,foreground,Color.ORANGE,new Color(0x93b8db),new Color(0x75808c),background,new Color(0x8c99a6));
            new EditorTheme().apply(area,scroll,palette,EditorThemeTest.syntax(variant.equals("light")?0x203040:0xbacada),variant.equals("glass")?.7:1);
            area.select(25,27);area.getCaret().setSelectionVisible(true);
            area.getHighlighter().addHighlight(area.getText().indexOf("42"),area.getText().indexOf("42")+2,new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW));
            scroll.setSize(440,300);scroll.doLayout();scroll.getViewport().doLayout();
            if(scroll.getRowHeader()!=null) {scroll.getRowHeader().doLayout();scroll.getGutter().doLayout();}
            BufferedImage image=new BufferedImage(440,300,BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics=image.createGraphics();
            try { graphics.setColor(new Color(0x49677a));graphics.fillRect(0,0,440,300);scroll.paint(graphics); }
            finally {graphics.dispose();}
            Path output=Path.of("build/typography-render/source-"+variant+".png");Files.createDirectories(output.getParent());ImageIO.write(image,"png",output.toFile());
            assertTrue(pixels(image,Color.YELLOW)>5,"ordinary layered highlights must reach the shaped view");
            assertTrue(area.modelToView2D(0).getWidth()>1,"block caret gets the actual glyph cell width");
            int url=area.getText().indexOf("https");var bounds=area.modelToView2D(url);
            var token=area.viewToToken(new Point((int)bounds.getX()+1,(int)bounds.getY()+2));
            assertNotNull(token);assertTrue(token.getLexeme().contains("https"));
            var activated=new java.util.ArrayList<String>();
            area.addHyperlinkListener(event -> {if(event.getEventType()==javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) activated.add(event.getURL().toString());});
            int mask=java.awt.event.InputEvent.CTRL_DOWN_MASK;area.setLinkScanningMask(mask);
            area.dispatchEvent(new java.awt.event.MouseEvent(area,java.awt.event.MouseEvent.MOUSE_MOVED,1,mask,(int)bounds.getX()+2,(int)bounds.getY()+2,0,false));
            area.dispatchEvent(new java.awt.event.MouseEvent(area,java.awt.event.MouseEvent.MOUSE_CLICKED,2,mask,(int)bounds.getX()+2,(int)bounds.getY()+2,1,false));
            assertEquals(List.of("https://example.com/office"),activated);
            assertEquals(area.getLineHeight(),area.modelToView2D(area.getLineStartOffset(1)).getY()-area.modelToView2D(0).getY());
        }
    }
    @Test void parserOccurrenceAndBracketLayersRemainVisible() throws Exception {
        try(var area=new RasterTextArea()) {
            area.typography(new TerminalTypography("Monospaced",18.5,List.of(),true,1.5));
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            area.setText("value value\n(value)\nproblem");area.setSize(350,180);
            area.setMarkOccurrences(true);area.setMarkOccurrencesColor(Color.GREEN);
            area.setCaretPosition(2);area.setDocument(area.getDocument());
            assertFalse(area.getMarkedOccurrences().isEmpty());
            var parser=new org.fife.ui.rsyntaxtextarea.parser.AbstractParser() {
                @Override public org.fife.ui.rsyntaxtextarea.parser.ParseResult parse(org.fife.ui.rsyntaxtextarea.RSyntaxDocument doc,String style) {
                    var result=new org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult(this);
                    var notice=new org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice(this,"problem",2,20,7);
                    notice.setColor(Color.MAGENTA);result.addNotice(notice);return result;
                }
            };
            area.addParser(parser);area.forceReparsing(0);
            var image=new BufferedImage(350,180,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
            try {area.paint(g);}finally{g.dispose();}
            assertTrue(pixels(image,Color.GREEN)>10,"occurrence background");
            assertTrue(pixels(image,Color.MAGENTA)>3,"parser squiggle");
            area.setBracketMatchingEnabled(true);area.setAnimateBracketMatching(false);
            area.setMatchedBracketBorderColor(Color.RED);area.setCaretPosition(13);
            image=new BufferedImage(350,180,BufferedImage.TYPE_INT_ARGB);g=image.createGraphics();
            try {area.paint(g);}finally{g.dispose();}
            assertTrue(pixels(image,Color.RED)>3,"matched bracket augmentation");
        }
    }
    private static int pixels(BufferedImage image,Color color) {
        int count=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)==color.getRGB())count++;return count;
    }
}

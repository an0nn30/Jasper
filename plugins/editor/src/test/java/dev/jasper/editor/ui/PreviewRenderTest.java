package dev.jasper.editor.ui;
import dev.jasper.sdk.ui.TerminalTypography;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PreviewRenderTest {
    static final String SAMPLE="# Native preview\n\nProse wraps through a narrow viewport with **strong**, *emphasis*, ~~strike~~, `inline code`, and [a link](https://example.com).\n\n> A block quote.\n\n1. Ordered item\n2. Another\n\n- [x] Completed task\n- [ ] Next task\n\n---\n\n```java\nint answer = 42; // code ligatures != ->\nString text = \"fallback 日本語 é\";\n```\n\n| Left | Right |\n| :--- | ---: |\n| table text | 123 |\n\n![supplied image](picture.png)\n";
    @Test void rendersLightDarkGlassNarrowAndLargeWithActualSwingViews() throws Exception {
        var prepared=PreviewPaneTest.prepare(SAMPLE);
        for(boolean dark:new boolean[]{false,true})for(int width:new int[]{300,760})for(int size:new int[]{16,28}) {
            var pane=PreviewPaneTest.install(prepared);
            SwingUtilities.invokeAndWait(() -> pane.install(prepared,new TerminalTypography("Monospaced",size,java.util.List.of("Serif"),true,1.3),PreviewPaneTest.palette(dark),EditorThemeTest.syntax(0x668800),new Font("Serif",0,12),dark?.75:1));
            PreviewPaneTest.await(pane,prepared.result());
            SwingUtilities.invokeAndWait(() -> {
                pane.image(0,new BufferedImage(1200,300,BufferedImage.TYPE_INT_RGB));
                var editor=pane.editor();var scroll=new JScrollPane(editor);scroll.setSize(width,1000);
                for(int pass=0;pass<4;pass++){scroll.doLayout();scroll.getViewport().doLayout();editor.doLayout();}
                var image=new BufferedImage(width,1000,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();scroll.paint(g);g.dispose();
                assertThat(editor.getPreferredSize().height).isBetween(100,100000);
                assertThat(PreviewPaneTest.allViews(editor.getUI().getRootView(editor))).allMatch(v -> Float.isFinite(v.getPreferredSpan(0))&&Float.isFinite(v.getPreferredSpan(1)));
                try {var dir=Path.of("build/preview-matrix");Files.createDirectories(dir);ImageIO.write(image,"png",dir.resolve((dark?"dark-glass":"light")+"-"+width+"-"+size+".png").toFile());}catch(Exception e){throw new AssertionError(e);}
                pane.close();
            });
        }
    }
}

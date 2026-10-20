package dev.jasper.app.notificationui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextBlockTest {
    @Test void paintedLiteralTextUsesAntialiasedCoverage() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var block = new TextBlock(dev.jasper.app.testsupport.KitTestSupport.context(),dev.jasper.ui.metrics.Typography.Role.BODY,dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY,0);
            block.setText("AgjyÉ");block.setWrapWidth(200);block.setSize(block.getPreferredSize());
            var image = new java.awt.image.BufferedImage(400,block.getHeight()*2,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics=image.createGraphics();graphics.scale(2,2);block.paint(graphics);graphics.dispose();
            int partial=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int alpha=image.getRGB(x,y)>>>24;if(alpha>0&&alpha<255)partial++;}
            assertThat(partial).as("native clamped text shares engine text coverage").isPositive();
        });
    }

    @Test void wordsWrapToTheWidth() {
        assertThat(TextBlock.wrap("aaaa bbbb cccc", String::length, 10, 0)).containsExactly("aaaa bbbb", "cccc");
    }

    @Test void aWordWiderThanTheLineBreaksByCharacter() {
        assertThat(TextBlock.wrap("abcdefghijklm", String::length, 5, 0)).containsExactly("abcde", "fghij", "klm");
    }

    @Test void lineBreaksAreKept() {
        assertThat(TextBlock.wrap("one\n\ntwo", String::length, 10, 0)).containsExactly("one", "", "two");
    }

    @Test void cutTextEndsInAnEllipsis() {
        assertThat(TextBlock.wrap("a b c d e f", String::length, 3, 2)).containsExactly("a b", "c…");
        assertThat(TextBlock.wrap("a b", String::length, 3, 2)).containsExactly("a b");
    }

    @Test void surrogatePairsStayWhole() {
        assertThat(TextBlock.wrap("😀😀", String::length, 3, 0)).containsExactly("😀", "😀");
    }

    @Test void emptyTextHasNoLines() {
        assertThat(TextBlock.wrap("", String::length, 10, 5)).isEqualTo(List.of());
    }
}

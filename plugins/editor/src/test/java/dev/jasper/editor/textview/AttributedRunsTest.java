package dev.jasper.editor.textview;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttributedRunsTest {
    @Test void indexedRunsMatchNativeAttributesAndClippedRanges() {
        String text="office bold blue ffi";var font=new Font("Serif",0,13);
        var runs=List.of(new AttributedRuns.Run(0,7,Map.of(TextAttribute.FONT,font,TextAttribute.FOREGROUND,Color.BLACK)),
            new AttributedRuns.Run(7,12,Map.of(TextAttribute.FONT,font.deriveFont(Font.BOLD),TextAttribute.FOREGROUND,Color.BLACK)),
            new AttributedRuns.Run(12,text.length(),Map.of(TextAttribute.FONT,font.deriveFont(Font.BOLD),TextAttribute.FOREGROUND,Color.BLUE)));
        var nativeText=new AttributedString(text);for(var run:runs)nativeText.addAttributes(run.attributes(),run.start(),run.end());
        var indexed=new AttributedRuns(text,runs);
        for(int begin:new int[]{0,3,7})for(int end:new int[]{12,text.length()}) {
            var expected=nativeText.getIterator(null,begin,end);var actual=indexed.iterator(begin,end);
            for(int at=begin;at<end;at++) {
                assertEquals(expected.setIndex(at),actual.setIndex(at));assertEquals(expected.getAttributes(),actual.getAttributes());
                assertEquals(expected.getRunStart(),actual.getRunStart());assertEquals(expected.getRunLimit(),actual.getRunLimit());
                for(var key:List.of(TextAttribute.FONT,TextAttribute.FOREGROUND,TextAttribute.KERNING)) {
                    assertEquals(expected.getRunStart(key),actual.getRunStart(key));assertEquals(expected.getRunLimit(key),actual.getRunLimit(key));
                }
                assertEquals(expected.getRunLimit(Set.of(TextAttribute.FONT,TextAttribute.FOREGROUND)),actual.getRunLimit(Set.of(TextAttribute.FONT,TextAttribute.FOREGROUND)));
            }
            assertEquals(AttributedCharacterIterator.DONE,actual.next());assertEquals(expected.last(),actual.last());
            var copy=(AttributedCharacterIterator)actual.clone();copy.first();assertEquals(end-1,actual.getIndex());
        }
    }
}

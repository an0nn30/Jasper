package dev.jasper.editor.textview;

import java.text.BreakIterator;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndexedBreakIteratorTest {
    @Test void indexedBoundariesMatchNativeWordsAndGraphemesInBothDirections() {
        for(boolean words:new boolean[]{false,true}) {
            var nativeBreaks=words?BreakIterator.getLineInstance(Locale.ROOT):BreakIterator.getCharacterInstance(Locale.ROOT);
            var indexed=new IndexedBreakIterator(words?BreakIterator.getLineInstance(Locale.ROOT):BreakIterator.getCharacterInstance(Locale.ROOT));
            String text="alpha abcdefghijklmnop \u65E5\u672C\u8A9E a\u0301 \uD83D\uDE00\tend";
            nativeBreaks.setText(text);indexed.setText(text);
            for(int offset=0;offset<=text.length();offset++) {
                if(offset<text.length())assertEquals(nativeBreaks.following(offset),indexed.following(offset),"following "+offset);
                else assertEquals(BreakIterator.DONE,indexed.following(offset));
                assertEquals(nativeBreaks.preceding(offset),indexed.preceding(offset),"preceding "+offset);
                assertEquals(nativeBreaks.isBoundary(offset),indexed.isBoundary(offset),"boundary "+offset);
            }
            assertEquals(nativeBreaks.first(),indexed.first());
            for(int at=nativeBreaks.next();at!=BreakIterator.DONE;at=nativeBreaks.next())assertEquals(at,indexed.next());
            assertEquals(BreakIterator.DONE,indexed.next());
            assertEquals(nativeBreaks.last(),indexed.last());
            for(int at=nativeBreaks.previous();at!=BreakIterator.DONE;at=nativeBreaks.previous())assertEquals(at,indexed.previous());
            assertEquals(BreakIterator.DONE,indexed.previous());
        }
    }
}

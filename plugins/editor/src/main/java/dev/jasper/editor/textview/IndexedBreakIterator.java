package dev.jasper.editor.textview;

import java.text.BreakIterator;
import java.text.CharacterIterator;
import java.util.Arrays;

/** Native legal breaks indexed once; repeated wrapping of an unbroken word must not rescan its suffix. */
final class IndexedBreakIterator extends BreakIterator {
    private final BreakIterator delegate;
    private int[] boundaries = new int[0];
    private int index;
    IndexedBreakIterator(BreakIterator delegate) { this.delegate = delegate; }
    @Override public void setText(CharacterIterator text) {
        delegate.setText(text);
        int[] values = new int[256]; int count = 0;
        for (int at=delegate.first();at!=DONE;at=delegate.next()) {
            if(count==values.length)values=Arrays.copyOf(values,values.length*2);
            values[count++]=at;
        }
        boundaries=Arrays.copyOf(values,count);index=0;
    }
    @Override public CharacterIterator getText() { return delegate.getText(); }
    @Override public int first() {index=0;return current();}
    @Override public int last() {index=boundaries.length-1;return current();}
    @Override public int current() {return boundaries.length==0?DONE:boundaries[index];}
    @Override public int next() {if(index>=boundaries.length-1)return DONE;return boundaries[++index];}
    @Override public int previous() {if(index<=0)return DONE;return boundaries[--index];}
    @Override public int next(int count) {
        int result=current();
        while(count>0){result=next();count--;if(result==DONE)return DONE;}
        while(count<0){result=previous();count++;if(result==DONE)return DONE;}
        return result;
    }
    private void check(int offset) {
        if(boundaries.length==0 || offset<boundaries[0] || offset>boundaries[boundaries.length-1])throw new IllegalArgumentException("Invalid offset");
    }
    @Override public int following(int offset) {
        check(offset);int found=Arrays.binarySearch(boundaries,offset);int next=found>=0?found+1:-found-1;
        if(next==boundaries.length){index=boundaries.length-1;return DONE;}
        index=next;return current();
    }
    @Override public int preceding(int offset) {
        check(offset);int found=Arrays.binarySearch(boundaries,offset);int previous=found>=0?found-1:-found-2;
        if(previous<0){index=0;return DONE;}
        index=previous;return current();
    }
}

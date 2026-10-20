package dev.jasper.editor.textview;

import java.awt.Color;
import java.awt.Shape;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shaping, wrapping, carets and hit testing in absolute UTF-16 document coordinates. */
public final class LineLayout {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private LineLayout() { }
    /** A syntax run, with absolute start/end offsets. */
    public record Span(int start, int end, int fontStyle, Color foreground) { }
    /** A shaped tab-free range; a null layout represents a tab. */
    public record Segment(int start, int end, float x, TextLayout layout) { }
    /** An immutable physical row; source ranges remain contiguous across wraps. */
    public static final class Row {
        private final int start, end, height;
        private final List<Segment> segments;
        private final float width, baseline;
        private final List<Integer> boundaries;
        private Row(int start, int end, List<Segment> segments, float width, EditorFonts fonts, List<Integer> boundaries) {
            this.start=start; this.end=end; this.segments=List.copyOf(segments); this.width=width;
            height=fonts.height(); baseline=fonts.baseline();
            int first=java.util.Collections.binarySearch(boundaries,start);
            int last=java.util.Collections.binarySearch(boundaries,end);
            this.boundaries=List.copyOf(boundaries.subList(first,last+1));
        }
        public int start() { return start; }
        public int end() { return end; }
        public List<Segment> segments() { return segments; }
        public float width() { return width; }
        public int height() { return height; }
        public float baseline() { return baseline; }
        int clamp(int offset) {
            int best=start;
            for (int p:boundaries) { if (p>offset) return offset-best <= p-offset ? best : p; best=p; }
            return end;
        }
        int previous(int offset) { int last=start; for(int p:boundaries) {if(p>=offset) return last;last=p;} return last; }
        int next(int offset) { for (int p:boundaries) if(p>offset) return p; return end; }
    }
    /** Shapes a logical line, retaining whitespace and every source offset. */
    public static List<Row> layout(String text, int startOffset, List<Span> styles, EditorFonts fonts,
                                   float width, boolean wrap, boolean wordWrap, int tabSize) {
        // Token producers are ordered; sorting also admits callers with unordered, disjoint spans.
        styles=styles.stream().sorted(java.util.Comparator.comparingInt(Span::start)).toList();
        var boundaries=new ArrayList<Integer>(); boundaries.add(startOffset);
        var matcher=GRAPHEME.matcher(text);
        while(matcher.find()) boundaries.add(startOffset+matcher.end());
        if(text.isEmpty()) return List.of(new Row(startOffset,startOffset,List.of(),0,fonts,boundaries));
        var attributed=new AttributedString(text);
        attributed.addAttribute(TextAttribute.LIGATURES,fonts.ligatures()?1:0);
        attributed.addAttribute(TextAttribute.KERNING,fonts.ligatures()?1:0);
        var runs=new ArrayList<AttributeRun>();
        int styleIndex=0;
        matcher.reset();
        while(matcher.find()) {
            int absolute=startOffset+matcher.start();
            while(styleIndex<styles.size() && styles.get(styleIndex).end()<=absolute) styleIndex++;
            Span span=styleIndex<styles.size() && styles.get(styleIndex).start()<=absolute?styles.get(styleIndex):null;
            int style=span==null?0:span.fontStyle();
            Color foreground=span==null?Color.BLACK:span.foreground();
            String grapheme=matcher.group();
            var font=fonts.forGrapheme(grapheme,style);
            if(font.canDisplayUpTo(grapheme)<0) appendRun(runs,matcher.start(),matcher.end(),font,foreground);
            else {
                // Combining marks and variation selectors stay with their base during per-code-point fallback.
                int p=matcher.start();
                while(p<matcher.end()) {
                    int cp=text.codePointAt(p), end=p+Character.charCount(cp);
                    while(end<matcher.end()) {
                        int next=text.codePointAt(end), type=Character.getType(next);
                        if(type!=Character.NON_SPACING_MARK && type!=Character.COMBINING_SPACING_MARK && type!=Character.ENCLOSING_MARK) break;
                        end+=Character.charCount(next);
                    }
                    appendRun(runs,p,end,fonts.forCodePoint(cp,style),foreground); p=end;
                }
            }
        }
        for(var run:runs) attributed.addAttributes(java.util.Map.of(TextAttribute.FONT,run.font(),
            TextAttribute.FOREGROUND,run.foreground()),run.start(),run.end());
        float limit=wrap?Math.max(1,width):Float.MAX_VALUE;
        float stop=Math.max(1,tabSize)*fonts.spaceAdvance();
        var rows=new ArrayList<Row>();
        int p=0, rowStart=0; float x=0;
        var segments=new ArrayList<Segment>();
        LineBreakMeasurer breaker=null; int breakerEnd=-1;
        while(p<text.length()) {
            if(text.charAt(p)=='\t') {
                float next=((float)Math.floor(x/stop)+1)*stop;
                if(wrap && x>0 && next>limit) {
                    rows.add(new Row(startOffset+rowStart,startOffset+p,segments,x,fonts,boundaries));
                    segments=new ArrayList<>(); rowStart=p; x=0; continue;
                }
                segments.add(new Segment(startOffset+p,startOffset+p+1,x,null)); x=next; p++;
            } else {
                int end=text.indexOf('\t',p); if(end<0) end=text.length();
                if(breaker==null || breakerEnd!=end) {
                    breaker=new LineBreakMeasurer(attributed.getIterator(null,p,end),
                        wordWrap?BreakIterator.getLineInstance(Locale.ROOT):BreakIterator.getCharacterInstance(Locale.ROOT),fonts.context());
                    breakerEnd=end;
                }
                int next=breaker.nextOffset(Math.max(.01f,limit-x),end,false);
                int boundary=java.util.Collections.binarySearch(boundaries,startOffset+next);
                if(boundary<0) boundary=-boundary-2;
                int safe=boundaries.get(boundary)-startOffset;
                if(safe==p && x>0) {
                    rows.add(new Row(startOffset+rowStart,startOffset+p,segments,x,fonts,boundaries));
                    segments=new ArrayList<>(); rowStart=p; x=0; continue;
                }
                if(safe==p) safe=boundaries.get(java.util.Collections.binarySearch(boundaries,startOffset+p)+1)-startOffset;
                var layout=new TextLayout(attributed.getIterator(null,p,safe),fonts.context());
                segments.add(new Segment(startOffset+p,startOffset+safe,x,layout)); x+=layout.getAdvance(); p=safe;
                breaker.setPosition(p);
                if(p<end) {
                    rows.add(new Row(startOffset+rowStart,startOffset+p,segments,x,fonts,boundaries));
                    segments=new ArrayList<>(); rowStart=p; x=0;
                }
            }
        }
        if(rowStart<text.length() || rows.isEmpty()) rows.add(new Row(startOffset+rowStart,startOffset+p,segments,x,fonts,boundaries));
        return List.copyOf(rows);
    }
    private record AttributeRun(int start,int end,java.awt.Font font,Color foreground) { }
    private static void appendRun(List<AttributeRun> runs,int start,int end,java.awt.Font font,Color foreground) {
        if(!runs.isEmpty()) {
            var previous=runs.getLast();
            if(previous.end()==start && previous.font().equals(font) && previous.foreground().equals(foreground)) {
                runs.set(runs.size()-1,new AttributeRun(previous.start(),end,font,foreground));return;
            }
        }
        runs.add(new AttributeRun(start,end,font,foreground));
    }
    static Row shifted(Row row, int delta, EditorFonts fonts) {
        return new Row(row.start()+delta,row.end()+delta,row.segments().stream()
            .map(s -> new Segment(s.start()+delta,s.end()+delta,s.x(),s.layout())).toList(),row.width(),fonts,
            row.boundaries.stream().map(p -> p+delta).toList());
    }
    /** The shaped insertion caret, relative to the row's left edge and baseline. */
    public static Shape caret(Row row,int offset) {
        offset=row.clamp(offset);
        for(Segment segment:row.segments()) if(offset>=segment.start() && offset<=segment.end()) {
            if(segment.layout()!=null) return AffineTransform.getTranslateInstance(segment.x(),0)
                .createTransformedShape(segment.layout().getCaretShapes(offset-segment.start())[0]);
            float x=offset==segment.start()?segment.x():segmentEnd(row,segment);
            return new java.awt.geom.Line2D.Float(x,-row.baseline(),x,row.height()-row.baseline());
        }
        return new java.awt.geom.Line2D.Float(0,-row.baseline(),0,row.height()-row.baseline());
    }
    /** Biased insertion edge, including the weak caret at a bidi junction. */
    public static Shape caret(Row row,Hit hit) {
        int offset=row.clamp(hit.offset());
        for(Segment segment:row.segments()) {
            if(offset<segment.start() || offset>segment.end()) continue;
            if(offset==segment.end() && !hit.backward() && segment!=row.segments().getLast()) continue;
            if(segment.layout()!=null) {
                int local=offset-segment.start();
                TextHitInfo edge=hit.backward()?TextHitInfo.beforeOffset(local):TextHitInfo.afterOffset(local);
                return AffineTransform.getTranslateInstance(segment.x(),0).createTransformedShape(segment.layout().getCaretShape(edge));
            }
            float x=offset==segment.start()?segment.x():segmentEnd(row,segment);
            return new java.awt.geom.Line2D.Float(x,-row.baseline(),x,row.height()-row.baseline());
        }
        return caret(row,offset);
    }
    /** The one grapheme cell adjacent to a shaped hit; its bounds never cross the opposite bidi edge. */
    public static Shape cell(Row row,Hit hit) {
        int offset=row.clamp(hit.offset());
        int start=hit.backward()?row.previous(offset):offset;
        int end=hit.backward()?offset:row.next(offset);
        if(start==end) {
            float x=(float)caret(row,hit).getBounds2D().getX();
            return new Rectangle2D.Float(x,-row.baseline(),1,row.height());
        }
        return highlight(row,start,end);
    }
    /** Closest grapheme boundary to a visual x coordinate. */
    public static int hit(Row row,float x) { return hitInfo(row,x).offset(); }
    /** Closest shaped hit, retaining its leading/trailing edge for Swing's bias-aware mapping. */
    public static Hit hitInfo(Row row,float x) {
        for(Segment segment:row.segments()) {
            float end=segmentEnd(row,segment);
            if(x<=end || segment==row.segments().getLast()) {
                if(segment.layout()==null) return x<(segment.x()+end)/2
                    ?new Hit(segment.start(),false):new Hit(segment.end(),true);
                var hit=segment.layout().hitTestChar(x-segment.x(),0);
                return new Hit(row.clamp(segment.start()+hit.getInsertionIndex()),!hit.isLeadingEdge());
            }
        }
        return new Hit(row.end(),false);
    }
    private static float segmentEnd(Row row,Segment segment) {
        int next=row.segments().indexOf(segment)+1;
        return next<row.segments().size()?row.segments().get(next).x():row.width();
    }
    /** Logical selection shape, retaining shaping across selection edges and bidi runs. */
    public static Shape highlight(Row row,int start,int end) {
        var path=new Path2D.Float();
        for(Segment segment:row.segments()) {
            int lo=Math.max(start,segment.start()), hi=Math.min(end,segment.end());
            if(lo>=hi) continue;
            Shape shape=segment.layout()==null?new Rectangle2D.Float(0,-row.baseline(),segmentEnd(row,segment)-segment.x(),row.height())
                :segment.layout().getLogicalHighlightShape(lo-segment.start(),hi-segment.start());
            path.append(AffineTransform.getTranslateInstance(segment.x(),0).createTransformedShape(shape),false);
        }
        return path;
    }
    /** A shaped hit retains which side of a bidi boundary the caret occupies. */
    public record Hit(int offset, boolean backward) { }
    /** Next visual hit, retaining the TextLayout edge identity across arrow movements. */
    public static Hit nextVisual(Row row,int offset,boolean backward,boolean right) {
        for(Segment s:row.segments()) {
            if(s.layout()==null) {
                if(right && offset==s.start()) return new Hit(s.end(),false);
                if(!right && offset==s.end()) return new Hit(s.start(),false);
                continue;
            }
            if(offset<s.start() || offset>s.end()) continue;
            int relative=offset-s.start();
            var current=backward?TextHitInfo.beforeOffset(relative):TextHitInfo.afterOffset(relative);
            TextHitInfo hit=right?s.layout().getNextRightHit(current):s.layout().getNextLeftHit(current);
            while(hit!=null) {
                int next=s.start()+hit.getInsertionIndex();
                if(row.clamp(next)==next && (next!=offset || hit.isLeadingEdge()==backward))
                    return new Hit(next,!hit.isLeadingEdge());
                hit=right?s.layout().getNextRightHit(hit):s.layout().getNextLeftHit(hit);
            }
        }
        return null;
    }
}

package dev.jasper.editor.textview;

import dev.jasper.sdk.ui.TerminalTypography;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaHighlighter;
import org.fife.ui.rsyntaxtextarea.SyntaxView;
import org.fife.ui.rsyntaxtextarea.Token;

/** Document geometry shared by paint, selections, caret navigation, gutter and Vim's physical viewport. */
public final class EditorTextView extends SyntaxView {
    /** Stable fractional metrics for logical Swing coordinates, also installed on the painting context. */
    public static final FontRenderContext CONTEXT = new FontRenderContext(null,true,true);
    private final Supplier<TerminalTypography> typography;
    private EditorFonts fonts;
    private TerminalTypography value;
    private Object scheme;
    private float width=1;
    private boolean wrap, wordWrap;
    private int tabSize;
    private final Map<Element,CachedLine> cache=new HashMap<>();
    private record CachedLine(int start, String text, List<LineLayout.Row> rows) { }
    private record Placed(int line, int y, LineLayout.Row row) { }
    private record Geometry(List<Placed> rows, int[] tops, int[] heights, float width, int height) { }

    public EditorTextView(Element element,Supplier<TerminalTypography> typography) { super(element); this.typography=typography; }
    private RSyntaxTextArea area() { return (RSyntaxTextArea)getContainer(); }
    private void configure() {
        RSyntaxTextArea area=area();
        var insets=area.getInsets();
        float available=area.getWidth()-insets.left-insets.right;
        if(available>0 && width!=available) {width=available;cache.clear();}
        TerminalTypography next=typography==null?null:typography.get();
        if(next==null) {
            Font font=area.getFont();
            next=new TerminalTypography(font.getFamily(),Math.max(6,Math.min(72,font.getSize2D())),List.of(),false,1);
        }
        if(!next.equals(value) || scheme!=area.getSyntaxScheme() || wrap!=area.getLineWrap()
            || wordWrap!=area.getWrapStyleWord() || tabSize!=area.getTabSize()) {
            value=next; fonts=new EditorFonts(next,CONTEXT); scheme=area.getSyntaxScheme();
            wrap=area.getLineWrap(); wordWrap=area.getWrapStyleWord(); tabSize=area.getTabSize(); cache.clear();
        }
    }
    private List<LineLayout.Row> line(int index) {
        Element element=getElement().getElement(index);
        int start=element.getStartOffset(), end=Math.min(getDocument().getLength(),element.getEndOffset());
        CachedLine old=cache.get(element);
        if(old!=null) {
            if(old.start()==start) return old.rows();
            var shifted=old.rows().stream().map(row -> LineLayout.shifted(row,start-old.start(),fonts)).toList();
            cache.put(element,new CachedLine(start,old.text(),shifted)); return shifted;
        }
        try {
            String text=getDocument().getText(start,end-start);
            if(text.endsWith("\n")) text=text.substring(0,text.length()-1);
            var spans=new ArrayList<LineLayout.Span>();
            for(Token token=area().getTokenListForLine(index); token!=null && token.isPaintable(); token=token.getNextToken()) {
                Font font=area().getFontForTokenType(token.getType());
                spans.add(new LineLayout.Span(token.getOffset(),token.getEndOffset(),font==null?Font.PLAIN:font.getStyle(),area().getForegroundForTokenType(token.getType())));
            }
            if(spans.isEmpty()) spans.add(new LineLayout.Span(start,start+text.length(),Font.PLAIN,area().getForeground()));
            var rows=LineLayout.layout(text,start,spans,fonts,width,wrap,wordWrap,tabSize);
            cache.put(element,new CachedLine(start,text,rows)); return rows;
        } catch(BadLocationException bad) { throw new IllegalStateException(bad); }
    }
    private Geometry geometry() {
        configure();
        int count=getElement().getElementCount(), y=0; float max=0;
        var placed=new ArrayList<Placed>(); int[] tops=new int[count], heights=new int[count];
        for(int i=0;i<count;i++) {
            tops[i]=y;
            if(area().isCodeFoldingEnabled() && area().getFoldManager().isLineHidden(i)) continue;
            for(var row:line(i)) { placed.add(new Placed(i,y,row)); y+=row.height(); max=Math.max(max,row.width()); }
            heights[i]=y-tops[i];
        }
        return new Geometry(List.copyOf(placed),tops,heights,max,y);
    }
    /** Physical wrapped row count for a logical line, independent of fold visibility. */
    public int rowsForLine(int logicalLine) { configure(); return line(logicalLine).size(); }
    /** Physical row within its logical line containing an absolute offset. */
    public int rowInLine(int offset) {
        configure(); var rows=line(getElement().getElementIndex(offset));
        for(int i=0;i<rows.size();i++) if(offset<rows.get(i).end() || i==rows.size()-1) return i;
        return 0;
    }
    /** Source boundary at the physical row's left, middle or final grapheme. */
    public int rowPosition(int offset,int delta,char which) {
        var g=geometry(); int index=Math.max(0,Math.min(g.rows().size()-1,locate(g,offset,Position.Bias.Forward)+delta));
        var row=g.rows().get(index).row();
        if(which=='$') {
            int end=row.end();
            return row.previous(end);
        }
        return which=='m'?LineLayout.hit(row,row.width()/2):row.start();
    }
    /** Vertical physical motion, preserving visual x. */
    public int moveRows(int offset,int delta,float desiredX) {
        Geometry g=geometry(); int index=locate(g,offset,Position.Bias.Forward);
        int target=Math.max(0,Math.min(g.rows().size()-1,index+delta));
        return LineLayout.hit(g.rows().get(target).row(),desiredX);
    }
    /** Horizontal insertion coordinate in the source row. */
    public float xForOffset(int offset) {
        Geometry g=geometry();
        return (float)LineLayout.caret(g.rows().get(locate(g,offset,Position.Bias.Forward)).row(),offset).getBounds2D().getX();
    }
    private int locate(Geometry g,int offset,Position.Bias bias) {
        int result=0;
        for(int i=0;i<g.rows().size();i++) {
            var row=g.rows().get(i).row();
            if(offset<row.start()) return result;
            result=i;
            if(offset<row.end() || offset==row.end() && bias==Position.Bias.Backward) return i;
        }
        return result;
    }
    @Override public float getPreferredSpan(int axis) {
        if(getContainer()==null) return 1;
        var g=geometry(); return axis==X_AXIS?Math.max(1,g.width()+1):g.height();
    }
    @Override public float getMinimumSpan(int axis) { return axis==X_AXIS?1:getPreferredSpan(axis); }
    @Override public void setSize(float width,float height) {
        float next=Math.max(1,width);
        if(this.width!=next) {this.width=next;cache.clear();preferenceChanged(null,true,true);}
    }
    @Override public int getViewCount() { return getElement().getElementCount(); }
    @Override public View getView(int index) { return new LogicalLine(getElement().getElement(index),index); }
    @Override public int getViewIndex(int position,Position.Bias bias) { return getElement().getElementIndex(position); }
    @Override public Shape getChildAllocation(int index,Shape allocation) {
        if(allocation==null || index<0 || index>=getViewCount()) return null;
        var g=geometry(); var a=allocation.getBounds();
        return new Rectangle(a.x,a.y+g.tops()[index],a.width,g.heights()[index]);
    }
    private final class LogicalLine extends View {
        private final int index;
        LogicalLine(Element element,int index) { super(element);this.index=index;setParent(EditorTextView.this); }
        @Override public float getPreferredSpan(int axis) { var g=geometry();return axis==Y_AXIS?g.heights()[index]:g.width(); }
        @Override public void paint(Graphics graphics,Shape allocation) { }
        @Override public Shape modelToView(int p,Shape a,Position.Bias b) throws BadLocationException { return EditorTextView.this.modelToView(p,a,b); }
        @Override public int viewToModel(float x,float y,Shape a,Position.Bias[] b) { return EditorTextView.this.viewToModel(x,y,a,b); }
    }
    @Override public Shape modelToView(int offset,Shape allocation,Position.Bias bias) throws BadLocationException {
        if(offset<0 || offset>getDocument().getLength()) throw new BadLocationException("offset",offset);
        var g=geometry(); var placed=g.rows().get(locate(g,offset,bias)); var row=placed.row();
        double x=LineLayout.caret(row,offset).getBounds2D().getX();
        double next=LineLayout.caret(row,row.next(offset)).getBounds2D().getX();
        var a=allocation.getBounds();
        return new Rectangle2D.Float(a.x+(float)Math.min(x,next),a.y+placed.y(),(float)Math.max(1,Math.abs(next-x)),row.height());
    }
    @Override public Shape modelToView(int p0,Position.Bias b0,int p1,Position.Bias b1,Shape allocation) throws BadLocationException {
        var g=geometry(); var a=allocation.getBounds(); var result=new java.awt.geom.Path2D.Float();
        for(var placed:g.rows()) {
            var row=placed.row(); if(p0>row.end() || p1<row.start()) continue;
            if(p0==p1) return modelToView(p0,allocation,b0);
            Shape selected=LineLayout.highlight(row,p0,p1);
            result.append(AffineTransform.getTranslateInstance(a.x,a.y+placed.y()+row.baseline()).createTransformedShape(selected),false);
        }
        return result;
    }
    @Override public int viewToModel(float x,float y,Shape allocation,Position.Bias[] bias) {
        var g=geometry(); var a=allocation.getBounds(); bias[0]=Position.Bias.Forward;
        int index=Math.max(0,Math.min(g.rows().size()-1,(int)Math.floor((y-a.y)/fonts.height())));
        return LineLayout.hit(g.rows().get(index).row(),x-a.x);
    }
    @Override public int getNextVisualPositionFrom(int offset,Position.Bias bias,Shape allocation,int direction,Position.Bias[] result) throws BadLocationException {
        var g=geometry(); result[0]=Position.Bias.Forward;
        if(offset==-1) return direction==SwingConstants.WEST || direction==SwingConstants.NORTH?getDocument().getLength():0;
        int index=locate(g,offset,bias); var row=g.rows().get(index).row();
        if(direction==SwingConstants.EAST || direction==SwingConstants.WEST) {
            boolean right=direction==SwingConstants.EAST;
            var next=LineLayout.nextVisual(row,offset,bias==Position.Bias.Backward,right);
            if(next!=null) { result[0]=next.backward()?Position.Bias.Backward:Position.Bias.Forward;return next.offset(); }
            int adjacent=index+(right?1:-1);
            if(adjacent<0 || adjacent>=g.rows().size()) return offset;
            var target=g.rows().get(adjacent).row();
            return LineLayout.hit(target,right?0:target.width());
        }
        float x=xForOffset(offset);
        var magic=area().getCaret().getMagicCaretPosition();
        if(magic!=null) x=magic.x-allocation.getBounds().x;
        return moveRows(offset,direction==SwingConstants.SOUTH?1:-1,x);
    }
    @Override public int yForLine(Rectangle allocation,int line) {
        if(line<0 || line>=getViewCount()) return -1;
        var g=geometry();return g.heights()[line]==0?-1:allocation.y+g.tops()[line];
    }
    @Override public int yForLineContaining(Rectangle allocation,int offset) throws BadLocationException {
        return yForLine(allocation,getElement().getElementIndex(offset));
    }
    @Override public float nextTabStop(float x,int offset) { configure();float stop=fonts.spaceAdvance()*tabSize;return ((float)Math.floor(x/stop)+1)*stop; }
    @Override public void paint(Graphics graphics,Shape allocation) {
        var g=(Graphics2D)graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            var geometry=geometry(); var a=allocation.getBounds(); var clip=g.getClipBounds();
            var host=area(); var highlighter=(RSyntaxTextAreaHighlighter)host.getHighlighter();
            for(var placed:geometry.rows()) {
                var row=placed.row();float baseline=a.y+placed.y()+row.baseline();
                if(clip!=null && (baseline+fonts.naturalHeight()<clip.y || baseline-fonts.naturalHeight()>clip.y+clip.height)) continue;
                highlighter.paintLayeredHighlights(g,row.start(),row.end(),allocation,host,this);
                for(var segment:row.segments()) if(segment.layout()!=null) segment.layout().draw(g,a.x+segment.x(),baseline);
                if(host.getUseSelectedTextColor()) paintColorRange(g,row,a.x,baseline,
                    host.getSelectionStart(),host.getSelectionEnd(),host.getSelectedTextColor());
                // Underline hyperlinks using the same shaped logical ranges as hit testing.
                for(Token token=host.getTokenListForLine(placed.line());token!=null && token.isPaintable();token=token.getNextToken()) {
                    if(!host.getForegroundForToken(token).equals(host.getForegroundForTokenType(token.getType())))
                        paintColorRange(g,row,a.x,baseline,token.getOffset(),token.getEndOffset(),host.getForegroundForToken(token));
                    if(!host.getUnderlineForToken(token)) continue;
                    var bounds=LineLayout.highlight(row,token.getOffset(),token.getEndOffset()).getBounds2D();
                    if(bounds.isEmpty()) continue;
                    g.setColor(host.getForegroundForToken(token));
                    g.draw(new java.awt.geom.Line2D.Double(a.x+bounds.getMinX(),baseline+1,a.x+bounds.getMaxX(),baseline+1));
                }
                highlighter.paintParserHighlights(g,row.start(),row.end(),allocation,host,this);
            }
        } finally {g.dispose();}
    }
    private static void paintColorRange(Graphics2D graphics,LineLayout.Row row,float x,float baseline,int start,int end,Color color) {
        if(start>=end || start>=row.end() || end<=row.start()) return;
        var g=(Graphics2D)graphics.create();
        try {
            g.translate(x,baseline);
            g.clip(LineLayout.highlight(row,start,end)); g.setColor(color);
            for(var segment:row.segments()) if(segment.layout()!=null)
                g.fill(segment.layout().getOutline(AffineTransform.getTranslateInstance(segment.x(),0)));
        } finally { g.dispose(); }
    }
    /** Keeps RSTA's occurrence/parser machinery and clips ordinary layered painters to shaped source ranges. */
    static final class ShapedHighlighter extends RSyntaxTextAreaHighlighter {
        @Override public Object addHighlight(int start,int end,javax.swing.text.Highlighter.HighlightPainter painter)
                throws BadLocationException {
            if(painter instanceof javax.swing.text.LayeredHighlighter.LayerPainter layered) {
                painter=new javax.swing.text.LayeredHighlighter.LayerPainter() {
                    @Override public void paint(Graphics g,int p0,int p1,Shape bounds,javax.swing.text.JTextComponent component) {
                        layered.paint(g,p0,p1,bounds,component);
                    }
                    @Override public Shape paintLayer(Graphics graphics,int p0,int p1,Shape bounds,
                            javax.swing.text.JTextComponent component,View view) {
                        var g=(Graphics2D)graphics.create();
                        try {
                            g.clip(view.modelToView(p0,Position.Bias.Forward,p1,Position.Bias.Backward,bounds));
                            return layered.paintLayer(g,p0,p1,bounds,component,view);
                        } catch(BadLocationException stale) { return null; }
                        finally {g.dispose();}
                    }
                };
            }
            return super.addHighlight(start,end,painter);
        }
    }
    private void changed(DocumentEvent event) {
        int first=getElement().getElementIndex(event.getOffset());
        int last=getElement().getElementIndex(Math.min(getDocument().getLength(),event.getOffset()+event.getLength()));
        for(int i=first;i<=last;i++) cache.remove(getElement().getElement(i));
        cache.keySet().removeIf(element -> element.getParentElement()!=getElement()
            || getElement().getElement(getElement().getElementIndex(element.getStartOffset()))!=element);
        preferenceChanged(null,true,true);
        if(getContainer()!=null) {getContainer().revalidate();getContainer().repaint();}
    }
    @Override public void insertUpdate(DocumentEvent event,Shape a,ViewFactory f) {changed(event);}
    @Override public void removeUpdate(DocumentEvent event,Shape a,ViewFactory f) {changed(event);}
    @Override public void changedUpdate(DocumentEvent event,Shape a,ViewFactory f) {changed(event);}
}

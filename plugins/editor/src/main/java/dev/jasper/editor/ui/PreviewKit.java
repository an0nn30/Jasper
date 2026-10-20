package dev.jasper.editor.ui;

import dev.jasper.editor.textview.EditorFonts;
import dev.jasper.editor.textview.LineLayout;
import dev.jasper.sdk.ui.TerminalTypography;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.*;

/** Controlled native HTML views, shared code shaping and palette-derived styles. */
final class PreviewKit extends HTMLEditorKit {
    static final String MARKERS="jasper.preview.markers";
    private EditorFonts fonts;
    private TerminalTypography typography;
    private WorkspacePalette palette;
    private Font body;
    private final Map<Integer,BufferedImage> images;
    private final Map<Integer,String> imageReasons;
    private final List<PreviewImageView> imageViews=new ArrayList<>();
    private final Map<Element,Map<Element,CodeRun>> codeLines=new java.util.IdentityHashMap<>();
    private record CodeRun(LineLayout.Row row,float origin) {
        float originAt(int offset){return origin+(offset==row.start()?0:(float)LineLayout.caret(row,offset).getBounds2D().getX());}
    }
    PreviewKit(TerminalTypography typography,WorkspacePalette palette,Font body,Map<Integer,BufferedImage> images,Map<Integer,String> imageReasons){
        this.imageReasons=imageReasons;this.images=images;configure(typography,palette,body);
    }
    private void configure(TerminalTypography typography,WorkspacePalette palette,Font body){this.typography=typography;this.palette=palette;this.body=body;
        fonts=new EditorFonts(typography,new FontRenderContext(null,true,true));codeLines.clear();}
    @Override public Document createDefaultDocument(){var doc=new PreviewDocument(styles());doc.setAsynchronousLoadPriority(-1);doc.setTokenThreshold(128);return doc;}
    private static final class PreviewDocument extends HTMLDocument {
        PreviewDocument(StyleSheet styles){super(styles);}
        @Override public HTMLEditorKit.ParserCallback getReader(int pos){return new DocumentReader(this,pos);}
    }
    static final class DocumentReader extends HTMLDocument.HTMLReader {
        private final List<String> pending=new ArrayList<>();
        DocumentReader(HTMLDocument doc,int pos){doc.super(pos);}
        void mark(String name){pending.add(name);}
        @Override protected void addContent(char[] data,int offset,int length,boolean generateImpliedPIfNecessary){
            if(!pending.isEmpty())charAttr.addAttribute(MARKERS,List.copyOf(pending));
            super.addContent(data,offset,length,generateImpliedPIfNecessary);
            if(!pending.isEmpty()){pending.clear();charAttr.removeAttribute(MARKERS);}
        }
        @Override protected void addSpecialElement(HTML.Tag tag,MutableAttributeSet attrs){
            if(!pending.isEmpty()){attrs.addAttribute(MARKERS,List.copyOf(pending));pending.clear();}
            super.addSpecialElement(tag,attrs);
        }
    }
    void restyle(HTMLDocument doc,TerminalTypography typography,WorkspacePalette palette,Font body){configure(typography,palette,body);
        styles(doc.getStyleSheet());
        // Rebuild views without replacing the document, positions, selection or resources.
        if(!imageViews.isEmpty())imageViews.clear();
        doc.setCharacterAttributes(0,doc.getLength(),new SimpleAttributeSet(),false);
    }
    private StyleSheet styles(){var s=new StyleSheet();styles(s);return s;}
    private void styles(StyleSheet s){
        float size=(float)typography.size();String family=body.getFamily().replace("'","").replace(";","");
        s.addRule("body { font-family: '"+family+"'; font-size: "+size+"pt; color: "+hex(palette.text())+"; margin: 0; }");
        s.addRule("b, strong { font-weight: bold; } em, i { font-style: italic; } strike { text-decoration: line-through; }");
        s.addRule("p { margin-top: 0; margin-bottom: "+size*.6+"pt; }");
        s.addRule("a { color: "+hex(palette.accent())+"; text-decoration: underline; }");
        s.addRule("ul, ol { margin-left: "+size*1.7+"pt; margin-top: 0; margin-bottom: "+size*.4+"pt; }");
        s.addRule("blockquote { margin-left: "+size+"pt; margin-right: 0; color: "+hex(palette.textMuted())+"; }");
        s.addRule("pre { background-color: "+hex(palette.surfaceSunken())+"; padding: 8pt; margin-top: 6pt; margin-bottom: 8pt; }");
        s.addRule("code { font-family: '"+fonts.primary().getFamily()+"'; }");
        s.addRule("table { border-width: 1px; border-style: solid; border-color: "+hex(palette.border())+"; border-spacing: 0; }");
        s.addRule("td, th { padding: 5pt; border-width: 1px; border-style: solid; border-color: "+hex(palette.border())+"; }");
        s.addRule("th { background-color: "+hex(palette.surfaceRaised())+"; }");
        for(int level=1;level<=6;level++)s.addRule("h"+level+" { font-size: "+size*(1.9-.15*level)+"pt; margin-top: "+size*.7+"pt; margin-bottom: "+size*.4+"pt; }");
    }
    private static String hex(Color c){return String.format("#%02x%02x%02x",c.getRed(),c.getGreen(),c.getBlue());}
    void imagesChanged(){for(var view:List.copyOf(imageViews))view.preferenceChanged(null,true,true);}
    @Override public ViewFactory getViewFactory(){return element -> {
        Object tag=element.getAttributes().getAttribute(StyleConstants.NameAttribute);
        if(tag==HTML.Tag.IMG){var view=new PreviewImageView(element,images,imageReasons,palette.textMuted());imageViews.add(view);return view;}
        if(tag==HTML.Tag.CONTENT){
            if(element.getAttributes().isDefined(HTML.Tag.CODE)||ancestor(element,HTML.Tag.PRE))return new Code(element);
            return new InlineView(element);
        }
        if(tag==HTML.Tag.P || tag==HTML.Tag.IMPLIED || tag==HTML.Tag.H1 || tag==HTML.Tag.H2 || tag==HTML.Tag.H3 || tag==HTML.Tag.H4 || tag==HTML.Tag.H5 || tag==HTML.Tag.H6)
            return new Paragraph(element,ancestor(element,HTML.Tag.PRE)||ancestor(element,HTML.Tag.TABLE));
        return new HTMLFactory().create(element);
    };}
    private static boolean ancestor(Element e,HTML.Tag tag){for(;e!=null;e=e.getParentElement())if(e.getAttributes().getAttribute(StyleConstants.NameAttribute)==tag)return true;return false;}
    private final class Paragraph extends javax.swing.text.html.ParagraphView {
        private final boolean pre;
        Paragraph(Element element,boolean pre){super(element);this.pre=pre;setLineSpacing(ancestor(getElement(),HTML.Tag.PRE)?0:(float)typography.lineHeight()-1);}
        @Override public int getFlowSpan(int index){if(pre)return Integer.MAX_VALUE/16;
            int span=super.getFlowSpan(index);var container=getContainer();
            if(container instanceof JComponent c){var viewport=(JViewport)SwingUtilities.getAncestorOfClass(JViewport.class,c);span=Math.min(span,Math.max(1,(viewport==null?c.getWidth():viewport.getWidth())-Math.max(16,getFlowStart(index))-16));}
            return span;}
        @Override public float getMinimumSpan(int axis){return axis==X_AXIS&&pre?getPreferredSpan(axis):axis==X_AXIS?0:super.getMinimumSpan(axis);}
        @Override protected void setPropertiesFromAttributes(){super.setPropertiesFromAttributes();if(typography!=null)setLineSpacing(ancestor(getElement(),HTML.Tag.PRE)?0:(float)typography.lineHeight()-1);}
    }
    private final class Code extends InlineView {
        Code(Element element){super(element);setGlyphPainter(new ShapedPainter());}
        @Override public float getPreferredSpan(int axis) {
            float span=super.getPreferredSpan(axis);
            if(axis==X_AXIS&&ancestor(getElement(),HTML.Tag.PRE)) {
                var run=codeLines.computeIfAbsent(getElement().getParentElement(),line -> shapeCodeLine(line,getParent())).get(getElement());
                float origin=run.originAt(getStartOffset());
                // Swing allocates integer child spans. Round cumulative edges, not every run's width, so
                // highlighting cannot accumulate truncation drift or change the next logical tab column.
                return Math.round(origin+span)-Math.round(origin);
            }
            return span;
        }
        @Override public float getMinimumSpan(int axis){return ancestor(getElement(),HTML.Tag.PRE)&&axis==X_AXIS?getPreferredSpan(axis):super.getMinimumSpan(axis);}
        @Override public int getBreakWeight(int axis,float pos,float len){return ancestor(getElement(),HTML.Tag.PRE)&&axis==X_AXIS?BadBreakWeight:super.getBreakWeight(axis,pos,len);}
        @Override public Font getFont(){return fonts.primary().deriveFont(super.getFont().getStyle());}
    }
    /** Each preformatted paragraph is one logical line; syntax leaves share its tab origin. */
    private Map<Element,CodeRun> shapeCodeLine(Element line,View parent) {
        var runs=new java.util.IdentityHashMap<Element,CodeRun>();float origin=0;
        for(int i=0;i<line.getElementCount();i++) {
            Element element=line.getElement(i);int start=element.getStartOffset(),end=Math.min(element.getEndOffset(),element.getDocument().getLength());
            // HTMLReader stores inline font/weight/color styles on these leaf elements. Resolve them exactly as
            // the visible InlineView does, without building a second component or painting the whole line per token.
            var style=new InlineView(element);style.setParent(parent);
            try {
                String text=element.getDocument().getText(start,Math.max(0,end-start)).replace('\n',' ');
                var row=LineLayout.layout(text,start,List.of(new LineLayout.Span(start,end,style.getFont().getStyle(),style.getForeground())),
                    fonts,Float.MAX_VALUE,false,false,4,origin).getFirst();
                runs.put(element,new CodeRun(row,origin));
                origin+=row.width();
            }catch(BadLocationException failure){throw new IllegalStateException(failure);}
        }
        return runs;
    }
    /** One shared TextLayout geometry for code painting, selection edges and pointer hit testing. */
    private final class ShapedPainter extends GlyphView.GlyphPainter {
        private GlyphView cachedView;private int start=-1,end=-1;private EditorFonts cachedFonts;private LineLayout.Row cached;
        private LineLayout.Row row(GlyphView view){
            if(cachedView==view&&start==view.getStartOffset()&&end==view.getEndOffset()&&cachedFonts==fonts)return cached;
            cachedView=view;start=view.getStartOffset();end=Math.min(view.getEndOffset(),view.getDocument().getLength());cachedFonts=fonts;
            float origin=0;
            if(ancestor(view.getElement(),HTML.Tag.PRE)) {
                var run=codeLines.computeIfAbsent(view.getElement().getParentElement(),line -> shapeCodeLine(line,view.getParent())).get(view.getElement());
                if(start==run.row().start()&&end==run.row().end()){cached=run.row();return cached;}
                origin=run.originAt(start);
            }
            try {String text=view.getDocument().getText(start,Math.max(0,end-start)).replace('\n',' ');
                int style=(view.getFont().isBold()?Font.BOLD:0)|(view.getFont().isItalic()?Font.ITALIC:0);
                cached=LineLayout.layout(text,start,List.of(new LineLayout.Span(start,end,style,view.getForeground())),fonts,Float.MAX_VALUE,false,false,4,origin).getFirst();return cached;
            }catch(BadLocationException e){throw new IllegalStateException(e);}
        }
        @Override public GlyphView.GlyphPainter getPainter(GlyphView v,int p0,int p1){return new ShapedPainter();}
        @Override public float getSpan(GlyphView v,int p0,int p1,TabExpander e,float x){var row=row(v);if(p0==row.start()&&p1>=row.end())return row.width();return (float)LineLayout.highlight(row,p0,Math.min(p1,row.end())).getBounds2D().getWidth();}
        @Override public float getHeight(GlyphView v){return ancestor(v.getElement(),HTML.Tag.PRE)?fonts.height():fonts.naturalHeight();}
        @Override public float getAscent(GlyphView v){return fonts.baseline()-(fonts.height()-getHeight(v))/2;}
        @Override public float getDescent(GlyphView v){return getHeight(v)-getAscent(v);}
        @Override public void paint(GlyphView view,Graphics graphics,Shape allocation,int p0,int p1){var row=row(view);
            if(!graphics.getColor().equals(view.getForeground())) {
                try {String text=view.getDocument().getText(row.start(),row.end()-row.start()).replace('\n',' ');
                    float origin=0;
                    if(ancestor(view.getElement(),HTML.Tag.PRE)) {
                        var run=codeLines.get(view.getElement().getParentElement()).get(view.getElement());
                        origin=run.originAt(row.start());
                    }
                    row=LineLayout.layout(text,row.start(),List.of(new LineLayout.Span(row.start(),row.end(),view.getFont().getStyle(),graphics.getColor())),fonts,Float.MAX_VALUE,false,false,4,origin).getFirst();
                }catch(BadLocationException failure){throw new IllegalStateException(failure);}
            }
            var bounds=allocation.getBounds();var g=(Graphics2D)graphics.create();
            try {g.translate(bounds.x,bounds.y+getAscent(view));g.clip(LineLayout.highlight(row,p0,Math.min(p1,row.end())));
                for(var segment:row.segments())if(segment.layout()!=null)segment.layout().draw(g,segment.x(),0);
            }finally{g.dispose();}}
        @Override public Shape modelToView(GlyphView view,int pos,Position.Bias bias,Shape a)throws BadLocationException{var row=row(view);var r=a.getBounds();float x=(float)LineLayout.caret(row,new LineLayout.Hit(Math.min(pos,row.end()),bias==Position.Bias.Backward)).getBounds2D().getX();return new Rectangle2D.Float(r.x+x,r.y,1,getHeight(view));}
        @Override public int viewToModel(GlyphView view,float x,float y,Shape a,Position.Bias[] bias){var hit=LineLayout.hitInfo(row(view),x-a.getBounds().x);bias[0]=hit.backward()?Position.Bias.Backward:Position.Bias.Forward;return hit.offset();}
        @Override public int getBoundedPosition(GlyphView view,int p0,float x,float len){var row=row(view);float at=(float)LineLayout.caret(row,p0).getBounds2D().getX();return LineLayout.hit(row,at+len);}
    }
}

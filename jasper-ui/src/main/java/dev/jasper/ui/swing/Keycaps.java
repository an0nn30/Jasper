package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import javax.swing.Icon;

/** Shared row/candidate composition. No shortcut parsing or command behavior lives here. */
final class Keycaps implements Icon {
    private final KitContext context;
    private final KeycapSequence sequence;
    private final Font font;
    private final int[] widths;
    private final int width,height,pad,gap;
    /** Opaque light caps (a classic selected row's, DESIGN_LANGUAGE §6 "in a light box"), not the passive translucent ones. */
    private final boolean boxed;
    Keycaps(KitContext context,KeycapSequence sequence) { this(context,sequence,false); }
    /** {@code boxed}: each cap is an opaque {@code surface.card} face (no hover tint) inside its edge, so its glyphs read on an accent fill. */
    Keycaps(KitContext context,KeycapSequence sequence,boolean boxed) {
        this.context=context;this.sequence=sequence;this.boxed=boxed;font=ThemePainting.font(context,Typography.Role.CAPTION,String.join("",sequence.keys()));
        pad=ThemePainting.metric(context,DesignMetrics.Role.KEYCAP_PADDING);gap=ThemePainting.metric(context,DesignMetrics.Role.KEYCAP_GAP);
        var metrics=new javax.swing.JLabel().getFontMetrics(font);
        height=metrics.getHeight()+ThemePainting.metric(context,DesignMetrics.Role.KEYCAP_VERTICAL_PADDING);
        // A single symbol sits on a near-square cap; longer key names take their own width.
        int square=height+ThemePainting.metric(context,DesignMetrics.Role.KEYCAP_SQUARE_EXTRA);
        widths=sequence.keys().stream().mapToInt(key->Math.max(square,metrics.stringWidth(key)+2*pad)).toArray();
        width=java.util.Arrays.stream(widths).sum()+gap*(widths.length-1);
    }
    private Color translucent(SemanticColor role,DesignMetrics.Role alpha) {
        Color color=ThemePainting.color(context,role);
        return new Color(color.getRed(),color.getGreen(),color.getBlue(),context.metrics().value(alpha));
    }
    /** The caps' font: the caption role, in the platform face when the kit's face lacks one of the keys' glyphs. */
    Font font(){return font;}
    @Override public int getIconWidth(){return width;}
    @Override public int getIconHeight(){return height;}
    int minimumWidth() { return java.util.Arrays.stream(widths).max().orElse(0); }
    int heightAt(int available) {
        int line=0,lines=1;
        for(int w:widths) { if(line>0 && line+gap+w>available){lines++;line=0;} line+=(line>0?gap:0)+w; }
        return lines*height+(lines-1)*gap;
    }
    @Override public void paintIcon(Component component,Graphics graphics,int x,int y) { paintWithin(component,graphics,x,y,width); }
    void paintWithin(Component component,Graphics graphics,int x,int y,int available) {
        var g=(Graphics2D)graphics.create();
        try {
            ThemePainting.antialias(g);g.setFont(font);
            int radius=ThemePainting.metric(context,DesignMetrics.Role.KEYCAP_RADIUS),stroke=ThemePainting.metric(context,DesignMetrics.Role.HAIRLINE),base=ThemePainting.metric(context,DesignMetrics.Role.KEYCAP_BASE);
            int start=x,used=0;
            g.clipRect(x,y,Math.max(0,available),heightAt(available));
            for(int i=0;i<widths.length;i++) {
                if(used>0 && used+gap+widths[i]>available){x=start;y+=height+gap;used=0;}
                if(used>0){x+=gap;used+=gap;}
                // A passive cap: a translucent face inside a translucent outline that is heavier along the base, so it
                // reads on any row background, selected and editing rows included.
                var outer=new java.awt.geom.RoundRectangle2D.Double(x,y,widths[i],height,radius*2,radius*2);
                var face=new java.awt.geom.RoundRectangle2D.Double(x+stroke,y+stroke,Math.max(0,widths[i]-2*stroke),Math.max(0,height-stroke-base),Math.max(0,radius-stroke)*2,Math.max(0,radius-stroke)*2);
                var edge=new java.awt.geom.Area(outer);edge.subtract(new java.awt.geom.Area(face));
                // A boxed cap lays the light face under its whole outline first, so the translucent edge and face read as on a card.
                if(boxed){g.setColor(ThemePainting.color(context,SemanticColor.SURFACE_CARD));g.fill(outer);}
                g.setColor(translucent(SemanticColor.TEXT_TERTIARY,DesignMetrics.Role.KEYCAP_EDGE_ALPHA));g.fill(edge);
                if(!boxed){g.setColor(translucent(SemanticColor.HOVER_OVERLAY,DesignMetrics.Role.KEYCAP_FACE_ALPHA));g.fill(face);}
                g.setColor(ThemePainting.color(context,SemanticColor.TEXT_PRIMARY));
                var metrics=g.getFontMetrics();String key=sequence.keys().get(i);
                g.drawString(key,x+(widths[i]-metrics.stringWidth(key))/2,y+(height-base+stroke-metrics.getHeight())/2+metrics.getAscent());
                x+=widths[i];used+=widths[i];
            }
        } finally {g.dispose();}
    }
}

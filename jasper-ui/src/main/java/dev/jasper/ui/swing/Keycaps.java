package dev.jasper.ui.swing;

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
    Keycaps(KitContext context,KeycapSequence sequence) {
        this.context=context;this.sequence=sequence;font=ThemePainting.font(context,Typography.Role.CAPTION);
        pad=com.formdev.flatlaf.util.UIScale.scale(5);gap=com.formdev.flatlaf.util.UIScale.scale(3);
        var metrics=new javax.swing.JLabel().getFontMetrics(font);
        height=metrics.getHeight()+com.formdev.flatlaf.util.UIScale.scale(4);
        widths=sequence.keys().stream().mapToInt(key->Math.max(height,metrics.stringWidth(key)+2*pad)).toArray();
        width=java.util.Arrays.stream(widths).sum()+gap*(widths.length-1);
    }
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
            int radius=com.formdev.flatlaf.util.UIScale.scale(4);
            int start=x,used=0;
            g.clipRect(x,y,Math.max(0,available),heightAt(available));
            for(int i=0;i<widths.length;i++) {
                if(used>0 && used+gap+widths[i]>available){x=start;y+=height+gap;used=0;}
                if(used>0){x+=gap;used+=gap;}
                g.setColor(ThemePainting.color(context,SemanticColor.SURFACE_CONTROL));g.fillRoundRect(x,y,widths[i]-1,height-1,radius,radius);
                g.setColor(ThemePainting.color(context,SemanticColor.BORDER_CONTROL));g.drawRoundRect(x,y,widths[i]-1,height-1,radius,radius);
                g.setColor(ThemePainting.color(context,SemanticColor.TEXT_PRIMARY));
                var metrics=g.getFontMetrics();String key=sequence.keys().get(i);
                g.drawString(key,x+(widths[i]-metrics.stringWidth(key))/2,y+(height-metrics.getHeight())/2+metrics.getAscent());
                x+=widths[i];used+=widths[i];
            }
        } finally {g.dispose();}
    }
}

package dev.jasper.editor.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.text.*;
import javax.swing.text.html.HTML;

/** Paints only controller-supplied decoded images. Never constructs Swing's URL-loading ImageView. */
final class PreviewImageView extends View {
    private final Map<Integer,BufferedImage> images;
    private final Color foreground;
    private final Map<Integer,String> reasons;
    private final int id;
    private float width=160;
    PreviewImageView(Element element,Map<Integer,BufferedImage> images,Map<Integer,String> reasons,Color foreground){super(element);this.reasons=reasons;this.images=images;this.foreground=foreground;
        id=PreviewPane.resourceId((String)element.getAttributes().getAttribute(HTML.Attribute.SRC),"jasper-image:");}
    int id(){return id;}
    private BufferedImage image(){return images.get(id);}
    private float available(){var c=getContainer();if(c==null)return width;
        var viewport=(javax.swing.JViewport)javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class,c);
        return Math.max(1,(viewport==null?c.getWidth():viewport.getWidth())-40); }
    @Override public float getPreferredSpan(int axis){var image=image();float w=image==null?Math.min(420,available()):Math.min(image.getWidth(),available());return axis==X_AXIS?w:image==null?48:w*image.getHeight()/image.getWidth();}
    @Override public float getMinimumSpan(int axis){return axis==X_AXIS?1:getPreferredSpan(axis);}
    @Override public float getMaximumSpan(int axis){return getPreferredSpan(axis);}
    @Override public void setSize(float width,float height){this.width=width;}
    @Override public void paint(Graphics graphics,Shape allocation){var bounds=allocation.getBounds();var image=image();
        if(image!=null){graphics.drawImage(image,bounds.x,bounds.y,(int)getPreferredSpan(X_AXIS),(int)getPreferredSpan(Y_AXIS),null);return;}
        var g=graphics.create();try{g.setColor(foreground);g.clipRect(bounds.x,bounds.y,bounds.width,bounds.height);Object alt=getElement().getAttributes().getAttribute(HTML.Attribute.ALT);g.drawString(alt==null || alt.toString().isBlank()?"Image":alt.toString(),bounds.x,bounds.y+Math.min(20,bounds.height));g.drawString(reasons.getOrDefault(id,"Image not loaded"),bounds.x,bounds.y+Math.min(40,bounds.height));}finally{g.dispose();}}
    @Override public Shape modelToView(int pos,Shape a,Position.Bias b)throws BadLocationException {var r=a.getBounds();if(pos>=getEndOffset())r.x+=r.width;r.width=0;return r;}
    @Override public int viewToModel(float x,float y,Shape a,Position.Bias[] bias){bias[0]=Position.Bias.Forward;return x<a.getBounds().getCenterX()?getStartOffset():getEndOffset();}
}

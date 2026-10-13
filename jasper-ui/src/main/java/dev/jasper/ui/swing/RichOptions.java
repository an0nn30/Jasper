package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import javax.swing.*;

/** Semantic mode artwork and annotations, with popup wrapping measured at the actual menu width. */
final class RichOptions extends JPanel {
    private final KitContext context;
    private final Option option;
    private final boolean selected, popup, checked, settings;
    private final int availableWidth;
    RichOptions(KitContext context, Option option, boolean selected, boolean popup, boolean checked, int width) { this(context,option,selected,popup,checked,width,false); }
    /** {@code settings}: a Settings row's dropdown, whose face and menu use the Settings value sizes. */
    RichOptions(KitContext context, Option option, boolean selected, boolean popup, boolean checked, int width, boolean settings) {
        this.context=context;this.option=option;this.selected=selected;this.popup=popup;this.checked=checked;availableWidth=width;this.settings=settings;
        setOpaque(false);getAccessibleContext().setAccessibleName(option.label()+(option.description().isBlank()?"":" — "+option.description()));
    }
    private int pad(){return ThemePainting.metric(context,DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);}
    // Settings values use their compact glyph, label and annotation sizes, with the Settings icon-to-text gap.
    private int gap(){return ThemePainting.metric(context,settings?DesignMetrics.Role.SETTINGS_ICON_TEXT_GAP:DesignMetrics.Role.ICON_GAP);}
    private int glyph(){return com.formdev.flatlaf.util.UIScale.scale(context.icons().size(settings?Nodes.IconSize.CAPTION:Nodes.IconSize.CONTROL));}
    // A Settings value runs a little closer to its annotation than to its glyph.
    private int annotationGap(){return settings?ThemePainting.metric(context,DesignMetrics.Role.SETTINGS_ANNOTATION_GAP):gap();}
    // A Settings menu marks its current value with a lighter check than its option glyphs.
    private int check(){return settings?ThemePainting.metric(context,DesignMetrics.Role.SETTINGS_POPUP_CHECK):glyph();}
    private Font font(){return ThemePainting.font(context,settings?Typography.Role.LABEL:Typography.Role.BODY);}
    private Font secondary(){return ThemePainting.font(context,settings?Typography.Role.SETTING_DESCRIPTION:Typography.Role.CAPTION);}
    int labelWidth(int width){return Math.max(0,width-pad()-(popup?pad():ThemePainting.metric(context,DesignMetrics.Role.CHOICE_VALUE_END_PADDING))-(option.icon().isBlank()?0:glyph()+gap())-(option.description().isBlank()?0:getFontMetrics(secondary()).stringWidth(option.description())+annotationGap())-(popup?check()+gap():0));}
    private List<String> lines(int width){
        if(!popup)return List.of(option.label());
        var fm=getFontMetrics(font());var lines=new ArrayList<String>();String line="";
        for(var word:option.label().split(" ")){String candidate=line.isEmpty()?word:line+" "+word;if(!line.isEmpty()&&fm.stringWidth(candidate)>labelWidth(width)){lines.add(line);line=word;}else line=candidate;}
        if(!line.isEmpty())lines.add(line);return lines;
    }
    @Override public Dimension getPreferredSize(){
        int width=availableWidth>0?availableWidth:ThemePainting.metric(context,DesignMetrics.Role.SETTINGS_RICH_CHOICE_WIDTH);
        int text=getFontMetrics(font()).getHeight()*lines(width).size();
        // A Settings menu row keeps its height until its wrapped lines need more; their own leading pads them.
        int margin=settings?0:ThemePainting.metric(context,DesignMetrics.Role.MENU_SEPARATOR_MARGIN)*2;
        return new Dimension(width,popup?Math.max(ThemePainting.height(context,DesignMetrics.Role.MENU_ITEM_HEIGHT,settings?Typography.Role.LABEL:Typography.Role.BODY),text+margin):getFontMetrics(font()).getHeight());
    }
    private static String clip(String text,FontMetrics fm,int width){if(fm.stringWidth(text)<=width)return text;String ellipsis="…";while(!text.isEmpty()&&fm.stringWidth(text+ellipsis)>width)text=text.substring(0,text.offsetByCodePoints(text.length(),-1));return text.isEmpty()?"":text+ellipsis;}
    @Override protected void paintComponent(Graphics graphics){
        var g=(Graphics2D)graphics.create();try{
            ThemePainting.antialias(g);
            // A highlighted option is a plain rectangle across the row (spec 2).
            int h=getHeight();
            if(selected){g.setColor(ThemePainting.color(context,SemanticColor.SELECTION_BG));g.fillRect(0,0,getWidth(),h);}
            SemanticColor role=selected?SemanticColor.TEXT_ONACCENT:SemanticColor.TEXT_PRIMARY;int x=pad(),right=getWidth()-(popup?pad():ThemePainting.metric(context,DesignMetrics.Role.CHOICE_VALUE_END_PADDING));
            if(!option.icon().isBlank()){var icon=context.icons().icon(option.icon(),context.theme().semantic(),role,glyph());icon.paintIcon(this,g,x,(h-icon.getIconHeight())/2);x+=icon.getIconWidth()+gap();}
            if(popup){if(checked){var icon=context.icons().icon("check",context.theme().semantic(),selected?SemanticColor.TEXT_ONACCENT:SemanticColor.TEXT_LINK,check());icon.paintIcon(this,g,right-check(),(h-check())/2);}right-=check()+gap();}
            if(!option.description().isBlank()){
                g.setFont(secondary());var fm=g.getFontMetrics();int width=fm.stringWidth(option.description());
                g.setColor(ThemePainting.color(context,selected?SemanticColor.TEXT_ONACCENT_SECONDARY:SemanticColor.TEXT_TERTIARY));
                g.drawString(option.description(),right-width,(h-fm.getHeight())/2+fm.getAscent());right-=width+annotationGap();
            }
            g.setFont(font());g.setColor(ThemePainting.color(context,role));var fm=g.getFontMetrics();var lines=lines(getWidth());int y=(h-fm.getHeight()*lines.size())/2+fm.getAscent();
            for(var line:lines){g.drawString(clip(line,fm,Math.max(0,right-x)),x,y);y+=fm.getHeight();}
        }finally{g.dispose();}
    }
}

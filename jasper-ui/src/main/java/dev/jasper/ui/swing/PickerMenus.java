package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;

/** Shared rich face and menu paint; domain producers supply only metadata and user intent. */
final class PickerMenus {
    private PickerMenus() {}
    private static int metric(KitContext c,DesignMetrics.Role r){return ThemePainting.metric(c,r);}
    // The menu reads in the default face's type, so it matches the field that opened it.
    private static int height(KitContext c,DesignMetrics.Role r){return ThemePainting.height(c,r,Typography.Role.BODY);}
    static void paintFace(JButton button,Graphics graphics,KitContext c,Nodes.PickerFace face,boolean empty,boolean focus){
        paintFace(button,graphics,c,face,empty,focus,button.getModel().isRollover(),Boolean.TRUE.equals(button.getClientProperty("Jasper.pickerExpanded")));
    }
    /** The rich face on any control: a PICKER's button, or a DROPDOWN whose closed face describes its selected option. */
    static void paintFace(JComponent button,Graphics graphics,KitContext c,Nodes.PickerFace face,boolean empty,boolean focus,boolean hover,boolean expanded){
        c.faces().buttons.paintPicker(button,graphics,c,face,empty,focus,hover,expanded);
    }
    /** The default look's rich face: a tile, the primary text and caption, and the chevron. */
    static void paintRichFace(JComponent button,Graphics graphics,KitContext c,Nodes.PickerFace face,boolean empty,boolean focus,boolean hover,boolean expanded){
        var g=(Graphics2D)graphics.create();try{
            ThemePainting.antialias(g);
            if(!button.isEnabled())g.setComposite(AlphaComposite.SrcOver.derive(c.metrics().value(DesignMetrics.Role.PICKER_DISABLED_PERCENT)/100f));
            // Focused or open faces take the focus border inside their halo, as text fields do.
            ThemePainting.rounded(g,button,c,ThemePainting.color(c,button.isEnabled()&&hover?SemanticColor.SURFACE_CONTROL_HOVER:SemanticColor.SURFACE_CONTROL),ThemePainting.color(c,focus||expanded?SemanticColor.BORDER_FOCUS:SemanticColor.BORDER_CONTROL),focus||expanded,false);
            boolean compact=face.density()==Nodes.PickerDensity.COMPACT;
            int pad=metric(c,DesignMetrics.Role.PICKER_PADDING),gap=metric(c,compact?DesignMetrics.Role.PICKER_GAP:DesignMetrics.Role.PICKER_DEFAULT_FACE_GAP),tile=metric(c,compact?DesignMetrics.Role.PICKER_FACE_TILE:DesignMetrics.Role.PICKER_DEFAULT_FACE_TILE);
            int x=metric(c,compact?DesignMetrics.Role.PICKER_COMPACT_FACE_INSET:DesignMetrics.Role.PICKER_DEFAULT_FACE_INSET);
            tile(g,button,c,empty?"lock":face.icon(),empty?Nodes.Hue.NONE:face.hue(),x,(button.getHeight()-tile)/2,tile,false,metric(c,compact?DesignMetrics.Role.PICKER_COMPACT_FACE_GLYPH:DesignMetrics.Role.PICKER_DEFAULT_FACE_GLYPH));
            x+=tile+gap;
            var arrow=c.icons().icon(expanded?"chevron.up":"chevron.down",c.theme().semantic(),SemanticColor.TEXT_SECONDARY,metric(c,compact?DesignMetrics.Role.PICKER_COMPACT_FACE_CHEVRON:DesignMetrics.Role.PICKER_DEFAULT_FACE_CHEVRON));
            int right=button.getWidth()-pad-arrow.getIconWidth()-metric(c,DesignMetrics.Role.FOCUS_RING);
            arrow.paintIcon(button,g,right,(button.getHeight()-arrow.getIconHeight())/2);
            line(g,c,empty?"Choose…":face.primary(),empty?"":face.caption(),x,0,Math.max(0,right-gap-x),button.getHeight(),false,empty,face.density()==Nodes.PickerDensity.COMPACT,false,face.technicalCaption());
        }finally{g.dispose();}
    }
    private static void tile(Graphics2D g,JComponent owner,KitContext c,String name,Nodes.Hue hue,int x,int y,int size,boolean active){
        tile(g,owner,c,name,hue,x,y,size,active,metric(c,DesignMetrics.Role.PICKER_GLYPH));
    }
    private static void tile(Graphics2D g,JComponent owner,KitContext c,String name,Nodes.Hue hue,int x,int y,int size,boolean active,int glyphSize){
        if(name.isEmpty())return;
        if(hue!=Nodes.Hue.NONE){g.setColor(ColorMath.opacity(active?ThemePainting.color(c,SemanticColor.TEXT_ONSELECTION):Choices.hueSurface(c,hue),ThemePainting.tileShare(c)));int radius=metric(c,DesignMetrics.Role.CHIP_RADIUS);g.fillRoundRect(x,y,size,size,radius*2,radius*2);}
        SemanticColor role=active?SemanticColor.TEXT_ONSELECTION:switch(hue){case BLUE->SemanticColor.HUE_BLUE;case GREEN->SemanticColor.HUE_GREEN;case AMBER->SemanticColor.HUE_AMBER;case PURPLE->SemanticColor.HUE_PURPLE;case NEUTRAL,NONE->SemanticColor.TEXT_TERTIARY;};
        int glyph=Math.min(size,glyphSize);
        c.icons().icon(name,c.theme().semantic(),role,glyph).paintIcon(owner,g,x+(size-glyph)/2,y+(size-glyph)/2);
    }
    private static String clipped(FontMetrics fm,String text,int width){if(fm.stringWidth(text)<=width)return text;String ellipsis="…";if(fm.stringWidth(ellipsis)>width)return "";int end=text.length();while(end>0&&fm.stringWidth(text.substring(0,end)+ellipsis)>width)end=text.offsetByCodePoints(end,-1);return text.substring(0,end)+ellipsis;}
    /** A caption's type: a data value (a technical caption) in the data face, otherwise the density's caption. */
    static Typography.Role captionRole(boolean compact,boolean technical){
        return technical?Typography.Role.MONO_CAPTION:compact?Typography.Role.PICKER_CAPTION:Typography.Role.CAPTION;
    }
    static void line(Graphics2D g,KitContext c,String label,String caption,int x,int y,int width,int height,boolean active,boolean empty,boolean compact,boolean link,boolean technical){
        g.setFont(ThemePainting.font(c,compact?Typography.Role.PICKER_COMPACT:Typography.Role.BODY));var fm=g.getFontMetrics();int baseline=y+(height-fm.getHeight())/2+fm.getAscent();
        g.setColor(ThemePainting.color(c,active?SemanticColor.TEXT_ONSELECTION:link?SemanticColor.TEXT_LINK:empty?SemanticColor.TEXT_PLACEHOLDER:SemanticColor.TEXT_PRIMARY));
        String primary=clipped(fm,label,width);g.drawString(primary,x,baseline);int used=fm.stringWidth(primary)+metric(c,DesignMetrics.Role.PICKER_GAP);
        if(!caption.isEmpty()&&used<width){g.setFont(ThemePainting.font(c,captionRole(compact,technical)));g.setColor(ThemePainting.color(c,active?SemanticColor.TEXT_ONSELECTION_SECONDARY:SemanticColor.TEXT_TERTIARY));g.drawString(clipped(g.getFontMetrics(),caption,width-used),x+used,baseline);}
    }
    static final class MenuControl extends ControlRegistry.Control {
        final JTextField search;
        final TextFields.FieldSurface searchSurface;
        final JLabel searchIcon=Labels.literal();
        final DefaultListModel<Nodes.PickerItem> model=new DefaultListModel<>();
        final JList<Nodes.PickerItem> list=new JList<>(model){@Override public boolean getScrollableTracksViewportWidth(){return true;}};
        final JScrollPane scroll=new JScrollPane(list){@Override protected void paintChildren(Graphics graphics){
            super.paintChildren(graphics);
            if(model.isEmpty())return;
            var cell=list.getCellBounds(model.size()-1,model.size()-1);if(cell==null)return;
            int y=SwingUtilities.convertPoint(list,0,cell.y,this).y;
            // The footer divider is one full-width hairline in the menu border role, just above the footer gap.
            int stroke=metric(context(),DesignMetrics.Role.HAIRLINE);
            if(y>=stroke&&y<=getHeight()){graphics.setColor(context().faces().buttons.menuDivider(context()));graphics.fillRect(0,y-stroke,getWidth(),stroke);}
        }};
        final JLabel empty=Labels.literal();
        final JPanel body=new JPanel(new BorderLayout());
        final JPanel searchBox=new JPanel(new BorderLayout());
        boolean applying,composing;
        String query,appliedActive;
        MenuControl(KitView view,Nodes.PickerMenu node){
            super(view,node,new JPanel(new BorderLayout()){@Override protected void paintComponent(Graphics g){Menus.paintSurface(this,g,view.context());}});
            search=new JTextField();search.putClientProperty("html.disable",true);KitOwnership.mark(search,node.id()+".search");KitOwnership.mark(list,node.id()+".rows");
            component.setOpaque(false);body.setOpaque(false);searchBox.setOpaque(false);
            search.setOpaque(false);search.setBorder(new EmptyBorder(0,0,0,0));
            searchSurface=new TextFields.FieldSurface(context(),search,false);searchSurface.role=Typography.Role.BODY;searchSurface.heightRole=DesignMetrics.Role.PICKER_SEARCH_HEIGHT;searchSurface.radiusRole=DesignMetrics.Role.PICKER_SEARCH_RADIUS;
            searchSurface.focus=new ThemePainting.Focus(search,searchSurface,view);own(searchSurface.focus);
            searchSurface.add(search);searchSurface.add(searchIcon,BorderLayout.WEST);searchBox.add(searchSurface);component.add(searchBox,BorderLayout.NORTH);component.add(body,BorderLayout.CENTER);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setFocusable(false);list.setOpaque(false);list.setCellRenderer((l,value,index,selected,focus)->new Row(this,value,selected));
            scroll.setBorder(new EmptyBorder(0,0,0,0));scroll.setOpaque(false);scroll.getViewport().setOpaque(false);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            body.add(empty,BorderLayout.NORTH);body.add(scroll);empty.setHorizontalAlignment(SwingConstants.CENTER);
            DocumentListener document=new DocumentListener(){void changed(){if(!applying)emit(new UiEvent.Edited(MenuControl.this.node.id(),search.getText()));}public void insertUpdate(DocumentEvent e){changed();}public void removeUpdate(DocumentEvent e){changed();}public void changedUpdate(DocumentEvent e){changed();}};
            search.getDocument().addDocumentListener(document);own(()->search.getDocument().removeDocumentListener(document));
            InputMethodListener ime=new InputMethodListener(){public void inputMethodTextChanged(InputMethodEvent e){var text=e.getText();composing=text!=null&&text.getEndIndex()-text.getBeginIndex()>e.getCommittedCharacterCount();}public void caretPositionChanged(InputMethodEvent e){}};
            search.addInputMethodListener(ime);own(()->search.removeInputMethodListener(ime));
            ListSelectionListener selection=e->{if(!e.getValueIsAdjusting()&&!applying&&list.getSelectedValue()!=null)emit(new UiEvent.Selected(this.node.id(),Set.of(list.getSelectedValue().id())));};
            list.addListSelectionListener(selection);own(()->list.removeListSelectionListener(selection));
            MouseAdapter click=new MouseAdapter(){public void mousePressed(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e)){int i=list.locationToIndex(e.getPoint());if(i>=0&&list.getCellBounds(i,i).contains(e.getPoint())){list.setSelectedIndex(i);accept();}}}};
            list.addMouseListener(click);own(()->list.removeMouseListener(click));
            bind("DOWN","down",()->move(1));bind("UP","up",()->move(-1));bind("ENTER","enter",this::accept);bind("ESCAPE","escape",()->emit(new UiEvent.Action(((Nodes.PickerMenu)this.node).cancelAction())));
            refresh();
        }
        void bind(String stroke,String name,Runnable action){search.getInputMap().put(KeyStroke.getKeyStroke(stroke),"picker."+name);search.getActionMap().put("picker."+name,new AbstractAction(){public void actionPerformed(ActionEvent event){if(interactive()&&!composing)action.run();}});}
        void move(int delta){if(model.isEmpty())return;list.setSelectedIndex(Math.floorMod(list.getSelectedIndex()+delta,model.size()));list.ensureIndexIsVisible(list.getSelectedIndex());}
        void accept(){if(composing||list.getSelectedValue()==null)return;emit(new UiEvent.Action(list.getSelectedValue().id()));}
        @Override JComponent focusTarget(){return search;}
        @Override void refresh(){state();var n=(Nodes.PickerMenu)node;applying=true;try{
            if(!Objects.equals(query,n.query())){query=n.query();if(!search.getText().equals(query))search.setText(query);}
            search.setEnabled(interactive());search.setFont(ThemePainting.font(context(),Typography.Role.BODY));search.setForeground(ThemePainting.color(context(),SemanticColor.TEXT_PRIMARY));search.setCaretColor(search.getForeground());search.setBackground(ThemePainting.color(context(),SemanticColor.SURFACE_INPUT));
            searchSurface.hint=n.placeholder();searchIcon.setIcon(context().icons().icon("search",context().theme().semantic(),SemanticColor.TEXT_TERTIARY,metric(context(),DesignMetrics.Role.PICKER_SEARCH_GLYPH)));
            searchIcon.setBorder(new EmptyBorder(0,0,0,metric(context(),DesignMetrics.Role.PICKER_SEARCH_GAP)));
            searchIcon.setVisible(context().faces().fields.glyph(Nodes.FieldKind.SEARCH));
            search.getAccessibleContext().setAccessibleName(n.placeholder());
            int pad=metric(context(),DesignMetrics.Role.PICKER_PADDING);searchBox.setBorder(new EmptyBorder(metric(context(),DesignMetrics.Role.PICKER_SEARCH_TOP),metric(context(),DesignMetrics.Role.PICKER_SEARCH_SIDE),pad,metric(context(),DesignMetrics.Role.PICKER_SEARCH_SIDE)));
            searchSurface.setBorder(new EmptyBorder(0,metric(context(),DesignMetrics.Role.PICKER_SEARCH_PADDING),0,metric(context(),DesignMetrics.Role.PICKER_SEARCH_PADDING)));
            searchBox.setPreferredSize(new Dimension(0,metric(context(),DesignMetrics.Role.PICKER_SEARCH_TOP)+height(context(),DesignMetrics.Role.PICKER_SEARCH_HEIGHT)+pad));
            var rows=new ArrayList<>(n.items());rows.add(new Nodes.PickerItem(n.footerAction(),n.footerLabel(),"","add",Nodes.Hue.NONE,Nodes.PickerItemKind.UTILITY));
            boolean changedRows=!Collections.list(model.elements()).equals(rows);
            if(changedRows){model.clear();model.addAll(rows);}
            if(changedRows||!Objects.equals(appliedActive,n.activeId())){
                for(int i=0;i<model.size();i++)if(model.get(i).id().equals(n.activeId())){list.setSelectedIndex(i);break;}
                appliedActive=n.activeId();
            }
            list.setFixedCellHeight(-1);list.setVisibleRowCount(Math.min(9,model.size()));list.setBorder(new EmptyBorder(0,0,metric(context(),DesignMetrics.Role.PICKER_MENU_BOTTOM),0));list.setFont(search.getFont());
            int bottom=metric(context(),DesignMetrics.Role.PICKER_MENU_BOTTOM),shown=Math.min(rows.stream().limit(9).mapToInt(item->rowHeight(this,item)).sum()+bottom,Math.max(height(context(),DesignMetrics.Role.PICKER_UTILITY_HEIGHT),view.availableHeight()-searchBox.getPreferredSize().height-(n.items().isEmpty()?metric(context(),DesignMetrics.Role.PICKER_EMPTY_HEIGHT):0)));
            // Rows that scroll leave the widest one its width beside the scrollbar.
            int bar=rows.stream().mapToInt(item->rowHeight(this,item)).sum()+bottom>shown?scroll.getVerticalScrollBar().getPreferredSize().width:0;
            scroll.setPreferredSize(new Dimension(list.getPreferredSize().width+bar,shown));
            empty.setBorder(new EmptyBorder(0,0,metric(context(),DesignMetrics.Role.PICKER_EMPTY_TEXT_INSET),0));empty.setText(n.noMatches());empty.setVisible(n.items().isEmpty());empty.setFont(ThemePainting.font(context(),Typography.Role.CAPTION));empty.setForeground(ThemePainting.color(context(),SemanticColor.TEXT_TERTIARY));empty.setPreferredSize(new Dimension(0,metric(context(),DesignMetrics.Role.PICKER_EMPTY_HEIGHT)));
        }finally{applying=false;}component.revalidate();component.repaint();}
    }
    private static int rowHeight(MenuControl owner,Nodes.PickerItem item){return height(owner.context(),item.kind()==Nodes.PickerItemKind.UTILITY?DesignMetrics.Role.PICKER_UTILITY_HEIGHT:DesignMetrics.Role.PICKER_ROW_HEIGHT)+(item.id().equals(((Nodes.PickerMenu)owner.node).footerAction())?metric(owner.context(),DesignMetrics.Role.PICKER_FOOTER_TOP):0);}
    private static final class Row extends JComponent {
        final MenuControl owner;final Nodes.PickerItem item;final boolean active;
        Row(MenuControl owner,Nodes.PickerItem item,boolean active){this.owner=owner;this.item=item;this.active=active;setOpaque(false);}
        /** A row reports its content, so the menu is as wide as its widest row (and the popover at least its face). */
        @Override public Dimension getPreferredSize(){
            var c=owner.context();
            int pad=metric(c,DesignMetrics.Role.PICKER_PADDING),tile=metric(c,DesignMetrics.Role.PICKER_TILE),gap=metric(c,DesignMetrics.Role.PICKER_ROW_GAP);
            int text=getFontMetrics(ThemePainting.font(c,Typography.Role.BODY)).stringWidth(item.label());
            if(!item.caption().isEmpty())text+=metric(c,DesignMetrics.Role.PICKER_GAP)+getFontMetrics(ThemePainting.font(c,Typography.Role.CAPTION)).stringWidth(item.caption());
            int lead=c.faces().buttons.tiles()?tile+gap:gap;
            return new Dimension(pad+lead+text+gap+tile+pad,rowHeight(owner,item));
        }
        @Override protected void paintComponent(Graphics graphics){var c=owner.context();var n=(Nodes.PickerMenu)owner.node;var g=(Graphics2D)graphics.create();try{
            ThemePainting.antialias(g);boolean footer=item.id().equals(n.footerAction());
            int pad=metric(c,DesignMetrics.Role.PICKER_PADDING),tile=metric(c,DesignMetrics.Role.PICKER_TILE),gap=metric(c,DesignMetrics.Role.PICKER_ROW_GAP);
            int top=footer?metric(c,DesignMetrics.Role.PICKER_FOOTER_TOP):0,rowHeight=getHeight()-top;
            if(active){g.setColor(ThemePainting.color(c,SemanticColor.SELECTION_BG));g.fillRect(0,top,getWidth(),rowHeight);}
            if(!c.faces().buttons.tiles()){}
            else if(item.kind()==Nodes.PickerItemKind.UTILITY){int glyph=metric(c,DesignMetrics.Role.PICKER_GLYPH);c.icons().icon(item.icon(),c.theme().semantic(),active?SemanticColor.TEXT_ONSELECTION:footer?SemanticColor.TEXT_LINK:SemanticColor.TEXT_TERTIARY,glyph).paintIcon(this,g,pad+(tile-glyph)/2,top+(rowHeight-glyph)/2);}
            else tile(g,this,c,item.icon(),item.hue(),pad,(getHeight()-tile)/2,tile,active);
            int x=pad+(c.faces().buttons.tiles()?tile+gap:gap);line(g,c,item.label(),item.caption(),x,top,getWidth()-x-pad-tile,rowHeight,active,false,false,footer,false);
            if(item.id().equals(n.checkedId()))c.icons().icon("check",c.theme().semantic(),active?SemanticColor.TEXT_ONSELECTION:SemanticColor.TEXT_LINK,metric(c,DesignMetrics.Role.ICON_CAPTION)).paintIcon(this,g,getWidth()-pad-tile,(getHeight()-tile)/2);
        }finally{g.dispose();}}
    }
}

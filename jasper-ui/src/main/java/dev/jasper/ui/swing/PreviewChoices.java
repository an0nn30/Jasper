package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

/** Reusable theme-source tiles. Color samples are data; all thumbnail geometry is engine-owned. */
final class PreviewChoices extends ControlRegistry.Control {
    private final Map<String, Tile> tiles = new LinkedHashMap<>();
    PreviewChoices(KitView view, Nodes.Choice node) {
        super(view,node,new JPanel()); component.setOpaque(false); own(this::closeTiles); refresh();
    }
    @Override void refresh() {
        state(); var choice = (Nodes.Choice)node;
        component.getAccessibleContext().setAccessibleName(choice.label());
        if (!new ArrayList<>(tiles.keySet()).equals(choice.options().stream().map(Option::id).toList())) {
            closeTiles(); component.removeAll();
            for (var option : choice.options()) {
                var tile = new Tile(); tiles.put(option.id(),tile); component.add(tile);
                KitOwnership.mark(tile,choice.id()+".option."+option.id());
                tile.addActionListener(event -> { if(interactive()) emit(new UiEvent.Selected(node.id(),Set.of(tile.option.id()))); });
                tile.focus = new ThemePainting.Focus(tile,tile,view);
                for (String stroke : new String[]{"LEFT","RIGHT","HOME","END"}) {
                    tile.getInputMap().put(KeyStroke.getKeyStroke(stroke),stroke);
                    tile.getActionMap().put(stroke,new AbstractAction() {
                        @Override public void actionPerformed(ActionEvent event) {
                            if (!interactive()) return;
                            var buttons = new ArrayList<>(tiles.values()); int at = buttons.indexOf(tile);
                            int next = switch(stroke) {case "HOME" -> 0;case "END" -> buttons.size()-1;case "LEFT" -> Math.floorMod(at-1,buttons.size());default -> (at+1)%buttons.size();};
                            buttons.get(next).requestFocusInWindow(); buttons.get(next).doClick(0);
                        }
                    });
                }
            }
        }
        int pad=ThemePainting.metric(context(),DesignMetrics.Role.CARD_PADDING);component.setBorder(new javax.swing.border.EmptyBorder(pad,pad,pad,pad));
        component.setLayout(new GridLayout(1,Math.max(1,tiles.size()),ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_GAP),0));
        for (var option : choice.options()) {
            var tile = tiles.get(option.id());tile.option=option;tile.setSelected(option.id().equals(choice.selectedId()));tile.setEnabled(interactive());
            tile.setFont(ThemePainting.font(context(),Typography.Role.BODY));tile.getAccessibleContext().setAccessibleName(option.label());
            tile.getAccessibleContext().setAccessibleDescription(option.description()); tile.setToolTipText(option.description().isBlank()?null:option.description());
        }
        component.revalidate();component.repaint();
    }
    private void closeTiles(){tiles.values().forEach(tile->{if(tile.focus!=null)tile.focus.close();});tiles.clear();}
    @Override JComponent focusTarget() { return tiles.values().stream().filter(AbstractButton::isSelected).map(JComponent.class::cast).findFirst().orElse(tiles.isEmpty()?component:tiles.values().iterator().next()); }
    private final class Tile extends JButton {
        Option option; ThemePainting.Focus focus;
        @Override public javax.accessibility.AccessibleContext getAccessibleContext(){if(accessibleContext==null)accessibleContext=new AccessibleJButton(){@Override public javax.accessibility.AccessibleRole getAccessibleRole(){return javax.accessibility.AccessibleRole.TOGGLE_BUTTON;}@Override public javax.accessibility.AccessibleStateSet getAccessibleStateSet(){var states=super.getAccessibleStateSet();if(isSelected())states.add(javax.accessibility.AccessibleState.CHECKED);return states;}};return accessibleContext;}
        Tile() { setOpaque(false);setContentAreaFilled(false);setBorderPainted(false);setFocusPainted(false); }
        @Override public Dimension getPreferredSize() { return new Dimension(ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_WIDTH),ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_HEIGHT)); }
        @Override protected void paintComponent(Graphics graphics) {
            var g=(Graphics2D)graphics.create();
            try {
                ThemePainting.antialias(g);if(!isEnabled())g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                int pad=ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_INSET)/2,caption=ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_LABEL_HEIGHT);
                int h=getHeight()-caption;int radius=ThemePainting.metric(context(),DesignMetrics.Role.CONTROL_RADIUS);

                if(option!=null && option.preview().isPresent()) {
                    var preview=option.preview().get();var clip=g.getClip();g.clip(new java.awt.geom.RoundRectangle2D.Double(pad,pad,Math.max(0,getWidth()-pad*2),Math.max(0,h-pad*2),radius*2,radius*2));
                    miniature(g,preview.primary(),pad,pad,getWidth()-pad*2,h-pad*2);
                    preview.alternate().ifPresent(other->{g.clipRect(getWidth()/2,0,getWidth(),h);miniature(g,other,pad,pad,getWidth()-pad*2,h-pad*2);});g.setClip(clip);
                }
                if(isSelected()){g.setColor(ThemePainting.color(context(),SemanticColor.ACCENT));g.setStroke(new BasicStroke(2));g.drawRoundRect(1,1,getWidth()-3,h-3,radius*2,radius*2);}
                if(focus!=null&&focus.visible()){g.setColor(ThemePainting.color(context(),SemanticColor.ACCENT));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,radius*2,radius*2);}
                g.setFont(getFont());g.setColor(isSelected()?dev.jasper.ui.theme.SurfaceContrast.on(context().theme().semantic(),SemanticColor.TEXT_ONACCENT,SemanticColor.SURFACE_CARD,dev.jasper.ui.theme.SurfaceContrast.TEXT):ThemePainting.color(context(),SemanticColor.TEXT_SECONDARY));var fm=g.getFontMetrics();String label=option==null?"":option.label();if(fm.stringWidth(label)>getWidth()){while(!label.isEmpty()&&fm.stringWidth(label+"…")>getWidth())label=label.substring(0,label.offsetByCodePoints(label.length(),-1));label+="…";}g.drawString(label,(getWidth()-fm.stringWidth(label))/2,h+(caption-fm.getHeight())/2+fm.getAscent());
            } finally {g.dispose();}
        }
        private void miniature(Graphics2D g,Option.Palette p,int x,int y,int w,int h) {
            if(w<=0||h<=0)return;
            g.setColor(new Color(p.background(),true));g.fillRect(x,y,w,h);
            int side=w*36/100,row=Math.max(2,h/16),gap=Math.max(3,h/16);
            g.setColor(new Color(p.sidebar(),true));g.fillRect(x,y,side,h);
            g.setColor(new Color(p.control(),true));g.fillRoundRect(x+side+gap,y+gap,Math.max(1,w-side-gap*2),Math.max(1,h-gap*2),3,3);
            for(int i=0;i<3;i++){g.setColor(i==1?new Color(p.accent(),true):dev.jasper.ui.theme.ColorMath.opacity(new Color(p.text(),true),.45));g.fillRoundRect(x+gap,y+gap+i*(row+gap),Math.max(1,side*(i==0?67:i==1?50:60)/100),row,2,2);}
            g.setColor(dev.jasper.ui.theme.ColorMath.opacity(new Color(p.text(),true),.45));for(int i=0;i<2;i++)g.fillRoundRect(x+side+gap*2,y+gap*2+i*(row+gap),Math.max(1,(w-side-gap*4)*(i==0?50:70)/100),row,2,2);
            g.setColor(new Color(p.accent(),true));g.fillRoundRect(x+w-gap*2-w/7,y+h-gap*2-h/8,w/7,h/8,2,2);
        }
    }
}

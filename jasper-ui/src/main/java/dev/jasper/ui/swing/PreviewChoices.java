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
        // The tiles sit inside the card's stroke; the line below them belongs to the next row.
        component.setBorder(new javax.swing.border.EmptyBorder(context().faces().exclusive.previewPadding(context())));
        component.setLayout(context().faces().exclusive.previewLayout(context(), tiles.size()));
        for (var option : choice.options()) {
            var tile = tiles.get(option.id());tile.option=option;tile.setSelected(option.id().equals(choice.selectedId()));tile.setEnabled(interactive());
            tile.setFont(ThemePainting.font(context(),Typography.Role.SETTINGS_PREVIEW));tile.getAccessibleContext().setAccessibleName(option.label());
            tile.getAccessibleContext().setAccessibleDescription(option.description()); tile.setToolTipText(option.description().isBlank()?null:option.description());
        }
        component.revalidate();component.repaint();
    }
    private void closeTiles(){tiles.values().forEach(tile->{if(tile.focus!=null)tile.focus.close();});tiles.clear();}
    @Override JComponent focusTarget() { return tiles.values().stream().filter(AbstractButton::isSelected).map(JComponent.class::cast).findFirst().orElse(tiles.isEmpty()?component:tiles.values().iterator().next()); }
    /** One option's tile; its face (the exclusive point's) sizes and paints it. */
    final class Tile extends JButton {
        Option option; ThemePainting.Focus focus;
        @Override public javax.accessibility.AccessibleContext getAccessibleContext(){if(accessibleContext==null)accessibleContext=new AccessibleJButton(){@Override public javax.accessibility.AccessibleRole getAccessibleRole(){return javax.accessibility.AccessibleRole.TOGGLE_BUTTON;}@Override public javax.accessibility.AccessibleStateSet getAccessibleStateSet(){var states=super.getAccessibleStateSet();if(isSelected())states.add(javax.accessibility.AccessibleState.CHECKED);return states;}};return accessibleContext;}
        Tile() { setOpaque(false);setContentAreaFilled(false);setBorderPainted(false);setFocusPainted(false); }
        @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        /** The selection box, then the caption one line high below a fixed gap, so a larger font grows the tile. */
        @Override public Dimension getPreferredSize() { return context().faces().exclusive.previewSize(this); }
        /** The context the tile paints in. */
        KitContext context() { return PreviewChoices.this.context(); }
        /** The selection box over a caption line (the default face). */
        Dimension tileSize() {
            return new Dimension(ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_WIDTH),
                ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_HEIGHT)+ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_CAPTION_GAP)+getFontMetrics(getFont()).getHeight());
        }
        @Override protected void paintComponent(Graphics graphics) {
            var g=(Graphics2D)graphics.create();
            try { context().faces().exclusive.paintPreview(this, g); } finally { g.dispose(); }
        }
        /** The miniature, its selection ring and its caption (the default face). */
        void paintTile(Graphics2D g) {
            {
                ThemePainting.antialias(g);if(!isEnabled())g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                int pad=ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_INSET)/2,ring=ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_RING);
                int h=Math.min(getHeight(),ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_HEIGHT));int radius=ThemePainting.metric(context(),DesignMetrics.Role.CONTROL_RADIUS);
                if(option!=null && option.preview().isPresent()) {
                    var preview=option.preview().get();var clip=g.getClip();int inner=Math.max(0,radius-ring);
                    g.clip(new java.awt.geom.RoundRectangle2D.Double(pad,pad,Math.max(0,getWidth()-pad*2),Math.max(0,h-pad*2),inner*2,inner*2));
                    miniature(g,preview.primary(),pad,pad,getWidth()-pad*2,h-pad*2);
                    preview.alternate().ifPresent(other->{g.clipRect(getWidth()/2,0,getWidth(),h);miniature(g,other,pad,pad,getWidth()-pad*2,h-pad*2);});g.setClip(clip);
                }
                if(isSelected()){
                    // A filled ring keeps both edges on the pixel grid at every device scale.
                    var shape=new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(0,0,getWidth(),h,(radius+1)*2,(radius+1)*2));
                    shape.subtract(new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(ring,ring,Math.max(0,getWidth()-2*ring),Math.max(0,h-2*ring),Math.max(0,radius+1-ring)*2,Math.max(0,radius+1-ring)*2)));
                    g.setColor(ThemePainting.color(context(),SemanticColor.ACCENT));g.fill(shape);
                }
                if(focus!=null&&focus.visible()){g.setColor(ThemePainting.color(context(),SemanticColor.ACCENT));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,radius*2,radius*2);}
                g.setFont(getFont());g.setColor(isSelected()?ThemePainting.painted(context(),DefaultChoiceFace.SELECTED_CAPTION):ThemePainting.color(context(),SemanticColor.TEXT_SECONDARY));var fm=g.getFontMetrics();String label=option==null?"":option.label();if(fm.stringWidth(label)>getWidth()){while(!label.isEmpty()&&fm.stringWidth(label+"…")>getWidth())label=label.substring(0,label.offsetByCodePoints(label.length(),-1));label+="…";}
                g.drawString(label,(getWidth()-fm.stringWidth(label))/2,h+ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_CAPTION_GAP)+fm.getAscent());
            }
        }
        /**
         * A miniature window: sidebar with three items (the middle one selected), a raised panel with two lines and an
         * accent button at its lower right. Proportions follow the preview height so every scale draws the same picture.
         */
        void miniature(Graphics2D g,Option.Palette p,int x,int y,int w,int h) {
            if(w<=0||h<=0)return;
            g.setColor(new Color(p.background(),true));g.fillRect(x,y,w,h);
            int side=w*36/100,margin=Math.max(2,Math.round(h/12f)),pad=Math.max(2,Math.round(h/10f)),bar=Math.max(2,Math.round(h/16f));
            g.setColor(new Color(p.sidebar(),true));g.fillRect(x,y,side,h);
            int px=x+side+margin,py=y+margin,pw=Math.max(1,w-side-2*margin),ph=Math.max(1,h-2*margin);
            g.setColor(new Color(p.control(),true));g.fill(new java.awt.geom.RoundRectangle2D.Double(px,py,pw,ph,bar,bar));
            var line=dev.jasper.ui.theme.ColorMath.opacity(new Color(p.text(),true),.45);
            int[] items={67,51,59};
            for(int i=0;i<items.length;i++){g.setColor(i==1?new Color(p.accent(),true):line);g.fill(new java.awt.geom.RoundRectangle2D.Double(x+margin,y+pad+i*2*bar,Math.max(1,side*items[i]/100),bar,bar,bar));}
            int[] lines={49,66};
            g.setColor(line);for(int i=0;i<lines.length;i++)g.fill(new java.awt.geom.RoundRectangle2D.Double(px+pad,py+pad+i*2*bar,Math.max(1,pw*lines[i]/100),bar,bar,bar));
            int bw=Math.max(1,Math.round(w/8f)),bh=2*bar;
            g.setColor(new Color(p.accent(),true));g.fill(new java.awt.geom.RoundRectangle2D.Double(px+pw-pad-bw,py+ph-pad-bh,bw,bh,bar,bar));
        }
    }
}

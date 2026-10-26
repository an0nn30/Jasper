package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.swing.*;
import static dev.jasper.app.appearance.DesktopOwnerPaints.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Pixel probes for mounted optional control faces; expected colors only locate pixels inside the named owner. */
public final class DesktopFacePaints {
    private DesktopFacePaints() { }
    static int metric(KitContext c,DesignMetrics.Role role) {return com.formdev.flatlaf.util.UIScale.scale(c.metrics().value(role));}
    static JPanel host(KitContext c,KitView view,int width,int height) {
        var host=new JPanel(new BorderLayout());host.setBackground(c.kit().colors().dialogSurface().color(c.theme()));
        host.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));host.add(view);host.setSize(width,height);KitTestSupport.layout(host);return host;
    }
    public static int locate(BufferedImage image,Color expected,String where) {
        var pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
        assertThat(Arrays.stream(pixels).filter(p->p==expected.getRGB()).count()).as(where+" solid owner pixels").isGreaterThan(3);
        return Arrays.stream(pixels).filter(p->p==expected.getRGB()).findFirst().orElseThrow();
    }
    static void pair(KitContext c,List<Sample> out,String owner,String state,ColorRole ink,ColorRole background,
                     BufferedImage region,int ground,double minimum) {
        var guard=minimum==3?SurfaceContrast.Guard.INDICATOR:SurfaceContrast.Guard.TEXT;
        int actual=locate(region,SurfaceContrast.painted(c.theme(),new PaintPair(ink,background,guard)),owner+"/"+state);
        out.add(new Sample(owner,state,ink.key(),background.key(),actual,ground,minimum));
    }
    static void measure(KitContext c,boolean high,List<Sample> out) {
        toggles(c,out); switchParents(c,out); radios(c,high,out); stepper(c,high,out); route(c,high,out); prompt(c,out);
    }
    private static void toggles(KitContext c,List<Sample> out) {
        boolean classic=c.kit().id().equals("classic");
        for(String state:List.of("off","on","pending")) {
            var node=new Nodes.Toggle("toggle",ControlState.READY,"Enabled",state.equals("on"),"toggle",false,"",state.equals("pending"));
            try(var view=new KitView(c,new ViewSpec(node),e->{})) {
                var root=host(c,view,180,70);var button=KitTestSupport.require(view,"toggle",JButton.class);var image=crop(root,button);
                int outside=pixel(paint(root),2,2);
                if(classic) {
                    int size=metric(c,DesignMetrics.Role.CHECKBOX_BOX);double x=(button.getWidth()-size)/2.0,y=(button.getHeight()-size)/2.0;
                    int fill=locate(image,(state.equals("off")?SemanticColor.SURFACE_INPUT:state.equals("pending")?SemanticColor.STATUS_WARNING:SemanticColor.ACCENT).color(c.theme()),"checkbox fill"),edge=pixel(image,x+size/2.0,y+.25);
                    out.add(new Sample("checkbox",state+"-edge",state.equals("off")?"border.hover":"accent.edge",
                        state.equals("off")?"surface.input":c.kit().colors().dialogSurface().key(),edge,state.equals("off")?fill:outside,3));
                    if(!state.equals("off")) pair(c,out,"checkbox",state+"-mark",SemanticColor.TEXT_ONACCENT,
                        state.equals("pending")?SemanticColor.STATUS_WARNING:SemanticColor.ACCENT,image,fill,3);
                } else {
                    int track=locate(image,(state.equals("pending")?SemanticColor.STATUS_WARNING:state.equals("on")?SemanticColor.ACCENT:SemanticColor.TOGGLE_TRACK_OFF).color(c.theme()),"switch track");
                    int knob=locate(image,SemanticColor.TOGGLE_KNOB.color(c.theme()),"switch knob");
                    out.add(new Sample("switch",state,"toggle.knob",state.equals("pending")?"status.warning":state.equals("on")?"accent":"toggle.track.off",knob,track,3));
                    if(state.equals("pending"))out.add(new Sample("switch","pending-track","status.warning","toggle.knob",track,knob,3));
                    if(state.equals("off"))out.add(new Sample("switch","off-track","toggle.track.off",c.kit().colors().dialogSurface().key(),track,outside,3));
                }
            }
        }
    }
    private static void switchParents(KitContext c,List<Sample> out) {
        if(c.kit().id().equals("classic"))return;
        for(var parent:List.of(SemanticColor.SURFACE_APP,SemanticColor.SURFACE_CARD)) {
            var node=new Nodes.Toggle("toggle",ControlState.READY,"Enabled",false,"toggle",false);
            try(var view=new KitView(c,new ViewSpec(node),e->{})) {
                var root=host(c,view,180,70);root.setBackground(parent.color(c.theme()));
                var button=KitTestSupport.require(view,"toggle",JButton.class);var image=crop(root,button);
                out.add(new Sample("switch","off-track-on-"+parent.key(),"toggle.track.off",parent.key(),
                    locate(image,SemanticColor.TOGGLE_TRACK_OFF.color(c.theme()),"off track"),pixel(paint(root),2,2),3));
            }
        }
    }
    private static void radios(KitContext c,boolean high,List<Sample> out) {
        for(var hue:List.of(Nodes.Hue.BLUE,Nodes.Hue.GREEN,Nodes.Hue.AMBER,Nodes.Hue.PURPLE,Nodes.Hue.NEUTRAL)) {
            var node=new Nodes.Choice("radio",ControlState.READY,Nodes.ChoiceKind.EXCLUSIVE,List.of(
                new Option("a","MMMM",Optional.of(hue)),new Option("b","MMMM",Optional.empty())),"a","Choice","select");
            try(var view=new KitView(c,new ViewSpec(node),e->{})) {
                var root=host(c,view,400,80);
                for(String id:List.of("a","b")) {
                    var button=KitTestSupport.require(view,"radio.option."+id,JButton.class);var image=crop(root,button);
                    var icon=button.getIcon();button.setIcon(null);var textImage=crop(root,button);
                    button.setText("");var blank=crop(root,button);button.setText("MMMM");button.setIcon(icon);
                    boolean classic=c.kit().id().equals("classic"),selected=id.equals("a");
                    String bg=classic?c.kit().colors().dialogSurface().key():selected?"segment.selected.bg":"segment.track";
                    out.add(glyph("exclusive",hue+"-"+id,classic||selected?"text.primary":"text.secondary",bg,textImage,blank,high?7:4.5));
                    if(classic) {
                        int size=metric(c,DesignMetrics.Role.RADIO_RING);double y=(button.getHeight()-size)/2.0;
                        int inside=pixel(image,size/2.0,y+2),edge=pixel(image,size/2.0,y+.5);
                        out.add(new Sample("radio-ring",hue+"-"+id,selected?"accent.edge":"border.hover","surface.input",edge,inside,3));
                        if(selected) {
                            out.add(new Sample("radio-dot",hue.name(),"accent","surface.input",pixel(image,size/2.0,y+size/2.0),inside,3));
                            int swatch=metric(c,DesignMetrics.Role.OPTION_SWATCH),gap=metric(c,DesignMetrics.Role.RADIO_LABEL_GAP);
                            out.add(new Sample("radio-swatch",hue.name(),"hue."+hue.name().toLowerCase(Locale.ROOT),bg,
                                pixel(image,size+gap+swatch/2.0,button.getHeight()/2.0),pixel(paint(root),2,2),3));
                        }
                    }
                }
            }
        }
    }
    private static void stepper(KitContext c,boolean high,List<Sample> out) {
        var node=new Nodes.Field("stepper",ControlState.READY,Nodes.FieldKind.NUMBER,"5","MMMM","Count","",Optional.of(new Nodes.Numeric(0,10,1,0)),Nodes.FieldPresentation.STEPPER);
        try(var view=new KitView(c,new ViewSpec(node),e->{})) {
            var root=host(c,view,220,100);var input=KitTestSupport.require(view,"stepper.input",JTextField.class);
            var unit=Arrays.stream(input.getParent().getComponents()).filter(JLabel.class::isInstance).map(JLabel.class::cast).findFirst().orElseThrow();
            var image=crop(root,unit);String text=unit.getText();unit.setText("");var blank=crop(root,unit);unit.setText(text);
            out.add(glyph("stepper","unit","text.placeholder","surface.input",image,blank,high?7:4.5));
            if(c.kit().id().equals("classic"))for(String id:List.of("plus","minus")) {
                var button=KitTestSupport.require(view,"stepper."+id,JButton.class);image=crop(root,button);
                // The gradient is horizontal-constant. Sample adjacent to each solid arrow pixel at the same scanline.
                int ink=locate(image,SurfaceContrast.painted(c.theme(),new PaintPair(SemanticColor.TEXT_SECONDARY,SemanticColor.BUTTON_SECONDARY_BG,SurfaceContrast.Guard.INDICATOR)),"stepper arrow");
                int ground=0;double worst=22;
                for(int y=4;y<image.getHeight()-4;y++)for(int x=4;x<image.getWidth()-4;x++)if(image.getRGB(x,y)==ink) {
                    int bg=image.getRGB(6,y);double ratio=ColorMath.contrast(new Color(ink),new Color(bg));if(ratio<worst){worst=ratio;ground=bg;}
                }
                out.add(new Sample("stepper",id,"text.secondary","button.secondary.bg",ink,ground,3));
            }
        }
    }
    private static void route(KitContext c,boolean high,List<Sample> out) {
        var steps=new ArrayList<Nodes.Node>();
        for(var hue:List.of(Nodes.Hue.BLUE,Nodes.Hue.GREEN,Nodes.Hue.AMBER,Nodes.Hue.PURPLE,Nodes.Hue.NEUTRAL)) {
            String id=hue.name();steps.add(new Nodes.Stack(id,ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
                new Nodes.Icon(id+".icon",ControlState.READY,"terminal",hue,Nodes.IconSize.CONTROL,"Route"),new Nodes.Text(id+".caption",ControlState.READY,"MMMM",Nodes.TextRole.CAPTION))));
        }
        try(var view=new KitView(c,new ViewSpec(new Nodes.Stack("route",ControlState.READY,Nodes.LayoutRole.ROUTE,steps)),e->{})) {
            var root=host(c,view,750,100);var strip=KitTestSupport.require(view,"route",JComponent.class);var image=crop(root,strip);
            if(c.kit().id().equals("classic")) {
                int top=metric(c,DesignMetrics.Role.ROUTE_TOP);int ground=pixel(image,strip.getWidth()-3,top+3);
                out.add(new Sample("route","outline","border.control",c.kit().colors().dialogSurface().key(),pixel(image,strip.getWidth()/2.0,top+.5),pixel(paint(root),2,2),3));
                int edge=metric(c,DesignMetrics.Role.ROUTE_EDGE),padding=metric(c,DesignMetrics.Role.ROUTE_PADDING),size=metric(c,DesignMetrics.Role.ROUTE_ARROW);
                var next=KitTestSupport.require(view,"GREEN",JComponent.class);
                int arrowX=next.getX()-edge-padding-metric(c,DesignMetrics.Role.ROUTE_ARROW_INSET)-size;
                var arrowImage=image.getSubimage(arrowX*4,(top+(strip.getHeight()-top-size)/2)*4,size*4,size*4);
                pair(c,out,"route","arrow",SemanticColor.TEXT_PLACEHOLDER,SemanticColor.SURFACE_INPUT,arrowImage,ground,3);
                for(var hue:List.of(Nodes.Hue.BLUE,Nodes.Hue.GREEN,Nodes.Hue.AMBER,Nodes.Hue.PURPLE,Nodes.Hue.NEUTRAL)) {
                    var step=KitTestSupport.require(view,hue.name(),JComponent.class);
                    out.add(new Sample("route","hue."+hue.name().toLowerCase(Locale.ROOT),"hue."+hue.name().toLowerCase(Locale.ROOT),"surface.input",
                        pixel(image,step.getX()-padding-edge+.5,top+3),ground,3));
                }
            }
            for(var hue:List.of(Nodes.Hue.BLUE,Nodes.Hue.GREEN,Nodes.Hue.AMBER,Nodes.Hue.PURPLE,Nodes.Hue.NEUTRAL)) {
                var label=KitTestSupport.require(view,hue.name()+".caption",JLabel.class);var painted=crop(root,label);label.setText("");var blank=crop(root,label);label.setText("MMMM");
                out.add(glyph("route-caption",hue.name(),"text.secondary",c.kit().id().equals("classic")?"surface.input":"surface.card",painted,blank,high?7:4.5));
            }
        }
    }
    private static void prompt(KitContext c,List<Sample> out) {
        var node=new Nodes.Stack("prompt",ControlState.READY,Nodes.LayoutRole.PROMPT_HEADER,List.of(
            new Nodes.Icon("prompt.icon",ControlState.READY,"play.filled",Nodes.Hue.BLUE,Nodes.IconSize.PROMPT,"Prompt"),
            new Nodes.Text("prompt.message",ControlState.READY,"Message",Nodes.TextRole.DESCRIPTION)));
        try(var view=new KitView(c,new ViewSpec(node),e->{})) {
            var root=host(c,view,360,100);var icon=KitTestSupport.require(view,"prompt.icon",JLabel.class);
            if(c.kit().id().equals("classic")) {
                var tile=(JComponent)icon.getParent();var image=crop(root,tile);
                int fill=locate(image,SemanticColor.ACCENT.color(c.theme()),"prompt fill");
                pair(c,out,"prompt","glyph",SemanticColor.TEXT_ONACCENT,SemanticColor.ACCENT,crop(root,icon),fill,3);
                out.add(new Sample("prompt","outline","accent.edge",c.kit().colors().dialogSurface().key(),pixel(image,tile.getWidth()/2.0,.5),pixel(paint(root),2,2),3));
            }
        }
    }
}

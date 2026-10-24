package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual custom faces and field perimeter paints. Sampling uses rendered pixels, never the resolver's pair colors. */
public final class DesktopOwnerPaints {
    public record Sample(String owner, String state, String inkRole, String backgroundRole, int ink, int background, double minimum) {
        double ratio() { return ColorMath.contrast(new Color(ink), new Color(background)); }
        String key() { return owner+"/"+state+"/"+inkRole+"/"+backgroundRole; }
    }
    private DesktopOwnerPaints() { }

    static List<Sample> measure(KitContext context, boolean high, String name, Path output) throws Exception {
        var samples=new ArrayList<Sample>();
        tabs(context,high,samples);
        picker(context,high,samples);
        fields(context,high,samples);
        buttons(context,high,samples);
        DesktopFacePaints.measure(context,high,samples);
        DesktopCompositionPaints.measure(context,high,samples);
        dev.jasper.app.palette.DesktopRunPaintFixture.measure(context,high,samples);
        dev.jasper.app.workspace.DesktopChromePaintFixture.measure(context,high,samples);
        DesktopHeaderPaints.measure(context,high,samples);
        DesktopRolePaints.measure(context,high,samples);
        assertThat(samples).extracting(Sample::key).doesNotHaveDuplicates();
        assertThat(samples.stream().filter(s->s.owner().equals("tabs")).count()).isEqualTo(4);
        assertThat(samples.stream().filter(s->s.owner().equals("picker-row")).count()).isEqualTo(4);
        assertThat(samples.stream().filter(s->s.owner().equals("field-halo")).count()).isEqualTo(10);
        assertThat(samples.stream().filter(s->s.owner().startsWith("field-")&&s.minimum()==3).count()).isEqualTo(40);
        var lines=new ArrayList<String>();lines.add("owner\tstate\tink-role\tbackground-role\tink\tbackground\tratio\tminimum");
        for(var sample:samples) {
            assertThat(sample.ratio()).as(name+" "+sample.key()).isGreaterThanOrEqualTo(sample.minimum());
            lines.add(String.format(Locale.ROOT,"%s\t%s\t%s\t%s\t%08x\t%08x\t%.8f\t%.1f",sample.owner(),sample.state(),
                sample.inkRole(),sample.backgroundRole(),sample.ink(),sample.background(),sample.ratio(),sample.minimum()));
        }
        Files.write(output.resolve(name+"-owner-pixels.tsv"),lines);
        return List.copyOf(samples);
    }

    private static void tabs(KitContext c,boolean high,List<Sample> samples) {
        var node=new Nodes.Choice("tabs",ControlState.READY,Nodes.ChoiceKind.TABS,List.of(new Option("a","MMMM",Optional.empty()),
            new Option("b","MMMM",Optional.empty())),"a","Tabs","select");
        try(var view=new KitView(c,new ViewSpec(node),ignored->{})) {
            var host=new JPanel(new BorderLayout());host.setBackground(c.kit().colors().dialogSurface().color(c.theme()));
            host.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));host.add(view);host.setSize(380,90);KitTestSupport.layout(host);
            for(String id:List.of("a","b")) {
                var button=KitTestSupport.require(view,"tabs.option."+id,JButton.class);
                for(boolean hover:List.of(false,true)) {
                    button.getModel().setRollover(hover);
                    var image=crop(host,button);button.setText("");var blank=crop(host,button);button.setText("MMMM");
                    boolean active=id.equals("a"),classic=c.kit().id().equals("classic");
                    String ink=classic||active?"text.primary":"text.secondary";
                    String fill=classic?(active?"surface.card":"surface.stripe"):(active?"segment.selected.bg":"segment.track");
                    samples.add(glyph("tabs",(active?"selected":"unselected")+(hover?"-hover":""),ink,fill,image,blank,high?7:4.5));
                }
                if(c.kit().id().equals("classic")) {
                    var image=paint(host);var at=SwingUtilities.convertPoint(button,0,0,host);double x=at.x+button.getWidth()/2.0;
                    samples.add(new Sample("tab-outline",id.equals("a")?"selected":"unselected","border.control",c.kit().colors().dialogSurface().key(),
                        pixel(image,x,at.y+.5),pixel(image,x,at.y-3),3));
                }
            }
            if(!c.kit().id().equals("classic")) {
                var track=(JComponent)KitTestSupport.require(view,"tabs.option.a",JButton.class).getParent();
                var image=paint(host);var at=SwingUtilities.convertPoint(track,0,0,host);double x=at.x+track.getWidth()/2.0;
                samples.add(new Sample("tab-track","outline","border.input",c.kit().colors().dialogSurface().key(),
                    pixel(image,x,at.y+.5),pixel(image,x,at.y-3),3));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void picker(KitContext c,boolean high,List<Sample> samples) {
        // The production PickerMenus.Row renderer is not a JLabel. Blank one item property in the live model and
        // paint the actual list inside its MenuSurface, preserving the row allocation and its selected state.
        var item=new Nodes.PickerItem("row","MMMM","","",Nodes.Hue.NONE);
        var node=new Nodes.PickerMenu("menu",ControlState.READY,"","Search",List.of(item),"","","No matches","Create","create","cancel");
        try(var view=new KitView(c,new ViewSpec(node),ignored->{})) {
            view.paintsSurface(true);KitFixtures.render(view,360,160,1);
            var list=(JList<Nodes.PickerItem>)KitTestSupport.require(view,"menu.rows",JList.class);
            var model=(DefaultListModel<Nodes.PickerItem>)list.getModel();
            for(boolean selected:List.of(false,true)) for(boolean caption:List.of(false,true)) {
                model.set(0,new Nodes.PickerItem("row",caption?"":"MMMM",caption?"MMMM":"","",Nodes.Hue.NONE));
                if(selected)list.setSelectedIndex(0);else list.clearSelection();
                var image=crop(view,list);
                model.set(0,new Nodes.PickerItem("row","","","",Nodes.Hue.NONE));
                var blank=crop(view,list);
                String ink=selected?(caption?"text.onSelection.secondary":"text.onSelection"):(caption?"text.tertiary":"text.primary");
                samples.add(glyph("picker-row",(selected?"selected":"normal")+(caption?"-caption":""),ink,
                    selected?"selection.bg":"surface.menu",image,blank,high?7:4.5));
            }
            selectedText(view,KitTestSupport.require(view,"menu.search",JTextField.class),"picker-search","surface.textSelection",high,samples);
        }
    }

    private static final class Focus extends DefaultKeyboardFocusManager {
        void target(Component component) {setGlobalFocusOwner(component);setGlobalPermanentFocusOwner(component);}
    }
    private static void fields(KitContext c,boolean high,List<Sample> samples) {
        var previous=KeyboardFocusManager.getCurrentKeyboardFocusManager();var focus=new Focus();
        KeyboardFocusManager.setCurrentKeyboardFocusManager(focus);
        try {
            for(var parent:List.of(SemanticColor.SURFACE_APP,SemanticColor.SURFACE_PANEL,SemanticColor.SURFACE_DIALOG,
                    SemanticColor.SURFACE_CARD,SemanticColor.SURFACE_INPUT)) {
                for(String state:List.of("normal","focus","error")) {
                    var status=new ControlState(true,true,false,state.equals("error")?"Invalid":"");
                    var node=new Nodes.Field("field",status,Nodes.FieldKind.TEXT,"","","Field","edited");
                    try(var view=new KitView(c,new ViewSpec(node),ignored->{})) {
                        // A real opaque host behind the transparent KitView, as the supported dialog/card mounts do.
                        var host=new JPanel(new BorderLayout());host.setBackground(parent.color(c.theme()));
                        host.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));host.add(view);host.setSize(360,130);
                        KitTestSupport.layout(host);
                        var input=KitTestSupport.require(view,"field.input",JTextField.class);
                        var surface=(JComponent)input.getParent();
                        if(state.equals("focus")) {
                            focus.target(input);
                            var event=new FocusEvent(input,FocusEvent.FOCUS_GAINED,false,null,FocusEvent.Cause.TRAVERSAL_FORWARD);
                            for(var listener:input.getFocusListeners())listener.focusGained(event);
                        }
                        var image=paint(host);var origin=SwingUtilities.convertPoint(surface,0,0,host);
                        double x=origin.x+surface.getWidth()/2.0;
                        int edge=pixel(image,x,origin.y+.5),inside=pixel(image,x,origin.y+3),outside=pixel(image,x,origin.y-5);
                        String role="border."+(state.equals("normal")?"input":state);
                        samples.add(new Sample("field-edge",state+"-on-"+parent.key(),role,"surface.input",edge,inside,3));
                        samples.add(new Sample("field-edge",state+"-on-"+parent.key()+"-outside",role,parent.key(),edge,outside,3));
                        if(!state.equals("normal")) {
                            assertThat(surface.getClientProperty("Jasper.kit.halo")).as("actual field registered its halo").isNotNull();
                            int halo=pixel(image,x,origin.y-.5);
                            samples.add(new Sample("field-halo",state+"-on-"+parent.key(),state+".halo",parent.key(),halo,outside,3));
                        }
                        if(state.equals("normal")&&parent==SemanticColor.SURFACE_PANEL)
                            selectedText(host,input,"field-selection","selection.bg",high,samples);
                        focus.target(null);
                    }
                }
            }
        } finally {focus.target(null);KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);}
    }

    private static void buttons(KitContext c,boolean high,List<Sample> samples) {
        for(var parent:List.of(SemanticColor.SURFACE_APP,SemanticColor.SURFACE_PANEL,SemanticColor.SURFACE_DIALOG,
                SemanticColor.SURFACE_CARD,SemanticColor.SURFACE_INPUT)) {
            for(var kind:List.of(Nodes.ButtonKind.PRIMARY,Nodes.ButtonKind.SECONDARY)) {
                var node=new Nodes.Button("button",ControlState.READY,"MMMM","",kind,"go","");
                try(var view=new KitView(c,new ViewSpec(node),ignored->{})) {
                    var host=new JPanel(new BorderLayout());host.setBackground(parent.color(c.theme()));
                    host.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));host.add(view);host.setSize(240,64);KitTestSupport.layout(host);
                    var button=KitTestSupport.require(view,"button",JButton.class);
                    boolean primary=kind==Nodes.ButtonKind.PRIMARY,classic=c.kit().id().equals("classic");
                    for(boolean hover:List.of(false,true)) {
                        button.getModel().setRollover(hover);
                        var image=crop(host,button);button.setText("");var blank=crop(host,button);button.setText("MMMM");
                        String state=kind.name().toLowerCase(Locale.ROOT)+(hover?"-hover":"")+"-on-"+parent.key();
                        samples.add(glyph("button-text",state,primary?"text.onAccent":classic?"text.primary":"text.strong.secondary",
                            primary?(hover?"accent.hover":"accent"):hover&&!classic?"button.secondary.bg.hover":"button.secondary.bg",image,blank,high?7:4.5));
                        if(!primary||classic) {
                            var full=paint(host);var at=SwingUtilities.convertPoint(button,0,0,host);double x=at.x+button.getWidth()/2.0;
                            String role=primary?"accent.edge":classic&&hover?"border.hover":"button.secondary.border";
                            samples.add(new Sample("button-edge",state,role,parent.key(),pixel(full,x,at.y+.5),pixel(full,x,at.y-3),3));
                        }
                    }
                }
            }
        }
    }

    private static void selectedText(JComponent root,JTextField field,String owner,String surface,boolean high,List<Sample> samples) {
        field.setText("MMMM");field.selectAll();field.getCaret().setSelectionVisible(true);
        var image=crop(root,field);var pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
        int ink=field.getSelectedTextColor().getRGB(),ground=field.getSelectionColor().getRGB();
        assertThat(Arrays.stream(pixels).filter(p->p==ink).count()).as(owner+" painted selection glyph").isGreaterThan(8);
        assertThat(Arrays.stream(pixels).filter(p->p==ground).count()).as(owner+" painted selection fill").isGreaterThan(8);
        samples.add(new Sample(owner,"selected","text.primary",surface,
            Arrays.stream(pixels).filter(p->p==ink).findFirst().orElseThrow(),Arrays.stream(pixels).filter(p->p==ground).findFirst().orElseThrow(),high?7:4.5));
    }

    public static int pixel(BufferedImage image,double x,double y) {return image.getRGB((int)(x*4),(int)(y*4));}
    public static BufferedImage crop(JComponent root,JComponent component) {
        var at=SwingUtilities.convertPoint(component,0,0,root);
        var image=new BufferedImage(component.getWidth()*4,component.getHeight()*4,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();try{g.scale(4,4);g.translate(-at.x,-at.y);root.paint(g);}finally{g.dispose();}
        return image;
    }
    public static BufferedImage paint(JComponent component) {
        var image=new BufferedImage(component.getWidth()*4,component.getHeight()*4,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();try{g.scale(4,4);component.paint(g);}finally{g.dispose();}return image;
    }
    public static Sample glyph(String owner,String state,String inkRole,String backgroundRole,BufferedImage image,BufferedImage blank,double minimum) {
        var counts=new HashMap<Integer,Integer>();
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
            if(image.getRGB(x,y)!=blank.getRGB(x,y))counts.merge(image.getRGB(x,y),1,Integer::sum);
        var ink=counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
        assertThat(ink.getValue()).as(owner+" "+state+" solid glyph pixels").isGreaterThan(8);
        double ratio=22;int ground=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)==ink.getKey()&&image.getRGB(x,y)!=blank.getRGB(x,y)) {
            double candidate=ColorMath.contrast(new Color(ink.getKey()),new Color(blank.getRGB(x,y)));
            if(candidate<ratio){ratio=candidate;ground=blank.getRGB(x,y);}
        }
        return new Sample(owner,state,inkRole,backgroundRole,ink.getKey(),ground,minimum);
    }
}

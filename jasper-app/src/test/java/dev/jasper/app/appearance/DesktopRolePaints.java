package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.theme.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import static dev.jasper.app.appearance.DesktopOwnerPaints.*;
import static dev.jasper.app.appearance.DesktopFacePaints.host;
import static dev.jasper.app.appearance.DesktopCompositionPaints.text;

/** Shared labels/links on supported opaque host surfaces, and severity notices through their own paint hooks. */
final class DesktopRolePaints {
    private DesktopRolePaints() { }
    static void measure(KitContext c,boolean high,List<Sample> out) {
        for(var parent:List.of(SemanticColor.SURFACE_APP,SemanticColor.SURFACE_PANEL,SemanticColor.SURFACE_DIALOG,SemanticColor.SURFACE_CARD,SemanticColor.SURFACE_SIDEBAR)) {
            var children=new ArrayList<Nodes.Node>();
            children.add(new Nodes.Text("primary",ControlState.READY,"MMMM",Nodes.TextRole.BODY));
            children.add(new Nodes.Text("secondary",ControlState.READY,"MMMM",Nodes.TextRole.DESCRIPTION));
            children.add(new Nodes.Text("tertiary",ControlState.READY,"MMMM",Nodes.TextRole.CAPTION));
            children.add(new Nodes.Button("link",ControlState.READY,"MMMM","",Nodes.ButtonKind.LINK,"go",""));
            children.add(new Nodes.Field("error",new ControlState(true,true,false,"MMMM"),Nodes.FieldKind.TEXT,"","","Value",""));
            var identityHead=new Nodes.Stack("identity-head",ControlState.READY,Nodes.LayoutRole.NAVIGATION,List.of(
                new Nodes.Icon("identity-icon",ControlState.READY,"server",Nodes.Hue.BLUE,Nodes.IconSize.CONTROL,"Identity")));
            children.add(new Nodes.Stack("identity",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(identityHead,
                new Nodes.StatusDot("identity-status",ControlState.READY,Nodes.StatusKind.SUCCESS),new Nodes.Text("success",ControlState.READY,"MMMM",Nodes.TextRole.CAPTION))));
            try(var view=new KitView(c,new ViewSpec(new Nodes.Stack("roles",ControlState.READY,Nodes.LayoutRole.VERTICAL,children)),e->{})) {
                view.setArtworkPresentation("identity-icon",ArtworkPresentation.IDENTITY_HEADER);
                var root=host(c,view,350,360);root.setBackground(parent.color(c.theme()));
                for(String role:List.of("primary","secondary","tertiary","success"))text(root,role,"shared-label",role+"-on-"+parent.key(),"text."+role,parent.key(),high,out);
                text(root,"error.error","shared-label","error-on-"+parent.key(),"text.error",parent.key(),high,out);
                var button=KitTestSupport.require(view,"link",JButton.class);
                for(boolean hover:List.of(false,true)) {
                    button.getModel().setRollover(hover);var image=crop(root,button);button.setText("");var blank=crop(root,button);button.setText("MMMM");
                    out.add(glyph("shared-link",(hover?"hover":"normal")+"-on-"+parent.key(),hover?"text.link.hover":"text.link",parent.key(),image,blank,high?7:4.5));
                }
            }
        }
        for(var severity:Nodes.Severity.values()) {
            var node=new Nodes.Notice("notice",ControlState.READY,severity,"MMMM",List.of(new Nodes.Button("link",ControlState.READY,"MMMM","",Nodes.ButtonKind.LINK,"go","")));
            try(var view=new KitView(c,new ViewSpec(new Nodes.Stack("notices",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(node))),e->{})) {
                var root=host(c,view,400,100);String ground=switch(severity){case INFO->"info.ground";case WARNING->"warning.ground";case DANGER->"danger.ground";};
                text(root,"notice.text","notice",severity+"-text","text.primary",ground,high,out);
                var button=KitTestSupport.require(view,"link",JButton.class);
                for(boolean hover:List.of(false,true)) {
                    button.getModel().setRollover(hover);var image=crop(root,button);button.setText("");var blank=crop(root,button);button.setText("MMMM");
                    out.add(glyph("notice",severity+(hover?"-hover":"-link"),hover?"text.link.hover":"text.link",ground,image,blank,high?7:4.5));
                }
            }
        }
    }
}

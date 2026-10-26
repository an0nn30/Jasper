package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.gallery.KitGallery;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import static dev.jasper.app.appearance.DesktopOwnerPaints.*;
import static dev.jasper.app.appearance.DesktopFacePaints.*;

/** Mounted compositions that own the classic label, property, table, tree, list, and preview declarations. */
public final class DesktopCompositionPaints {
    private DesktopCompositionPaints() { }
    public static void text(JComponent root,String id,String owner,String state,String ink,String ground,boolean high,List<Sample> out) {
        var component=KitTestSupport.require(root,id,JComponent.class);var image=crop(root,component);
        // WrappedText paints itself and is not a JLabel. Hiding just this leaf preserves the allocated layout.
        String saved=component instanceof JLabel label?label.getText():null;
        if(component instanceof JLabel label)label.setText("");else component.setVisible(false);
        var blank=crop(root,component);
        if(component instanceof JLabel label)label.setText(saved);else component.setVisible(true);
        out.add(glyph(owner,state,ink,ground,image,blank,high?7:4.5));
    }
    static void measure(KitContext c,boolean high,List<Sample> out) {
        labels(c,high,out);properties(c,high,out);previews(c,high,out);gallery(c,high,out);tags(c,high,out);readonly(c,high,out);combos(c,high,out);rows(c,high,out);
    }
    private static void labels(KitContext c,boolean high,List<Sample> out) {
        var field=new Nodes.Field("field",ControlState.READY,Nodes.FieldKind.TEXT,"MMMM","","Field","");
        var contents=List.<Nodes.Node>of(new Nodes.FormRow("row",ControlState.READY,"MMMM","MMMM",field,Optional.empty()),
            new Nodes.SettingRow("setting",ControlState.READY,"MMMM","MMMM",new Nodes.Toggle("toggle",ControlState.READY,"Toggle",false,"toggle",false),false),
            new Nodes.Empty("empty",ControlState.READY,new Nodes.Icon("empty-icon",ControlState.READY,"file",Nodes.Hue.BLUE,Nodes.IconSize.EMPTY,"Empty"),"MMMM","MMMM",List.of()),
            new Nodes.Secret("secret",ControlState.READY,"Password","Password",""));
        var node=new Nodes.Section("section",ControlState.READY,"MMMM",new Nodes.Card("card",ControlState.READY,Nodes.CardKind.FORM,contents));
        try(var view=new KitView(c,new ViewSpec(node),e->{})) {
            var root=host(c,view,640,500);boolean classic=c.kit().id().equals("classic");String bg=classic?"surface.app":"surface.card";
            text(root,"section.title","group","title","text.primary",c.kit().colors().dialogSurface().key(),high,out);
            text(root,"row.label","form-label","label","text.strong.secondary",bg,high,out);
            text(root,"row.caption","form-label","caption","text.secondary",bg,high,out);
            text(root,"setting.title","setting-label","title","text.primary",bg,high,out);
            text(root,"empty.title","empty","title","text.primary",bg,high,out);
            text(root,"empty.description","empty","description","text.secondary",bg,high,out);
            if(classic)text(root,"secret.show.label","reveal","label","text.primary",bg,high,out);
        }
    }
    private static void properties(KitContext c,boolean high,List<Sample> out) {
        for(boolean titled:List.of(false,true)) {
            var node=new Nodes.Properties("props",ControlState.READY,titled?"MMMM":"",List.of(new Nodes.PropertyRow("MMMM","MMMM",false)));
            try(var view=new KitView(c,new ViewSpec(node),e->{})) {
                var root=host(c,view,360,160);boolean classic=c.kit().id().equals("classic");
                String bg=classic&&!titled?"surface.app":"surface.card";
                if(titled)text(root,"props.title","properties","title","text.strong.secondary",classic?"surface.panel":bg,high,out);
                text(root,"props.label.0","properties",titled?"grid-label":"plain-label","text.secondary",classic&&titled?"surface.panel":bg,high,out);
                text(root,"props.value.0","properties",titled?"grid-value":"plain-value","text.primary",bg,high,out);
            }
        }
    }
    private static void previews(KitContext c,boolean high,List<Sample> out) {
        var choice=new Nodes.Choice("preview",ControlState.READY,Nodes.ChoiceKind.PREVIEWS,List.of(new Option("a","MMMM",Optional.empty())),"a","Preview","select");
        var node=new Nodes.Card("card",ControlState.READY,Nodes.CardKind.SETTINGS,List.of(choice));
        try(var view=new KitView(c,new ViewSpec(node),e->{})) {
            var root=host(c,view,400,220);var tile=KitTestSupport.require(view,"preview.option.a",JButton.class);var image=crop(root,tile);
            boolean classic=c.kit().id().equals("classic");int h=metric(c,dev.jasper.ui.metrics.DesignMetrics.Role.THEME_PREVIEW_HEIGHT);
            var caption=image.getSubimage(0,(h+2)*4,image.getWidth(),image.getHeight()-(h+2)*4);
            var bg=classic?SemanticColor.SURFACE_APP:SemanticColor.SURFACE_CARD;
            pair(c,out,"preview","caption",SemanticColor.TEXT_PRIMARY,bg,caption,pixel(caption,tile.getWidth()-2,2),high?7:4.5);
            if(classic)out.add(new Sample("preview","frame","accent","surface.app",pixel(image,tile.getWidth()/2.0,.5),pixel(paint(root),2,2),3));
        }
    }
    private static void gallery(KitContext c,boolean high,List<Sample> out) {
        for(String name:List.of("tables-0-18","classic-project-markers")) {
            var fixture=KitGallery.enterpriseCatalog().stream().filter(f->f.name().equals(name)).findFirst().orElseThrow();
            try(var view=new KitView(c,fixture.spec(),e->{})) {
                var root=host(c,view,fixture.width()+24,fixture.height()+24);
                if(c.kit().id().equals("classic")) {
                    if(name.equals("tables-0-18")) {
                        text(root,"@table/t18-groups/header/group","table","header","text.strong.secondary","surface.tableHeader",high,out);
                        text(root,"@table/t18-groups/t18-jasper/count","table","count","text.secondary","surface.tableHeader",high,out);
                        text(root,"@tree/t18-sessions/header/name","tree","header","text.strong.secondary","surface.tableHeader",high,out);
                        var disclosure=KitTestSupport.require(view,"@tree/t18-sessions/lan/expand",JButton.class);
                        var glyphImage=crop(root,disclosure);
                        pair(c,out,"tree","disclosure",SemanticColor.TEXT_SECONDARY,SemanticColor.SURFACE_CARD,glyphImage,pixel(glyphImage,disclosure.getWidth()-1,1),3);
                        for(String id:List.of("t18-groups","t18-sessions")) {
                            var component=KitTestSupport.require(view,id,JComponent.class);var image=crop(root,component);
                            pair(c,out,id.equals("t18-groups")?"table":"tree","outline",SemanticColor.BORDER_CONTROL,SemanticColor.SURFACE_APP,image,pixel(paint(root),2,2),3);
                        }
                    } else {
                        for(int i=0;i<2;i++)text(root,"@ordered/markers/"+(i==0?"csproj":"xcodeproj")+"/index","ordered",i==0?"index":"zebra-index","text.tertiary",i==0?"surface.input":"surface.zebra",high,out);
                        var component=KitTestSupport.require(view,"markers",JComponent.class);
                        pair(c,out,"ordered","outline",SemanticColor.BORDER_CONTROL,SemanticColor.SURFACE_APP,crop(root,component),pixel(paint(root),2,2),3);
                    }
                }
            }
        }
    }
    private static void tags(KitContext c,boolean high,List<Sample> out) {
        for(var parent:List.of(SemanticColor.SURFACE_APP,SemanticColor.SURFACE_PANEL,SemanticColor.SURFACE_CARD,SemanticColor.SURFACE_SIDEBAR))
            for(var hue:List.of(Nodes.Hue.BLUE,Nodes.Hue.GREEN,Nodes.Hue.AMBER,Nodes.Hue.PURPLE,Nodes.Hue.NEUTRAL)) {
                var node=new Nodes.Chip("chip",ControlState.READY,"MMMM",hue);
                try(var view=new KitView(c,new ViewSpec(node),e->{})) {
                    var root=host(c,view,160,70);root.setBackground(parent.color(c.theme()));
                    text(root,"chip","tag",hue+"-on-"+parent.key(),"hue."+hue.name().toLowerCase(Locale.ROOT),parent.key(),high,out);
                }
            }
    }
    private static void rows(KitContext c,boolean high,List<Sample> out) {
        var columns=List.of(new Column("primary","MMMM",Nodes.TextRole.BODY,false),new Column("secondary","MMMM",Nodes.TextRole.DESCRIPTION,false),new Column("tertiary","MMMM",Nodes.TextRole.CAPTION,false));
        var rows=List.of(new TableRow("a",Map.of("primary","MMMM","secondary","MMMM","tertiary","MMMM"),Optional.empty(),false,Optional.empty(),""),
            new TableRow("b",Map.of("primary","MMMM","secondary","MMMM","tertiary","MMMM"),Optional.empty(),false,Optional.empty(),""));
        try(var view=new KitView(c,new ViewSpec(new Nodes.Table("grid",ControlState.READY,columns,rows,Set.of(),"open",List.of())),e->{})) {
            var root=host(c,view,500,150);boolean classic=c.kit().id().equals("classic");
            if(!classic)text(root,"@table/grid/header/primary","table-default","header","text.primary","surface.tableHeader",high,out);
            if(classic) {
                for(String role:List.of("primary","secondary","tertiary"))text(root,"@table/grid/b/"+role,"table-row","zebra-"+role,"text."+role,"surface.zebra",high,out);
                var label=KitTestSupport.require(view,"@table/grid/a/primary",JLabel.class);
                label.dispatchEvent(new java.awt.event.MouseEvent(label,java.awt.event.MouseEvent.MOUSE_ENTERED,0,0,5,5,0,false));
                for(String role:List.of("primary","secondary","tertiary"))text(root,"@table/grid/a/"+role,"table-row","hover-"+role,"text."+role,"surface.rowHover",high,out);
            }
        }
    }
    private static void readonly(KitContext c,boolean high,List<Sample> out) {
        var node=new Nodes.Field("readonly",ControlState.READY,Nodes.FieldKind.TEXT,"MMMM","","Value","",Optional.empty(),Nodes.FieldPresentation.STANDARD,"","","",true);
        try(var view=new KitView(c,new ViewSpec(node),e->{})) {
            var root=host(c,view,300,90);var field=KitTestSupport.require(view,"readonly.input",JTextField.class);var image=crop(root,field);field.setVisible(false);var blank=crop(root,field);field.setVisible(true);
            out.add(glyph("readonly","value",c.kit().id().equals("classic")?"text.secondary":"text.primary",c.kit().id().equals("classic")?"surface.tableHeader":"surface.input",image,blank,high?7:4.5));
        }
    }
    private static void combos(KitContext c,boolean high,List<Sample> out) {
        for(boolean toolbar:List.of(false,true)) {
            var choice=new Nodes.Choice("combo",ControlState.READY,Nodes.ChoiceKind.DROPDOWN,List.of(new Option("a","MMMM",Optional.empty())),"a","Choice","select","",Optional.empty(),
                toolbar?Optional.of(new Nodes.PickerFace("MMMM","","",Nodes.Hue.NONE,Nodes.PickerDensity.COMPACT)):Optional.empty());
            Nodes.Node node=toolbar?new Nodes.Stack("bar",ControlState.READY,Nodes.LayoutRole.NAVIGATION,List.of(choice)):choice;
            try(var view=new KitView(c,new ViewSpec(node),e->{})) {
                view.toolbarStrip(toolbar);var root=host(c,view,300,80);var combo=KitTestSupport.require(view,"combo",JComponent.class);var image=crop(root,combo);
                if(c.kit().id().equals("classic")) {
                    var bg=toolbar?SemanticColor.SURFACE_INPUT:SemanticColor.BUTTON_SECONDARY_BG;
                    int ground=toolbar?locate(image,bg.color(c.theme()),"toolbar combo fill"):pixel(image,3,combo.getHeight()/2.0);
                    int arrowWidth=toolbar?metric(c,dev.jasper.ui.metrics.DesignMetrics.Role.TOOLBAR_COMBO_ARROW):24;
                    int arrowInset=toolbar?metric(c,dev.jasper.ui.metrics.DesignMetrics.Role.TOOLBAR_COMBO_TRAILING):0;
                    var arrowImage=image.getSubimage((combo.getWidth()-arrowInset-arrowWidth)*4,4,arrowWidth*4,(combo.getHeight()-2)*4);
                    pair(c,out,"combo",toolbar?"toolbar-arrow":"arrow",SemanticColor.TEXT_SECONDARY,bg,arrowImage,ground,3);
                    if(toolbar) {
                        pair(c,out,"combo","toolbar-text",SemanticColor.TEXT_PRIMARY,bg,image.getSubimage(8*4,4,(combo.getWidth()-arrowInset-arrowWidth-8)*4,(combo.getHeight()-2)*4),ground,high?7:4.5);
                        pair(c,out,"combo","toolbar-outline",SemanticColor.BORDER_CONTROL,SemanticColor.SURFACE_APP,image,pixel(paint(root),2,2),3);
                    }
                }
            }
        }
    }
}

package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.awt.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

class GroupedFormGeometryTest {
    @Test void fullFormsAccountForCardStrokeAndDependentDescriptionRows() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var row=new FormRow("row",ControlState.READY,"Name","",field("name",FieldKind.TEXT,""),Optional.empty());
            var plain=new SettingRow("plain",ControlState.READY,"Use TLS","",new Toggle("tls",ControlState.READY,"TLS",false,"tls",false),false);
            var dependent=new SettingRow("dependent",ControlState.READY,"Require TLS","Refuse logins and transfers without it",new Toggle("require",ControlState.READY,"Require",false,"require",false),true);
            var sections=List.<Node>of(new Section("general",ControlState.READY,"Server",new Card("card",ControlState.READY,CardKind.FORM,List.of(row))),new Section("security",ControlState.READY,"Security",new Card("settings",ControlState.READY,CardKind.SETTINGS,List.of(plain,dependent))));
            try(var view=new KitView(context,new ViewSpec(FormsTest.form(FormKind.FULL,sections)),e->{})) {
                KitFixtures.render(view,540,690,2);
                var name=find(view,"name",javax.swing.JComponent.class);var bounds=javax.swing.SwingUtilities.convertRectangle(name.getParent(),name.getBounds(),view);
                assertThat(bounds).isEqualTo(new Rectangle(147,54,354,28));
                var normal=find(view,"plain.title",javax.swing.JComponent.class);var child=find(view,"dependent.title",javax.swing.JComponent.class);
                assertThat(child.getX()-normal.getX()).isEqualTo(16);
                assertThat(find(view,"dependent",javax.swing.JComponent.class).getHeight()).isEqualTo(65);
            }finally{release(context);}
        });
    }
    @Test void mixedCardDividerUsesPreviousVisibleChildAcrossHiddenProtocolRows() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var form=new FormRow("row",ControlState.READY,"Login","",field("login",FieldKind.TEXT,""),Optional.empty());
            var hidden=new Text("hidden",new ControlState(false,true,false,""),"keys",TextRole.BODY);
            var setting=new SettingRow("setting",ControlState.READY,"Allow changes","Clients can upload, rename and delete files",new Toggle("toggle",ControlState.READY,"Changes",false,"toggle",false),false);
            try(var view=new KitView(context,new ViewSpec(new Card("mixed",ControlState.READY,CardKind.MIXED,List.of(form,hidden,setting))),e->{})) {
                var image=KitFixtures.render(view,492,180,1);var control=find(view,"setting",javax.swing.JComponent.class);
                var login=find(view,"row",javax.swing.JComponent.class);assertThat(control.getY()-login.getY()-login.getHeight()).isEqualTo(14);
                assertThat(image.getRGB(5,control.getY())).isEqualTo(ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.BORDER_SUBTLE).getRGB());
            }finally{release(context);}
        });
    }
    @Test void supportingTextWrapsThroughControlColumnStackWithMutedSansMetrics() {
        Edt.run(()->{
            var context=KitFixtures.context();
            assertThat(java.util.Arrays.stream(TextRole.values()).map(Enum::name)).contains("SUPPORTING");
            var help=new Text("help",ControlState.READY,"Clients sign in with the username and password stored in this Vault account.",TextRole.valueOf("SUPPORTING"));
            var stack=new Nodes.Stack("support",ControlState.READY,LayoutRole.VERTICAL,List.of(help));
            var row=new FormRow("row",ControlState.READY,"Account","",stack,Optional.empty());
            try(var view=new KitView(context,new ViewSpec(new Card("card",ControlState.READY,CardKind.FORM,List.of(row))),e->{})) {
                KitFixtures.render(view,492,160,2);var text=find(view,"help",javax.swing.JLabel.class);
                assertThat(text.getFont().getSize2D()).isEqualTo(11.5f);
                assertThat(text.getForeground()).isEqualTo(ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.TEXT_TERTIARY));
                assertThat(text.getHeight()).isGreaterThanOrEqualTo(36);
            }finally{release(context);}
        });
    }
    @Test void pathHasSemanticFolderCueAndSegmentLabelsFitAtFullFormWidth() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var choice=new Nodes.Choice("login",ControlState.READY,ChoiceKind.SEGMENTED,List.of(new Option("anon","Anonymous",Optional.empty()),new Option("vault","Vault Account",Optional.empty()),new Option("session","Session Password",Optional.empty())),"anon","Login","");
            try(var view=new KitView(context,new ViewSpec(new Nodes.Stack("stack",ControlState.READY,LayoutRole.VERTICAL,List.of(choice,field("path",FieldKind.PATH,"")))),e->{})) {
                KitFixtures.render(view,354,200,2);
                assertThat(find(view,"path.leading",javax.swing.JLabel.class).getIcon()).isNotNull();
                var button=find(view,"login.option.session",javax.swing.JButton.class);var insets=button.getInsets();
                assertThat(button.getWidth()-insets.left-insets.right).isGreaterThanOrEqualTo(button.getFontMetrics(button.getFont()).stringWidth(button.getText()));
            }finally{release(context);}
        });
    }

    @Test void contentSizedButtonsFitTheSameFractionalTextMetricsUsedByTheirPainter() {
        Edt.run(()->{
            var context=KitFixtures.context();
            try(var view=new KitView(context,new ViewSpec(new Nodes.Button("choose",ControlState.READY,"Choose…","",ButtonKind.SECONDARY,"choose","")),e->{})) {
                var button=find(view,"choose",javax.swing.JButton.class);
                var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
                try {
                    g.scale(2,2);ThemePainting.antialias(g);g.setFont(button.getFont());
                    var size=button.getPreferredSize();var insets=button.getInsets();
                    String painted=javax.swing.SwingUtilities.layoutCompoundLabel(button,g.getFontMetrics(),button.getText(),null,
                        javax.swing.SwingConstants.CENTER,javax.swing.SwingConstants.CENTER,javax.swing.SwingConstants.CENTER,javax.swing.SwingConstants.RIGHT,
                        new Rectangle(insets.left,insets.top,size.width-insets.left-insets.right,size.height-insets.top-insets.bottom),new Rectangle(),new Rectangle(),button.getIconTextGap());
                    assertThat(painted).isEqualTo(button.getText());
                }finally{g.dispose();}
            }finally{release(context);}
        });
    }

    @Test void fullFooterUsesThirtyPixelActionsAndTwentyTwoPixelGapWithoutChangingOtherForms() {
        Edt.run(()->{
            var context=KitFixtures.context();
            try(var view=new KitView(context,new ViewSpec(FormsTest.form(FormKind.FULL,List.of())),e->{})) {
                for(var kind:List.of(FormKind.FULL,FormKind.SETTINGS,FormKind.HERO,FormKind.FULL)) {
                    view.update(new ViewSpec(FormsTest.form(kind,List.of())));KitFixtures.render(view,540,300,1);
                    var cancel=find(view,"cancel",javax.swing.JButton.class);
                    assertThat(cancel.getHeight()).as("%s footer action",kind).isEqualTo(kind==FormKind.FULL || kind==FormKind.HERO?30:28);
                    if(kind==FormKind.FULL)assertThat(((javax.swing.JComponent)cancel.getParent().getParent()).getInsets().top).isEqualTo(22);
                }
            }finally{release(context);}
        });
    }
    @Test void orderedRowsAndTheirAddActionHaveSeparateSourceMeasuredHeights() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var item=new ListItem("key",Optional.empty(),List.of(new Text("name",ControlState.READY,"key",TextRole.MONO)),List.of(),List.of());
            var list=new ItemList("keys",ControlState.READY,ListKind.ORDERED,List.of(item,itemWithId("key2")),java.util.Set.of(),"",List.of(),SelectionMode.SINGLE,
                Optional.of(new Nodes.Button("add",ControlState.READY,"Add Stored Key…","add",ButtonKind.LINK,"add","")),java.util.OptionalInt.of(2));
            try(var view=new KitView(context,new ViewSpec(list),e->{})) {
                assertThat(view.getPreferredSize().height).isEqualTo(102);
                KitFixtures.render(view,354,102,1);assertThat(find(view,"add",javax.swing.JButton.class).getHeight()).isEqualTo(32);
            }finally{release(context);}
        });
    }
    private static ListItem itemWithId(String id){return new ListItem(id,Optional.empty(),List.of(new Text(id+".name",ControlState.READY,id,TextRole.MONO)),List.of(),List.of());}

    @Test void descriptionDensityFollowsCurrentFormKindWithoutChangingCompactAndHeroRows() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var setting=new SettingRow("setting",ControlState.READY,"Option","Short helper",new Toggle("toggle",ControlState.READY,"Enabled",false,"toggle",false),false);
            try(var view=new KitView(context,new ViewSpec(FormsTest.form(FormKind.FULL,List.of(setting))),e->{})) {
                int[] expected={65,49,54,65};int at=0;
                for(var kind:List.of(FormKind.FULL,FormKind.SETTINGS,FormKind.HERO,FormKind.FULL)) {
                    view.update(new ViewSpec(FormsTest.form(kind,List.of(setting))));KitFixtures.render(view,540,400,1);
                    assertThat(find(view,"setting",javax.swing.JComponent.class).getInsets().left).isEqualTo(kind==FormKind.FULL?15:13);
                    assertThat(find(view,"setting",javax.swing.JComponent.class).getHeight()).as("%s current context",kind).isEqualTo(expected[at++]);
                }
            }finally{release(context);}
        });
    }

}

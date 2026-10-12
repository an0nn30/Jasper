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
                assertThat(bounds).isEqualTo(new Rectangle(147,53,354,28));
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
                assertThat(text.getHeight()).isGreaterThanOrEqualTo(2*17);
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
    @Test void orderedRowsAlignIndexGlyphNameAndCompactCommandsToSourceColumns() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var key=new ListItem("key",Optional.of(new Icon("key.icon",ControlState.READY,"key",Hue.NONE,IconSize.CAPTION,"SSH key")),
                List.of(new Text("key.name",ControlState.READY,"id_ed25519",TextRole.MONO),new Text("key.caption",ControlState.READY,"Stored key",TextRole.CAPTION)),List.of(),List.of());
            var list=new ItemList("keys",ControlState.READY,ListKind.ORDERED,List.of(key,itemWithId("key2")),java.util.Set.of("key2"),"",List.of(),SelectionMode.SINGLE,
                Optional.of(new Nodes.Button("add",ControlState.READY,"Add Stored Key…","add",ButtonKind.LINK,"add","")),java.util.OptionalInt.of(2));
            try(var view=new KitView(context,new ViewSpec(list),e->{})) {
                var image=KitFixtures.render(view,354,102,2);
                java.util.function.Function<String,Rectangle> at=id->{var c=find(view,id,javax.swing.JComponent.class);return javax.swing.SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),view);};
                // Source 2x columns: index ink 12.5, glyph 35.5, name 57 logical; commands 31 apart, the last centred 18 from the edge.
                assertThat(at.apply("@ordered/keys/key/index").x).isEqualTo(12);
                assertThat(at.apply("@ordered/keys/key/index").width).isEqualTo(16);
                assertThat(at.apply("key.icon").x).isEqualTo(34);
                assertThat(at.apply("key.name").x).isEqualTo(56);
                assertThat(at.apply("key.caption").x-at.apply("key.name").x-at.apply("key.name").width).isEqualTo(9);
                var up=at.apply("@ordered/keys/key/up");var down=at.apply("@ordered/keys/key/down");var remove=at.apply("@ordered/keys/key/remove");
                assertThat(java.util.List.of(up.getSize(),down.getSize(),remove.getSize())).containsOnly(new Dimension(24,24));
                assertThat(down.x-up.x).isEqualTo(31);assertThat(remove.x-down.x).isEqualTo(31);
                assertThat(354-(remove.x+remove.width/2)).isEqualTo(18);
                var add=find(view,"add",javax.swing.JButton.class);
                assertThat(add.getInsets().left).isEqualTo(12);assertThat(add.getFont().getSize2D()).isEqualTo(12f);
                // An unfocused ordered list keeps its selection identity without painting a selected fill.
                var row=at.apply("@ordered/keys/key2/index");
                assertThat(new Color(image.getRGB(2*200,2*(row.y+row.height/2)),true))
                    .isEqualTo(ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT));
            }finally{release(context);}
        });
    }
    @Test void fullSettingRowsUseCompactChoicesAndCloseTheirCardWithoutChangingSettingsForms() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var group=new SettingRow("group",ControlState.READY,"Group","",new Nodes.Choice("group.choice",ControlState.READY,ChoiceKind.DROPDOWN,List.of(new Option("none","None",Optional.empty())),"none","Group",""),false);
            var favorite=new SettingRow("favorite",ControlState.READY,"Add to Favorites","",new Toggle("favorite.toggle",ControlState.READY,"Favorite",false,"favorite",false),false);
            var card=new Section("options",ControlState.READY,"Options",new Card("options.card",ControlState.READY,CardKind.SETTINGS,List.of(group,favorite)));
            try(var view=new KitView(context,new ViewSpec(FormsTest.form(FormKind.FULL,List.of(card))),e->{})) {
                for(var kind:List.of(FormKind.FULL,FormKind.SETTINGS,FormKind.FULL)) {
                    view.update(new ViewSpec(FormsTest.form(kind,List.of(card))));KitFixtures.render(view,540,400,1);
                    boolean full=kind==FormKind.FULL;
                    assertThat(find(view,"group.choice.input",javax.swing.JComponent.class).getHeight()).as("%s choice",kind).isEqualTo(full?26:28);
                    assertThat(find(view,"group",javax.swing.JComponent.class).getHeight()).as("%s row",kind).isEqualTo(full?43:44);
                    assertThat(find(view,"options.card",javax.swing.JComponent.class).getHeight()).as("%s card",kind).isEqualTo(full?43+43+1:44+42);
                }
            }finally{release(context);}
        });
    }
    @Test void controlFacesPaintCrispHairlinesAndDropdownChevronsShareThePickerInset() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var choice=new Nodes.Choice("jump",ControlState.READY,ChoiceKind.DROPDOWN,List.of(new Option("none","None",Optional.empty())),"none","Jump Host","");
            try(var view=new KitView(context,new ViewSpec(new Nodes.Stack("stack",ControlState.READY,LayoutRole.VERTICAL,List.of(field("name",FieldKind.TEXT,""),choice))),e->{})) {
                var image=KitFixtures.render(view,200,200,2);
                var name=(javax.swing.JComponent)find(view,"name.input",javax.swing.JComponent.class).getParent();var top=javax.swing.SwingUtilities.convertPoint(name.getParent(),name.getLocation(),view);
                int x=2*(top.x+100),y=2*top.y;
                var border=ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.BORDER_INPUT).getRGB();
                assertThat(image.getRGB(x,y)).as("first device row of the 1-logical border").isEqualTo(border);
                assertThat(image.getRGB(x,y+1)).as("second device row of the 1-logical border").isEqualTo(border);
                assertThat(image.getRGB(x,y+2)).isEqualTo(ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT).getRGB());
                var input=find(view,"jump.input",javax.swing.JComponent.class);var origin=javax.swing.SwingUtilities.convertPoint(input.getParent(),input.getLocation(),view);
                int right=-1,fill=ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL).getRGB();
                for(int px=2*(origin.x+input.getWidth())-1;px>2*(origin.x+input.getWidth()-30)&&right<0;px--)
                    for(int py=2*(origin.y+8);py<2*(origin.y+input.getHeight()-8);py++)
                        if(image.getRGB(px,py)!=fill&&px<2*(origin.x+input.getWidth())-4){right=px;break;}
                // Source chevron ink ends 12 logical before the face edge, as on the picker face.
                assertThat(2*(origin.x+input.getWidth())-right).isBetween(2*10,2*13);
            }finally{release(context);}
        });
    }
    @Test void formRowLabelsAndTrailingTextFollowTheControlsFirstLineWhenAnErrorAppears() {
        Edt.run(()->{
            var context=KitFixtures.context();
            java.util.function.Function<String,Node> row=error->new FormRow("row",ControlState.READY,"Port","",
                new Field("port",new ControlState(true,true,false,error),FieldKind.TEXT,"70000","","Port","",Optional.empty()),
                Optional.of(new Text("unit",ControlState.READY,"seconds",TextRole.CAPTION)));
            try(var view=new KitView(context,new ViewSpec(new Card("card",ControlState.READY,CardKind.FORM,List.of(row.apply("")))),e->{})) {
                KitFixtures.render(view,492,200,1);
                var label=find(view,"row.label",javax.swing.JComponent.class);var unit=find(view,"unit",javax.swing.JComponent.class);
                var face=(javax.swing.JComponent)find(view,"port.input",javax.swing.JComponent.class).getParent();
                int labelY=label.getY(),unitY=unit.getY(),faceHeight=face.getHeight();
                assertThat(2*labelY+label.getHeight()).isCloseTo(faceHeight,within(1));
                assertThat(2*unitY+unit.getHeight()).isCloseTo(faceHeight,within(1));
                view.update(new ViewSpec(new Card("card",ControlState.READY,CardKind.FORM,List.of(row.apply("Use a port from 1 to 65535")))));
                KitFixtures.render(view,492,200,1);
                assertThat(find(view,"row",javax.swing.JComponent.class).getHeight()).isGreaterThan(faceHeight);
                assertThat(label.getY()).as("an inline error below the control does not move its label").isEqualTo(labelY);
                assertThat(unit.getY()).isEqualTo(unitY);
            }finally{release(context);}
        });
    }
    @Test void aCaptionedLabelStaysOnTheControlWhenItsTrailingFieldShowsAnError() {
        Edt.run(()->{
            var context=KitFixtures.context();
            java.util.function.Function<String,Node> row=error->new FormRow("listen",ControlState.READY,"Listen On","this computer",
                new Field("address",ControlState.READY,FieldKind.TEXT,"localhost","","Listen address","",Optional.empty()),
                Optional.of(new Field("port",new ControlState(true,true,false,error),FieldKind.PORT,"99999","","Listen port","",Optional.empty())));
            try(var view=new KitView(context,new ViewSpec(new Card("card",ControlState.READY,CardKind.FORM,List.of(row.apply("")))),e->{})) {
                KitFixtures.render(view,492,200,1);
                var label=find(view,"listen.label",javax.swing.JComponent.class);int labelY=label.getY(),rowHeight=find(view,"listen",javax.swing.JComponent.class).getHeight();
                view.update(new ViewSpec(new Card("card",ControlState.READY,CardKind.FORM,List.of(row.apply("Use 0 to 65535")))));
                KitFixtures.render(view,492,200,1);
                assertThat(find(view,"listen",javax.swing.JComponent.class).getHeight()).as("the trailing error grows the row").isGreaterThan(rowHeight);
                assertThat(label.getY()).as("the label and caption stay centred on the control").isEqualTo(labelY);
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

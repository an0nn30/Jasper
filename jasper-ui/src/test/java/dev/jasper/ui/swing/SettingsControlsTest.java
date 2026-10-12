package dev.jasper.ui.swing;
import static org.assertj.core.api.Assertions.*;
import dev.jasper.ui.model.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import dev.jasper.ui.testsupport.Edt;
class SettingsControlsTest {
    @Test void settingsNavigationOverlayRetainsNativeScrollbarWhenRoleIsRemoved() { Edt.run(()->{
        var context=dev.jasper.ui.testsupport.KitFixtures.context();
        var items=new java.util.ArrayList<TreeItem>();for(int i=0;i<50;i++)items.add(new TreeItem("row"+i,"Row "+i,Optional.empty(),true,List.of()));
        var tree=new Nodes.Tree("tree",ControlState.READY,items,Set.of(),Set.of("row0"),"");
        try(var view=new KitView(context,new ViewSpec(tree),event->{})) {
            view.setSize(236,180);layout(view);var control=(Trees.TreeControl)view.control("tree");var pane=control.scroll;var bar=pane.getVerticalScrollBar();var model=bar.getModel();
            Class<?> nativeUI=bar.getUI().getClass();int nativeMode=pane.getViewport().getScrollMode();
            for(var orientation:List.of(java.awt.ComponentOrientation.LEFT_TO_RIGHT,java.awt.ComponentOrientation.RIGHT_TO_LEFT)) {
                view.applyComponentOrientation(orientation);
                for(int width:new int[]{236,120}) {
                    view.update(new ViewSpec(new Nodes.Stack("nav",ControlState.READY,Nodes.LayoutRole.SETTINGS_NAVIGATION,List.of(tree))));view.setSize(width,180);layout(view);
                    assertThat(pane.getViewport().getWidth()).isEqualTo(pane.getWidth());
                    assertThat(pane.getVerticalScrollBar()).isSameAs(bar);assertThat(bar.getModel()).isSameAs(model);
                    var overlayUI=bar.getUI();view.update(new ViewSpec(new Nodes.Stack("nav",ControlState.READY,Nodes.LayoutRole.SETTINGS_NAVIGATION,List.of(tree))));assertThat(bar.getUI()).as("idempotent retained refresh").isSameAs(overlayUI);
                    pane.dispatchEvent(new java.awt.event.MouseWheelEvent(pane,java.awt.event.MouseEvent.MOUSE_WHEEL,1,0,30,30,0,false,java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,3,1));
                    assertThat(bar.getValue()).isPositive();
                    SwingUtilities.updateComponentTreeUI(view);layout(view);assertThat(pane.getViewport().getWidth()).isEqualTo(pane.getWidth());
                    view.update(new ViewSpec(tree));layout(view);
                    assertThat(bar.getUI().getClass()).isEqualTo(nativeUI);assertThat(bar.getModel()).isSameAs(model);
                    assertThat(pane.getViewport().getWidth()).isLessThan(pane.getWidth());assertThat(pane.getViewport().getScrollMode()).isEqualTo(nativeMode);
                    assertThat(bar.contains(bar.getWidth()/2,bar.getHeight()/2)).isTrue();
                }
            }
        }finally{context.binding().close();context.icons().close();}
    }); }

    @Test void settingsActionsHaveSourceSizedJoinedAndFooterEnvelopes() { Edt.run(()->{
        var context=dev.jasper.ui.testsupport.KitFixtures.context();
        var back=new Nodes.Button("back",ControlState.READY,"","chevron.left",Nodes.ButtonKind.GHOST,"back","Back");
        var next=new Nodes.Button("next",ControlState.READY,"","chevron.right",Nodes.ButtonKind.GHOST,"next","Next");
        var help=new Nodes.Button("help",ControlState.READY,"","question",Nodes.ButtonKind.GHOST,"help","Help",Nodes.ButtonPresentation.CIRCULAR);
        var text=new Nodes.Text("status",ControlState.READY,"",Nodes.TextRole.CAPTION);
        var ok=new Nodes.Button("ok",ControlState.READY,"OK","",Nodes.ButtonKind.PRIMARY,"ok","");
        var footer=new Nodes.Stack("footer",ControlState.READY,Nodes.LayoutRole.SETTINGS_FOOTER,List.of(help,text,ok));
        try(var view=new KitView(context,new ViewSpec(new Nodes.Stack("joined",ControlState.READY,Nodes.LayoutRole.JOINED_ACTIONS,List.of(back,next))),event->{})) {
            assertThat(view.getPreferredSize()).isEqualTo(new java.awt.Dimension(50,24));
            view.update(new ViewSpec(footer));view.setSize(600,53);layout(view);
            assertThat(find(view,"ok",JButton.class).getWidth()).isEqualTo(64);
            assertThat(find(view,"help",JButton.class).getWidth()).isEqualTo(24);
        }finally{context.binding().close();context.icons().close();}
    }); }

    @Test void navigationHasGroupBreaksWithoutExpandingTheHitTargets() { Edt.run(()->{
        var context=dev.jasper.ui.testsupport.KitFixtures.context();
        var child=new TreeItem("child","Child",Optional.empty(),true,List.of());
        var group=new TreeItem("group","Group",Optional.empty(),false,List.of(child));
        var leaf=new TreeItem("leaf","Root leaf",Optional.empty(),true,List.of());
        var next=new TreeItem("next","Next group",Optional.empty(),false,List.of());
        var tree=new Nodes.Tree("tree",ControlState.READY,List.of(group,leaf,next),Set.of("group"),Set.of("child"),"");
        try(var view=new KitView(context,new ViewSpec(new Nodes.Stack("nav",ControlState.READY,Nodes.LayoutRole.SETTINGS_NAVIGATION,List.of(tree))),event->{})) {
            view.setSize(236,500);layout(view);var rows=(Trees.TreeControl)view.control("tree");
            assertThat(rows.mounted.get("child").getHeight()).isEqualTo(29);
            assertThat(rows.top(2)-rows.top(1)-rows.mounted.get("child").getHeight()).as("an unlabelled root group").isEqualTo(context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_GROUP_GAP));
            assertThat(rows.top(3)-rows.top(2)-rows.mounted.get("leaf").getHeight()).as("a labelled group heading").isEqualTo(context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_HEADING_GAP));
            view.update(new ViewSpec(tree));view.setSize(236,500);layout(view);
            assertThat(rows.top(2)-rows.top(1)).isEqualTo(rows.mounted.get("child").getHeight());
        }finally{context.binding().close();context.icons().close();}
    }); }

    @Test void identityRowTitleAndMetadataAreOneCenteredBlock() { Edt.run(()->{
        var context=dev.jasper.ui.testsupport.KitFixtures.context();
        var icon=new Nodes.Icon("identity",ControlState.READY,"plugin",Nodes.Hue.BLUE,Nodes.IconSize.CONTROL,"Owner");
        var item=new ListItem("row",Optional.of(icon),List.of(new Nodes.Text("title",ControlState.READY,"Title",Nodes.TextRole.BODY)),List.of(new Nodes.Text("metadata",ControlState.READY,"Version",Nodes.TextRole.CAPTION)),List.of());
        var list=new Nodes.ItemList("list",ControlState.READY,Nodes.ListKind.DOUBLE,List.of(item),Set.of("row"),"",List.of());
        try(var view=new KitView(context,new ViewSpec(list),event->{})) {
            view.setArtworkPresentation("identity",ArtworkPresentation.IDENTITY_ROW);view.setSize(258,200);layout(view);
            var row=((InteractiveRows.RowsControl)view.control("list")).mounted.get("row");
            assertThat(row.title.getHeight()).isEqualTo(row.title.getPreferredSize().height);
            assertThat(row.subtitle.getY()).isEqualTo(row.title.getY()+row.title.getHeight());
        }finally{context.binding().close();context.icons().close();}
    }); }

    @Test void identityHeaderAddsAnInwardContourWithoutChangingNormalRowsOrAuthoredGlyphs() { Edt.run(()->{
        var context=dev.jasper.ui.testsupport.KitFixtures.context();
        var icon=new Nodes.Icon("icon",ControlState.READY,"plugin",Nodes.Hue.BLUE,Nodes.IconSize.HERO,"Owner");
        try(var view=new KitView(context,new ViewSpec(icon),event->{})) {
            view.setArtworkPresentation("icon",ArtworkPresentation.IDENTITY_HEADER);
            var rendered=find(view,"icon",JLabel.class).getIcon();
            var pixels=new java.awt.image.BufferedImage(108,108,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g=pixels.createGraphics();try {g.scale(2,2);rendered.paintIcon(null,g,0,0);}finally{g.dispose();}
            assertThat(pixels.getRGB(54,0)).isEqualTo(context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_CONTROL).getRGB());
            assertThat(pixels.getRGB(54,1)).isEqualTo(pixels.getRGB(54,0));
            assertThat(pixels.getRGB(54,2)).isNotEqualTo(pixels.getRGB(54,0));
        }finally{context.binding().close();context.icons().close();}
    }); }

    @Test void stepperButtonsRespectBoundsAndRetainTheEditableDocument() { Edt.run(() -> {
        var events = new ArrayList<UiEvent>();
        var field = new Nodes.Field("number", ControlState.READY, Nodes.FieldKind.NUMBER, "3", "px", "Height", "", Optional.of(new Nodes.Numeric(1, 3, 1, 0)), Nodes.FieldPresentation.STEPPER);
        try (var view = new KitView(dev.jasper.ui.testsupport.KitFixtures.context(), new ViewSpec(field), events::add)) {
            var input = find(view, "number.input", JTextField.class); var document = input.getDocument();
            find(view, "number.minus", JButton.class).doClick(0);
            assertThat(events).contains(new UiEvent.Edited("number", "2"));
            view.update(new ViewSpec(field));
            assertThat(find(view, "number.input", JTextField.class).getDocument()).isSameAs(document);
            find(view, "number.plus", JButton.class).doClick(0);
            assertThat(input.getText()).isEqualTo("3");
        }
    }); }
    @Test void previewChoiceHasARealTileAndRetainsItsButtonOnSelection() { Edt.run(() -> {
        var palette = new Option.Palette(0xff111111,0xff222222,0xff333333,0xffdddddd,0xff5588ee);
        var options = List.of(new Option("dark","Dark",Optional.empty(),"","",Optional.of(new Option.Preview(palette,Optional.empty()))));
        var choice = new Nodes.Choice("themes",ControlState.READY,Nodes.ChoiceKind.PREVIEWS,options,"dark","Appearance policy","");
        var events = new ArrayList<UiEvent>();
        try (var view = new KitView(dev.jasper.ui.testsupport.KitFixtures.context(),new ViewSpec(choice),events::add)) {
            var button = find(view,"themes.option.dark",JButton.class);
            assertThat(button.getPreferredSize().height).isGreaterThan(90);
            button.doClick(0);
            assertThat(events).contains(new UiEvent.Selected("themes",Set.of("dark")));
            view.update(new ViewSpec(choice));
            assertThat(find(view,"themes.option.dark",JButton.class)).isSameAs(button);
        }
    }); }
    @Test void flatLafChoicePaddingDoesNotAddASecondRendererInset() { Edt.run(() -> {
        Object before=UIManager.get("ComboBox.padding");UIManager.put("ComboBox.padding",new java.awt.Insets(2,6,2,6));
        try {
            var context=dev.jasper.ui.testsupport.KitFixtures.context();var combo=new Choices.ChoiceCombo(context);
            combo.addItem(new Option("one","One",Optional.empty()));
            combo.setRenderer((list,value,index,selected,focus)->new JLabel(){@Override protected void paintComponent(java.awt.Graphics g){g.setColor(java.awt.Color.MAGENTA);g.fillRect(10,3,1,1);}});
            combo.setSize(190,28);combo.doLayout();var image=new java.awt.image.BufferedImage(190,28,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();combo.paint(g);g.dispose();
            int left=190;for(int y=0;y<28;y++)for(int x=0;x<190;x++)if(image.getRGB(x,y)==java.awt.Color.MAGENTA.getRGB())left=Math.min(left,x);
            assertThat(left).as("renderer owns its ten-pixel inset; installed ComboBox.padding must not add six").isEqualTo(10);
        }finally{UIManager.put("ComboBox.padding",before);}
    }); }
    @Test void sliderKeepsItsThemePainterAndValueAcrossLookAndFeelRefresh() { Edt.run(() -> {
        var field=new Nodes.Field("opacity",ControlState.READY,Nodes.FieldKind.NUMBER,"90","%","Opacity","",Optional.of(new Nodes.Numeric(0,100,1,0)),Nodes.FieldPresentation.SLIDER);
        var events=new ArrayList<UiEvent>();
        try(var view=new KitView(dev.jasper.ui.testsupport.KitFixtures.context(),new ViewSpec(field),events::add)){
            var slider=find(view,"opacity.input",JSlider.class);var painter=slider.getUI().getClass();
            SwingUtilities.updateComponentTreeUI(view);
            assertThat(slider.getUI().getClass()).isEqualTo(painter);
            assertThat(slider.getValue()).isEqualTo(90);assertThat(events).isEmpty();
            slider.setValue(75);assertThat(events).containsExactly(new UiEvent.Edited("opacity","75"));
        }
    }); }
    @Test void narrowSettingsRowsKeepStepperTextAndActionsUsable() { Edt.run(()->{
        var field=new Nodes.Field("columns",ControlState.READY,Nodes.FieldKind.NUMBER,"129","columns","Columns","",Optional.of(new Nodes.Numeric(1,300,1,0)),Nodes.FieldPresentation.STEPPER);
        var row=new Nodes.SettingRow("size",ControlState.READY,"Initial size","Applies to new windows",field,false,false);
        try(var view=new KitView(dev.jasper.ui.testsupport.KitFixtures.context(),new ViewSpec(new Nodes.Stack("page",ControlState.READY,Nodes.LayoutRole.SETTINGS_PAGE,List.of(row))),e->{})){
            view.setSize(260,300);layout(view);var input=find(view,"columns.input",JTextField.class);
            assertThat(input.getWidth()).as("narrow row must move control below its label before shrinking editable text").isGreaterThanOrEqualTo(input.getFontMetrics(input.getFont()).stringWidth("129"));
        }
    }); }
    @Test void defaultAnnotationDoesNotTruncateAStandardStickyCardFace() { Edt.run(()->{
        var context=dev.jasper.ui.testsupport.KitFixtures.context();
        try{
            var option=new Option("sticky","Sticky card","notification.sticky","Default");
            var renderer=new RichOptions(context,option,false,false,true,148);
            assertThat(renderer.labelWidth(148)).isGreaterThanOrEqualTo(renderer.getFontMetrics(ThemePainting.font(context,dev.jasper.ui.metrics.Typography.Role.BODY)).stringWidth(option.label()));
        }finally{context.binding().close();context.icons().close();}
    }); }
    @Test void segmentedChoicesReflowWithoutReplacingButtonsOrLosingKeyboardSelection() { Edt.run(() -> {
        var options = List.of(new Option("both", "Icons & Labels", Optional.empty()), new Option("icons", "Icons Only", Optional.empty()), new Option("none", "Hidden", Optional.empty()));
        var choice = new Nodes.Choice("toolbar", ControlState.READY, Nodes.ChoiceKind.SEGMENTED, options, "both", "Toolbar", "");
        var events = new ArrayList<UiEvent>();
        try (var view = new KitView(dev.jasper.ui.testsupport.KitFixtures.context(), new ViewSpec(choice), events::add)) {
            var first = find(view, "toolbar.option.both", JButton.class);
            view.setSize(600, 200); layout(view); int wideY = first.getY();
            assertThat(find(view, "toolbar.option.none", JButton.class).getY()).isEqualTo(wideY);
            view.setSize(180, 250); layout(view);
            for (String id : List.of("both", "icons", "none")) {
                var button = find(view, "toolbar.option." + id, JButton.class);
                assertThat(button.getWidth()).as(id + " complete visible label").isGreaterThanOrEqualTo(button.getPreferredSize().width);
            }
            var key = first.getInputMap().get(KeyStroke.getKeyStroke("RIGHT"));
            first.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(first, 0, ""));
            assertThat(events).contains(new UiEvent.Selected("toolbar", Set.of("icons")));
            view.update(new ViewSpec(new Nodes.Choice("toolbar", ControlState.READY, Nodes.ChoiceKind.SEGMENTED, options, "icons", "Toolbar", "")));
            assertThat(find(view, "toolbar.option.both", JButton.class)).isSameAs(first);
            assertThat(find(view, "toolbar.option.icons", JButton.class).isSelected()).isTrue();
            float priorScale = com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
            try {
                for (float scale : new float[]{1.5f, 1f}) {
                    var setter = com.formdev.flatlaf.util.UIScale.class.getDeclaredMethod("setUserScaleFactor", float.class, boolean.class);
                    setter.setAccessible(true); setter.invoke(null, scale, false);
                    view.update(new ViewSpec(choice));
                    view.setSize(com.formdev.flatlaf.util.UIScale.scale(180), 400); layout(view);
                    assertThat(find(view, "toolbar.option.both", JButton.class)).isSameAs(first);
                    for (String id : List.of("both", "icons", "none")) {
                        var button = find(view, "toolbar.option." + id, JButton.class);
                        assertThat(button.getWidth()).isGreaterThanOrEqualTo(button.getPreferredSize().width);
                    }
                }
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            finally {
                try {
                    var setter = com.formdev.flatlaf.util.UIScale.class.getDeclaredMethod("setUserScaleFactor", float.class, boolean.class);
                    setter.setAccessible(true); setter.invoke(null, priorScale, false);
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            }
            view.update(new ViewSpec(choice)); view.setSize(600, 200); layout(view);
            assertThat(find(view, "toolbar.option.none", JButton.class).getY()).isEqualTo(first.getY());
        }
    }); }

    /** A list editor (an ordered list and its entry) keeps its natural width beside its title; the page keeps its reading width. */
    @Test void settingsListEditorsDoNotWidenTheCard() { Edt.run(()->{
        var context=dev.jasper.ui.testsupport.KitFixtures.context();
        var items=new ArrayList<ListItem>();
        for(String word:List.of("ls","cd","pwd","clear","exit"))items.add(new ListItem("item-"+word,Optional.empty(),List.of(new Nodes.Text("text-"+word,ControlState.READY,word,Nodes.TextRole.BODY)),List.of(),List.of()));
        var editor=new Nodes.Stack("trivial:editor",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.ItemList("trivial",ControlState.READY,Nodes.ListKind.ORDERED,items,Set.of(),"",List.of()),
            new Nodes.Stack("trivial:entry",ControlState.READY,Nodes.LayoutRole.INLINE,List.of(
                new Nodes.Field("trivial:new",ControlState.READY,Nodes.FieldKind.TEXT,"","Add…","Add to Trivial commands",""),
                new Nodes.Button("trivial:add",ControlState.READY,"Add","add",Nodes.ButtonKind.SECONDARY,"trivial:add","")))));
        var reset=new Nodes.Button("trivial:reset",new ControlState(false,true,false,""),"","reset",Nodes.ButtonKind.GHOST,"trivial:reset","Reset");
        var row=new Nodes.SettingRow("row:trivial",ControlState.READY,"Trivial commands","Single words compared with a command's first word.",
            new Nodes.Stack("trivial:controls",ControlState.READY,Nodes.LayoutRole.SETTINGS_TRAILING,List.of(editor,reset)),false);
        var toggle=new Nodes.SettingRow("row:last",ControlState.READY,"Rank trivial commands last","",new Nodes.Toggle("last",ControlState.READY,"Rank trivial commands last",true,"last",false),false);
        var page=new Nodes.Stack("page",ControlState.READY,Nodes.LayoutRole.SETTINGS_PAGE,List.of(
            new Nodes.Section("ranking",ControlState.READY,"Ranking",new Nodes.Card("card",ControlState.READY,Nodes.CardKind.SETTINGS,List.of(row,toggle)))));
        try(var view=new KitView(context,new ViewSpec(page),event->{})) {
            view.setSize(744,900);layout(view);
            var card=find(view,"card",JComponent.class);var list=find(view,"trivial",JComponent.class);var title=find(view,"row:trivial.title",JComponent.class);
            assertThat(card.getWidth()).isEqualTo(context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_CARD_WIDTH));
            var listBounds=SwingUtilities.convertRectangle(list.getParent(),list.getBounds(),card);var titleBounds=SwingUtilities.convertRectangle(title.getParent(),title.getBounds(),card);
            var editorView=find(view,"trivial:editor",JComponent.class);
            assertThat(editorView.getWidth()).as("at its natural width").isEqualTo(editorView.getPreferredSize().width);
            assertThat(listBounds.x).as("beside its title").isGreaterThanOrEqualTo(titleBounds.x+context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.FORM_LABEL_WIDTH));
            assertThat(listBounds.x+listBounds.width).as("inside the card").isLessThanOrEqualTo(card.getWidth());
            assertThat(view.getMinimumSize().width).as("the page does not demand more than its card").isLessThanOrEqualTo(744);
        }finally{context.binding().close();context.icons().close();}
    }); }

    private static void layout(java.awt.Container parent){parent.doLayout();for(var child:parent.getComponents())if(child instanceof java.awt.Container container)layout(container);}
    static <T extends java.awt.Component> T find(java.awt.Component c, String name, Class<T> type) {
        if (name.equals(c.getName()) && type.isInstance(c)) return type.cast(c);
        if (c instanceof java.awt.Container parent) for (var child : parent.getComponents()) { var value = find(child, name, type); if (value != null) return value; }
        return null;
    }
}

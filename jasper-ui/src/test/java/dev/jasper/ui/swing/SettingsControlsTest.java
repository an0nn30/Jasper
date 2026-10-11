package dev.jasper.ui.swing;
import static org.assertj.core.api.Assertions.*;
import dev.jasper.ui.model.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import dev.jasper.ui.testsupport.Edt;
class SettingsControlsTest {
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

    private static void layout(java.awt.Container parent){parent.doLayout();for(var child:parent.getComponents())if(child instanceof java.awt.Container container)layout(container);}
    static <T extends java.awt.Component> T find(java.awt.Component c, String name, Class<T> type) {
        if (name.equals(c.getName()) && type.isInstance(c)) return type.cast(c);
        if (c instanceof java.awt.Container parent) for (var child : parent.getComponents()) { var value = find(child, name, type); if (value != null) return value; }
        return null;
    }
}

package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FieldTrailingTest {
    private static Field field(String id,ControlState state) {return new Field(id,state,FieldKind.SEARCH,"pending","Find","Search","");}
    private static Button action(String id) {return new Button(id,ControlState.READY,"Record","",ButtonKind.GHOST,id,"");}
    private static ViewSpec spec(ControlState state) {return new ViewSpec(new Nodes.Stack("root",state,LayoutRole.INLINE,List.of(field("find",ControlState.READY),action("clear"))));}
    /** The same view whose field says whether it is read-only (spec 8.2's Field.readOnly). */
    private static ViewSpec spec(ControlState state,boolean readOnly) {return new ViewSpec(new Nodes.Stack("root",state,LayoutRole.INLINE,List.of(
        new Field("find",ControlState.READY,FieldKind.SEARCH,"pending","Find","Search","",Optional.empty(),FieldPresentation.STANDARD,"","","",readOnly),action("clear"))));}
    @Test void ownedActionKeepsDocumentAndRejectsCollisionsBeforeReplacement() {
        Edt.run(()->{
            var context=KitFixtures.context();var events=new ArrayList<UiEvent>();
            try(var view=new KitView(context,spec(ControlState.READY),events::add)) {
                var input=KitViewTest.find(view,"find.input",JTextField.class);var document=input.getDocument();input.select(1,4);
                view.setFieldTrailing("find",Optional.of(action("record")));
                var button=KitViewTest.find(view,"record",JButton.class);button.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("record"));
                assertThatThrownBy(()->view.setFieldTrailing("find",Optional.of(action("clear")))).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(()->view.setFieldTrailing("clear",Optional.of(action("other")))).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(()->view.setFieldTrailing("find",Optional.of(new Button("other",ControlState.READY,"Bad","missing:icon",ButtonKind.GHOST,"bad","")))).isInstanceOf(IllegalArgumentException.class);
                assertThat(view.control("record").component).isSameAs(button);
                view.update(spec(ControlState.READY));
                assertThat(input.getDocument()).isSameAs(document);assertThat(input.getSelectedText()).isEqualTo("end");
                view.setSize(140,60);InteractiveRowsTest.layout(view);
                assertThat(input.getWidth()).isGreaterThanOrEqualTo(0);
                assertThat(SwingUtilities.convertRectangle(input.getParent(),input.getBounds(),button.getParent()).intersects(button.getBounds())).isFalse();
                view.update(spec(new ControlState(true,false,false,"")));assertThat(button.isEnabled()).isFalse();button.doClick(0);assertThat(events).hasSize(1);
                view.setFieldTrailing("find",Optional.empty());assertThat(button.getActionListeners()).isEmpty();
                assertThatThrownBy(()->view.control("record")).isInstanceOf(IllegalArgumentException.class);
            } finally {KitViewTest.release(context);}
        });
    }
    @Test void readOnlyKeepsSelectionAndIndependentActionButParentStateStillGatesBoth() {
        Edt.run(()->{
            var context=KitFixtures.context();var events=new ArrayList<UiEvent>();
            try(var view=new KitView(context,spec(ControlState.READY),events::add)) {
                var input=KitViewTest.find(view,"find.input",JTextField.class);var document=input.getDocument();input.select(1,4);
                view.update(spec(ControlState.READY,true));view.setFieldTrailing("find",Optional.of(action("stop")));
                input.getActionMap().get(javax.swing.text.DefaultEditorKit.deleteNextCharAction).actionPerformed(new java.awt.event.ActionEvent(input,0,"delete"));
                assertThat(input.getText()).isEqualTo("pending");assertThat(input.isEditable()).isFalse();assertThat(input.isEnabled()).isTrue();
                assertThat(input.getAccessibleContext().getAccessibleStateSet().contains(javax.accessibility.AccessibleState.EDITABLE)).isFalse();
                var stop=(JButton)view.control("stop").component;stop.doClick(0);assertThat(events).containsExactly(new UiEvent.Action("stop"));
                view.update(spec(ControlState.READY,true));assertThat(input.getDocument()).isSameAs(document);assertThat(input.getSelectedText()).isEqualTo("end");assertThat(input.isEditable()).isFalse();
                view.update(spec(new ControlState(true,true,true,""),true));assertThat(stop.isEnabled()).isFalse();assertThat(input.isEnabled()).isFalse();
                view.update(spec(ControlState.READY));assertThat(input.isEditable()).isTrue();
                view.close();assertThat(stop.getActionListeners()).isEmpty();
            } finally {KitViewTest.release(context);}
        });
    }
    @Test void modelCannotStealRetainedActionIdAndRemovalClosesChild() {
        Edt.run(()->{
            var context=KitFixtures.context();
            try(var view=new KitView(context,spec(ControlState.READY),event->{})) {
                view.setFieldTrailing("find",Optional.of(action("record")));var child=view.control("record");
                var collision=new ViewSpec(new Nodes.Stack("root",ControlState.READY,LayoutRole.INLINE,List.of(field("find",ControlState.READY),action("record"))));
                assertThatThrownBy(()->view.update(collision)).isInstanceOf(IllegalArgumentException.class);
                assertThat(view.control("record")).isSameAs(child);
                view.update(new ViewSpec(action("find")));assertThat(child.closed).isTrue();
                view.update(spec(ControlState.READY));assertThatThrownBy(()->view.control("record")).isInstanceOf(IllegalArgumentException.class);
            } finally {KitViewTest.release(context);}
        });
    }
}

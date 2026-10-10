package dev.jasper.ui.swing;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.ui.swing.KitViewTest.*;
class PickerMenusTest {
 @Test void referenceMenuAllocatesItsVisibleSearchAndUtilityRowsWithoutHorizontalClipping(){Edt.run(()->{
  var context=KitFixtures.context();var menu=new Nodes.PickerMenu("menu",ControlState.READY,"staging","Search credentials",List.of(),"","create","No credentials match “staging”","New Credential…","create","cancel");
  try(var view=new KitView(context,new ViewSpec(menu),e->{})){
   var size=view.getPreferredSize();assertThat(size.width).isEqualTo(222);assertThat(size.height).isEqualTo(107);view.setSize(size);layout(view);
   var field=find(view,"menu.search",JTextField.class);assertThat(field.getParent().getHeight()).isEqualTo(21);
   var list=find(view,"menu.rows",JList.class);assertThat(list.getCellBounds(0,0).height).isEqualTo(30);assertThat(list.getCellBounds(0,0).width).isEqualTo(212);
  }finally{release(context);}
 });}
 static void layout(java.awt.Container owner){owner.doLayout();for(var child:owner.getComponents())if(child instanceof java.awt.Container c)layout(c);}
 @Test void richCompactAndDefaultHaveSeparateGeometryAndRetainInputAcrossRefresh(){Edt.run(() -> {
  var context=KitFixtures.context();var events=new ArrayList<UiEvent>();
  var face=new Nodes.PickerFace("root","Key · test_ed25519","key",Nodes.Hue.GREEN,Nodes.PickerDensity.COMPACT);
  var choice=new Nodes.Choice("pick",ControlState.READY,Nodes.ChoiceKind.PICKER,List.of(),"","Credential","pick","lock",Optional.empty(),Optional.of(face));
  var item=new Nodes.PickerItem("root","root","Key · test_ed25519","key",Nodes.Hue.GREEN);
  var menu=new Nodes.PickerMenu("menu",ControlState.READY,"","Search credentials",List.of(item),"root","root","No matches","New Credential…","create","cancel");
  try(var view=new KitView(context,stack(choice,menu),events::add)){
   var button=find(view,"pick.input",JButton.class);assertThat(button.getPreferredSize().height).isEqualTo(22);
   view.update(stack(new Nodes.Choice("pick",ControlState.READY,Nodes.ChoiceKind.PICKER,List.of(),"","Credential","pick","lock",Optional.empty(),Optional.of(new Nodes.PickerFace("root","Stored key · test_ed25519","key",Nodes.Hue.GREEN,Nodes.PickerDensity.DEFAULT))),menu));
   assertThat(button.getPreferredSize().height).isEqualTo(28);view.update(stack(choice,menu));
   var input=find(view,"menu.search",JTextField.class);input.setText("roo");input.setCaretPosition(1);var document=input.getDocument();
   view.update(stack(choice,menu));assertThat(input.getDocument()).isSameAs(document);assertThat(input.getText()).isEqualTo("roo");assertThat(input.getCaretPosition()).isEqualTo(1);
   input.getActionMap().get("picker.enter").actionPerformed(null);assertThat(events).contains(new UiEvent.Action("root"));
  }finally{release(context);}
 });}
}

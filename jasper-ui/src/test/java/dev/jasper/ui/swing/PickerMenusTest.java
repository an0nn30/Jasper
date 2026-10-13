package dev.jasper.ui.swing;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.ui.swing.KitViewTest.*;
class PickerMenusTest {
 @Test void emptyFacesUseSourceDensitySpecificTextInsets(){Edt.run(()->{
  var context=KitFixtures.context();try{
   for(var density:Nodes.PickerDensity.values()){
    var button=new JButton();button.setSize(190,density==Nodes.PickerDensity.COMPACT?22:28);
    var image=new java.awt.image.BufferedImage(380,button.getHeight()*2,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
    try{g.scale(2,2);PickerMenus.paintFace(button,g,context,new Nodes.PickerFace("","","lock",Nodes.Hue.NONE,density),true,false);}finally{g.dispose();}
    var ink=ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.TEXT_PLACEHOLDER);int first=image.getWidth();
    for(int y=8;y<image.getHeight()-8;y++)for(int x=46;x<200;x++){var pixel=new java.awt.Color(image.getRGB(x,y),true);
     if(Math.abs(pixel.getRed()-ink.getRed())+Math.abs(pixel.getGreen()-ink.getGreen())+Math.abs(pixel.getBlue()-ink.getBlue())<15)first=Math.min(first,x);}
    var r=dev.jasper.ui.metrics.DesignMetrics.DEFAULT;boolean compact=density==Nodes.PickerDensity.COMPACT;
     // Device 2: the face inset, tile and gap before the text, plus the glyph's side bearing.
     int expected=2*(r.value(compact?dev.jasper.ui.metrics.DesignMetrics.Role.PICKER_COMPACT_FACE_INSET:dev.jasper.ui.metrics.DesignMetrics.Role.PICKER_DEFAULT_FACE_INSET)+r.value(compact?dev.jasper.ui.metrics.DesignMetrics.Role.PICKER_FACE_TILE:dev.jasper.ui.metrics.DesignMetrics.Role.PICKER_DEFAULT_FACE_TILE)+r.value(compact?dev.jasper.ui.metrics.DesignMetrics.Role.PICKER_GAP:dev.jasper.ui.metrics.DesignMetrics.Role.PICKER_DEFAULT_FACE_GAP))+2;assertThat(first).as("%s primary ink x at device2",density).isBetween(expected-1,expected+1);
   }
  }finally{release(context);}
 });}
 @Test void keyboardIntentSurvivesThemeAndFontRefresh(){Edt.run(()->{
  var context=KitFixtures.context();var events=new ArrayList<UiEvent>();
  var items=List.of(new Nodes.PickerItem("none","None","","none",Nodes.Hue.NONE,Nodes.PickerItemKind.UTILITY),new Nodes.PickerItem("login","Login","Password","user",Nodes.Hue.BLUE));
  var menu=new Nodes.PickerMenu("menu",ControlState.READY,"","Search",items,"login","none","No matches","New Credential…","create","cancel");
  var raw=new HashMap<Object,Object>();UIManager.getDefaults().forEach(raw::put);
  try(var view=new KitView(context,new ViewSpec(menu),events::add)){
   var field=find(view,"menu.search",JTextField.class);var rows=find(view,"menu.rows",JList.class);var document=field.getDocument();
   for(String expected:List.of("login","create")){
    field.getActionMap().get("picker.down").actionPerformed(null);
    var theme=KitFixtures.light();context.binding().publish(new dev.jasper.ui.theme.ThemePair(theme,theme));
    UIManager.put("defaultFont",new java.awt.Font("Dialog",0,18));UIManager.put("defaultFont",raw.get("defaultFont"));
    assertThat(((Nodes.PickerItem)rows.getSelectedValue()).id()).isEqualTo(expected);assertThat(field.getDocument()).isSameAs(document);
    field.getActionMap().get("picker.enter").actionPerformed(null);assertThat(events.getLast()).isEqualTo(new UiEvent.Action(expected));
   }
  }finally{UIManager.put("defaultFont",raw.get("defaultFont"));release(context);}
 });}
 @Test void disabledOpacityIsDimensionlessAtEveryUiScale(){Edt.run(()->{
  var context=KitFixtures.context();float previous=com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
  try{
   var method=com.formdev.flatlaf.util.UIScale.class.getDeclaredMethod("setUserScaleFactor",float.class,boolean.class);method.setAccessible(true);
   try{for(float scale:new float[]{1f,1.5f,2.5f}){
    method.invoke(null,scale,false);var button=new JButton();button.setEnabled(false);button.setSize(Math.round(190*scale),Math.round(22*scale));
    var image=new java.awt.image.BufferedImage(button.getWidth(),button.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
    try{PickerMenus.paintFace(button,g,context,new Nodes.PickerFace("Login","Password","user",Nodes.Hue.BLUE,Nodes.PickerDensity.COMPACT),false,false);}finally{g.dispose();}
    assertThat(image.getRGB(button.getWidth()*3/4,button.getHeight()/2)>>>24).as("opacity at %s",scale).isBetween(114,116);
   }}finally{method.invoke(null,previous,false);}
  }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}finally{release(context);}
 });}
 @Test void installedPopupWrapperFitsExactMenuWithoutScrollbar(){Edt.run(()->{
  var context=KitFixtures.context();var priorLaf=javax.swing.UIManager.getLookAndFeel();FlatLafInstaller.install(context.theme());
  var menu=new Nodes.PickerMenu("menu",ControlState.READY,"staging","Search credentials",List.of(),"","create","No credentials match “staging”","New Credential…","create","cancel");
  try(var view=new KitView(context,new ViewSpec(menu),e->{})){
   var popup=new JPopupMenu();popup.setLayout(new java.awt.BorderLayout());popup.setBorder(new javax.swing.border.EmptyBorder(0,0,0,0));popup.setOpaque(false);popup.add(view);popup.setPopupSize(222,107);popup.setSize(222,107);SwingUtilities.updateComponentTreeUI(popup);layout(popup);
   var rows=find(view,"menu.rows",JList.class);var scroll=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,rows);
   assertThat(popup.getInsets()).as("native host wrapper insets").isEqualTo(new java.awt.Insets(0,0,0,0));
   assertThat(view.getSize()).isEqualTo(new java.awt.Dimension(222,107));
   assertThat(scroll.getVerticalScrollBar().isVisible()).isFalse();assertThat(rows.getCellBounds(0,0).width).as("rows run edge to edge").isEqualTo(rows.getWidth());
  }finally{release(context);try{javax.swing.UIManager.setLookAndFeel(priorLaf);}catch(javax.swing.UnsupportedLookAndFeelException failure){throw new AssertionError(failure);}}
 });}
 @Test void referenceMenuAllocatesItsVisibleSearchAndUtilityRowsWithoutHorizontalClipping(){Edt.run(()->{
  var context=KitFixtures.context();var menu=new Nodes.PickerMenu("menu",ControlState.READY,"staging","Search credentials",List.of(),"","create","No credentials match “staging”","New Credential…","create","cancel");
  try(var view=new KitView(context,new ViewSpec(menu),e->{})){
   var size=view.getPreferredSize();assertThat(size.width).as("its widest row").isEqualTo(find(view,"menu.rows",JList.class).getPreferredSize().width);assertThat(size.height).isEqualTo(92);view.setSize(size);layout(view);
   var field=find(view,"menu.search",JTextField.class);assertThat(field.getParent().getHeight()).isEqualTo(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.PICKER_SEARCH_HEIGHT));
   var list=find(view,"menu.rows",JList.class);assertThat(list.getCellBounds(0,0).height).isEqualTo(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.PICKER_UTILITY_HEIGHT)+dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.PICKER_FOOTER_TOP));assertThat(list.getCellBounds(0,0).width).isEqualTo(list.getWidth());
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
   assertThat(button.getPreferredSize().height).isEqualTo(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.PICKER_DEFAULT_HEIGHT));view.update(stack(choice,menu));
   var input=find(view,"menu.search",JTextField.class);input.setText("roo");input.setCaretPosition(1);var document=input.getDocument();
   view.update(stack(choice,menu));assertThat(input.getDocument()).isSameAs(document);assertThat(input.getText()).isEqualTo("roo");assertThat(input.getCaretPosition()).isEqualTo(1);
   input.getActionMap().get("picker.enter").actionPerformed(null);assertThat(events).contains(new UiEvent.Action("root"));
  }finally{release(context);}
 });}

 @Test void aScrollingMenuLeavesItsWidestRowRoomBesideTheScrollbar(){Edt.run(()->{
  var context=KitFixtures.context();
  var items=java.util.stream.IntStream.range(0,12).mapToObj(i->new Nodes.PickerItem("k"+i,i==3?"Production database login":"Key "+i,"deploy@prod","key",Nodes.Hue.BLUE,Nodes.PickerItemKind.VALUE)).toList();
  var menu=new Nodes.PickerMenu("menu",ControlState.READY,"","Search",items,"","k0","No matches","New Credential\u2026","create","cancel");
  try(var view=new KitView(context,new ViewSpec(menu),e->{})){
   layout(view);
   var control=(PickerMenus.MenuControl)view.control("menu");
   int bar=control.scroll.getVerticalScrollBar().getPreferredSize().width;
   assertThat(bar).as("precondition: the bar takes width").isPositive();
   assertThat(control.scroll.getPreferredSize().width).as("thirteen rows scroll: the widest row plus the bar").isEqualTo(control.list.getPreferredSize().width+bar);
  }finally{release(context);}
 });}

 @Test void theMenuIsSizedByItsWidestRowInTheDefaultFacesType(){Edt.run(()->{
  var context=KitFixtures.context();
  var items=List.of(new Nodes.PickerItem("login","Production database login","deploy@prod","key",Nodes.Hue.BLUE,Nodes.PickerItemKind.VALUE));
  var menu=new Nodes.PickerMenu("menu",ControlState.READY,"","Search",items,"","login","No matches","New Credential\u2026","create","cancel");
  try(var view=new KitView(context,new ViewSpec(menu),e->{})){
   layout(view);
   var control=(PickerMenus.MenuControl)view.control("menu");
   var row=control.list.getCellRenderer().getListCellRendererComponent(control.list,items.getFirst(),0,false,false);
   var body=ThemePainting.font(context,dev.jasper.ui.metrics.Typography.Role.BODY);
   assertThat(row.getPreferredSize().width).as("a row reports its content width").isGreaterThan(control.list.getFontMetrics(body).stringWidth("Production database login"));
   assertThat(control.search.getFont()).as("the default face's type").isEqualTo(body);
   assertThat(control.component.getPreferredSize().width).as("no fixed menu width").isEqualTo(control.scroll.getPreferredSize().width);
  }finally{release(context);}
 });}
}

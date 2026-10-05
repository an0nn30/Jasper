package dev.jasper.app.plugins;
import dev.jasper.sdk.ui.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.ui.Nodes.*;
import static org.assertj.core.api.Assertions.*;
class KitValuesTest {
 static final ControlState READY=ControlState.READY;
 static Text text(String id){return new Text(id,READY,"content",TextRole.CAPTION);}
 static Button button(String id){return new Button(id,READY,"Save","add",ButtonKind.PRIMARY,"save","");}
 static Header header(){return new Header("header",READY,"Title",OptionalInt.of(2),Optional.of(text("context")),Optional.of(button("primary")),List.of());}
 @Test void everyNodeAndSupportFieldProjectsWithoutLoss() {
  var menu=List.of(new MenuItem("menu","Item","add","act",true,false));
  List<Node> nodes=List.of(text("text"),new Icon("icon",READY,"add",Hue.BLUE,IconSize.CONTROL,"Add"),button("button"),
   new MenuButton("menuButton",READY,"Menu","",ButtonKind.SECONDARY,"go",menu,"Tip"),new StatusDot("status",READY,StatusKind.SUCCESS),
   new Field("field",READY,FieldKind.PATH,"/tmp","Hint","Path","browse"),new Secret("secret",READY,"Hint","Secret","group"),
   new Choice("choice",READY,ChoiceKind.DROPDOWN,List.of(new Option("a","A",Optional.of(Hue.GREEN))),"a","Choice","select"),
   new Toggle("toggle",READY,"Toggle",true,"toggleAction",true),new Chip("chip",READY,"Chip",Hue.AMBER),
   new Progress("progress",READY,.5,"Half"),new Strength("strength",READY,3,"Strong"),
   new Nodes.Stack("stack",READY,LayoutRole.INLINE,List.of(text("child"))),
   new FormRow("row",READY,"","Caption",text("child"),Optional.of(text("trailing"))),
   new SettingRow("setting",READY,"Title","Description",new Toggle("check",READY,"On",false,"change",false),true),
   new Card("card",READY,CardKind.MIXED,List.of(text("child"))),new Section("section",READY,"Title",text("child")),
   new Empty("empty",READY,new Icon("ico",READY,"add",Hue.NONE,IconSize.EMPTY,"Empty"),"Title","Description",List.of(button("action"))),
   new ItemList("list",READY,ListKind.ORDERED,List.of(new ListItem("a",Optional.of(text("leading")),List.of(text("title")),List.of(text("subtitle")),List.of(new Toggle("inline",READY,"On",true,"change",true)))),Set.of("a"),"activate",menu,SelectionMode.MULTIPLE,Optional.of(button("add"))),
   header(),new Panel("panel",READY,header(),Optional.of(text("query")),text("body"),Optional.of(text("footer"))),
   new Form("form",READY,FormKind.HERO,"Title",Optional.of(text("hero")),List.of(text("section")),Optional.of(text("preview")),List.of(button("save")),"cancel"),
   new Split("split",READY,SplitKind.SIDEBAR_DETAIL,List.of(text("side"),text("body"))),
   new Table("table",READY,List.of(new Column("column","Title",TextRole.MONO,true)),List.of(new TableRow("row",Map.of("column","42"),Optional.of("add"))),Set.of("row"),"activate",menu,SelectionMode.MULTIPLE),
   new Tree("tree",READY,List.of(new TreeItem("branch","Title",Optional.of("folder"),false,List.of(new TreeItem("leaf","Leaf",Optional.empty(),true,List.of())))),Set.of("branch"),Set.of("leaf"),"activate"),
   new Custom("custom",READY,"document",CustomKind.EDITOR,"Document"));
  assertThat(nodes.stream().map(Object::getClass).distinct()).hasSize(Node.class.getPermittedSubclasses().length);
  for(var node:nodes){var spec=new ViewSpec(node);assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());}
 }
 @Test void explicitPickerCuesSurviveHostProjectionAndRejectOtherKinds(){
  for(String icon:List.of("lock","")){
   var choice=new Choice("picker",READY,ChoiceKind.PICKER,List.of(),"","Credential","choose",icon);
   var projected=(dev.jasper.ui.model.Nodes.Choice)KitValues.toModule(new ViewSpec(choice)).root();
   assertThat(projected.icon()).isEqualTo(icon);
  }
  assertThat(new Choice("picker",READY,ChoiceKind.PICKER,List.of(),"","Picker","choose").icon()).isEqualTo("key");
  assertThatThrownBy(()->new Choice("dropdown",READY,ChoiceKind.DROPDOWN,List.of(),"","Choice","select","lock")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new dev.jasper.ui.model.Nodes.Choice("dropdown",dev.jasper.ui.model.ControlState.READY,dev.jasper.ui.model.Nodes.ChoiceKind.DROPDOWN,List.of(),"","Choice","select","lock")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void everyMenuHueProjectsAndLegacyRemainsNeutral(){
  for(var hue:Hue.values()){
   var item=new MenuItem("item","Item","key","open",true,false,hue);
   assertThat(KitValues.toModule(item).hue().name()).isEqualTo(hue.name());
  }
  assertThat(new MenuItem("old","Old","key","open",true,false).hue()).isEqualTo(Hue.NEUTRAL);
 }
 @Test void everyEventProjectsItsOwnerItemAndPayload(){
  List<dev.jasper.ui.model.UiEvent> events=List.of(new dev.jasper.ui.model.UiEvent.Action("act"),new dev.jasper.ui.model.UiEvent.Edited("field","text"),new dev.jasper.ui.model.UiEvent.Selected("list",Set.of("a")),new dev.jasper.ui.model.UiEvent.Toggled("toggle",true),new dev.jasper.ui.model.UiEvent.Expanded("tree","branch",true),new dev.jasper.ui.model.UiEvent.Reordered("list","a",2),new dev.jasper.ui.model.UiEvent.Removed("list","a"),new dev.jasper.ui.model.UiEvent.SecretChanged("secret"));
  assertThat(events).hasSize(UiEvent.class.getPermittedSubclasses().length);for(var event:events)assertThat(KitValues.toSdk(event).toString()).isEqualTo(event.toString());
  assertThat(Arrays.stream(SemanticColor.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.theme.SemanticColor.values()).map(Enum::name).toArray(String[]::new));
 }
 @Test void settingsCapabilitiesProjectWithoutLoss(){
  var toggle=new Toggle("toggle",READY,"On",true,"toggle",false);
  var options=List.of(new Option("same","Same",Optional.empty()),new Option("nord","Nord",Optional.of(Hue.BLUE)));
  List<Node> nodes=List.of(
   new Field("number",READY,FieldKind.NUMBER,"1.5","","Size","",Optional.of(new Numeric(0.5,3,0.05,2))),
   new Choice("pick",READY,ChoiceKind.SEARCHABLE,options,"nord","Theme","","",Optional.of(new ChoiceSearch(1,"No matching themes",Optional.empty()))),
   new Choice("free",READY,ChoiceKind.SEARCHABLE,options,"","Font","","",Optional.of(new ChoiceSearch(0,"No matches",Optional.of("Iosevka")))),
   new SettingRow("setting",READY,"Title","",toggle,false,true),
   new FormRow("row",READY,"Label","",new Text("c",READY,"x",TextRole.BODY),Optional.empty(),true),
   new Tree("tree",READY,List.of(new TreeItem("a","A",Optional.empty(),false,List.of(new TreeItem("b","B",Optional.empty(),true,List.of(),OptionalInt.of(4),true)),OptionalInt.of(0),false)),Set.of("a"),Set.of("b"),""));
  for(var node:nodes){var spec=new ViewSpec(node);assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());}
  assertThat(Arrays.stream(FieldKind.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.FieldKind.values()).map(Enum::name).toArray(String[]::new));
  assertThat(Arrays.stream(ChoiceKind.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.ChoiceKind.values()).map(Enum::name).toArray(String[]::new));
 }
}

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
 /** The node classes in {@code nodes}, those inside their stacks and their forms' sections included (a Separator or a Notice is never a root). */
 static Set<Class<?>> classes(List<Node> nodes){var found=new HashSet<Class<?>>();for(var node:nodes){found.add(node.getClass());if(node instanceof Nodes.Stack stack)found.addAll(classes(stack.children()));if(node instanceof Form form)found.addAll(classes(form.sections()));}return found;}
 @Test void everyNodeAndSupportFieldProjectsWithoutLoss() {
  var menu=List.of(new MenuItem("menu","Item","add","act",true,false));
  List<Node> nodes=List.of(text("text"),new Icon("icon",READY,"add",Hue.BLUE,IconSize.CONTROL,"Add"),button("button"),
   new MenuButton("menuButton",READY,"Menu","",ButtonKind.SECONDARY,"go",menu,"Tip"),new StatusDot("status",READY,StatusKind.SUCCESS),
   new Field("field",READY,FieldKind.PATH,"/tmp","Hint","Path","browse"),new Secret("secret",READY,"Hint","Secret","group"),
   new PickerMenu("menu",READY,"","Search",List.of(new PickerItem("a","A","caption","key",Hue.GREEN),new PickerItem("none","None","","remove",Hue.NONE,PickerItemKind.UTILITY)),"a","a","Empty","Create","create","cancel"),
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
   new Form("form",READY,FormKind.PROMPT,"Title",Optional.of(text("hero")),List.of(text("section")),Optional.of(text("preview")),List.of(button("save")),"cancel"),
   new Split("split",READY,SplitKind.SIDEBAR_DETAIL,List.of(text("side"),text("body"))),
   new Table("table",READY,List.of(new Column("column","Title",TextRole.MONO,true)),List.of(new TableRow("row",Map.of("column","42"),Optional.of("add"))),Set.of("row"),"activate",menu,SelectionMode.MULTIPLE),
   new Tree("tree",READY,List.of(new TreeItem("branch","Title",Optional.of("folder"),false,List.of(new TreeItem("leaf","Leaf",Optional.empty(),true,List.of())))),Set.of("branch"),Set.of("leaf"),"activate"),
   new Breadcrumbs("path",READY,List.of(new Crumb("home","Home",List.of()),new Crumb("docs","docs",menu)),"Location",true,"/tmp","edit","go","done"),
   new Nodes.Stack("separated",READY,LayoutRole.INLINE,List.of(text("before"),new Separator("separator",READY),text("after"))),
   new Nodes.Stack("noticed",READY,LayoutRole.VERTICAL,List.of(new Notice("notice",READY,Severity.WARNING,"Plain FTP sends the password unencrypted",
    List.of(new Button("details",READY,"Details","",ButtonKind.LINK,"details",""))))),
   new Nodes.Properties("properties",READY,"Properties — minecraft-local",List.of(new PropertyRow("Host","192.168.1.124",true),
    new PropertyRow("Credentials","Vault locked",false,Optional.of(Severity.WARNING)))),
   new Custom("custom",READY,"document",CustomKind.EDITOR,"Document"));
  assertThat(classes(nodes)).hasSize(Node.class.getPermittedSubclasses().length);
  for(var node:nodes){var spec=new ViewSpec(node);assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());}
 }
 /** Plan 3b: a read-only field crosses the projection unchanged. */
 @Test void readOnlyFieldsProjectWithoutLoss(){
  for(boolean readOnly:List.of(false,true)){
   var spec=new ViewSpec(new Field("login",READY,FieldKind.TEXT,"No login (TFTP has none)","","Login","",Optional.empty(),FieldPresentation.STANDARD,"","","",readOnly));
   assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
   assertThat(((dev.jasper.ui.model.Nodes.Field)KitValues.toModule(spec).root()).readOnly()).isEqualTo(readOnly);
  }
 }
 /** Plan 3b: a technical caption crosses the projection unchanged. */
 @Test void technicalPickerCaptionsProjectWithoutLoss(){
  for(boolean technical:List.of(false,true)){
   var face=new PickerFace("Mac","dustin@192.168.1.119:22","server",Hue.BLUE,PickerDensity.DEFAULT,technical);
   var spec=new ViewSpec(new Choice("via",READY,ChoiceKind.DROPDOWN,List.of(new Option("mac","Mac",Optional.empty())),"mac","Via host","","",Optional.empty(),Optional.of(face)));
   assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
   assertThat(((dev.jasper.ui.model.Nodes.Choice)KitValues.toModule(spec).root()).face().orElseThrow().technicalCaption()).isEqualTo(technical);
  }
 }
 /** Plan 3b: a TABS choice crosses the projection unchanged. */
 @Test void tabsProjectWithoutLoss(){
  var spec=new ViewSpec(new Choice("tabs",READY,ChoiceKind.TABS,List.of(new Option("overview","Overview",Optional.empty()),new Option("details","Details",Optional.empty())),"overview","Plugin information",""));
  assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
  assertThat(((dev.jasper.ui.model.Nodes.Choice)KitValues.toModule(spec).root()).kind()).isEqualTo(dev.jasper.ui.model.Nodes.ChoiceKind.TABS);
 }
 /** Plan 3b: swatches of every hue cross the projection unchanged. */
 @Test void swatchesProjectWithoutLoss(){
  for(var hue:List.of(Hue.BLUE,Hue.GREEN,Hue.AMBER,Hue.PURPLE,Hue.NEUTRAL)){
   var spec=new ViewSpec(new Icon("swatch",READY,"file",hue,IconSize.SWATCH,"Swatch"));
   assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
  }
  assertThat(Arrays.stream(IconSize.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.IconSize.values()).map(Enum::name).toArray(String[]::new));
  // 3a's other renamed enums keep the model's names too (3a's final review: only ChoiceKind and ButtonPresentation were pinned).
  assertThat(Arrays.stream(FormKind.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.FormKind.values()).map(Enum::name).toArray(String[]::new));
  assertThat(Arrays.stream(LayoutRole.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.LayoutRole.values()).map(Enum::name).toArray(String[]::new));
  assertThat(Arrays.stream(TextRole.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.TextRole.values()).map(Enum::name).toArray(String[]::new));
 }
 /** Plan 3b: a footnote crosses the projection unchanged. */
 @Test void footnotesProjectWithoutLoss(){
  var spec=new ViewSpec(new Form("unlock-form",READY,FormKind.PROMPT_COMPACT,"Unlock Vault",Optional.empty(),List.of(text("password")),Optional.empty(),List.of(button("unlock")),
   "cancel",FormSubmission.NONE,"Auto-locks after 15 min idle"));
  assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
  assertThat(((dev.jasper.ui.model.Nodes.Form)KitValues.toModule(spec).root()).footnote()).isEqualTo("Auto-locks after 15 min idle");
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
  List<dev.jasper.ui.model.UiEvent> events=List.of(new dev.jasper.ui.model.UiEvent.Action("act"),new dev.jasper.ui.model.UiEvent.Edited("field","text"),new dev.jasper.ui.model.UiEvent.Selected("list",Set.of("a")),new dev.jasper.ui.model.UiEvent.Toggled("toggle",true),new dev.jasper.ui.model.UiEvent.Expanded("tree","branch",true),new dev.jasper.ui.model.UiEvent.Reordered("list","a",2),new dev.jasper.ui.model.UiEvent.Removed("list","a"),new dev.jasper.ui.model.UiEvent.SecretChanged("secret"),new dev.jasper.ui.model.UiEvent.Sorted("table","name"),new dev.jasper.ui.model.UiEvent.Clicked("tree","leaf"));
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
   new Choice("group",READY,ChoiceKind.SEARCHABLE,options,"","Group","","",Optional.of(new ChoiceSearch(0,"No matches",Optional.of(""),"Other"))),
   new Choice("via",READY,ChoiceKind.DROPDOWN,options,"nord","Via Host","","",Optional.empty(),
    Optional.of(new PickerFace("nord","deploy@db.example:22 · Work","server",Hue.BLUE,PickerDensity.DEFAULT))),
   new SettingRow("setting",READY,"Title","",toggle,false,true),
   new FormRow("row",READY,"Label","",new Text("c",READY,"x",TextRole.BODY),Optional.empty(),true),
   new Tree("tree",READY,List.of(new TreeItem("a","A",Optional.empty(),false,List.of(new TreeItem("b","B",Optional.empty(),true,List.of(),OptionalInt.of(4),true)),OptionalInt.of(0),false)),Set.of("a"),Set.of("b"),""));
  for(var node:nodes){var spec=new ViewSpec(node);assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());}
  assertThat(Arrays.stream(ChoiceKind.values()).map(Enum::name)).hasSize(dev.jasper.ui.model.Nodes.ChoiceKind.values().length);
  assertThat(Arrays.stream(FieldKind.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.FieldKind.values()).map(Enum::name).toArray(String[]::new));
  assertThat(Arrays.stream(ChoiceKind.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.ChoiceKind.values()).map(Enum::name).toArray(String[]::new));
 }
 @Test void browserCapabilitiesProjectWithoutLoss(){
  var pin=List.of(new MenuItem("pin","Pin","bookmark","pin",true,false));
  List<Node> nodes=List.of(
   new Table("files",READY,List.of(new Column("name","Name",TextRole.BODY,false,4.5,Optional.of(true)),new Column("size","Size",TextRole.DESCRIPTION,true,1,Optional.of(false)),new Column("plain","Plain",TextRole.DESCRIPTION,false)),
    List.of(new TableRow("docs",Map.of("name","docs"),Optional.of("folder"),false,Optional.of(pin)),new TableRow("notes",Map.of("name","notes"),Optional.empty(),true,Optional.of(List.of())),new TableRow("plain",Map.of(),Optional.empty())),
    Set.of("docs","notes"),"open",pin,SelectionMode.MULTIPLE,ScrollPolicy.OVERLAY),
   new Tree("keys",READY,List.of(new TreeItem("tabs","Tabs",Optional.of("folder"),false,List.of(new TreeItem("new","New tab",Optional.empty(),true,List.of(),OptionalInt.empty(),true,"Context",Optional.of(StatusKind.SUCCESS),List.of("Global","Cmd+T"),Optional.of(pin))),OptionalInt.of(1),false)),Set.of("tabs"),Set.of("new"),"edit"),
   new Breadcrumbs("path",READY,List.of(new Crumb("home","Home",List.of()),new Crumb("docs","docs",pin)),"Location",false,"","edit","go","done"));
  for(var node:nodes){var spec=new ViewSpec(node);assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());}
  assertThat(Arrays.stream(ScrollPolicy.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.ScrollPolicy.values()).map(Enum::name).toArray(String[]::new));
  assertThat(KitValues.toSdk(new dev.jasper.ui.model.UiEvent.Sorted("files","size"))).isEqualTo(new UiEvent.Sorted("files","size"));
 }
 /** Plan 2 Task 6c: row-owned list menus, switch tooltips and severity-tinted glyphs cross the projection unchanged. */
 @Test void managerAndNoticeCapabilitiesProjectWithoutLoss(){
  var menu=List.of(new MenuItem("settings","Open Settings","","a:settings",true,false),new MenuItem("sep","","","",false,true));
  var row=new ListItem("a",Optional.of(new Icon("a.icon",READY,"plugin",Hue.NEUTRAL,IconSize.RAIL,"Plugin")),List.of(new Text("a.name",READY,"A",TextRole.BODY_STRONG)),List.of(),
   List.of(new Toggle("a.on",READY,"Enable A",true,"a:toggle",true,"Restart to apply")),false,Optional.of(menu));
  var none=new ListItem("b",Optional.empty(),List.of(new Text("b.name",READY,"B",TextRole.BODY)),List.of(),List.of(),false,Optional.of(List.of()));
  List<Node> nodes=List.of(new ItemList("plugins",READY,ListKind.DOUBLE,List.of(row,none),Set.of("a"),"",List.of(),SelectionMode.SINGLE,Optional.empty()),
   new Icon("e",READY,"error",Hue.NONE,IconSize.RAIL,"Error",Optional.of(Severity.DANGER)),new Toggle("t",READY,"T",false,"t",false,"Why"));
  for(var node:nodes){var spec=new ViewSpec(node);assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());}
  var module=(dev.jasper.ui.model.Nodes.ItemList)KitValues.toModule(new ViewSpec(nodes.getFirst())).root();
  assertThat(module.items().getFirst().contextActions()).hasValueSatisfying(items -> assertThat(items).hasSize(2));
  assertThat(module.items().get(1).contextActions()).hasValue(List.of());
  assertThat(Arrays.stream(Severity.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.Severity.values()).map(Enum::name).toArray(String[]::new));
  for(var presentation:ButtonPresentation.values()){
   var link=new ViewSpec(new Button("b",READY,"Open","",ButtonKind.LINK,"open","",presentation));
   assertThat(KitValues.toModule(link).toString()).isEqualTo(link.toString());
   var more=new ViewSpec(new MenuButton("m",READY,"More","",ButtonKind.LINK,"",List.of(new MenuItem("i","I","","i",true,false)),"",presentation));
   assertThat(KitValues.toModule(more).toString()).isEqualTo(more.toString());
  }
  assertThat(Arrays.stream(ButtonPresentation.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.ButtonPresentation.values()).map(Enum::name).toArray(String[]::new));
 }
 /** Plan 2 Task 6b-2: RESULTS lists, headings and visible rows cross the projection unchanged. */
 @Test void resultListsProjectWithoutLoss(){
  var heading=new ListItem("h",Optional.empty(),List.of(new Text("h.title",READY,"Commands",TextRole.CAPTION)),List.of(),List.of(),true);
  var row=new ListItem("r",Optional.of(new Icon("r.icon",READY,"play",Hue.NONE,IconSize.RAIL,"Command")),List.of(new Text("r.title",READY,"New Tab",TextRole.BODY)),
   List.of(new Text("r.detail",READY,"a detail",TextRole.DESCRIPTION)),List.of(new Text("r.tag",READY,"Cmd+T",TextRole.CAPTION)));
  var list=new ItemList("results",READY,ListKind.RESULTS,List.of(heading,row),Set.of("r"),"run",List.of(),SelectionMode.SINGLE,Optional.empty(),OptionalInt.of(5));
  var spec=new ViewSpec(list);
  assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
  var module=(dev.jasper.ui.model.Nodes.ItemList)KitValues.toModule(spec).root();
  assertThat(module.items().getFirst().heading()).isTrue();
  assertThat(module.visibleRows()).hasValue(5);
  assertThat(module.kind()).isEqualTo(dev.jasper.ui.model.Nodes.ListKind.RESULTS);
  assertThat(Arrays.stream(ListKind.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.ListKind.values()).map(Enum::name).toArray(String[]::new));
  for(var presentation:FieldPresentation.values()){
   var field=new ViewSpec(new Field("q",READY,presentation == FieldPresentation.PLAIN_PATH ? FieldKind.PATH : presentation == FieldPresentation.STEPPER || presentation == FieldPresentation.SLIDER ? FieldKind.NUMBER : FieldKind.SEARCH,"42","Search","Search all","",presentation == FieldPresentation.STEPPER || presentation == FieldPresentation.SLIDER ? Optional.of(new Numeric(0,100,1,0)) : Optional.empty(),presentation));
   assertThat(KitValues.toModule(field).toString()).isEqualTo(field.toString());
   assertThat(((dev.jasper.ui.model.Nodes.Field)KitValues.toModule(field).root()).presentation().name()).isEqualTo(presentation.name());
  }
  assertThat(Arrays.stream(FieldPresentation.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.FieldPresentation.values()).map(Enum::name).toArray(String[]::new));
 }
 /** Plan 2 Task 8-kit: row tooltips, list Enter and Delete actions and pending switches cross the projection unchanged. */
 @Test void remoteRowCapabilitiesProjectWithoutLoss(){
  var row=new ListItem("a",Optional.of(new StatusDot("a.dot",READY,StatusKind.WARNING)),List.of(new Text("a.name",READY,"web",TextRole.BODY)),List.of(),
   List.of(new Button("a.star",READY,"","star.filled",ButtonKind.GHOST,"favorite:a","Remove from favorites",ButtonPresentation.EMBEDDED,Optional.of(Severity.WARNING)),
    new Toggle("a.run",READY,"Stop tunnel",true,"toggle:a",true,"",true)),false,Optional.empty(),"web, up 3 min");
  var list=new ItemList("tunnels",READY,ListKind.DOUBLE,List.of(row),Set.of("a"),"edit",List.of(),SelectionMode.SINGLE,Optional.empty(),OptionalInt.empty(),"toggle","delete");
  var table=new Table("files",READY,List.of(new Column("name","Name",TextRole.BODY,false)),List.of(new TableRow("n",Map.of("name","notes"),Optional.of("file"),false,Optional.empty(),"notes — 6 B")),Set.of(),"open",List.of());
  for(Node node:List.<Node>of(list,table)){var spec=new ViewSpec(node);assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());}
  var module=(dev.jasper.ui.model.Nodes.ItemList)KitValues.toModule(new ViewSpec(list)).root();
  assertThat(module.enterAction()).isEqualTo("toggle");assertThat(module.deleteAction()).isEqualTo("delete");
  assertThat(module.items().getFirst().tooltip()).isEqualTo("web, up 3 min");
  assertThat(((dev.jasper.ui.model.Nodes.Toggle)module.items().getFirst().trailing().get(1)).pending()).isTrue();
  assertThat(((dev.jasper.ui.model.Nodes.Button)module.items().getFirst().trailing().get(0)).iconSeverity()).hasValue(dev.jasper.ui.model.Nodes.Severity.WARNING);
  for(var severity:Severity.values()){var star=new ViewSpec(new Button("s",READY,"","star",ButtonKind.GHOST,"f","Star",ButtonPresentation.EMBEDDED,Optional.of(severity)));assertThat(KitValues.toModule(star).toString()).isEqualTo(star.toString());}
  var rows=(dev.jasper.ui.model.Nodes.Table)KitValues.toModule(new ViewSpec(table)).root();
  assertThat(rows.rows().getFirst().tooltip()).isEqualTo("notes — 6 B");
  assertThat(KitValues.toSdk(new dev.jasper.ui.model.UiEvent.Clicked("tunnels","a"))).isEqualTo(new UiEvent.Clicked("tunnels","a"));
 }
 /** Plan 2 Task 9-kit: the Editor's caret and severity text, field keys and list, stack Escape, toggle buttons and the DOCUMENT split project without loss. */
 @Test void editorCapabilitiesProjectWithoutLoss(){
  var find=new Field("find",READY,FieldKind.SEARCH,"x","Find","Find","next",Optional.empty(),FieldPresentation.EMBEDDED,"previous","close","");
  var filter=new Field("filter",READY,FieldKind.SEARCH,"","Filter","Filter","choose",Optional.empty(),FieldPresentation.STANDARD,"","","languages");
  var languages=new ItemList("languages",READY,ListKind.SINGLE,List.of(new ListItem("java",Optional.empty(),List.of(text("java.name")),List.of(),List.of())),Set.of(),"choose",List.of());
  var option=new Button("case",READY,"Aa","",ButtonKind.GHOST,"case","Match case",ButtonPresentation.STANDARD,Optional.empty(),Optional.of(true));
  var vim=new Text("vim",READY,":s/a/b",TextRole.MONO,Optional.of(Severity.DANGER),OptionalInt.of(3));
  var bar=new Nodes.Stack("bar",READY,LayoutRole.INLINE,List.of(find,option,vim),"close");
  var spec=new ViewSpec(new Split("tab",READY,SplitKind.DOCUMENT,List.of(new Custom("doc",READY,"document",CustomKind.EDITOR,"Document"),bar,filter,languages)));
  assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
  var module=(dev.jasper.ui.model.Nodes.Split)KitValues.toModule(spec).root();
  var stack=(dev.jasper.ui.model.Nodes.Stack)module.children().get(1);
  assertThat(stack.cancelAction()).isEqualTo("close");
  var field=(dev.jasper.ui.model.Nodes.Field)stack.children().get(0);
  assertThat(List.of(field.alternateAction(),field.cancelAction())).containsExactly("previous","close");
  assertThat(((dev.jasper.ui.model.Nodes.Button)stack.children().get(1)).selected()).hasValue(true);
  var caret=(dev.jasper.ui.model.Nodes.Text)stack.children().get(2);
  assertThat(caret.severity()).hasValue(dev.jasper.ui.model.Nodes.Severity.DANGER);assertThat(caret.caret()).hasValue(3);
  assertThat(((dev.jasper.ui.model.Nodes.Field)module.children().get(2)).listId()).isEqualTo("languages");
  assertThat(module.kind()).isEqualTo(dev.jasper.ui.model.Nodes.SplitKind.DOCUMENT);
 }
}

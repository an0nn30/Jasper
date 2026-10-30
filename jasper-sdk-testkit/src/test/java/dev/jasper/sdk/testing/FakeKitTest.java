package dev.jasper.sdk.testing;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FakeKitTest { @Test void kitOwnsCreatedViews() throws Exception {
 javax.swing.SwingUtilities.invokeAndWait(() -> {
  var kit = new FakeKit("sample");
  var view = kit.create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("t",dev.jasper.sdk.ui.ControlState.READY,"Hello",dev.jasper.sdk.ui.Nodes.TextRole.BODY)), e -> {});
  assertThat(kit.views()).containsExactly(view); view.close(); assertThat(kit.views()).isEmpty(); kit.close();
 });
}
 @Test void specializedContentFollowsFakeHostFontChanges() throws Exception {javax.swing.SwingUtilities.invokeAndWait(() -> {
  var kit=new FakeKit("editor");kit.contentFont(new java.awt.Font("Serif",0,17));
  var view=kit.create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Custom("custom",dev.jasper.sdk.ui.ControlState.READY,"content",dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR,"Document")),event -> {});
  var content=new javax.swing.JComponent() {};var changes=new java.util.ArrayList<java.awt.Font>();content.addPropertyChangeListener("font",event -> changes.add((java.awt.Font)event.getNewValue()));
  view.registerContent(new dev.jasper.sdk.ui.CustomContent("content",dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR,content,() -> {}));
  assertThat(content.getFont().getFamily()).isEqualTo("Serif");kit.contentFont(new java.awt.Font("SansSerif",0,20));
  assertThat(content.getFont().getFamily()).isEqualTo("SansSerif");assertThat(changes).hasSize(2);kit.close();
 });}
 @Test void plugInTestsSimulateAnInputMethodComposingInAField() throws Exception {javax.swing.SwingUtilities.invokeAndWait(() -> {
  var kit=new FakeKit("sample");var view=(FakeKitView)kit.create(KitContractTest.results("new",5),e -> {});
  assertThat(view.composing("query")).isFalse();
  view.setComposing("query",true);assertThat(view.composing("query")).isTrue();
  view.setComposing("query",false);assertThat(view.composing("query")).isFalse();
  assertThatIllegalArgumentException().isThrownBy(() -> view.setComposing("results",true));
  kit.close();
 });}
 /** Plan 3b: the fake takes no typing in a read-only field, or in one a hidden container holds, as the host does. */
 @Test void aReadOnlyOrHiddenFieldTakesNoTypingInTheFake() throws Exception {javax.swing.SwingUtilities.invokeAndWait(() -> {
  var kit=new FakeKit("sample");var events=new java.util.ArrayList<dev.jasper.sdk.ui.UiEvent>();var ready=dev.jasper.sdk.ui.ControlState.READY;
  var locked=new dev.jasper.sdk.ui.Nodes.Field("locked",ready,dev.jasper.sdk.ui.Nodes.FieldKind.TEXT,"x","","Locked","",java.util.Optional.empty(),dev.jasper.sdk.ui.Nodes.FieldPresentation.STANDARD,"","","",true);
  var open=new dev.jasper.sdk.ui.Nodes.Field("open",ready,dev.jasper.sdk.ui.Nodes.FieldKind.TEXT,"","","Open","");
  var hidden=new dev.jasper.sdk.ui.Nodes.Stack("hidden",new dev.jasper.sdk.ui.ControlState(false,true,false,""),dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL,java.util.List.of(open));
  var view=kit.create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("root",ready,dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL,java.util.List.of(locked,hidden))),events::add);
  view.type("locked","typed");view.emit(new dev.jasper.sdk.ui.UiEvent.Edited("locked","typed"));view.type("open","typed");
  assertThat(events).isEmpty();
  assertThatIllegalArgumentException().isThrownBy(() -> view.type("root","typed"));
  kit.close();
 });}
 /** Plan 4b Task 1: a click into a field focuses it; only a select-on-click field it brings the focus into selects all, and a hidden one takes no click. */
 @Test void aClickIntoAFieldFocusesItAndSelectsAllOnlyWhenItBringsTheFocusIn() throws Exception {javax.swing.SwingUtilities.invokeAndWait(() -> {
  var kit=new FakeKit("sample");var ready=dev.jasper.sdk.ui.ControlState.READY;
  var address=new dev.jasper.sdk.ui.Nodes.Field("address",ready,dev.jasper.sdk.ui.Nodes.FieldKind.TEXT,"example.com","","Address","",java.util.Optional.empty(),
   dev.jasper.sdk.ui.Nodes.FieldPresentation.STANDARD,"","","",false,java.util.Optional.empty(),"","",java.util.Optional.empty(),true);
  var tucked=new dev.jasper.sdk.ui.Nodes.Field("tucked",ready,dev.jasper.sdk.ui.Nodes.FieldKind.TEXT,"x","","Tucked","",java.util.Optional.empty(),
   dev.jasper.sdk.ui.Nodes.FieldPresentation.STANDARD,"","","",false,java.util.Optional.empty(),"","",java.util.Optional.empty(),true);
  var hidden=new dev.jasper.sdk.ui.Nodes.Stack("hidden",new dev.jasper.sdk.ui.ControlState(false,true,false,""),dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL,java.util.List.of(tucked));
  var view=(FakeKitView)kit.create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("root",ready,dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL,java.util.List.of(address,hidden))),e -> {});
  view.clickInto("address");
  assertThat(view.focused()).contains("address");
  assertThat(view.fieldSelection("address")).containsExactly(0,"example.com".length());
  view.clickInto("address");
  assertThat(view.fieldSelection("address")).as("a click in the focused field places the caret").containsExactly(0,0);
  view.clickInto("tucked");
  assertThat(view.focused()).as("a hidden field takes no click").contains("address");
  assertThatIllegalArgumentException().isThrownBy(() -> view.clickInto("root"));
  kit.close();
 });}
 /**
  * Plan 4b Task 1: the fake's suggestions follow the keys as the host's list does: Up and Down within the suggestions, an
  * update keeping a listed selection, an edit selecting the first, the focus moving on hiding them, a loading list that
  * Escape hides while Enter keeps its meaning, and a busy field taking no key.
  */
 @Test void aFieldsSuggestionsFollowTheKeysTheFocusAndUpdatesAsTheHostsDo() throws Exception {javax.swing.SwingUtilities.invokeAndWait(() -> {
  var kit=new FakeKit("sample");var ready=dev.jasper.sdk.ui.ControlState.READY;var events=new java.util.ArrayList<dev.jasper.sdk.ui.UiEvent>();
  var a=new dev.jasper.sdk.ui.Nodes.Suggestion("a","Alpha","");var b=new dev.jasper.sdk.ui.Nodes.Suggestion("b","Beta","Web Search");var c=new dev.jasper.sdk.ui.Nodes.Suggestion("c","Gamma","");
  java.util.function.BiFunction<dev.jasper.sdk.ui.ControlState,java.util.List<dev.jasper.sdk.ui.Nodes.Suggestion>,dev.jasper.sdk.ui.Nodes.Field> address=(state,rows) -> new dev.jasper.sdk.ui.Nodes.Field("address",state,
   dev.jasper.sdk.ui.Nodes.FieldKind.TEXT,"","","Address","go",java.util.Optional.empty(),dev.jasper.sdk.ui.Nodes.FieldPresentation.STANDARD,"","revert","",false,java.util.Optional.empty(),"","",java.util.Optional.empty(),false,rows,rows.isEmpty());
  java.util.function.BiFunction<dev.jasper.sdk.ui.ControlState,java.util.List<dev.jasper.sdk.ui.Nodes.Suggestion>,dev.jasper.sdk.ui.ViewSpec> spec=(state,rows) -> new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("root",ready,
   dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL,java.util.List.of(address.apply(state,rows),new dev.jasper.sdk.ui.Nodes.Field("other",ready,dev.jasper.sdk.ui.Nodes.FieldKind.TEXT,"","","Other",""))));
  var view=kit.create(spec.apply(ready,java.util.List.of(a,b,c)),events::add);
  assertThat(view.suggestions("address")).containsExactly(a,b,c);
  assertThat(view.selectedSuggestion("address")).contains("a");
  view.press("address","UP");assertThat(view.selectedSuggestion("address")).as("the first stays selected").contains("a");
  view.press("address","DOWN");view.press("address","DOWN");view.press("address","DOWN");
  assertThat(view.selectedSuggestion("address")).as("and the last").contains("c");
  view.update(spec.apply(ready,java.util.List.of(c,a)));assertThat(view.selectedSuggestion("address")).as("still listed").contains("c");
  view.update(spec.apply(ready,java.util.List.of(a,b)));assertThat(view.selectedSuggestion("address")).as("gone: the first").contains("a");
  view.press("address","DOWN");view.type("address","al");assertThat(view.selectedSuggestion("address")).as("an edit selects the first").contains("a");
  assertThat(events).containsExactly(new dev.jasper.sdk.ui.UiEvent.Edited("address","al"));
  view.focus("other");
  assertThat(view.suggestions("address")).as("the focus moved on").isEmpty();
  assertThat(view.selectedSuggestion("address")).isEmpty();
  view.type("address","alp");assertThat(view.suggestions("address")).containsExactly(a,b);
  view.update(spec.apply(ready,java.util.List.of()));events.clear();
  assertThat(view.suggestions("address")).as("only loading").isEmpty();
  view.press("address","TAB");view.press("address","DOWN");view.press("address","ENTER");
  assertThat(events).as("Enter keeps its meaning").containsExactly(new dev.jasper.sdk.ui.UiEvent.Action("go"));
  view.press("address","ESCAPE");view.press("address","ESCAPE");
  assertThat(events).as("Escape hides the loading list, then is the field's own").containsExactly(new dev.jasper.sdk.ui.UiEvent.Action("go"),
   new dev.jasper.sdk.ui.UiEvent.Action("revert"),new dev.jasper.sdk.ui.UiEvent.Action("revert"));
  view.update(spec.apply(new dev.jasper.sdk.ui.ControlState(true,true,true,""),java.util.List.of(a)));view.type("address","x");events.clear();
  assertThat(view.suggestions("address")).as("a busy field shows none").isEmpty();
  view.press("address","TAB");view.press("address","ESCAPE");assertThat(events).as("and takes no key").isEmpty();
  view.update(spec.apply(ready,java.util.List.of(a)));view.type("address","y");assertThat(view.suggestions("address")).containsExactly(a);
  view.emit(new dev.jasper.sdk.ui.UiEvent.SuggestionChosen("address","a",false));
  assertThat(view.suggestions("address")).as("an emitted choice hides them as a person's does").isEmpty();
  assertThatIllegalArgumentException().isThrownBy(() -> view.press("address","SPACE"));
  assertThatIllegalArgumentException().isThrownBy(() -> view.press("root","TAB"));
  assertThatIllegalArgumentException().isThrownBy(() -> view.suggestions("root"));
  kit.close();
 });}
 /** Plan 3c: rows unmount a table's empty state, so the fake drops focus inside it and refuses its nodes, as the host does. */
 @Test void rowsReplacingAFocusedEmptyStateClearFocusInTheFake() throws Exception {javax.swing.SwingUtilities.invokeAndWait(() -> {
  var kit=new FakeKit("sample");var view=(FakeKitView)kit.create(KitContractTest.tables("card",false),e -> {});
  view.focus("tunnels.new");assertThat(view.focused()).contains("tunnels.new");
  view.update(KitContractTest.tables("sticky",true));
  assertThat(view.focused()).as("the focused state is gone").isEmpty();
  assertThatIllegalArgumentException().isThrownBy(() -> view.focus("tunnels.new"));
  view.update(KitContractTest.tables("card",false));view.focus("tunnels.new");assertThat(view.focused()).contains("tunnels.new");
  kit.close();
 });}
 @Test void contextFailedStartAndStopOwnKitAndSemanticRefresh() throws Exception {javax.swing.SwingUtilities.invokeAndWait(() -> {
  try(var host=new FakePluginHost()) {
   var info=new dev.jasper.sdk.PluginInfo("owner","Owner","1.0.0",java.util.Set.of());var retained=new java.util.ArrayList<FakeKit>();
   host.start(info,java.util.Set.of(),java.util.Set.of(),context -> {var kit=(FakeKit)context.kit();retained.add(kit);kit.create(KitContractTest.spec("Before"),e -> {});kit.registerIcon(new dev.jasper.sdk.ui.IconAsset("owner:icon","M 1 1 L 15 15"));throw new IllegalStateException("failed start");});
   assertThat(retained.getFirst().views()).isEmpty();assertThat(retained.getFirst().icons()).isEmpty();
   var context=host.start(new dev.jasper.sdk.PluginInfo("active","Active","1.0.0",java.util.Set.of()),java.util.Set.of(),java.util.Set.of(),c -> {c.kit().create(KitContractTest.spec("Active"),e -> {});});
   var colors=new java.util.EnumMap<dev.jasper.sdk.ui.SemanticColor,java.awt.Color>(dev.jasper.sdk.ui.SemanticColor.class);for(var role:dev.jasper.sdk.ui.SemanticColor.values())colors.put(role,java.awt.Color.MAGENTA);
   var seen=new java.util.ArrayList<java.awt.Color>();context.appearance().onChanged(v -> seen.add(context.appearance().color(dev.jasper.sdk.ui.SemanticColor.ACCENT)));
   host.setSemanticColors(colors);host.flush();assertThat(seen).containsExactly(java.awt.Color.MAGENTA);
   assertThat(context.appearance().color(dev.jasper.sdk.ui.SemanticColor.SURFACE_STATUS)).isEqualTo(java.awt.Color.MAGENTA);
   var incomplete=new java.util.EnumMap<>(colors);incomplete.remove(dev.jasper.sdk.ui.SemanticColor.SURFACE_STATUS);
   assertThatIllegalArgumentException().isThrownBy(() -> host.setSemanticColors(incomplete));
   host.stopAll();assertThat(context.kit().views()).isEmpty();assertThat(context.kit().icons()).isEmpty();
   host.setSemanticColors(colors);host.flush();assertThat(seen).hasSize(1);
  }
 });}
}
class FakeKitContractTest extends KitContractTest {
 protected dev.jasper.sdk.ui.UiKit kit(String owner){return new FakeKit(owner);}
 protected void emit(dev.jasper.sdk.ui.UiView view,dev.jasper.sdk.ui.UiEvent event){((FakeKitView)view).emit(event);}
 protected void secret(dev.jasper.sdk.ui.UiView view,String id,char[] value){((FakeKitView)view).setSecret(id,value);}
 protected void owns(dev.jasper.sdk.ui.UiKit kit,dev.jasper.sdk.ui.UiView view){((FakeKit)kit).requireOwned(view);}
 protected void fieldText(dev.jasper.sdk.ui.UiView view,String id,String text){((FakeKitView)view).emit(new dev.jasper.sdk.ui.UiEvent.Edited(id,text));}
 protected int[] fieldSelection(dev.jasper.sdk.ui.UiView view,String id){return ((FakeKitView)view).fieldSelection(id);}
 protected void type(dev.jasper.sdk.ui.UiView view,String id,String text){((FakeKitView)view).type(id,text);}
 /** The fake has no components: a click is the event the host's button or option reports. */
 protected void click(dev.jasper.sdk.ui.UiView view,String id){int option=id.indexOf(".option.");emit(view,option<0?new dev.jasper.sdk.ui.UiEvent.Action(id):new dev.jasper.sdk.ui.UiEvent.Selected(id.substring(0,option),java.util.Set.of(id.substring(option+".option.".length()))));}
 protected void clickInto(dev.jasper.sdk.ui.UiView view,String id){((FakeKitView)view).clickInto(id);}
 protected java.util.List<String> suggestions(dev.jasper.sdk.ui.UiView view,String id){return ((FakeKitView)view).suggestions(id).stream().map(dev.jasper.sdk.ui.Nodes.Suggestion::title).toList();}
 protected void press(dev.jasper.sdk.ui.UiView view,String id,String key){((FakeKitView)view).press(id,key);}
 protected void stop(dev.jasper.sdk.ui.UiKit kit){((FakeKit)kit).close();}
}

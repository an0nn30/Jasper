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
 protected void stop(dev.jasper.sdk.ui.UiKit kit){((FakeKit)kit).close();}
}

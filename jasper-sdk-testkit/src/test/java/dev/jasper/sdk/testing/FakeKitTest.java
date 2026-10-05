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
 @Test void contextFailedStartAndStopOwnKitAndSemanticRefresh() throws Exception {javax.swing.SwingUtilities.invokeAndWait(() -> {
  try(var host=new FakePluginHost()) {
   var info=new dev.jasper.sdk.PluginInfo("owner","Owner","1.0.0",java.util.Set.of());var retained=new java.util.ArrayList<FakeKit>();
   host.start(info,java.util.Set.of(),java.util.Set.of(),context -> {var kit=(FakeKit)context.kit();retained.add(kit);kit.create(KitContractTest.spec("Before"),e -> {});kit.registerIcon(new dev.jasper.sdk.ui.IconAsset("owner:icon","M 1 1 L 15 15"));throw new IllegalStateException("failed start");});
   assertThat(retained.getFirst().views()).isEmpty();assertThat(retained.getFirst().icons()).isEmpty();
   var context=host.start(new dev.jasper.sdk.PluginInfo("active","Active","1.0.0",java.util.Set.of()),java.util.Set.of(),java.util.Set.of(),c -> {c.kit().create(KitContractTest.spec("Active"),e -> {});});
   var colors=new java.util.EnumMap<dev.jasper.sdk.ui.SemanticColor,java.awt.Color>(dev.jasper.sdk.ui.SemanticColor.class);for(var role:dev.jasper.sdk.ui.SemanticColor.values())colors.put(role,java.awt.Color.MAGENTA);
   var seen=new java.util.ArrayList<java.awt.Color>();context.appearance().onChanged(v -> seen.add(context.appearance().color(dev.jasper.sdk.ui.SemanticColor.ACCENT)));
   host.setSemanticColors(colors);host.flush();assertThat(seen).containsExactly(java.awt.Color.MAGENTA);
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
 protected void stop(dev.jasper.sdk.ui.UiKit kit){((FakeKit)kit).close();}
}

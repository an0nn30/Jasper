package dev.jasper.app.plugins;
import dev.jasper.sdk.ui.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class HostedKitTest {
 static ViewSpec custom(Nodes.CustomKind kind,String content){return new ViewSpec(new Nodes.Custom("node",ControlState.READY,content,kind,"Document"));}
 @Test void customLifecycleAndOrdinaryControlRejection() throws Exception {SwingUtilities.invokeAndWait(() -> {
  var kit=new HostedKit("editor",() -> true,() -> KitThemeBridge.fallback(false),null,new Containment(java.awt.EventQueue::isDispatchThread));
  var view=kit.create(custom(Nodes.CustomKind.EDITOR,"doc"),e -> {});
  var ordinary=new JPanel();ordinary.add(new JButton("Wrong"));
  assertThatThrownBy(() -> view.registerContent(new CustomContent("doc",Nodes.CustomKind.EDITOR,ordinary,() -> {}))).isInstanceOf(IllegalArgumentException.class);
  var editor=new JTextArea("keep this document");editor.setCaretPosition(4);var document=editor.getDocument();var closed=new AtomicInteger();
  view.registerContent(new CustomContent("doc",Nodes.CustomKind.EDITOR,new JScrollPane(editor),closed::incrementAndGet));
  view.update(custom(Nodes.CustomKind.EDITOR,"doc"));assertThat(editor.getDocument()).isSameAs(document);assertThat(editor.getCaretPosition()).isEqualTo(4);assertThat(closed.get()).isZero();
  view.update(custom(Nodes.CustomKind.PREVIEW,"other"));assertThat(closed.get()).isEqualTo(1);view.close();kit.close();assertThat(closed.get()).isEqualTo(1);
 });}
 @Test void sameVariantThemeAndGlassRefreshPreserveEditorDocumentCaretAndSyntax() throws Exception {SwingUtilities.invokeAndWait(() -> {
  var current=new java.util.concurrent.atomic.AtomicReference<>(new dev.jasper.app.appearance.ThemeController().current());
  var updates=new java.util.ArrayList<java.util.function.Consumer<dev.jasper.app.appearance.ResolvedTheme>>();var glass=new java.util.ArrayList<Runnable>();double[] opacity={1};
  var containment=new Containment(java.awt.EventQueue::isDispatchThread);
  var ui=new HostedUi("editor",new dev.jasper.app.contributions.Contributions(),containment,Runnable::run,java.awt.EventQueue::isDispatchThread,() -> true,getClass().getClassLoader(),() -> dev.jasper.sdk.Variant.DARK,h -> () -> {},null,null,current::get,h -> {updates.add(h);return new dev.jasper.app.lifecycle.Subscription(() -> updates.remove(h));},new GlassSource(c -> opacity[0],h -> {glass.add(h);return new dev.jasper.app.lifecycle.Subscription(() -> glass.remove(h));}));
  var appearance=ui.appearance();var kit=new HostedKit("editor",() -> true,ui::kitThemes,appearance,containment);
  var editor=new JTextArea("retain document");editor.setCaretPosition(5);var document=editor.getDocument();var scroll=new JScrollPane(editor);
  var syntax=new java.util.concurrent.atomic.AtomicReference<dev.jasper.sdk.ui.SyntaxPalette>();var receivedOpacity=new java.util.concurrent.atomic.AtomicReference<Double>();
  Runnable domainRefresh=() -> {var palette=appearance.editorPalette();editor.setForeground(palette.foreground());editor.setCaretColor(palette.caret());syntax.set(appearance.syntaxPalette());receivedOpacity.set(appearance.workspaceOpacity(scroll));};
  domainRefresh.run();var domain=appearance.onWorkspaceChanged(v -> domainRefresh.run());
  var view=kit.create(custom(Nodes.CustomKind.EDITOR,"doc"),e -> {});view.registerContent(new CustomContent("doc",Nodes.CustomKind.EDITOR,scroll,domain::close));
  var before=appearance.color(SemanticColor.ACCENT);var previous=current.get().chrome();
  var tokens=new java.util.EnumMap<dev.jasper.app.appearance.Token,java.awt.Color>(dev.jasper.app.appearance.Token.class);tokens.putAll(previous.tokens());tokens.put(dev.jasper.app.appearance.Token.ACCENT,java.awt.Color.MAGENTA);tokens.put(dev.jasper.app.appearance.Token.EDITOR_CARET,java.awt.Color.ORANGE);
  var styles=new java.util.EnumMap<dev.jasper.app.appearance.SyntaxRole,dev.jasper.app.appearance.SyntaxStyle>(dev.jasper.app.appearance.SyntaxRole.class);styles.putAll(previous.syntax());styles.put(dev.jasper.app.appearance.SyntaxRole.COMMENT,new dev.jasper.app.appearance.SyntaxStyle(java.awt.Color.GREEN,false,true));
  var changed=new dev.jasper.app.appearance.GeneratedTheme(previous.name(),previous.dark(),previous.author(),tokens,styles,previous.terminal(),previous.uiKeys(),previous.icons());current.set(new dev.jasper.app.appearance.ResolvedTheme(changed,changed));
  var variants=new java.util.ArrayList<dev.jasper.sdk.Variant>();appearance.onChanged(variants::add);for(var update:java.util.List.copyOf(updates))update.accept(current.get());
  assertThat(variants).containsExactly(appearance.variant());assertThat(appearance.color(SemanticColor.ACCENT)).isNotEqualTo(before).isEqualTo(java.awt.Color.MAGENTA);
  assertThat(editor.getCaretColor()).isEqualTo(java.awt.Color.ORANGE);assertThat(syntax.get().style(dev.jasper.sdk.ui.SyntaxRole.COMMENT).color()).isEqualTo(java.awt.Color.GREEN);
  opacity[0]=.5;for(var listener:java.util.List.copyOf(glass))listener.run();assertThat(scroll.getBackground().getAlpha()).isEqualTo(128);assertThat(receivedOpacity.get()).isEqualTo(.5);
  assertThat(editor.getDocument()).isSameAs(document);assertThat(editor.getCaretPosition()).isEqualTo(5);
  view.close();kit.close();ui.closeAll();assertThat(updates).isEmpty();assertThat(glass).isEmpty();
 });}
 @Test void failingDisposalAndEventsAreSanitizedAndLeaveNoOwners() throws Exception {SwingUtilities.invokeAndWait(() -> {
  var failures=new java.util.ArrayList<PluginRuntime.Failure>();var containment=new Containment(java.awt.EventQueue::isDispatchThread,failures::add);
  var kit=new HostedKit("editor",() -> true,() -> KitThemeBridge.fallback(false),null,containment);
  var view=kit.create(custom(Nodes.CustomKind.EDITOR,"doc"),e -> {throw new IllegalStateException("secret-sentinel");});
  view.registerContent(new CustomContent("doc",Nodes.CustomKind.EDITOR,new JTextArea(),() -> {throw new IllegalStateException("secret-sentinel");}));
  view.dispatch(new UiEvent.Action("go"));view.update(new ViewSpec(new Nodes.Text("text",ControlState.READY,"Done",Nodes.TextRole.BODY)));
  assertThat(failures).hasSize(2);assertThat(failures.toString()).doesNotContain("secret-sentinel");kit.close();assertThat(kit.viewCount()).isZero();
 });}
 @org.junit.jupiter.api.io.TempDir java.nio.file.Path data;
 @Test void actualContextFailureStopAndRestartCloseKitViewsAndIcons() throws Exception {
  var retained=new java.util.ArrayList<HostedKit>();var views=new java.util.ArrayList<dev.jasper.sdk.ui.UiView>();
  for(boolean fail:java.util.List.of(true,false,false)) {
   var host=AppContractTest.host(data,java.time.Duration.ofMillis(100));
   SwingUtilities.invokeAndWait(() -> {
    var plugin=new HostedPlugin(new dev.jasper.sdk.PluginInfo("test.kit","Kit","1.0.0",java.util.Set.of()),java.util.Set.of(),java.util.Set.of(),java.util.Set.of(),getClass().getClassLoader(),() -> context -> {
     var kit=(HostedKit)context.kit();retained.add(kit);kit.registerIcon(new IconAsset("test.kit:icon","M 1 1 L 15 15"));
     views.add(kit.create(new ViewSpec(new Nodes.Progress("progress",ControlState.READY,-1,"Busy")),e -> {}));if(fail)throw new IllegalStateException("start failed");
    });
    var result=host.start(plugin);assertThat(result.state()).isEqualTo(fail?PluginStatus.State.FAILED:PluginStatus.State.ACTIVE);host.stop();
    for(var kit:retained) {assertThat(kit.viewCount()).isZero();assertThatThrownBy(() -> kit.registerIcon(new IconAsset("test.kit:other","M 1 1 L 2 2"))).isInstanceOf(IllegalStateException.class);}
    for(var view:views)assertThatThrownBy(view::spec).isInstanceOf(IllegalStateException.class);
   });
  }
 }
 @Test void eventsAfterOwnerShutdownRequestCannotReachPlugin() throws Exception {
  var kit=new HostedKit[1];var view=new HostedKitView[1];var delivered=new AtomicInteger();
  SwingUtilities.invokeAndWait(() -> {kit[0]=new HostedKit("owner",() -> true,() -> KitThemeBridge.fallback(false),null,new Containment(java.awt.EventQueue::isDispatchThread));view[0]=kit[0].create(new ViewSpec(new Nodes.Text("text",ControlState.READY,"Text",Nodes.TextRole.BODY)),e -> delivered.incrementAndGet());});
  // Stop requested inside an event turn from a worker: disposal cannot run until this turn ends.
  SwingUtilities.invokeAndWait(() -> {var worker=new Thread(kit[0]::close);worker.start();try{worker.join();}catch(InterruptedException e){throw new AssertionError(e);}view[0].dispatch(new UiEvent.Action("stale"));assertThat(delivered.get()).isZero();});
 }
}

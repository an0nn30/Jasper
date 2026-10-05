package dev.jasper.app.plugins;
import dev.jasper.sdk.ui.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class HostedKitTest {
 static ViewSpec custom(Nodes.CustomKind kind,String content){return new ViewSpec(new Nodes.Custom("node",ControlState.READY,content,kind,"Document"));}
 @Test void customLifecycleAndOrdinaryControlRejection() throws Exception {SwingUtilities.invokeAndWait(() -> {
  var kit=new HostedKit("editor",() -> true,() -> dev.jasper.app.appearance.ThemeTestSupport.pair(false),null,new Containment(java.awt.EventQueue::isDispatchThread));
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
  var tokens=new java.util.EnumMap<dev.jasper.ui.theme.Token,java.awt.Color>(dev.jasper.ui.theme.Token.class);tokens.putAll(previous.tokens());tokens.put(dev.jasper.ui.theme.Token.ACCENT,java.awt.Color.MAGENTA);tokens.put(dev.jasper.ui.theme.Token.EDITOR_CARET,java.awt.Color.ORANGE);
  var styles=new java.util.EnumMap<dev.jasper.ui.theme.SyntaxRole,dev.jasper.ui.theme.SyntaxStyle>(dev.jasper.ui.theme.SyntaxRole.class);styles.putAll(previous.syntax());styles.put(dev.jasper.ui.theme.SyntaxRole.COMMENT,new dev.jasper.ui.theme.SyntaxStyle(java.awt.Color.GREEN,false,true));
  var changed=new dev.jasper.ui.theme.GeneratedTheme(previous.name(),previous.dark(),previous.author(),tokens,styles,previous.terminal(),previous.uiKeys(),previous.icons(),previous.glass(),dev.jasper.ui.theme.SemanticDefaults.resolve(java.util.Map.of(),tokens,previous.terminal()));current.set(new dev.jasper.app.appearance.ResolvedTheme(changed,changed));
  var variants=new java.util.ArrayList<dev.jasper.sdk.Variant>();appearance.onChanged(variants::add);for(var update:java.util.List.copyOf(updates))update.accept(current.get());
  assertThat(variants).containsExactly(appearance.variant());assertThat(appearance.color(SemanticColor.ACCENT)).isNotEqualTo(before).isEqualTo(java.awt.Color.MAGENTA);
  assertThat(editor.getCaretColor()).isEqualTo(java.awt.Color.ORANGE);assertThat(syntax.get().style(dev.jasper.sdk.ui.SyntaxRole.COMMENT).color()).isEqualTo(java.awt.Color.GREEN);
  opacity[0]=.5;for(var listener:java.util.List.copyOf(glass))listener.run();assertThat(scroll.getBackground().getAlpha()).isEqualTo(128);assertThat(receivedOpacity.get()).isEqualTo(.5);
  assertThat(editor.getDocument()).isSameAs(document);assertThat(editor.getCaretPosition()).isEqualTo(5);
  view.close();kit.close();ui.closeAll();assertThat(updates).isEmpty();assertThat(glass).isEmpty();
 });}
 @Test void failingDisposalAndEventsAreSanitizedAndLeaveNoOwners() throws Exception {SwingUtilities.invokeAndWait(() -> {
  var failures=new java.util.ArrayList<PluginRuntime.Failure>();var containment=new Containment(java.awt.EventQueue::isDispatchThread,failures::add);
  var kit=new HostedKit("editor",() -> true,() -> dev.jasper.app.appearance.ThemeTestSupport.pair(false),null,containment);
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
  SwingUtilities.invokeAndWait(() -> {kit[0]=new HostedKit("owner",() -> true,() -> dev.jasper.app.appearance.ThemeTestSupport.pair(false),null,new Containment(java.awt.EventQueue::isDispatchThread));view[0]=kit[0].create(new ViewSpec(new Nodes.Text("text",ControlState.READY,"Text",Nodes.TextRole.BODY)),e -> delivered.incrementAndGet());});
  // Stop requested inside an event turn from a worker: disposal cannot run until this turn ends.
  SwingUtilities.invokeAndWait(() -> {var worker=new Thread(kit[0]::close);worker.start();try{worker.join();}catch(InterruptedException e){throw new AssertionError(e);}view[0].dispatch(new UiEvent.Action("stale"));assertThat(delivered.get()).isZero();});
 }
 @Test void chromeOnlyChangesRefreshContentAndDisposeBothAppearanceSubscriptions() throws Exception {SwingUtilities.invokeAndWait(() -> {
  var current=new java.util.concurrent.atomic.AtomicReference<>(new dev.jasper.app.appearance.ThemeController().current());
  var workspace=current.get().workspace();var updates=new java.util.ArrayList<java.util.function.Consumer<dev.jasper.app.appearance.ResolvedTheme>>();
  var glass=new java.util.ArrayList<Runnable>();double[] opacity={1};var themeSubscriptions=new AtomicInteger();var workspaceSubscriptions=new AtomicInteger();
  var containment=new Containment(java.awt.EventQueue::isDispatchThread);
  var ui=new HostedUi("editor",new dev.jasper.app.contributions.Contributions(),containment,Runnable::run,java.awt.EventQueue::isDispatchThread,() -> true,getClass().getClassLoader(),() -> dev.jasper.sdk.Variant.DARK,h -> () -> {},null,null,current::get,h -> {updates.add(h);return new dev.jasper.app.lifecycle.Subscription(() -> updates.remove(h));},new GlassSource(c -> opacity[0],h -> {glass.add(h);return new dev.jasper.app.lifecycle.Subscription(() -> glass.remove(h));}));
  var source=ui.appearance();
  var appearance=new Appearance() {
   public java.awt.Color color(SemanticColor role){return source.color(role);}
   public dev.jasper.sdk.Variant variant(){return source.variant();}
   public double workspaceOpacity(java.awt.Component component){return source.workspaceOpacity(component);}
   public javax.swing.Icon icon(String path){return source.icon(path);}
   public dev.jasper.sdk.Subscription onChanged(java.util.function.Consumer<dev.jasper.sdk.Variant> handler){var subscription=source.onChanged(handler);themeSubscriptions.incrementAndGet();return () -> {subscription.close();themeSubscriptions.decrementAndGet();};}
   public dev.jasper.sdk.Subscription onWorkspaceChanged(java.util.function.Consumer<dev.jasper.sdk.Variant> handler){var subscription=source.onWorkspaceChanged(handler);workspaceSubscriptions.incrementAndGet();return () -> {subscription.close();workspaceSubscriptions.decrementAndGet();};}
  };
  var kit=new HostedKit("editor",() -> true,ui::kitThemes,appearance,containment);
  var editor=new JTextArea("stable document");editor.setCaretPosition(4);var document=editor.getDocument();var scroll=new JScrollPane(editor);
  var view=kit.create(custom(Nodes.CustomKind.EDITOR,"doc"),e -> {});
  try {
   view.registerContent(new CustomContent("doc",Nodes.CustomKind.EDITOR,scroll,() -> {}));
   var previous=current.get().chrome();var tokens=new java.util.EnumMap<dev.jasper.ui.theme.Token,java.awt.Color>(dev.jasper.ui.theme.Token.class);tokens.putAll(previous.tokens());
   tokens.put(dev.jasper.ui.theme.Token.INPUT_BACKGROUND,java.awt.Color.MAGENTA);tokens.put(dev.jasper.ui.theme.Token.TEXT,java.awt.Color.GREEN);
   var changed=new dev.jasper.ui.theme.GeneratedTheme(previous.name(),previous.dark(),previous.author(),tokens,previous.syntax(),previous.terminal(),previous.uiKeys(),previous.icons(),previous.glass(),dev.jasper.ui.theme.SemanticDefaults.resolve(java.util.Map.of(),tokens,previous.terminal()));
   current.set(new dev.jasper.app.appearance.ResolvedTheme(changed,workspace));for(var update:java.util.List.copyOf(updates))update.accept(current.get());
   assertThat(current.get().workspace()).isSameAs(workspace);assertThat(source.editorPalette()).isEqualTo(HostedPalettes.editor(workspace));assertThat(source.syntaxPalette()).isEqualTo(HostedPalettes.syntax(workspace));
   assertThat(scroll.getBackground()).isEqualTo(source.color(SemanticColor.SURFACE_INPUT));assertThat(scroll.getForeground()).isEqualTo(source.color(SemanticColor.TEXT_PRIMARY));
   assertThat(editor.getDocument()).isSameAs(document);assertThat(editor.getCaretPosition()).isEqualTo(4);
   assertThat(themeSubscriptions.get()).isEqualTo(2);assertThat(workspaceSubscriptions.get()).isEqualTo(1);
   view.close();assertThat(themeSubscriptions.get()).isEqualTo(1);assertThat(workspaceSubscriptions.get()).isZero();
   var retainedColor=scroll.getBackground();opacity[0]=.5;for(var listener:java.util.List.copyOf(glass))listener.run();assertThat(scroll.getBackground()).isEqualTo(retainedColor);
  } finally {view.close();kit.close();ui.closeAll();}
  assertThat(themeSubscriptions.get()).isZero();assertThat(workspaceSubscriptions.get()).isZero();assertThat(updates).isEmpty();assertThat(glass).isEmpty();
 });}
}

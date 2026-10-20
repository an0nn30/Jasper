package dev.jasper.app.plugins;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.sdk.ui.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class HostedKitTest {
 static ViewSpec custom(Nodes.CustomKind kind,String content){return new ViewSpec(new Nodes.Custom("node",ControlState.READY,content,kind,"Document"));}
 @Test void presentationIconsFollowTheirPaintRouteAndSameVariantThemeChanges() throws Exception {SwingUtilities.invokeAndWait(() -> {
  var light=dev.jasper.app.appearance.ThemeTestSupport.generated(false);
  var dark=dev.jasper.app.appearance.ThemeTestSupport.generated(true);
  // Another Dark theme: Slate Cobalt Dark with plum text, so the same-variant change repaints the glyph.
  var other=ThemeTestSupport.recolored(dark,java.util.Map.of(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY,new java.awt.Color(0xc9a0e8),
   dev.jasper.ui.theme.SemanticColor.TEXT_SECONDARY,new java.awt.Color(0xa77fc4)));
  var themes=new java.util.concurrent.atomic.AtomicReference<>(new dev.jasper.ui.theme.ThemePair(light,dark));
  var kit=new HostedKit("owner",() -> true,themes::get,null,new Containment(java.awt.EventQueue::isDispatchThread));
  dev.jasper.ui.swing.FlatLafInstaller.install(light, dev.jasper.ui.kit.Kit.DEFAULT);
  var icon=kit.icon("add");var chrome=new JPanel();var workspace=new JPanel();workspace.putClientProperty("Jasper.zone","workspace");workspace.putClientProperty("Jasper.workspaceTheme",dark);
  try(var registry=new dev.jasper.ui.icons.IconRegistry()) {
   assertThat(icon.getIconWidth()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(registry.size(dev.jasper.ui.kit.Kit.DEFAULT.metrics(), dev.jasper.ui.model.Nodes.IconSize.RAIL)));
   assertThat(icon.getIconHeight()).isEqualTo(icon.getIconWidth());
   assertThat(pixels(icon,chrome)).containsExactly(pixels(registry.icon("add",light.semantic(),dev.jasper.ui.model.Nodes.Hue.NONE,16),chrome));
   assertThat(pixels(icon,workspace)).containsExactly(pixels(registry.icon("add",dark.semantic(),dev.jasper.ui.model.Nodes.Hue.NONE,16),workspace));
   var before=pixels(icon,workspace);themes.set(new dev.jasper.ui.theme.ThemePair(light,other));workspace.putClientProperty("Jasper.workspaceTheme",other);
   assertThat(pixels(icon,workspace)).containsExactly(pixels(registry.icon("add",other.semantic(),dev.jasper.ui.model.Nodes.Hue.NONE,16),workspace));
   assertThat(pixels(icon,workspace)).isNotEqualTo(before);
   kit.close();
  } finally {ThemeTestSupport.controller(true);}
 });}
 @Test void customPresentationSnapshotsOutliveTheSenderAndBelongToReceivingViews() throws Exception {SwingUtilities.invokeAndWait(() -> {
  var pair=dev.jasper.app.appearance.ThemeTestSupport.pair(false);
  var kit=new HostedKit("owner",() -> true,() -> pair,null,new Containment(java.awt.EventQueue::isDispatchThread));
  var registration=kit.registerIcon(new IconAsset("owner:custom","M 2 2 L 14 14"));
  var icon=kit.icon("owner:custom");
  try(var receiver=new dev.jasper.app.platform.PresentationIcons();var binding=new dev.jasper.ui.theme.ThemeBinding(pair,e -> {throw new AssertionError(e);});var original=new dev.jasper.ui.icons.IconRegistry()) {
   var source=dev.jasper.ui.swing.KitContext.of(dev.jasper.ui.kit.Kit.DEFAULT, binding, dev.jasper.ui.theme.ThemeRoute.WORKSPACE, original, () -> false, action -> "");
   var context=receiver.context(source);
   assertThat(context.binding()).isSameAs(binding);assertThat(context.route()).isEqualTo(source.route());
   assertThat(context.metrics()).isSameAs(source.metrics());assertThat(context.animationsEnabled()).isSameAs(source.animationsEnabled());assertThat(context.kit()).isSameAs(dev.jasper.ui.swing.FlatLafInstaller.installedKit());
   assertThat(receiver.name(icon)).contains("owner:custom");
   assertThat(original.names()).doesNotContain("owner:custom");
   registration.close();assertThatIllegalArgumentException().isThrownBy(() -> kit.icon("owner:custom"));kit.close();
   receiver.reset();assertThat(context.icons().names()).doesNotContain("owner:custom");
   // History may remount an expired record after its original plugin stopped: only the immutable path crosses.
   assertThat(receiver.name(icon)).contains("owner:custom");
   var first=pixels(context.icons().icon("owner:custom",context.theme().semantic(),dev.jasper.ui.model.Nodes.Hue.NONE,16),new JPanel());
   var other=dev.jasper.app.appearance.ThemeTestSupport.generated(true);
   binding.publish(new dev.jasper.ui.theme.ThemePair(pair.chrome(),other));
   var next=pixels(context.icons().icon("owner:custom",context.theme().semantic(),dev.jasper.ui.model.Nodes.Hue.NONE,16),new JPanel());
   assertThat(next).isNotEqualTo(first);
   receiver.close();assertThatIllegalStateException().isThrownBy(() -> context.icons().names());
  }
 });}
 /** Enterprise presentation plan 1 and plan 5a Task 10: the three-argument AppearanceBindings and seven-argument HostedKit read the kit installed when they make a context; PresentationIcons keeps its source context's kit. */
 @Test void appContextsAreMadeOverTheInstalledKitAndIconContextsKeepTheirSources() throws Exception {SwingUtilities.invokeAndWait(() -> {
  var base=dev.jasper.ui.kit.Kit.DEFAULT;
  var tall=new dev.jasper.ui.kit.Kit("tall-buttons",base.metrics().with(dev.jasper.ui.metrics.DesignMetrics.Role.BUTTON_HEIGHT,40),base.type(),base.faces(),base.controls(),base.colors(),base.uiKeys());
  var pair=ThemeTestSupport.pair(true);
  var spec=new ViewSpec(new Nodes.Button("go",ControlState.READY,"Go","",Nodes.ButtonKind.SECONDARY,"go",""));
  var original=UIManager.getLookAndFeel();
  var kit=new HostedKit("owner",() -> true,() -> pair,null,new Containment(java.awt.EventQueue::isDispatchThread));
  try(var bindings=new dev.jasper.app.appearance.AppearanceBindings(pair,e -> {throw new AssertionError(e);},() -> false);var receiver=new dev.jasper.app.platform.PresentationIcons()) {
   dev.jasper.ui.swing.FlatLafInstaller.install(pair.chrome(),base);
   var source=bindings.context(dev.jasper.ui.theme.ThemeRoute.WORKSPACE);
   var view=kit.create(spec,e -> {});
   dev.jasper.ui.swing.FlatLafInstaller.install(pair.chrome(),tall);
   assertThat(source.kit()).as("read when the context was made").isSameAs(base);
   assertThat(bindings.context(dev.jasper.ui.theme.ThemeRoute.CHROME).kit()).isSameAs(tall);
   assertThat(receiver.context(source).kit()).as("the source context's kit, not the installed one").isSameAs(source.kit());
   var tallView=kit.create(spec,e -> {});
   assertThat(tallView.component().getPreferredSize().height).as("a hosted view measures with the kit installed when it was made")
    .isGreaterThan(view.component().getPreferredSize().height);
  } finally {
   kit.close();
   try{UIManager.setLookAndFeel(original);}catch(UnsupportedLookAndFeelException failure){throw new AssertionError(failure);}
  }
 });}
 @Test void failedPresentationReconciliationRetainsBothGenerationsUntilRecovery() throws Exception {SwingUtilities.invokeAndWait(() -> {
  try(var receiver=new dev.jasper.app.platform.PresentationIcons()) {
   var context=receiver.context(dev.jasper.app.testsupport.KitTestSupport.context());
   var old=new dev.jasper.app.platform.PresentationIcon("owner:old","M 2 2 L 14 14");
   var next=new dev.jasper.app.platform.PresentationIcon("owner:next","M 2 14 L 14 2");
   receiver.reconcile(() -> receiver.name(old));
   var oldSpec=new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Icon("glyph",dev.jasper.ui.model.ControlState.READY,"owner:old",dev.jasper.ui.model.Nodes.Hue.NONE,dev.jasper.ui.model.Nodes.IconSize.RAIL,"Old"));
   try(var view=new dev.jasper.ui.swing.KitView(context,oldSpec,e -> {})) {
    var invalid=new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Stack("invalid",dev.jasper.ui.model.ControlState.READY,dev.jasper.ui.model.Nodes.LayoutRole.VERTICAL,java.util.List.of(
     new dev.jasper.ui.model.Nodes.Icon("next",dev.jasper.ui.model.ControlState.READY,"owner:next",dev.jasper.ui.model.Nodes.Hue.NONE,dev.jasper.ui.model.Nodes.IconSize.RAIL,"Next"),
     new dev.jasper.ui.model.Nodes.Icon("missing",dev.jasper.ui.model.ControlState.READY,"missing",dev.jasper.ui.model.Nodes.Hue.NONE,dev.jasper.ui.model.Nodes.IconSize.RAIL,"Missing"))));
    assertThatIllegalArgumentException().isThrownBy(() -> receiver.reconcile(() -> {receiver.name(next);view.update(invalid);}));
    assertThat(view.spec()).isEqualTo(oldSpec);
    assertThat(context.icons().names()).contains("owner:old","owner:next");
    // The actual previously mounted view remains paintable after failed reconciliation.
    view.setSize(40,40);var image=new java.awt.image.BufferedImage(40,40,java.awt.image.BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
    try{view.doLayout();view.printAll(graphics);}finally{graphics.dispose();}
    assertThat(java.util.Arrays.stream(image.getRGB(0,0,40,40,null,0,40)).anyMatch(pixel -> pixel!=0)).isTrue();
    var nextSpec=new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Icon("glyph",dev.jasper.ui.model.ControlState.READY,"owner:next",dev.jasper.ui.model.Nodes.Hue.NONE,dev.jasper.ui.model.Nodes.IconSize.RAIL,"Next"));
    receiver.reconcile(() -> {receiver.name(next);view.update(nextSpec);});
    assertThat(context.icons().names()).contains("owner:next").doesNotContain("owner:old");
   }
  }
 });}
 @Test void sameNameNewSnapshotSurvivesFailedReconciliationAndRetry() throws Exception {SwingUtilities.invokeAndWait(() -> {
  var source=new HostedKit("owner",() -> true,() -> dev.jasper.app.appearance.ThemeTestSupport.pair(false),null,new Containment(java.awt.EventQueue::isDispatchThread));
  var registration=source.registerIcon(new IconAsset("owner:mark","M 2 2 L 14 14"));var old=source.icon("owner:mark");
  registration.close();source.registerIcon(new IconAsset("owner:mark","M 2 14 L 14 2"));var next=source.icon("owner:mark");source.close();
  try(var receiver=new dev.jasper.app.platform.PresentationIcons();var expected=new dev.jasper.ui.icons.IconRegistry()) {
   var context=receiver.context(dev.jasper.app.testsupport.KitTestSupport.context());
   var oldName=receiver.name(old).orElseThrow();
   var oldSpec=receiverIconSpec(oldName);
   try(var view=new dev.jasper.ui.swing.KitView(context,oldSpec,e -> {})) {
    var before=dev.jasper.app.testsupport.IconPixels.of(dev.jasper.app.testsupport.KitTestSupport.require(view,"glyph",JLabel.class).getIcon());
    var attemptedName=new java.util.concurrent.atomic.AtomicReference<String>();
    assertThatIllegalArgumentException().isThrownBy(() -> receiver.reconcile(() -> {
     attemptedName.set(receiver.name(next).orElseThrow());
     // Fail while creating a new subtree, before mutating the retained control.
     view.update(new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Stack("invalid",dev.jasper.ui.model.ControlState.READY,dev.jasper.ui.model.Nodes.LayoutRole.VERTICAL,java.util.List.of(
      new dev.jasper.ui.model.Nodes.Icon("next",dev.jasper.ui.model.ControlState.READY,attemptedName.get(),dev.jasper.ui.model.Nodes.Hue.NONE,dev.jasper.ui.model.Nodes.IconSize.RAIL,"Next"),
      new dev.jasper.ui.model.Nodes.Icon("missing",dev.jasper.ui.model.ControlState.READY,"missing",dev.jasper.ui.model.Nodes.Hue.NONE,dev.jasper.ui.model.Nodes.IconSize.RAIL,"Missing")))));
    }));
    assertThat(view.spec()).isEqualTo(oldSpec);
    assertThat(dev.jasper.app.testsupport.IconPixels.of(dev.jasper.app.testsupport.KitTestSupport.require(view,"glyph",JLabel.class).getIcon())).containsExactly(before);
    assertThat(attemptedName.get()).isNotEqualTo(oldName);
    assertThat(context.icons().names().stream().filter(name -> name.contains(":"))).hasSize(2);
    expected.register("owner:mark","M 2 14 L 14 2");
    var expectedPixels=dev.jasper.app.testsupport.IconPixels.of(expected.icon("owner:mark",context.theme().semantic(),dev.jasper.ui.model.Nodes.Hue.NONE,16));
    assertThat(dev.jasper.app.testsupport.IconPixels.of(context.icons().icon(attemptedName.get(),context.theme().semantic(),dev.jasper.ui.model.Nodes.Hue.NONE,16))).containsExactly(expectedPixels);
    receiver.reconcile(() -> view.update(receiverIconSpec(receiver.name(next).orElseThrow())));
    var after=dev.jasper.app.testsupport.KitTestSupport.require(view,"glyph",JLabel.class).getIcon();
    assertThat(dev.jasper.app.testsupport.IconPixels.of(after)).isNotEqualTo(before).containsExactly(dev.jasper.app.testsupport.IconPixels.of(expected.icon("owner:mark",context.theme().semantic(),dev.jasper.ui.model.Nodes.Hue.NONE,after.getIconWidth())));
    assertThat(context.icons().names().stream().filter(name -> name.contains(":"))).hasSize(1);
   }
  }
 });}
 private static dev.jasper.ui.model.ViewSpec receiverIconSpec(String name) {
  return new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Icon("glyph",dev.jasper.ui.model.ControlState.READY,name,dev.jasper.ui.model.Nodes.Hue.NONE,dev.jasper.ui.model.Nodes.IconSize.RAIL,"Glyph"));
 }
 private static int[] pixels(javax.swing.Icon icon,java.awt.Component component) {
  var image=new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
  try{icon.paintIcon(component,graphics,0,0);}finally{graphics.dispose();}
  return image.getRGB(0,0,16,16,null,0,16);
 }
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
  var current=new java.util.concurrent.atomic.AtomicReference<>(ThemeTestSupport.controller(true).current());
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
  var recoloured=ThemeTestSupport.withSyntax(ThemeTestSupport.recolored(previous,java.util.Map.of(dev.jasper.ui.theme.SemanticColor.ACCENT,java.awt.Color.MAGENTA)),
   java.util.Map.of(dev.jasper.ui.theme.SyntaxRole.COMMENT,new dev.jasper.ui.theme.SyntaxStyle(java.awt.Color.GREEN,false,true)));
  var e=recoloured.editor();var changed=new dev.jasper.ui.theme.GeneratedTheme(recoloured.name(),recoloured.dark(),recoloured.author(),recoloured.semantic(),recoloured.terminal(),
   new dev.jasper.ui.theme.EditorColors(e.background(),e.foreground(),java.awt.Color.ORANGE,e.selection(),e.currentLine(),e.gutter(),e.lineNumbers()),recoloured.syntax(),recoloured.glass(),recoloured.derivedOverrides());
  current.set(dev.jasper.app.appearance.ThemeTestSupport.mixed(changed,changed));
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
  var current=new java.util.concurrent.atomic.AtomicReference<>(ThemeTestSupport.controller(true).current());
  var workspace=current.get().workspace();var updates=new java.util.ArrayList<java.util.function.Consumer<dev.jasper.app.appearance.ResolvedTheme>>();
  var glass=new java.util.ArrayList<Runnable>();double[] opacity={1};var themeSubscriptions=new AtomicInteger();var workspaceSubscriptions=new AtomicInteger();
  var containment=new Containment(java.awt.EventQueue::isDispatchThread);
  var ui=new HostedUi("editor",new dev.jasper.app.contributions.Contributions(),containment,Runnable::run,java.awt.EventQueue::isDispatchThread,() -> true,getClass().getClassLoader(),() -> dev.jasper.sdk.Variant.DARK,h -> () -> {},null,null,current::get,h -> {updates.add(h);return new dev.jasper.app.lifecycle.Subscription(() -> updates.remove(h));},new GlassSource(c -> opacity[0],h -> {glass.add(h);return new dev.jasper.app.lifecycle.Subscription(() -> glass.remove(h));}));
  var source=ui.appearance();
  var appearance=new Appearance() {
   public java.awt.Color color(SemanticColor role){return source.color(role);}
   public dev.jasper.sdk.Variant variant(){return source.variant();}
   public double workspaceOpacity(java.awt.Component component){return source.workspaceOpacity(component);}
   public WorkspacePalette workspacePalette(){return source.workspacePalette();}
   public EditorPalette editorPalette(){return source.editorPalette();}
   public SyntaxPalette syntaxPalette(){return source.syntaxPalette();}
   public dev.jasper.sdk.ui.Presentation presentation(){return HostedPresentation.standard();}
   public dev.jasper.sdk.Subscription onChanged(java.util.function.Consumer<dev.jasper.sdk.Variant> handler){var subscription=source.onChanged(handler);themeSubscriptions.incrementAndGet();return () -> {subscription.close();themeSubscriptions.decrementAndGet();};}
   public dev.jasper.sdk.Subscription onWorkspaceChanged(java.util.function.Consumer<dev.jasper.sdk.Variant> handler){var subscription=source.onWorkspaceChanged(handler);workspaceSubscriptions.incrementAndGet();return () -> {subscription.close();workspaceSubscriptions.decrementAndGet();};}
  };
  var kit=new HostedKit("editor",() -> true,ui::kitThemes,appearance,containment);
  var editor=new JTextArea("stable document");editor.setCaretPosition(4);var document=editor.getDocument();var scroll=new JScrollPane(editor);
  var view=kit.create(custom(Nodes.CustomKind.EDITOR,"doc"),e -> {});
  try {
   view.registerContent(new CustomContent("doc",Nodes.CustomKind.EDITOR,scroll,() -> {}));
   var previous=current.get().chrome();
   var changed=ThemeTestSupport.recolored(previous,java.util.Map.of(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT,java.awt.Color.MAGENTA,
    dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY,java.awt.Color.GREEN));
   current.set(dev.jasper.app.appearance.ThemeTestSupport.mixed(changed,workspace));for(var update:java.util.List.copyOf(updates))update.accept(current.get());
   assertThat(current.get().workspace()).isSameAs(workspace);assertThat(source.editorPalette()).isEqualTo(HostedPalettes.editor(workspace));assertThat(source.syntaxPalette()).isEqualTo(HostedPalettes.syntax(workspace));
   assertThat(scroll.getBackground()).isEqualTo(source.color(SemanticColor.SURFACE_INPUT));assertThat(scroll.getForeground()).isEqualTo(source.color(SemanticColor.TEXT_PRIMARY));
   assertThat(editor.getDocument()).isSameAs(document);assertThat(editor.getCaretPosition()).isEqualTo(4);
   assertThat(themeSubscriptions.get()).isEqualTo(2);assertThat(workspaceSubscriptions.get()).isEqualTo(1);
   view.close();assertThat(themeSubscriptions.get()).isEqualTo(1);assertThat(workspaceSubscriptions.get()).isZero();
   var retainedColor=scroll.getBackground();opacity[0]=.5;for(var listener:java.util.List.copyOf(glass))listener.run();assertThat(scroll.getBackground()).isEqualTo(retainedColor);
  } finally {view.close();kit.close();ui.closeAll();}
  assertThat(themeSubscriptions.get()).isZero();assertThat(workspaceSubscriptions.get()).isZero();assertThat(updates).isEmpty();assertThat(glass).isEmpty();
 });}
  @org.junit.jupiter.api.Test void aPluginsViewsAreMadeWithThePresentationsKit() throws Exception {
    var kit = new dev.jasper.ui.kit.Kit("reach", dev.jasper.ui.kit.Kit.DEFAULT.metrics().with(dev.jasper.ui.metrics.DesignMetrics.Role.BUTTON_HEIGHT, 26),
        dev.jasper.ui.kit.Kit.DEFAULT.type(), dev.jasper.ui.kit.Kit.DEFAULT.faces(), dev.jasper.ui.kit.Kit.DEFAULT.controls(),
        dev.jasper.ui.kit.Kit.DEFAULT.colors(), dev.jasper.ui.kit.Kit.DEFAULT.uiKeys());
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      var hosted = new HostedKit("owner", () -> true, () -> dev.jasper.app.appearance.ThemeTestSupport.pair(false), null,
          new Containment(java.awt.EventQueue::isDispatchThread), () -> false, action -> "", () -> kit);
      var view = hosted.create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("t", dev.jasper.sdk.ui.ControlState.READY, "x",
          dev.jasper.sdk.ui.Nodes.TextRole.BODY)), event -> { });
      org.assertj.core.api.Assertions.assertThat(view.context().kit()).isSameAs(kit);
      view.close(); hosted.close();
    });
  }
}

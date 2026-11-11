package dev.jasper.sdk.testing;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/** Identical producer/consumer attachment admission and lifetime contracts for host and fake. */
public abstract class AnchorContractTest {
 public record Endpoint(UiKit kit,Windows windows,Runnable stop){}
 public interface Fixture extends AutoCloseable {Endpoint endpoint(String id);void close();}
 protected abstract Fixture fixture();
 static ViewSpec picker(ControlState state){return new ViewSpec(new Nodes.Choice("pick",state,Nodes.ChoiceKind.PICKER,List.of(),"","Credential","pick"));}
 /** Mounts {@code view} where anchors may be taken from it: a content tab of the plugin, which closing disposes. */
 static ContentTab mount(Endpoint e,UiView view,String id){return e.windows().openTab(new ContentSpec(id+".owner","Owner"),null,view);}
 @Test void delegatedAttachmentDoesNotDelegateContentOwnershipAndCloseIsOnce()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){
   var source=f.endpoint("source");var producer=f.endpoint("producer");var view=source.kit().create(picker(ControlState.READY),e->{});
   assertThatThrownBy(()->view.anchor("pick")).isInstanceOf(IllegalArgumentException.class);var window=mount(source,view,"source");var anchor=view.anchor("pick");
   var popup=producer.windows().popover(anchor);assertThatThrownBy(()->popup.setContent(view)).isInstanceOf(IllegalArgumentException.class);
   var content=producer.kit().create(picker(ControlState.READY),e->{});popup.setContent(content);popup.show();var closed=new AtomicInteger();popup.onClosed(closed::incrementAndGet);
   var other=producer.windows().popover(anchor);assertThatThrownBy(()->other.setContent(content)).isInstanceOf(IllegalArgumentException.class);other.close();
   var child=producer.windows().dialog(new DialogSpec("Owned child",anchor,true));child.show();view.update(picker(ControlState.READY));assertThat(anchor.isOpen()).isTrue();
   window.close();assertThat(anchor.isOpen()).isFalse();assertThat(closed).hasValue(1);popup.close();assertThat(closed).hasValue(1);
   assertThatThrownBy(content::spec).isInstanceOf(IllegalStateException.class);
  }
 });}
 @Test void hiddenDisabledBusyRemovedAndRetypedControlsPermanentlyInvalidate()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){
   var source=f.endpoint("source");var producer=f.endpoint("producer");
   var replacements=List.of(picker(new ControlState(false,true,false,"")),picker(new ControlState(true,false,false,"")),picker(new ControlState(true,true,true,"")),
      new ViewSpec(new Nodes.Text("pick",ControlState.READY,"replacement",Nodes.TextRole.BODY)),new ViewSpec(new Nodes.Text("other",ControlState.READY,"removed",Nodes.TextRole.BODY)));
   for(var replacement:replacements){var view=source.kit().create(picker(ControlState.READY),e->{});var window=mount(source,view,"source");var anchor=view.anchor("pick");var count=new AtomicInteger();anchor.onClosed(count::incrementAndGet);
    view.update(replacement);assertThat(anchor.isOpen()).isFalse();assertThat(count).hasValue(1);view.update(picker(ControlState.READY));assertThat(anchor.isOpen()).isFalse();assertThatThrownBy(()->producer.windows().dialog(new DialogSpec("Stale",anchor,true))).isInstanceOf(IllegalArgumentException.class);window.close();}
  }
 });}
 /** Plan 3c: rows unmount a table's empty state, so an anchor in it closes once and none is granted while rows show. */
 @Test void rowsUnmountingATablesEmptyStateCloseItsAnchors()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){var source=f.endpoint("source");var view=source.kit().create(KitContractTest.tables("card",false),e->{});mount(source,view,"source");
   var anchor=view.anchor("tunnels.new");var closed=new AtomicInteger();anchor.onClosed(closed::incrementAndGet);assertThat(anchor.isOpen()).isTrue();
   view.update(KitContractTest.tables("card",true));assertThat(anchor.isOpen()).isFalse();assertThat(closed).hasValue(1);
   assertThatThrownBy(()->view.anchor("tunnels.new")).as("no anchor into the state while rows show").isInstanceOf(IllegalArgumentException.class);
   view.update(KitContractTest.tables("card",false));assertThat(closed).hasValue(1);assertThat(view.anchor("tunnels.new").isOpen()).isTrue();
  }
 });}
 static ViewSpec menu(ControlState state,String item){return new ViewSpec(new Nodes.MenuButton("menu",state,"","menu",Nodes.ButtonKind.GHOST,"",
    List.of(new MenuItem(item,item,"",item,true,false)),"Menu"));}
 /** Since 0.29.0 a menu button issues an anchor too, as a browser's menu button opens its bookmark panel; new items keep it. */
 @Test void aMenuButtonAnchorsAPopoverWhichNewItemsKeepAndDisablingCloses()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){
   var source=f.endpoint("source");var view=source.kit().create(menu(ControlState.READY,"add"),e->{});mount(source,view,"source");var anchor=view.anchor("menu");
   var popup=source.windows().popover(anchor);var content=source.kit().create(actions(),e->{});popup.setContent(content);popup.show();
   var closed=new AtomicInteger();popup.onClosed(closed::incrementAndGet);
   view.update(menu(ControlState.READY,"remove"));assertThat(anchor.isOpen()).as("the same menu button with other items").isTrue();assertThat(closed).hasValue(0);
   view.update(menu(new ControlState(true,false,false,""),"remove"));assertThat(anchor.isOpen()).isFalse();assertThat(closed).hasValue(1);
   assertThatThrownBy(content::spec).isInstanceOf(IllegalStateException.class);
   view.update(new ViewSpec(new Nodes.Text("menu",ControlState.READY,"text",Nodes.TextRole.BODY)));
   assertThatThrownBy(()->view.anchor("menu")).as("text issues none").isInstanceOf(IllegalArgumentException.class);
  }
 });}
 @Test void movingTheSameControlToAnotherDescriptorParentInvalidates()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){var source=f.endpoint("source");var button=picker(ControlState.READY).root();
   var view=source.kit().create(new ViewSpec(new Nodes.Stack("first",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(button))),e->{});mount(source,view,"source");var anchor=view.anchor("pick");
   view.update(new ViewSpec(new Nodes.Stack("second",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(button))));assertThat(anchor.isOpen()).isFalse();
  }
 });}
 @Test void fabricatedAndCrossHostCapabilitiesFailBeforeMounting()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture();var foreign=fixture()){
   var source=f.endpoint("source");var other=foreign.endpoint("other");var view=source.kit().create(picker(ControlState.READY),e->{});mount(source,view,"source");var anchor=view.anchor("pick");
   UiAnchor spoof=new UiAnchor(){public boolean isOpen(){return true;}public Subscription onClosed(Runnable r){return ()->{};}public void close(){}};
   for(var value:List.of(spoof,anchor)){assertThatThrownBy(()->other.windows().popover(value)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->other.windows().dialog(new DialogSpec("Invalid",value,true))).isInstanceOf(IllegalArgumentException.class);}
  }
 });}
 @Test void eitherPluginStopClosesPresentationWithoutTakingTheOtherKit()throws Exception{SwingUtilities.invokeAndWait(()->{
  for(boolean producerStops:List.of(false,true))try(var f=fixture()){
   var source=f.endpoint("source");var producer=f.endpoint("producer");var view=source.kit().create(picker(ControlState.READY),e->{});mount(source,view,"source");var anchor=view.anchor("pick");
   var popup=producer.windows().popover(anchor);var content=producer.kit().create(picker(ControlState.READY),e->{});popup.setContent(content);popup.show();var count=new AtomicInteger();popup.onClosed(count::incrementAndGet);
   (producerStops?producer:source).stop().run();assertThat(count).hasValue(1);assertThatThrownBy(content::spec).isInstanceOf(IllegalStateException.class);if(producerStops)assertThat(anchor.isOpen()).isTrue();
  }
 });}
 static ViewSpec actions(){return new ViewSpec(new Nodes.Stack("actions",ControlState.READY,Nodes.LayoutRole.INLINE,List.of(new Nodes.Button("lock",ControlState.READY,"Lock","lock",Nodes.ButtonKind.GHOST,"lock",""))));}
 /** A page: a Custom node for canvas content, as a browser page is, beside a button. */
 static ViewSpec page(ControlState state){return new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
    new Nodes.Custom("page",state,"page.content",Nodes.CustomKind.PREVIEW,"Page"),new Nodes.Button("go",ControlState.READY,"Go","",Nodes.ButtonKind.GHOST,"go",""))));}
 /** A custom-painted component for the page's content, at a size of its own. */
 static javax.swing.JComponent canvas(){var canvas=new javax.swing.JComponent(){};canvas.setSize(640,480);return canvas;}
 static CustomContent content(javax.swing.JComponent canvas){return new CustomContent("page.content",Nodes.CustomKind.PREVIEW,canvas,()->{});}
 /** Browser plan 8 Task 1b (SDK 0.32.0): an area of a mounted Custom node's content issues an anchor a popover and a dialog attach to. */
 @Test void anAreaOfCustomContentIssuesAnAnchorForAPopover()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){
   var source=f.endpoint("source");var view=source.kit().create(page(ControlState.READY),e->{});view.registerContent(content(canvas()));
   var area=new ContentArea(10,20,30,8);
   assertThatThrownBy(()->view.anchorAt("page",area)).as("an unmounted view").isInstanceOf(IllegalArgumentException.class);
   mount(source,view,"source");var anchor=view.anchorAt("page",area);assertThat(anchor.isOpen()).isTrue();
   var popup=source.windows().popover(anchor);var content=source.kit().create(actions(),e->{});popup.setContent(content);popup.show();
   var closed=new AtomicInteger();popup.onClosed(closed::incrementAndGet);
   view.update(page(ControlState.READY));assertThat(anchor.isOpen()).as("the same node again").isTrue();assertThat(closed).hasValue(0);
   var child=source.windows().dialog(new DialogSpec("Owned child",anchor,true));child.show();assertThat(anchor.isOpen()).isTrue();child.close();
   assertThat(view.anchorAt("page",new ContentArea(0,0,0,0)).isOpen()).as("an empty area is a point").isTrue();
   anchor.close();assertThat(closed).hasValue(1);assertThatThrownBy(content::spec).isInstanceOf(IllegalStateException.class);
   assertThatThrownBy(()->source.windows().popover(anchor)).as("a closed anchor").isInstanceOf(IllegalArgumentException.class);
  }
 });}
 @Test void anAreaAnchorClosesWhenItsCustomNodeHidesOrLeaves()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){
   var source=f.endpoint("source");
   var replacements=List.of(page(new ControlState(false,true,false,"")),page(new ControlState(true,false,false,"")),
      new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(new Nodes.Button("go",ControlState.READY,"Go","",Nodes.ButtonKind.GHOST,"go","")))));
   for(var replacement:replacements){var view=source.kit().create(page(ControlState.READY),e->{});view.registerContent(content(canvas()));var window=mount(source,view,"source");
    var anchor=view.anchorAt("page",new ContentArea(10,20,30,8));var popup=source.windows().popover(anchor);popup.setContent(source.kit().create(actions(),e->{}));popup.show();
    var count=new AtomicInteger();popup.onClosed(count::incrementAndGet);
    view.update(replacement);assertThat(anchor.isOpen()).as("after %s",replacement).isFalse();assertThat(count).hasValue(1);
    if(replacement.root() instanceof Nodes.Stack stack&&stack.children().size()==1)assertThatThrownBy(()->view.anchorAt("page",new ContentArea(10,20,30,8))).as("a node that left").isInstanceOf(IllegalArgumentException.class);
    else{view.update(page(ControlState.READY));assertThat(anchor.isOpen()).as("closed for good").isFalse();}
    window.close();}
  }
 });}
 /** A page laid out again has moved what was in the area: resizing the content closes the anchor and its popover. */
 @Test void resizingAnAreasContentClosesItsAnchor()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){
   var source=f.endpoint("source");var view=source.kit().create(page(ControlState.READY),e->{});var canvas=canvas();view.registerContent(content(canvas));mount(source,view,"source");
   var anchor=view.anchorAt("page",new ContentArea(10,20,30,8));var popup=source.windows().popover(anchor);popup.setContent(source.kit().create(actions(),e->{}));popup.show();
   var count=new AtomicInteger();popup.onClosed(count::incrementAndGet);
   canvas.setLocation(5,5);assertThat(anchor.isOpen()).as("moving the content keeps the area where it is in it").isTrue();
   canvas.setSize(800,480);assertThat(anchor.isOpen()).isFalse();assertThat(count).hasValue(1);
   canvas.setSize(640,480);assertThat(anchor.isOpen()).as("closed for good").isFalse();
   assertThat(view.anchorAt("page",new ContentArea(10,20,30,8)).isOpen()).as("a new anchor at the new layout").isTrue();
  }
 });}
 @Test void onlyARegisteredCustomNodeIssuesAnAreaAnchor()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){
   var source=f.endpoint("source");var view=source.kit().create(page(ControlState.READY),e->{});mount(source,view,"source");var area=new ContentArea(10,20,30,8);
   assertThatThrownBy(()->view.anchorAt("page",area)).as("no content registered").isInstanceOf(IllegalArgumentException.class);
   view.registerContent(content(canvas()));
   assertThatThrownBy(()->view.anchorAt("go",area)).as("a button").isInstanceOf(IllegalArgumentException.class);
   assertThatThrownBy(()->view.anchorAt("missing",area)).as("an unknown id").isInstanceOf(IllegalArgumentException.class);
   assertThatThrownBy(()->view.anchorAt("root",area)).as("a stack").isInstanceOf(IllegalArgumentException.class);
   assertThatThrownBy(()->new ContentArea(10,20,-1,8)).as("a negative width").isInstanceOf(IllegalArgumentException.class);
   assertThatThrownBy(()->new ContentArea(10,20,30,-1)).as("a negative height").isInstanceOf(IllegalArgumentException.class);
   assertThatThrownBy(()->view.anchor("page")).as("a Custom node is no control").isInstanceOf(IllegalArgumentException.class);
   assertThat(view.anchorAt("page",area).isOpen()).isTrue();
  }
 });}
 /** A dialog's content replaced while a child its old content owns closes the dialog still releases both views. */
 @Test void bodyReplacementClosedReentrantlyByOwnedChildStillReleasesAcceptedReplacement()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){var e=f.endpoint("source");var tab=mount(e,e.kit().create(actions(),event->{}),"source");
   var old=e.kit().create(actions(),event->{});var w=e.windows().dialog(new DialogSpec("Surface",tab,false));w.setContent(old);w.show();
   var anchor=old.anchor("lock");var child=e.windows().dialog(new DialogSpec("Child",anchor,true));child.onClosed(w::close);child.show();
   var closed=new AtomicInteger();w.onClosed(closed::incrementAndGet);
   var next=e.kit().create(actions(),event->{});w.setContent(next);w.close();next.close();assertThat(closed).hasValue(1);assertThatThrownBy(next::spec).isInstanceOf(IllegalStateException.class);assertThatThrownBy(old::spec).isInstanceOf(IllegalStateException.class);
  }
 });}
 /** A dialog is a surface: a cleanup registered after it closed runs at once, and once. */
 @Test void closedSurfaceImmediatelyRunsLateCleanupRegistrationOnce()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){var e=f.endpoint("source");var view=e.kit().create(actions(),event->{});mount(e,view,"source");
   var w=e.windows().dialog(new DialogSpec("Surface",view.anchor("lock"),false));w.setContent(e.kit().create(picker(ControlState.READY),event->{}));w.show();w.close();
   var called=new AtomicInteger();var registration=w.onClosed(called::incrementAndGet);assertThat(called).hasValue(1);
   registration.close();w.close();assertThat(called).hasValue(1);
  }
 });}

}

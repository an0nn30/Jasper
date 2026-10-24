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

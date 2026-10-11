package dev.jasper.sdk.testing;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.*;
import java.awt.Dimension;
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
 static PluginWindow mount(Endpoint e,UiView view,String id){var w=e.windows().create(new WindowSpec(id+".owner","Owner",new Dimension(300,200),false));w.setContent(view);w.show();return w;}
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
 static void title(PluginWindow window,Optional<UiView> view){window.setTitleActions(view);}
 @Test void titleAndBodyHaveIndependentAdmissionUpdateAndLifetime()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){
   var source=f.endpoint("source");var foreign=f.endpoint("foreign");var body=source.kit().create(actions(),e->{});
   var window=mount(source,body,"source");
   assertThatThrownBy(()->title(window,Optional.of(body))).isInstanceOf(IllegalArgumentException.class);
   var action=source.kit().create(actions(),e->{});title(window,Optional.of(action));var anchor=action.anchor("lock");
   title(window,Optional.of(action));assertThat(anchor.isOpen()).isTrue();
   assertThatThrownBy(()->window.setContent(action)).isInstanceOf(IllegalArgumentException.class);
   var wrong=foreign.kit().create(actions(),e->{});assertThatThrownBy(()->title(window,Optional.of(wrong))).isInstanceOf(IllegalArgumentException.class);assertThat(wrong.spec()).isEqualTo(actions());
   var bad=source.kit().create(picker(ControlState.READY),e->{});assertThatThrownBy(()->title(window,Optional.of(bad))).isInstanceOf(IllegalArgumentException.class);assertThat(bad.spec()).isEqualTo(picker(ControlState.READY));
   assertThatThrownBy(()->action.update(picker(ControlState.READY))).isInstanceOf(IllegalArgumentException.class);assertThat(action.spec()).isEqualTo(actions());assertThat(anchor.isOpen()).isTrue();
   var child=foreign.windows().dialog(new DialogSpec("Title child",anchor,true));var closed=new AtomicInteger();child.onClosed(closed::incrementAndGet);child.show();
   var replacement=source.kit().create(picker(ControlState.READY),e->{});window.setContent(replacement);assertThat(anchor.isOpen()).isTrue();assertThatThrownBy(body::spec).isInstanceOf(IllegalStateException.class);
   title(window,Optional.empty());assertThat(anchor.isOpen()).isFalse();assertThat(closed).hasValue(1);assertThat(replacement.spec()).isEqualTo(picker(ControlState.READY));assertThatThrownBy(action::spec).isInstanceOf(IllegalStateException.class);
   var last=source.kit().create(actions(),e->{});title(window,Optional.of(last));window.close();assertThatThrownBy(last::spec).isInstanceOf(IllegalStateException.class);
  }
 });}
 @Test void unshownAndClosedTitleActionsCannotIssueAnchors()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){var source=f.endpoint("source");var action=source.kit().create(actions(),e->{});var w=source.windows().create(new WindowSpec("source.owner","Owner",new Dimension(300,200),false));title(w,Optional.of(action));
   assertThatThrownBy(()->action.anchor("lock")).isInstanceOf(IllegalArgumentException.class);w.show();var anchor=action.anchor("lock");assertThat(anchor.isOpen()).isTrue();source.stop().run();assertThat(anchor.isOpen()).isFalse();
  }
 });}
 @Test void attachedSheetRejectsAnUnshownParentAtShowAndReleasesItsView()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){var e=f.endpoint("source");var w=e.windows().create(new WindowSpec("source.window","Owner",new Dimension(300,200),false));
   var child=e.windows().dialog(new DialogSpec("Sheet",w,true,DialogSpec.Presentation.ATTACHED_SHEET));var view=e.kit().create(picker(ControlState.READY),event->{});child.setContent(view);
   assertThatThrownBy(child::show).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(view::spec).isInstanceOf(IllegalStateException.class);
  }
 });}
 @Test void titleReplacementClosedReentrantlyByOwnedChildStillReleasesAcceptedReplacement()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){var e=f.endpoint("source");var body=e.kit().create(picker(ControlState.READY),event->{});var w=mount(e,body,"source");
   var old=e.kit().create(actions(),event->{});title(w,Optional.of(old));var anchor=old.anchor("lock");var child=e.windows().dialog(new DialogSpec("Child",anchor,true));child.onClosed(w::close);child.show();
   var next=e.kit().create(actions(),event->{});title(w,Optional.of(next));assertThatThrownBy(next::spec).isInstanceOf(IllegalStateException.class);assertThatThrownBy(old::spec).isInstanceOf(IllegalStateException.class);
  }
 });}
 @Test void bodyReplacementClosedReentrantlyByOwnedChildStillReleasesAcceptedReplacement()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){var e=f.endpoint("source");var old=e.kit().create(actions(),event->{});var w=mount(e,old,"source");
   var anchor=old.anchor("lock");var child=e.windows().dialog(new DialogSpec("Child",anchor,true));child.onClosed(w::close);child.show();
   var closed=new AtomicInteger();w.onClosed(closed::incrementAndGet);
   var next=e.kit().create(actions(),event->{});w.setContent(next);w.close();next.close();assertThat(closed).hasValue(1);assertThatThrownBy(next::spec).isInstanceOf(IllegalStateException.class);assertThatThrownBy(old::spec).isInstanceOf(IllegalStateException.class);
  }
 });}

 @Test void closedSurfaceImmediatelyRunsLateCleanupRegistrationOnce()throws Exception{SwingUtilities.invokeAndWait(()->{
  try(var f=fixture()){var e=f.endpoint("source");var view=e.kit().create(actions(),event->{});var w=mount(e,view,"source");w.close();
   var called=new AtomicInteger();var registration=w.onClosed(called::incrementAndGet);assertThat(called).hasValue(1);
   registration.close();w.close();assertThat(called).hasValue(1);
  }
 });}

}

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
}

package dev.jasper.app.plugins;
import dev.jasper.sdk.ui.*;
import dev.jasper.ui.swing.*;
import java.awt.EventQueue;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Opaque SDK handle; only app plugin adapters can mount its component. */
final class HostedKitView implements UiView {
 final HostedKit owner;
 private final KitView view;
 private ViewSpec spec;
 private boolean updating;
 private Object mount;
 void unmount(Object target){if(mount==target)mount=null;}
 void mount(Object target){guard();if(mount!=null && mount!=target)throw new IllegalArgumentException("View is already mounted");mount=target;}

 private Consumer<UiEvent> events;
 java.util.function.Predicate<UiEvent> hostEvents = event -> false;
 java.util.function.UnaryOperator<ViewSpec> hostSpec = value -> value;
 /** Runs after each descriptor update; a host-packed dialog uses it to follow the view's size (DialogFit). */
 Runnable updated = () -> { };

 private final AtomicBoolean closed=new AtomicBoolean();
 HostedKitView(HostedKit owner,KitContext context,ViewSpec spec,Consumer<UiEvent> events){this.owner=owner;this.events=Objects.requireNonNull(events);view=new KitView(context,KitValues.toModule(spec),e -> dispatch(KitValues.toSdk(e)));this.spec=spec;}
 void guard(){owner.guard();if(closed.get())throw new IllegalStateException("View is closed");}
 KitView component(){guard();return view;}
 void dispatch(UiEvent event){if(closed.get()||!owner.live())return;try{if(hostEvents.test(event))return;events.accept(event);}catch(Throwable failure){owner.failed();}}
 @Override public void update(ViewSpec next){guard();if(updating)throw new IllegalStateException("Reentrant view update");updating=true;try{next=hostSpec.apply(next);view.update(KitValues.toModule(next));spec=next;}finally{updating=false;}updated.run();}
 @Override public ViewSpec spec(){guard();return spec;}
 @Override public void withSecret(String id,Consumer<char[]> use){guard();view.withSecret(id,use);}
 @Override public void clearSecret(String id){guard();view.clearSecret(id);}
 @Override public void replaceSecret(String id,char[] value){guard();view.replaceSecret(id,value);}
 @Override public void focus(String id){guard();view.focus(id);}
 @Override public void reveal(String id){guard();view.reveal(id);}
 @Override public boolean composing(String fieldId){guard();return view.composing(fieldId);}
 @Override public void registerContent(CustomContent content){guard();Objects.requireNonNull(content);KitDiagnostics.validate(content);
  var expected=KitContent.find(spec.root(),content.id());if(expected==null||expected.kind()!=content.kind())throw new IllegalArgumentException("No matching Custom content id/kind");
  var owned=new KitContent(owner,content,view::route);try{view.registerContent(content.id(),content.component(),owned);}catch(Throwable failure){owned.abort();throw failure;}
 }
 @Override public void close(){if(!closed.compareAndSet(false,true))return;Runnable cleanup=() -> {view.close();events=e -> {};hostEvents=e -> false;hostSpec=v -> v;updated=() -> { };mount=null;owner.removed(this);};if(EventQueue.isDispatchThread())cleanup.run();else EventQueue.invokeLater(cleanup);}
}

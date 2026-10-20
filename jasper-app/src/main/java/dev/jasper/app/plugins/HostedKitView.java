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
 private ViewSpec sourceSpec;
 private boolean updating;
 Object mount;
 private final List<HostedAnchor> anchors=new ArrayList<>();
 void invalidateAnchors(){for(var anchor:List.copyOf(anchors))anchor.close();}
 void checkAnchors(){for(var anchor:List.copyOf(anchors))anchor.isOpen();}
 void unmount(Object target){if(mount==target){invalidateAnchors();mount=null;}}
 void mount(Object target){guard();if(mount!=null && mount!=target)throw new IllegalArgumentException("View is already mounted");mount=target;}

 private Consumer<UiEvent> events;
 java.util.function.Predicate<UiEvent> hostEvents = event -> false;
 java.util.function.UnaryOperator<ViewSpec> hostSpec = value -> value;
 /** Host-only: completes the converted view with what the SDK cannot say (a host row's own menu, spec 12.1). */
 java.util.function.UnaryOperator<dev.jasper.ui.model.ViewSpec> hostModule = value -> value;
 /** Runs after each descriptor update; a host-packed dialog uses it to follow the view's size (DialogFit). */
 Runnable updated = () -> { };

 private final AtomicBoolean closed=new AtomicBoolean();
 private final KitContext context;
 /** The context the view was made with. */
 KitContext context(){return context;}
 HostedKitView(HostedKit owner,KitContext context,ViewSpec spec,Consumer<UiEvent> events){this.owner=owner;this.context=context;this.events=Objects.requireNonNull(events);view=new KitView(context,KitValues.toModule(spec),e -> dispatch(KitValues.toSdk(e)));this.spec=spec;sourceSpec=spec;}
 void guard(){owner.guard();if(closed.get())throw new IllegalStateException("View is closed");}
 KitView component(){guard();return view;}
 void dispatch(UiEvent event){if(closed.get()||!owner.live())return;try{if(hostEvents.test(event))return;events.accept(event);}catch(Throwable failure){owner.failed();}}
 @Override public void update(ViewSpec next){guard();if(updating)throw new IllegalStateException("Reentrant view update");updating=true;try{owner.validateIcons(next);var projected=hostSpec.apply(next);if(projected!=next)owner.validateIcons(projected);view.update(hostModule.apply(KitValues.toModule(projected)));sourceSpec=next;spec=projected;}finally{updating=false;}checkAnchors();updated.run();}
 @Override public UiAnchor anchor(String id){guard();if(mount==null || owner.anchorHost==null)throw new IllegalArgumentException("Unmounted view");var anchor=new HostedAnchor(this,id);anchors.add(anchor);anchor.onClosed(() -> anchors.remove(anchor));return anchor;}
 @Override public ViewSpec spec(){guard();return spec;}
 /** Host-only original plugin descriptor, before draft/search row projection. */
 ViewSpec sourceSpec(){guard();return sourceSpec;}
 @Override public void withSecret(String id,Consumer<char[]> use){guard();view.withSecret(id,use);}
 @Override public void clearSecret(String id){guard();view.clearSecret(id);}
 @Override public void replaceSecret(String id,char[] value){guard();view.replaceSecret(id,value);}
 @Override public void focus(String id){guard();view.focus(id);}
 @Override public void selectAll(String id){guard();view.selectAll(id);}
 @Override public void reveal(String id){guard();view.reveal(id);}
 @Override public boolean composing(String fieldId){guard();return view.composing(fieldId);}
 @Override public void registerContent(CustomContent content){guard();Objects.requireNonNull(content);KitDiagnostics.validate(content);
  var expected=KitContent.find(spec.root(),content.id());if(expected==null||expected.kind()!=content.kind())throw new IllegalArgumentException("No matching Custom content id/kind");
  var owned=new KitContent(owner,content,view::route);try{view.registerContent(content.id(),content.component(),owned);}catch(Throwable failure){owned.abort();throw failure;}
  contents.removeIf(KitContent::closed);contents.add(owned);
 }
 /** This view's registered specialized content, refreshed when the view changes theme slot. */
 private final List<KitContent> contents=new ArrayList<>();
 /** Host-only: moves the view to a theme slot (a workspace content tab, or back to chrome) with or without the opaque
  * surface, and refreshes its registered specialized content at once rather than at its next reparent. */
 void route(dev.jasper.ui.theme.ThemeRoute route,boolean surface){guard();view.route(route);view.paintsSurface(surface);contents.removeIf(KitContent::closed);for(var content:List.copyOf(contents))content.refresh();}
 @Override public void close(){if(!closed.compareAndSet(false,true))return;Runnable cleanup=() -> {invalidateAnchors();view.close();events=e -> {};hostEvents=e -> false;hostSpec=v -> v;hostModule=v -> v;updated=() -> { };mount=null;owner.removed(this);};if(EventQueue.isDispatchThread())cleanup.run();else EventQueue.invokeLater(cleanup);}
}

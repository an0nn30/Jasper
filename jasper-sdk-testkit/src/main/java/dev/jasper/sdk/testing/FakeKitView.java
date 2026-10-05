package dev.jasper.sdk.testing;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.awt.EventQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
/** Functional descriptor/secret/event owner for plugin tests. */
public final class FakeKitView implements UiView {
 private static final Map<javax.swing.JComponent,FakeKitView> CONTENT_OWNERS=new IdentityHashMap<>();
 final FakeKit owner;
 private ViewSpec spec;
 private Consumer<UiEvent> events;
 private final Map<String,char[]> secrets=new HashMap<>();
 private final Map<String,CustomContent> contents=new HashMap<>();
 private final AtomicBoolean closed=new AtomicBoolean();
 private String focused;
 FakeKitView(FakeKit owner, ViewSpec spec, Consumer<UiEvent> events) {this.owner=owner;this.events=Objects.requireNonNull(events);update(spec);}
 void guard(){owner.guard();if(closed.get())throw new IllegalStateException("View is closed");}
 @Override public ViewSpec spec(){guard();return spec;}
 @Override public void update(ViewSpec next){guard();Objects.requireNonNull(next);var nodes=index(next.root());
  secrets.entrySet().removeIf(e -> {if(nodes.get(e.getKey()) instanceof Nodes.Secret)return false;Arrays.fill(e.getValue(),(char)0);return true;});
  for(var content:List.copyOf(contents.values()))if(nodes.values().stream().noneMatch(n -> n instanceof Nodes.Custom c && c.contentId().equals(content.id()) && c.kind()==content.kind() && index(spec.root()).get(c.id()) instanceof Nodes.Custom previous && previous.contentId().equals(content.id()) && previous.kind()==content.kind())){dispose(content);}
  spec=next;if(focused!=null&&!nodes.containsKey(focused))focused=null;
 }
 private static Map<String,Nodes.Node> index(Nodes.Node root){var result=new HashMap<String,Nodes.Node>();walk(root,result);return result;}
 private static void walk(Nodes.Node node,Map<String,Nodes.Node> result){result.put(node.id(),node);for(var child:children(node))walk(child,result);}
 private void requireSecret(String id){guard();if(!(index(spec.root()).get(id) instanceof Nodes.Secret))throw new IllegalArgumentException("Not a secret node");}
 /** Simulate input; the fake owns a copy and never includes it in diagnostics. */
 public void setSecret(String id,char[] value){requireSecret(id);clearSecret(id);secrets.put(id,value.clone());emit(new UiEvent.SecretChanged(id));}
 @Override public void withSecret(String id,Consumer<char[]> use){requireSecret(id);Objects.requireNonNull(use);char[] copy=secrets.getOrDefault(id,new char[0]).clone();try{use.accept(copy);}finally{Arrays.fill(copy,(char)0);}}
 @Override public void clearSecret(String id){requireSecret(id);var old=secrets.remove(id);if(old!=null)Arrays.fill(old,(char)0);}
 @Override public void focus(String id){guard();if(!index(spec.root()).containsKey(id))throw new IllegalArgumentException("Unknown node");focused=id;}
 /** Requested logical focus. */
 public Optional<String> focused(){guard();return Optional.ofNullable(focused);}
 /** Deliver a typed event through containment. */
 public void emit(UiEvent event){guard();try{events.accept(Objects.requireNonNull(event));}catch(Throwable failure){owner.failed();}}
 @Override public void registerContent(CustomContent content){guard();Objects.requireNonNull(content);
  if(index(spec.root()).values().stream().noneMatch(n -> n instanceof Nodes.Custom c && c.contentId().equals(content.id())&&c.kind()==content.kind()))throw new IllegalArgumentException("No matching Custom content");
  if(contents.containsKey(content.id()))throw new IllegalArgumentException("Content already registered");
  FakeKitContent.validate(content);if(CONTENT_OWNERS.containsKey(content.component())||content.component().getParent()!=null)throw new IllegalArgumentException("Content is already owned or mounted");CONTENT_OWNERS.put(content.component(),this);contents.put(content.id(),content);
 }
 private void dispose(CustomContent content){
  // Claim removal before invoking plugin code; reentrant close may consume the remaining snapshot.
  if(contents.get(content.id())!=content)return;contents.remove(content.id());CONTENT_OWNERS.remove(content.component(),this);try{content.lifetime().close();}catch(Throwable failure){owner.failed();}}
 @Override public void close(){if(!closed.compareAndSet(false,true))return;Runnable cleanup=() -> {for(var value:secrets.values())Arrays.fill(value,(char)0);secrets.clear();for(var content:List.copyOf(contents.values()))dispose(content);contents.clear();events=e -> {};owner.removed(this);};if(EventQueue.isDispatchThread())cleanup.run();else EventQueue.invokeLater(cleanup);}
    static List<Nodes.Node> children(Nodes.Node node) {
        var result = new ArrayList<Nodes.Node>();
        switch (node) {
            case Nodes.Panel n -> { result.add(n.header()); n.searchOrNavigation().ifPresent(result::add); result.add(n.body()); n.footer().ifPresent(result::add); }
            case Nodes.Header n -> { n.context().ifPresent(result::add); n.primary().ifPresent(result::add); }
            case Nodes.Stack n -> result.addAll(n.children());
            case Nodes.ItemList n -> { n.addAction().ifPresent(result::add); for(var item:n.items()) { item.leading().ifPresent(result::add);result.addAll(item.title());result.addAll(item.subtitle());result.addAll(item.trailing()); } }
            case Nodes.Card n -> result.addAll(n.children());
            case Nodes.Split n -> result.addAll(n.children());
            case Nodes.FormRow n -> { result.add(n.control()); n.trailing().ifPresent(result::add); }
            case Nodes.SettingRow n -> result.add(n.control());
            case Nodes.Section n -> result.add(n.body());
            case Nodes.Empty n -> { result.add(n.icon()); result.addAll(n.actions()); }
            case Nodes.Form n -> { n.hero().ifPresent(result::add); result.addAll(n.sections()); n.preview().ifPresent(result::add); result.addAll(n.actions()); }
            default -> {}
        }
        return result;
    }
}
